package com.example.micropad.data.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.micropad.data.AppErrorLogger
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * A [CoroutineWorker] that uploads the application's error log to Firebase Storage.
 *
 * This worker is typically scheduled to run weekly. It checks if the user has enabled
 * weekly error log uploads and if a non-empty log file exists before proceeding.
 * If successful, the local log file is cleared.
 *
 * @param appContext The application context.
 * @param params The worker parameters.
 * @return [Result.success] if the upload is disabled, the log is empty, or the upload succeeds.
 */
class FirebaseErrorLogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    /**
     * Executes the background work to upload the error log.
     *
     * @return [Result.success] if the upload is disabled, the log is empty, or the upload succeeds.
     *         [Result.retry] if the upload fails due to an exception.
     */
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!CloudSyncManager.isWeeklyErrorUploadEnabled(context)) {
            return Result.success()
        }

        val logFile = AppErrorLogger.getLogFile(context)
        if (!logFile.exists() || logFile.length() == 0L) {
            return Result.success()
        }

        return try {
            val installationId = CloudSyncManager.getInstallationId(context)
            val path = buildString {
                append("error_logs/")
                append(installationId)
                append("/")
                append(System.currentTimeMillis())
                append(".txt")
            }

            val storageRef = FirebaseStorage.getInstance().reference.child(path)
            logFile.inputStream().use { input ->
                storageRef.putStream(input).await()
            }
            AppErrorLogger.clearLog(context)
            Result.success()
        } catch (e: Exception) {
            AppErrorLogger.logError(context, "FirebaseErrorLogWorker", "Weekly Firebase upload failed", e)
            Result.retry()
        }
    }
}
