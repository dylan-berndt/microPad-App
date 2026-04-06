package com.example.micropad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

/**
 * Display the results of a classification run.
 *
 * The screen shows a scrollable list of sample result cards, one per sample,
 * and a persistent pair of export buttons fixed at the bottom of the screen.
 * The export buttons are placed outside the LazyColumn so that their activity
 * result launchers are always registered and never fall out of composition.
 *
 * @param viewModel The view model for the app.
 * @param navController The navigation controller for the app.
 * @receiver The Composable calling this function.
 * @return Unit
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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

        // Hoist CSV data and launchers to the top level of the Scaffold content so that
        // rememberLauncherForActivityResult is always registered regardless of scroll position.
        val csvData = viewModel.toCsvString(includeHeader = false)
        val initialName = viewModel.importedFileName
        val existingUri = viewModel.importedFileUri
        val context = LocalContext.current

        val refLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv")
        ) { uri ->
            if (uri != null) writeToCsv(csvData, "references", uri, context)
        }

        val sampleLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv")
        ) { uri ->
            if (uri != null) writeToCsv(csvData, "samples", uri, context)
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
                            results = sample.classificationResults.toList(),
                            distanceMetric = viewModel.distanceMetric
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Export buttons are fixed at the bottom of the screen outside the LazyColumn
            // so their launchers are always active and receive activity results correctly.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (existingUri != null) {
                            writeToCsv(csvData, "references", existingUri, context)
                        } else {
                            refLauncher.launch(initialName)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save as references")
                }

                Button(
                    onClick = {
                        if (existingUri != null) {
                            writeToCsv(csvData, "samples", existingUri, context)
                        } else {
                            sampleLauncher.launch(initialName)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save as samples")
                }
            }
        }
    }
}

/**
 * Display the results of a single sample.
 *
 * Branches on the current comparison mode from the view model. In Whole Card mode,
 * shows a single row with the closest reference and combined distance score. In Per
 * Color mode, shows a ranked breakdown of individual well results.
 *
 * @param viewModel The view model for the app.
 * @param sampleIndex The index of the sample.
 * @param results The list of classification results for the sample.
 * @param distanceMetric The distance metric used for the classification.
 * @receiver The Composable calling this function.
 * @return Unit
 */
@Composable
fun SampleResultCard(
    viewModel: DatasetModel,
    sampleIndex: Int,
    results: List<ClassificationResult>,
    distanceMetric: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = "Sample ${sampleIndex + 1}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (results.isEmpty()) {
                Text(
                    text = "No results. Ensure a reference dataset was loaded before classifying.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                return@Column
            }

            // The top compound is the label that appears most frequently across all wells
            val topLabel = if (viewModel.comparisonMode == "Whole Card") {
                results.firstOrNull()?.assignedLabel ?: "Unknown"
            } else {
                results
                    .filter { it.assignedLabel.isNotBlank() }
                    .groupingBy { it.assignedLabel }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key ?: "Unknown"
            }

            Text("Detected Compound:", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                text = topLabel,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (viewModel.comparisonMode == "Whole Card") {
                // Whole card mode: show the single closest reference and its combined score
                val best = results.firstOrNull()
                if (best != null) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Whole-card comparison — closest reference:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    val scoreText = if (best.distanceScore < 0) "N/A"
                    else "%.2f".format(best.distanceScore)
                    val maxExpected = 441.0
                    val fraction = if (best.distanceScore < 0) 1f
                    else (best.distanceScore / maxExpected).toFloat().coerceIn(0f, 1f)
                    val scoreColor = lerpColor(Color(0xFF4CAF50), Color(0xFFF44336), fraction)

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
                                text = best.closestReferenceName.ifBlank { "Unknown" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "$distanceMetric distance (all wells combined)",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Score", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                text = scoreText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = scoreColor
                            )
                        }
                    }
                }
            } else {
                // Per color mode: show a ranked breakdown of individual well results
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Well breakdown — ranked by $distanceMetric distance (lower = closer match):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                val ranked = results.sortedBy { it.distanceScore }
                ranked.forEachIndexed { rank, result ->
                    WellResultRow(rank = rank + 1, result = result)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * Display the results of a single well.
 *
 * Shows the well index, assigned label, closest reference name, and distance
 * score. The score is colour coded from green (close match) to red (far match)
 * using a linear interpolation across the expected maximum Euclidean distance.
 *
 * @param rank The rank of the well result, sorted by ascending distance score.
 * @param result The classification result for the well.
 * @receiver The Composable calling this function.
 * @return Unit
 */
@Composable
fun WellResultRow(rank: Int, result: ClassificationResult) {
    val scoreText = if (result.distanceScore < 0) "N/A"
    else "%.2f".format(result.distanceScore)

    // Normalise score for colour: green = close match, red = far match
    val maxExpected = 441.0
    val fraction = if (result.distanceScore < 0) 1f
    else (result.distanceScore / maxExpected).toFloat().coerceIn(0f, 1f)
    val scoreColor = lerpColor(Color(0xFF4CAF50), Color(0xFFF44336), fraction)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Rank badge
        Text(
            text = "#$rank",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.width(28.dp)
        )

        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = if (result.wellIndex < 0) "Whole Sample" else "Well ${result.wellIndex + 1}",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = result.assignedLabel.ifBlank { "—" },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            if (result.closestReferenceName.isNotBlank()) {
                Text(
                    text = "Ref: ${result.closestReferenceName}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text("Score", fontSize = 11.sp, color = Color.Gray)
            Text(
                text = scoreText,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = scoreColor
            )
        }
    }
}

/**
 * Linearly interpolate between two colors.
 *
 * Used to produce a colour gradient between green and red for distance scores,
 * where a fraction of 0.0 returns the start colour and 1.0 returns the end colour.
 *
 * @param start The start color.
 * @param end The end color.
 * @param fraction The fraction between the start and end colors, clamped to 0..1.
 * @return The interpolated color.
 */
fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red   = start.red   + (end.red   - start.red)   * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue  = start.blue  + (end.blue  - start.blue)  * fraction
    )
}