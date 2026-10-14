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

data class EnrollFaceUiState(
    val metrics: FaceMetrics = FaceMetrics(),
    val spoofResult: SpoofDetectionResult = SpoofDetectionResult(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val result: EnrollFaceResponse? = null
) {
    val canCapture: Boolean
        get() = metrics.hasFace &&
            spoofResult.riskLevel != SpoofRiskLevel.HIGH &&
            !isSubmitting &&
            result == null
}

class EnrollFaceViewModel(application: Application) : AndroidViewModel(application) {

    private val embedder by lazy { ArcFaceEmbedder.getInstance(application) }
    private val biometricEngine = FaceBiometricEngine()

    private val _uiState = MutableStateFlow(EnrollFaceUiState())
    val uiState: StateFlow<EnrollFaceUiState> = _uiState.asStateFlow()

    @Volatile
    private var latestFrame: Bitmap? = null
    private var enrollJob: Job? = null

    fun reset() {
        enrollJob?.cancel()
        enrollJob = null
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
                spoofResult = result.spoofResult
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
                spoofResult = biometricEngine.evaluateSpoofing(metrics)
            )
        }
    }

    fun enroll(mobileNumber: String) {
        if (!_uiState.value.canCapture) return
        val frame = latestFrame
        if (frame == null) {
            _uiState.update { it.copy(errorMessage = "No camera frame yet. Hold still and try again.") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        enrollJob = viewModelScope.launch {
            try {
                val response = submitEnrollment(mobileNumber, frame)
                _uiState.update { it.copy(isSubmitting = false, result = response) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, errorMessage = describeError(e)) }
            }
        }
    }

    private suspend fun submitEnrollment(mobileNumber: String, frame: Bitmap): EnrollFaceResponse {
        val embedder = withContext(Dispatchers.Default) { embedder }
        check(embedder.isArcFaceLoaded) {
            "ArcFace model is not loaded, so a 512-D face embedding cannot be created."
        }
        val face = embedder.embedFace(frame)
            ?: throw IllegalStateException("Face not clear. Look straight at the camera and try again.")
        val features = face.vector.features

        val request = EnrollFaceRequest(
            mobileNumber = mobileNumber,
            face = FacePayload(
                embedding = features.toList(),
                embeddingDim = features.size,
                normalized = true,
                imageBase64 = encodeJpegBase64(face.alignedFace)
            )
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
    }
}
