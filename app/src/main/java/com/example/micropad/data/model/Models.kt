/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.micropad.data.model

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.micropad.data.util.AppErrorLogger
import com.example.micropad.data.analysis.drawOrdering
import com.example.micropad.data.analysis.extractManualDotAtPoint
import com.example.micropad.data.model.greyscale
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import java.util.Collections
import kotlin.math.sqrt


/**
 * Data class to hold image and its semantic label.
 */
data class LabeledImage(
    val uri: Uri,
    val label: String
)

/**
 * A class for handling the results of a whole-card classification.
 *
 * @property closestReferenceName The name of the reference that best matched the sample.
 * @property totalDistance The overall similarity distance (Euclidean or Manhattan) for the entire card.
 * @property wellNames The names of the dye wells included in the classification.
 * @property sampleColors The extracted RGB colors from the sample's dye wells.
 * @property referenceColors The RGB colors from the corresponding reference's dye wells.
 * @property wellDistances The individual distances computed per dye well.
 * ADDITION:
 * @property wellClosestReferences The name of the reference that best matched each individual well (optional).
 */
data class ClassificationResult(
    val closestReferenceName: String,
    val totalDistance: Double,
    val wellNames: List<String>,
    val sampleColors: List<Scalar>,
    val referenceColors: List<Scalar>,
    val wellDistances: List<Double>,
    //ADDITION
    val wellClosestReferences: List<String> = emptyList()
)

/**
 * Represents an entry in the analysis history.
 */
data class AnalysisHistoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val fileName: String,
    val summary: String,
    val csvData: String,
    val distanceMetric: String,
    val colorMode: String,
    val normalizationStrategy: String
)


/**
 * A sample to hold data on images.
 *
 * @property imageData The original image data obtained for the sample.
 * @property balanced The rebalanced image used for dye well extraction.
 * @property initialOrdering An optional bitmap showing the initial dot ordering.
 * @property dots The identified dots, as a list of contour and color pairs.
 */
