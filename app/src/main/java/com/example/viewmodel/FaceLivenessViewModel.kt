package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.remote.ApiClient
import com.example.data.remote.DeviceInfoPayload
import com.example.data.remote.EmployeeFaceResponse
import com.example.data.remote.LivenessPayload
import com.example.data.remote.MatchConfigDto
import com.example.data.remote.MatchResultRequest
import com.example.data.remote.MatchResultResponse
import com.example.mediapipe.MediaPipeFaceLandmarkerHelper
import com.example.ml.ArcFaceEmbedder
import com.example.ml.FaceBiometricEngine
import com.example.ml.FaceStandoff
import com.example.model.BiometricVector
import com.example.model.ChallengeStep
import com.example.model.ChallengeType
import com.example.model.EnrolledProfile
import com.example.model.FaceMetrics
import com.example.model.FacialLandmarks
import com.example.model.IdentityMatchResult
import com.example.model.MatchConfig
import com.example.model.SpoofDetectionResult
import com.example.model.SpoofRiskLevel
import com.example.model.VerificationLogEntry
import com.google.mlkit.vision.face.Face
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
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
    SETTINGS
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
    val verificationLogs: List<VerificationLogEntry> = emptyList(),

    val mobileNumber: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val isLoggingIn: Boolean = false,
    val matchConfig: MatchConfig = MatchConfig(),
    val faceId: String = "",
    val deviceId: String = "",
    val gateId: Int? = null,
    /** True after "Start Recognize": match 1:N against the full enrolled list. */
    val isRecognizeMode: Boolean = false,

    val isPostingMatchResult: Boolean = false,
    val matchResultMessage: MatchResultMessage? = null
)

enum class MatchResultStatus { RECORDED, NOT_RECORDED, FAILED }

data class MatchResultMessage(
    val status: MatchResultStatus,
    val title: String,
    val message: String,
    val details: List<String> = emptyList()
)

sealed interface LoginEvent {
    data object Success : LoginEvent
    data class Error(val message: String) : LoginEvent
}

enum class SimulationType(val label: String, val description: String) {
    LIVE_HUMAN("Live Human Subject", "Simulates normal living human face with MediaPipe 3D mesh micro-jitter and responsive blink."),
    STATIC_PHOTO_ATTACK("2D Printed Photo Spoof", "Simulates presenting a printed photo with zero micro-motion and zero z-depth relief."),
    SCREEN_REPLAY_ATTACK("Screen Replay Spoof", "Simulates presenting a tablet/phone screen replay with planar reflection."),
    DIFFERENT_PERSON("Different Subject (Mismatch)", "Simulates a genuine living human whose 3D facial geometry differs from reference.")
}

class FaceLivenessViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val biometricEngine: FaceBiometricEngine = FaceBiometricEngine()
    private val identityEmbedder: ArcFaceEmbedder by lazy { ArcFaceEmbedder.getInstance(application) }

    private val _uiState = MutableStateFlow(FaceLivenessUiState())
    val uiState: StateFlow<FaceLivenessUiState> = _uiState.asStateFlow()

    private val _loginEvents = Channel<LoginEvent>(Channel.BUFFERED)
    val loginEvents: Flow<LoginEvent> = _loginEvents.receiveAsFlow()
    private var loginJob: Job? = null

    private var simulationJob: Job? = null
    private var officeInCompleteJob: Job? = null
    private var matchResultJob: Job? = null
    private var pendingMatchResult: MatchResultRequest? = null
    /** 0 = wait for close, 1 = wait for reopen. */
    private var blinkStage = 0
    private var blinkClosedFrames = 0
    private var blinkOpenFrames = 0
    private var lastChallengeUiAtMs = 0L

    init {
        // Loading the ONNX model takes seconds on low-end devices; keep it off the main thread.
        viewModelScope.launch(Dispatchers.Default) { identityEmbedder }
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
        resetBlinkTracker()
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
        resetBlinkTracker()
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
            _uiState.update { it.copy(matchResultMessage = null) }
        }
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun loginWithFace(mobileNumber: String, deviceId: String, gateId: Int?) {
        if (_uiState.value.isLoggingIn) return
        _uiState.update { it.copy(isLoggingIn = true) }
        loginJob = viewModelScope.launch {
            val event = try {
                val response = ApiClient.faceApi.getEmployeeFace(mobileNumber)
                startSession(mobileNumber, response, deviceId, gateId)
                LoginEvent.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoginEvent.Error(describeLoginError(e))
            }
            _uiState.update { it.copy(isLoggingIn = false) }
            _loginEvents.send(event)
        }
    }

    /**
     * Loads every enrolled face from GET /api/employees/face (no mobileNumber), then opens the
     * dashboard. Office In matches 1:N against that list.
     */
    fun startRecognize(deviceId: String, gateId: Int?) {
        if (_uiState.value.isLoggingIn) return
        _uiState.update { it.copy(isLoggingIn = true) }
        loginJob = viewModelScope.launch {
            val event = try {
                startRecognizeSession(deviceId, gateId)
                LoginEvent.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoginEvent.Error(describeLoginError(e))
            }
            _uiState.update { it.copy(isLoggingIn = false) }
            _loginEvents.send(event)
        }
    }

    private suspend fun startSession(
        mobileNumber: String,
        response: EmployeeFaceResponse,
        deviceId: String,
        gateId: Int?
    ) {
        val face = response.face
        val embedding = face?.embedding.orEmpty()
        val config = response.matchConfig.toMatchConfig()
        if (embedding.isEmpty()) throw IllegalStateException("No face enrolled for this mobile number.")
        if (embedding.size != config.embeddingDim) {
            throw IllegalStateException(
                "Enrolled face has ${embedding.size} values; this app needs ${config.embeddingDim}. Re-enroll the face."
            )
        }

        val employee = response.employee
        val name = employee?.name?.takeIf { it.isNotBlank() } ?: "+91 $mobileNumber"
        val profile = EnrolledProfile(
            id = face?.faceId ?: employee?.employeeId ?: mobileNumber,
            name = name,
            enrolledDate = "",
            vector = BiometricVector(embedding.toFloatArray()),
            photoUri = face?.imageBase64?.let { saveEnrolledFacePhoto(it, face.faceId ?: mobileNumber) },
            mobileNo = employee?.mobileNumber ?: mobileNumber,
            employeeId = employee?.employeeId.orEmpty()
        )

        _uiState.update {
            it.copy(
                mobileNumber = mobileNumber,
                employeeId = employee?.employeeId.orEmpty(),
                employeeName = name,
                enrolledProfiles = listOf(profile),
                matchConfig = config,
                faceId = face?.faceId.orEmpty(),
                deviceId = deviceId,
                gateId = gateId,
                isRecognizeMode = false,
                selectedTab = AppTab.HOME,
                currentScreen = AppScreen.MAIN_DASHBOARD,
                identityMatch = IdentityMatchResult(),
                matchResultMessage = null
            )
        }
    }

    private suspend fun startRecognizeSession(deviceId: String, gateId: Int?) {
        val responses = ApiClient.faceApi.getAllEmployeeFaces()
        if (responses.isEmpty()) {
            throw IllegalStateException("No enrolled faces found on the server.")
        }

        clearEnrolledFacePhotos()
        val defaults = MatchConfig()
        var config = defaults
        val profiles = ArrayList<EnrolledProfile>()
        for (response in responses) {
            val face = response.face ?: continue
            val embedding = face.embedding.orEmpty()
            val entryConfig = response.matchConfig.toMatchConfig()
            if (embedding.size != entryConfig.embeddingDim) continue
            if (profiles.isEmpty()) config = entryConfig

            val employee = response.employee
            val faceId = face.faceId?.takeIf { it.isNotBlank() }
                ?: employee?.employeeId?.takeIf { it.isNotBlank() }
                ?: continue
            val name = employee?.name?.takeIf { it.isNotBlank() }
                ?: employee?.mobileNumber?.let { "+91 $it" }
                ?: faceId
            profiles.add(
                EnrolledProfile(
                    id = faceId,
                    name = name,
                    enrolledDate = "",
                    vector = BiometricVector(embedding.toFloatArray()),
                    photoUri = face.imageBase64?.let { saveEnrolledFacePhoto(it, faceId) },
                    mobileNo = employee?.mobileNumber.orEmpty(),
                    employeeId = employee?.employeeId.orEmpty()
                )
            )
        }
        if (profiles.isEmpty()) {
            throw IllegalStateException(
                "No usable ${defaults.embeddingDim}-D face templates found. Re-enroll faces on the server."
            )
        }

        _uiState.update {
            it.copy(
                mobileNumber = "",
                employeeId = "",
                employeeName = "Recognize · ${profiles.size} faces",
                enrolledProfiles = profiles,
                matchConfig = config,
                faceId = "",
                deviceId = deviceId,
                gateId = gateId,
                isRecognizeMode = true,
                selectedTab = AppTab.HOME,
                currentScreen = AppScreen.MAIN_DASHBOARD,
                identityMatch = IdentityMatchResult(),
                matchResultMessage = null
            )
        }
    }

    private fun MatchConfigDto?.toMatchConfig(): MatchConfig {
        val defaults = MatchConfig()
        if (this == null) return defaults
        val challenges = requiredChallenges.orEmpty().mapNotNull { name ->
            ChallengeType.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        }
        return defaults.copy(
            threshold = threshold?.takeIf { it > 0f && it <= 1f } ?: defaults.threshold,
            maxSpoofProbability = maxSpoofProbability?.takeIf { it > 0f && it <= 1f }
                ?: defaults.maxSpoofProbability,
            requiredChallenges = challenges.distinct().ifEmpty { defaults.requiredChallenges }
        )
    }

    /** Writes the server face crop to cache so the result screen can show it; returns a file URI. */
    private suspend fun saveEnrolledFacePhoto(imageBase64: String, key: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(imageBase64.substringAfter("base64,"), Base64.DEFAULT)
                if (bytes.isEmpty()) return@withContext null
                val safeKey = key.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                val file = File(
                    getApplication<Application>().cacheDir,
                    "$ENROLLED_FACE_PREFIX${safeKey}_${System.currentTimeMillis()}.jpg"
                )
                file.writeBytes(bytes)
                Uri.fromFile(file).toString()
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IOException) {
                null
            }
        }

    private fun clearEnrolledFacePhotos() {
        getApplication<Application>().cacheDir
            .listFiles { file -> file.name.startsWith(ENROLLED_FACE_PREFIX) }
            ?.forEach { it.delete() }
    }

    private fun describeLoginError(e: Exception): String = when (e) {
        is HttpException -> ApiClient.errorMessage(e)?.takeIf { it.isNotBlank() }
            ?: if (e.code() == 404) "Employee doesn't exist" else "Login failed (HTTP ${e.code()})"
        is IOException -> "Cannot reach the server at ${ApiClient.baseUrl}"
        is JsonDataException -> "Unexpected response from the server."
        else -> e.message ?: "Login failed."
    }

    fun logout() {
        loginJob?.cancel()
        matchResultJob?.cancel()
        pendingMatchResult = null
        cancelOfficeIn()
        clearEnrolledFacePhotos()
        _uiState.update {
            it.copy(
                mobileNumber = "",
                employeeId = "",
                employeeName = "",
                isLoggingIn = false,
                enrolledProfiles = emptyList(),
                matchConfig = MatchConfig(),
                faceId = "",
                deviceId = "",
                gateId = null,
                isRecognizeMode = false,
                isPostingMatchResult = false,
                matchResultMessage = null,
                selectedTab = AppTab.HOME,
                currentScreen = AppScreen.MAIN_DASHBOARD,
                verifiedCapturedBitmap = null,
                identityMatch = IdentityMatchResult()
            )
        }
    }

    fun completeOfficeIn() {
        viewModelScope.launch {
            val stateBefore = _uiState.value
            val liveBmp = stateBefore.lastCapturedBitmap
            val liveBox = stateBefore.metrics.boundingBox
            val profiles = stateBefore.enrolledProfiles
            val config = stateBefore.matchConfig
            val recognizeMode = stateBefore.isRecognizeMode
            _uiState.update {
                it.copy(
                    isMatchingIdentity = true,
                    challengeStatusText = if (recognizeMode) {
                        "Matching against enrolled faces…"
                    } else {
                        "Matching your enrolled face…"
                    }
                )
            }
            var match = identityEmbedder.match(
                liveBmp,
                liveBox,
                profiles,
                threshold = config.threshold
            )
            if (!match.isMatch && recognizeMode) {
                match = match.copy(
                    profile = null,
                    statusLabel = "Not enrolled yet"
                )
            }
            val spoof = _uiState.value.spoofResult
            if (!_uiState.value.isLivenessVerified) {
                _uiState.update { it.copy(isMatchingIdentity = false) }
                return@launch
            }
            if (spoof.riskLevel == SpoofRiskLevel.HIGH || !spoof.isLive ||
                spoof.spoofProbability > config.maxSpoofProbability
            ) {
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
            val capturedAt = System.currentTimeMillis()
            val matchedProfile = match.profile
            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.VERIFICATION_SUCCESS,
                    isOfficeInActive = false,
                    isChallengeActive = false,
                    isMatchingIdentity = false,
                    verifiedCapturedBitmap = liveBmp ?: it.lastCapturedBitmap,
                    officeInTimestamp = capturedAt,
                    identityMatch = match,
                    // Fill employee fields from the 1:N hit so Settings / POST use the right person.
                    faceId = if (match.isMatch) {
                        matchedProfile?.id.orEmpty()
                    } else if (recognizeMode) {
                        ""
                    } else {
                        it.faceId
                    },
                    employeeId = if (match.isMatch) {
                        matchedProfile?.employeeId?.ifBlank { matchedProfile.id }.orEmpty()
                    } else if (recognizeMode) {
                        ""
                    } else {
                        it.employeeId
                    },
                    employeeName = if (match.isMatch) {
                        matchedProfile?.name.orEmpty()
                    } else if (recognizeMode) {
                        it.employeeName
                    } else {
                        it.employeeName
                    },
                    mobileNumber = if (match.isMatch) {
                        matchedProfile?.mobileNo.orEmpty()
                    } else if (recognizeMode) {
                        ""
                    } else {
                        it.mobileNumber
                    },
                    // Not enrolled: show dialog on the result page (no server POST).
                    matchResultMessage = if (!match.isMatch && recognizeMode) {
                        MatchResultMessage(
                            status = MatchResultStatus.NOT_RECORDED,
                            title = "Not enrolled yet",
                            message = "This face is not in the enrolled employee list."
                        )
                    } else {
                        null
                    }
                )
            }
            logVerificationEvent(wasMediaPipeMesh = true)
            // Recognize mode only posts when a face from the list matched.
            if (match.isMatch || !recognizeMode) {
                submitMatchResult(match, _uiState.value.verifiedCapturedBitmap, capturedAt)
            }
        }
    }

    private fun submitMatchResult(match: IdentityMatchResult, capture: Bitmap?, capturedAt: Long) {
        val state = _uiState.value
        val faceId = when {
            match.isMatch -> match.profile?.id?.takeIf { it.isNotBlank() }
                ?: state.faceId.takeIf { it.isNotBlank() }
            else -> state.faceId.takeIf { it.isNotBlank() }
                ?: state.enrolledProfiles.firstOrNull()?.id
        }.orEmpty()
        if (faceId.isBlank()) {
            _uiState.update {
                it.copy(
                    isPostingMatchResult = false,
                    matchResultMessage = MatchResultMessage(
                        status = MatchResultStatus.FAILED,
                        title = "Office In not recorded",
                        message = "Missing faceId — cannot send the match result."
                    )
                )
            }
            return
        }
        matchResultJob?.cancel()
        _uiState.update { it.copy(isPostingMatchResult = true, matchResultMessage = null) }
        matchResultJob = viewModelScope.launch {
            val request = MatchResultRequest(
                event = EVENT_IN,
                result = if (match.isMatch) RESULT_SUCCESS else RESULT_FAILURE,
                faceId = faceId,
                cosineSimilarity = match.cosineSimilarity,
                threshold = state.matchConfig.threshold,
                liveness = LivenessPayload(
                    passed = state.isLivenessVerified,
                    challengesCompleted = state.challenges.filter { it.isCompleted }.map { it.type.name },
                    spoofProbability = state.spoofResult.spoofProbability
                ),
                imageBase64 = capture?.let { encodeCaptureBase64(it) },
                capturedAt = SimpleDateFormat(ISO_OFFSET_PATTERN, Locale.US).format(Date(capturedAt)),
                device = DeviceInfoPayload(
                    deviceId = state.deviceId.ifBlank { null },
                    appVersion = BuildConfig.VERSION_NAME,
                    gateId = state.gateId
                )
            )
            pendingMatchResult = request
            sendMatchResult(request)
        }
    }

    /** Re-sends the last match result after a failed POST. */
    fun retryMatchResult() {
        val request = pendingMatchResult ?: return
        if (_uiState.value.isPostingMatchResult) return
        _uiState.update { it.copy(isPostingMatchResult = true, matchResultMessage = null) }
        matchResultJob = viewModelScope.launch { sendMatchResult(request) }
    }

    fun dismissMatchResultMessage() {
        _uiState.update { it.copy(matchResultMessage = null) }
    }

    private suspend fun sendMatchResult(request: MatchResultRequest) {
        val message = try {
            val response = ApiClient.faceApi.postMatchResult(request)
            pendingMatchResult = null
            response.toMessage(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MatchResultMessage(
                status = MatchResultStatus.FAILED,
                title = "Office In not recorded",
                message = describeMatchResultError(e)
            )
        }
        _uiState.update {
            it.copy(
                isPostingMatchResult = false,
                matchResultMessage = message.takeIf { _ -> it.currentScreen == AppScreen.VERIFICATION_SUCCESS }
            )
        }
    }

    private fun MatchResultResponse.toMessage(request: MatchResultRequest): MatchResultMessage {
        val recorded = attendanceRecorded ?: (request.result == RESULT_SUCCESS)
        return MatchResultMessage(
            status = if (recorded) MatchResultStatus.RECORDED else MatchResultStatus.NOT_RECORDED,
            title = if (recorded) "Attendance recorded" else "Attendance not recorded",
            message = message?.takeIf { it.isNotBlank() }
                ?: if (recorded) "Office In recorded" else "Face did not match. Office In was not recorded.",
            details = listOfNotNull(
                recordId?.let { "Record ID: $it" },
                recordedAt?.let { "Recorded at: $it" }
            )
        )
    }

    private fun describeMatchResultError(e: Exception): String = when (e) {
        is HttpException -> ApiClient.errorMessage(e)?.takeIf { it.isNotBlank() }
            ?: "Server rejected the result (HTTP ${e.code()})"
        is IOException -> "Cannot reach the server at ${ApiClient.baseUrl}"
        is JsonDataException -> "Unexpected response from the server."
        else -> e.message ?: "Could not send the match result."
    }

    private suspend fun encodeCaptureBase64(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        try {
            val scale = (CAPTURE_MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, CAPTURE_JPEG_QUALITY, out)
                out.toByteArray()
            }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: IllegalStateException) {
            null
        }
    }

    fun restartVerificationFlow() {
        _uiState.update {
            it.copy(
                selectedTab = AppTab.HOME,
                currentScreen = AppScreen.MAIN_DASHBOARD,
                verifiedCapturedBitmap = null,
                identityMatch = IdentityMatchResult(),
                matchResultMessage = null
            )
        }
        startOfficeIn()
    }

    fun resetChallenges() {
        initChallenges()
        biometricEngine.resetHistory()
        resetBlinkTracker()
    }

    private fun resetBlinkTracker() {
        blinkStage = 0
        blinkClosedFrames = 0
        blinkOpenFrames = 0
    }

    private fun initChallenges() {
        // MiniFASNet PAD first (mesh on), then the MediaPipe challenges from the match config.
        val challenges = _uiState.value.matchConfig.requiredChallenges.map { ChallengeStep(it) }
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
                // Blendshapes + EAR (lid height/width). Need close→open with a few frames each way
                // so brief natural blinks count and half-closed squints do not.
                val blinkL = metrics.blendshapes["eyeBlinkLeft"] ?: (1f - metrics.leftEyeOpenProb)
                val blinkR = metrics.blendshapes["eyeBlinkRight"] ?: (1f - metrics.rightEyeOpenProb)
                val earL = metrics.blendshapes["eyeAspectRatioLeft"] ?: 0.25f
                val earR = metrics.blendshapes["eyeAspectRatioRight"] ?: 0.25f
                val closedByBlend = blinkL >= BLINK_CLOSED_BLEND && blinkR >= BLINK_CLOSED_BLEND
                val closedByEar = earL <= BLINK_CLOSED_EAR && earR <= BLINK_CLOSED_EAR
                val openByBlend = blinkL <= BLINK_OPEN_BLEND && blinkR <= BLINK_OPEN_BLEND
                val openByEar = earL >= BLINK_OPEN_EAR && earR >= BLINK_OPEN_EAR
                val bothClosed = closedByBlend || closedByEar
                val bothOpen = openByBlend || openByEar

                when (blinkStage) {
                    0 -> {
                        if (bothClosed) {
                            blinkClosedFrames++
                            blinkOpenFrames = 0
                            stepProgress = (blinkClosedFrames.toFloat() / BLINK_MIN_CLOSED_FRAMES)
                                .coerceAtMost(0.5f)
                            if (blinkClosedFrames >= BLINK_MIN_CLOSED_FRAMES) {
                                blinkStage = 1
                                blinkOpenFrames = 0
                                stepProgress = 0.55f
                            }
                        } else {
                            blinkClosedFrames = 0
                            stepProgress = 0f
                        }
                    }
                    1 -> {
                        when {
                            bothOpen -> {
                                blinkOpenFrames++
                                stepProgress = (0.55f + 0.45f *
                                    (blinkOpenFrames.toFloat() / BLINK_MIN_OPEN_FRAMES))
                                    .coerceAtMost(1f)
                                if (blinkOpenFrames >= BLINK_MIN_OPEN_FRAMES) {
                                    stepProgress = 1f
                                    stepCompleted = true
                                }
                            }
                            bothClosed -> {
                                // Still closed — keep waiting for reopen; do not reset stage.
                                blinkOpenFrames = 0
                                stepProgress = 0.55f
                            }
                            else -> {
                                // Ambiguous mid-blink — do not reset closed credit.
                                blinkOpenFrames = 0
                            }
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
                resetBlinkTracker()
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
                    delay(350)
                    if (_uiState.value.isLivenessVerified && _uiState.value.isOfficeInActive) {
                        completeOfficeIn()
                    }
                    officeInCompleteJob = null
                }
            }
        } else {
            // Throttle non-blink UI churn so Compose does not rebuild every camera frame.
            val now = System.currentTimeMillis()
            val isBlink = currentStep.type == ChallengeType.BLINK_EYES
            val progressDelta = abs(stepProgress - currentStep.progress)
            if (isBlink || progressDelta >= 0.08f || now - lastChallengeUiAtMs >= 66L) {
                lastChallengeUiAtMs = now
                val updatedList = state.challenges.toMutableList()
                updatedList[state.currentChallengeIndex] = currentStep.copy(progress = stepProgress)
                val status = when {
                    !isBlink -> state.challengeStatusText
                    blinkStage == 0 -> "Blink both eyes"
                    else -> "Open your eyes"
                }
                _uiState.update {
                    it.copy(challenges = updatedList, challengeStatusText = status)
                }
            }
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

    private companion object {
        const val ENROLLED_FACE_PREFIX = "enrolled_face_"
        const val EVENT_IN = "IN"
        const val RESULT_SUCCESS = "SUCCESS"
        const val RESULT_FAILURE = "FAILURE"
        const val ISO_OFFSET_PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX"
        const val CAPTURE_MAX_SIDE = 480
        const val CAPTURE_JPEG_QUALITY = 85

        // Blink: blendshape close ≥ 0.38, EAR ≤ 0.17; reopen blend ≤ 0.28 or EAR ≥ 0.21.
        const val BLINK_CLOSED_BLEND = 0.38f
        const val BLINK_OPEN_BLEND = 0.28f
        const val BLINK_CLOSED_EAR = 0.17f
        const val BLINK_OPEN_EAR = 0.21f
        const val BLINK_MIN_CLOSED_FRAMES = 2
        const val BLINK_MIN_OPEN_FRAMES = 2
    }
}
