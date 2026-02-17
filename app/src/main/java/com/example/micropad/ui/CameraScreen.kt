// Integrating camera functionality
package com.example.micropad.ui

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import java.io.File

/**
 * A screen that provides a camera preview, allows capturing an image,
 * and shows a preview of the captured image.
 *
 * @param onImageCapture A callback invoked when the user confirms they want to use the captured image.
 */
@Composable
fun CameraScreen(onImageCapture: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State to hold the URI of the captured image. A null value means no image has been captured yet.
    var capturedImageUri: Uri? by remember { mutableStateOf(null) }

    // Create and remember the camera controller, configuring it for image capture.
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    // Bind the camera controller to the lifecycle of the owner.
    LaunchedEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
    }

    // Display the camera preview or the image preview based on whether an image has been captured.
    if (capturedImageUri == null) {
        CameraPreview(
            controller = cameraController,
            onCapture = { uri ->
                capturedImageUri = uri
            }
        )
    } else {
        // Use a non-null assertion because we've already checked that capturedImageUri is not null.
        ImagePreviewScreen(
            imageUri = capturedImageUri!!,
            onRetake = { capturedImageUri = null },
            onUsePhoto = onImageCapture
        )
    }
}

/**
 * A composable that displays the camera preview and a capture button.
 *
 * @param controller The camera controller instance.
 * @param onCapture A callback invoked with the URI of the captured image.
 */
@Composable
fun CameraPreview(
    controller: LifecycleCameraController,
    onCapture: (Uri) -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Display the camera preview feed.
        AndroidView(
            factory = {
                PreviewView(it).apply {
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Capture button.
        Button(
            onClick = {
                // Create a file to store the captured image.
                val photoFile = File(
                    context.cacheDir,
                    "photo_${System.currentTimeMillis()}.jpg"
                )

                val outputOptions =
                    ImageCapture.OutputFileOptions.Builder(photoFile).build()

                // Take the picture and handle the result.
                controller.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(
                            outputFileResults: ImageCapture.OutputFileResults
                        ) {
                            onCapture(Uri.fromFile(photoFile))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            Text("Capture")
        }
    }
}

/**
 * A screen that displays the captured image and provides options to "Retake" or "Use Photo".
 *
 * @param imageUri The URI of the image to display.
 * @param onRetake A callback invoked when the user wants to retake the photo.
 * @param onUsePhoto A callback invoked when the user confirms they want to use the photo.
 */
@Composable
fun ImagePreviewScreen(
    imageUri: Uri,
    onRetake: () -> Unit,
    onUsePhoto: (Uri) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Display the captured image using Coil's AsyncImage.
        AsyncImage(
            model = imageUri,
            contentDescription = "Captured Image Preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Buttons for user actions.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            Button(onClick = onRetake) {
                Text("Retake")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(onClick = { onUsePhoto(imageUri) }) {
                Text("Use Photo")
            }
        }
    }
}
