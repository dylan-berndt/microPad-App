package com.example.micropad.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import java.util.Collections
import kotlin.math.abs
import android.util.Log

/**
 * A class for handling sample data captured from an image of a micropad.
 *
 * @param imageData The original image data obtained for the sample
 * @param balanced The rebalanced image that is used for dye well extraction
 * @param ordering An image displaying the selected ordering for each of the dots, this is
 * automatically generated in the ingestImages function but can be rearranged on the dye well
 * naming screen
 * @param dots The identified dots in the balanced image, provided as a list of pairs containing
 * the contour of the dot and the extracted color value from the dot
 * @property rgb The color values extracted from each dot, in the same order as they are presented in
 * the image
 * @property greyscale The greyscale version of the color data
 * @property names The chosen names for each of the dye wells in an image
 * @property sampleId The sample number in the CSV file
 * @property referenceId The number the sample is matched with, if any
 */

data class ClassificationResult(
    val wellIndex: Int,
    val assignedLabel: String,
    val closestReferenceName: String,
    val distanceScore: Double
)

class Sample(val imageData: Mat?, val balanced: Mat?, val initialOrdering: Bitmap?, val dots: MutableList<Pair<MatOfPoint, Scalar>>) {
    // Grey = 0.299R + 0.587G + 0.114B
    var rgb: List<Scalar> = dots.map { it.second }
    var greyscale: List<Double> =
        rgb.map { 0.299 * it.`val`[0] + 0.587 * it.`val`[1] + 0.114 * it.`val`[2] }
    var names = mutableStateListOf<String>().apply {
        repeat(rgb.size) { add("") }
    }
    var sampleId: String? = ""
    var referenceName: String? = ""
    var isSelected = mutableStateListOf<Boolean>().apply {
        repeat(rgb.size) { add(true) }
    }
    var referenceName: String = ""
    var classificationResults = mutableStateListOf<ClassificationResult>()

    var ordering by mutableStateOf(initialOrdering)
        private set

    fun validateLabels(): Boolean {
        val activeNames = names.filterIndexed { index, _ -> isSelected[index] }
        if (activeNames.any { it.isBlank() }) return false
        if (activeNames.toSet().size != activeNames.size) return false
        return true
    }

    fun reassignLabel(index: Int, newLabel: String): Boolean {
        if (index !in names.indices) return false
        if (names.contains(newLabel) && names[index] != newLabel) return false

        names[index] = newLabel
        return true
    }

    fun toggleSelection(index: Int, selected: Boolean) {
        if (index in isSelected.indices) {
            isSelected[index] = selected
        }
    }

    // Function to reorder the chosen dots in an image
    // The user should be able to reorder the dots in an image in the case they
    // are ordered incorrectly
    fun reorder(from: Int, to: Int) {
        Collections.swap(dots, from, to)
        Collections.swap(names, from, to)
        Collections.swap(rgb, from, to)
        Collections.swap(greyscale, from, to)
        Collections.swap(isSelected, from, to)

        if (imageData != null) {
            ordering = drawOrdering(imageData, dots)
        }
    }
}


/**
 * A class for abstracting useful functionality when comparing two datasets
 *
 * @property samples A list of samples compiled in one place, to be used for classification
 * @property selected A list of booleans indicating which of the ROIs are to be used for classification
 */
class SampleDataset(val samples: MutableList<Sample>) {
    var selected = mutableListOf<Boolean>().apply {repeat(samples.size) { add(true) } }

    fun nameWell(index: Int, name: String): Boolean {
        var worked = true
        for (sample in samples) {
            worked = worked && sample.reassignLabel(index, name)
        }

        return worked
    }

    fun toggleWell(index: Int, selected: Boolean) {
        for (sample in samples) {
            sample.toggleSelection(index, selected)
        }
    }

    fun reorderSample(sampleID: Int, from: Int, to: Int) {
        samples[sampleID].reorder(from, to)
    }

