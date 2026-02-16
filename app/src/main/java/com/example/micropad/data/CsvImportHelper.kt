package com.example.micropad.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * CsvImportHelper
 *
 * Opens Android system file picker restricted to CSV files only.
 * Returns selected file URI via callback.
 */
@Composable
fun CsvImportButton(onFileSelected: (Uri?) -> Unit) {

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onFileSelected(uri)
    }

    Button(onClick = {
        launcher.launch(arrayOf("text/csv"))
    }) {
        Text("Import CSV")
    }
}
