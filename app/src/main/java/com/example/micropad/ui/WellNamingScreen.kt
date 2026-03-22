package com.example.micropad.ui

import android.R.style.Theme
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.SampleDataset
import kotlinx.coroutines.selects.select
import com.example.micropad.data.drawOrdering
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

@Composable
fun WellNamingGrid(dataset: SampleDataset, onFocusChange: (Int?) -> Unit) {
    val numberOfDots = dataset.samples.getOrNull(0)?.rgb?.size ?: 0
    val scrollState = rememberScrollState()

    var texts = remember { mutableStateListOf<String>().apply {repeat(numberOfDots) { add("") } } }
    var selected = remember { mutableStateListOf<Boolean>().apply {repeat(numberOfDots) { add(true) } } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().height(20.dp)) {
            Text("Selected", modifier = Modifier.fillMaxWidth(0.2f))
            Text("Region of Interest Name", modifier = Modifier.fillMaxWidth(0.8f))
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(texts) { i, text ->
                val isSelected = if (i < selected.size) selected[i] else true
                Box {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            selected[i], { dataset.toggleWell(i, it); selected[i] = it },
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


// Creates a tab for editing the ordering of dye dots in the dataset
@Composable
fun WellOrderingGrid(dataset: SampleDataset, sampleIndex: Int) {
    var from by remember { mutableIntStateOf(-1) };
    var to by remember { mutableIntStateOf(-1) };
    val scrollState = rememberScrollState()

    // No image has been selected
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

        // If the user has selected a dot to transfer from and to, allow them to reorder
        if (from != -1 && to != -1) {
            Text("ROI ${from + 1} <-> ROI ${to + 1}")
            Button({
                dataset.reorderSample(sampleIndex, from, to)
                from = -1
                to = -1
            }) {
                Text("Reorder samples")
            }
        }

        LazyColumn() {
            itemsIndexed(dataset.samples[sampleIndex].dots) { i, dot ->
                // Create a selection button for each ROI
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            // If the user selects this radio button
                            // and this button is not already selected,
                            // select the region to be the from or to, in order
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


// Main composable for Well Naming screen
@Composable
fun WellNamingScreen(addresses: List<Uri>, viewModel: DatasetModel, navController: NavController) {
    val context = LocalContext.current
    var focusedIndex by remember { mutableStateOf<Int?>(null) }

    var selectedImage by remember { mutableIntStateOf(-1) }

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

            Column(modifier = Modifier.fillMaxWidth().padding(top=36.dp, bottom=36.dp)) {
                LazyColumn(
                    modifier = Modifier.weight(1f).align(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(dataset.samples) { index, sample ->
                        var modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(200.dp)
                            .clickable(onClick = { selectedImage = index })
                        if (index == selectedImage) {
                            modifier = modifier
                                .border(width=2.dp, color=MaterialTheme.colorScheme.primary)
                                .background(MaterialTheme.colorScheme.secondary)
                        }

                        // Yeesh
                        val displayBitmap = remember(sample.ordering, focusedIndex, sample.isSelected.toList()) {
                            sample.ordering?.let { drawOrdering(sample.balanced ?: sample.imageData ?: return@let it, sample.dots, focusedIndex, sample.isSelected) }
                        }

                        if (displayBitmap != null) {
                            Row(modifier = modifier) {
                                Image(
                                    bitmap = displayBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f / 0.9f)
                                        .height(200.dp)
                                )
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    itemsIndexed(sample.names) { i, name ->
                                        if (name != "") Text("${i + 1}: ${name}") else null
                                    }
                                }
                            }
                        }
                    }
                }

                val tabs: List<@Composable () -> Unit> = listOf(
                    { WellNamingGrid(dataset) { index -> focusedIndex = index } },
                    { WellOrderingGrid(dataset, selectedImage) }
                )
                val tabNames = listOf("ROI Naming", "ROI Ordering")
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
                    tabs.forEachIndexed { index, composable ->
                        Tab(
                            text = { Text(tabNames[index]) },
                            selected = pagerState.currentPage == index,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                        )
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
                        text = "Please assign all active labels before continuing",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}