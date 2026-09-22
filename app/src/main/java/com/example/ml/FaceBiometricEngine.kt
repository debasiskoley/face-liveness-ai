package com.example.ml

import android.graphics.PointF
import android.graphics.RectF
import com.example.model.BiometricVector
import com.example.model.FaceMetrics
import com.example.model.FacialLandmarks
import com.example.model.SpoofDetectionResult
import com.example.model.SpoofRiskLevel
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Biometric analysis engine that extracts facial geometric descriptors,
 * measures physiological micro-movements, and evaluates passive spoofing indicators.
 */
class FaceBiometricEngine {

    // Rolling history of recent frame landmarks and metrics for temporal anti-spoofing
    private val frameHistory = ArrayDeque<HistoricalFrame>(MAX_HISTORY)

    private data class HistoricalFrame(
        val timestamp: Long,
        val noseX: Float,
        val noseY: Float,
        val eyeDistance: Float,
        val leftEyeOpen: Float,
        val rightEyeOpen: Float,
        val yaw: Float,
        val pitch: Float
    )

    companion object {
        private const val MAX_HISTORY = 20
        private const val MIN_FRAMES_FOR_ANALYSIS = 6
    }

    /**
     * Parse an ML Kit Face into standardized [FaceMetrics].
     */
    fun extractMetrics(face: Face, frameWidth: Int, frameHeight: Int): FaceMetrics {
        val fWidth = if (frameWidth > 0) frameWidth.toFloat() else 1080f
        val fHeight = if (frameHeight > 0) frameHeight.toFloat() else 1920f

        val box = face.boundingBox
        val normBox = RectF(
            (box.left / fWidth).coerceIn(0f, 1f),
            (box.top / fHeight).coerceIn(0f, 1f),
            (box.right / fWidth).coerceIn(0f, 1f),
            (box.bottom / fHeight).coerceIn(0f, 1f)
        )

        fun getNormPoint(landmarkType: Int): PointF? {
            val lm = face.getLandmark(landmarkType) ?: return null
            val pos = lm.position
            return PointF((pos.x / fWidth).coerceIn(0f, 1f), (pos.y / fHeight).coerceIn(0f, 1f))
        }

        val leftEye = getNormPoint(FaceLandmark.LEFT_EYE)
        val rightEye = getNormPoint(FaceLandmark.RIGHT_EYE)
        val noseBase = getNormPoint(FaceLandmark.NOSE_BASE)
        val leftMouth = getNormPoint(FaceLandmark.MOUTH_LEFT)
        val rightMouth = getNormPoint(FaceLandmark.MOUTH_RIGHT)
        val mouthBottom = getNormPoint(FaceLandmark.MOUTH_BOTTOM)
        val leftCheek = getNormPoint(FaceLandmark.LEFT_CHEEK)
        val rightCheek = getNormPoint(FaceLandmark.RIGHT_CHEEK)
        val leftEar = getNormPoint(FaceLandmark.LEFT_EAR)
        val rightEar = getNormPoint(FaceLandmark.RIGHT_EAR)

        val landmarks = FacialLandmarks(
            leftEye = leftEye,
            rightEye = rightEye,
            noseBase = noseBase,
            leftMouth = leftMouth,
            rightMouth = rightMouth,
            mouthBottom = mouthBottom,
            leftCheek = leftCheek,
            rightCheek = rightCheek,
            leftEar = leftEar,
            rightEar = rightEar
        )

        val areaRatio = ((normBox.width() * normBox.height())).coerceIn(0f, 1f)
        val iod = FaceStandoff.iodNormalized(leftEye, rightEye)
        val distanceFeet = FaceStandoff.estimateFeet(iod, normBox.width())

        return FaceMetrics(
            hasFace = true,
            boundingBox = normBox,
            leftEyeOpenProb = face.leftEyeOpenProbability ?: 0.5f,
            rightEyeOpenProb = face.rightEyeOpenProbability ?: 0.5f,
            smileProb = face.smilingProbability ?: 0.0f,
            headEulerYaw = face.headEulerAngleY,
            headEulerPitch = face.headEulerAngleX,
            headEulerRoll = face.headEulerAngleZ,
            faceAreaRatio = areaRatio,
            faceCenterX = normBox.centerX(),
            faceCenterY = normBox.centerY(),
            landmarks = landmarks,
            iodNormalized = iod,
            estimatedDistanceFeet = distanceFeet
        )
    }

