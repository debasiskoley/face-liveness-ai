package com.example

import com.example.data.remote.ApiErrorResponse
import com.example.data.remote.EmployeeFaceResponse
import com.example.data.remote.EnrollFaceRequest
import com.example.data.remote.EnrollFaceResponse
import com.example.data.remote.FacePayload
import com.example.data.remote.GateDto
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
}
