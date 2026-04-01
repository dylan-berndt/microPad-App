package com.example.micropad

import android.net.Uri
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
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.micropad.data.LabeledImage
import com.example.micropad.ui.AnalysisScreen
import com.example.micropad.ui.GalleryPickerScreen
import com.example.micropad.ui.ImportScreen
import com.example.micropad.ui.WellNamingScreen
import com.example.micropad.ui.camera.CameraScreen
import com.example.micropad.ui.GalleryReferenceFlow
import com.example.micropad.ui.LabelingScreen
import com.example.micropad.ui.theme.MicroPadTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    private val sharedViewModel: DatasetModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "Failed to load OpenCV")
        }

        enableEdgeToEdge()
        setContent {
            MicroPadTheme {
                MicroPadApp(sharedViewModel)
            }
        }
    }
}

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
        composable("import") {
            ImportScreen(viewModel, navController)
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
fun FrontPage(navController: NavController, viewModel: DatasetModel) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                val canProceed = (viewModel.referenceDataset != null || viewModel.pendingReferences.isNotEmpty()) && 
                                 viewModel.pendingSamples.isNotEmpty()
                
                if (currentDestination == AppDestinations.HOME) {
                    Button(
                        onClick = { navController.navigate("namingScreen") },
                        enabled = canProceed,
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
            when (currentDestination) {
                AppDestinations.HOME -> HomePage(
                    modifier = Modifier.padding(innerPadding),
                    viewModel = viewModel,
                    navController = navController
                )
                AppDestinations.GALLERY -> GalleryPickerScreen(navController)
                AppDestinations.CAMERA -> CameraScreen(onImagesProcessed = { uris ->
                    viewModel.temporaryUris = uris
                    viewModel.labelingTargetIsReference = false
                    navController.navigate("labelingScreen")
                })
            }
        }
    }
}

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
        Text(
            text = "Analyze microPAD",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Card 1: References
        DataAcquisitionCard(
            title = "1. Reference Data",
            description = "Upload known baselines (H2O, standard solutions, etc.)",
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
            title = "2. Test Samples",
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
                    Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
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

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Start", Icons.Default.Home),
    GALLERY("Gallery", Icons.Default.Collections),
    CAMERA("Camera", Icons.Default.PhotoCamera)
}
