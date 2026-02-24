package com.example.micropad.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import android.os.Environment

@Composable
fun GalleryPickerScreen(
    selectedImageUris: List<Uri>,
    onImagesPicked: (List<Uri>) -> Unit,
    onNextClicked: () -> Unit
) {
    val context = LocalContext.current

    // 1. STATE: Store the URI of the selected image
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val imageFiles = downloadDir.listFiles { file ->
        file.extension.lowercase() in listOf("png", "jpg", "jpeg")
    }?.toList() ?: emptyList()

    // 2. LAUNCHER: Register the activity for launching
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        onImagesPicked(uris)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // 3. BOTTOM BUTTON: Place the button at the Scaffold's bottom bar
            Button(
                onClick = {
                    // Launch the gallery photo picker
                    photoPickerLauncher.launch(arrayOf("image/*"))
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

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {

                    LazyColumn(
                        modifier = Modifier.weight(1f),
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

                    Button(
                        onClick = {
                            onNextClicked()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Next Step")
                    }
                }
            } else {
                Text("No Images Selected")
            }
        }
    }
}