class Sample(
    val imageData: Mat?,
    val balanced: Mat?,
    val initialOrdering: Bitmap?,
    val dots: MutableList<Pair<MatOfPoint, Scalar>>,
    val squares: MutableList<Scalar> = mutableListOf<Scalar>(),
    var type: String = "Sample", // "Reference" or "Sample"
    val isImage: Boolean = true
) {
    var rgb: MutableList<Scalar> = dots.map { it.second }.toMutableList()
    var greyscale: MutableList<Double> =
        rgb.map { greyscale(it.`val`[0], it.`val`[1], it.`val`[2]) }.toMutableList()
    var names = mutableStateListOf<String>().apply {
        repeat(rgb.size) { add("") }
    }
    var isSelected = mutableStateListOf<Boolean>().apply {
        repeat(rgb.size) { add(true) }
    }
    var referenceName: String = ""
    var classificationResults = mutableStateListOf<ClassificationResult>()

    var ordering by mutableStateOf(initialOrdering)
        private set

    /**
     * Appends a manually selected ROI to this sample.
     */
    fun addManualDot(center: Point, radius: Double, selectionStrategy: String) {
        val source = balanced ?: imageData ?: return
        val (contour, color) = extractManualDotAtPoint(source, center, radius, selectionStrategy)
        dots.add(Pair(contour, color))
        rgb.add(color)
        greyscale.add(greyscale(color.`val`[0], color.`val`[1], color.`val`[2]))
        names.add("")
        isSelected.add(true)
        if (imageData != null) ordering = drawOrdering(imageData, dots, selectionStates = isSelected)
    }

    /**
     * Normalizes the dot data based on the chosen strategy and mode.
     *
     * @param strategy The user-selected normalization: MinMax, Z-Score, or none.
     * @param mode Normalize in RGB or grayscale.
     * @return List of DoubleArray, where each DoubleArray is the feature vector for a dot.
     */
    fun getNormalizedData(strategy: String, mode: String, selection: String): List<DoubleArray> {
        val greySquares = squares.map{ greyscale(it.`val`[0], it.`val`[1], it.`val`[2]) }

        val rawData = if (mode == "RGB") {
            // Append calibration square data to rgb data before normalizing
            val data = if (selection == "Include Squares") rgb + squares else rgb
            data.map { doubleArrayOf(it.`val`[0], it.`val`[1], it.`val`[2]) }
        } else {
            // Create greyscale values from calibration squares and append
            val data = if (selection == "Include Squares") greyscale + greySquares else greyscale
            data.map { doubleArrayOf(it) }
        }

        if (strategy == "None" || rawData.isEmpty()) return rawData

        val numDots = rawData.size
        val numFeatures = rawData[0].size
        var normalized = List(numDots) { DoubleArray(numFeatures) }

        for (f in 0 until numFeatures) {
            val vals = rawData.map { it[f] }
            val transformed = when (strategy) {
                "MinMax" -> {
                    val minVal = vals.minOrNull() ?: 0.0
                    val maxVal = vals.minOrNull() ?: 255.0
                    if (maxVal == minVal) vals.map { 0.0 } else vals.map { (it - minVal) / (maxVal - minVal) }
                }

                "Z-Score" -> {
                    val mean = vals.average()
                    val std = sqrt(vals.map { (it - mean) * (it - mean) }.average())
                    if (std == 0.0) vals.map { 0.0 } else vals.map { (it - mean) / std }
                }

                else -> vals
            }
            for (i in transformed.indices) {
                normalized[i][f] = transformed[i]
            }
        }

        // Remove calibration square data from final color data
        if (selection == "Include Squares") {
            normalized = normalized.subList(0, normalized.size - squares.size)
        }

        return normalized
    }

    /**
     * Ensure selected dye well labels are unique and filled.
     *
     * @return True if labels are valid, false otherwise.
     */
    fun validateLabels(): Boolean {
        val activeNames = names.filterIndexed { index, _ -> isSelected[index] }
        if (activeNames.any { it.isBlank() }) return false
        return activeNames.toSet().size == activeNames.size
    }

    /**
     * Reassigns a label to a specific well index.
     *
     * @param index The index of the well.
     * @param newLabel The new label to assign.
     * @return True if reassignment was successful, false if label already exists elsewhere or index is invalid.
     */
    fun reassignLabel(index: Int, newLabel: String): Boolean {
        if (index !in names.indices) return false
        if (names.contains(newLabel) && names[index] != newLabel) return false
        names[index] = newLabel
        return true
    }

    /**
     * Toggles whether a well is selected for analysis.
     *
     * @param index The index of the well.
     * @param selected Whether the well should be selected.
     */
    fun toggleSelection(index: Int, selected: Boolean) {
        if (index in isSelected.indices) isSelected[index] = selected
    }

    /**
     * Swaps the position of two dots in the sample.
     *
     * @param from The original index of the dot.
     * @param to The new index to swap with.
     */
    fun reorder(from: Int, to: Int) {
        if (from !in dots.indices || to !in dots.indices) return
        try {
            Collections.swap(dots, from, to)
            Collections.swap(names, from, to)
            Collections.swap(rgb, from, to)
            Collections.swap(greyscale, from, to)
            Collections.swap(isSelected, from, to)
            if (imageData != null) ordering = drawOrdering(imageData, dots)
        } catch (e: Exception) {
            // swap failed — state unchanged, logged silently
        }
    }
}

/**
 * A collection of samples, providing methods for bulk operations and dataset-wide analysis.
 *
 * @property samples The list of samples in this dataset.
 */
class SampleDataset(val samples: MutableList<Sample>) {
    /**
     * Tracks which samples are currently selected.
     */
    var selected = mutableListOf<Boolean>().apply {repeat(samples.size) { add(true) } }

    fun isEmpty() = samples.isEmpty()

    /**
     * Updates the name of a well across all samples in the dataset.
     *
     * @param index The index of the well.
     * @param name The new name for the well.
     * @return True if the rename was successful for all samples.
     */
    fun nameWell(index: Int, name: String): Boolean {
        var worked = true
        for (sample in samples) {
            worked = worked && sample.reassignLabel(index, name)
        }
        return worked
    }

