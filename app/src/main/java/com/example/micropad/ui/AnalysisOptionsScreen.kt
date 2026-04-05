package com.example.micropad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.micropad.data.CsvImportButton
import com.example.micropad.data.DatasetModel

/**
 * Display the configuration/options screen for the analysis.
 *
 * @param viewModel The view model for the app.
 * @param navController The navigation controller for the app.
 * @receiver The Composable calling this function.
 * @return Unit
 */
@Composable
fun AnalysisConfigScreen(viewModel: DatasetModel, navController: NavController) {
    val context = LocalContext.current

    val distanceOptions = listOf("Euclidean", "Manhattan")
    val colorModeOptions = listOf("RGB", "Grayscale")
    val normalizationOptions = listOf("MinMax", "Z-Score", "None")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Set Analysis Configuration",
            style = MaterialTheme.typography.headlineSmall
        )

        // Reference CSV import button
        Text("Step 1: Load a reference CSV file")
        CsvImportButton { uri ->
            if (uri != null) {
                viewModel.setReferenceDataset(uri, context)
            }
        }

        // Show confirmation that reference loaded
        if (viewModel.referenceDataset != null && !viewModel.referenceDataset!!.isEmpty()) {
            Text(
                text = "Reference loaded (${viewModel.referenceDataset!!.samples.size} samples)",
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

        // Distance metric selector
        Text("Step 2: Choose distance metric")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            distanceOptions.forEach { option ->
                FilterChip(
                    selected = viewModel.distanceMetric == option,
                    onClick = { viewModel.distanceMetric = option },
                    label = { Text(option) }
                )
            }
        }

        // Color mode selector
        Text("Step 3: Choose color mode")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colorModeOptions.forEach { option ->
                FilterChip(
                    selected = viewModel.colorMode == option,
                    onClick = { viewModel.colorMode = option },
                    label = { Text(option) }
                )
            }
        }

        // Normalization selector
        Text("Step 4: Choose normalization method")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            normalizationOptions.forEach { option ->
                FilterChip(
                    selected = viewModel.normalizationStrategy == option,
                    onClick = { viewModel.normalizationStrategy = option },
                    label = { Text(option) }
                )
            }
        }

        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

        // Classify button — only enabled when reference is loaded
        Button(
            onClick = {
                viewModel.runClassification()
                navController.navigate("analysis")
            },
            enabled = viewModel.referenceDataset != null && !viewModel.referenceDataset!!.isEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run Classification & View Results")
        }

        if (viewModel.referenceDataset == null || viewModel.referenceDataset!!.isEmpty()) {
            Text(
                text = "Please load a reference CSV to enable classification",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}