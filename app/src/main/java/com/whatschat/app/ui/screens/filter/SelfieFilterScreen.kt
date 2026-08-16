package com.whatschat.app.ui.screens.filter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.whatschat.app.data.filter.FaceFilter
import com.whatschat.app.data.filter.FaceFilterCompositor
import com.whatschat.app.data.repository.AuthRepository
import com.whatschat.app.data.repository.ChatRepository
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Selfie capture with an emoji "funny filter" positioned on the actual
 * detected face (ML Kit). The filter is applied to the still photo right
 * after it's taken, not as a live overlay while framing the shot — see the
 * README for why (camera rotation/mirroring is very hard to get right
 * without testing on a real device).
 */
@Composable
fun SelfieFilterScreen(
    chatId: String,
    onSent: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository() }
    val chatRepository = remember { ChatRepository() }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!cameraGranted) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera access is needed for selfie filters.")
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                }
            }
        }
        return
    }

    var selectedFilter by remember { mutableStateOf(FaceFilter.NONE) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sending by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val bitmap = capturedBitmap
    if (bitmap == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { viewContext ->
                    val previewView = PreviewView(viewContext)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder().build()
                        imageCapture = capture
                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                capture
                            )
                        }
                    }, ContextCompat.getMainExecutor(viewContext))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FaceFilter.entries.forEach { filter ->
                    Text(
                        text = filter.emoji,
                        fontSize = 30.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (filter == selectedFilter) Color.White.copy(alpha = 0.4f) else Color.Transparent
                            )
                            .clickable { selectedFilter = filter }
                            .padding(8.dp)
                    )
                }
            }

            FilledIconButton(
                onClick = {
                    val capture = imageCapture ?: return@FilledIconButton
                    val photoFile = File(context.cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val decoded = decodeBitmapWithExif(photoFile)
                                if (decoded != null) {
                                    detectAndComposite(decoded, selectedFilter) { composited ->
                                        capturedBitmap = composited
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) = Unit
                        }
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .size(72.dp)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Capture")
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured selfie",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                FilledIconButton(onClick = { capturedBitmap = null }, enabled = !sending) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Retake")
                }
                FilledIconButton(
                    onClick = {
                        val uid = authRepository.currentUser?.uid ?: return@FilledIconButton
                        sending = true
                        scope.launch {
                            val file = File(context.cacheDir, "selfie_send_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                            chatRepository.sendImageMessage(chatId, uid, Uri.fromFile(file))
                            sending = false
                            onSent()
                        }
                    },
                    enabled = !sending
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

private fun decodeBitmapWithExif(file: File): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
    val orientation = runCatching {
        ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }
    return if (!matrix.isIdentity) {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}

private fun detectAndComposite(bitmap: Bitmap, filter: FaceFilter, onResult: (Bitmap) -> Unit) {
    if (filter == FaceFilter.NONE) {
        onResult(bitmap)
        return
    }
    val options = FaceDetectorOptions.Builder()
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .build()
    val detector = FaceDetection.getClient(options)
    val inputImage = InputImage.fromBitmap(bitmap, 0)
    detector.process(inputImage)
        .addOnSuccessListener { faces ->
            val face = faces.firstOrNull()
            onResult(if (face != null) FaceFilterCompositor.apply(bitmap, face, filter) else bitmap)
        }
        .addOnFailureListener {
            onResult(bitmap)
        }
}
