package com.example.attendance.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SessionInfo(
    val classId: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Int,
    val expiresAtEpochMs: Long
)

@JsonClass(generateAdapter = true)
data class StartSessionRequest(
    val classId: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Int = 10,
    val durationMinutes: Int = 10
)

@JsonClass(generateAdapter = true)
data class StartSessionResponse(val ok: Boolean)

@JsonClass(generateAdapter = true)
data class CheckInResponse(val ok: Boolean, val matchConfidence: Double? = null, val message: String? = null)
