package com.example.micropad.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * A reusable flow that allows users to pick multiple images from the file system.
 */
@Composable
fun GalleryReferenceFlow(
    onImagesPicked: (List<Uri>) -> Unit,
    onCancel: () -> Unit,
    isSimulating: Boolean = false
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val hasInvalidFormat = uris.any { uri ->
                val type = context.contentResolver.getType(uri)
                type != "image/jpeg" && type != "image/png"
            }

            if (hasInvalidFormat) {
                errorMessage = "One of the images you selected is in an invalid format. Only JPG and PNG are allowed."
            } else {
                onImagesPicked(uris)
            }
        } else {
            if (errorMessage == null) {
                onCancel()
            }
        }
    }

    // Launch the picker immediately when the screen enters the composition
    LaunchedEffect(Unit) {
        if (!isSimulating) {
            launcher.launch(arrayOf("*/*"))
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (errorMessage != null) {
            // Dim background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
            
            // Error overlay
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    errorMessage = null
                    launcher.launch(arrayOf("*/*"))
                }) {
                    Text("Back")
                }
            }
        } else if (isSimulating) {
            Text("Accessing File System...")
        }
    }
}
