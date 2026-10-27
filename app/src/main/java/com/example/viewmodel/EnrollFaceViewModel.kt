package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ApiClient
import com.example.data.remote.EnrollFaceRequest
import com.example.data.remote.EnrollFaceResponse
import com.example.data.remote.FacePayload
import com.example.mediapipe.MediaPipeFaceLandmarkerHelper
import com.example.ml.ArcFaceEmbedder
import com.example.ml.FaceBiometricEngine
import com.example.model.EnrollPose
import com.example.model.FaceMetrics
import com.example.model.SpoofDetectionResult
import com.example.model.SpoofRiskLevel
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.abs

data class CapturedPoseTemplate(
    val pose: EnrollPose,
    val payload: FacePayload
)

data class EnrollFaceUiState(
    val metrics: FaceMetrics = FaceMetrics(),
    val spoofResult: SpoofDetectionResult = SpoofDetectionResult(),
    val currentPoseIndex: Int = 0,
    val captured: Map<EnrollPose, CapturedPoseTemplate> = emptyMap(),
    val isSubmitting: Boolean = false,
    val isCapturingPose: Boolean = false,
    val errorMessage: String? = null,
    val hintMessage: String? = null,
    val result: EnrollFaceResponse? = null
) {
    val poses: List<EnrollPose> get() = EnrollPose.ENROLL_ORDER

    val currentPose: EnrollPose
        get() = poses.getOrElse(currentPoseIndex) { EnrollPose.FRONT }

    val allPosesCaptured: Boolean
        get() = poses.all { captured.containsKey(it) }

    val capturedCount: Int
        get() = captured.size

    val poseMatchesCurrent: Boolean
        get() = EnrollFaceViewModel.poseSatisfied(metrics, currentPose)

    val canCapturePose: Boolean
        get() = metrics.hasFace &&
            spoofResult.riskLevel != SpoofRiskLevel.HIGH &&
            poseMatchesCurrent &&
            !isSubmitting &&
            !isCapturingPose &&
            result == null &&
            !captured.containsKey(currentPose)

    val canSubmit: Boolean
        get() = allPosesCaptured && !isSubmitting && result == null
}

class EnrollFaceViewModel(application: Application) : AndroidViewModel(application) {

    private val embedder by lazy { ArcFaceEmbedder.getInstance(application) }
    private val biometricEngine = FaceBiometricEngine()

    private val _uiState = MutableStateFlow(EnrollFaceUiState())
    val uiState: StateFlow<EnrollFaceUiState> = _uiState.asStateFlow()

    @Volatile
    private var latestFrame: Bitmap? = null
    private var enrollJob: Job? = null
    private var captureJob: Job? = null

    fun reset() {
        enrollJob?.cancel()
        captureJob?.cancel()
        enrollJob = null
        captureJob = null
        latestFrame = null
        biometricEngine.resetHistory()
        _uiState.value = EnrollFaceUiState()
    }

    fun onMediaPipeDetected(
        result: MediaPipeFaceLandmarkerHelper.MediaPipeAnalysisResult,
        frame: Bitmap?
    ) {
        if (result.hasFace && frame != null) latestFrame = frame
        _uiState.update {
            it.copy(
                metrics = result.metrics.copy(
                    meshPoints = result.meshPoints,
                    blendshapes = result.blendshapes
                ),
                spoofResult = result.spoofResult,
                hintMessage = null
            )
        }
    }

    fun onFaceDetected(face: Face?, frameWidth: Int, frameHeight: Int, frame: Bitmap?) {
        if (face == null) {
            _uiState.update { it.copy(metrics = FaceMetrics(hasFace = false)) }
            return
        }
        if (frame != null) latestFrame = frame
        val metrics = biometricEngine.extractMetrics(face, frameWidth, frameHeight)
        _uiState.update {
            it.copy(
                metrics = metrics,
                spoofResult = biometricEngine.evaluateSpoofing(metrics),
                hintMessage = null
            )
        }
    }

    fun selectPose(index: Int) {
        val poses = EnrollPose.ENROLL_ORDER
        if (index !in poses.indices) return
        if (_uiState.value.isSubmitting || _uiState.value.isCapturingPose) return
        _uiState.update {
            it.copy(currentPoseIndex = index, errorMessage = null, hintMessage = null)
        }
    }

    /** Capture ArcFace template for the current guided pose, then advance. */
    fun captureCurrentPose() {
        val state = _uiState.value
        if (!state.canCapturePose) {
            _uiState.update {
                it.copy(hintMessage = poseHint(state.metrics, state.currentPose, state.spoofResult))
            }
            return
        }
        val frame = latestFrame
        if (frame == null) {
            _uiState.update { it.copy(errorMessage = "No camera frame yet. Hold still and try again.") }
            return
        }
        val pose = state.currentPose
        _uiState.update { it.copy(isCapturingPose = true, errorMessage = null, hintMessage = null) }
        captureJob = viewModelScope.launch {
            try {
                val template = withContext(Dispatchers.Default) {
                    buildPoseTemplate(pose, frame)
                }
                _uiState.update { current ->
                    val nextCaptured = current.captured + (pose to template)
                    val nextIndex = nextUncapturedIndex(nextCaptured, current.currentPoseIndex)
                    current.copy(
                        isCapturingPose = false,
                        captured = nextCaptured,
                        currentPoseIndex = nextIndex,
                        hintMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCapturingPose = false,
                        errorMessage = e.message ?: "Could not capture this pose."
                    )
                }
            }
        }
    }

