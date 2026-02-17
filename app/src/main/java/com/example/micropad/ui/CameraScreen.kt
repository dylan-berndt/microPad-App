// Integrating camera functionality
package com.example.micropad.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import java.io.File

/**
 * A screen that handles camera permission and displays the camera preview.
 *
 * @param onImageCapture A callback invoked when the user confirms they want to use the captured image.
 */
@Composable
fun CameraScreen(onImageCapture: (Uri) -> Unit) {
    val context = LocalContext.current
    // We need the activity to check for permission rationale.
    val activity = context as? Activity

    // State to track if the camera permission is granted.
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Launcher for requesting camera permission.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
        }
    )

    // Request permission when the screen is first displayed if not already granted.
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Display content based on whether the permission is granted.
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraContent(onImageCapture = onImageCapture)
        } else {
            PermissionRationaleScreen(
                onRequestPermission = {
                    // If the user has permanently denied the permission, open app settings.
                    // Otherwise, launch the permission request again.
                    if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", context.packageName, null)
                        context.startActivity(intent)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            )
        }
    }
}

/**
 * The main content of the camera screen, shown when permission is granted.
 * This includes the camera preview and image preview logic.
 *
 * @param onImageCapture A callback invoked when the user confirms they want to use the captured image.
 */
@Composable
private fun CameraContent(onImageCapture: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State to hold the URI of the captured image.
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
 * A screen shown when camera permission is not granted, explaining why it's needed
 * and providing a button to grant it.
 *
 * @param onRequestPermission A callback to request the camera permission.
 */
@Composable
private fun PermissionRationaleScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Camera permission is required to use this feature. Please grant the permission to continue.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
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
