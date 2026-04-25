package com.example.micropad.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A reusable flow that allows users to pick multiple images from the gallery.
 */
@Composable
fun GalleryReferenceFlow(
    onImagesPicked: (List<Uri>) -> Unit,
    onCancel: () -> Unit,
    isSimulating: Boolean = false
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImagesPicked(uris)
        } else {
            onCancel()
        }
    }

    // Launch the picker immediately when the screen enters the composition
    LaunchedEffect(Unit) {
        if (!isSimulating) {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    if (isSimulating) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Accessing Photo Gallery...")
        }
    }
}
