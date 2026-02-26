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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

import java.io.InputStream
import java.io.FileOutputStream
import java.io.File

import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt


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
    Imgproc.GaussianBlur(gray, blurred, Size(9.0, 9.0), 0.0)

    if (log) {
        saveMat(blurred, "blurred.png", context)
    }

    val thresh = Mat()
    Imgproc.adaptiveThreshold(
        blurred, thresh, 255.0,
        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
        35, 2.0
    )

    if (log) {
        saveMat(thresh, "threshold.png", context)
    }

    // Find enclosed shapes in the image, this includes stuff like the dye dots
    val contours = ArrayList<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(
        thresh,
        contours,
        hierarchy,
        Imgproc.RETR_EXTERNAL,
        Imgproc.CHAIN_APPROX_SIMPLE
    )

    val visualization = image.clone()
    // Draw all contours; for debugging
    for (i in contours.indices) {
        val color = Scalar(0.0, 255.0, 0.0) // Green in BGR
        Imgproc.drawContours(visualization, contours, i, color, 2) // thickness = 2
    }
    if (log) {
        saveMat(visualization, "contours.png", context)
    }

    gray.release()
    thresh.release()
    hierarchy.release()

    return contours
}


/**
 * Detects the white card in the image, applies a perspective warp,
 * and returns a flattened, top-down crop of just the card.
 * Returns null if no card candidate is found.
 */
fun findAndWarpCard(image: Mat, context: Context, log: Boolean): Mat? {
    val hsv = Mat()
    Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV)

    val whiteMask = Mat()
    Core.inRange(
        hsv,
        Scalar(0.0, 0.0, 180.0),    // min: any hue, low saturation, bright
        Scalar(180.0, 40.0, 255.0),  // max: any hue, low saturation, max brightness
        whiteMask
    )

    if (log) saveMat(whiteMask, "card_white_mask.png", context)

    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
    val closed = Mat()
    Imgproc.morphologyEx(whiteMask, closed, Imgproc.MORPH_CLOSE, kernel)

    if (log) saveMat(closed, "card_thresh.png", context)

    val contours = ArrayList<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

    // Pick largest contour that covers at least 10% of the frame
    val best = contours
        .filter { Imgproc.contourArea(it) > image.total() * 0.10 }
        .maxByOrNull { Imgproc.contourArea(it) }

    if (best == null) {
        Log.w("Image", "findAndWarpCard: no card contour found")
        return null
    }

    // Use bounding rect instead of trying to find exact corners
    val rect = Imgproc.boundingRect(best)
    val cropped = Mat(image, rect)

    if (log) saveMat(cropped, "card_warped.png", context)

    hsv.release(); whiteMask.release()
    closed.release(); hierarchy.release()

    return cropped
}


