package com.example.micropad.data

import android.content.Context
import kotlinx.coroutines.CancellationException

/**
 * Centralised safe-execution wrappers.
 *
 * Rules:
 *  - [safeExecute]        : for synchronous operations; returns defaultValue on failure.
 *  - [safeSuspend]        : for suspend functions; re-throws CancellationException so coroutine
 *                           cancellation is never silently swallowed.
 *  - [safeUnit]           : convenience for fire-and-forget calls that return Unit.
 *
 * Every path logs through [AppErrorLogger]. The caller never needs its own try/catch.
 */
object ErrorHandler {

    /**
     * Execute [block] and return its result, or [defaultValue] if any exception is thrown.
     * Logs every caught exception to the hidden error file.
     */
    inline fun <T> safeExecute(
        context: Context,
        defaultValue: T? = null,
        tag: String = "ErrorHandler",
        block: () -> T
    ): T? = try {
        block()
    } catch (e: Exception) {
        AppErrorLogger.logError(context, tag, e.message ?: "Unknown error", e)
        defaultValue
    }

    /**
     * Suspend-safe variant. Re-throws [CancellationException] so coroutine cancellation
     * propagates correctly; all other exceptions are caught and logged.
     */
    suspend inline fun <T> safeSuspend(
        context: Context,
        defaultValue: T? = null,
        tag: String = "ErrorHandler",
        crossinline block: suspend () -> T
    ): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e  // must not swallow — coroutine lifecycle depends on this
    } catch (e: Exception) {
        AppErrorLogger.logError(context, tag, e.message ?: "Unknown error", e)
        defaultValue
    }

    /**
     * Fire-and-forget variant for Unit-returning calls.
     */
    inline fun safeUnit(
        context: Context,
        tag: String = "ErrorHandler",
        block: () -> Unit
    ) {
        safeExecute(context, Unit, tag, block)
    }
}