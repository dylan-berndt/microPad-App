package com.example.micropad.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.LabeledImage

/**
 * Screen to label a batch of images sequentially.
 */
@Composable
fun LabelingScreen(viewModel: DatasetModel, navController: NavController) {
    val uris = viewModel.temporaryUris
    val isReference = viewModel.labelingTargetIsReference
    
    var currentIndex by remember { mutableIntStateOf(0) }
    var currentLabel by remember { mutableStateOf("") }
    val labels = remember { mutableStateListOf<String>() }

    if (uris.isEmpty() || currentIndex >= uris.size) {
        LaunchedEffect(Unit) { navController.popBackStack("home", false) }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Label ${if (isReference) "Reference" else "Sample"} (${currentIndex + 1}/${uris.size})",
            style = MaterialTheme.typography.headlineSmall
        )

        AsyncImage(
            model = uris[currentIndex],
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(300.dp).padding(vertical = 24.dp),
            contentScale = ContentScale.Fit
        )

        OutlinedTextField(
            value = currentLabel,
            onValueChange = { currentLabel = it },
            label = { Text("Enter Name (e.g. H2O, Sample A)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = {
                    if (currentIndex > 0) {
                        currentIndex--
                        currentLabel = labels[currentIndex]
                        labels.removeAt(currentIndex)
                    } else {
                        navController.popBackStack()
                    }
                }
            ) { Text("Back") }

            Button(
                onClick = {
                    labels.add(currentLabel)
                    if (currentIndex < uris.size - 1) {
                        currentIndex++
                        currentLabel = ""
                    } else {
                        // Finish: Map URIs to LabeledImages and add to ViewModel
                        val labeledResults = uris.mapIndexed { i, uri -> LabeledImage(uri, labels[i]) }
                        if (isReference) {
                            viewModel.pendingReferences.addAll(labeledResults)
                        } else {
                            viewModel.pendingSamples.addAll(labeledResults)
                        }
                        viewModel.temporaryUris = emptyList()
                        navController.popBackStack("home", false)
                    }
                },
                enabled = currentLabel.isNotBlank()
            ) {
                Text(if (currentIndex < uris.size - 1) "Next Image" else "Finish")
            }
        }
    }
}
