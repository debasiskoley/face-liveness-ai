package com.example.data.local

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey

/**
 * One pose template (front / left / right / …) for an enrolled employee,
 * cached from GET /api/employees/face for offline 1:N matching.
 */
class FaceTemplate : RealmObject {
    @PrimaryKey
    var id: String = ""

    @Index
    var employeeId: String = ""

    var employeeName: String = ""

    @Index
    var mobileNumber: String = ""

    var pose: String = ""
    var faceId: String? = null
    var embedding: ByteArray = ByteArray(0)
    var embeddingDim: Int = 0
    var normalized: Boolean = true
    var syncedAtEpochMs: Long = 0L
}