    fun fromCSV(uri: Uri, context: Context) {
        val input = context.contentResolver.openInputStream(uri) ?: return
        samples.clear()

        val lines = input.bufferedReader().readLines()
        if (lines.isEmpty()) return

        // Parse header to extract dye names
        // Header format: sample_id, reference_name, distance_calculation,
        // similarity_score, No Dye_r, No Dye_g, No Dye_b, DMGO_r ...
        val header = lines[0].trimStart('\uFEFF').split(",") // trimStart removes BOM character
        val metadataColumns = 4 // sample_id, reference_name, distance_calculation, similarity_score
        val colorColumns = header.drop(metadataColumns) // everything after the 4 metadata cols

        // Extract unique dye names from headers like "No Dye_r", "No Dye_g", "No Dye_b"
        val dyeNames = colorColumns
            .filter { it.endsWith("_r") }
            .map { it.removeSuffix("_r") }

        val numberOfDots = dyeNames.size // should be 6

        Log.d("CSV", "Dye names found: $dyeNames")
        Log.d("CSV", "Number of dots: $numberOfDots")

        // Parse each data row
        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            try {
                val tokens = line.split(",")

                Log.d("CSV", "Parsing row with ${tokens.size} tokens: $line")

                if (tokens.size < metadataColumns + numberOfDots * 3) {
                    Log.d("CSV", "Skipping row — not enough columns: ${tokens.size}")
                    return@forEach
                }

                val referenceName = tokens[1].trim() // e.g. "H2O", "Ni(II)"

                // Extract RGB values starting after the 4 metadata columns
                val colorTokens = tokens.drop(metadataColumns)
                val colors = colorTokens
                    .chunked(3)
                    .take(numberOfDots)
                    .map { chunk ->
                        Scalar(
                            chunk[0].trim().toDoubleOrNull() ?: 0.0, // R
                            chunk[1].trim().toDoubleOrNull() ?: 0.0, // G
                            chunk[2].trim().toDoubleOrNull() ?: 0.0  // B
                        )
                    }
                    .toMutableList()

                val dots = colors.map { Pair(MatOfPoint(), it) }.toMutableList()

                val sample = Sample(null, null, null, dots)
                sample.names.clear()
                sample.names.addAll(dyeNames) // assign dye names to wells
                sample.rgb = colors
                sample.referenceName = referenceName

                Log.d("CSV", "Added sample: $referenceName with ${colors.size} dots")
                samples.add(sample)

            } catch (e: Exception) {
                Log.d("CSV", "Exception parsing row: ${e.message}")
                return@forEach
            }
        }

        Log.d("CSV", "Total samples loaded: ${samples.size}")
    }

    fun classify(
        referenceData: SampleDataset,
        newData: SampleDataset,
        distance: String = "Euclidean",
        mode: String = "RGB"
    ): SampleDataset {
        for (sample in newData.samples) {
            sample.classificationResults.clear()

            val newNames = sample.rgb.mapIndexed { index, dotColor ->
                if (!sample.isSelected[index]) return@mapIndexed ""

                var bestScore = Double.MAX_VALUE
                var bestRefName = ""

                val closestRef = referenceData.samples.minByOrNull { refSample ->
                    if (index >= refSample.rgb.size) return@minByOrNull Double.MAX_VALUE
                    val refColor = refSample.rgb[index]

                    val score = when (distance) {
                        "Euclidean" -> {
                            if (mode == "RGB") {
                                val dr = dotColor.`val`[0] - refColor.`val`[0]
                                val dg = dotColor.`val`[1] - refColor.`val`[1]
                                val db = dotColor.`val`[2] - refColor.`val`[2]
                                Math.sqrt(dr * dr + dg * dg + db * db)
                            } else {
                                val grayDot = 0.299 * dotColor.`val`[0] + 0.587 * dotColor.`val`[1] + 0.114 * dotColor.`val`[2]
                                val grayRef = 0.299 * refColor.`val`[0] + 0.587 * refColor.`val`[1] + 0.114 * refColor.`val`[2]
                                Math.abs(grayDot - grayRef)
                            }
                        }
                        "Manhattan" -> {
                            if (mode == "RGB") {
                                val dr = abs(dotColor.`val`[0] - refColor.`val`[0])
                                val dg = abs(dotColor.`val`[1] - refColor.`val`[1])
                                val db = abs(dotColor.`val`[2] - refColor.`val`[2])
                                dr + dg + db
                            } else {
                                val grayDot = 0.299 * dotColor.`val`[0] + 0.587 * dotColor.`val`[1] + 0.114 * dotColor.`val`[2]
                                val grayRef = 0.299 * refColor.`val`[0] + 0.587 * refColor.`val`[1] + 0.114 * refColor.`val`[2]
                                abs(grayDot - grayRef)
                            }
                        }
                        else -> Double.MAX_VALUE
                    }

                    if (score < bestScore) {
                        bestScore = score
                        bestRefName = refSample.referenceName
                    }
                    score
                }

                val label = closestRef?.names?.getOrNull(index) ?: ""
                sample.classificationResults.add(
                    ClassificationResult(
                        wellIndex = index,
                        assignedLabel = label,
                        closestReferenceName = bestRefName,
                        distanceScore = if (bestScore == Double.MAX_VALUE) -1.0 else bestScore
                    )
                )
                label
            }

            sample.names.clear()
            sample.names.addAll(newNames)
        }
        return newData
    }

    fun isEmpty(): Boolean {
        return samples.isEmpty()
    }
}


