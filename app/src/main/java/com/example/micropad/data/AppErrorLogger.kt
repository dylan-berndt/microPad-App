package com.example.micropad.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured error logger. Persists errors as a JSON array to a hidden file in internal storage.
 * The file is never shown to the user; they are only offered a share prompt at app start if
 * errors were recorded in a prior session.
 *
 * Log entry schema:
 *   timestamp : ISO-8601 string
 *   tag       : subsystem identifier (e.g. "CSV", "ImagePipeline")
 *   message   : human-readable description
 *   exception : stack trace string, or null if no throwable was supplied
 *   appVersion: BuildConfig.VERSION_NAME at time of logging
 */
object AppErrorLogger {

    private const val FILE_NAME = "app_error_log.json"
    private const val MAX_ENTRIES = 200

    /**
     * Record an error. Never throws — if logging itself fails, the failure is silently ignored
     * so that the logger can never be the cause of a crash.
     */
    fun logError(
        context: Context,
        tag: String,
        message: String,
        e: Throwable? = null
    ) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val array: JSONArray = if (file.exists()) {
                try { JSONArray(file.readText()) } catch (_: Exception) { JSONArray() }
            } else {
                JSONArray()
            }

            val entry = JSONObject().apply {
                put("timestamp", iso8601())
                put("tag", tag)
                put("message", message)
                put("exception", e?.stackTraceToString() ?: JSONObject.NULL)
                put("appVersion", appVersion(context))
            }

            array.put(entry)

            // Trim to cap — keep the most recent MAX_ENTRIES
            val trimmed = if (array.length() > MAX_ENTRIES) {
                val start = array.length() - MAX_ENTRIES
                JSONArray().also { out ->
                    for (i in start until array.length()) out.put(array.get(i))
                }
            } else array

            file.writeText(trimmed.toString(2))
        } catch (_: Exception) {
            // Intentionally swallowed — logger must never crash the app
        }
    }

    /** Returns true if there is at least one logged error entry. */
    fun hasErrors(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return false
            val array = JSONArray(file.readText())
            array.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Builds a share Intent for the JSON log file.
     * Uses the correct FileProvider authority declared in AndroidManifest.xml.
     * Returns null if no log file exists.
     */
    fun buildShareIntent(context: Context): Intent? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists() || !hasErrors(context)) return null

            // Authority must match AndroidManifest provider: "${applicationId}.fileprovider"
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "microPAD error log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            // If share intent construction fails, swallow — user just won't see the dialog
            null
        }
    }

    /** Deletes the log file entirely. */
    fun clearLog(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {}
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun iso8601(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    private fun appVersion(context: Context): String = try {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: "unknown"
    } catch (_: Exception) { "unknown" }
}