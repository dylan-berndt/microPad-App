package com.example.micropad

import android.content.Context
import android.net.Uri

object CsvParser {

    fun validateScientificCsv(context: Context, uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val lines = inputStream?.bufferedReader()?.readLines() ?: return false

            // Find the row that starts with "Data type"
            val headerIndex = lines.indexOfFirst {
                it.startsWith("Data type", ignoreCase = true)
            }

            if (headerIndex == -1) return false

            val headerRow = lines[headerIndex].split(",")
            val secondRow = lines.getOrNull(headerIndex + 1)?.split(",") ?: return false

            // Validate first column
            if (headerRow.first().trim() != "Data type") return false

            // Check at least one "Reference"
            val hasReference = headerRow.any { it.trim().equals("Reference", ignoreCase = true) }

            // Check at least one "Sample"
            val hasSample = headerRow.any { it.trim().equals("Sample", ignoreCase = true) }

            if (!hasReference || !hasSample) return false

            // Validate second row
            if (!secondRow.first().trim()
                    .equals("Label of sample/reference", ignoreCase = true)
            ) return false

            true

        } catch (e: Exception) {
            false
        }
    }
}
