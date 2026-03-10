package com.example.micropad.ui.camera

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

enum class ImageLabel {
    REFERENCE, SAMPLE
}

data class LabeledImage(
    val uri: Uri,
    val label: ImageLabel
)

/**
 * Screen to label a captured image and decide whether to capture more or finish.
 */
@Composable
fun LabelingScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    onConfirm: (ImageLabel) -> Unit
) {
    var selectedLabel by remember { mutableStateOf<ImageLabel?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Label this image",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        AsyncImage(
            model = imageUri,
            contentDescription = "Image to label",
            modifier = Modifier
                .size(300.dp)
                .padding(bottom = 24.dp),
            contentScale = ContentScale.Fit
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = selectedLabel == ImageLabel.REFERENCE,
                onClick = { selectedLabel = ImageLabel.REFERENCE },
                label = { Text("Reference") }
            )
            FilterChip(
                selected = selectedLabel == ImageLabel.SAMPLE,
                onClick = { selectedLabel = ImageLabel.SAMPLE },
                label = { Text("Sample") }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
            Button(
                onClick = { selectedLabel?.let { onConfirm(it) } },
                enabled = selectedLabel != null
            ) {
                Text("Confirm Label")
            }
        }
    }
}

/**
 * Prompt shown after labeling an image to decide next steps.
 */
@Composable
fun NextStepPrompt(
    capturedCount: Int,
    onCaptureMore: () -> Unit,
    onProcess: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Image labeled successfully!",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Total images captured: $capturedCount",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        Button(
            onClick = onCaptureMore,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text("Capture More Images")
        }

        OutlinedButton(
            onClick = onProcess,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send for Processing")
        }
    }
}
