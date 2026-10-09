package com.kissansevaai.ui.component

import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kissansevaai.ui.theme.Emerald500
import com.kissansevaai.ui.theme.Slate900
import com.kissansevaai.ui.theme.Slate950
import java.io.File

/**
 * CameraX Capture Dialog for photo capture in Crop and Livestock features.
 */
@Composable
fun CameraCaptureDialog(
    onDismissRequest: () -> Unit,
    onImageCaptured: (Uri) -> Unit,
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCapturing by remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Slate950)
        ) {
            // Camera Preview View
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraCaptureDialog", "Camera binding failed", e)
                            onError("Camera initialization failed: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraCaptureDialog", "Camera re-binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )

            // Top Bar: Close and Switch lens
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismissRequest,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Slate900.copy(alpha = 0.7f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Camera", tint = Color.White)
                }

                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Slate900.copy(alpha = 0.7f))
                ) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                }
            }

            // Bottom Bar: Shutter Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (isCapturing) return@IconButton
                        isCapturing = true

                        val photoFile = File(
                            context.cacheDir,
                            "camera_capture_${System.currentTimeMillis()}.jpg"
                        )
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    isCapturing = false
                                    val savedUri = Uri.fromFile(photoFile)
                                    onImageCaptured(savedUri)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    Log.e("CameraCaptureDialog", "Photo capture failed", exception)
                                    onError("Capture failed: ${exception.message}")
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Emerald500)
                        .border(4.dp, Color.White, CircleShape),
                    enabled = !isCapturing
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}
