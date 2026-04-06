package com.example.micropad.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppErrorLogger {

    private const val FILE_NAME = "app_error_log.txt"

    fun logError(context: Context, tag: String, message: String, e: Throwable? = null) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())

            val logMessage = """
                [$timestamp]
                [$tag] $message
                ${e?.stackTraceToString() ?: ""}
                
                -------------------------
                
            """.trimIndent()

            File(context.filesDir, FILE_NAME).appendText(logMessage)

        } catch (ignored: Exception) {}
    }

    fun hasErrors(context: Context): Boolean {
        val file = File(context.filesDir, FILE_NAME)
        return file.exists() && file.readText().isNotBlank()
    }

    fun buildShareIntent(context: Context): Intent? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun clearLog(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}