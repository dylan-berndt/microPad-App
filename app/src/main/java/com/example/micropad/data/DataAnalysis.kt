package com.example.micropad.data

import java.util.Collections
import android.graphics.Bitmap
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import android.util.Log

/**
 * Data class to hold image and its semantic label.
 */
data class LabeledImage(
    val uri: Uri,
    val label: String
)

/**
 * Data structures for classification results and image samples.
 */
data class ClassificationResult(
    val wellIndex: Int,
    val assignedLabel: String,
    val closestReferenceName: String,
    val distanceScore: Double
)

class Sample(
    val imageData: Mat?, 
    val balanced: Mat?, 
    val initialOrdering: Bitmap?, 
    val dots: MutableList<Pair<MatOfPoint, Scalar>>,
    var type: String = "Sample" // "Reference" or "Sample"
) {
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
                    val minVal = vals.minOrNull() ?: 0.0
                    val maxVal = vals.maxOrNull() ?: 255.0
                    if (maxVal == minVal) vals.map { 0.0 } else vals.map { (it - minVal) / (maxVal - minVal) }
                }
                "Z-Score" -> {
                    val mean = vals.average()
                    val std = sqrt(vals.map { (it - mean) * (it - mean) }.average())
                    if (std == 0.0) vals.map { 0.0 } else vals.map { (it - mean) / std }
                }
                "Regression" -> {
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

    fun validateLabels(): Boolean {
        val activeNames = names.filterIndexed { index, _ -> isSelected[index] }
        if (activeNames.any { it.isBlank() }) return false
        return activeNames.toSet().size == activeNames.size
    }

    fun reassignLabel(index: Int, newLabel: String): Boolean {
        if (index !in names.indices) return false
        if (names.contains(newLabel) && names[index] != newLabel) return false
        names[index] = newLabel
        return true
    }

    fun toggleSelection(index: Int, selected: Boolean) {
        if (index in isSelected.indices) isSelected[index] = selected
    }

    fun reorder(from: Int, to: Int) {
        Collections.swap(dots, from, to)
        Collections.swap(names, from, to)
        Collections.swap(rgb, from, to)
        Collections.swap(greyscale, from, to)
        Collections.swap(isSelected, from, to)
        if (imageData != null) ordering = drawOrdering(imageData, dots)
    }
}

class SampleDataset(val samples: MutableList<Sample>) {
    var selected = mutableListOf<Boolean>().apply { repeat(samples.size) { add(true) } }

    fun isEmpty() = samples.isEmpty()

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
        if (sampleID in samples.indices) {
            samples[sampleID].reorder(from, to)
        }
    }

    fun fromCSV(uri: Uri, context: Context) {
        val input = context.contentResolver.openInputStream(uri) ?: return
        samples.clear()
        val lines = input.bufferedReader().readLines()
        if (lines.isEmpty()) return

        val header = lines[0].trimStart('\uFEFF').split(",")
        val metadataColumns = 4
        val colorColumns = header.drop(metadataColumns)
        val dyeNames = colorColumns.filter { it.endsWith("_r") }.map { it.removeSuffix("_r") }
        val numberOfDots = dyeNames.size

        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            try {
                val tokens = line.split(",")
                if (tokens.size < metadataColumns + numberOfDots * 3) return@forEach
                val refName = tokens[1].trim()
                val colors = tokens.drop(metadataColumns).chunked(3).take(numberOfDots).map { chunk ->
                    Scalar(chunk[0].trim().toDoubleOrNull() ?: 0.0, chunk[1].trim().toDoubleOrNull() ?: 0.0, chunk[2].trim().toDoubleOrNull() ?: 0.0)
                }
                val dots = colors.map { Pair(MatOfPoint(), it) }.toMutableList()
                val sample = Sample(null, null, null, dots, type = "Reference")
                sample.names.clear(); sample.names.addAll(dyeNames)
                sample.rgb = colors; sample.referenceName = refName
                samples.add(sample)
            } catch (e: Exception) { return@forEach }
        }
    }

    fun classify(
        referenceData: SampleDataset,
        newData: SampleDataset,
        distance: String = "Euclidean",
        mode: String = "RGB",
        normalizationStrategy: String = "None"
    ) {
        val normalizedRefs = referenceData.samples.map { it.getNormalizedData(normalizationStrategy, mode) }
        for (sample in newData.samples) {
            sample.classificationResults.clear()
            val normalizedSampleData = sample.getNormalizedData(normalizationStrategy, mode)
            val newNames = sample.rgb.mapIndexed { dotIdx, _ ->
                if (!sample.isSelected[dotIdx]) return@mapIndexed ""
                val dotFeatures = normalizedSampleData[dotIdx]
                var bestScore = Double.MAX_VALUE; var bestRefName = ""; var bestRefSample: Sample? = null

                for (r in referenceData.samples.indices) {
                    val refSample = referenceData.samples[r]
                    if (dotIdx >= normalizedRefs[r].size) continue
                    val refFeatures = normalizedRefs[r][dotIdx]
                    val score = when (distance) {
                        "Euclidean" -> {
                            var sum = 0.0
                            for (i in dotFeatures.indices) { val diff = dotFeatures[i] - refFeatures[i]; sum += diff * diff }
                            sqrt(sum)
                        }
                        "Manhattan" -> {
                            var sum = 0.0
                            for (i in dotFeatures.indices) { sum += abs(dotFeatures[i] - refFeatures[i]) }
                            sum
                        }
                        else -> Double.MAX_VALUE
                    }
                    if (score < bestScore) { bestScore = score; bestRefName = refSample.referenceName; bestRefSample = refSample }
                }
                val label = bestRefSample?.names?.getOrNull(dotIdx) ?: ""
                sample.classificationResults.add(ClassificationResult(dotIdx, label, bestRefName, if (bestScore == Double.MAX_VALUE) -1.0 else bestScore))
                label
            }
            sample.names.clear(); sample.names.addAll(newNames)
        }
    }
}

