package com.example.micropad.ui.common

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.micropad.data.cloud.CloudSyncManager
import com.example.micropad.data.util.AppErrorLogger
import com.example.micropad.data.util.ErrorHandler

@Composable
fun ReferenceOnlyDialog(navigate: () -> Unit, onDismissRequest: () -> Unit) {
    AlertDialog(
        title = { Text("No Sample Data") },
        text = { Text("You have only imported reference data. Move on only if you are only exporting a reference sheet for later use.") },
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = navigate) { Text("Next") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Return") } }
    )
}

@Composable
fun ErrorReportBanner() {
    val context = LocalContext.current
    // Evaluate once at composition time — not on every recompose
    var showDialog by remember { mutableStateOf(AppErrorLogger.hasErrors(context)) }
    if (!showDialog) return

    AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text("Share system errors to improve the app?") },
        text = {
            Text(
                "Errors were recorded during this session. You can share them anonymously " +
                        "to help us fix issues. The file contains no personal data."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                ErrorHandler.safeUnit(context, tag = "ErrorShare") {
                    val intent = AppErrorLogger.buildShareIntent(context)
                    if (intent != null) {
                        context.startActivity(Intent.createChooser(intent, "Share error log"))
                    }
                    AppErrorLogger.clearLog(context)
                }
                showDialog = false
            }) { Text("Share once") }
        },
        dismissButton = {
            TextButton(onClick = {
                ErrorHandler.safeUnit(context, tag = "ErrorShare") {
                    AppErrorLogger.clearLog(context)
                }
                showDialog = false
            }) { Text("Dismiss") }
            Row {
                TextButton(onClick = {
                    CloudSyncManager.setWeeklyErrorUploadEnabled(context, true)
                    showDialog = false
                }) { Text("Enable weekly upload") }
                TextButton(onClick = { showDialog = false }) { Text("Later") }
            }
        }
    )
}

@Composable
fun DataAcquisitionCard(
    title: String,
    description: String,
    count: Int,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    showCsv: Boolean,
    onCsv: ((Uri?) -> Unit)? = null,
    isGalleryHighlighted: Boolean = false,
    isCameraHighlighted: Boolean = false,
    isCsvHighlighted: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (count > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            "$count added",
                            color = Color.White,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onGallery,
                    modifier = Modifier.weight(1f).then(
                        if (isGalleryHighlighted) Modifier.border(
                            4.dp,
                            Color.Yellow,
                            RoundedCornerShape(8.dp)
                        ).padding(4.dp) else Modifier
                    )
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gallery", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onCamera,
                    modifier = Modifier.weight(1f).then(
                        if (isCameraHighlighted) Modifier.border(
                            4.dp,
                            Color.Yellow,
                            RoundedCornerShape(8.dp)
                        ).padding(4.dp) else Modifier
                    )
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Camera", fontSize = 12.sp)
                }
            }
            if (showCsv) {
                val csvLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri -> onCsv?.invoke(uri) }
                Button(
                    onClick = { csvLauncher.launch("text/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isCsvHighlighted) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) else ButtonDefaults.buttonColors()
                ) {
                    Text("Import CSV")
                }
            }
        }
    }
}
