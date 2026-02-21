package com.example.micropad.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.content.MediaType.Companion.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.micropad.data.ingestImages


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

@Composable
fun WellNamingScreen(addresses: List<Uri>) {
    val context = LocalContext.current
    val samples = ingestImages(addresses, context)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(samples) { sample ->
            Image(
                bitmap = sample.ordering.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}