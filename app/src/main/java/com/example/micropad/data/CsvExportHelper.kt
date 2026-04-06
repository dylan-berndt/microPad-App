package com.example.micropad.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.compose.foundation.layout.Column
import com.example.micropad.data.writeToCsv
import com.example.micropad.data.AppErrorLogger

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
fun CsvExportButton(dataRows: String, type: String, initialFilename: String? = "data.csv", existingUri: Uri? = null, navHome: () -> Unit = {}) {
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
    val tempFile = File(context.cacheDir, "temp_export.csv")
    
    try {
        // 1. Read existing content to memory
        val existingLines = mutableListOf<String>()
        try {
            context.contentResolver.openInputStream(filePath)?.use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        existingLines.add(line)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("CsvExportHelper", "Starting new file or file unreadable: ${e.message}")
        }

        // 2. Identify header and sections
        val incomingLines = newData.split("\n").map { it.trim() }
        val incomingHeader = if (incomingLines.first().contains("_r,") || incomingLines.first().startsWith("sample_id")) {
            incomingLines.first()
        } else null
        val incomingRows = if (incomingHeader != null) incomingLines.drop(1) else incomingLines

        val header = if (existingLines.isNotEmpty() && existingLines[0].contains(",")) {
            existingLines[0]
        } else {
            incomingHeader ?: return
        }

        val contentRows = if (existingLines.isNotEmpty() && existingLines[0] == header) existingLines.drop(1) else existingLines
        
        // Find the blank row that separates references from samples
        val blankRowIndex = contentRows.indexOfFirst { it.trim().isEmpty() }
        
        val references = mutableListOf<String>()
        val samples = mutableListOf<String>()
        
        if (blankRowIndex == -1) {
            // No blank row found. Assume all existing data are references (as they come first).
            references.addAll(contentRows.filter { it.isNotBlank() })
        } else {
            references.addAll(contentRows.subList(0, blankRowIndex).filter { it.isNotBlank() })
            samples.addAll(
                contentRows.subList(blankRowIndex + 1, contentRows.size).filter { it.isNotBlank() })
        }
        
        val isReferences = type.equals("references", ignoreCase = true)
        val targetList = if (isReferences) references else samples
        
        for (row in incomingRows.filter { it.isNotBlank() }) {
            if (!targetList.contains(row)) {
                targetList.add(row)
            }
        }

        val finalContent = buildString {
            appendLine(header)
            references.forEach { appendLine(it) }
            appendLine()
            samples.forEachIndexed { i, row ->
                if (i < samples.lastIndex) appendLine(row) else append(row)
            }
        }

        // 5. Write to a temporary file first
        FileOutputStream(tempFile).use { fos ->
            fos.write(finalContent.toByteArray())
            fos.flush()
            fos.fd.sync() 
        }

        // 6. Replace target file content
        context.contentResolver.openOutputStream(filePath, "rwt")?.use { outputStream ->
            tempFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    } catch (e: IOException) {
        AppErrorLogger.logError(context, "CSV", "writeToCsv: atomic write failed", e)
    } catch (e: Exception) {
        AppErrorLogger.logError(context, "CSV", "writeToCsv: unexpected error", e)
    } finally {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }
}