// ViewModel to handle image datasets and pass them around the different screens
class DatasetModel : ViewModel() {
    // Current dataset property
    var newDataset by mutableStateOf<SampleDataset?>(null)
        private set

    // Reference dataset property
    var referenceDataset by mutableStateOf<SampleDataset?>(null)
        private set

    // Current loading state
    var isLoading by mutableStateOf(false)
        private set

    // Your ingest function
    fun ingest(uris: List<Uri>, context: Context, selectionStrategy: String) {
        viewModelScope.launch {
            isLoading = true
            newDataset = null
            newDataset = ingestImages(uris, context, log=false, selectionStrategy=selectionStrategy)
            isLoading = false
        }
    }

    fun allSamplesValid(): Boolean {
        // Checks all samples in the dataset
        return newDataset?.samples?.all { it.validateLabels() } ?: false
    }

    var importedFileName by mutableStateOf("data.csv")
        private set

    /**
     * Track imported CSV file for use in exporting to it.
     */
    fun setImportedFile(uri: Uri, context: Context) {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    importedFileName = it.getString(nameIndex)
                }
            }
        }
    }

    var distanceMetric by mutableStateOf("Euclidean")
    var colorMode by mutableStateOf("RGB")
    var classificationRan by mutableStateOf(false)

    fun runClassification() {
        val ref = referenceDataset
        val new = newDataset
        if (ref == null || new == null) return
        new.classify(ref, new, distanceMetric, colorMode)
        classificationRan = true
    }

    fun setReferenceDataset(uri: Uri, context: Context) {
        val dataset = SampleDataset(mutableListOf())
        dataset.fromCSV(uri, context)
        referenceDataset = dataset
    }

    // This is never updated with the samples from classify
    val samples = mutableStateListOf<Sample>()

    /**
     * Provide a proper CSV string to CsvExportButton functionality 
     * based on SampleDataset.classify().
     */
    fun toCsvString(header: String): String {
        if (newDataset?.isEmpty() ?: true) return ""  // No dataset or empty

        val rows = newDataset!!.samples.joinToString("\n") { sample ->
            // Only include selected wells in the CSV output?
            // Or include them all but with empty names?
            // Usually, users expect only selected ones if they "choose which they want".

            val selectedIndices = sample.isSelected.indices.filter { sample.isSelected[it] }

            val rgbParts = selectedIndices.flatMap { index ->
                val scalar = sample.rgb[index]
                listOf(scalar.`val`[0], scalar.`val`[1], scalar.`val`[2])
            }.map { it.toString() }

            val nameParts = selectedIndices.map { index ->
                val name = sample.names[index]
                val escaped = name.replace("\"", "\"\"")
                if (name.contains(',') || name.contains('\n') || name.contains('"')) {
                    "\"$escaped\""
                } else {
                    name
                }
            }
            (rgbParts + nameParts).joinToString(",")
        }
        return "$header\n$rows"
    }
}
