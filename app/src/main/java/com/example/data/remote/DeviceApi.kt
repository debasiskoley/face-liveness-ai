package com.example.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DeviceApi {

    @Headers("Accept: application/json")
    @GET("api/gates")
    suspend fun getGates(@Query("onlyActive") onlyActive: Boolean = true): List<GateDto>

    @Headers("Accept: application/json")
    @POST("api/Enrolldevice")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): DeviceResponse

    @Headers("Accept: application/json")
    @GET("api/Enrolldevice/{deviceId}")
    suspend fun getDevice(@Path("deviceId") deviceId: String): DeviceResponse
}
