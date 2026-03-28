package com.example.micropad.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Provide a UI button to launch a file picker for importing a CSV file.
 *
 * Opens Android system file picker restricted to CSV files only.
 * Returns selected file URI via callback.
 *
 * @param onFileSelected Callback to handle the selected file URI.
 * @return A button to trigger the file picker.
 */
@Composable
fun CsvImportButton(onFileSelected: (Uri?) -> Unit) {

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onFileSelected(uri)
    }

    Button(onClick = {
        launcher.launch(arrayOf("text/csv", "text/plain", "application/octet-stream", "*/*"))
    }) {
        Text("Import CSV")
    }
}
