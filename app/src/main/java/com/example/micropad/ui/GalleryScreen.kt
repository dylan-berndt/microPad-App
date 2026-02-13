package com.example.micropad.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun GalleryPickerScreen() {
    // 1. STATE: Store the URI of the selected image
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // 2. LAUNCHER: Register the activity for launching
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedImageUri = uri  // Update the state when the user picks an image
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // 3. BOTTOM BUTTON: Place the button at the Scaffold's bottom bar
            Button(
                onClick = {
                    // Launch the gallery photo picker
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Select Image")
            }
        }
    ) { innerPadding ->
        // 4. MAIN CONTENT: Display the selected image
        Box(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = "Selected Image",
                    modifier = Modifier.fillMaxWidth().height(400.dp)
                )
            } else {
                Text("No Image Selected")
            }
        }
    }
}