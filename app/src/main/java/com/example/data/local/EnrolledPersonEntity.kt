package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ml.ArcFaceEmbedder
import com.example.model.BiometricVector
import com.example.model.EnrolledProfile

@Entity(tableName = "enrolled_persons")
data class EnrolledPersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String = "",
    val mobileNo: String = "",
    val email: String = "",
    val profilePictureUri: String? = null,
    val role: String = "Authorized Person",
    val enrolledDate: String = "",
    val vectorFeatures: String = "", // Comma-separated ArcFace embedding (512 floats)
    val avatarColor: Long = 0xFF0284C7,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Converts Room database entity to application domain model.
 */
fun EnrolledPersonEntity.toEnrolledProfile(): EnrolledProfile {
    val floatArray = if (vectorFeatures.isNotBlank()) {
        vectorFeatures.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
    } else FloatArray(32)

    val vec = if (floatArray.size >= ArcFaceEmbedder.MIN_TEMPLATE_SIZE) {
        BiometricVector(floatArray)
    } else {
        BiometricVector.EMPTY
    }

    return EnrolledProfile(
        id = id,
        name = name,
        address = address,
        mobileNo = mobileNo,
        email = email,
        role = role,
        enrolledDate = enrolledDate,
        vector = vec,
        avatarColor = avatarColor,
        notes = notes,
        photoUri = profilePictureUri
    )
}

/**
 * Converts application domain model to Room database entity.
 */
fun EnrolledProfile.toEntity(): EnrolledPersonEntity {
    return EnrolledPersonEntity(
        id = id,
        name = name,
        address = address,
        mobileNo = mobileNo,
        email = email,
        profilePictureUri = photoUri,
        role = role,
        enrolledDate = enrolledDate,
        vectorFeatures = vector.features.joinToString(","),
        avatarColor = avatarColor,
        notes = notes,
        timestamp = System.currentTimeMillis()
    )
}
