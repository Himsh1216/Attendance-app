package com.example.attendance

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AttendanceApp() }
    }
}

@Composable
fun AttendanceApp() {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Student") }, icon = { Icon(painterResource(android.R.drawable.ic_menu_camera), null) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Instructor") }, icon = { Icon(painterResource(android.R.drawable.ic_menu_myplaces), null) })
            }
        }
    ) { padding ->
        if (tab == 0) {
            StudentScreen(Modifier.padding(padding))
        } else {
            InstructorScreen(Modifier.padding(padding))
        }
    }
}

@Composable
fun StudentScreen(modifier: Modifier = Modifier, vm: StudentViewModel = viewModel()) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var classId by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bitmap = bmp
    }
    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(value = classId, onValueChange = { classId = it }, label = { Text("Class ID") })
        OutlinedTextField(value = studentId, onValueChange = { studentId = it }, label = { Text("Student ID") })
        Spacer(Modifier.height(16.dp))
        if (bitmap != null) {
            Image(painter = rememberAsyncImagePainter(bitmap), contentDescription = null, modifier = Modifier.size(200.dp))
            Row {
                Button(onClick = { bitmap = null }) { Text("Retake") }
                Spacer(Modifier.width(16.dp))
                Button(onClick = {
                    vm.checkIn(classId, studentId, bitmap!!) { ok, msg ->
                        result = msg
                        bitmap = null
                    }
                }) { Text("Submit") }
            }
        } else {
            Button(onClick = { takePicture.launch(null) }) { Text("Take Selfie") }
        }
        result?.let { Text(it) }
    }
}

@Composable
fun InstructorScreen(modifier: Modifier = Modifier, vm: InstructorViewModel = viewModel()) {
    val context = LocalContext.current
    var classId by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("10") }
    var result by remember { mutableStateOf<String?>(null) }

    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(value = classId, onValueChange = { classId = it }, label = { Text("Class ID") })
        OutlinedTextField(value = radius, onValueChange = { radius = it }, label = { Text("Radius meters") })
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            vm.startSession(classId, radius.toFloat()) { ok, msg -> result = msg }
        }) { Text("Open Attendance Window") }
        result?.let { Text(it) }
    }
}

// ---- ViewModels & Networking ----

import android.app.Application
import android.graphics.Bitmap.CompressFormat
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.io.FileOutputStream

class StudentViewModel(app: Application) : AndroidViewModel(app) {
    private val api = Api.service
    private val fused = LocationServices.getFusedLocationProviderClient(app)

    fun checkIn(classId: String, studentId: String, bmp: Bitmap, cb: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val file = File.createTempFile("selfie", ".jpg", getApplication<Application>().cacheDir)
            FileOutputStream(file).use { out -> bmp.compress(CompressFormat.JPEG, 90, out) }
            val loc = fused.lastLocation.await()
            val imgPart = MultipartBody.Part.createFormData("image", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            val resp = api.checkIn(classId, loc.latitude, loc.longitude, studentId, imgPart)
            val body = resp.body()
            val msg = if (body?.ok == true) "Marked (${body.matchConfidence})" else body?.message ?: "Error"
            cb(body?.ok == true, msg)
        }
    }
}

class InstructorViewModel(app: Application) : AndroidViewModel(app) {
    private val api = Api.service
    private val fused = LocationServices.getFusedLocationProviderClient(app)

    fun startSession(classId: String, radius: Float, cb: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val loc = fused.lastLocation.await()
            val resp = api.startSession(classId, loc.latitude, loc.longitude, radius)
            val body = resp.body()
            val msg = if (body?.ok == true) "Session opened" else body?.message ?: "Error"
            cb(body?.ok == true, msg)
        }
    }
}

object Api {
    private val client = OkHttpClient.Builder().build()
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8000/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service: AttendanceApi = retrofit.create(AttendanceApi::class.java)
}

interface AttendanceApi {
    @FormUrlEncoded
    @POST("session/start")
    suspend fun startSession(
        @Field("classId") classId: String,
        @Field("lat") lat: Double,
        @Field("lon") lon: Double,
        @Field("radius") radius: Float,
        @Field("durationMinutes") duration: Int = 10
    ): retrofit2.Response<SimpleResponse>

    @Multipart
    @POST("attendance/checkin")
    suspend fun checkIn(
        @Part("classId") classId: String,
        @Part("lat") lat: Double,
        @Part("lon") lon: Double,
        @Part("studentId") studentId: String,
        @Part image: MultipartBody.Part
    ): retrofit2.Response<CheckInResponse>
}

data class SimpleResponse(val ok: Boolean, val message: String? = null)
data class CheckInResponse(val ok: Boolean, val matchConfidence: Double? = null, val message: String? = null)

// Helper extension to await lastLocation (since no coroutine support by default)
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
