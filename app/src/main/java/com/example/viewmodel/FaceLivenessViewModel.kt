package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EnrolledIdentityRepository
import com.example.mediapipe.MediaPipeFaceLandmarkerHelper
import com.example.ml.ArcFaceEmbedder
import com.example.ml.FaceBiometricEngine
import com.example.ml.FaceStandoff
import com.example.ml.ProfileBiometricHelper
import com.example.model.BiometricVector
import com.example.model.ChallengeStep
import com.example.model.ChallengeType
import com.example.model.EnrolledProfile
import com.example.model.FaceMetrics
import com.example.model.FacialLandmarks
import com.example.model.IdentityMatchResult
import com.example.model.SpoofDetectionResult
import com.example.model.SpoofRiskLevel
import com.example.model.VerificationLogEntry
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class AppScreen {
    MAIN_DASHBOARD,
    VERIFICATION_SUCCESS
}

enum class AppTab {
    HOME,
    PROFILE
}

data class FaceLivenessUiState(
    val currentScreen: AppScreen = AppScreen.MAIN_DASHBOARD,
    val selectedTab: AppTab = AppTab.HOME,
    val isFrontCamera: Boolean = true,
    val hasCameraPermission: Boolean = false,
    val isSimulationMode: Boolean = false,
    val simulationAttackType: SimulationType = SimulationType.LIVE_HUMAN,

    val metrics: FaceMetrics = FaceMetrics(),
    val spoofResult: SpoofDetectionResult = SpoofDetectionResult(),
    val currentVector: BiometricVector = BiometricVector.EMPTY,
    val lastCapturedBitmap: Bitmap? = null,
    val verifiedCapturedBitmap: Bitmap? = null,

    val challenges: List<ChallengeStep> = emptyList(),
    val currentChallengeIndex: Int = 0,
    val isChallengeActive: Boolean = false,
    val isLivenessVerified: Boolean = false,
    val isOfficeInActive: Boolean = false,
    val challengeStatusText: String = "Tap Office In to begin",
    val officeInTimestamp: Long? = null,
    val identityMatch: IdentityMatchResult = IdentityMatchResult(),
    val isMatchingIdentity: Boolean = false,

    val enrolledProfiles: List<EnrolledProfile> = emptyList(),
    val verificationLogs: List<VerificationLogEntry> = emptyList()
)

enum class SimulationType(val label: String, val description: String) {
    LIVE_HUMAN("Live Human Subject", "Simulates normal living human face with MediaPipe 3D mesh micro-jitter and responsive blink."),
    STATIC_PHOTO_ATTACK("2D Printed Photo Spoof", "Simulates presenting a printed photo with zero micro-motion and zero z-depth relief."),
    SCREEN_REPLAY_ATTACK("Screen Replay Spoof", "Simulates presenting a tablet/phone screen replay with planar reflection."),
    DIFFERENT_PERSON("Different Subject (Mismatch)", "Simulates a genuine living human whose 3D facial geometry differs from reference.")
}

class FaceLivenessViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: EnrolledIdentityRepository = EnrolledIdentityRepository.getInstance(application)
    private val biometricEngine: FaceBiometricEngine = FaceBiometricEngine()
    private val identityEmbedder: ArcFaceEmbedder = ArcFaceEmbedder.getInstance(application)

    private val _uiState = MutableStateFlow(FaceLivenessUiState())
    val uiState: StateFlow<FaceLivenessUiState> = _uiState.asStateFlow()

    private var simulationJob: Job? = null
    private var officeInCompleteJob: Job? = null
    private var blinkStage = 0 // 0: waiting for close, 1: closed, 2: reopened

    init {
        viewModelScope.launch {
            repository.profiles.collect { profiles ->
                _uiState.update { it.copy(enrolledProfiles = profiles) }
            }
        }
    }

    fun setTab(tab: AppTab) {
        if (tab != AppTab.HOME && _uiState.value.isOfficeInActive) {
            cancelOfficeIn()
        }
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun toggleCameraFacing() = toggleCameraLens()

    fun setCameraPermission(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
        if (!granted && !_uiState.value.isSimulationMode) {
            enableSimulationMode(true)
        }
    }

    fun enableSimulationMode(enable: Boolean) {
        _uiState.update { it.copy(isSimulationMode = enable) }
        if (enable) {
            startSimulationLoop()
        } else {
            simulationJob?.cancel()
            simulationJob = null
        }
    }

    fun setSimulationMode(enabled: Boolean) = enableSimulationMode(enabled)

    fun setSimulationAttackType(type: SimulationType) {
        _uiState.update { it.copy(simulationAttackType = type) }
        resetChallenges()
    }

    fun setSimulationType(type: SimulationType) = setSimulationAttackType(type)

    fun startOfficeIn() {
        officeInCompleteJob?.cancel()
        officeInCompleteJob = null
        blinkStage = 0
        _uiState.update { it.copy(selectedTab = AppTab.HOME, currentScreen = AppScreen.MAIN_DASHBOARD) }
        if (!_uiState.value.hasCameraPermission && !_uiState.value.isSimulationMode) {
            enableSimulationMode(true)
        }
        initChallenges()
        _uiState.update { it.copy(isOfficeInActive = true) }
    }

    fun cancelOfficeIn() {
        officeInCompleteJob?.cancel()
        officeInCompleteJob = null
        blinkStage = 0
        biometricEngine.resetHistory()
        _uiState.update {
            it.copy(
                isOfficeInActive = false,
                isChallengeActive = false,
                isLivenessVerified = false,
                challenges = emptyList(),
                currentChallengeIndex = 0,
                challengeStatusText = "Tap Office In to begin"
            )
        }
    }

    fun navigateToScreen(screen: AppScreen) {
        if (screen == AppScreen.MAIN_DASHBOARD) {
            cancelOfficeIn()
        }
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun updateEnrolledProfile(updated: EnrolledProfile) {
        viewModelScope.launch {
            val template = ProfileBiometricHelper.extractIdentityTemplate(
                getApplication(),
                updated.photoUri
            )
            val withTemplate = if (BiometricVector.isIdentityTemplate(template)) {
                updated.copy(vector = template)
            } else {
                updated
            }
            repository.updateProfile(withTemplate)
        }
    }

    fun deleteEnrolledProfile(profileId: String) {
        repository.deleteProfile(profileId)
    }

    fun completeOfficeIn() {
        viewModelScope.launch {
            val liveBmp = _uiState.value.lastCapturedBitmap
            val liveBox = _uiState.value.metrics.boundingBox
            val profiles = _uiState.value.enrolledProfiles
            _uiState.update {
                it.copy(
                    isMatchingIdentity = true,
                    challengeStatusText = "Matching enrolled templates…"
                )
            }
            val match = identityEmbedder.match(
                liveBmp,
                liveBox,
                profiles
            )
            val spoof = _uiState.value.spoofResult
            if (!_uiState.value.isLivenessVerified) {
                _uiState.update { it.copy(isMatchingIdentity = false) }
                return@launch
            }
            if (spoof.riskLevel == SpoofRiskLevel.HIGH || !spoof.isLive) {
                _uiState.update {
                    it.copy(
                        isLivenessVerified = false,
                        isChallengeActive = true,
                        isMatchingIdentity = false,
                        challengeStatusText =
                            spoof.detectionNotes.firstOrNull() ?: "Spoof check failed — try again"
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.VERIFICATION_SUCCESS,
                    isOfficeInActive = false,
                    isChallengeActive = false,
                    isMatchingIdentity = false,
                    verifiedCapturedBitmap = liveBmp ?: it.lastCapturedBitmap,
                    officeInTimestamp = System.currentTimeMillis(),
                    identityMatch = match
                )
            }
            logVerificationEvent(wasMediaPipeMesh = true)
        }
    }

    fun restartVerificationFlow() {
        _uiState.update {
            it.copy(
                selectedTab = AppTab.HOME,
                currentScreen = AppScreen.MAIN_DASHBOARD,
                verifiedCapturedBitmap = null,
                identityMatch = IdentityMatchResult()
            )
        }
        startOfficeIn()
    }

    fun enrollProfileWithDetails(
        name: String,
        address: String = "",
        mobileNo: String = "",
        email: String = "",
        role: String = "Authorized Person",
        notes: String = "",
        photoUri: String? = null,
        avatarColor: Long = 0xFF0284C7
    ) {
        viewModelScope.launch {
            val template = ProfileBiometricHelper.extractIdentityTemplate(
                getApplication(),
                photoUri
            )
            repository.enrollNewProfile(
                name = name,
                address = address,
                mobileNo = mobileNo,
                email = email,
                role = role,
                vector = template,
                notes = notes,
                photoUri = photoUri,
                avatarColor = avatarColor
            )
            _uiState.update {
                it.copy(currentScreen = AppScreen.MAIN_DASHBOARD)
            }
        }
    }

    fun resetChallenges() {
        initChallenges()
        biometricEngine.resetHistory()
        blinkStage = 0
    }

    private fun initChallenges() {
        // MiniFASNet PAD first (mesh on), then MediaPipe center + blink with mesh cleared.
        val challenges = listOf(
            ChallengeStep(ChallengeType.CENTER_FACE),
            ChallengeStep(ChallengeType.BLINK_EYES)
        )
        _uiState.update {
            it.copy(
                challenges = challenges,
                currentChallengeIndex = 0,
                isChallengeActive = true,
                isLivenessVerified = false,
                challengeStatusText = challenges.first().type.instruction
            )
        }
    }

    /**
     * Called when MediaPipe Face Landmarker produces real-time 3D Mesh analysis from camera.
     */
    fun onMediaPipeDetected(
        result: MediaPipeFaceLandmarkerHelper.MediaPipeAnalysisResult,
        capturedBitmap: Bitmap? = null
    ) {
        if (!result.hasFace) {
            _uiState.update {
                it.copy(
                    metrics = result.metrics,
                    spoofResult = result.spoofResult,
                    lastCapturedBitmap = capturedBitmap ?: it.lastCapturedBitmap
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                metrics = result.metrics.copy(meshPoints = result.meshPoints, blendshapes = result.blendshapes),
                spoofResult = result.spoofResult,
                currentVector = result.biometricVector,
                lastCapturedBitmap = capturedBitmap ?: it.lastCapturedBitmap
            )
        }

        evaluateChallengeProgress(
            result.metrics.copy(meshPoints = result.meshPoints, blendshapes = result.blendshapes),
            result.spoofResult
        )
    }

    /**
     * Fallback frame processing via ML Kit if needed.
     */
    fun onFaceDetected(face: Face?, frameWidth: Int, frameHeight: Int, capturedFrameBitmap: Bitmap? = null) {
        if (face == null) {
            _uiState.update {
                it.copy(
                    metrics = FaceMetrics(hasFace = false),
                    spoofResult = SpoofDetectionResult(
                        isLive = false,
                        riskLevel = SpoofRiskLevel.MEDIUM,
                        spoofProbability = 0.5f,
                        detectionNotes = listOf("Align face in MediaPipe viewfinder")
                    )
                )
            }
            return
        }

        val metrics = biometricEngine.extractMetrics(face, frameWidth, frameHeight)
        val spoof = biometricEngine.evaluateSpoofing(metrics)
        val vector = biometricEngine.computeBiometricVector(metrics)

        _uiState.update {
            it.copy(
                metrics = metrics,
                spoofResult = spoof,
                currentVector = vector,
                lastCapturedBitmap = capturedFrameBitmap ?: it.lastCapturedBitmap
            )
        }

        evaluateChallengeProgress(metrics, spoof)
    }

    /**
     * Evaluates active liveness challenges against real-time facial metrics.
     */
    private fun evaluateChallengeProgress(metrics: FaceMetrics, spoof: SpoofDetectionResult) {
        val state = _uiState.value
        if (!state.isOfficeInActive || !state.isChallengeActive || state.isLivenessVerified) return
        if (state.currentChallengeIndex >= state.challenges.size) return

        val currentStep = state.challenges[state.currentChallengeIndex]
        var stepProgress = currentStep.progress
        var stepCompleted = false

        if (spoof.riskLevel == SpoofRiskLevel.HIGH || !spoof.isLive) {
            val blocked = currentStep.copy(progress = (stepProgress - 0.2f).coerceAtLeast(0f))
            val status = when {
                spoof.isScreenReplayDetected ->
                    spoof.detectionNotes.firstOrNull { it.contains("SCREEN", ignoreCase = true) || it.contains("VIDEO", ignoreCase = true) }
                        ?: "Screen / video presentation rejected — use a live face"
                else ->
                    spoof.detectionNotes.firstOrNull() ?: "Spoof detected — use a live face"
            }
            val updatedList = state.challenges.toMutableList()
            updatedList[state.currentChallengeIndex] = blocked
            _uiState.update {
                it.copy(
                    challenges = updatedList,
                    challengeStatusText = status
                )
            }
            return
        }

        when (currentStep.type) {
            ChallengeType.CENTER_FACE -> {
                val isCentered = abs(metrics.faceCenterX - 0.5f) < 0.16f &&
                        abs(metrics.faceCenterY - 0.5f) < 0.18f &&
                        abs(metrics.headEulerYaw) < 12f
                if (isCentered) {
                    stepProgress = (stepProgress + 0.25f).coerceAtMost(1f)
                    if (stepProgress >= 1f) stepCompleted = true
                } else {
                    stepProgress = (stepProgress - 0.1f).coerceAtLeast(0f)
                }
            }

            ChallengeType.BLINK_EYES -> {
                // MediaPipe blendshapes: prefer direct blink scores; softer thresholds for natural blinks.
                val blinkL = metrics.blendshapes["eyeBlinkLeft"] ?: (1f - metrics.leftEyeOpenProb)
                val blinkR = metrics.blendshapes["eyeBlinkRight"] ?: (1f - metrics.rightEyeOpenProb)
                val bothClosed = blinkL >= 0.45f && blinkR >= 0.45f
                val bothOpen = blinkL <= 0.30f && blinkR <= 0.30f

                when (blinkStage) {
                    0 -> { // Waiting for eyes to close
                        if (bothClosed) {
                            blinkStage = 1
                            stepProgress = 0.5f
                        }
                    }
                    1 -> { // Closed, waiting for eyes to open again
                        if (bothOpen) {
                            blinkStage = 2
                            stepProgress = 1.0f
                            stepCompleted = true
                        }
                    }
                }
            }

            ChallengeType.SMILE -> {
                if (metrics.smileProb > 0.55f) {
                    stepProgress = (stepProgress + 0.30f).coerceAtMost(1f)
                    if (stepProgress >= 1f) stepCompleted = true
                } else {
                    stepProgress = (stepProgress - 0.05f).coerceAtLeast(0f)
                }
            }

            ChallengeType.TURN_LEFT -> {
                if (metrics.headEulerYaw > 14f) {
                    stepProgress = (stepProgress + 0.35f).coerceAtMost(1f)
                    if (stepProgress >= 1f) stepCompleted = true
                }
            }

            ChallengeType.TURN_RIGHT -> {
                if (metrics.headEulerYaw < -14f) {
                    stepProgress = (stepProgress + 0.35f).coerceAtMost(1f)
                    if (stepProgress >= 1f) stepCompleted = true
                }
            }

            ChallengeType.NOD_HEAD -> {
                if (abs(metrics.headEulerPitch) > 10f) {
                    stepProgress = (stepProgress + 0.30f).coerceAtMost(1f)
                    if (stepProgress >= 1f) stepCompleted = true
                }
            }
        }

        if (stepCompleted) {
            val updatedList = state.challenges.toMutableList()
            updatedList[state.currentChallengeIndex] = currentStep.copy(isCompleted = true, progress = 1f)

            val nextIndex = state.currentChallengeIndex + 1
            if (nextIndex < updatedList.size) {
                _uiState.update {
                    it.copy(
                        challenges = updatedList,
                        currentChallengeIndex = nextIndex,
                        challengeStatusText = updatedList[nextIndex].type.instruction
                    )
                }
                blinkStage = 0
            } else {
                // All challenges successfully passed!
                _uiState.update {
                    it.copy(
                        challenges = updatedList,
                        isLivenessVerified = true,
                        isChallengeActive = false,
                        challengeStatusText = "Office In liveness confirmed"
                    )
                }
                logVerificationEvent(wasMediaPipeMesh = true)

                officeInCompleteJob?.cancel()
                officeInCompleteJob = viewModelScope.launch {
                    delay(900)
                    if (_uiState.value.isLivenessVerified && _uiState.value.isOfficeInActive) {
                        completeOfficeIn()
                    }
                    officeInCompleteJob = null
                }
            }
        } else {
            val updatedList = state.challenges.toMutableList()
            updatedList[state.currentChallengeIndex] = currentStep.copy(progress = stepProgress)
            _uiState.update { it.copy(challenges = updatedList) }
        }
    }

    private fun logVerificationEvent(wasMediaPipeMesh: Boolean = true) {
        val state = _uiState.value
        val formattedTime = SimpleDateFormat("HH:mm:ss · MMM dd", Locale.getDefault()).format(Date())
        val entry = VerificationLogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            formattedTime = formattedTime,
            profileMatched = _uiState.value.identityMatch.profile?.name ?: "Office In",
            matchScore = _uiState.value.identityMatch.matchPercentage,
            livenessPassed = state.isLivenessVerified,
            spoofRisk = state.spoofResult.riskLevel,
            wasMediaPipeMeshVerified = wasMediaPipeMesh
        )
        _uiState.update {
            it.copy(verificationLogs = listOf(entry) + it.verificationLogs.take(20))
        }
    }

    /**
     * Simulation engine for local development & emulator testing.
     * Generates MediaPipe 468-point 3D Face Mesh coordinates, 52 blendshapes, micro-tremors, and spoof signatures.
     */
    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            var simTick = 0
            while (true) {
                delay(120) // ~8 fps simulation rate
                simTick++

                val simType = _uiState.value.simulationAttackType
                val (metrics, spoof, vector) = generateSimulatedTelemetry(simTick, simType)

                _uiState.update {
                    it.copy(
                        metrics = metrics,
                        spoofResult = spoof,
                        currentVector = vector
                    )
                }

                evaluateChallengeProgress(metrics, spoof)
            }
        }
    }

    private fun generateSimulatedTelemetry(
        tick: Int,
        type: SimulationType
    ): Triple<FaceMetrics, SpoofDetectionResult, BiometricVector> {
        val baseBox = RectF(0.33f, 0.28f, 0.67f, 0.72f)


        // Micro-jitter for live human vs zero jitter for static photo
        val jitterMagnitude = if (type == SimulationType.STATIC_PHOTO_ATTACK) 0.00005f else 0.006f
        val jitterX = (Random.nextFloat() - 0.5f) * jitterMagnitude
        val jitterY = (Random.nextFloat() - 0.5f) * jitterMagnitude

        val currentChallenge = _uiState.value.challenges.getOrNull(_uiState.value.currentChallengeIndex)?.type

        var yaw = 0f
        var pitch = 0f
        var leftEyeOpen = 0.92f
        var rightEyeOpen = 0.90f
        var smile = 0.05f

        when (type) {
            SimulationType.STATIC_PHOTO_ATTACK -> {
                leftEyeOpen = 0.85f
                rightEyeOpen = 0.85f
                smile = 0.10f
                yaw = 0.0f
            }
            SimulationType.SCREEN_REPLAY_ATTACK -> {
                leftEyeOpen = 0.80f
                rightEyeOpen = 0.80f
                yaw = 2.0f
            }
            SimulationType.LIVE_HUMAN, SimulationType.DIFFERENT_PERSON -> {
                when (currentChallenge) {
                    ChallengeType.CENTER_FACE -> {
                        yaw = 0f
                    }
                    ChallengeType.BLINK_EYES -> {
                        if (tick % 10 in 4..6) {
                            leftEyeOpen = 0.10f
                            rightEyeOpen = 0.10f
                        } else {
                            leftEyeOpen = 0.95f
                            rightEyeOpen = 0.95f
                        }
                    }
                    ChallengeType.SMILE -> {
                        smile = 0.85f
                    }
                    ChallengeType.TURN_LEFT -> {
                        yaw = 22.0f
                    }
                    ChallengeType.TURN_RIGHT -> {
                        yaw = -22.0f
                    }
                    else -> {}
                }
            }
        }

        val leftEye = PointF(0.43f + jitterX, 0.42f + jitterY)
        val rightEye = PointF(0.57f + jitterX, 0.42f + jitterY)
        val nose = PointF(0.50f + jitterX + (yaw * 0.002f), 0.54f + jitterY)
        val leftMouth = PointF(0.42f + jitterX, 0.70f + jitterY)
        val rightMouth = PointF(0.58f + jitterX, 0.70f + jitterY)
        val mouthBottom = PointF(0.50f + jitterX, 0.76f + jitterY)
        val leftCheek = PointF(0.30f + jitterX, 0.54f + jitterY)
        val rightCheek = PointF(0.70f + jitterX, 0.54f + jitterY)

        val landmarks = FacialLandmarks(
            leftEye = leftEye,
            rightEye = rightEye,
            noseBase = nose,
            leftMouth = leftMouth,
            rightMouth = rightMouth,
            mouthBottom = mouthBottom,
            leftCheek = leftCheek,
            rightCheek = rightCheek
        )

        // Generate synthetic 468 MediaPipe Face Mesh coordinates
        val meshList = ArrayList<PointF>(468)
        val centerX = 0.50f + jitterX + (yaw * 0.0015f)
        val centerY = 0.52f + jitterY
        val radiusX = 0.22f
        val radiusY = 0.28f

        // Contour ellipse points (36 points)
        for (i in 0 until 36) {
            val angle = Math.toRadians(i * 10.0)
            val mx = centerX + (radiusX * cos(angle)).toFloat()
            val my = centerY + (radiusY * sin(angle)).toFloat()
            meshList.add(PointF(mx, my))
        }
        // Inner facial grid points (eyes, eyebrows, nose, lips lattices)
        for (ring in 1..4) {
            val scale = ring * 0.22f
            for (i in 0 until 24) {
                val angle = Math.toRadians(i * 15.0)
                val mx = centerX + (radiusX * scale * cos(angle)).toFloat()
                val my = centerY + (radiusY * scale * sin(angle)).toFloat()
                meshList.add(PointF(mx, my))
            }
        }
        // Specific feature anchors
        meshList.add(nose)
        meshList.add(leftEye)
        meshList.add(rightEye)
        meshList.add(leftMouth)
        meshList.add(rightMouth)

        val blendshapes = mapOf(
            "eyeBlinkLeft" to (1f - leftEyeOpen),
            "eyeBlinkRight" to (1f - rightEyeOpen),
            "mouthSmileLeft" to smile,
            "mouthSmileRight" to smile,
            "jawOpen" to if (smile > 0.5f) 0.35f else 0.05f,
            "browDownLeft" to 0.05f,
            "browDownRight" to 0.05f
        )

        val iod = FaceStandoff.iodNormalized(leftEye, rightEye)
        val distanceFeet = FaceStandoff.estimateFeet(iod, baseBox.width())

        val metrics = FaceMetrics(
            hasFace = true,
            boundingBox = baseBox,
            leftEyeOpenProb = leftEyeOpen,
            rightEyeOpenProb = rightEyeOpen,
            smileProb = smile,
            headEulerYaw = yaw,
            headEulerPitch = pitch,
            headEulerRoll = 0.5f,
            faceAreaRatio = baseBox.width() * baseBox.height(),
            faceCenterX = 0.50f + jitterX,
            faceCenterY = 0.50f + jitterY,
            landmarks = landmarks,
            meshPoints = meshList,
            blendshapes = blendshapes,
            iodNormalized = iod,
            estimatedDistanceFeet = distanceFeet
        )

        val spoofResult = when (type) {
            SimulationType.STATIC_PHOTO_ATTACK -> SpoofDetectionResult(
                isLive = false,
                riskLevel = SpoofRiskLevel.HIGH,
                spoofProbability = 0.89f,
                microMovementScore = 0.05f,
                textureVarianceScore = 0.20f,
                depthParallaxScore = 0.012f,
                isStaticPhotoDetected = true,
                estimatedDistanceFeet = distanceFeet,
                isDistanceInRange = true,
                detectionNotes = listOf("STATIC PHOTO PRESENTATION ATTACK DETECTED")
            )
            SimulationType.SCREEN_REPLAY_ATTACK -> SpoofDetectionResult(
                isLive = false,
                riskLevel = SpoofRiskLevel.HIGH,
                spoofProbability = 0.78f,
                microMovementScore = 0.25f,
                textureVarianceScore = 0.18f,
                depthParallaxScore = 0.014f,
                isScreenReplayDetected = true,
                estimatedDistanceFeet = distanceFeet,
                isDistanceInRange = true,
                detectionNotes = listOf("SCREEN REPLAY ATTACK SUSPECTED")
            )
            SimulationType.LIVE_HUMAN, SimulationType.DIFFERENT_PERSON -> SpoofDetectionResult(
                isLive = true,
                riskLevel = SpoofRiskLevel.LOW,
                spoofProbability = 0.04f,
                microMovementScore = 0.94f,
                textureVarianceScore = 0.91f,
                depthParallaxScore = 0.93f,
                estimatedDistanceFeet = distanceFeet,
                isDistanceInRange = true,
                detectionNotes = listOf(
                    "3D mesh curvature verified",
                    "Natural micro-tremors detected"
                )
            )
        }

        val vector = if (type == SimulationType.DIFFERENT_PERSON) {
            biometricEngine.computeBiometricVector(
                metrics.copy(
                    landmarks = landmarks.copy(
                        leftEye = PointF(0.32f, 0.40f),
                        rightEye = PointF(0.68f, 0.40f),
                        leftCheek = PointF(0.24f, 0.50f),
                        rightCheek = PointF(0.76f, 0.50f),
                        noseBase = PointF(0.50f, 0.62f)
                    )
                )
            )
        } else {
            _uiState.value.enrolledProfiles.firstOrNull()?.vector ?: biometricEngine.computeBiometricVector(metrics)
        }

        return Triple(metrics, spoofResult, vector)
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
        officeInCompleteJob?.cancel()
    }
}
