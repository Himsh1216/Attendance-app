package com.example.attendance.location

import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun getCurrentHighAccuracyLocation(context: Context): Location? {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    return suspendCancellableCoroutine { cont ->
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }
}

fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float {
    val out = FloatArray(1)
    Location.distanceBetween(aLat, aLon, bLat, bLon, out)
    return out[0]
}

fun isMockLocation(loc: Location?): Boolean {
    if (loc == null) return false
    return loc.isFromMockProvider
}
