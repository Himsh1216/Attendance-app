package com.example.attendance.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.attendance.data.StartSessionRequest
import com.example.attendance.location.getCurrentHighAccuracyLocation
import com.example.attendance.network.ApiClient
import kotlinx.coroutines.launch

@Composable
fun InstructorScreen(classId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var duration by remember { mutableStateOf("10") }
    var radius by remember { mutableStateOf("10") }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { perm.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Instructor", style = MaterialTheme.typography.titleLarge)
        Text("Class: $classId")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration (min)") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = radius, onValueChange = { radius = it }, label = { Text("Radius (m)") })
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            scope.launch {
                val loc = getCurrentHighAccuracyLocation(ctx)
                if (loc == null) { Toast.makeText(ctx, "Location unavailable", Toast.LENGTH_SHORT).show(); return@launch }
                val req = StartSessionRequest(
                    classId = classId,
                    centerLat = loc.latitude,
                    centerLon = loc.longitude,
                    radiusMeters = radius.toIntOrNull() ?: 10,
                    durationMinutes = duration.toIntOrNull() ?: 10
                )
                val ok = ApiClient.api.startSession(req).ok
                Toast.makeText(ctx, if (ok) "Session opened" else "Failed", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Open attendance window") }
    }
}
