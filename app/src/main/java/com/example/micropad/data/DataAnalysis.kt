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

/**
 * A class for handling sample data captured from an image of a micropad.
 *
 * @property wellIndex The ID value for the dye well.
 * @property assignedLabel The text value used for that dye well.
 * @property closestReferenceName The nearest reference by computed distance.
 * @property distanceScore The computed distance between sample and reference.
 */
data class ClassificationResult(
    val wellIndex: Int,
    val assignedLabel: String,
    val closestReferenceName: String,
    val distanceScore: Double
)

/**
 * A sample to hold data on images.
 * 
 * @property imageData The original image data obtained for the sample.
 * @property balanced The rebalanced image used for dye well extraction.
 * @property initialOrdering An optional bitmap showing the initial dot ordering.
 * @property dots The identified dots, as a list of contour and color pairs.
 */
class Sample(val imageData: Mat?, val balanced: Mat?, val initialOrdering: Bitmap?, val dots: MutableList<Pair<MatOfPoint, Scalar>>) {
    // Grey = 0.299R + 0.587G + 0.114B
    var rgb: List<Scalar> = dots.map { it.second }
    var greyscale: List<Double> =
        rgb.map { 0.299 * it.`val`[0] + 0.587 * it.`val`[1] + 0.114 * it.`val`[2] }
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
     * Normalizes the dot data based on the chosen strategy and mode.
     *
     * @param strategy The user-selected normalization: MinMax, Z-Score, Regression, or none.
     * @param mode Normalize in RGB or grayscale.
     * @return List of DoubleArray, where each DoubleArray is the feature vector for a dot.
     */
    fun getNormalizedData(strategy: String, mode: String): List<DoubleArray> {
        val rawData = if (mode == "RGB") {
            rgb.map { doubleArrayOf(it.`val`[0], it.`val`[1], it.`val`[2]) }
        } else {
            greyscale.map { doubleArrayOf(it) }
        }

        if (strategy == "None" || rawData.isEmpty()) return rawData

        val numDots = rawData.size
        val numFeatures = rawData[0].size
        val normalized = List(numDots) { DoubleArray(numFeatures) }

        for (f in 0 until numFeatures) {
            val vals = rawData.map { it[f] }
            val transformed = when (strategy) {
                "MinMax" -> {
                    val min = vals.minOrNull() ?: 0.0
                    val max = vals.maxOrNull() ?: 255.0
                    if (max == min) vals.map { 0.0 } else vals.map { (it - min) / (max - min) }
                }
                "Z-Score" -> {
                    val mean = vals.average()
                    val std = Math.sqrt(vals.map { (it - mean) * (it - mean) }.average())
                    if (std == 0.0) vals.map { 0.0 } else vals.map { (it - mean) / std }
                }
                "Regression" -> {
                    // Normalize by maximum possible pixel value
                    vals.map { it / 255.0 }
                }
                else -> vals
            }
            for (i in transformed.indices) {
                normalized[i][f] = transformed[i]
            }
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
        if (activeNames.toSet().size != activeNames.size) return false
        return true
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
        if (index in isSelected.indices) {
            isSelected[index] = selected
        }
    }

    /**
     * Swaps the position of two dots in the sample.
     * 
     * @param from The original index of the dot.
     * @param to The new index to swap with.
     */
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
 * A collection of samples, providing methods for bulk operations and dataset-wide analysis.
 * 
 * @property samples The list of samples in this dataset.
 */
class SampleDataset(val samples: MutableList<Sample>) {
    /**
     * Tracks which samples are currently selected.
     */
    var selected = mutableListOf<Boolean>().apply {repeat(samples.size) { add(true) } }

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
        samples[sampleID].reorder(from, to)
    }

    /**
     * Populates the dataset by parsing a CSV file.
     * 
     * @param uri The URI of the CSV file.
     * @param context The Android context for content resolution.
     */
    fun fromCSV(uri: Uri, context: Context) {
        val input = context.contentResolver.openInputStream(uri) ?: return
        samples.clear()

        val lines = input.bufferedReader().readLines()
        if (lines.isEmpty()) return

        val header = lines[0].trimStart('\uFEFF').split(",")
        val metadataColumns = 4
        val colorColumns = header.drop(metadataColumns)

        val dyeNames = colorColumns
            .filter { it.endsWith("_r") }
            .map { it.removeSuffix("_r") }

        val numberOfDots = dyeNames.size

        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            try {
                val tokens = line.split(",")
                if (tokens.size < metadataColumns + numberOfDots * 3) return@forEach

                val referenceName = tokens[1].trim()
                val colorTokens = tokens.drop(metadataColumns)
                val colors = colorTokens
                    .chunked(3)
                    .take(numberOfDots)
                    .map { chunk ->
                        Scalar(
                            chunk[0].trim().toDoubleOrNull() ?: 0.0,
                            chunk[1].trim().toDoubleOrNull() ?: 0.0,
                            chunk[2].trim().toDoubleOrNull() ?: 0.0
                        )
                    }
                    .toMutableList()

                val dots = colors.map { Pair(MatOfPoint(), it) }.toMutableList()
                val sample = Sample(null, null, null, dots)
                sample.names.clear()
                sample.names.addAll(dyeNames)
                sample.rgb = colors
                sample.referenceName = referenceName
                samples.add(sample)
            } catch (e: Exception) {
                return@forEach
            }
        }
    }

