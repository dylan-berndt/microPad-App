package com.example.micropad

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.micropad.data.CsvImportButton
import com.example.micropad.data.CsvExportButton
import com.example.micropad.ui.CameraScreen
import com.example.micropad.ui.theme.MicroPadTheme
import com.example.micropad.ui.GalleryPickerScreen
import com.example.micropad.ui.WellNamingScreen
import com.example.micropad.ui.stringToURIs

import org.opencv.android.OpenCVLoader

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

@PreviewScreenSizes
@Composable
fun MicroPadApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            FrontPage(navController)
        }
        composable("namingScreen/{uris}",
            arguments = listOf(navArgument("uris") {type = NavType.StringType}))
        { backStackEntry ->
            val data = backStackEntry.arguments?.getString("uris") ?: ""
            WellNamingScreen(stringToURIs(data))
        }
    }
}

@Composable
fun FrontPage(navController: NavController) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> Greeting(
                    name = "Android",
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.GALLERY -> GalleryPickerScreen(navController)
                AppDestinations.CAMERA -> CameraScreen(onImageCapture = { uri ->
                    Log.d("MainActivity", "Image captured: $uri")
                })
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    GALLERY("Gallery", Icons.Default.Favorite),
    CAMERA("Camera", Icons.Default.Add)
}

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

                val isValid = CsvParser.parseAndValidate(
                    context.contentResolver,
                    uri
                )

                resultMessage = if (isValid) {
                    "✅ CSV schema is valid"
                } else {
                    "❌ Invalid CSV schema"
                }
            }
        }

        CsvExportButton { uri ->
            if (uri != null) {
                val isValid = CsvParser.parseAndValidate(
                    context.contentResolver, uri
                )

                resultMessage = if (isValid) {
                    "✅ CSV schema is valid"
                } else {
                    "❌ Invalid CSV schema"
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = resultMessage)
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MicroPadTheme {
        Greeting("Android")
    }
}
