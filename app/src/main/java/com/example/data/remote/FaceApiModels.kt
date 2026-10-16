package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EnrollFaceRequest(
    val mobileNumber: String,
    val face: FacePayload
)

@JsonClass(generateAdapter = true)
data class FacePayload(
    val faceId: String? = null,
    val embedding: List<Float>? = null,
    val embeddingDim: Int? = null,
    val normalized: Boolean? = null,
    val imageBase64: String? = null
)

@JsonClass(generateAdapter = true)
data class EnrollFaceResponse(
    val mobileNumber: String? = null,
    val faceId: String? = null,
    val face: FacePayload? = null,
    val enrolledAt: String? = null
)

@JsonClass(generateAdapter = true)
data class EmployeeFaceResponse(
    val employee: EmployeeDto? = null,
    val face: FacePayload? = null
)

@JsonClass(generateAdapter = true)
data class EmployeeDto(
    val employeeId: String? = null,
    val name: String? = null,
    val mobileNumber: String? = null
)

@JsonClass(generateAdapter = true)
data class GateDto(
    val gateId: Int,
    val gateName: String? = null,
    val status: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class RegisterDeviceRequest(
    val deviceId: String,
    val deviceName: String,
    val gateId: Int,
    val gateName: String? = null
)

@JsonClass(generateAdapter = true)
data class DeviceResponse(
    val slno: Int? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val gateId: Int? = null,
    val gateName: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class MatchResultRequest(
    val event: String,
    val result: String,
    val faceId: String,
    val cosineSimilarity: Float? = null,
    val threshold: Float? = null,
    val liveness: LivenessPayload? = null,
    val imageBase64: String? = null,
    val capturedAt: String? = null,
    val device: DeviceInfoPayload? = null
)

@JsonClass(generateAdapter = true)
data class LivenessPayload(
    val passed: Boolean,
    val challengesCompleted: List<String> = emptyList(),
    val spoofProbability: Float? = null
)

@JsonClass(generateAdapter = true)
data class DeviceInfoPayload(
    val deviceId: String? = null,
    val appVersion: String? = null,
    val gateId: Int? = null
)

@JsonClass(generateAdapter = true)
data class MatchResultResponse(
    val recordId: String? = null,
    val employeeId: String? = null,
    val gateId: Int? = null,
    val event: String? = null,
    val result: String? = null,
    val attendanceRecorded: Boolean? = null,
    val recordedAt: String? = null,
    val message: String? = null
)

/** Covers both `{"message": ...}` and ASP.NET ProblemDetails / validation error bodies. */
@JsonClass(generateAdapter = true)
data class ApiErrorResponse(
    val message: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val errors: Map<String, List<String>>? = null
)
