package com.example.micropad.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.micropad.data.AppErrorLogger
import com.example.micropad.data.DatasetModel
import com.example.micropad.data.cloud.CloudSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PendingDriveAction {
    UploadArtifacts,
    UploadErrorLog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(viewModel: DatasetModel, navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity

    var weeklyEnabled by remember {
        mutableStateOf(CloudSyncManager.isWeeklyErrorUploadEnabled(context))
    }
    var statusText by remember {
        mutableStateOf("Not connected yet")
    }
    var pendingAction by remember {
        mutableStateOf<PendingDriveAction?>(null)
    }

    fun performAuthorizedAction(accessToken: String) {
        val action = pendingAction ?: return
        CoroutineScope(Dispatchers.IO).launch {
            when (action) {
                PendingDriveAction.UploadArtifacts -> {
                    val summary = CloudSyncManager.uploadDatasetArtifacts(context, viewModel, accessToken)
                    withContext(Dispatchers.Main) {
                        statusText = "Connected to Google Drive"
                        Toast.makeText(
                            context,
                            "Uploaded ${summary.csvCount} CSV file(s) and ${summary.imageCount} image(s)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                PendingDriveAction.UploadErrorLog -> {
                    val uploaded = CloudSyncManager.uploadCurrentErrorLogToCloud(context, accessToken)
                    withContext(Dispatchers.Main) {
                        statusText = "Connected to Google Drive"
                        Toast.makeText(
                            context,
                            if (uploaded) "Error log uploaded to Google Drive"
                            else "No error log available",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            pendingAction = null
        }
    }

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        CloudSyncManager.finishDriveAuthorization(
            context = context,
            data = result.data,
            onAuthorized = { accessToken ->
                performAuthorizedAction(accessToken)
            },
            onError = { error ->
                pendingAction = null
                statusText = "Drive authorization failed"
                Toast.makeText(
                    context,
                    "Google Drive authorization failed: ${error.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    fun authorizeThen(action: PendingDriveAction) {
        if (activity == null) {
            Toast.makeText(context, "This screen requires an Activity context.", Toast.LENGTH_LONG).show()
            return
        }

        pendingAction = action
        CloudSyncManager.startDriveAuthorization(
            activity = activity,
            launcher = authorizationLauncher,
            onAuthorized = { accessToken ->
                performAuthorizedAction(accessToken)
            },
            onError = { error ->
                pendingAction = null
                statusText = "Drive authorization failed"
                Toast.makeText(
                    context,
                    "Google Drive authorization failed: ${error.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync & Backup") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Google Drive access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "This app requests Drive access only when you tap an upload button. Files created by the app are uploaded directly to your Google Drive.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Status: $statusText", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { authorizeThen(PendingDriveAction.UploadArtifacts) }
                    ) {
                        Text("Connect Google Drive and upload CSVs/images")
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Automatic error log backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "When enabled, the app schedules weekly error-log work. The actual Drive upload still requires valid authorization when you perform a Drive action.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Weekly Firebase upload")
                        Switch(
                            checked = weeklyEnabled,
                            onCheckedChange = {
                                weeklyEnabled = it
                                CloudSyncManager.setWeeklyErrorUploadEnabled(context, it)
                            }
                        )
                    }
                    Button(
                        onClick = { authorizeThen(PendingDriveAction.UploadErrorLog) }
                    ) {
                        Text("Upload current error log to Google Drive now")
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Manual upload",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Uploads current session CSV exports and any reference/sample images tracked by the app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider()
                    Button(
                        onClick = { authorizeThen(PendingDriveAction.UploadArtifacts) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upload CSVs and images to Google Drive")
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "What gets uploaded",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("• Reference CSV imported into the app, if present")
                    Text("• Generated reference, sample, and combined CSV exports when available")
                    Text("• Reference and sample image URIs tracked in the current app session")
                    Text("• Error log file only if you upload it manually or enable weekly Firebase upload")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (AppErrorLogger.hasErrors(context)) {
                            "An error log is currently available."
                        } else {
                            "No local error log is currently stored."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}