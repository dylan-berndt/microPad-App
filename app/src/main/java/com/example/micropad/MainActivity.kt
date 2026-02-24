package com.example.micropad

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.micropad.data.CsvImportButton
import com.example.micropad.data.model.Roi
import com.example.micropad.ui.CameraScreen
import com.example.micropad.ui.GalleryPickerScreen
import com.example.micropad.ui.RoiLabelingScreen
import com.example.micropad.ui.RoiViewModel
import com.example.micropad.ui.theme.MicroPadTheme
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Scalar
import com.example.micropad.data.preprocessImage
import com.example.micropad.data.findDots
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "Failed to load OpenCV")
        }

        enableEdgeToEdge()
        setContent {
            MicroPadTheme {
                MicroPadApp()
            }
        }
    }
}

@Composable
fun MicroPadApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val roiViewModel = remember { RoiViewModel() }
    val context = LocalContext.current

    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

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
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            when (currentDestination) {
                AppDestinations.HOME -> Greeting(
                    name = "Android",
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.GALLERY -> GalleryPickerScreen(
                    selectedImageUris = selectedImageUris,
                    onImagesPicked = { uris: List<Uri> ->
                        selectedImageUris = uris
                    },
                    onNextClicked = {
                        if (selectedImageUris.isNotEmpty()) {
                            val uri = selectedImageUris.first()

                            scope.launch {
                                isLoading = true

                                val rois = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    handlePickedImage(uri, roiViewModel, context)
                                }

                                isLoading = false

                                if (rois.isNotEmpty()) {
                                    currentDestination = AppDestinations.ROI
                                }
                            }
                        }
                    }
                )
                AppDestinations.CAMERA -> CameraScreen(
                    onImageCapture = {
                        pickedUri?.let { uri ->
                            scope.launch {
                                isLoading = true

                                val rois = withContext(Dispatchers.IO) {
                                    handlePickedImage(uri, roiViewModel, context)
                                }

                                isLoading = false

                                if (rois.isNotEmpty()) {
                                    currentDestination = AppDestinations.ROI
                                    pickedUri = null
                                }
                            }
                        }
                    },
                    onUriCaptured = { uri: Uri ->
                        pickedUri = uri
                    }
                )
                AppDestinations.PROFILE -> {
                    Text("Profile Screen")
                }
                AppDestinations.ROI -> RoiLabelingScreen(viewModel = roiViewModel)
            }
        }
    }
}

// --- Image Handling ---
fun handlePickedImage(uri: Uri, roiViewModel: RoiViewModel, context: Context): List<Roi> {

    // 1️⃣ Load bitmap
    val originalBitmap: Bitmap =
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)

    // 2️⃣ Downscale (VERY IMPORTANT)
    val maxSize = 1200  // adjust if needed
    val ratio = minOf(
        maxSize.toFloat() / originalBitmap.width,
        maxSize.toFloat() / originalBitmap.height
    )

    val scaledBitmap = Bitmap.createScaledBitmap(
        originalBitmap,
        (originalBitmap.width * ratio).toInt(),
        (originalBitmap.height * ratio).toInt(),
        true
    )

    // 3️⃣ Convert to Mat
    val mat = Mat()
    Utils.bitmapToMat(scaledBitmap, mat)

    // 4️⃣ Preprocess (NO logging)
    val (_, roiList) = preprocessImage(mat, context, log = false)

    // 5️⃣ Update ViewModel
    roiViewModel.setRois(roiList)

    return roiList
}

// --- Destinations ---
enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    GALLERY("Gallery", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
    CAMERA("Camera", Icons.Default.Add),
    ROI("ROI", Icons.Default.Add)
}

// --- Greeting Composable ---
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var resultMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Hello $name!")
        Spacer(modifier = Modifier.height(16.dp))

        CsvImportButton { uri ->
            if (uri != null) {
                val isValid = CsvParser.parseAndValidate(context.contentResolver, uri)
                resultMessage = if (isValid) "✅ CSV schema is valid" else "❌ Invalid CSV schema"
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = resultMessage)
    }
}

// --- Previews ---
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MicroPadTheme { Greeting("Android") }
}

@Preview(showBackground = true)
@Composable
fun MicroPadAppPreview() {
    MicroPadTheme { MicroPadApp() }
}