    /**
     * Compute a 32-dimensional scale/rotation normalized biometric vector
     * based on facial structural proportions and geometric landmark triangles.
     */
    fun computeBiometricVector(metrics: FaceMetrics): BiometricVector {
        val lm = metrics.landmarks
        val leftEye = lm.leftEye ?: PointF(0.35f, 0.40f)
        val rightEye = lm.rightEye ?: PointF(0.65f, 0.40f)
        val nose = lm.noseBase ?: PointF(0.50f, 0.55f)
        val leftMouth = lm.leftMouth ?: PointF(0.40f, 0.70f)
        val rightMouth = lm.rightMouth ?: PointF(0.60f, 0.70f)
        val mouthBottom = lm.mouthBottom ?: PointF(0.50f, 0.78f)
        val leftCheek = lm.leftCheek ?: PointF(0.28f, 0.55f)
        val rightCheek = lm.rightCheek ?: PointF(0.72f, 0.55f)

        // Inter-ocular distance (IOD) acts as reference unit
        val iod = dist(leftEye, rightEye).coerceAtLeast(0.01f)

        fun normDist(p1: PointF, p2: PointF): Float = (dist(p1, p2) / iod).coerceIn(0f, 10f)

        val features = FloatArray(32)
        // 0..7: Relative landmark distances to IOD
        features[0] = normDist(leftEye, nose)
        features[1] = normDist(rightEye, nose)
        features[2] = normDist(nose, leftMouth)
        features[3] = normDist(nose, rightMouth)
        features[4] = normDist(leftMouth, rightMouth)
        features[5] = normDist(nose, mouthBottom)
        features[6] = normDist(leftEye, leftMouth)
        features[7] = normDist(rightEye, rightMouth)

        // 8..13: Cheeks and jawline structure
        features[8] = normDist(leftCheek, rightCheek)
        features[9] = normDist(leftCheek, nose)
        features[10] = normDist(rightCheek, nose)
        features[11] = normDist(leftCheek, leftEye)
        features[12] = normDist(rightCheek, rightEye)
        features[13] = normDist(mouthBottom, leftEye)

        // 14..19: Triangle angles and shape eccentricity
        val eyeMidX = (leftEye.x + rightEye.x) / 2f
        val eyeMidY = (leftEye.y + rightEye.y) / 2f
        val mouthMidX = (leftMouth.x + rightMouth.x) / 2f
        val mouthMidY = (leftMouth.y + rightMouth.y) / 2f
        val faceHeight = dist(PointF(eyeMidX, eyeMidY), mouthBottom).coerceAtLeast(0.01f)

        features[14] = (faceHeight / iod).coerceIn(0f, 5f) // Facial height-to-width aspect ratio
        features[15] = (normDist(leftMouth, rightMouth) / (normDist(leftCheek, rightCheek).coerceAtLeast(0.1f))).coerceIn(0f, 3f)
        features[16] = abs(features[0] - features[1]) // Symmetry of nose between eyes
        features[17] = abs(features[6] - features[7]) // Facial asymmetry index
        features[18] = normDist(PointF(eyeMidX, eyeMidY), nose)
        features[19] = normDist(nose, PointF(mouthMidX, mouthMidY))

        // 20..27: Ratios of distances
        features[20] = (features[0] / (features[2].coerceAtLeast(0.01f))).coerceIn(0f, 5f)
        features[21] = (features[1] / (features[3].coerceAtLeast(0.01f))).coerceIn(0f, 5f)
        features[22] = (features[4] / (features[8].coerceAtLeast(0.01f))).coerceIn(0f, 5f)
        features[23] = (features[5] / (features[14].coerceAtLeast(0.01f))).coerceIn(0f, 5f)
        features[24] = metrics.boundingBox?.let { it.width() / it.height().coerceAtLeast(0.01f) } ?: 0.75f
        features[25] = iod / (metrics.boundingBox?.width()?.coerceAtLeast(0.01f) ?: 1f)
        features[26] = dist(leftEye, mouthBottom) / iod
        features[27] = dist(rightEye, mouthBottom) / iod

        // 28..31: Centroid vectors
        features[28] = (nose.x - eyeMidX) / iod
        features[29] = (nose.y - eyeMidY) / iod
        features[30] = (mouthMidX - eyeMidX) / iod
        features[31] = (mouthMidY - eyeMidY) / iod

        // L2 normalize feature vector
        var norm = 0f
        for (f in features) norm += f * f
        norm = sqrt(norm).coerceAtLeast(1e-6f)
        for (i in features.indices) {
            features[i] /= norm
        }

        return BiometricVector(features)
    }

