package com.example.micropad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.micropad.data.CsvImportButton
import com.example.micropad.data.DatasetModel

@Composable
fun ImportScreen(viewModel: DatasetModel, navController: NavController) {
    val context = LocalContext.current

    val distanceOptions = listOf("Euclidean", "Manhattan")
    val colorModeOptions = listOf("RGB", "Grayscale")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Import Reference & Classify",
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
                text = "✅ Reference loaded (${viewModel.referenceDataset!!.samples.size} samples)",
                color = MaterialTheme.colorScheme.primary
            )
        }

        Divider()

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

        Divider()

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