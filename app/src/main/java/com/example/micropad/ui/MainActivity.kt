/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.micropad.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.micropad.data.viewmodel.DatasetModel
import com.example.micropad.data.cloud.CloudSyncManager
import com.example.micropad.data.util.ErrorHandler
import com.example.micropad.ui.common.DataAcquisitionCard
import com.example.micropad.ui.common.ErrorReportBanner
import com.example.micropad.ui.common.ReferenceOnlyDialog
import com.example.micropad.ui.features.simulation.runNavigationSimulation
import com.example.micropad.ui.navigation.AppNavHost
import com.example.micropad.ui.theme.MicroPadTheme
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("APP_FLOW", "onCreate")
        super.onCreate(savedInstanceState)

        ErrorHandler.safeExecute(this) {
            if (!OpenCVLoader.initDebug()) {
                android.util.Log.e("OpenCV", "Unable to load OpenCV!")
            } else {
                android.util.Log.d("OpenCV", "OpenCV loaded successfully.")
            }
        }

        CloudSyncManager.ensureScheduledWorkMatchesPreference(this)

        enableEdgeToEdge()

        Thread {
            try {
                Log.d("OpenCV", "Starting OpenCV init")

                val start = System.currentTimeMillis()
                val ok = OpenCVLoader.initLocal()

                Log.d(
                    "OpenCV",
                    "Finished init in ${System.currentTimeMillis() - start}ms, success=$ok"
                )

                if (!ok) {
                    Log.e("OpenCV", "OpenCV failed to load")
                }

            } catch (e: Exception) {
                Log.e("OpenCV", "OpenCV exception", e)
            }
        }.start()

        Log.d("APP_FLOW", "before setContent")
        setContent {
            MicroPadTheme {
                val viewModel: DatasetModel = viewModel()
                val navController = rememberNavController()
                /*CrashlyticsConsentDialog()*/
                MainContent(viewModel, navController)
            }
        }
    }
}

@Composable
fun MainContent(viewModel: DatasetModel, navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp)) {
                if (viewModel.isSimulating && currentRoute == "home") {
                    Button(
                        onClick = { viewModel.isSimulating = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("Cancel")
                    }
                } else if (currentRoute != "analysis") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                viewModel.syncNames()
                                scope.launch {
                                    ErrorHandler.safeExecute(context) {
                                        runNavigationSimulation(viewModel, navController, scope)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Instructions")
                        }

                        if (currentRoute == "home") {
                            IconButton(onClick = { navController.navigate("history") }) {
                                Icon(Icons.Default.History, contentDescription = "History")
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                AppNavHost(navController, viewModel, startDestination = "home")
            }
        }
    }
}

@Composable
fun FrontPage(navController: NavHostController, viewModel: DatasetModel) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val hasData = viewModel.pendingReferences.isNotEmpty() ||
                    viewModel.pendingSamples.isNotEmpty() ||
                    viewModel.referenceDataset != null

            val canProceed = (viewModel.referenceDataset != null || viewModel.pendingReferences.isNotEmpty()) &&
                    viewModel.pendingSamples.isNotEmpty()

            val canExportReference = viewModel.pendingReferences.isNotEmpty()

            val openAlertDialog = rememberSaveable { mutableStateOf(false) }

            if (openAlertDialog.value) {
                ReferenceOnlyDialog(
                    navigate = { navController.navigate("namingScreen") },
                    onDismissRequest = { openAlertDialog.value = false })
            }

            Column {
                if (hasData) {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restart Data Upload")
                    }
                }

                Button(
                    onClick = {
                        if (canProceed) {
                            navController.navigate("namingScreen")
                        } else {
                            openAlertDialog.value = true
                        }
                    },
                    enabled = canProceed || canExportReference,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp)
                ) {
                    Text("Next: Process & Name Wells")
                }
            }
        }
    ) { innerPadding ->
        HomePage(
            modifier = Modifier.padding(innerPadding),
            viewModel = viewModel,
            navController = navController
        )
    }
}

@Composable
fun HomePage(modifier: Modifier, viewModel: DatasetModel, navController: NavHostController) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ErrorReportBanner()
        OutlinedButton(
            onClick = { navController.navigate("cloudSync") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cloud Sync & Backups")
        }
        Text(
            text = "Analyze microPAD",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        DataAcquisitionCard(
            title = "Upload Reference Data",
            description = "Capture or upload the photos of reference microPADs. Otherwise, import a reference file (CSV).",
            count = viewModel.pendingReferences.size + (if (viewModel.referenceDataset != null) 1 else 0),
            onGallery = { navController.navigate("gallery_ref") },
            onCamera = { navController.navigate("camera_ref") },
            showCsv = true,
            onCsv = { uri ->
                if (uri != null) {
                    viewModel.setImportedFile(uri, context)
                    viewModel.setReferenceDataset(uri, context)
                }
            },
            isGalleryHighlighted = viewModel.highlightedButtonId == "ref_gallery",
            isCameraHighlighted = viewModel.highlightedButtonId == "ref_camera",
            isCsvHighlighted = viewModel.highlightedButtonId == "ref_csv"
        )

        DataAcquisitionCard(
            title = "Upload Test Samples",
            description = "Capture or upload the photos of microPADs for test samples.",
            count = viewModel.pendingSamples.size,
            onGallery = { navController.navigate("gallery_sample") },
            onCamera = { navController.navigate("camera_sample") },
            showCsv = false,
            isGalleryHighlighted = viewModel.highlightedButtonId == "sample_gallery",
            isCameraHighlighted = viewModel.highlightedButtonId == "sample_camera"
        )
    }
}
