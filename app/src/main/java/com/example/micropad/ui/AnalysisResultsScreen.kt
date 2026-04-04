package com.example.micropad.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.micropad.data.ClassificationResult
import com.example.micropad.data.CsvExportButton
import com.example.micropad.data.DatasetModel

/**
 * Display the results of a classification run.
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Export button at the top
            item {
                Spacer(modifier = Modifier.height(8.dp))
                val csvData = viewModel.toCsvString()
                CsvExportButton(type = "references", dataRows = csvData, initialFilename = viewModel.importedFileName)
            }

            itemsIndexed(dataset.samples) { sampleIndex, sample ->
                SampleResultCard(
                    viewModel,
                    sampleIndex = sampleIndex,
                    results = sample.classificationResults.toList(),
                    distanceMetric = viewModel.distanceMetric
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/**
 * Display the results of a single sample.
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

            // Top compound: the label that appears most across all wells
            val topLabel = results
                .filter { it.assignedLabel.isNotBlank() }
                .groupingBy { it.assignedLabel }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: "Unknown"

            Text("Detected Compound:", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                text = topLabel,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Ranked breakdown header
            Text(
                text = "Well Breakdown — ranked by $distanceMetric distance (lower = closer match):",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Sort by score ascending = best match first
            val ranked = results.sortedBy { it.distanceScore }

            ranked.forEachIndexed { rank, result ->
                WellResultRow(viewModel, rank = rank + 1, result = result)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Display the results of a single well.
 *
 * @param viewModel The view model for the app.
 * @param rank The rank of the well.
 * @param result The classification result for the well.
 * @receiver The Composable calling this function.
 * @return Unit
 */
@Composable
fun WellResultRow(viewModel: DatasetModel, rank: Int, result: ClassificationResult) {
    val scoreText = if (result.distanceScore < 0) "N/A"
    else "%.2f".format(result.distanceScore)

    // Normalize score for color: green = close match, red = far
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
                text = "Well ${result.wellIndex + 1}",
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

        // Get the data to be exported (excluding header as writeToCsv adds it or handles existing ones)
        val csvData = viewModel.toCsvString(includeHeader = false)
        val initialName = viewModel.importedFileName
        val existingUri = viewModel.importedFileUri

        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Button to append current results as new references
            CsvExportButton(
                dataRows = csvData,
                type = "references",
                initialFilename = initialName,
                existingUri = existingUri
            )

            // Button to append current results as new samples
            CsvExportButton(
                dataRows = csvData,
                type = "samples",
                initialFilename = initialName,
                existingUri = existingUri
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

/**
 * Linearly interpolate between two colors.
 *
 * @param start The start color.
 * @param end The end color.
 * @param fraction The fraction between the start and end colors.
 * @receiver The Composable calling this function.
 * @return The interpolated color.
 */
fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red   = start.red   + (end.red   - start.red)   * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue  = start.blue  + (end.blue  - start.blue)  * fraction
    )
}