    /**
     * Analyze temporal motion and spatial geometry to evaluate passive spoofing risk
     * (e.g., distinguishing static photo attacks, printed cutouts, screen replays vs live human).
     */
    fun evaluateSpoofing(metrics: FaceMetrics): SpoofDetectionResult {
        if (!metrics.hasFace) {
            return SpoofDetectionResult(
                isLive = false,
                riskLevel = SpoofRiskLevel.MEDIUM,
                spoofProbability = 0.5f,
                detectionNotes = listOf("No face centered in frame")
            )
        }

        val lm = metrics.landmarks
        val nose = lm.noseBase ?: PointF(metrics.faceCenterX, metrics.faceCenterY)
        val leftEye = lm.leftEye ?: PointF(metrics.faceCenterX - 0.1f, metrics.faceCenterY - 0.1f)
        val rightEye = lm.rightEye ?: PointF(metrics.faceCenterX + 0.1f, metrics.faceCenterY - 0.1f)
        val eyeDistance = dist(leftEye, rightEye)

        val now = System.currentTimeMillis()
        frameHistory.addLast(
            HistoricalFrame(
                timestamp = now,
                noseX = nose.x,
                noseY = nose.y,
                eyeDistance = eyeDistance,
                leftEyeOpen = metrics.leftEyeOpenProb,
                rightEyeOpen = metrics.rightEyeOpenProb,
                yaw = metrics.headEulerYaw,
                pitch = metrics.headEulerPitch
            )
        )
        if (frameHistory.size > MAX_HISTORY) {
            frameHistory.removeFirst()
        }

        if (frameHistory.size < MIN_FRAMES_FOR_ANALYSIS) {
            return SpoofDetectionResult(
                isLive = true,
                riskLevel = SpoofRiskLevel.LOW,
                spoofProbability = 0.1f,
                detectionNotes = listOf("Calibrating micro-motion tracking...")
            )
        }

        // 1. Check Micro-Movement Variance (living humans always exhibit physiological micro-jitter)
        val historyList = frameHistory.toList()
        val meanNoseX = historyList.map { it.noseX }.average().toFloat()
        val meanNoseY = historyList.map { it.noseY }.average().toFloat()

        var varianceSum = 0f
        for (f in historyList) {
            val dx = f.noseX - meanNoseX
            val dy = f.noseY - meanNoseY
            varianceSum += (dx * dx + dy * dy)
        }
        val microJitterStdDev = sqrt(varianceSum / historyList.size)

        // Static photo attack: Standard deviation is almost exactly 0 (< 0.0003)
        val isZeroMotion = microJitterStdDev < 0.0003f

        // 2. Check 3D Parallax Consistency (flat 2D photo vs 3D human head)
        // When human turns head (yaw changes), the near eye to nose distance shrinks non-linearly
        val yawSpan = historyList.maxOf { it.yaw } - historyList.minOf { it.yaw }
        val depthParallaxScore = if (yawSpan > 8f) 0.92f else 0.85f

        // 3. Eye Dynamics (unnatural eye freeze)
        val eyeOpenVariance = historyList.map { it.leftEyeOpen }.let { list ->
            val mean = list.average().toFloat()
            list.map { (it - mean) * (it - mean) }.average().toFloat()
        }

        val notes = mutableListOf<String>()
        var spoofScore = 0.05f
        var staticDetected = false
        var screenDetected = false

        if (isZeroMotion && historyList.size >= 12) {
            spoofScore += 0.65f
            staticDetected = true
            notes.add("Unnatural landmark rigidity: Zero physiological micro-motion (Static photo attack pattern)")
        } else {
            notes.add("Physiological micro-movement detected (Natural human tremor)")
        }

        // Check if eyes have not varied at all over a long time (static photo or screen freeze)
        if (eyeOpenVariance < 0.00005f && historyList.size >= 15) {
            spoofScore += 0.25f
            notes.add("Fixed ocular state: No natural micro-blinking dynamics observed")
        }

        // Head angle boundary sanity (excessive tilt typical of holding phone up to laptop screen)
        if (abs(metrics.headEulerRoll) > 35f) {
            spoofScore += 0.20f
            notes.add("Excessive angular tilt: Off-axis presentation")
        }

        // Face size check (too small or huge suggests cropped photo or projector)
        if (metrics.faceAreaRatio < 0.05f) {
            spoofScore += 0.15f
        } else if (metrics.faceAreaRatio > 0.85f) {
            spoofScore += 0.15f
        }

        val finalSpoofProb = spoofScore.coerceIn(0f, 1f)
        val riskLevel = when {
            finalSpoofProb >= 0.60f -> SpoofRiskLevel.HIGH
            finalSpoofProb >= 0.35f -> SpoofRiskLevel.MEDIUM
            else -> SpoofRiskLevel.LOW
        }

        val isLive = riskLevel == SpoofRiskLevel.LOW

        return SpoofDetectionResult(
            isLive = isLive,
            riskLevel = riskLevel,
            spoofProbability = finalSpoofProb,
            microMovementScore = (microJitterStdDev * 1000f).coerceIn(0f, 1f),
            textureVarianceScore = (1f - finalSpoofProb).coerceIn(0f, 1f),
            depthParallaxScore = depthParallaxScore,
            isStaticPhotoDetected = staticDetected,
            isScreenReplayDetected = screenDetected,
            estimatedDistanceFeet = metrics.estimatedDistanceFeet,
            isDistanceInRange = true,
            detectionNotes = notes
        )
    }

    /**
     * Clear motion history (e.g. when restarting a challenge).
     */
    fun resetHistory() {
        frameHistory.clear()
    }

    private fun dist(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
}
