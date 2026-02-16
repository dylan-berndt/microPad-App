package com.example.micropad

import android.content.ContentResolver
import android.net.Uri

object CsvParser {

    private val expectedHeaders = listOf("id", "name", "email")

    fun parseAndValidate(
        contentResolver: ContentResolver,
        uri: Uri
    ): Boolean {

        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val text = inputStream?.bufferedReader()?.use { it.readText() }

            val firstLine = text?.lines()?.firstOrNull()
            val headers = firstLine?.split(",")?.map { it.trim() }

            headers == expectedHeaders

        } catch (e: Exception) {
            false
        }
    }
}