class DatasetModel : ViewModel() {
    var labelingTargetIsReference = true

    // Persistent Session State
    val pendingReferences = mutableStateListOf<LabeledImage>()
    val pendingSamples = mutableStateListOf<LabeledImage>()

    // Temporary storage for labeling flow
    var temporaryUris by mutableStateOf<List<Uri>>(emptyList())

    var referenceDataset by mutableStateOf<SampleDataset?>(null)
    var newDataset by mutableStateOf<SampleDataset?>(null)
    
    var isLoading by mutableStateOf(false)
        private set

    var importedFileName by mutableStateOf("data.csv")
    var importedFileUri by mutableStateOf<Uri?>(null)

    var distanceMetric by mutableStateOf("Euclidean")
    var colorMode by mutableStateOf("RGB")
    var normalizationStrategy by mutableStateOf("None")

    /**
     * Ingests all pending images into structured datasets.
     */
    fun ingestAllPending(context: Context, onComplete: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            
            // 1. Process References
            if (pendingReferences.isNotEmpty()) {
                val uris = pendingReferences.map { it.uri }
                val dataset = ingestImages(uris, context, log = false)
                dataset.samples.forEachIndexed { i, sample -> 
                    sample.type = "Reference"
                    sample.referenceName = pendingReferences.getOrNull(i)?.label ?: ""
                }
                referenceDataset = dataset
            }

            // 2. Process Samples
            if (pendingSamples.isNotEmpty()) {
                val uris = pendingSamples.map { it.uri }
                val dataset = ingestImages(uris, context, log = false)
                dataset.samples.forEachIndexed { i, sample ->
                    sample.type = "Sample"
                    sample.referenceName = pendingSamples.getOrNull(i)?.label ?: ""
                }
                newDataset = dataset
            }

            isLoading = false
            onComplete()
        }
    }

    fun allSamplesValid(): Boolean {
        val refValid = referenceDataset?.samples?.all { it.validateLabels() } ?: true
        val sampleValid = newDataset?.samples?.all { it.validateLabels() } ?: true
        return refValid && sampleValid
    }

    fun setImportedFile(uri: Uri, context: Context) {
        importedFileUri = uri
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) importedFileName = it.getString(nameIndex)
            }
        }
    }

    fun setReferenceDataset(uri: Uri, context: Context) {
        val dataset = SampleDataset(mutableListOf())
        dataset.fromCSV(uri, context)
        referenceDataset = dataset
    }

    fun runClassification() {
        val ref = referenceDataset; val new = newDataset
        if (ref == null || new == null) return
        new.classify(ref, new, distanceMetric, colorMode, normalizationStrategy)
    }

    fun toCsvString(header: String = "", includeHeader: Boolean = true): String {
        val dataset = newDataset ?: return ""
        val rows = dataset.samples.joinToString("\n") { sample ->
            val selectedIndices = sample.isSelected.indices.filter { sample.isSelected[it] }
            val rgbParts = selectedIndices.flatMap { index ->
                val scalar = sample.rgb[index]
                listOf(scalar.`val`[0], scalar.`val`[1], scalar.`val`[2])
            }.map { it.toString() }
            val nameParts = selectedIndices.map { index ->
                val name = sample.names[index]
                "\"${name.replace("\"", "\"\"")}\""
            }
            (rgbParts + nameParts).joinToString(",")
        }
        return if (includeHeader && header.isNotEmpty()) "$header\n$rows" else rows
    }
}
