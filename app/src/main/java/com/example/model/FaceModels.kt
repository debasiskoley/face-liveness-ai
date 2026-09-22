package com.example.model

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.sqrt

/**
 * Normalized 2D point on camera frame (0.0f .. 1.0f).
 */
data class NormalizedPoint(val x: Float, val y: Float)

/**
 * Facial landmark positions detected on the frame.
 */
data class FacialLandmarks(
    val leftEye: PointF? = null,
    val rightEye: PointF? = null,
    val noseBase: PointF? = null,
    val leftMouth: PointF? = null,
    val rightMouth: PointF? = null,
    val mouthBottom: PointF? = null,
    val leftCheek: PointF? = null,
    val rightCheek: PointF? = null,
    val leftEar: PointF? = null,
    val rightEar: PointF? = null,
    val contourPoints: List<PointF> = emptyList()
)

/**
 * Real-time biometric metrics calculated per frame.
 */
data class FaceMetrics(
    val hasFace: Boolean = false,
    val boundingBox: RectF? = null,
    val leftEyeOpenProb: Float = 0f,
    val rightEyeOpenProb: Float = 0f,
    val smileProb: Float = 0f,
    val headEulerYaw: Float = 0f,    // Left (< 0) / Right (> 0) rotation
    val headEulerPitch: Float = 0f,  // Looking down (< 0) / Looking up (> 0)
    val headEulerRoll: Float = 0f,   // Tilting head
    val faceAreaRatio: Float = 0f,   // Face area compared to frame area
    val faceCenterX: Float = 0.5f,
    val faceCenterY: Float = 0.5f,
    val landmarks: FacialLandmarks = FacialLandmarks(),
    val meshPoints: List<PointF> = emptyList(), // MediaPipe 468+ Face Mesh vertices
    val blendshapes: Map<String, Float> = emptyMap(), // MediaPipe 52 facial blendshapes
    val iodNormalized: Float = 0f,
    val estimatedDistanceFeet: Float = 0f
)

/**
 * Interactive active liveness challenges.
 */
enum class ChallengeType(val title: String, val instruction: String, val icon: String) {
    CENTER_FACE("Center Face", "Align your face in the oval", "🎯"),
    BLINK_EYES("Blink Eyes", "Blink both eyes naturally", "👁️"),
    SMILE("Smile", "Smile clearly to verify facial elasticity", "😊"),
    TURN_LEFT("Turn Left", "Slowly turn your head slightly to the left", "⬅️"),
    TURN_RIGHT("Turn Right", "Slowly turn your head slightly to the right", "➡️"),
    NOD_HEAD("Nod Head", "Nod your head slightly up and down", "↕️")
}

data class ChallengeStep(
    val type: ChallengeType,
    val isCompleted: Boolean = false,
    val progress: Float = 0f // 0.0 to 1.0
)

/**
 * Spoof detection risk evaluation.
 */
enum class SpoofRiskLevel(val label: String, val colorHex: Long) {
    LOW("Low Spoof Risk (Authentic Human)", 0xFF10B981),
    MEDIUM("Medium Risk (Unusual Dynamics)", 0xFFF59E0B),
    HIGH("High Spoof Risk (Potential Attack)", 0xFFEF4444)
}

data class SpoofDetectionResult(
    val isLive: Boolean = true,
    val riskLevel: SpoofRiskLevel = SpoofRiskLevel.LOW,
    val spoofProbability: Float = 0.05f, // 0.0 (live) to 1.0 (spoof)
    val microMovementScore: Float = 0.88f,
    val textureVarianceScore: Float = 0.85f,
    val depthParallaxScore: Float = 0.90f,
    val isStaticPhotoDetected: Boolean = false,
    val isScreenReplayDetected: Boolean = false,
    val estimatedDistanceFeet: Float = 0f,
    val isDistanceInRange: Boolean = false,
    val detectionNotes: List<String> = listOf("Physiological micro-movement verified")
)

/**
 * Standardized 32-dimensional biometric feature descriptor vector
 * calculated from facial geometry, ratios, and relative distances.
 */
data class BiometricVector(
    val features: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BiometricVector) return false
        return features.contentEquals(other.features)
    }

    override fun hashCode(): Int {
        return features.contentHashCode()
    }

    /**
     * Cosine similarity between this vector and another vector (-1.0 to 1.0).
     * High values (> 0.75 - 0.85) indicate the same person.
     */
    fun cosineSimilarity(other: BiometricVector): Float {
        if (features.size != other.features.size || features.isEmpty()) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in features.indices) {
            dotProduct += features[i] * other.features[i]
            normA += features[i] * features[i]
            normB += other.features[i] * other.features[i]
        }
        if (normA <= 0f || normB <= 0f) return 0f
        val sim = dotProduct / (sqrt(normA) * sqrt(normB))
        return sim.coerceIn(-1f, 1f)
    }

    /**
     * Euclidean distance (lower is more similar).
     */
    fun euclideanDistance(other: BiometricVector): Float {
        if (features.size != other.features.size) return Float.MAX_VALUE
        var sum = 0f
        for (i in features.indices) {
            val diff = features[i] - other.features[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    companion object {
        val EMPTY = BiometricVector(FloatArray(0))

        fun isIdentityTemplate(vector: BiometricVector): Boolean {
            return vector.features.size >= 128
        }
    }
}

/**
 * Reference profile identity for biometric matching.
 */
data class EnrolledProfile(
    val id: String,
    val name: String,
    val role: String = "Authorized Person",
    val enrolledDate: String,
    val vector: BiometricVector,
    val avatarColor: Long = 0xFF0284C7,
    val notes: String = "",
    val photoUri: String? = null,
    val address: String = "",
    val mobileNo: String = "",
    val email: String = ""
)

/**
 * 1:N identity check of a live crop against enrolled ArcFace templates.
 */
data class IdentityMatchResult(
    val isMatch: Boolean = false,
    val profile: EnrolledProfile? = null,
    val cosineSimilarity: Float = 0f,
    val matchPercentage: Int = 0,
    val statusLabel: String = "Not enrolled"
)

/**
 * Verification audit log entry.
 */
data class VerificationLogEntry(
    val id: String,
    val timestamp: Long,
    val formattedTime: String,
    val profileMatched: String,
    val matchScore: Int,
    val livenessPassed: Boolean,
    val spoofRisk: SpoofRiskLevel,
    val wasMediaPipeMeshVerified: Boolean = true
)
