package com.example.micropad.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Presents user with boxes where to align the calibration dots during image capture.
 */
@Composable
fun BoundingBoxOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val screenWidth = size.width
            val screenHeight = size.height

            // 1. CHANGE SIZE: Increase or decrease the dp value here
            val squareSize = 30.dp.toPx()
            
            // 2. CHANGE SPACING: Change the distance between the squares
            val spacing = 10.dp.toPx()
            
            val totalWidth = (squareSize * 4) + (spacing * 3)
            
            // 3. CHANGE HORIZONTAL POSITION: 
            // Replace this calculation with a specific value if you don't want it centered
            val startX = (screenWidth - totalWidth) / 2f
            
            // 4. CHANGE VERTICAL POSITION:
            // Adjust the '2f' or add/subtract to move the row up or down
            // e.g., (screenHeight / 3f) would move them higher up.
            val centerY = screenHeight / 1.4f - (squareSize / 2f)

            val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

            for (i in 0 until 4) {
                val x = startX + (i * (squareSize + spacing))
                drawRect(
                    color = Color.White,
                    topLeft = Offset(x, centerY),
                    size = Size(squareSize, squareSize),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = dashPathEffect
                    )
                )
            }
        }

        // Instructional text at the bottom
        Text(
            text = "Please align the calibration squares on the microPAD with the 4 squares seen on the screen",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .padding(horizontal = 32.dp)
        )
    }
}
