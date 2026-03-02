package com.example.micropad.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileWriter
import java.io.IOException


/**
 * Provide a UI button to launch a file picker.
 */
@Composable
fun CsvExportButton(dataRow: String) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { writeToCsv(dataRow, it, context) }
    }

    Button(onClick = {
        launcher.launch("export.csv")
    }) {
        Text("Export CSV")
    }
}

/**
 * Write dataRow to filePath.
 */
fun writeToCsv(dataRow: String, filePath: Uri, context: Context) {
    try {
        context.contentResolver.openOutputStream(filePath)?.use { outputStream ->
            outputStream.write(dataRow.toByteArray())
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}
