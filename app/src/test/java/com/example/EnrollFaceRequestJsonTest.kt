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
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollFaceRequestJsonTest {

  private val moshi = Moshi.Builder().build()

  @Test
  fun `enroll request serializes mobileNumber and faces poses only`() {
    val front = FacePayload(
        pose = "front",
        embedding = listOf(0.5f, -0.25f),
        embeddingDim = 2,
        normalized = true,
        imageBase64 = "AAA="
    )
    val left = FacePayload(
        pose = "left",
        embedding = listOf(0.1f, 0.2f),
        embeddingDim = 2,
        normalized = true,
        imageBase64 = "BBB="
    )
    val right = FacePayload(
        pose = "right",
        embedding = listOf(-0.1f, 0.3f),
        embeddingDim = 2,
        normalized = true,
        imageBase64 = "CCC="
    )
    val request = EnrollFaceRequest(
        mobileNumber = "9876543210",
        faces = listOf(front, left, right)
    )

    val json = moshi.adapter(EnrollFaceRequest::class.java).toJson(request)

    assertEquals(
        """{"mobileNumber":"9876543210","faces":[""" +
            """{"pose":"front","embedding":[0.5,-0.25],"embeddingDim":2,"normalized":true,"imageBase64":"AAA="},""" +
            """{"pose":"left","embedding":[0.1,0.2],"embeddingDim":2,"normalized":true,"imageBase64":"BBB="},""" +
            """{"pose":"right","embedding":[-0.1,0.3],"embeddingDim":2,"normalized":true,"imageBase64":"CCC="}]}""",
        json
    )
  }

  @Test
  fun `employee face response parses multi-pose faces list`() {
    val json = """
      {"employee":{"employeeId":"E1","name":"Asha","mobileNumber":"9999999999"},
       "faces":[
         {"pose":"front","faceId":"f-1","embedding":[0.6,0.8],"embeddingDim":2,"normalized":true},
         {"pose":"left","faceId":"f-2","embedding":[0.1,0.2],"embeddingDim":2,"normalized":true}
       ]}
    """.trimIndent()

    val response = moshi.adapter(EmployeeFaceResponse::class.java).fromJson(json)!!

    assertEquals("Asha", response.employee?.name)
    assertEquals(2, response.templates().size)
    assertEquals("front", response.templates()[0].pose)
    assertEquals(listOf(0.1f, 0.2f), response.templates()[1].embedding)
  }

  @Test
  fun `employee face response with empty faces yields no templates`() {
    val json = """
      {"employee":{"employeeId":"E1","name":"Asha","mobileNumber":"9999999999"},
       "faces":[]}
    """.trimIndent()

    val response = moshi.adapter(EmployeeFaceResponse::class.java).fromJson(json)!!

    assertTrue(response.templates().isEmpty())
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
  fun `all employee faces list parses faces poses without mobileNumber`() {
    val json = """
      [{"employee":{"employeeId":"1001","name":"Asha","mobileNumber":"9999999999"},
        "faces":[
          {"pose":"front","embedding":[0.1,0.2],"embeddingDim":2,"normalized":true},
          {"pose":"left","embedding":[0.15,0.25],"embeddingDim":2,"normalized":true},
          {"pose":"right","embedding":[0.05,0.22],"embeddingDim":2,"normalized":true}
        ]},
       {"employee":{"employeeId":"1002","name":"Ravi","mobileNumber":"9888888888"},
        "faces":[
          {"pose":"front","embedding":[0.3,0.4],"embeddingDim":2,"normalized":true}
        ]}]
    """.trimIndent()
    val type = com.squareup.moshi.Types.newParameterizedType(
        List::class.java,
        EmployeeFaceResponse::class.java
    )
    val list = moshi.adapter<List<EmployeeFaceResponse>>(type).fromJson(json)!!

    assertEquals(2, list.size)
    assertEquals("Asha", list[0].employee?.name)
    assertEquals(3, list[0].templates().size)
    assertEquals("left", list[0].templates()[1].pose)
    assertEquals("front", list[1].templates().single().pose)
    assertEquals(listOf(0.3f, 0.4f), list[1].templates().single().embedding)
  }

  @Test
  fun `match result response parses the 201 body`() {
    val response = moshi.adapter(MatchResultResponse::class.java).fromJson(
        """{"recordId":"rec_20261015_5010","employeeId":"1001","gateId":2,"event":"IN","result":"SUCCESS",""" +
            """"attendanceRecorded":true,"recordedAt":"2026-10-15T17:01:16+05:30","message":"Presence recorded"}"""
    )!!

    assertEquals("rec_20261015_5010", response.recordId)
    assertEquals(true, response.attendanceRecorded)
    assertEquals("Presence recorded", response.message)
  }
}