    fun retakePose(pose: EnrollPose) {
        if (_uiState.value.isSubmitting || _uiState.value.isCapturingPose) return
        val index = EnrollPose.ENROLL_ORDER.indexOf(pose).coerceAtLeast(0)
        _uiState.update {
            it.copy(
                captured = it.captured - pose,
                currentPoseIndex = index,
                errorMessage = null,
                hintMessage = null,
                result = null
            )
        }
    }

    fun enroll(mobileNumber: String) {
        val state = _uiState.value
        if (!state.canSubmit) {
            _uiState.update {
                it.copy(
                    errorMessage = if (!state.allPosesCaptured) {
                        "Capture all ${EnrollPose.ENROLL_ORDER.size} poses before enrolling."
                    } else {
                        it.errorMessage
                    }
                )
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        enrollJob = viewModelScope.launch {
            try {
                val response = submitEnrollment(mobileNumber, state.captured)
                _uiState.update { it.copy(isSubmitting = false, result = response) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, errorMessage = describeError(e)) }
            }
        }
    }

    private suspend fun buildPoseTemplate(pose: EnrollPose, frame: Bitmap): CapturedPoseTemplate {
        val embedder = withContext(Dispatchers.Default) { embedder }
        check(embedder.isArcFaceLoaded) {
            "ArcFace model is not loaded, so a 512-D face embedding cannot be created."
        }
        val face = embedder.embedFace(frame)
            ?: throw IllegalStateException("Face not clear for ${pose.title}. Adjust and try again.")
        val features = face.vector.features
        val payload = FacePayload(
            pose = pose.apiValue,
            embedding = features.toList(),
            embeddingDim = features.size,
            normalized = true,
            imageBase64 = encodeJpegBase64(face.alignedFace)
        )
        return CapturedPoseTemplate(pose = pose, payload = payload)
    }

    private suspend fun submitEnrollment(
        mobileNumber: String,
        captured: Map<EnrollPose, CapturedPoseTemplate>
    ): EnrollFaceResponse {
        val ordered = EnrollPose.ENROLL_ORDER.mapNotNull { captured[it]?.payload }
        require(ordered.size == EnrollPose.ENROLL_ORDER.size) {
            "Missing pose templates."
        }
        val request = EnrollFaceRequest(
            mobileNumber = mobileNumber,
            faces = ordered
        )
        return ApiClient.faceApi.enroll(request)
    }

    private fun encodeJpegBase64(bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun describeError(e: Exception): String = when (e) {
        is HttpException -> {
            val serverMessage = ApiClient.errorMessage(e)
            when {
                !serverMessage.isNullOrBlank() -> serverMessage
                e.code() == 404 -> "No employee found for this mobile number."
                e.code() == 409 -> "This mobile number already has an enrolled face."
                else -> "Enroll failed (HTTP ${e.code()})"
            }
        }
        is IOException -> "Cannot reach the server at ${ApiClient.baseUrl}"
        is IllegalArgumentException -> "Invalid API base URL: ${ApiClient.baseUrl}"
        else -> e.message ?: "Enroll failed."
    }

    override fun onCleared() {
        super.onCleared()
        enrollJob?.cancel()
        captureJob?.cancel()
    }

    companion object {
        private const val FRONT_YAW_MAX = 12f
        private const val FRONT_PITCH_MAX = 12f
        private const val SIDE_YAW_MIN = 14f
        private const val VERTICAL_PITCH_MIN = 10f

        fun poseSatisfied(metrics: FaceMetrics, pose: EnrollPose): Boolean {
            if (!metrics.hasFace) return false
            val yaw = metrics.headEulerYaw
            val pitch = metrics.headEulerPitch
            return when (pose) {
                EnrollPose.FRONT -> abs(yaw) < FRONT_YAW_MAX && abs(pitch) < FRONT_PITCH_MAX
                // Same signs as Record Presence TURN_LEFT / TURN_RIGHT challenges.
                EnrollPose.LEFT -> yaw > SIDE_YAW_MIN
                EnrollPose.RIGHT -> yaw < -SIDE_YAW_MIN
                EnrollPose.UP -> pitch > VERTICAL_PITCH_MIN
                EnrollPose.DOWN -> pitch < -VERTICAL_PITCH_MIN
            }
        }

        fun poseHint(
            metrics: FaceMetrics,
            pose: EnrollPose,
            spoof: SpoofDetectionResult
        ): String {
            if (!metrics.hasFace) return "Position your face inside the frame"
            if (spoof.riskLevel == SpoofRiskLevel.HIGH) {
                return spoof.detectionNotes.firstOrNull() ?: "Possible spoof detected"
            }
            if (poseSatisfied(metrics, pose)) return "Hold still, then tap Capture pose"
            return when (pose) {
                EnrollPose.FRONT -> "Face the camera straight on"
                EnrollPose.LEFT -> "Turn a bit more to your left"
                EnrollPose.RIGHT -> "Turn a bit more to your right"
                EnrollPose.UP -> "Tilt your chin a bit higher"
                EnrollPose.DOWN -> "Tilt your chin a bit lower"
            }
        }

        private fun nextUncapturedIndex(
            captured: Map<EnrollPose, CapturedPoseTemplate>,
            currentIndex: Int
        ): Int {
            val poses = EnrollPose.ENROLL_ORDER
            for (i in currentIndex + 1 until poses.size) {
                if (!captured.containsKey(poses[i])) return i
            }
            for (i in poses.indices) {
                if (!captured.containsKey(poses[i])) return i
            }
            return currentIndex.coerceIn(0, poses.lastIndex)
        }
    }
}
