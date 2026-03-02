package com.example.micropad.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.Text

@Composable
fun CsvExportButton(onFileSelected: (Uri?) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onFileSelected(uri)
    }

    Button(onClick = {
        launcher.launch(arrayOf("text/csv"))
    }) {
        Text("Export CSV")
    }
}