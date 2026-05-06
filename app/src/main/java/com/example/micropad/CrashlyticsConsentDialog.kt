package com.example.micropad

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.google.firebase.crashlytics.FirebaseCrashlytics

@Composable
fun CrashlyticsConsentDialog() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("micropad_prefs", Context.MODE_PRIVATE)
    var showDialog by remember {
        mutableStateOf(!prefs.getBoolean("crashlytics_consent", false))
    }

    if (!showDialog) return

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Help Improve microPAD?") },
        text = {
            Text(
                "If the app encounters an error, would you like to automatically " +
                        "send a crash report to the developer? This helps us fix bugs faster. " +
                        "No personal data or images are ever included."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.edit {
                    putBoolean("crashlytics_consent", true)
                        .putBoolean("crashlytics_enabled", true)
                }
                FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
            }) { Text("Yes, send reports") }
        },
        dismissButton = {
            TextButton(onClick = {
                prefs.edit {
                    putBoolean("crashlytics_consent", true)
                        .putBoolean("crashlytics_enabled", false)
                }
                FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
            }) { Text("No thanks") }
        }
    )
}