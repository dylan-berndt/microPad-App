package com.example.micropad.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.example.micropad.R
import com.example.micropad.data.CsvExportButton
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.SampleDataset
import com.example.micropad.data.drawOrdering
import com.example.micropad.data.estimateWellRadius
import kotlinx.coroutines.launch
import org.opencv.core.Point

/**
 * Convert a list of URIs to a comma-separated string.
 */
fun urisToString(addresses: List<Uri>): String {
    val uriStrings = addresses.map { it.toString() }
    return Uri.encode(uriStrings.joinToString(","))
}

/**
 * Convert a comma-separated string of URIs to a list of URIs.
 */
fun stringToURIs(data: String): List<Uri> {
    return if (data.isNotEmpty()) {
        data.split(",").map { it.toUri() }
    } else emptyList()
}

/**
 * Display a grid of well names.
 */
@Composable
fun WellNamingGrid(dataset: SampleDataset, onFocusChange: (Int?) -> Unit) {
    val numberOfDots = dataset.samples.getOrNull(0)?.rgb?.size ?: 0

    val texts = remember(numberOfDots, dataset.samples.getOrNull(0)?.names?.toList()) {
        mutableStateListOf<String>().apply {
            val existingNames = dataset.samples.getOrNull(0)?.names ?: emptyList()
            repeat(numberOfDots) { i ->
                add(existingNames.getOrNull(i) ?: "")
            }
        }
    }
    val selected = remember(numberOfDots, dataset.samples.getOrNull(0)?.isSelected?.toList()) {
        mutableStateListOf<Boolean>().apply {
            val existingSelected = dataset.samples.getOrNull(0)?.isSelected ?: emptyList()
            repeat(numberOfDots) { i ->
                add(existingSelected.getOrNull(i) ?: true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Selected", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
            Text("Region of Interest Name", style = MaterialTheme.typography.labelMedium)
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(texts) { i, _ ->
                val isSelected = if (i < selected.size) selected[i] else true
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selected[i],
                        onCheckedChange = { dataset.toggleWell(i, it); selected[i] = it },
                        modifier = Modifier.size(48.dp)
                    )
                    OutlinedTextField(
                        value = texts[i],
                        onValueChange = { dataset.nameWell(i, it); texts[i] = it },
                        label = { Text("ROI ${i + 1}") },
                        placeholder = { Text("Enter a Label") },
                        singleLine = true,
                        enabled = isSelected,
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
}

/**
 * Display a grid for ordering wells.
 */
@Composable
fun WellOrderingGrid(dataset: SampleDataset, sampleIndex: Int) {
    var from by remember { mutableIntStateOf(-1) }
    var to by remember { mutableIntStateOf(-1) }

    if (sampleIndex == -1) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select an Image Card above", color = Color.Gray)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Select two ROIs to swap", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))

        if (from != -1 && to != -1) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("ROI ${from + 1} ↔ ROI ${to + 1}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    dataset.reorderSample(sampleIndex, from, to)
                    from = -1
                    to = -1
                }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Swap Positions", fontSize = 12.sp)
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(dataset.samples[sampleIndex].dots) { i, _ ->
                Surface(
                    onClick = {
                        if (from == -1) { from = i }
                        else if (to == -1 && from != i) { to = i }
                        else if (from != i) { from = to; to = i }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = if (i == from || i == to) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        RadioButton(i == from || i == to, null)
                        Text("ROI ${i + 1}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}


@Composable
private fun ManualRoiPickerDialog(
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit,
    onPointSelected: (Point) -> Unit
) {
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tap missed dye spot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("The new ROI will be added to every card using the same layout position.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "First card for manual ROI selection",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { imageSize = it }
                        .pointerInput(bitmap, imageSize) {
                            detectTapGestures { offset ->
                                if (imageSize.width == 0 || imageSize.height == 0) return@detectTapGestures
                                val x = offset.x * bitmap.width / imageSize.width
                                val y = offset.y * bitmap.height / imageSize.height
                                onPointSelected(Point(x.toDouble(), y.toDouble()))
                            }
                        }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
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
    val openAlertDialog = remember { mutableStateOf(false) }
    var showManualRoiPicker by remember { mutableStateOf(false) }
    var manualWellVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.ingestAllPending(context) {}
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Process & Name Wells", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Processing Images...", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            val combinedSamples = mutableListOf<com.example.micropad.data.Sample>()
            viewModel.referenceDataset?.samples?.let { it1 -> combinedSamples.addAll(it1.filter { it2 -> it2.isImage }) }
            viewModel.newDataset?.samples?.let { combinedSamples.addAll(it) }

            if (combinedSamples.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text("No data to process", color = Color.Gray)
                }
                return@Scaffold
            }

            if (openAlertDialog.value) {
                val csvData = viewModel.toCsvString(datasetChoice="reference")
                AlertDialog(
                    title = { Text("Export Reference Data") },
                    text = { Text("You have only imported reference data. Move on to export a reference dataset.") },
                    onDismissRequest = { openAlertDialog.value = false },
                    confirmButton = { CsvExportButton(type = "references", dataRows = csvData, navHome = {
                        viewModel.reset()
                        navController.navigate("home")
                    }) },
                    dismissButton = { TextButton(onClick = { openAlertDialog.value = false }) { Text("Return") } }
                )
            }

            val firstSample = combinedSamples.firstOrNull()

            if (showManualRoiPicker && firstSample?.ordering != null) {
                ManualRoiPickerDialog(
                    bitmap = firstSample.ordering!!,
                    onDismiss = { showManualRoiPicker = false },
                    onPointSelected = { point ->
                        val radius = estimateWellRadius(firstSample.dots)
                        combinedSamples.forEach { it.addManualDot(point, radius, viewModel.selectionStrategy) }
                        manualWellVersion++
                        selectedImage = 0
                        focusedIndex = combinedSamples.firstOrNull()?.dots?.lastIndex
                        showManualRoiPicker = false
                    }
                )
            }

            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(combinedSamples) { index, sample ->
                        val isSelected = index == selectedImage
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clickable { selectedImage = index },
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                val displayBitmap = remember(sample.ordering, focusedIndex, sample.isSelected.toList(), manualWellVersion) {
                                    sample.ordering?.let { drawOrdering(sample.imageData ?: return@let it, sample.dots, focusedIndex, sample.isSelected) }
                                }
                                if (displayBitmap != null) {
                                    Image(
                                        bitmap = displayBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.weight(1.2f).fillMaxHeight()
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = sample.referenceName.ifBlank { "Sample ${index + 1}" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        itemsIndexed(sample.names) { i, name ->
                                            if (name.isNotBlank()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "${i + 1}. $name",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    val scalar = sample.dots[i].second
                                                    val dotColor = Color(
                                                        red = scalar.`val`[0].toInt().coerceIn(0, 255),
                                                        green = scalar.`val`[1].toInt().coerceIn(0, 255),
                                                        blue = scalar.`val`[2].toInt().coerceIn(0, 255),
                                                        alpha = 255
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .clip(CircleShape)
                                                            .background(dotColor)
                                                            .border(0.5.dp, Color.LightGray, CircleShape)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = { showManualRoiPicker = true; selectedImage = 0 }) {
                        Text("Add missed ROI")
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Column {
                        val tempDataset = remember(combinedSamples) { SampleDataset(combinedSamples) }
                        val tabNames = listOf("ROI Ordering", "ROI Naming")
                        val pagerState = rememberPagerState(pageCount = { 2 })
                        val scope = rememberCoroutineScope()

                        TabRow(selectedTabIndex = pagerState.currentPage) {
                            tabNames.forEachIndexed { index, name ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    text = { Text(name, style = MaterialTheme.typography.titleSmall) }
                                )
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f).padding(16.dp)
                        ) { page ->
                            if (page == 0) WellOrderingGrid(tempDataset, selectedImage)
                            else WellNamingGrid(tempDataset) { focusedIndex = it }
                        }

                        var isDropDownExpanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDropDownExpanded = true }
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Dye Extraction: ${viewModel.selectionStrategy} ", style = MaterialTheme.typography.bodySmall)
                            Icon(painterResource(R.drawable.dropdown), null, Modifier.size(12.dp))
                            DropdownMenu(expanded = isDropDownExpanded, onDismissRequest = { isDropDownExpanded = false }) {
                                strategies.forEach { strategy ->
                                    DropdownMenuItem(
                                        text = { Text(strategy) },
                                        onClick = { viewModel.selectionStrategy = strategy; isDropDownExpanded = false }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (viewModel.newDataset != null) navController.navigate("options")
                                else openAlertDialog.value = true
                            },
                            enabled = viewModel.allSamplesValid(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Next")
                        }
                    }
                }
            }
        }
    }
}
