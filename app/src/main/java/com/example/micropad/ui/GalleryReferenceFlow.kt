package com.example.micropad.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*

/**
 * A reusable flow that allows users to pick multiple images from the gallery.
 */
@Composable
fun GalleryReferenceFlow(
    onImagesPicked: (List<Uri>) -> Unit,
    onCancel: () -> Unit
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
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
