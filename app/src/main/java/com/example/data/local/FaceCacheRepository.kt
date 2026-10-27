package com.example.data.local

import android.content.Context
import com.example.data.remote.ApiClient
import com.example.data.remote.EmployeeFaceResponse
import com.example.model.BiometricVector
import com.example.model.EnrolledProfile
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local face-template cache backed by Realm.
 * Syncs once from GET /api/employees/face on first init (or when forced),
 * then 1:N matching reads only from disk.
 */
class FaceCacheRepository private constructor(
    private val realm: Realm
) {
    private val syncMutex = Mutex()

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        realm.query<FaceTemplate>().count().find().toInt()
    }

    suspend fun hasCachedFaces(): Boolean = count() > 0

    /**
     * Fetches the full face list from the API and replaces the local cache.
     * @return number of pose templates stored
     */
    suspend fun syncFromApi(): Int = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val responses = ApiClient.faceApi.getAllEmployeeFaces()
            val now = System.currentTimeMillis()
            val rows = responses.flatMap { it.toSnapshots(now) }
            realm.write {
                deleteAll()
                for (row in rows) {
                    copyToRealm(row.toRealmObject(), updatePolicy = UpdatePolicy.ALL)
                }
            }
            rows.size
        }
    }

    /**
     * Sync only when the cache is empty (first app init / wiped DB).
     * @return template count after ensure
     */
    suspend fun ensureSynced(): Int {
        val existing = count()
        if (existing > 0) return existing
        return syncFromApi()
    }

    suspend fun loadAllProfiles(embeddingDim: Int): List<EnrolledProfile> =
        withContext(Dispatchers.IO) {
            realm.query<FaceTemplate>().find().mapNotNull { it.toProfile(embeddingDim) }
        }

    suspend fun loadProfilesByMobile(mobileNumber: String, embeddingDim: Int): List<EnrolledProfile> =
        withContext(Dispatchers.IO) {
            realm.query<FaceTemplate>("mobileNumber == $0", mobileNumber)
                .find()
                .mapNotNull { it.toProfile(embeddingDim) }
        }

    /** Upserts one employee's templates (e.g. after a targeted GET) without wiping others. */
    suspend fun upsertEmployee(response: EmployeeFaceResponse) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val rows = response.toSnapshots(now)
        if (rows.isEmpty()) return@withContext
        realm.write {
            for (row in rows) {
                copyToRealm(row.toRealmObject(), updatePolicy = UpdatePolicy.ALL)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: FaceCacheRepository? = null

        fun get(context: Context): FaceCacheRepository =
            instance ?: synchronized(this) {
                instance ?: FaceCacheRepository(FaceRealm.get(context))
                    .also { instance = it }
            }
    }
}

/** Unmanaged snapshot used to build Realm objects inside a write transaction. */
private data class FaceTemplateSnapshot(
    val id: String,
    val employeeId: String,
    val employeeName: String,
    val mobileNumber: String,
    val pose: String,
    val faceId: String?,
    val embedding: ByteArray,
    val embeddingDim: Int,
    val normalized: Boolean,
    val syncedAtEpochMs: Long
)

private fun FaceTemplateSnapshot.toRealmObject(): FaceTemplate {
    val src = this
    return FaceTemplate().apply {
        id = src.id
        employeeId = src.employeeId
        employeeName = src.employeeName
        mobileNumber = src.mobileNumber
        pose = src.pose
        faceId = src.faceId
        embedding = src.embedding
        embeddingDim = src.embeddingDim
        normalized = src.normalized
        syncedAtEpochMs = src.syncedAtEpochMs
    }
}

private fun EmployeeFaceResponse.toSnapshots(syncedAtEpochMs: Long): List<FaceTemplateSnapshot> {
    val employee = employee ?: return emptyList()
    val employeeId = employee.employeeId.orEmpty()
    val mobile = employee.mobileNumber.orEmpty()
    val name = employee.name.orEmpty()
    val baseId = employeeId.ifBlank { mobile }.ifBlank { return emptyList() }

    return templates().mapIndexedNotNull { index, face ->
        val embedding = face.embedding.orEmpty()
        if (embedding.isEmpty()) return@mapIndexedNotNull null
        val pose = face.pose?.takeIf { it.isNotBlank() } ?: "face$index"
        val faceId = face.faceId?.takeIf { it.isNotBlank() }
        val id = faceId ?: "${baseId}_$pose"
        FaceTemplateSnapshot(
            id = id,
            employeeId = employeeId.ifBlank { baseId },
            employeeName = name,
            mobileNumber = mobile,
            pose = pose,
            faceId = faceId,
            embedding = FaceEmbeddingCodec.encode(embedding),
            embeddingDim = face.embeddingDim ?: embedding.size,
            normalized = face.normalized ?: true,
            syncedAtEpochMs = syncedAtEpochMs
        )
    }
}

private fun FaceTemplate.toProfile(expectedDim: Int): EnrolledProfile? {
    val floats = FaceEmbeddingCodec.decode(embedding)
    if (floats.size != expectedDim) return null
    val matchId = faceId?.takeIf { it.isNotBlank() } ?: employeeId.ifBlank { id }
    val displayName = employeeName.ifBlank {
        mobileNumber.takeIf { it.isNotBlank() }?.let { "+91 $it" } ?: matchId
    }
    return EnrolledProfile(
        id = matchId,
        name = displayName,
        enrolledDate = "",
        vector = BiometricVector(floats),
        mobileNo = mobileNumber,
        employeeId = employeeId,
        notes = pose,
        pose = pose
    )
}
