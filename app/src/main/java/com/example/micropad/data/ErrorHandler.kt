package com.example.micropad.data

import android.content.Context

/**
 * Centralized safe execution wrapper.
 * Prevents crashes and logs all errors silently.
 */
object ErrorHandler {

    inline fun <T> safeExecute(
        context: Context,
        defaultValue: T? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            AppErrorLogger.logError(
                context = context,
                tag = "ErrorHandler",
                message = e.message ?: "Unknown error",
                e = e
            )
            defaultValue
        }
    }
}