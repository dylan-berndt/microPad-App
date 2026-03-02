package com.example.micropad.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.micropad.data.CsvExportButton
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.SampleDataset

// Here's where we should put everything to do with choosing the analysis mode
// and potentially showing analysis results

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

        // Export-to-CSV button
        val csvData = viewModel.toCsvString()
        CsvExportButton(dataRow = csvData)
    }
}