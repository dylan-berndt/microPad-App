package com.example.micropad.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.SampleDataset
import com.example.micropad.R

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
fun WellNamingGrid(dataset: SampleDataset) {
    val numberOfDots = dataset.samples[0].rgb.size
    val scrollState = rememberScrollState()

    var texts = remember { mutableStateListOf<String>().apply {repeat(numberOfDots) { add("") } } }

    Column (modifier = Modifier.verticalScroll(scrollState).fillMaxWidth()) {
        for (i in 0 until numberOfDots) {
            Box {
                TextField(
                    value = texts[i],
                    onValueChange = { dataset.nameWell(i, it); texts[i] = it },
                    label = { Text(text = "ROI ${i + 1}") },
                    placeholder = { Text("Enter a Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()

                )
            }
        }
    }
}

// Main composable for Well Naming screen
@Composable
fun WellNamingScreen(addresses: List<Uri>, viewModel: DatasetModel, navController: NavController) {
    val context = LocalContext.current

    val strategies = listOf("Mean", "Center")
    var selectionStrategy by remember {mutableStateOf("Mean")}

    LaunchedEffect(addresses, selectionStrategy) {
        viewModel.ingest(addresses, context, selectionStrategy)
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
                            }
                        }
                    }
                }

                WellNamingGrid(dataset)

                val isDropDownExpanded = remember {
                    mutableStateOf(false)
                }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isDropDownExpanded.value = true
                    }
                ) {
                    Text(text = "Dye Extraction Strategy: $selectionStrategy ")
                    Image(
                        painter = painterResource(id = R.drawable.dropdown),
                        contentDescription = "DropDown Icon",
                        modifier = Modifier.size(15.dp)
                    )
                }

                DropdownMenu(
                    expanded = isDropDownExpanded.value,
                    onDismissRequest = {
                        isDropDownExpanded.value = false
                    }) {
                    strategies.forEachIndexed { index, strategy ->
                        DropdownMenuItem(text = {
                            Text(text = strategy)
                        },
                            onClick = {
                                isDropDownExpanded.value = false
                                selectionStrategy = strategy
                            })
                    }
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