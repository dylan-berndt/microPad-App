package com.example.micropad

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.micropad.CsvParser.expectedHeaders
import com.example.micropad.data.ErrorHandler

/**
 * A utility class for parsing and validating CSV files.
 *
 * @property expectedHeaders A list of expected headers in the CSV file.
 * @receiver The Composable calling this function.
 * @return Unit
 */
object CsvParser {

    private val expectedHeaders = listOf(
        "sample_id", "reference_name",
        "distance_calculation", "similarity_score"
    )

    fun parseAndValidate(
        contentResolver: ContentResolver,
        uri: Uri,
        context: Context
    ): Boolean {

        return ErrorHandler.safeExecute(context, false) {

            val inputStream = contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot open file")

            val text = inputStream.bufferedReader().use { it.readText() }

            val headers = text.lines().firstOrNull()
                ?.split(",")
                ?.map { it.trim() }
                ?: throw Exception("Invalid CSV format")

            headers.containsAll(expectedHeaders)
        } ?: false
    }
}
