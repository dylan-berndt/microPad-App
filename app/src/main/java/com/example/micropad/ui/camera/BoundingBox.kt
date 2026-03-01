package com.example.micropad.ui.camera

import androidx.compose.runtime.Composable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

@Composable
fun BoundingBoxOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenWidth = size.width
        val screenHeight = size.height

        // Define the bounds for the bounding box
        val boxWidth = screenWidth * 0.9f // Increased size for better fit
        val boxHeight = screenHeight * 0.6f
        val startX = (screenWidth - boxWidth) / 2f
        val startY = (screenHeight - boxHeight) / 2f
        val boxRect = Rect(startX, startY, startX + boxWidth, startY + boxHeight)

        // Create a cutout effect by clipping the bounding box area
        clipPath(
            path = Path().apply { addRect(boxRect) },
            clipOp = ClipOp.Difference
        ) {
            // Draw the dark, semi-transparent background everywhere except the cutout area
            drawRect(color = Color.Black.copy(alpha = 0.5f))
        }

        val strokeWidth = 2.dp.toPx()
        // Draw the white border for the bounding box
        drawRect(
            color = Color.White,
            topLeft = boxRect.topLeft,
            size = boxRect.size,
            style = Stroke(width = strokeWidth)
        )

        val cornerLength = 25.dp.toPx()
        val cornerStroke = Stroke(width = strokeWidth * 2) // Make corners thicker

        // Draw corner guides
        // Top-left corner
        var path = Path().apply {
            moveTo(boxRect.left, boxRect.top + cornerLength)
            lineTo(boxRect.left, boxRect.top)
            lineTo(boxRect.left + cornerLength, boxRect.top)
        }
        drawPath(path, color = Color.White, style = cornerStroke)

        // Top-right corner
        path = Path().apply {
            moveTo(boxRect.right - cornerLength, boxRect.top)
            lineTo(boxRect.right, boxRect.top)
            lineTo(boxRect.right, boxRect.top + cornerLength)
        }
        drawPath(path, color = Color.White, style = cornerStroke)

        // Bottom-left corner
        path = Path().apply {
            moveTo(boxRect.left, boxRect.bottom - cornerLength)
            lineTo(boxRect.left, boxRect.bottom)
            lineTo(boxRect.left + cornerLength, boxRect.bottom)
        }
        drawPath(path, color = Color.White, style = cornerStroke)

        // Bottom-right corner
        path = Path().apply {
            moveTo(boxRect.right - cornerLength, boxRect.bottom)
            lineTo(boxRect.right, boxRect.bottom)
            lineTo(boxRect.right, boxRect.bottom - cornerLength)
        }
        drawPath(path, color = Color.White, style = cornerStroke)
    }
}
