package com.example.attendance.ui

import android.content.Context
import android.net.Uri
import com.example.attendance.data.CheckInResponse
import com.example.attendance.data.SessionInfo
import com.example.attendance.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File

class AttendanceViewModel {
    private val _session = MutableStateFlow<SessionInfo?>(null)
    val session = _session.asStateFlow()

    suspend fun loadSession(classId: String) {
        _session.value = ApiClient.api.getCurrentSession(classId)
    }

    suspend fun enroll(imageUri: Uri, studentId: String, context: Context): Boolean {
        val file = File(imageUri.path!!)
        val img = RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
        val part = MultipartBody.Part.createFormData("image", file.name, img)
        val sid = RequestBody.create("text/plain".toMediaType(), studentId)
        val res = ApiClient.api.enroll(part, sid)
        return (res["ok"] as? Boolean) == true
    }

    suspend fun checkIn(
        imageUri: Uri,
        classId: String,
        lat: Double,
        lon: Double,
        studentId: String
    ): CheckInResponse {
        val file = File(imageUri.path!!)
        val img = RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
        val part = MultipartBody.Part.createFormData("image", file.name, img)
        val cid = RequestBody.create("text/plain".toMediaType(), classId)
        val rlat = RequestBody.create("text/plain".toMediaType(), lat.toString())
        val rlon = RequestBody.create("text/plain".toMediaType(), lon.toString())
        val sid = RequestBody.create("text/plain".toMediaType(), studentId) // TODO: replace with auth token server-side
        return ApiClient.api.checkIn(part, cid, rlat, rlon, sid)
    }
}
