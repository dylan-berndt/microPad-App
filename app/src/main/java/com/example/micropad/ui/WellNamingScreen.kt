package com.example.micropad.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.SampleDataset
import com.example.micropad.data.drawOrdering

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

@Composable
fun WellNamingGrid(dataset: SampleDataset, onFocusChange: (Int?) -> Unit) {
    val numberOfDots = dataset.samples.getOrNull(0)?.rgb?.size ?: 0
    val scrollState = rememberScrollState()

    // Use the names from the first sample as they are kept in sync across all samples
    val names = dataset.samples.getOrNull(0)?.names ?: mutableStateListOf()

    Column (modifier = Modifier.verticalScroll(scrollState).fillMaxWidth()) {
        for (i in 0 until numberOfDots) {
            Box {
                TextField(
                    value = if (i < names.size) names[i] else "",
                    onValueChange = { dataset.nameWell(i, it) },
                    label = { Text(text = "ROI ${i + 1}") },
                    placeholder = { Text("Enter a Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                onFocusChange(i)
                            }
                        }
                )
            }
        }
    }
}

// Main composable for Well Naming screen
@Composable
fun WellNamingScreen(addresses: List<Uri>, viewModel: DatasetModel, navController: NavController) {
    val context = LocalContext.current
    var focusedIndex by remember { mutableStateOf<Int?>(null) }

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
        viewModel.newDataset?.let { dataset ->
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // TODO: Implement reordering in the case ROI are out of order
                    items(dataset.samples) { sample ->
                        val displayBitmap = remember(sample, focusedIndex) {
                            if (focusedIndex != null && sample.balanced != null) {
                                drawOrdering(sample.balanced, sample.dots, focusedIndex)
                            } else {
                                sample.ordering
                            }
                        }

                        if (displayBitmap != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Image(
                                    bitmap = displayBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )
                            }
                        }
                    }
                }

                WellNamingGrid(dataset) { index ->
                    focusedIndex = index
                }

                // NEXT BUTTON
                Button(
                    onClick = {
                        navController.navigate("import")
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