package com.example.micropad.data.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.micropad.data.AppErrorLogger
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.cloud.CloudSyncManager.KEY_INSTALLATION_ID
import com.example.micropad.data.cloud.CloudSyncManager.KEY_TREE_URI
import com.example.micropad.data.cloud.CloudSyncManager.KEY_WEEKLY_ERROR_UPLOAD
import com.example.micropad.data.cloud.CloudSyncManager.PREFS
import com.example.micropad.data.cloud.CloudSyncManager.WEEKLY_ERROR_WORK_NAME
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Synchronizes files to a Google Drive folder.
 * 
 * @property PREFS The name of the shared preferences file.
 * @property KEY_TREE_URI The key for the cloud folder URI in shared preferences.
 * @property KEY_WEEKLY_ERROR_UPLOAD The key for whether weekly error log uploads are enabled in shared preferences.
 * @property KEY_INSTALLATION_ID The key for the installation ID in shared preferences.
 * @property WEEKLY_ERROR_WORK_NAME The name of the work request for weekly error log uploads.
 */
object CloudSyncManager {
    private const val PREFS = "cloud_sync_prefs"
    private const val KEY_TREE_URI = "cloud_tree_uri"
    private const val KEY_WEEKLY_ERROR_UPLOAD = "weekly_error_upload"
    private const val KEY_INSTALLATION_ID = "installation_id"
    const val WEEKLY_ERROR_WORK_NAME = "weekly_error_upload"

