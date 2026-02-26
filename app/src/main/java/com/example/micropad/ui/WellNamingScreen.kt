package com.example.micropad.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.content.MediaType.Companion.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.micropad.data.SampleDataset
import com.example.micropad.data.ingestImages
import kotlinx.coroutines.launch


fun urisToString(addresses: List<Uri>): String {
    val uriStrings = addresses.map { it.toString() }
    val encoded = Uri.encode(uriStrings.joinToString(","))

    return encoded
}

fun stringToURIs(data: String): List<Uri> {
    val uris = if (data.isNotEmpty()) {
        data.split(",").map { it.toUri() }
    } else {
        emptyList()
    }

    return uris
}


class ImageViewModel : ViewModel() {
    var dataset by mutableStateOf<SampleDataset?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun ingest(uris: List<Uri>, context: Context) {
        viewModelScope.launch {
            isLoading = true
            dataset = ingestImages(uris, context, log=true)
            isLoading = false
        }
    }
}


// TODO: Allow users to rename dye wells, and reorder dye wells on each image
@Composable
fun WellNamingScreen(addresses: List<Uri>, viewModel: ImageViewModel = viewModel()) {
    val context = LocalContext.current

    LaunchedEffect(addresses) {
        viewModel.ingest(addresses, context)
    }

    // Allows dataset to load concurrently using multiple threads
    if (viewModel.isLoading) {
        Column(modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
    } else {
        viewModel.dataset?.let { dataset ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dataset.samples) { sample ->
                    if (sample.ordering != null) {
                        Image(
                            bitmap = sample.ordering!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
            }
        }
    }


}