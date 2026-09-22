package com.example.ml

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Optional face-size / distance estimate from inter-ocular distance.
 * Not used as a verification gate — informational only.
 */
object FaceStandoff {
    const val MIN_FEET = 1.5f
    const val MAX_FEET = 2.5f

    /** Empirically tuned: IOD ~0.12 of frame width ≈ ~2 ft on a typical selfie FOV. */
    private const val REF_IOD = 0.12f
    private const val REF_FEET = 2.0f

    fun iodNormalized(leftEye: PointF?, rightEye: PointF?): Float {
        if (leftEye == null || rightEye == null) return 0f
        val dx = leftEye.x - rightEye.x
        val dy = leftEye.y - rightEye.y
        return sqrt(dx * dx + dy * dy)
    }

    fun estimateFeet(iodNormalized: Float, faceWidthNorm: Float = 0f): Float {
        val iod = when {
            iodNormalized > 0.01f -> iodNormalized
            faceWidthNorm > 0.02f -> faceWidthNorm * 0.45f
            else -> return 0f
        }
        return (REF_IOD / iod.coerceAtLeast(0.02f) * REF_FEET).coerceIn(0.5f, 8f)
    }

    fun isInRange(feet: Float): Boolean = feet in MIN_FEET..MAX_FEET

    fun guidance(feet: Float): String {
        return when {
            feet <= 0f -> "Face the camera"
            else -> "Distance ${"%.1f".format(feet)} ft"
        }
    }
}
