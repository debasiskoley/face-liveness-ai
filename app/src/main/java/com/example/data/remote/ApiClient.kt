package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    val baseUrl: String = BuildConfig.API_BASE_URL.trim().let { if (it.endsWith("/")) it else "$it/" }

    val faceApi: FaceApi by lazy { retrofit.create(FaceApi::class.java) }

    private val moshi: Moshi = Moshi.Builder().build()

    /** Reads `{"message": "..."}` from an error response, falling back to the raw body text. */
    fun errorMessage(e: HttpException): String? {
        val body = try {
            e.response()?.errorBody()?.string()
        } catch (_: IOException) {
            null
        }?.trim()
        if (body.isNullOrEmpty()) return null
        val parsed = try {
            moshi.adapter(ApiErrorResponse::class.java).fromJson(body)?.message
        } catch (_: Exception) {
            null
        }
        return parsed ?: body.take(200)
    }

    private val retrofit: Retrofit by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // Never BODY: requests carry face embeddings and face images.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}