    /**
     * Saves the URI of the selected cloud folder.
     * 
     * @param context The application context.
     * @param treeUri The URI of the selected cloud folder.
     */
    fun saveCloudFolder(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
            // Some providers may already have granted/persisted permission.
        }
        prefs(context).edit { putString(KEY_TREE_URI, treeUri.toString()) }
    }

    /**
     * Retrieves the URI of the selected cloud folder.
     * 
     * @param context The application context.
     * @return The URI of the selected cloud folder, or null if no folder is selected.
     */
    fun getCloudFolderUri(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_TREE_URI, null) ?: return null
        return runCatching { raw.toUri() }.getOrNull()
    }

    /**
     * Checks if a cloud folder is selected.
     * 
     * @param context The application context.
     */
    fun hasCloudFolder(context: Context): Boolean = getCloudFolderUri(context) != null

    /**
     * Retrieves the [DocumentFile] representing the selected cloud folder.
     *
     * @param context The application context.
     * @return The [DocumentFile] for the cloud folder, or null if not set or inaccessible.
     */
    fun getCloudFolder(context: Context): DocumentFile? {
        val uri = getCloudFolderUri(context) ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    /**
     * Gets the display name of the linked cloud folder.
     *
     * @param context The application context.
     * @return The folder name, or a default "No cloud folder linked" message.
     */
    fun getLinkedFolderName(context: Context): String {
        return getCloudFolder(context)?.name ?: "No cloud folder linked"
    }

    /**
     * Checks if the weekly error log upload feature is enabled.
     *
     * @param context The application context.
     * @return True if enabled, false otherwise.
     */
    fun isWeeklyErrorUploadEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_WEEKLY_ERROR_UPLOAD, false)
    }

    /**
     * Enables or disables the weekly error log upload feature and manages the background work.
     *
     * @param context The application context.
     * @param enabled True to enable, false to disable.
     */
    fun setWeeklyErrorUploadEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_WEEKLY_ERROR_UPLOAD, enabled) }
        if (enabled) {
            scheduleWeeklyErrorUploads(context)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WEEKLY_ERROR_WORK_NAME)
        }
    }

    /**
     * Ensures that the background work for weekly error uploads is correctly scheduled
     * or cancelled based on the user's current preference.
     *
     * @param context The application context.
     */
    fun ensureScheduledWorkMatchesPreference(context: Context) {
        if (isWeeklyErrorUploadEnabled(context)) {
            scheduleWeeklyErrorUploads(context)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WEEKLY_ERROR_WORK_NAME)
        }
    }

    /**
     * Schedules a periodic work request to upload error logs to Firebase weekly.
     *
     * @param context The application context.
     */
    fun scheduleWeeklyErrorUploads(context: Context) {
        val request = PeriodicWorkRequestBuilder<FirebaseErrorLogWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WEEKLY_ERROR_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WEEKLY_ERROR_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Uploads the content of a [Uri] to the selected cloud folder.
     *
     * @param context The application context.
     * @param sourceUri The URI of the file to upload.
     * @param fileName The desired name for the file in the cloud folder.
     * @param mimeType Optional MIME type. If null, it will be inferred from the URI or file name.
     * @return True if the upload was successful, false otherwise.
     */
    fun uploadUriToCloudFolder(
        context: Context,
        sourceUri: Uri,
        fileName: String,
        mimeType: String? = null
    ): Boolean {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(sourceUri) ?: return false
        return input.use { stream ->
            writeStreamToFolder(
                context = context,
                inputStreamProvider = { stream },
                fileName = fileName,
                mimeType = mimeType ?: resolver.getType(sourceUri) ?: guessMimeType(fileName)
            )
        }
    }

    /**
     * Uploads a local [File] to the selected cloud folder.
     *
     * @param context The application context.
     * @param file The file to upload.
     * @param fileName The desired name in the cloud folder (defaults to the file's name).
     * @param mimeType The MIME type of the file (defaults to inferred type).
     * @return True if the upload was successful, false otherwise.
     */
    fun uploadFileToCloudFolder(
        context: Context,
        file: File,
        fileName: String = file.name,
        mimeType: String = guessMimeType(fileName)
    ): Boolean {
        if (!file.exists()) return false
        return file.inputStream().use { input ->
            writeStreamToFolder(context, { input }, fileName, mimeType)
        }
    }

    /**
     * Uploads a string of text as a file to the selected cloud folder.
     *
     * @param context The application context.
     * @param text The text content to upload.
     * @param fileName The desired name for the file.
     * @param mimeType The MIME type for the file (defaults to inferred type).
     * @return True if the upload was successful, false otherwise.
     */
    fun uploadTextToCloudFolder(
        context: Context,
        text: String,
        fileName: String,
        mimeType: String = guessMimeType(fileName)
    ): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return uploadBytesToCloudFolder(context, bytes, fileName, mimeType)
    }

    /**
     * Uploads a [ByteArray] as a file to the selected cloud folder.
     *
     * @param context The application context.
     * @param bytes The byte array content to upload.
     * @param fileName The desired name for the file.
     * @param mimeType The MIME type for the file (defaults to inferred type).
     * @return True if the upload was successful, false otherwise.
     */
    fun uploadBytesToCloudFolder(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String = guessMimeType(fileName)
    ): Boolean {
        return bytes.inputStream().use { input ->
            writeStreamToFolder(context, { input }, fileName, mimeType)
        }
    }

    /**
     * Uploads all artifacts (CSV data and images) associated with a [DatasetModel] to the cloud.
     *
     * @param context The application context.
     * @param viewModel The dataset model containing the data to upload.
     * @return A [CloudUploadSummary] containing the counts of successfully uploaded files.
     */
    fun uploadDatasetArtifacts(context: Context, viewModel: DatasetModel): CloudUploadSummary {
        var csvCount = 0
        var imageCount = 0

        val timestamp = timestampForFileNames()

        if (viewModel.referenceDataset != null) {
            val csv = viewModel.toCsvString(includeHeader = true, datasetChoice = "references")
            if (csv.isNotBlank() && uploadTextToCloudFolder(context, csv, "references_$timestamp.csv", "text/csv")) {
                csvCount++
            }
        }

        if (viewModel.newDataset != null) {
            val samplesCsv = viewModel.toCsvString(includeHeader = true, datasetChoice = "sample")
            if (samplesCsv.isNotBlank() && uploadTextToCloudFolder(context, samplesCsv, "samples_$timestamp.csv", "text/csv")) {
                csvCount++
            }
        }

        if (viewModel.referenceDataset != null && viewModel.newDataset != null) {
            val referencesCsv = viewModel.toCsvString(includeHeader = true, datasetChoice = "references")
            val sampleRows = viewModel.toCsvString(includeHeader = false, datasetChoice = "sample")
            val combinedCsv = listOf(referencesCsv, sampleRows).filter { it.isNotBlank() }.joinToString("\n")
            if (combinedCsv.isNotBlank() && uploadTextToCloudFolder(context, combinedCsv, "combined_$timestamp.csv", "text/csv")) {
                csvCount++
            }
        }

        if (viewModel.importedFileUri != null) {
            val importedName = viewModel.importedFileName.ifBlank { "imported_reference_$timestamp.csv" }
            if (uploadUriToCloudFolder(context, viewModel.importedFileUri!!, importedName, "text/csv")) {
                csvCount++
            }
        }

        viewModel.referenceImageUris.forEachIndexed { index, uri ->
            if (uploadUriToCloudFolder(context, uri, "reference_${index + 1}_$timestamp.jpg")) {
                imageCount++
            }
        }

        viewModel.sampleImageUris.forEachIndexed { index, uri ->
            if (uploadUriToCloudFolder(context, uri, "sample_${index + 1}_$timestamp.jpg")) {
                imageCount++
            }
        }

        return CloudUploadSummary(csvCount = csvCount, imageCount = imageCount)
    }

    /**
     * Uploads the current local error log file to the selected cloud folder.
     *
     * @param context The application context.
     * @return True if the upload was successful, false if the log was empty or upload failed.
     */
    fun uploadCurrentErrorLogToCloud(context: Context): Boolean {
        val file = AppErrorLogger.getLogFile(context)
        if (!file.exists() || file.length() == 0L) return false
        val name = "error_log_${timestampForFileNames()}.txt"
        return uploadFileToCloudFolder(context, file, name, "text/plain")
    }

    /**
     * Retrieves or generates a unique installation ID for this device.
     *
     * @param context The application context.
     * @return A unique UUID string identifying this installation.
     */
    fun getInstallationId(context: Context): String {
        val prefs = prefs(context)
        val existing = prefs.getString(KEY_INSTALLATION_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit { putString(KEY_INSTALLATION_ID, created) }
        return created
    }

    /**
     * Internal helper to write an input stream to a file in the cloud folder.
     */
    private fun writeStreamToFolder(
        context: Context,
        inputStreamProvider: () -> java.io.InputStream,
        fileName: String,
        mimeType: String
    ): Boolean {
        val folder = getCloudFolder(context) ?: return false
        val safeName = sanitizeFileName(fileName)
        val existing = folder.findFile(safeName)
        existing?.delete()
        val target = folder.createFile(mimeType, safeName) ?: return false
        return try {
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                inputStreamProvider().copyTo(output)
            } ?: return false
            true
        } catch (e: Exception) {
            AppErrorLogger.logError(context, "CloudSync", "Failed to upload $safeName to selected cloud folder", e)
            false
        }
    }

    /**
     * Sanitizes a file name by replacing non-alphanumeric characters (except dots, underscores, and dashes) with underscores.
     */
    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    /**
     * Generates a timestamp string for use in file names.
     */
    private fun timestampForFileNames(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    /**
     * Accesses the shared preferences used for cloud sync settings.
     */
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Guesses the MIME type based on the file extension.
     */
    private fun guessMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".txt", ignoreCase = true) -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}

/**
 * Summary of a cloud upload operation.
 *
 * @property csvCount The number of CSV files successfully uploaded.
 * @property imageCount The number of image files successfully uploaded.
 */
/**
 * Summary of a cloud upload operation.
 *
 * @property csvCount The number of CSV files successfully uploaded.
 * @property imageCount The number of image files successfully uploaded.
 */
data class CloudUploadSummary(
    val csvCount: Int,
    val imageCount: Int
)
