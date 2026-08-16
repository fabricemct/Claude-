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
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
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

/** Normalized (0..1) position of the most recently detected face, from the live analysis stream. */
private data class LiveFaceBox(val centerXRatio: Float, val centerYRatio: Float, val widthRatio: Float)

/**
 * Selfie capture with an emoji "funny filter" positioned on the actual
 * detected face (ML Kit). A live, approximate preview of the filter tracks
 * your face in the viewfinder (via [ImageAnalysis]); the final sent photo
 * re-detects the face on the still image and composites the filter there
 * precisely (eyes/nose-aligned, not just approximate), since that path
 * doesn't depend on getting camera rotation/mirroring exactly right — the
 * live preview is best-effort and may be slightly offset on some devices.
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
    var liveFace by remember { mutableStateOf<LiveFaceBox?>(null) }
    var lastWidthRatio by remember { mutableStateOf(0.35f) }
    // Non-null once the user has dragged the filter — from then on this exact
    // spot is used instead of auto face-tracking, both live and in the sent photo.
    var manualDisplayOffset by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(liveFace) {
        liveFace?.let { lastWidthRatio = it.widthRatio }
    }
    LaunchedEffect(selectedFilter) {
        manualDisplayOffset = null
    }

    val bitmap = capturedBitmap
    if (bitmap == null) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val boxWidthPx = constraints.maxWidth.toFloat()
            val boxHeightPx = constraints.maxHeight.toFloat()
            val density = LocalDensity.current

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
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    ContextCompat.getMainExecutor(viewContext),
                                    LiveFaceAnalyzer { face -> liveFace = face }
                                )
                            }
                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                capture,
                                analysis
                            )
                        }
                    }, ContextCompat.getMainExecutor(viewContext))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // The analysis frame isn't mirrored even though the front-camera
            // preview is, so the X axis is flipped to line the overlay back up.
            val face = liveFace
            val autoDisplay = face?.let {
                Offset(boxWidthPx * (1f - it.centerXRatio), boxHeightPx * it.centerYRatio)
            }
            val currentAutoDisplay by rememberUpdatedState(autoDisplay)
            val effectiveDisplay = manualDisplayOffset ?: autoDisplay

            if (effectiveDisplay != null && selectedFilter != FaceFilter.NONE) {
                val emojiSizePx = boxWidthPx * lastWidthRatio * 1.2f
                val emojiSizeSp = with(density) { emojiSizePx.toDp().toSp() }
                Text(
                    text = selectedFilter.emoji,
                    fontSize = emojiSizeSp,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (effectiveDisplay.x - emojiSizePx / 2f).toInt(),
                                (effectiveDisplay.y - emojiSizePx / 2f).toInt()
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val base = manualDisplayOffset
                                    ?: currentAutoDisplay
                                    ?: Offset(boxWidthPx / 2f, boxHeightPx / 2f)
                                manualDisplayOffset = base + dragAmount
                            }
                        }
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }

            if (selectedFilter != FaceFilter.NONE) {
                Text(
                    text = "Drag the emoji to reposition it",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .horizontalScroll(rememberScrollState()),
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
                    val manualOffset = manualDisplayOffset
                    val manualBitmapRatio = if (manualOffset != null && boxWidthPx > 0 && boxHeightPx > 0) {
                        Offset(1f - manualOffset.x / boxWidthPx, manualOffset.y / boxHeightPx)
                    } else {
                        null
                    }
                    val photoFile = File(context.cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val decoded = decodeBitmapWithExif(photoFile)
                                if (decoded == null) return
                                if (manualBitmapRatio != null && selectedFilter != FaceFilter.NONE) {
                                    val cx = manualBitmapRatio.x * decoded.width
                                    val cy = manualBitmapRatio.y * decoded.height
                                    val sizePx = decoded.width * lastWidthRatio * 1.2f
                                    capturedBitmap = FaceFilterCompositor.applyAt(decoded, selectedFilter, cx, cy, sizePx)
                                } else {
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

/** Runs ML Kit face detection on the live camera stream for the preview overlay. */
private class LiveFaceAnalyzer(
    private val onFaceDetected: (LiveFaceBox?) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val box = faces.firstOrNull()?.boundingBox
                onFaceDetected(
                    if (box == null) {
                        null
                    } else {
                        LiveFaceBox(
                            centerXRatio = box.exactCenterX() / inputImage.width,
                            centerYRatio = box.exactCenterY() / inputImage.height,
                            widthRatio = box.width().toFloat() / inputImage.width
                        )
                    }
                )
            }
            .addOnFailureListener { onFaceDetected(null) }
            .addOnCompleteListener { imageProxy.close() }
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
