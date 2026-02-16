package com.example.micropad.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.android.Utils
import androidx.core.graphics.createBitmap

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log

import java.io.InputStream
import java.io.FileOutputStream
import java.io.File


fun saveMat(mat: Mat, filename: String, context: Context): String? {
    return try {
        val bitmap = createBitmap(mat.cols(), mat.rows())
        Utils.matToBitmap(mat, bitmap)

        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File(picturesDir, filename)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        Log.d("Image", "Saved " + file.absolutePath)

        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


// Finds any enclosed shapes in a given image
// Useful for rectangle and dot finding processes
fun findContours(image: Mat, context: Context, log: Boolean): ArrayList<MatOfPoint> {
    val gray = Mat()
    Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY)

    val blurred = Mat()
    Imgproc.GaussianBlur(gray, blurred, Size(7.0, 7.0), 0.0)

    if (log) {saveMat(blurred, "blurred.png", context)}

    val thresh = Mat()
    Imgproc.adaptiveThreshold(blurred, thresh, 255.0,
        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
        35, 2.0)

    if (log) {saveMat(thresh, "threshold.png", context)}

    // Find enclosed shapes in the image, this includes stuff like the dye dots
    val contours = ArrayList<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

    val visualization = image.clone()
    // Draw all contours; for debugging
    for (i in contours.indices) {
        val color = Scalar(0.0, 255.0, 0.0) // Green in BGR
        Imgproc.drawContours(visualization, contours, i, color, 2) // thickness = 2
    }
    if (log) {saveMat(visualization, "contours.png", context)}

    gray.release()
    thresh.release()
    hierarchy.release()

    return contours
}


// Attempts to find the calibration rectangle in the image
// Returns a pair consisting of the rectangle portion of the image and its bounding box
fun findCalibrationSquares(image: Mat, contours: ArrayList<MatOfPoint>, context: Context, log: Boolean): MutableList<Pair<Mat, Point>> {
    var shapes: MutableList<Pair<Mat, Point>> = mutableListOf<Pair<Mat, Point>>()

    // Check each contour, one of them should be a rectangle
    var i = 0
    for (contour in contours) {
        val contour2f = MatOfPoint2f(*contour.toArray())
        val peri = Imgproc.arcLength(contour2f, true)
        val approx = MatOfPoint2f()
        // Attempt to create a rough polygon from the contour
        Imgproc.approxPolyDP(contour2f, approx, 0.04 * peri, true)

        if (approx.total() == 4L) {
            val area = Imgproc.contourArea(contour)

            // Remove any small shapes
            if (area > 100) {
                val corners = approx.toArray().toList()
                val rect = Imgproc.boundingRect(contour)

                val ratio = rect.width.toDouble() / rect.height.toDouble()
                // Make sure we're dealing with squares
                if (0.9 < ratio && ratio < 1.1) {
                    val calibrationRegion = Mat(image, rect)

                    Log.d("Image", "Ratio DD " + ratio.toString() + " " + i.toString())

                    val centroid = Point(
                        (corners[0].x + corners[1].x) / 2,
                        (corners[0].y + corners[1].y) / 2
                    )

                    val pair = Pair(calibrationRegion, centroid)
                    shapes.add(pair)
                }
                else {
                    Log.d("Image", "Ratio " + ratio.toString())
                }
            }

            i += 1
        }
    }

    shapes.sortBy { it.second.x }
    if (log) {
        i = 0
        for (shape in shapes) {
            saveMat(shape.first, "calibrationArea" + i.toString() + ".png", context)
            i += 1
        }
    }
    Log.d("Image", "Shapes " + shapes.size.toString())

    return shapes
}


// Extract the calibration colors from a calibration region
fun extractCalibrationColors(shapes: MutableList<Pair<Mat, Point>>): List<Scalar> {
    val colors = mutableListOf<Scalar>()

    for (shape in shapes) {
        val mean = Core.mean(shape.first)
        colors.add(mean)
    }

    return colors
}


// Rebalances the image given a set of the found color points and the intended reference color
fun rebalanceImage(image: Mat, found: List<Scalar>, reference: List<Scalar>): Mat {
    val balanced = image.clone()

    // Find a linear fit that tries to push image brightness towards calibration standard
    // Cannot always find a perfect match, but that's also bc I don't know the expected RGB
    // of each of the calibration points
    fun computeLinearFit(measured: DoubleArray, expected: DoubleArray): Pair<Double, Double> {
        val n = measured.size
        val sumX = measured.sum()
        val sumY = expected.sum()
        val sumXY = measured.zip(expected).sumOf { it.first * it.second }
        val sumX2 = measured.sumOf { it * it }

        val scale = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val offset = (sumY - scale * sumX) / n

        return Pair(scale, offset)
    }

    // Ugh
    val bMeasured = found.map { it.`val`[0] }.toDoubleArray()
    val gMeasured = found.map { it.`val`[1] }.toDoubleArray()
    val rMeasured = found.map { it.`val`[2] }.toDoubleArray()

    val bExpected = reference.map { it.`val`[0] }.toDoubleArray()
    val gExpected = reference.map { it.`val`[1] }.toDoubleArray()
    val rExpected = reference.map { it.`val`[2] }.toDoubleArray()

    // Compute linear fits
    val (bScale, bOffset) = computeLinearFit(bMeasured, bExpected)
    val (gScale, gOffset) = computeLinearFit(gMeasured, gExpected)
    val (rScale, rOffset) = computeLinearFit(rMeasured, rExpected)

    Log.d("Image", "Measured B: ${bMeasured.contentToString()}")
    Log.d("Image", "Expected B: ${bExpected.contentToString()}")
    Log.d("Image", "Scale/Offset - B: $bScale, $bOffset | G: $gScale, $gOffset | R: $rScale, $rOffset")

    // Apply correction
    val channels = ArrayList<Mat>()
    Core.split(balanced, channels)

    channels[0].convertTo(channels[0], -1, bScale, bOffset)
    channels[1].convertTo(channels[1], -1, gScale, gOffset)
    channels[2].convertTo(channels[2], -1, rScale, rOffset)

    Core.merge(channels, balanced)

    // Clean up
    channels.forEach { it.release() }

    return balanced
}


// TODO: Find a better way to rebalance images
fun rebalanceImage2(image: Mat, found: List<Scalar>, reference: List<Scalar>): Mat {
    val balanced = image.clone()

    var bGain = 0.0
    var gGain = 0.0
    var rGain = 0.0

    var count = 0
    for (i in found.indices) {
        if (found[i].`val`[0] > 10) {
            bGain += reference[i].`val`[0] / found[i].`val`[0]
            count++
        }
        if (found[i].`val`[1] > 10) {
            gGain += reference[i].`val`[1] / found[i].`val`[1]
        }
        if (found[i].`val`[2] > 10) {
            rGain += reference[i].`val`[2] / found[i].`val`[2]
        }
    }

    bGain /= count
    gGain /= count
    rGain /= count

    // Clamp gains to reasonable range
    bGain = bGain.coerceIn(0.5, 2.0)
    gGain = gGain.coerceIn(0.5, 2.0)
    rGain = rGain.coerceIn(0.5, 2.0)

    println("Gains - B: $bGain, G: $gGain, R: $rGain")

    // Apply correction
    val channels = ArrayList<Mat>()
    Core.split(balanced, channels)

    channels[0].convertTo(channels[0], -1, bGain, 0.0)
    channels[1].convertTo(channels[1], -1, gGain, 0.0)
    channels[2].convertTo(channels[2], -1, rGain, 0.0)

    Core.merge(channels, balanced)
    channels.forEach { it.release() }

    return balanced
}


// Finds the donut shapes in the preprocessed image
// Returns a List of all dots that were found, each element consisting of:
// List<Point>: the bounding box, and Scalar: the extracted color value
fun findDots(image: Mat, contours: ArrayList<MatOfPoint>, context: Context, log: Boolean): List<Pair<List<Point>, Scalar>>? {
    // TODO: Check for area, color, and number of holes in each contour to find dots
    // TODO: Either rotate images or determine an ordering scheme that is consistent

    return null
}


// Colors used on dye sheet, arranged in BGR ordering
val expectedColors = listOf(
    Scalar(0.0, 0.0, 0.0),       // Black
    Scalar(255.0, 255.0, 0.0),   // Cyan (B=255, G=255, R=0)
    Scalar(0.0, 255.0, 255.0),   // Yellow (B=0, G=255, R=255)
    Scalar(255.0, 0.0, 255.0)    // Magenta (B=255, G=0, R=255)
)


// Perform the full preprocessing of the image
// Could potentially return dot locations and values later
fun preprocessImage(image: Mat, context: Context, log: Boolean = false): Mat {
    val contours = findContours(image, context, log)
    val shapes = findCalibrationSquares(image, contours, context, log)

    val colors = extractCalibrationColors(shapes)

    val balanced = rebalanceImage2(image, colors, expectedColors)

    return balanced
}


fun ingestImages(addresses: List<Uri>, context: Context): List<Mat?> {
    val images: MutableList<Mat?> = mutableListOf<Mat?>().toMutableList();
    for (uri in addresses) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val mat = Mat()
        Utils.bitmapToMat(mutableBitmap, mat)

        val preprocessed = preprocessImage(mat, context, log=true)
        saveMat(preprocessed, "preprocessed.png", context)

        images += preprocessed
    }

    return images
}