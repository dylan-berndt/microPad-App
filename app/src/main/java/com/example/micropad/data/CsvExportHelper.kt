package com.example.micropad.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Provide a UI button to launch a file picker for creating a new CSV or appending to an existing one.
 *
 * @param dataRows The new data rows to be added (newline separated).
 * @param type Either "references" or "samples".
 * @param initialFilename Default filename if creating a new file.
 * @param existingUri The Uri of the file to append to. If null, a file picker will be launched.
 * @param navHome Optional function to allow for immediate navigation home in cases where an export is the final step.
 */
@Composable
fun CsvExportButton(
    dataRows: String,
    type: String,
    initialFilename: String? = "data.csv",
    existingUri: Uri? = null,
    navHome: () -> Unit = {}
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            writeToCsv(dataRows, type, uri, context)
            navHome()
        }
        // user cancelled picker — do nothing
    }

    Button(onClick = {
        if (existingUri != null) {
            writeToCsv(dataRows, type, existingUri, context)
            navHome()
        } else {
            launcher.launch(initialFilename ?: "data.csv")
        }
    }) {
        Text("Save as $type")
    }
}

/**
 * Write newData to filePath atomically by using a temporary file.
 * The CSV file is structured with:
 * 1. Header
 * 2. References rows
 * 3. A blank row separator
 * 4. Samples rows
 *
 * This function ensures no duplicate rows are added to the specified section.
 *
 * @param newData String containing one or more rows separated by newlines.
 * @param type Section to append to: "references" or "samples".
 */
fun writeToCsv(newData: String, type: String, filePath: Uri, context: Context) {
    val tempFile = File(context.cacheDir, "temp_export_${System.currentTimeMillis()}.csv")
    try {
        // 1. Read existing file content
        val existingLines = mutableListOf<String>()
        try {
            context.contentResolver.openInputStream(filePath)?.use { stream ->
                stream.bufferedReader().forEachLine { existingLines.add(it) }
            }
        } catch (e: Exception) {
            Log.d("CsvExport", "Starting new file: ${e.message}")
        }

        // 2. Parse incoming rows
        if (newData.isBlank()) {
            AppErrorLogger.logError(context, "CSV", "writeToCsv: newData is blank, nothing to write")
            return
        }
        val incomingLines = newData.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (incomingLines.isEmpty()) return

        val incomingHeader = incomingLines.firstOrNull {
            it.contains("_r,") || it.startsWith("sample_id")
        }
        val incomingRows = if (incomingHeader != null) incomingLines.drop(1) else incomingLines

        // 3. Derive header — prefer existing, fall back to incoming
        val header: String = when {
            existingLines.isNotEmpty() && existingLines[0].contains(",") -> existingLines[0]
            incomingHeader != null -> incomingHeader
            else -> {
                AppErrorLogger.logError(context, "CSV", "writeToCsv: no valid header found")
                return
            }
        }

        // 4. Split existing content into reference / sample sections
        val contentRows = if (existingLines.firstOrNull() == header) existingLines.drop(1)
        else existingLines
        val blankIndex = contentRows.indexOfFirst { it.trim().isEmpty() }
        val references = mutableListOf<String>()
        val samples    = mutableListOf<String>()
        if (blankIndex == -1) {
            references.addAll(contentRows.filter { it.isNotBlank() })
        } else {
            references.addAll(contentRows.subList(0, blankIndex).filter { it.isNotBlank() })
            samples.addAll(contentRows.subList(blankIndex + 1, contentRows.size).filter { it.isNotBlank() })
        }

        // 5. Append new rows (deduplicating)
        val isReferences = type.equals("references", ignoreCase = true)
        val target = if (isReferences) references else samples
        for (row in incomingRows.filter { it.isNotBlank() }) {
            if (!target.contains(row)) target.add(row)
        }

        // 6. Assemble final content
        val finalContent = buildString {
            appendLine(header)
            references.forEach { appendLine(it) }
            appendLine()   // blank separator
            samples.forEachIndexed { i, row ->
                if (i < samples.lastIndex) appendLine(row) else append(row)
            }
        }

        // 7. Write to temp file (fsync before touching the real URI)
        FileOutputStream(tempFile).use { fos ->
            fos.write(finalContent.toByteArray(Charsets.UTF_8))
            fos.flush()
            fos.fd.sync()
        }

        // 8. Copy temp → target URI (truncate first)
        context.contentResolver.openOutputStream(filePath, "rwt")?.use { out ->
            tempFile.inputStream().use { it.copyTo(out) }
        } ?: AppErrorLogger.logError(context, "CSV",
            "writeToCsv: openOutputStream returned null for $filePath")

    } catch (e: IOException) {
        AppErrorLogger.logError(context, "CSV", "writeToCsv: IO failure", e)
    } catch (e: Exception) {
        AppErrorLogger.logError(context, "CSV", "writeToCsv: unexpected error", e)
    } finally {
        if (tempFile.exists()) tempFile.delete()
    }
}
