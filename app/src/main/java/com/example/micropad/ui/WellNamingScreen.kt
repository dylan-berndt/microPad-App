package com.example.micropad.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.micropad.data.Sample
import com.example.micropad.data.SampleDataset
import com.example.micropad.data.ingestImages
import com.example.coloranalysisapp.data.DyeLabels
import kotlinx.coroutines.launch

// Convert URI list to String
fun urisToString(addresses: List<Uri>): String {
    val uriStrings = addresses.map { it.toString() }
    return Uri.encode(uriStrings.joinToString(","))
}

// Convert String back to URI list
fun stringToURIs(data: String): List<Uri> {
    return if (data.isNotEmpty()) {
        data.split(",").map { it.toUri() }
    } else emptyList()
}

// ViewModel to handle image dataset
class ImageViewModel : ViewModel() {
    // Current dataset property
    var dataset by mutableStateOf<SampleDataset?>(null)
        private set

    // Current loading state
    var isLoading by mutableStateOf(false)
        private set

    // Your ingest function
    fun ingest(uris: List<Uri>, context: Context) {
        viewModelScope.launch {
            isLoading = true
            dataset = ingestImages(uris, context, log=true)
            isLoading = false
        }
    }

    fun allSamplesValid(): Boolean {
        // Checks all samples in the dataset
        return dataset?.samples?.all { it.validateLabels() } ?: false
    }
}

// Composable to display ROIs 2 per row
@Composable
fun WellNamingGrid(sample: Sample) {
    val numberOfDots = sample.rgb.size

    for (i in 0 until numberOfDots step 2) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (j in i until (i + 2).coerceAtMost(numberOfDots)) {
                Column {
                    Text(text = "ROI ${j + 1}")

                    var expanded by remember { mutableStateOf(false) }
                    val currentLabel = sample.names[j]

                    Box {
                        Button(onClick = { expanded = true }) {
                            Text(if (currentLabel.isBlank()) "Select Label" else currentLabel)
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DyeLabels.predefinedLabels.forEach { label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        val success = sample.reassignLabel(j, label)
                                        if (success) expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// Main composable for Well Naming screen
@Composable
fun WellNamingScreen(addresses: List<Uri>, viewModel: ImageViewModel = viewModel()) {
    val context = LocalContext.current

    LaunchedEffect(addresses) {
        viewModel.ingest(addresses, context)
    }

    if (viewModel.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        viewModel.dataset?.let { dataset ->
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(dataset.samples) { sample ->
                        if (sample.ordering != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Image(
                                    bitmap = sample.ordering!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                WellNamingGrid(sample)
                            }
                        }
                    }
                }

                // NEXT BUTTON
                Button(
                    onClick = {
                        // Navigate or proceed with analysis
                        println("All labels valid! Proceeding...")
                    },
                    enabled = viewModel.allSamplesValid(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Next")
                }

                // Show warning if not valid
                if (!viewModel.allSamplesValid()) {
                    Text(
                        text = "Please assign all labels before continuing",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}