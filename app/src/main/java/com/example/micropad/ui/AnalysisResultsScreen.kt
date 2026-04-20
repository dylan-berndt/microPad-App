package com.example.micropad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.micropad.data.ClassificationResult
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.writeToCsv
import org.opencv.core.Scalar

/**
 * Display the results of a classification run.
 *
 * The screen shows a scrollable list of sample result cards, one per sample,
 * and a persistent pair of export buttons fixed at the bottom of the screen.
 *
 * @param viewModel The view model for the app.
 * @param navController The navigation controller for the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(viewModel: DatasetModel, navController: NavController) {
    val dataset = viewModel.newDataset

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis Results") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->

        if (dataset == null || dataset.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No analysis results available.\nPlease scan a micropad first.",
                    color = Color.Gray
                )
            }
            return@Scaffold
        }

        val initialName = viewModel.importedFileName
        val context = LocalContext.current
        var export by remember { mutableStateOf("references") }

        val refLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv")
        ) { uri ->
            var csvData = ""
            if (export == "references") {
                csvData = viewModel.toCsvString(includeHeader = true, datasetChoice = "references")
            }
            else if (export == "samples") {
                csvData = viewModel.toCsvString(includeHeader = true, datasetChoice = "sample")
            }
            else {
                csvData = viewModel.toCsvString(includeHeader = true, datasetChoice = "references")
                csvData += "\n" + viewModel.toCsvString(includeHeader = false, datasetChoice = "sample")
            }

            if (uri != null) writeToCsv(csvData, "references", uri, context)
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dataset.samples.forEachIndexed { sampleIndex, sample ->
                    item {
                        SampleResultCard(
                            viewModel = viewModel,
                            sampleIndex = sampleIndex,
                            result = sample.classificationResults.firstOrNull(),
                            distanceMetric = viewModel.distanceMetric
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            Text("Export", modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.CenterHorizontally)
            )
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                    .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            export = "samples"
                            refLauncher.launch(initialName)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Categorized Samples")
                    }

                    Button(
                        onClick = {
                            export = "references"
                            refLauncher.launch(initialName)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reference Data")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            export = "combined"
                            refLauncher.launch(initialName)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Combined Dataset")
                    }
                }
            }
        }
    }
}

/**
 * Display the results of a single sample classification.
 */
@Composable
fun SampleResultCard(
    viewModel: DatasetModel,
    sampleIndex: Int,
    result: ClassificationResult?,
    distanceMetric: String
) {
    val sample = viewModel.newDataset?.samples?.getOrNull(sampleIndex) ?: return
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = sample.referenceName,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (result == null) {
                Text(
                    text = "No results available for this sample.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                return@Column
            }

            Text("Detected Compound:", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                text = result.closestReferenceName,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            val totalDistText = "%.2f".format(result.totalDistance)
            // Estimate max distance for coloring (RGB max diff is 441 per well)
            val maxExpected = 441.0 * result.wellNames.size
            val fraction = (result.totalDistance / maxExpected).toFloat().coerceIn(0f, 1f)
            val distanceColor = lerpColor(Color(0xFF4CAF50), Color(0xFFF44336), fraction)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Card Distance",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Metric: $distanceMetric",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Text(
                    text = totalDistText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = distanceColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable header for well details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Dye Well Distances",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    result.wellNames.forEachIndexed { i, name ->
                        WellResultRow(
                            name = name,
                            sampleColor = result.sampleColors[i],
                            referenceColor = result.referenceColors[i],
                            distance = result.wellDistances[i]
                        )
                    }
                }
            }
        }
    }
}

/**
 * Display the results of a single well, comparing sample and reference colors.
 */
@Composable
fun WellResultRow(
    name: String,
    sampleColor: Scalar,
    referenceColor: Scalar,
    distance: Double
) {
    val distText = "%.2f".format(distance)
    val maxExpected = 441.0
    val fraction = (distance / maxExpected).toFloat().coerceIn(0f, 1f)
    val distanceColor = lerpColor(Color(0xFF4CAF50), Color(0xFFF44336), fraction)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.2f)) {
            Text(text = name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = "Dye Well", fontSize = 11.sp, color = Color.Gray)
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorSwatch(color = sampleColor, label = "S")
            Spacer(modifier = Modifier.width(8.dp))
            ColorSwatch(color = referenceColor, label = "R")
        }

        Column(
            modifier = Modifier.weight(0.8f),
            horizontalAlignment = Alignment.End
        ) {
            Text(text = "Distance", fontSize = 11.sp, color = Color.Gray)
            Text(
                text = distText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = distanceColor
            )
        }
    }
}

@Composable
fun ColorSwatch(color: Scalar, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    Color(
                        (color.`val`[0] / 255.0).toFloat().coerceIn(0f, 1f),
                        (color.`val`[1] / 255.0).toFloat().coerceIn(0f, 1f),
                        (color.`val`[2] / 255.0).toFloat().coerceIn(0f, 1f)
                    ),
                    shape = CircleShape
                )
        )
        Text(text = label, fontSize = 9.sp, color = Color.Gray)
    }
}

/**
 * Linearly interpolate between two colors.
 */
fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red   = start.red   + (end.red   - start.red)   * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue  = start.blue  + (end.blue  - start.blue)  * fraction
    )
}
