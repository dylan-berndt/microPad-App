package com.example.micropad.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage

/**
 * Display a screen for selecting images from the device's gallery.
 *
 * @param navController The navigation controller for the app.
 * @receiver The Composable calling this function.
 * @return Unit
 */
@Composable
fun GalleryPickerScreen(navController: NavController) {
    val context = LocalContext.current

    // 1. STATE: Store the URI of the selected image
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // 2. LAUNCHER: Register the activity for launching
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems=50)
    ) { uris ->
        selectedImageUris = uris  // Update the state when the user picks an image
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                if (selectedImageUris.isNotEmpty()) {
                    val uriString = urisToString(selectedImageUris)
                    Button(
                        onClick = {navController.navigate("namingScreen/$uriString")},
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) { Text("Next Step") }
                }
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