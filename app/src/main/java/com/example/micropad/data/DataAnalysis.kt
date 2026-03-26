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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import android.util.Log

/**
 * A class for handling sample data captured from an image of a micropad.
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
    var isSelected = mutableStateListOf<Boolean>().apply {
        repeat(rgb.size) { add(true) }
    }
    var referenceName: String = ""
    var classificationResults = mutableStateListOf<ClassificationResult>()

    var ordering by mutableStateOf(initialOrdering)
        private set

    /**
     * Normalizes the dot data based on the chosen strategy and mode.
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

    fun isEmpty(): Boolean {
        return samples.isEmpty()
    }
}

class DatasetModel : ViewModel() {
    var newDataset by mutableStateOf<SampleDataset?>(null)
        private set

    var referenceDataset by mutableStateOf<SampleDataset?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    fun ingest(uris: List<Uri>, context: Context, selectionStrategy: String) {
        viewModelScope.launch {
            isLoading = true
            newDataset = null
            newDataset = ingestImages(uris, context, log=false, selectionStrategy=selectionStrategy)
            isLoading = false
        }
    }

    fun allSamplesValid(): Boolean {
        return newDataset?.samples?.all { it.validateLabels() } ?: false
    }

    var importedFileName by mutableStateOf("data.csv")
        private set

    var importedFileUri by mutableStateOf<Uri?>(null)
        private set

    /**
     * Track imported CSV file for use in exporting to it.
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

    var distanceMetric by mutableStateOf("Euclidean")
    var colorMode by mutableStateOf("RGB")
    var normalizationStrategy by mutableStateOf("None")
    var classificationRan by mutableStateOf(false)

    fun runClassification() {
        val ref = referenceDataset
        val new = newDataset
        if (ref == null || new == null) return
        new.classify(ref, new, distanceMetric, colorMode, normalizationStrategy)
        classificationRan = true
    }

    fun setReferenceDataset(uri: Uri, context: Context) {
        val dataset = SampleDataset(mutableListOf())
        dataset.fromCSV(uri, context)
        referenceDataset = dataset
    }

    val samples = mutableStateListOf<Sample>()

    /**
     * Provide a proper CSV string to CsvExportButton functionality 
     * based on SampleDataset.classify().
     */
    fun toCsvString(includeHeader: Boolean = true): String {
        if (newDataset?.isEmpty() ?: true) return ""  // No dataset or empty

        val header = "sample_id,reference_name,distance_calculation,similarity_score,No Dye_r,No Dye_g,No Dye_b,DMGO_r,DMGO_g,DMGO_b,XO_r,XO_g,XO_b,Phen_r,Phen_g,Phen_b,DCP_r,DCP_g,DCP_b,PAR_r,PAR_g,PAR_b"

        val rows = newDataset!!.samples.joinToString("\n") { sample ->
            // Current implementation assumes fixed number of color columns for the entire row
            // matching the header structure.
            
            // Reconstructing the row according to the header:
            // sample_id,reference_name,distance_calculation,similarity_score,colors...
            val rowItems = mutableListOf<String>()
            // TODO: Add sampleId if we actually need it
//            rowItems.add(sample.sampleId ?: "")
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
