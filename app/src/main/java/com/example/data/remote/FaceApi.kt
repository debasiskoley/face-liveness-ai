package com.example.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface FaceApi {

    @Headers("Accept: application/json")
    @POST("api/faces/enroll_Face")
    suspend fun enroll(@Body request: EnrollFaceRequest): EnrollFaceResponse

    @Headers("Accept: application/json")
    @GET("api/employees/face")
    suspend fun getEmployeeFace(@Query("mobileNumber") mobileNumber: String): EmployeeFaceResponse
}