    /**
     * Toggles the selection status of a specific well index across all samples.
     *
     * @param index The index of the well.
     * @param selected The new selection status.
     */
    fun toggleWell(index: Int, selected: Boolean) {
        for (sample in samples) {
            sample.toggleSelection(index, selected)
        }
    }

    /**
     * Triggers a reorder operation on a specific sample.
     *
     * @param sampleID The index of the sample in the dataset.
     * @param from The starting index of the dot.
     * @param to The target index for the swap.
     */
    fun reorderSample(sampleID: Int, from: Int, to: Int) {
        if (sampleID in samples.indices) {
            samples[sampleID].reorder(from, to)
        }
    }

    /**
     * Populates the dataset by parsing a CSV file.
     *
     * @param uri The URI of the CSV file.
     * @param context The Android context for content resolution.
     */
    fun fromCSV(uri: Uri, context: Context) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: run {
                AppErrorLogger.logError(context, "CSV", "fromCSV: could not open input stream for $uri")
                return
            }
            samples.clear()
            val lines = input.bufferedReader().readLines()
            if (lines.isEmpty()) return

            val firstLine = lines[0].trimStart('\uFEFF')
            val header = firstLine.split(",")

            val dyeNames = header.filter { it.endsWith("_r") && !it.startsWith("cal") }
                .map { it.removeSuffix("_r") }

            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                try {
                    val tokens = line.split(",")
                    val rowMap = header.indices.associate { i -> header[i] to tokens.getOrNull(i) }

                    val refName = rowMap["reference_name"]?.trim() ?: ""

                    val squares = mutableListOf<Scalar>()
                    for (i in 0..3) {
                        val r = rowMap["cal${i}_r"]?.toDoubleOrNull() ?: 0.0
                        val g = rowMap["cal${i}_g"]?.toDoubleOrNull() ?: 0.0
                        val b = rowMap["cal${i}_b"]?.toDoubleOrNull() ?: 0.0
                        squares.add(Scalar(r, g, b))
                    }

                    val dots = mutableListOf<Pair<MatOfPoint, Scalar>>()
                    val names = mutableListOf<String>()

                    for (name in dyeNames) {
                        val rStr = rowMap["${name}_r"]
                        val gStr = rowMap["${name}_g"]
                        val bStr = rowMap["${name}_b"]

                        if (!rStr.isNullOrBlank() && !gStr.isNullOrBlank() && !bStr.isNullOrBlank()) {
                            dots.add(Pair(MatOfPoint(), Scalar(rStr.toDoubleOrNull() ?: 0.0,
                                gStr.toDoubleOrNull() ?: 0.0,
                                bStr.toDoubleOrNull() ?: 0.0)))
                            names.add(name)
                        }
                    }

                    val sample = Sample(null, null, null, dots, type = "Reference", squares = squares, isImage = false)
                    sample.names.clear(); sample.names.addAll(names)
                    sample.referenceName = refName
                    samples.add(sample)
                    Log.d("CSV", "Sample added: ${sample.rgb.size} dots identified")

                } catch (e: Exception) {
                    AppErrorLogger.logError(context, "CSV", "fromCSV: failed to parse row: $line", e)
                }

                Log.d("CSV", "Samples imported: ${samples.size}")
            }
        } catch (e: Exception) {
            AppErrorLogger.logError(context, "CSV", "fromCSV: unexpected failure", e)
            samples.clear()
        }
    }

    /**
     * Performs classification on a target dataset using a reference dataset.
     * This method is deprecated in favor of whole-card classification.
     */
    fun classify(
        referenceData: SampleDataset,
        newData: SampleDataset,
        distance: String = "Euclidean",
        mode: String = "RGB",
        normalizationStrategy: String = "None"
    ) {
        // No longer actively used in the main flow, which prefers runWholeCardClassification.
    }
}

fun greyscale(r: Double, g: Double, b: Double): Double {
    return 0.299 * r + 0.587 * g + 0.114 * b
}
