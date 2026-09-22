package com.example.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.example.ml.FaceStandoff
import com.example.ml.SilentFaceAntiSpoof
import com.example.model.BiometricVector
import com.example.model.FaceMetrics
import com.example.model.FacialLandmarks
import com.example.model.SpoofDetectionResult
import com.example.model.SpoofRiskLevel
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * MediaPipe Face Landmarker for tracking, blink/center challenges, and mesh overlay.
 * Anti-spoofing is MiniFASNet Silent-Face only (not MediaPipe depth heuristics).
 */
class MediaPipeFaceLandmarkerHelper(
    private val context: Context
) {
    companion object {
        private const val TAG = "MediaPipeFaceLandmarker"
        private const val MODEL_NAME = "face_landmarker.task"
    }

    private var faceLandmarker: FaceLandmarker? = null
    var isInitialized: Boolean = false
        private set

    private val silentFacePad = SilentFaceAntiSpoof.getInstance(context)
    private var padFrameCounter = 0
    private var lastSilentPad: SilentFaceAntiSpoof.Result? = null

    data class MediaPipeAnalysisResult(
        val hasFace: Boolean,
        val metrics: FaceMetrics,
        val spoofResult: SpoofDetectionResult,
        val biometricVector: BiometricVector,
        val meshPoints: List<PointF>, // 468+ normalized 2D landmark points for Mesh drawing
        val blendshapes: Map<String, Float>
    )

    init {
        setupFaceLandmarker()
    }

    private fun setupFaceLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_NAME)
                .setDelegate(Delegate.CPU) // CPU is guaranteed to work across all devices/emulators
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            isInitialized = true
            Log.i(TAG, "MediaPipe Face Landmarker initialized successfully.")
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing MediaPipe Face Landmarker", t)
            isInitialized = false
        }
    }

    /**
     * Detects MediaPipe Face Landmarker & Face Mesh on a camera frame bitmap.
     */
    fun processBitmap(bitmap: Bitmap, isFrontCamera: Boolean): MediaPipeAnalysisResult {
        val landmarker = faceLandmarker
        if (landmarker == null || !isInitialized) {
            return emptyResult("MediaPipe initializing...")
        }

        try {
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
            val result: FaceLandmarkerResult = landmarker.detect(mpImage)

            val faceLandmarksList = result.faceLandmarks()
            if (faceLandmarksList.isEmpty() || faceLandmarksList[0].isEmpty()) {
                return emptyResult("Align face inside MediaPipe mesh oval")
            }

            val landmarks = faceLandmarksList[0] // 478 points
            val blendshapesMap = mutableMapOf<String, Float>()
            val blendshapeCategories = result.faceBlendshapes().orElse(null)
            if (blendshapeCategories != null && blendshapeCategories.isNotEmpty()) {
                for (category in blendshapeCategories[0]) {
                    blendshapesMap[category.categoryName()] = category.score()
                }
            }

            // Extract blendshapes
            val eyeBlinkLeft = blendshapesMap["eyeBlinkLeft"] ?: 0.0f
            val eyeBlinkRight = blendshapesMap["eyeBlinkRight"] ?: 0.0f
            val mouthSmileLeft = blendshapesMap["mouthSmileLeft"] ?: 0.0f
            val mouthSmileRight = blendshapesMap["mouthSmileRight"] ?: 0.0f
            val jawOpen = blendshapesMap["jawOpen"] ?: 0.0f

            val leftEyeOpenProb = (1.0f - eyeBlinkLeft).coerceIn(0f, 1f)
            val rightEyeOpenProb = (1.0f - eyeBlinkRight).coerceIn(0f, 1f)
            val smileProb = ((mouthSmileLeft + mouthSmileRight) / 2f).coerceIn(0f, 1f)

            // Key landmarks by MediaPipe canonical indices
            // 1: Nose tip, 4: Nose base
            // 33: Left eye outer, 133: Left eye inner, 159: Left eye top, 145: Left eye bottom
            // 263: Right eye outer, 362: Right eye inner, 386: Right eye top, 374: Right eye bottom
            // 61: Mouth left corner, 291: Mouth right corner, 17: Mouth bottom center, 0: Mouth top center
            // 234: Left cheek / temple, 454: Right cheek / temple
            // 152: Chin, 10: Top forehead
            // 468: Left iris center, 473: Right iris center (if 478 model)
            val noseTip = landmarks[1]
            val noseBase = landmarks[4]
            val leftEye = if (landmarks.size > 468) landmarks[468] else landmarks[133]
            val rightEye = if (landmarks.size > 473) landmarks[473] else landmarks[362]
            val mouthLeft = landmarks[61]
            val mouthRight = landmarks[291]
            val mouthBottom = landmarks[17]
            val leftCheek = landmarks[234]
            val rightCheek = landmarks[454]
            val chin = landmarks[152]
            val forehead = landmarks[10]

            // Calculate Bounding Box from 468 landmarks
            var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
            var minZ = 100f; var maxZ = -100f
            val allPoints2D = ArrayList<PointF>(landmarks.size)

            for (lm in landmarks) {
                val x = lm.x().coerceIn(0f, 1f)
                val y = lm.y().coerceIn(0f, 1f)
                val z = lm.z()
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (z < minZ) minZ = z
                if (z > maxZ) maxZ = z
                allPoints2D.add(PointF(x, y))
            }

            val box = RectF(minX, minY, maxX, maxY)
            val faceArea = (box.width() * box.height()).coerceIn(0f, 1f)

            // 3D Depth span between nose tip and temples
            // Genuine human 3D face has natural depth relief (nose is physically in front of ears/cheeks)
            // 2D printed photo or flat screen has zero depth variation!
            val zDepthSpan = abs(noseTip.z() - ((leftCheek.z() + rightCheek.z()) / 2f))

            // Compute head rotation angles (Euler angles in degrees) from 3D MediaPipe coordinates
            val dx = rightEye.x() - leftEye.x()
            val dy = rightEye.y() - leftEye.y()
            val dz = rightEye.z() - leftEye.z()
            val rollDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

            // Yaw angle from nose to eye midpoint
            val eyeMidX = (leftEye.x() + rightEye.x()) / 2f
            val eyeDist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val yawDeg = ((noseTip.x() - eyeMidX) / eyeDist) * 90f

            // Pitch angle from eye midpoint to mouth midpoint
            val mouthMidY = (mouthLeft.y() + mouthRight.y()) / 2f
            val pitchDeg = ((noseTip.y() - ((leftEye.y() + mouthMidY) / 2f)) / eyeDist) * 75f

            val facialLandmarks = FacialLandmarks(
                leftEye = PointF(leftEye.x(), leftEye.y()),
                rightEye = PointF(rightEye.x(), rightEye.y()),
                noseBase = PointF(noseBase.x(), noseBase.y()),
                leftMouth = PointF(mouthLeft.x(), mouthLeft.y()),
                rightMouth = PointF(mouthRight.x(), mouthRight.y()),
                mouthBottom = PointF(mouthBottom.x(), mouthBottom.y()),
                leftCheek = PointF(leftCheek.x(), leftCheek.y()),
                rightCheek = PointF(rightCheek.x(), rightCheek.y())
            )

            val iod = FaceStandoff.iodNormalized(
                PointF(leftEye.x(), leftEye.y()),
                PointF(rightEye.x(), rightEye.y())
            )
            val distanceFeet = FaceStandoff.estimateFeet(iod, box.width())

            // Once MiniFASNet clears, latch and skip PAD so blink tracking stays responsive.
            val padAlreadyCleared = lastSilentPad?.available == true && lastSilentPad?.isReal == true
            val silentPad = if (padAlreadyCleared) {
                lastSilentPad!!
            } else {
                padFrameCounter++
                if (padFrameCounter % 2 == 0 || lastSilentPad == null) {
                    silentFacePad.evaluate(bitmap, box).also { lastSilentPad = it }
                } else {
                    lastSilentPad!!
                }
            }
            val padCleared = silentPad.available && silentPad.isReal

            val faceMetrics = FaceMetrics(
                hasFace = true,
                boundingBox = box,
                leftEyeOpenProb = leftEyeOpenProb,
                rightEyeOpenProb = rightEyeOpenProb,
                smileProb = smileProb,
                headEulerYaw = yawDeg,
                headEulerPitch = pitchDeg,
                headEulerRoll = rollDeg,
                faceAreaRatio = faceArea,
                faceCenterX = box.centerX(),
                faceCenterY = box.centerY(),
                landmarks = facialLandmarks,
                iodNormalized = iod,
                estimatedDistanceFeet = distanceFeet,
                meshPoints = if (padCleared) emptyList() else allPoints2D,
                blendshapes = blendshapesMap
            )

            val spoofResult = evaluateMiniFasPad(
                zDepthSpan = zDepthSpan,
                distanceFeet = distanceFeet,
                silentPad = silentPad
            )

            val biometricVector = computeMediaPipeBiometricVector(landmarks, eyeDist)

            return MediaPipeAnalysisResult(
                hasFace = true,
                metrics = faceMetrics,
                spoofResult = spoofResult,
                biometricVector = biometricVector,
                meshPoints = if (padCleared) emptyList() else allPoints2D,
                blendshapes = blendshapesMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error running MediaPipe Face Landmarker", e)
            return emptyResult("MediaPipe processing error: ${e.message}")
        }
    }

    /**
     * Computes invariant 32-dimensional biometric feature embedding
     * from MediaPipe's 468+ high-density 3D face mesh coordinates.
     */
    private fun computeMediaPipeBiometricVector(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        eyeDist: Float
    ): BiometricVector {
        val features = FloatArray(32)
        val iod = eyeDist.coerceAtLeast(0.01f)

        fun d(i1: Int, i2: Int): Float {
            val p1 = landmarks[i1]
            val p2 = landmarks[i2]
            val dx = p1.x() - p2.x()
            val dy = p1.y() - p2.y()
            val dz = p1.z() - p2.z()
            return (sqrt(dx * dx + dy * dy + dz * dz) / iod).coerceIn(0f, 10f)
        }

        // Structural facial distance ratios across MediaPipe mesh:
        // 1: Nose tip, 33: Left eye outer, 263: Right eye outer
        // 61: Mouth left, 291: Mouth right, 17: Mouth bottom, 0: Mouth top
        // 234: Left cheek, 454: Right cheek, 152: Chin, 10: Forehead
        features[0] = d(33, 1)    // Left eye outer to nose tip
        features[1] = d(263, 1)   // Right eye outer to nose tip
        features[2] = d(1, 61)    // Nose tip to mouth left
        features[3] = d(1, 291)   // Nose tip to mouth right
        features[4] = d(61, 291)  // Mouth width
        features[5] = d(0, 17)    // Mouth height
        features[6] = d(1, 152)   // Nose to chin
        features[7] = d(10, 1)    // Forehead to nose tip
        features[8] = d(234, 454) // Face width (temple to temple)
        features[9] = d(234, 1)   // Left cheek to nose
        features[10] = d(454, 1)  // Right cheek to nose
        features[11] = d(10, 152) // Total facial height
        features[12] = d(33, 61)  // Left eye to left mouth
        features[13] = d(263, 291)// Right eye to right mouth
        features[14] = features[11] / (features[8].coerceAtLeast(0.01f)) // Aspect ratio
        features[15] = features[4] / (features[8].coerceAtLeast(0.01f))  // Mouth-to-face width ratio

        // 16..23: Philtrum, jawline and orbital ratios
        // 133: Left eye inner, 362: Right eye inner
        features[16] = d(133, 362) // Inter-canthal distance
        features[17] = d(1, 0)     // Philtrum length (nose to upper lip)
        features[18] = d(17, 152)  // Mental crease to chin
        features[19] = d(159, 145) // Left eye height
        features[20] = d(386, 374) // Right eye height
        features[21] = d(70, 300)  // Eyebrow outer distance
        features[22] = d(107, 336) // Eyebrow inner distance
        features[23] = abs(features[0] - features[1]) // Nasal symmetry index

        // 24..31: 3D Z-depth profile ratios (unique to MediaPipe 3D mesh)
        val noseZ = landmarks[1].z()
        val leftEarZ = landmarks[234].z()
        val rightEarZ = landmarks[454].z()
        val chinZ = landmarks[152].z()
        features[24] = ((noseZ - leftEarZ) / iod).coerceIn(-5f, 5f)
        features[25] = ((noseZ - rightEarZ) / iod).coerceIn(-5f, 5f)
        features[26] = ((noseZ - chinZ) / iod).coerceIn(-5f, 5f)
        features[27] = d(33, 152)  // Left eye to chin
        features[28] = d(263, 152) // Right eye to chin
        features[29] = features[16] / (features[4].coerceAtLeast(0.01f))
        features[30] = features[6] / (features[7].coerceAtLeast(0.01f))
        features[31] = features[12] / (features[13].coerceAtLeast(0.01f))

        // L2 Normalize
        var norm = 0f
        for (f in features) norm += f * f
        norm = sqrt(norm).coerceAtLeast(1e-6f)
        for (i in features.indices) features[i] /= norm

        return BiometricVector(features)
    }

    /** MiniFASNet Silent-Face is the only PAD gate. */
    private fun evaluateMiniFasPad(
        zDepthSpan: Float,
        distanceFeet: Float,
        silentPad: SilentFaceAntiSpoof.Result?
    ): SpoofDetectionResult {
        val notes = mutableListOf<String>()
        val miniFasPass = silentPad?.available == true && silentPad.isReal
        val miniFasFail = silentPad?.available == true && !silentPad.isReal
        val padMissing = silentPad == null || !silentPad.available

        when {
            padMissing -> {
                notes.add("PAD unavailable — cannot verify live face")
                notes.add(silentPad?.label ?: "MiniFASNet model missing")
            }
            miniFasFail -> {
                notes.add("VIDEO / PHOTO SPOOF DETECTED")
                notes.add(silentPad!!.label)
            }
            miniFasPass -> notes.add(silentPad!!.label)
        }

        val spoofProbability = when {
            miniFasPass -> (1f - (silentPad?.realProbability ?: 1f)).coerceIn(0f, 0.20f)
            miniFasFail -> maxOf(silentPad?.spoofProbability ?: 0.9f, 0.85f)
            else -> 0.90f
        }
        val riskLevel = when {
            miniFasPass -> SpoofRiskLevel.LOW
            else -> SpoofRiskLevel.HIGH
        }
        val isLive = miniFasPass

        if (!isLive) {
            Log.i(TAG, "PAD fail silent=${silentPad?.label} score=$spoofProbability")
        }

        return SpoofDetectionResult(
            isLive = isLive,
            riskLevel = riskLevel,
            spoofProbability = spoofProbability,
            microMovementScore = if (isLive) 0.92f else 0.08f,
            textureVarianceScore = if (isLive) 0.85f else 0.20f,
            depthParallaxScore = (zDepthSpan * 18f).coerceIn(0f, 1f),
            isStaticPhotoDetected = miniFasFail,
            isScreenReplayDetected = miniFasFail,
            estimatedDistanceFeet = distanceFeet,
            isDistanceInRange = true,
            detectionNotes = notes.distinct()
        )
    }

    private fun emptyResult(message: String): MediaPipeAnalysisResult {
        return MediaPipeAnalysisResult(
            hasFace = false,
            metrics = FaceMetrics(hasFace = false),
            spoofResult = SpoofDetectionResult(
                isLive = false,
                riskLevel = SpoofRiskLevel.MEDIUM,
                spoofProbability = 0.5f,
                detectionNotes = listOf(message)
            ),
            biometricVector = BiometricVector.EMPTY,
            meshPoints = emptyList(),
            blendshapes = emptyMap()
        )
    }

    fun reset() {
        lastSilentPad = null
        padFrameCounter = 0
    }

    fun close() {
        try {
            faceLandmarker?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Error closing FaceLandmarker", t)
        }
        faceLandmarker = null
        isInitialized = false
        lastSilentPad = null
        padFrameCounter = 0
    }
}
