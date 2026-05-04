package com.example.micropad.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.micropad.ui.FrontPage
import com.example.micropad.ui.features.cloud.CloudSyncScreen
import com.example.micropad.ui.features.acquisition.GalleryReferenceFlow
import com.example.micropad.ui.features.acquisition.LabelingScreen
import com.example.micropad.ui.features.acquisition.camera.CameraScreen
import com.example.micropad.ui.features.analysis.AnalysisConfigScreen
import com.example.micropad.ui.features.analysis.AnalysisScreen
import com.example.micropad.ui.features.analysis.WellNamingScreen
import com.example.micropad.ui.features.history.HistoryScreen
import com.example.micropad.data.viewmodel.DatasetModel


@Composable
fun AppNavHost(navController: NavHostController, viewModel: DatasetModel, startDestination: String = "home") {
    NavHost(navController = navController, startDestination = startDestination) {
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
        composable("history") {
            HistoryScreen(viewModel, navController)
        }
        composable("cloudSync") {
            CloudSyncScreen(viewModel, navController)
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
                onCancel = { navController.popBackStack() },
                isSimulating = viewModel.isSimulating
            )
        }
        composable("gallery_sample") {
            GalleryReferenceFlow(
                onImagesPicked = { uris ->
                    viewModel.temporaryUris = uris
                    viewModel.labelingTargetIsReference = false
                    navController.navigate("labelingScreen")
                },
                onCancel = { navController.popBackStack() },
                isSimulating = viewModel.isSimulating
            )
        }
    }
}