    /**
     * Performs classification on a target dataset using a reference dataset.
     * 
     * @param referenceData The dataset containing labeled reference samples.
     * @param newData The dataset to be classified.
     * @param distance The distance metric to use (e.g., "Euclidean", "Manhattan").
     * @param mode The color mode for comparison ("RGB" or grayscale).
     * @param normalizationStrategy The strategy for normalizing feature vectors.
     * @return The classified dataset.
     */
    fun classify(
        referenceData: SampleDataset,
        newData: SampleDataset,
        distance: String = "Euclidean",
        mode: String = "RGB",
        normalizationStrategy: String = "None"
    ): SampleDataset {
        // Pre-calculate normalized data for all reference samples
        val normalizedRefs = referenceData.samples.map { it.getNormalizedData(normalizationStrategy, mode) }

        for (sample in newData.samples) {
            sample.classificationResults.clear()
            val normalizedSampleData = sample.getNormalizedData(normalizationStrategy, mode)

            val newNames = sample.rgb.mapIndexed { dotIdx, _ ->
                if (!sample.isSelected[dotIdx]) return@mapIndexed ""

                val dotFeatures = normalizedSampleData[dotIdx]
                
                var bestScore = Double.MAX_VALUE
                var bestRefName = ""
                var bestRefSample: Sample? = null

                for (r in referenceData.samples.indices) {
                    val refSample = referenceData.samples[r]
                    if (dotIdx >= normalizedRefs[r].size) continue
                    
                    val refFeatures = normalizedRefs[r][dotIdx]

                    val score = when (distance) {
                        "Euclidean" -> {
                            var sum = 0.0
                            for (i in dotFeatures.indices) {
                                val diff = dotFeatures[i] - refFeatures[i]
                                sum += diff * diff
                            }
                            Math.sqrt(sum)
                        }
                        "Manhattan" -> {
                            var sum = 0.0
                            for (i in dotFeatures.indices) {
                                sum += Math.abs(dotFeatures[i] - refFeatures[i])
                            }
                            sum
                        }
                        else -> Double.MAX_VALUE
                    }

                    if (score < bestScore) {
                        bestScore = score
                        bestRefName = refSample.referenceName
                        bestRefSample = refSample
                    }
                }

                val label = bestRefSample?.names?.getOrNull(dotIdx) ?: ""
                sample.classificationResults.add(
                    ClassificationResult(
                        wellIndex = dotIdx,
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

    /**
     * Checks if the dataset contains any samples.
     * 
     * @return True if the samples list is empty.
     */
    fun isEmpty(): Boolean {
        return samples.isEmpty()
    }
}

/**
 * ViewModel for managing the state of datasets across different screens in the app.
 */
class DatasetModel : ViewModel() {
    /**
     * The dataset currently being processed or edited.
     */
    var newDataset by mutableStateOf<SampleDataset?>(null)
        private set

    /**
     * The dataset used as a reference for classification.
     */
    var referenceDataset by mutableStateOf<SampleDataset?>(null)
        private set

    /**
     * Indicates whether a long-running operation (like ingestion) is in progress.
     */
    var isLoading by mutableStateOf(false)
        private set

    /**
     * Ingests a list of image URIs to create a new dataset.
     * 
     * @param uris List of image URIs to process.
     * @param context Android context for image processing.
     * @param selectionStrategy The extraction strategy to use during ingestion.
     */
    fun ingest(uris: List<Uri>, context: Context, selectionStrategy: String) {
        viewModelScope.launch {
            isLoading = true
            newDataset = null
            newDataset = ingestImages(uris, context, log=false, selectionStrategy=selectionStrategy)
            isLoading = false
        }
    }

    /**
     * Checks if all samples in the current dataset have valid labels.
     * 
     * @return True if all active labels are valid.
     */
    fun allSamplesValid(): Boolean {
        return newDataset?.samples?.all { it.validateLabels() } ?: false
    }

    /**
     * The name of the file currently imported (e.g., for CSV export defaults).
     */
    var importedFileName by mutableStateOf("data.csv")
        private set

    /**
     * The URI of the currently imported file.
     */
    var importedFileUri by mutableStateOf<Uri?>(null)
        private set

    /**
     * Updates the tracked import file and extracts its display name.
     * 
     * @param uri The URI of the file.
     * @param context Context for resolving the file name.
     */
    fun setImportedFile(uri: Uri, context: Context) {
        importedFileUri = uri
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

    /**
     * User-selected distance metric for classification.
     */
    var distanceMetric by mutableStateOf("Euclidean")
    
    /**
     * User-selected color mode for classification.
     */
    var colorMode by mutableStateOf("RGB")
    
    /**
     * User-selected normalization strategy for classification.
     */
    var normalizationStrategy by mutableStateOf("None")
    
    /**
     * Tracks whether classification has been executed on the current dataset.
     */
    var classificationRan by mutableStateOf(false)

    /**
     * Executes classification using the current settings and datasets.
     */
    fun runClassification() {
        val ref = referenceDataset
        val new = newDataset
        if (ref == null || new == null) return
        new.classify(ref, new, distanceMetric, colorMode, normalizationStrategy)
        classificationRan = true
    }

    /**
     * Sets the reference dataset by loading it from a CSV file.
     * 
     * @param uri The URI of the CSV file.
     * @param context Context for file access.
     */
    fun setReferenceDataset(uri: Uri, context: Context) {
        val dataset = SampleDataset(mutableListOf())
        dataset.fromCSV(uri, context)
        referenceDataset = dataset
    }

    /**
     * Generates a CSV string representation of the current dataset.
     * 
     * @param includeHeader Whether to include the column headers in the output.
     * @return A formatted CSV string.
     */
    fun toCsvString(includeHeader: Boolean = true): String {
        if (newDataset?.isEmpty() ?: true) return ""  // No dataset or empty

        val header = "sample_id,reference_name,distance_calculation,similarity_score,No Dye_r,No Dye_g,No Dye_b,DMGO_r,DMGO_g,DMGO_b,XO_r,XO_g,XO_b,Phen_r,Phen_g,Phen_b,DCP_r,DCP_g,DCP_b,PAR_r,PAR_g,PAR_b"

        val rows = newDataset!!.samples.joinToString("\n") { sample ->
            // Reconstructing the row according to the header:
            // sample_id,reference_name,distance_calculation,similarity_score,colors...
            val rowItems = mutableListOf<String>()
            rowItems.add(sample.referenceName ?: "")
            rowItems.add("") // distance_calculation
            rowItems.add("") // similarity_score
            
            sample.rgb.forEach { scalar ->
                rowItems.add(scalar.`val`[0].toInt().toString())
                rowItems.add(scalar.`val`[1].toInt().toString())
                rowItems.add(scalar.`val`[2].toInt().toString())
            }
            
            rowItems.joinToString(",")
        }
        return if (includeHeader) "$header\n$rows" else rows
    }
}
