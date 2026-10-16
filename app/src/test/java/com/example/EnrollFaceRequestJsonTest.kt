package com.example

import com.example.data.remote.ApiErrorResponse
import com.example.data.remote.DeviceInfoPayload
import com.example.data.remote.EmployeeFaceResponse
import com.example.data.remote.EnrollFaceRequest
import com.example.data.remote.EnrollFaceResponse
import com.example.data.remote.FacePayload
import com.example.data.remote.GateDto
import com.example.data.remote.LivenessPayload
import com.example.data.remote.MatchResultRequest
import com.example.data.remote.MatchResultResponse
import com.example.data.remote.RegisterDeviceRequest
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnrollFaceRequestJsonTest {

  private val moshi = Moshi.Builder().build()

  @Test
  fun `enroll request serializes to the agreed API shape`() {
    val request = EnrollFaceRequest(
        mobileNumber = "9876543210",
        face = FacePayload(
            embedding = listOf(0.5f, -0.25f),
            embeddingDim = 2,
            normalized = true,
            imageBase64 = "AAA="
        )
    )

    val json = moshi.adapter(EnrollFaceRequest::class.java).toJson(request)

    assertEquals(
        """{"mobileNumber":"9876543210","face":{"embedding":[0.5,-0.25],""" +
            """"embeddingDim":2,"normalized":true,"imageBase64":"AAA="}}""",
        json
    )
  }

  @Test
  fun `employee face response parses the get-face API shape`() {
    val json = """
      {"employee":{"employeeId":"E1","name":"Asha","mobileNumber":"9999999999"},
       "face":{"faceId":"f-1","embedding":[0.6,0.8],"embeddingDim":2,"normalized":true,"imageBase64":"AAA="},
       "matchConfig":{"threshold":0.4,"requiredChallenges":["CENTER_FACE","BLINK_EYES"],"maxSpoofProbability":0.35}}
    """.trimIndent()

    val response = moshi.adapter(EmployeeFaceResponse::class.java).fromJson(json)!!

    assertEquals("Asha", response.employee?.name)
    assertEquals("f-1", response.face?.faceId)
    assertEquals(listOf(0.6f, 0.8f), response.face?.embedding)
    assertEquals(0.4f, response.matchConfig?.threshold!!, 0.0001f)
    assertEquals(listOf("CENTER_FACE", "BLINK_EYES"), response.matchConfig?.requiredChallenges)
  }

  @Test
  fun `api error message parses from 404 body`() {
    val error = moshi.adapter(ApiErrorResponse::class.java)
        .fromJson("""{"message":"Employee dosent exist"}""")!!

    assertEquals("Employee dosent exist", error.message)
  }

  @Test
  fun `register device request includes the required device name`() {
    val json = moshi.adapter(RegisterDeviceRequest::class.java).toJson(
        RegisterDeviceRequest(deviceId = "abc123", deviceName = "LENOVO TB-7306F", gateId = 1, gateName = "Gate 1")
    )

    assertEquals(
        """{"deviceId":"abc123","deviceName":"LENOVO TB-7306F","gateId":1,"gateName":"Gate 1"}""",
        json
    )
  }

  @Test
  fun `gates and validation errors parse`() {
    val gates = moshi.adapter<List<GateDto>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, GateDto::class.java)
    ).fromJson("""[{"gateId":1,"gateName":"Gate 1","status":true}]""")!!
    assertEquals(1, gates.single().gateId)

    val problem = moshi.adapter(ApiErrorResponse::class.java).fromJson(
        """{"title":"One or more validation errors occurred.","status":400,""" +
            """"errors":{"DeviceName":["The DeviceName field is required."]}}"""
    )!!
    assertEquals("The DeviceName field is required.", problem.errors?.values?.flatten()?.first())
  }

  @Test
  fun `enroll response tolerates missing optional fields`() {
    val response = moshi.adapter(EnrollFaceResponse::class.java)
        .fromJson("""{"mobileNumber":"9876543210","faceId":"f-1","enrolledAt":"2026-10-14T10:00:00Z"}""")!!

    assertEquals("f-1", response.faceId)
    assertEquals("2026-10-14T10:00:00Z", response.enrolledAt)
    assertNull(response.face)
  }

  @Test
  fun `match result request serializes to the match-result API shape`() {
    val request = MatchResultRequest(
        event = "IN",
        result = "SUCCESS",
        faceId = "f_326d6a1f",
        cosineSimilarity = 0.62f,
        threshold = 0.4f,
        liveness = LivenessPayload(
            passed = true,
            challengesCompleted = listOf("CENTER_FACE", "BLINK_EYES"),
            spoofProbability = 0.12f
        ),
        imageBase64 = "AAA=",
        capturedAt = "2026-10-15T16:57:00+05:30",
        device = DeviceInfoPayload(deviceId = "43c2ce523cd60cba", appVersion = "1.0", gateId = 2)
    )

    val json = moshi.adapter(MatchResultRequest::class.java).toJson(request)

    assertEquals(
        """{"event":"IN","result":"SUCCESS","faceId":"f_326d6a1f","cosineSimilarity":0.62,"threshold":0.4,""" +
            """"liveness":{"passed":true,"challengesCompleted":["CENTER_FACE","BLINK_EYES"],"spoofProbability":0.12},""" +
            """"imageBase64":"AAA=","capturedAt":"2026-10-15T16:57:00+05:30",""" +
            """"device":{"deviceId":"43c2ce523cd60cba","appVersion":"1.0","gateId":2}}""",
        json
    )
  }

  @Test
  fun `all employee faces list parses without mobileNumber`() {
    val json = """
      [{"employee":{"employeeId":"1001","name":"Asha","mobileNumber":"9999999999"},
        "face":{"faceId":"f_1","embedding":[0.1,0.2],"embeddingDim":2,"normalized":true},
        "matchConfig":{"threshold":0.4,"requiredChallenges":["CENTER_FACE"],"maxSpoofProbability":0.35}},
       {"employee":{"employeeId":"1002","name":"Ravi","mobileNumber":"9888888888"},
        "face":{"faceId":"f_2","embedding":[0.3],"embeddingDim":1,"normalized":true},
        "matchConfig":{"threshold":0.4,"requiredChallenges":["BLINK_EYES"],"maxSpoofProbability":0.3}}]
    """.trimIndent()
    val type = com.squareup.moshi.Types.newParameterizedType(
        List::class.java,
        EmployeeFaceResponse::class.java
    )
    val list = moshi.adapter<List<EmployeeFaceResponse>>(type).fromJson(json)!!

    assertEquals(2, list.size)
    assertEquals("Asha", list[0].employee?.name)
    assertEquals("f_2", list[1].face?.faceId)
    assertEquals(listOf(0.3f), list[1].face?.embedding)
  }

  @Test
  fun `match result response parses the 201 body`() {
    val response = moshi.adapter(MatchResultResponse::class.java).fromJson(
        """{"recordId":"rec_20261015_5010","employeeId":"1001","gateId":2,"event":"IN","result":"SUCCESS",""" +
            """"attendanceRecorded":true,"recordedAt":"2026-10-15T17:01:16+05:30","message":"Office In recorded"}"""
    )!!

    assertEquals("rec_20261015_5010", response.recordId)
    assertEquals(true, response.attendanceRecorded)
    assertEquals("Office In recorded", response.message)
  }
}
