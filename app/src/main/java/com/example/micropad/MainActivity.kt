package com.example.micropad

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.micropad.data.CsvImportButton
import com.example.micropad.data.DatasetModel
import com.example.micropad.ui.AnalysisScreen
import com.example.micropad.ui.AnalysisConfigScreen
import com.example.micropad.ui.WellNamingScreen
import com.example.micropad.ui.camera.CameraScreen
import com.example.micropad.ui.GalleryReferenceFlow
import com.example.micropad.ui.LabelingScreen
import com.example.micropad.data.ErrorHandler
import com.example.micropad.data.AppErrorLogger
import com.example.micropad.ui.theme.MicroPadTheme
import org.opencv.android.OpenCVLoader

/**
 * Creates the app and sets up the navigation.
 *
 * @param sharedViewModel The view model for the app.
 * @receiver The Composable calling this function.
 * @return Unit
 */
class MainActivity : ComponentActivity() {
    private val sharedViewModel: DatasetModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ErrorHandler.safeExecute(this) {
                if (!OpenCVLoader.initLocal()) {
                    throw Exception("OpenCV failed to load")
                }
            }

        enableEdgeToEdge()
        setContent {
            MicroPadTheme {
                MicroPadApp(sharedViewModel)
            }
        }
    }
}

/**
 * Sets up the navigation for the app.
 *
 * @param viewModel The view model for the app.
 * @receiver The Composable calling this function.
 * @return Unit
 */
@Composable
fun MicroPadApp(viewModel: DatasetModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            FrontPage(navController, viewModel)
        }
        composable("namingScreen") {
            WellNamingScreen(viewModel, navController)
        }
        composable("options") {
            AnalysisConfigScreen(viewModel, navController)
        }
        composable("analysis") {
            AnalysisScreen(viewModel, navController)
        }
        composable("labelingScreen") {
            LabelingScreen(viewModel, navController)
        }

        // Sub-flows for data acquisition
        composable("camera_ref") {
            CameraScreen(onImagesProcessed = { uris ->
                viewModel.temporaryUris = uris
                viewModel.labelingTargetIsReference = true
                navController.navigate("labelingScreen")
            })
        }
        composable("camera_sample") {
            CameraScreen(onImagesProcessed = { uris ->
                viewModel.temporaryUris = uris
                viewModel.labelingTargetIsReference = false
                navController.navigate("labelingScreen")
            })
        }
        composable("gallery_ref") {
            GalleryReferenceFlow(
                onImagesPicked = { uris ->
                    viewModel.temporaryUris = uris
                    viewModel.labelingTargetIsReference = true
                    navController.navigate("labelingScreen")
                },
                onCancel = { navController.popBackStack() }
            )
        }
        composable("gallery_sample") {
            GalleryReferenceFlow(
                onImagesPicked = { uris ->
                    viewModel.temporaryUris = uris
                    viewModel.labelingTargetIsReference = false
                    navController.navigate("labelingScreen")
                },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun ReferenceOnlyDialog(navigate: () -> Unit, onDismissRequest: () -> Unit) {
    AlertDialog(
        title = { Text("No Sample Data") },
        text = { Text("You have only imported reference data. Move on only if you are only exporting a reference sheet for later use.") },
        onDismissRequest = onDismissRequest,
        confirmButton = {TextButton(onClick = navigate) { Text("Next") }},
        dismissButton = {TextButton(onClick = onDismissRequest) { Text("Return") } }
    )
}

    @Composable
    fun ErrorReportBanner() {
        val context = LocalContext.current
        var showDialog by remember { mutableStateOf(AppErrorLogger.hasErrors(context)) }
        if (!showDialog) return

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Share system errors to improve the app?") },
            text = {
                Text("Errors were recorded during this session. You can share them anonymously to help us fix issues. The file contains no personal data.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = AppErrorLogger.buildShareIntent(context)
                    if (intent != null) {
                        context.startActivity(Intent.createChooser(intent, "Share error log"))
                    }
                    AppErrorLogger.clearLog(context)
                    showDialog = false
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = {
                    AppErrorLogger.clearLog(context)
                    showDialog = false
                }) { Text("Dismiss") }
            }
        )
    }
    @Composable
    fun FrontPage(navController: NavController, viewModel: DatasetModel) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                val hasData = viewModel.pendingReferences.isNotEmpty() ||
                              viewModel.pendingSamples.isNotEmpty() ||
                              viewModel.referenceDataset != null

                val canProceed = (viewModel.referenceDataset != null || viewModel.pendingReferences.isNotEmpty()) &&
                                 viewModel.pendingSamples.isNotEmpty()

                val canExportReference = viewModel.pendingReferences.isNotEmpty()

                val openAlertDialog = remember {mutableStateOf(false)}

                when {
                    openAlertDialog.value -> {
                        ReferenceOnlyDialog(
                            navigate = {navController.navigate("namingScreen")},
                            onDismissRequest = { openAlertDialog.value = false })
                    }
                }

                Column {
                    if (hasData) {
                        OutlinedButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                        }
                        else {
                            openAlertDialog.value = true
                        }},
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

/**
 * Show user the main screen of the app.
 *
 * @param modifier The modifier to apply to the layout.
 * @param viewModel The view model for the app.
 * @receiver The Composable calling this function.
 * @return Unit
 */
@Composable
fun HomePage(modifier: Modifier, viewModel: DatasetModel, navController: NavController) {
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
            Text(
                text = "Analyze microPAD",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

        // Card 1: References
        DataAcquisitionCard(
            title = "Upload Reference Data",
            description = "Upload known baselines (H2O, Fe(III), Fe(II), Ni(II), etc.)",
            count = viewModel.pendingReferences.size + (if (viewModel.referenceDataset != null) 1 else 0),
            onGallery = { navController.navigate("gallery_ref") },
            onCamera = { navController.navigate("camera_ref") },
            showCsv = true,
            onCsv = { uri ->
                if (uri != null) {
                    viewModel.setImportedFile(uri, context)
                    viewModel.setReferenceDataset(uri, context)
                }
            }
        )

        // Card 2: Samples
        DataAcquisitionCard(
            title = "Upload Test Samples",
            description = "Capture or upload the microPADs you want to analyze.",
            count = viewModel.pendingSamples.size,
            onGallery = { navController.navigate("gallery_sample") },
            onCamera = { navController.navigate("camera_sample") },
            showCsv = false
        )
    }
}

@Composable
fun DataAcquisitionCard(
    title: String,
    description: String,
    count: Int,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    showCsv: Boolean,
    onCsv: ((Uri?) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (count > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text("$count added", color = Color.White, modifier = Modifier.padding(4.dp))
                    }
                }
            }

            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gallery", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Camera", fontSize = 12.sp)
                }
            }

            if (showCsv && onCsv != null) {
                CsvImportButton(onFileSelected = onCsv)
            }
        }
    }
}
