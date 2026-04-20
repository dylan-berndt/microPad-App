package com.example.micropad.ui

import android.net.Uri
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

/**
 * Offers a separate screen allowing users to upload CSVs and images to Google Drive.
 * Provides a weekly error log upload option to Firebase (not set up at Firebase, yet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(viewModel: DatasetModel, navController: NavController) {
    val context = LocalContext.current
    var weeklyEnabled by remember { mutableStateOf(CloudSyncManager.isWeeklyErrorUploadEnabled(context)) }
    var linkedFolderLabel by remember { mutableStateOf(CloudSyncManager.getLinkedFolderName(context)) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            CloudSyncManager.saveCloudFolder(context, uri)
            linkedFolderLabel = CloudSyncManager.getLinkedFolderName(context)
            Toast.makeText(context, "Cloud folder linked", Toast.LENGTH_SHORT).show()
        }
    }

    fun requireCloudFolder(action: suspend () -> Unit) {
        if (!CloudSyncManager.hasCloudFolder(context)) {
            Toast.makeText(context, "Choose a cloud folder first. You can pick a Google Drive folder.", Toast.LENGTH_LONG).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            action()
        }
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
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Cloud folder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Use Android's built-in folder picker to choose a cloud folder. If the user picks a Google Drive folder, uploads go there.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Linked folder: $linkedFolderLabel", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { folderLauncher.launch(null) }) {
                        Text("Choose Google Drive / cloud folder")
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Automatic error log backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "When enabled, the app uploads its local error log to Firebase once a week if a log exists. This is intended for a Firebase free-tier setup.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                        onClick = {
                            requireCloudFolder {
                                val uploaded = CloudSyncManager.uploadCurrentErrorLogToCloud(context)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (uploaded) "Error log copied to cloud folder" else "No error log available",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Text("Upload current error log to cloud folder now")
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Manual upload", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Uploads current session CSV exports and any reference/sample images tracked by the app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider()
                    Button(
                        onClick = {
                            requireCloudFolder {
                                val summary = CloudSyncManager.uploadDatasetArtifacts(context, viewModel)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Uploaded ${summary.csvCount} CSV file(s) and ${summary.imageCount} image(s)",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upload CSVs and images to cloud folder")
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("What gets uploaded", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("• Reference CSV imported into the app, if present")
                    Text("• Generated reference, sample, and combined CSV exports when available")
                    Text("• Reference and sample image URIs tracked in the current app session")
                    Text("• Error log file only if you upload it manually or enable weekly Firebase upload")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (AppErrorLogger.hasErrors(context)) "An error log is currently available." else "No local error log is currently stored.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