// Attempts to find the calibration rectangle in the image
// Returns a pair consisting of the rectangle portion of the image and its bounding box
fun findCalibrationSquares(image: Mat, contours: ArrayList<MatOfPoint>, context: Context, log: Boolean): MutableList<Pair<Mat, Point>> {

    // --- Step 1: collect all square-ish candidates ---
    data class Candidate(val region: Mat, val center: Point, val area: Double)
    val candidates = mutableListOf<Candidate>()

    for (contour in contours) {
        val area = Imgproc.contourArea(contour)
        if (area < 50) continue

        val contour2f = MatOfPoint2f(*contour.toArray())
        val peri = Imgproc.arcLength(contour2f, true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approx, 0.04 * peri, true)

        if (approx.total() !in 4L..6L) continue

        val rect = Imgproc.boundingRect(contour)
        val ratio = rect.width.toDouble() / rect.height.toDouble()
        if (ratio < 0.7 || ratio > 1.3) continue

        val m = Imgproc.moments(contour)
        if (m.m00 == 0.0) continue
        val center = Point(m.m10 / m.m00, m.m01 / m.m00)

        candidates.add(Candidate(Mat(image, rect), center, area))
    }

    if (candidates.size < 4) {
        Log.w("Image", "Not enough square candidates: ${candidates.size}")
        return mutableListOf()
    }

    // --- Step 2: find the median area and filter outliers ---
    val sortedAreas = candidates.map { it.area }.sorted()
    val medianArea = sortedAreas[sortedAreas.size / 2]
    val areaFiltered = candidates.filter { abs(it.area - medianArea) / medianArea < 0.5 }

    if (areaFiltered.size < 4) {
        Log.w("Image", "Not enough candidates after area filter: ${areaFiltered.size}")
        return mutableListOf()
    }

    // --- Step 3: among all combinations of 4, pick the most collinear group ---
    // Collinearity score: fit a line through 4 points, sum of squared residuals
    fun collinearityScore(pts: List<Point>): Double {
        // Use PCA / linear regression on the 4 centers
        val cx = pts.map { it.x }.average()
        val cy = pts.map { it.y }.average()

        var sxx = 0.0; var sxy = 0.0; var syy = 0.0
        for (p in pts) {
            val dx = p.x - cx; val dy = p.y - cy
            sxx += dx * dx; sxy += dx * dy; syy += dy * dy
        }

        // Direction of best-fit line via angle of covariance matrix
        val angle = 0.5 * atan2(2 * sxy, sxx - syy)
        val nx = -sin(angle); val ny = cos(angle) // normal to the line

        // Sum of squared perpendicular distances
        return pts.sumOf { p ->
            val dx = p.x - cx; val dy = p.y - cy
            (dx * nx + dy * ny).pow(2)
        }
    }

    // Also check that the 4 points are roughly evenly spaced
    fun spacingScore(pts: List<Point>): Double {
        val cx = pts.map { it.x }.average()
        val cy = pts.map { it.y }.average()
        val angle = atan2(pts.last().y - pts.first().y, pts.last().x - pts.first().x)

        // Project onto the line direction and check spacing variance
        val projections = pts.map { p ->
            (p.x - cx) * cos(angle) + (p.y - cy) * sin(angle)
        }.sorted()

        val gaps = projections.zipWithNext { a, b -> b - a }
        val meanGap = gaps.average()
        return gaps.sumOf { (it - meanGap).pow(2) } / meanGap.pow(2)
    }

    var bestScore = Double.MAX_VALUE
    var bestGroup = areaFiltered.take(4)

    // n choose 4 — only feasible if candidates stay small (they should after area filter)
    val n = areaFiltered.size
    for (i in 0 until n) for (j in i+1 until n) for (k in j+1 until n) for (l in k+1 until n) {
        val group = listOf(areaFiltered[i], areaFiltered[j], areaFiltered[k], areaFiltered[l])
        val pts = group.map { it.center }
        val score = collinearityScore(pts) + spacingScore(pts) * medianArea
        if (score < bestScore) {
            bestScore = score
            bestGroup = group
        }
    }

    val shapes = bestGroup
        .sortedBy { it.center.x}
        .map { Pair(it.region, it.center) }
        .toMutableList()

    if (log) {
        shapes.forEachIndexed { i, shape -> saveMat(shape.first, "calibrationArea$i.png", context) }
    }
    Log.d("Image", "Shapes found: ${shapes.size}, best score: $bestScore")

    return shapes
}


