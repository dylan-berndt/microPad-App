package com.example.micropad.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.SampleDataset
import com.example.micropad.data.drawOrdering
import kotlinx.coroutines.launch
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
fun WellNamingGrid(dataset: SampleDataset, onFocusChange: (Int?) -> Unit) {
    val numberOfDots = dataset.samples.getOrNull(0)?.rgb?.size ?: 0

    val texts = remember { mutableStateListOf<String>().apply { repeat(numberOfDots) { add("") } } }
    val selected = remember { mutableStateListOf<Boolean>().apply { repeat(numberOfDots) { add(true) } } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().height(20.dp)) {
            Text("Selected", modifier = Modifier.fillMaxWidth(0.2f))
            Text("Region of Interest Name", modifier = Modifier.fillMaxWidth(0.8f))
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(texts) { i, _ ->
                val isSelected = if (i < selected.size) selected[i] else true
                Box {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selected[i], 
                            onCheckedChange = { dataset.toggleWell(i, it); selected[i] = it },
                            modifier = Modifier.fillMaxWidth(0.2f)
                        )
                        TextField(
                            value = texts[i],
                            onValueChange = { dataset.nameWell(i, it); texts[i] = it },
                            label = { Text(text = "ROI ${i + 1}") },
                            placeholder = { Text("Enter a Label") },
                            singleLine = true,
                            enabled = isSelected,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
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
    }
}


@Composable
fun WellOrderingGrid(dataset: SampleDataset, sampleIndex: Int) {
    var from by remember { mutableIntStateOf(-1) }
    var to by remember { mutableIntStateOf(-1) }

    if (sampleIndex == -1) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Select an Image")
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Select two ROIs to swap")

        if (from != -1 && to != -1) {
            Text("ROI ${from + 1} <-> ROI ${to + 1}")
            Button(onClick = {
                dataset.reorderSample(sampleIndex, from, to)
                from = -1
                to = -1
            }) {
                Text("Reorder samples")
            }
        }

        LazyColumn {
            itemsIndexed(dataset.samples[sampleIndex].dots) { i, _ ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            if (from == -1) {
                                from = i
                            } else if (to == -1 && from != i) {
                                to = i
                            } else if (from != i) {
                                from = to
                                to = i
                            }
                        }
                        .padding(8.dp)) {
                    RadioButton(i == from || i == to, null)
                    Text("ROI ${i + 1}")
                }
            }
        }
    }
}

@Composable
fun WellNamingScreen(viewModel: DatasetModel, navController: NavController) {
    val context = LocalContext.current
    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    var selectedImage by remember { mutableIntStateOf(-1) }
    val strategies = listOf("Mean", "Center")
    var selectionStrategy by remember { mutableStateOf("Mean") }

    LaunchedEffect(Unit) {
        viewModel.ingestAllPending(context) {}
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
        val combinedSamples = mutableListOf<com.example.micropad.data.Sample>()
        viewModel.referenceDataset?.samples?.let { combinedSamples.addAll(it) }
        viewModel.newDataset?.samples?.let { combinedSamples.addAll(it) }

        if (combinedSamples.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data to process")
            }
            return
        }

        Column(modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 36.dp)) {
            LazyColumn(
                modifier = Modifier.weight(1f).align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(combinedSamples) { index, sample ->
                    var modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(200.dp)
                        .clickable(onClick = { selectedImage = index })
                        .border(width = 2.dp, color = MaterialTheme.colorScheme.primary)
                    if (index == selectedImage) {
                        modifier = modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    }

                    val displayBitmap = remember(sample.ordering, focusedIndex, sample.isSelected.toList()) {
                        sample.ordering?.let { drawOrdering(sample.imageData ?: return@let it, sample.dots, focusedIndex, sample.isSelected) }
                    }

                    if (displayBitmap != null) {
                        Row(modifier = modifier) {
                            Image(
                                bitmap = displayBitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth(0.6f / 0.95f)
                                    .height(200.dp)
                            )
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                itemsIndexed(sample.names) { i, name ->
                                    if (name != "") {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${i + 1}. $name", modifier = Modifier.padding(end = 5.dp))
                                            val scalar = sample.dots[i].second
                                            val color = Color(
                                                red = scalar.`val`[2].toInt().coerceIn(0, 255),
                                                green = scalar.`val`[1].toInt().coerceIn(0, 255),
                                                blue = scalar.`val`[0].toInt().coerceIn(0, 255),
                                                alpha = 255
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .background(color)
                                                    .border(width = 2.dp, color = MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Simplified for logic: wrap everything in a SampleDataset for the grids
            val tempDataset = SampleDataset(combinedSamples)

            val tabs: List<@Composable () -> Unit> = listOf(
                { WellOrderingGrid(tempDataset, selectedImage) },
                { WellNamingGrid(tempDataset) { index -> focusedIndex = index } }
            )
            val tabNames = listOf("ROI Ordering", "ROI Naming")
            val pagerState = rememberPagerState(pageCount = { tabs.size })
            val coroutineScope = rememberCoroutineScope()

            HorizontalPager(state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(all = 10.dp)) { page ->
                tabs[page]()
            }
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, _ ->
                    Tab(
                        text = { Text(tabNames[index]) },
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                    )
                }
            }

            var isDropDownExpanded by remember { mutableStateOf(false) }

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { isDropDownExpanded = true }
            ) {
                Text(text = "Dye Extraction Strategy: $selectionStrategy ")
                Icon(
                    painter = painterResource(id = R.drawable.dropdown),
                    contentDescription = "DropDown Icon",
                    modifier = Modifier.size(15.dp)
                )
            }

            DropdownMenu(
                expanded = isDropDownExpanded,
                onDismissRequest = { isDropDownExpanded = false }) {
                strategies.forEach { strategy ->
                    DropdownMenuItem(
                        text = { Text(text = strategy) },
                        onClick = {
                            isDropDownExpanded = false
                            selectionStrategy = strategy
                        })
                }
            }

            Button(
                onClick = { navController.navigate("import") },
                enabled = viewModel.allSamplesValid(),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Next")
            }

            if (!viewModel.allSamplesValid()) {
                Text(
                    text = "Please assign all active labels before continuing",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
