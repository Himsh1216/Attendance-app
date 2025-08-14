package com.example.attendance.network

import com.example.attendance.data.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface AttendanceApi {
    @GET("session/current")
    suspend fun getCurrentSession(@Query("classId") classId: String): SessionInfo

    @POST("session/start")
    suspend fun startSession(@Body req: StartSessionRequest): StartSessionResponse

    @Multipart
    @POST("enroll")
    suspend fun enroll(
        @Part image: MultipartBody.Part,
        @Part("studentId") studentId: RequestBody
    ): Map<String, Any>

    @Multipart
    @POST("attendance/checkin")
    suspend fun checkIn(
        @Part image: MultipartBody.Part,
        @Part("classId") classId: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lon") lon: RequestBody,
        @Part("studentId") studentId: RequestBody // TODO: replace with auth-derived ID
    ): CheckInResponse
}
