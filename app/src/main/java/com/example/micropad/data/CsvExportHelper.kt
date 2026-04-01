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

/**
 * Provide a UI button to launch a file picker for creating a new CSV or appending to an existing one.
 *
 * @param dataRows The new data rows to be added (newline separated).
 * @param type Either "references" or "samples".
 * @param initialFilename Default filename if creating a new file.
 * @param existingUri The Uri of the file to append to. If null, a file picker will be launched.
 */
@Composable
fun CsvExportButton(dataRows: String, type: String, initialFilename: String? = "data.csv", existingUri: Uri? = null) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { writeToCsv(dataRows, type, it, context) }
    }

    Button(onClick = {
        if (existingUri != null) {
            writeToCsv(dataRows, type, existingUri, context)
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
        val defaultHeader = "sample_id,reference_name,distance_calculation,similarity_score,No Dye_r,No Dye_g,No Dye_b,DMGO_r,DMGO_g,DMGO_b,XO_r,XO_g,XO_b,Phen_r,Phen_g,Phen_b,DCP_r,DCP_g,DCP_b,PAR_r,PAR_g,PAR_b"
        val header = if (existingLines.isNotEmpty() && existingLines[0].contains(",")) existingLines[0] else defaultHeader
        
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
            samples.addAll(contentRows.subList(blankRowIndex + 1, contentRows.size).filter { it.isNotBlank() })
        }

        // 3. Prepare new rows and check for duplicates in the target section
        val rowsToAdd = newData.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        val isReferences = type.equals("references", ignoreCase = true)
        val targetList = if (isReferences) references else samples
        
        for (row in rowsToAdd) {
            if (!targetList.contains(row)) {
                targetList.add(row)
            }
        }

        // 4. Construct final CSV content with sections
        val finalContent = buildString {
            appendLine(header)
            references.forEach { appendLine(it) }
            appendLine() // The mandatory blank row separator
            samples.forEach { appendLine(it) }
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
        Log.e("CsvExportHelper", "Atomic write failed", e)
    } finally {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }
}
