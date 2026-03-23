package com.example.micropad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.micropad.data.CsvExportButton
import com.example.micropad.data.DatasetModel

@Composable
fun AnalysisScreen(viewModel: DatasetModel, navController: NavController) {
    val context = LocalContext.current

    // Analysis UI
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Analysis Results")
        Spacer(modifier = Modifier.height(24.dp))

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
    }
}
