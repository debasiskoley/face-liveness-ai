package com.example.data

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.EnrolledPersonDao
import com.example.data.local.toEnrolledProfile
import com.example.data.local.toEntity
import com.example.model.BiometricVector
import com.example.model.EnrolledProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Manages enrolled biometric reference profiles persisted in Room Database.
 * Supports enrollment, updates, and deletion of person identities with name, address,
 * mobile number, email, and reference profile pictures.
 */
class EnrolledIdentityRepository(
    private val dao: EnrolledPersonDao? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private val _profiles = MutableStateFlow<List<EnrolledProfile>>(emptyList())
    val profiles: StateFlow<List<EnrolledProfile>> = _profiles.asStateFlow()

    private val _selectedProfile = MutableStateFlow<EnrolledProfile?>(null)
    val selectedProfile: StateFlow<EnrolledProfile?> = _selectedProfile.asStateFlow()

    init {
        if (dao != null) {
            coroutineScope.launch {
                // Pre-populate default personas if database is empty
                if (dao.getPersonCount() == 0) {
                    val defaultProfiles = createDefaultProfiles()
                    dao.insertPersons(defaultProfiles.map { it.toEntity() })
                }

                // Reactively observe all enrolled persons from Room database
                dao.getAllPersons().collect { entities ->
                    val profileList = entities.map { it.toEnrolledProfile() }
                    _profiles.value = profileList

                    val currentSelectedId = _selectedProfile.value?.id
                    if (currentSelectedId == null || profileList.none { it.id == currentSelectedId }) {
                        _selectedProfile.value = profileList.firstOrNull()
                    } else {
                        _selectedProfile.value = profileList.find { it.id == currentSelectedId }
                    }
                }
            }
        } else {
            // Fallback for isolated unit tests without Room database context
            val initialList = createDefaultProfiles()
            _profiles.value = initialList
            _selectedProfile.value = initialList.firstOrNull()
        }
    }

    fun selectProfile(profileId: String) {
        val found = _profiles.value.find { it.id == profileId }
        if (found != null) {
            _selectedProfile.value = found
        }
    }

    /**
     * Enrolls a new person with name, address, mobile number, email, and profile picture into Room DB.
     */
    fun enrollNewProfile(
        name: String,
        address: String = "",
        mobileNo: String = "",
        email: String = "",
        role: String = "Authorized Person",
        vector: BiometricVector,
        notes: String = "",
        photoUri: String? = null,
        avatarColor: Long = 0xFF0284C7
    ): EnrolledProfile {
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
        val newProfile = EnrolledProfile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Enrolled Subject #${_profiles.value.size + 1}" },
            address = address.trim(),
            mobileNo = mobileNo.trim(),
            email = email.trim(),
            role = role.ifBlank { "Authorized Person" },
            enrolledDate = dateStr,
            vector = vector,
            avatarColor = avatarColor,
            notes = notes.ifBlank { "Enrolled via Room Biometric Database" },
            photoUri = photoUri
        )

        if (dao != null) {
            coroutineScope.launch {
                dao.insertPerson(newProfile.toEntity())
            }
        }
        _profiles.value = listOf(newProfile) + _profiles.value.filterNot { it.id == newProfile.id }

        _selectedProfile.value = newProfile
        return newProfile
    }

    /**
     * Convenience overload for enrolling with basic credentials.
     */
    fun enrollNewProfile(
        name: String,
        role: String,
        vector: BiometricVector
    ): EnrolledProfile = enrollNewProfile(
        name = name,
        address = "",
        mobileNo = "",
        email = "",
        role = role,
        vector = vector
    )

    /**
     * Updates an existing enrolled person with modified details (name, address, mobile, email, photo, etc.).
     */
    fun updateProfile(updated: EnrolledProfile) {
        if (dao != null) {
            coroutineScope.launch {
                dao.updatePerson(updated.toEntity())
            }
        }
        val currentList = _profiles.value
        _profiles.value = currentList.map { if (it.id == updated.id) updated else it }

        if (_selectedProfile.value?.id == updated.id) {
            _selectedProfile.value = updated
        }
    }

    /**
     * Deletes an enrolled person from the Room database.
     */
    fun deleteProfile(profileId: String) {
        if (dao != null) {
            coroutineScope.launch {
                dao.deletePersonById(profileId)
            }
        } else {
            val currentList = _profiles.value
            val newList = currentList.filterNot { it.id == profileId }
            _profiles.value = newList
        }

        if (_selectedProfile.value?.id == profileId) {
            _selectedProfile.value = _profiles.value.firstOrNull { it.id != profileId }
        }
    }

    private fun createDefaultProfiles(): List<EnrolledProfile> {
        val v1 = createCalibratedVector(
            iod = 1.0f,
            noseYRatio = 0.52f,
            mouthYRatio = 0.72f,
            mouthWidthRatio = 0.85f,
            cheekWidthRatio = 1.70f,
            faceHeightRatio = 1.55f
        )

        val v2 = createCalibratedVector(
            iod = 1.0f,
            noseYRatio = 0.48f,
            mouthYRatio = 0.69f,
            mouthWidthRatio = 0.76f,
            cheekWidthRatio = 1.58f,
            faceHeightRatio = 1.48f
        )

        val v3 = createCalibratedVector(
            iod = 1.0f,
            noseYRatio = 0.55f,
            mouthYRatio = 0.75f,
            mouthWidthRatio = 0.94f,
            cheekWidthRatio = 1.82f,
            faceHeightRatio = 1.62f
        )

        val v4 = createCalibratedVector(
            iod = 1.0f,
            noseYRatio = 0.35f,
            mouthYRatio = 0.88f,
            mouthWidthRatio = 0.52f,
            cheekWidthRatio = 1.25f,
            faceHeightRatio = 1.95f
        )

        return listOf(
            EnrolledProfile(
                id = "profile_alex",
                name = "Alex Chen",
                address = "104 Silicon Parkway, Suite 400, San Jose, CA",
                mobileNo = "+1 (555) 382-9102",
                email = "alex.chen@biometric.id",
                role = "Executive Access (Badge #4892)",
                enrolledDate = "Sep 12, 2026",
                vector = v1,
                avatarColor = 0xFF0284C7,
                notes = "Reference photo registered with biometric credential"
            ),
            EnrolledProfile(
                id = "profile_sarah",
                name = "Dr. Sarah Jenkins",
                address = "42 Bio-Tech Boulevard, Cambridge, MA",
                mobileNo = "+1 (555) 719-4821",
                email = "sarah.jenkins@biometric.id",
                role = "Senior Researcher (Badge #1043)",
                enrolledDate = "Aug 29, 2026",
                vector = v2,
                avatarColor = 0xFF8B5CF6,
                notes = "High-clearance laboratory biometric enrollment"
            ),
            EnrolledProfile(
                id = "profile_marcus",
                name = "Marcus Vance",
                address = "880 Data Center Way, Austin, TX",
                mobileNo = "+1 (555) 492-3019",
                email = "marcus.vance@biometric.id",
                role = "Infrastructure Lead (Badge #7731)",
                enrolledDate = "Sep 05, 2026",
                vector = v3,
                avatarColor = 0xFF059669,
                notes = "Server room multi-factor authentication"
            ),
            EnrolledProfile(
                id = "profile_imposter",
                name = "Unknown Imposter (Test Mismatch)",
                address = "Unverified Location",
                mobileNo = "+1 (555) 000-0000",
                email = "unauthorized.test@biometric.id",
                role = "Unenrolled Subject (Simulated Test)",
                enrolledDate = "N/A",
                vector = v4,
                avatarColor = 0xFFDC2626,
                notes = "Used to verify system correctly rejects unauthorized faces"
            )
        )
    }

    private fun createCalibratedVector(
        iod: Float,
        noseYRatio: Float,
        mouthYRatio: Float,
        mouthWidthRatio: Float,
        cheekWidthRatio: Float,
        faceHeightRatio: Float
    ): BiometricVector {
        val features = FloatArray(32)
        features[0] = noseYRatio
        features[1] = noseYRatio
        features[2] = mouthYRatio - noseYRatio
        features[3] = mouthYRatio - noseYRatio
        features[4] = mouthWidthRatio
        features[5] = 1.0f - mouthYRatio
        features[6] = mouthYRatio
        features[7] = mouthYRatio
        features[8] = cheekWidthRatio
        features[9] = cheekWidthRatio * 0.5f
        features[10] = cheekWidthRatio * 0.5f
        features[11] = cheekWidthRatio * 0.6f
        features[12] = cheekWidthRatio * 0.6f
        features[13] = faceHeightRatio
        features[14] = faceHeightRatio
        features[15] = mouthWidthRatio / cheekWidthRatio
        features[16] = 0.02f
        features[17] = 0.02f
        features[18] = noseYRatio * 0.8f
        features[19] = (mouthYRatio - noseYRatio) * 0.8f
        features[20] = noseYRatio / (mouthYRatio - noseYRatio).coerceAtLeast(0.01f)
        features[21] = noseYRatio / (mouthYRatio - noseYRatio).coerceAtLeast(0.01f)
        features[22] = mouthWidthRatio / cheekWidthRatio
        features[23] = (1.0f - mouthYRatio) / faceHeightRatio
        features[24] = 0.72f
        features[25] = iod / cheekWidthRatio
        features[26] = faceHeightRatio * 0.9f
        features[27] = faceHeightRatio * 0.9f
        features[28] = 0.0f
        features[29] = noseYRatio - 0.5f
        features[30] = 0.0f
        features[31] = mouthYRatio - 0.5f

        var norm = 0f
        for (f in features) norm += f * f
        norm = kotlin.math.sqrt(norm).coerceAtLeast(1e-6f)
        for (i in features.indices) features[i] /= norm

        return BiometricVector(features)
    }

    companion object {
        @Volatile
        private var INSTANCE: EnrolledIdentityRepository? = null

        fun getInstance(context: Context): EnrolledIdentityRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val instance = EnrolledIdentityRepository(db.enrolledPersonDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
