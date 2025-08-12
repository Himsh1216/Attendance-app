package com.example.attendance.ui

import android.Manifest
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.attendance.camera.CameraPreview
import com.example.attendance.camera.rememberImageCapture
import com.example.attendance.camera.takePhoto
import com.example.attendance.location.distanceMeters
import com.example.attendance.location.getCurrentHighAccuracyLocation
import com.example.attendance.location.isMockLocation
import kotlinx.coroutines.launch

@Composable
fun StudentScreen(classId: String, studentId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = remember { AttendanceViewModel() }
    val session by vm.session.collectAsState()

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        perms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
        vm.loadSession(classId)
    }

    var selfie by remember { mutableStateOf<Uri?>(null) }
    val imageCapture: ImageCapture = rememberImageCapture()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Student", style = MaterialTheme.typography.titleLarge)
        Text("Class: $classId")
        Spacer(Modifier.height(8.dp))

        if (session == null) {
            Text("No active session.")
        } else {
            val s = session!!
            Text("Window ends: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(s.expiresAtEpochMs))}")
            Spacer(Modifier.height(12.dp))

            CameraPreview(imageCapture)
            Spacer(Modifier.height(12.dp))

            Row {
                Button(onClick = {
                    takePhoto(ctx, imageCapture) { uri -> selfie = uri }
                }) { Text("Take selfie") }

                Spacer(Modifier.width(12.dp))

                Button(enabled = selfie != null, onClick = {
                    scope.launch {
                        val loc: Location? = getCurrentHighAccuracyLocation(ctx)
                        if (loc == null || isMockLocation(loc)) {
                            Toast.makeText(ctx, "Location unavailable or mocked", Toast.LENGTH_SHORT).show(); return@launch
                        }
                        val d = distanceMeters(loc.latitude, loc.longitude, s.centerLat, s.centerLon)
                        val now = System.currentTimeMillis()
                        if (now > s.expiresAtEpochMs || d > s.radiusMeters) {
                            Toast.makeText(ctx, "Not in zone or window expired", Toast.LENGTH_SHORT).show(); return@launch
                        }
                        val resp = vm.checkIn(selfie!!, s.classId, loc.latitude, loc.longitude, studentId)
                        Toast.makeText(ctx, if (resp.ok) "Attendance marked (${resp.matchConfidence?.toInt()}%)" else resp.message ?: "Failed", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Submit") }
            }
        }
    }
}
