package com.example.micropad.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.SampleDataset

// Here's where we should put everything to do with choosing the analysis mode
// and potentially showing analysis results

@Composable
fun AnalysisScreen(viewModel: DatasetModel, navController: NavController) {
    val context = LocalContext.current
}