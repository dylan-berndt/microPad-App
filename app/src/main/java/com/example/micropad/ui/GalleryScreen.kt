package com.example.micropad.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.micropad.data.ingestImages

@Composable
fun GalleryPickerScreen() {
    val context = LocalContext.current

    // 1. STATE: Store the URI of the selected image
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // 2. LAUNCHER: Register the activity for launching
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems=50)
    ) { uris ->
        ingestImages(uris, context)
        selectedImageUris = uris  // Update the state when the user picks an image
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
                Text("Select Images")
            }
        }
    ) { innerPadding ->
        // 4. MAIN CONTENT: Display the selected image
        Box(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUris.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedImageUris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                Text("No Images Selected")
            }
        }
    }
}