// Extract the calibration colors from a calibration region
fun extractCalibrationColors(shapes: MutableList<Pair<Mat, Point>>): MutableList<Scalar> {
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
    val rMeasured = found.map { it.`val`[0] }.toDoubleArray()
    val gMeasured = found.map { it.`val`[1] }.toDoubleArray()
    val bMeasured = found.map { it.`val`[2] }.toDoubleArray()

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


fun getCenter(contour: MatOfPoint): Point {
    val m = Imgproc.moments(contour)
    return Point(m.m10 / m.m00, m.m01 / m.m00)
}


// Used to shrink a contour around its center
// Primarily exists to extract the center of the dye dots
fun shrinkContour(contour: MatOfPoint, shrink: Float): MatOfPoint {
    val center = getCenter(contour)

    val points: MutableList<Point> = mutableListOf<Point>()
    for (point in contour.toList()) {
        val direction = Point(point.x - center.x, point.y - center.y)
        val offset = Point(direction.x * shrink, direction.y * shrink)
        val newPoint = Point(center.x + offset.x, center.y + offset.y)

        points.add(newPoint)
    }

    val newContour = MatOfPoint(*points.toTypedArray())

    return newContour
}


fun drawOrdering(image: Mat, orderedDots: List<Pair<MatOfPoint, Scalar>>): Bitmap {
    val output = image.clone()

    for ((index, pair) in orderedDots.withIndex()) {
        val contour = pair.first
        val center = getCenter(contour)

        // Estimate dot radius from contour area
        val area = Imgproc.contourArea(contour)
        val radius = sqrt(area / Math.PI)

        // Scale font so text fills roughly half the dot diameter
        val fontScale = radius / 20.0
        val thickness = (fontScale * 3).toInt().coerceAtLeast(1)
        val outlineThickness = thickness + 4

        val text = (index + 1).toString()

        // Measure text so we can center it
        val baseline = IntArray(1)
        val textSize = Imgproc.getTextSize(text, Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, thickness, baseline)
        val textOrigin = Point(
            center.x - textSize.width / 2.0,
            center.y + textSize.height / 2.0
        )

        Imgproc.drawContours(output, listOf(contour), -1, Scalar(0.0, 0.0, 0.0, 255.0), 6)

        // White outline drawn first, then black text on top
        Imgproc.putText(output, text, textOrigin, Imgproc.FONT_HERSHEY_SIMPLEX,
            fontScale, Scalar(255.0, 255.0, 255.0, 255.0), outlineThickness)
        Imgproc.putText(output, text, textOrigin, Imgproc.FONT_HERSHEY_SIMPLEX,
            fontScale, Scalar(0.0, 0.0, 0.0, 255.0), thickness)
    }

    val bitmap = createBitmap(output.cols(), output.rows())
    Utils.matToBitmap(output, bitmap)

    return bitmap
}


fun assignGridIndices(
    dots: List<Pair<MatOfPoint, Scalar>>
): List<Pair<Pair<MatOfPoint, Scalar>, Pair<Int, Int>>> {
    if (dots.isEmpty()) return emptyList()

    val centers = dots.map { Pair(it, getCenter(it.first)) }

    // Estimate grid spacing from nearest-neighbor distances
    val distances = centers.map { (_, p) ->
        centers
            .filter { (_, q) -> q != p }
            .minOf { (_, q) -> Math.hypot(q.x - p.x, q.y - p.y) }
    }
    val spacing = distances.average()
    // At 30 degrees of rotation, a neighbor in the same row can be
    // offset vertically by spacing * sin(30) = spacing * 0.5
    // Use 0.6 to give a little headroom
    val rowTolerance = spacing * 0.6

    // Group into rows by Y proximity, using spacing-aware tolerance
    val sortedByY = centers.sortedBy { it.second.y }
    val rows = mutableListOf<MutableList<Pair<Pair<MatOfPoint, Scalar>, Point>>>()

    for (item in sortedByY) {
        val matchingRow = rows.find { row ->
            val avgY = row.map { it.second.y }.average()
            Math.abs(item.second.y - avgY) < rowTolerance
        }
        if (matchingRow != null) {
            matchingRow.add(item)
        } else {
            rows.add(mutableListOf(item))
        }
    }

    // Within each row, sort by X to get column index
    return rows
        .sortedBy { row -> row.map { it.second.y }.average() }
        .flatMapIndexed { rowIdx, row ->
            row.sortedBy { it.second.x }
                .mapIndexed { colIdx, item ->
                    Pair(item.first, Pair(rowIdx, colIdx))
                }
        }
}


fun extractContour(image: Mat, contour: MatOfPoint): Mat {
    val mask = Mat.zeros(image.size(), CvType.CV_8UC1)
    Imgproc.drawContours(mask, listOf(contour), 0,
        Scalar(255.0), Imgproc.FILLED)

    val extractedData = Mat()
    image.copyTo(extractedData, mask)

    return extractedData
}


fun extractDyeColor(extractedMat: Mat): Scalar {
    // Convert to HSV for better color filtering
    val hsv = Mat()
    Imgproc.cvtColor(extractedMat, hsv, Imgproc.COLOR_BGR2HSV)

    // Create a mask that excludes white pixels and black (outside contour) pixels
    // White in HSV: low saturation, high value
    // Black (outside contour): near-zero value
    val colorMask = Mat()
    Core.inRange(
        hsv,
        Scalar(0.0, 15.0, 50.0),   // min: any hue, low saturation threshold, not too dark
        Scalar(180.0, 255.0, 255.0), // max: full range
        colorMask
    )

    // Also exclude near-white pixels (high value + low saturation)
    val whiteMask = Mat()
    Core.inRange(
        hsv,
        Scalar(0.0, 0.0, 200.0),   // high brightness
        Scalar(180.0, 40.0, 255.0), // low saturation = white
        whiteMask
    )

    // Subtract white mask from color mask
    Core.subtract(colorMask, whiteMask, colorMask)

    // Get mean color of remaining pixels in BGR
    val meanColor = Core.mean(extractedMat, colorMask)
    return meanColor
}


// Finds the donut shapes in the preprocessed image
// Returns a List of all dots that were found, each element consisting of:
// MatOfPoint: the dot contour, and Scalar: the extracted color value
// Attempts to order dots from left to right, then top to bottom
fun findDots(image: Mat, contours: ArrayList<MatOfPoint>, context: Context, log: Boolean, shrink: Float = 0.4f): MutableList<Pair<MatOfPoint, Scalar>> {
    val candidates: MutableList<Pair<MatOfPoint, Scalar>> = mutableListOf<Pair<MatOfPoint, Scalar>>()

    var i = 0
    for (contour in contours) {
        val area = Imgproc.contourArea(contour)
        val rect = Imgproc.boundingRect(contour)
        val diameter = (rect.width + rect.height) / 2.0

        // Check if the area of the contour matches a circle
        // with the same diameter as the width of the contour
        val circularArea = (diameter / 2.0).pow(2) * 3.141592
        val areaError = abs(((circularArea - area) / area))

        // Check if the perimeter is also circular enough
        val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
        val circularity = 4 * Math.PI * area / (perimeter * perimeter)
        val perimeterError = abs(1 - circularity)

        if (areaError < 0.3 && perimeterError < 0.4 && area > 100) {
            Log.d("Image", "Dots " + areaError.toString() + " " + perimeterError.toString())

            val center = shrinkContour(contour, shrink)
            val extractedData = extractContour(image, center)

            if (log) {saveMat(extractedData, "candidate " + i.toString() + ".png", context)}

            val dataPoint = extractDyeColor(extractedData)
            val pair = Pair(center, dataPoint)
            candidates.add(pair)
            i += 1
        }
    }

    val sizeSorted = candidates
        .map { it to Pair(it, Imgproc.contourArea(it.first)) }
        .sortedByDescending { it.second.second }

    val top = sizeSorted.take(4)
    val areas = top.map { it.second.second }
    val medianArea = areas.sorted()[areas.size / 2]

    val tolerance = 0.2
    val finalDots = sizeSorted.filter { (_, area) ->
        abs(area.second - medianArea) / medianArea < tolerance
    }.map { it.first }

    if (log) {
        i = 0
        for (pair in finalDots) {
            val contour = pair.first
            val mask = Mat.zeros(image.size(), CvType.CV_8UC1)
            Imgproc.drawContours(mask, listOf(contour), 0,
                Scalar(255.0), Imgproc.FILLED)

            val extractedData = Mat()
            image.copyTo(extractedData, mask)

            saveMat(extractedData, "center " + i.toString() + ".png", context)
            i += 1
        }
    }

    val indexed = assignGridIndices(finalDots)

    // Sort row-major (top to bottom, left to right)
    val sorted = indexed
        .sortedWith(compareBy({ it.second.first }, { it.second.second }))
        .map { it.first }

    return sorted.toMutableList()
}


// Colors used on dye sheet, arranged in BGR ordering
val expectedColors = mutableListOf(
    Scalar(0.0, 0.0, 0.0),       // Black
    Scalar(255.0, 255.0, 0.0),   // Cyan (B=255, G=255, R=0)
    Scalar(0.0, 255.0, 255.0),   // Yellow (B=0, G=255, R=255)
    Scalar(255.0, 0.0, 255.0)    // Magenta (B=255, G=0, R=255)
)


// Perform the full preprocessing of the image
fun preprocessImage(image: Mat, context: Context, log: Boolean, normalizationStrategy: String): Sample {{}
    val contours = findContours(image, context, log)

    val shapes = findCalibrationSquares(image, contours, context, log)
    val colors = extractCalibrationColors(shapes)

    val dots = findDots(image, contours, context, log)

    // Requires that control dot is in top left
    val controlDot = extractDyeColor(extractContour(image, dots[0].first))

    // TODO: Normalization modes: MinMax, Z-Score
    var balanced = image
    if (normalizationStrategy == "Regression") {
        balanced = rebalanceImage(image, colors, expectedColors)
    }
    else {
        colors.add(0, controlDot)
        expectedColors.add(0, Scalar(255.0, 255.0, 255.0))
        if (normalizationStrategy == "MinMax") {

        }
        else if (normalizationStrategy == "Z-Score") {

        }
    }

    val dotColors = dots.map {
        extractDyeColor(extractContour(balanced, it.first))
    }

    val orderingImage = drawOrdering(balanced, dots)
    for (color in dotColors) {
        Log.d("Image", "Color: $color")
    }

    return Sample(image, balanced, orderingImage, dots)
}

/**
 * A function for taking in a list of image locations and returning a list of completely
 * preprocessed images in the form of a list of Samples. This includes extracting the colors
 * from each of the dye spots in each image
 *
 * @param addresses The Uris identifying the locations of all the images that we want
 * to preprocess
 * @param context The context of the Composable calling this function,
 * obtained from LocalContext.current. This is only used for logging
 *
 * @param log Boolean indicating whether or not the function should save snapshots of each
 * step in the preprocessing algorithm
 * @param normalizationStrategy Identifies how the algorithm should normalize the image data.
 * Options are:
 *  Regression: Rebalances using a linear regression to perform a min-max normalization
 *  on the whole RGB gamut
 *  MinMax: Performs normalization by pulling the maximum and minimum observed colors to the
 *  expected minimums and maximums separately for each channel
 *  Z-Score:
 *
 * @return SampleDataset, a list of all the Samples obtained from the images
 */
suspend fun ingestImages(addresses: List<Uri>, context: Context, log: Boolean = false, normalizationStrategy: String = "Regression"): SampleDataset = coroutineScope {
    val images = addresses.map { uri ->
        async(Dispatchers.Default) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val mat = Mat()
            Utils.bitmapToMat(mutableBitmap, mat)

            val image = Mat()
            val ratio = min(1000.0 / mat.width().toDouble(), 1000.0 / mat.height().toDouble())
            Imgproc.resize(mat, image,  Size(0.0, 0.0), ratio, ratio, Imgproc.INTER_AREA)

            val cropped = findAndWarpCard(image, context, log) ?: image

            preprocessImage(cropped, context, log, normalizationStrategy)
        }
    }.awaitAll()

    SampleDataset(images.toMutableList())
}
