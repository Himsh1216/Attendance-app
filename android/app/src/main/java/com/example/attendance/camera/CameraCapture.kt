package com.example.attendance.camera

import android.content.Context
import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CameraPreview(imageCapture: ImageCapture) {
    val ctx = LocalContext.current
    AndroidView(factory = { c ->
        val pv = PreviewView(c).apply { layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT) }
        val providerFuture = ProcessCameraProvider.getInstance(c)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(pv.surfaceProvider)
            }
            val selector = CameraSelector.DEFAULT_FRONT_CAMERA
            val ic = imageCapture
            provider.unbindAll()
            provider.bindToLifecycle(ctx as androidx.lifecycle.LifecycleOwner, selector, preview, ic)
        }, ContextCompat.getMainExecutor(c))
        pv
    })
}

fun rememberImageCapture(): ImageCapture =
    ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()

fun takePhoto(context: Context, imageCapture: ImageCapture, onSaved: (Uri?) -> Unit) {
    val file = File(context.cacheDir, "selfie_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg")
    val opts = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        opts,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) = onSaved(null)
            override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved(Uri.fromFile(file))
        }
    )
}
