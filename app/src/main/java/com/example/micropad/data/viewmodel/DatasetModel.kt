package com.example.micropad.data.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.micropad.data.util.AppErrorLogger
import com.example.micropad.data.analysis.ingestImages
import com.example.micropad.data.model.*
import com.example.micropad.ui.features.simulation.runNavigationSimulation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.core.Scalar
import kotlin.collections.get
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ViewModel for managing the state of datasets across different screens in the app.
 */
class DatasetModel : ViewModel() {

    var simulationRunning by mutableStateOf(false)
    var labelingTargetIsReference by mutableStateOf(true)

    // Persistent Session State
    val pendingReferences = mutableStateListOf<LabeledImage>()
    val pendingSamples = mutableStateListOf<LabeledImage>()
    val referenceImageUris = mutableStateListOf<Uri>()
    val sampleImageUris = mutableStateListOf<Uri>()

    // Temporary storage for labeling flow
    var temporaryUris by mutableStateOf<List<Uri>>(emptyList())

    /**
     * The dataset used as a reference for classification.
     */
    var referenceDataset by mutableStateOf<SampleDataset?>(null)
    var newDataset by mutableStateOf<SampleDataset?>(null)

    var isLoading by mutableStateOf(false)
        private set

    var importedFileName by mutableStateOf("data.csv")
    var importedFileUri by mutableStateOf<Uri?>(null)

    var distanceMetric by mutableStateOf("Euclidean")
    var colorMode by mutableStateOf("RGB")
    var normalizationStrategy by mutableStateOf("None")
    var normalizationSelection by mutableStateOf("Include Squares")
    var comparisonMode by mutableStateOf("Whole Card")
    var selectionStrategy by mutableStateOf("Mean")

    // ROI names persistence across screen transitions/simulation
    val savedNames = mutableStateListOf<List<String>>()

    // Track last ingestion state to avoid redundant work
    private var lastIngestedRefUris = emptyList<Uri>()
    private var lastIngestedSampleUris = emptyList<Uri>()
    private var lastIngestedSelectionStrategy = ""

    var ingestSelectionStrategy by mutableStateOf("Mean")

    // Simulation State
    var isSimulating by mutableStateOf(false)
    var narrationText by mutableStateOf("")
    var highlightedButtonId by mutableStateOf<String?>(null)

    // History State
    val analysisHistory = mutableStateListOf<AnalysisHistoryEntry>()


    /**
     * Ingests all pending images into structured datasets.
     */
    fun ingestAllPending(context: Context, onComplete: () -> Unit) {
        if (isSimulating || isLoading) {
            onComplete()
            return
        }

        val currentRefUris = pendingReferences.map { it.uri }
        val currentSampleUris = pendingSamples.map { it.uri }

        // Skip if nothing has changed
        if (currentRefUris == lastIngestedRefUris &&
            currentSampleUris == lastIngestedSampleUris &&
            ingestSelectionStrategy == lastIngestedSelectionStrategy &&
            (referenceDataset != null || pendingReferences.isEmpty()) &&
            (newDataset != null || pendingSamples.isEmpty())
        ) {
            onComplete()
            return
        }

        viewModelScope.launch {
            isLoading = true

            // Sync current names into savedNames before they are potentially lost during re-ingestion
            syncNames()

            if (pendingReferences.isNotEmpty()) {
                try {
                    val dataset = ingestImages(
                        currentRefUris,
                        context,
                        log = false,
                        selectionStrategy = ingestSelectionStrategy
                    )
                    dataset.samples.forEachIndexed { i, sample ->
                        sample.type = "Reference"
                        sample.referenceName = pendingReferences.getOrNull(i)?.label ?: ""
                    }
                    referenceDataset = dataset
                    lastIngestedRefUris = currentRefUris
                } catch (e: Exception) {
                    AppErrorLogger.logError(context, "Ingest", "Failed ingesting references", e)
                }
            }

            if (pendingSamples.isNotEmpty()) {
                try {
                    val dataset = ingestImages(
                        currentSampleUris,
                        context,
                        log = false,
                        selectionStrategy = ingestSelectionStrategy
                    )
                    dataset.samples.forEachIndexed { i, sample ->
                        sample.type = "Sample"
                        sample.referenceName = pendingSamples.getOrNull(i)?.label ?: ""
                    }
                    newDataset = dataset
                    lastIngestedSampleUris = currentSampleUris
                } catch (e: Exception) {
                    AppErrorLogger.logError(context, "Ingest", "Failed ingesting samples", e)
                }
            }

            lastIngestedSelectionStrategy = ingestSelectionStrategy

            // Restore names from savedNames
            var nameIdx = 0
            referenceDataset?.samples?.filter { it.isImage }?.forEach { sample ->
                savedNames.getOrNull(nameIdx++)?.let { names ->
                    sample.names.clear()
                    sample.names.addAll(names)
                }
            }
            newDataset?.samples?.filter { it.isImage }?.forEach { sample ->
                savedNames.getOrNull(nameIdx++)?.let { names ->
                    sample.names.clear()
                    sample.names.addAll(names)
                }
            }

            isLoading = false
            onComplete()
        }
    }

    /**
     * Checks if all samples in the current dataset have valid labels.
     *
     * @return True if all active labels are valid.
     */
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

    private fun flattenSample(sample: Sample, normalizationStrategy: String): DoubleArray {
        val data = sample.getNormalizedData(normalizationStrategy, colorMode, selectionStrategy)
        return sample.isSelected.indices
            .filter { sample.isSelected[it] }
            .sortedBy { sample.names[it] }
            .flatMap { data[it].toList() }
            .toDoubleArray()
    }

    fun runWholeCardClassification() {
        val ref = referenceDataset ?: return
        val new = newDataset ?: return

        for (sample in new.samples) {
            try {
                sample.classificationResults.clear()
                val sampleVec = flattenSample(sample, normalizationStrategy)

                var bestDistance = Double.MAX_VALUE
                var bestRef: Sample? = null

                for (refSample in ref.samples) {
                    val refVec = flattenSample(refSample, normalizationStrategy)
                    if (refVec.size != sampleVec.size) continue

                    val distance = when (distanceMetric) {
                        "Euclidean" -> {
                            var sum = 0.0
                            for (i in sampleVec.indices) {
                                val diff = sampleVec[i] - refVec[i]
                                sum += diff * diff
                            }
                            sqrt(sum)
                        }
                        "Manhattan" -> {
                            var sum = 0.0
                            for (i in sampleVec.indices) { sum += abs(sampleVec[i] - refVec[i]) }
                            sum
                        }
                        else -> Double.MAX_VALUE
                    }
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestRef = refSample
                    }
                }

                if (bestRef != null) {
                    val wellNames = mutableListOf<String>()
                    val sampleColors = mutableListOf<Scalar>()
                    val referenceColors = mutableListOf<Scalar>()
                    val wellDistances = mutableListOf<Double>()

                    val normalizedSample = sample.getNormalizedData(normalizationStrategy, colorMode, selectionStrategy)
                    val normalizedRef = bestRef.getNormalizedData(normalizationStrategy, colorMode, selectionStrategy)

                    sample.isSelected.forEachIndexed { dotIdx, isSelected ->
                        if (isSelected) {
                            val dotFeatures = normalizedSample[dotIdx]
                            // Match by name if possible, or index fallback
                            val refDotIdx = bestRef.names.indexOfFirst { it == sample.names[dotIdx] }
                            if (refDotIdx != -1 && refDotIdx < normalizedRef.size) {
                                val refFeatures = normalizedRef[refDotIdx]
                                val wellDistance = when (distanceMetric) {
                                    "Euclidean" -> {
                                        var sum = 0.0
                                        for (i in dotFeatures.indices) {
                                            val diff = dotFeatures[i] - refFeatures[i]
                                            sum += diff * diff
                                        }
                                        sqrt(sum)
                                    }
                                    "Manhattan" -> {
                                        var sum = 0.0
                                        for (i in dotFeatures.indices) {
                                            sum += abs(dotFeatures[i] - refFeatures[i])
                                        }
                                        sum
                                    }
                                    else -> -1.0
                                }
                                wellNames.add(sample.names[dotIdx])
                                sampleColors.add(sample.rgb[dotIdx])
                                referenceColors.add(bestRef.rgb[refDotIdx])
                                wellDistances.add(wellDistance)
                            }
                        }
                    }

                    sample.classificationResults.add(
                        ClassificationResult(
                            closestReferenceName = bestRef.referenceName,
                            totalDistance = bestDistance,
                            wellNames = wellNames,
                            sampleColors = sampleColors,
                            referenceColors = referenceColors,
                            wellDistances = wellDistances
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("Classification", "Whole card classification failed for sample", e)
            }
        }
    }

    fun runPerColorClassification() {
        val ref = referenceDataset ?: return
        val new = newDataset ?: return

        for (sample in new.samples) {
            try {
                sample.classificationResults.clear()

                val wellNames = mutableListOf<String>()
                val sampleColors = mutableListOf<Scalar>()
                val referenceColors = mutableListOf<Scalar>()
                val wellDistances = mutableListOf<Double>()
                val wellClosestRefs = mutableListOf<String>()

                val normalizedSample = sample.getNormalizedData(normalizationStrategy, colorMode, selectionStrategy)

                sample.isSelected.forEachIndexed { dotIdx, isSelected ->
                    if (isSelected) {
                        val dotFeatures = normalizedSample[dotIdx]
                        var bestWellDistance = Double.MAX_VALUE
                        var bestWellRefName = "Unknown"
                        var bestWellRefColor = Scalar(0.0, 0.0, 0.0)

                        for (refSample in ref.samples) {
                            val normalizedRef = refSample.getNormalizedData(normalizationStrategy, colorMode, selectionStrategy)
                            val refDotIdx = refSample.names.indexOfFirst { it == sample.names[dotIdx] }
                            if (refDotIdx != -1 && refDotIdx < normalizedRef.size) {
                                val refFeatures = normalizedRef[refDotIdx]
                                val distance = when (distanceMetric) {
                                    "Euclidean" -> {
                                        var sum = 0.0
                                        for (i in dotFeatures.indices) {
                                            val diff = dotFeatures[i] - refFeatures[i]
                                            sum += diff * diff
                                        }
                                        sqrt(sum)
                                    }
                                    "Manhattan" -> {
                                        var sum = 0.0
                                        for (i in dotFeatures.indices) { sum += abs(dotFeatures[i] - refFeatures[i]) }
                                        sum
                                    }
                                    else -> Double.MAX_VALUE
                                }
                                if (distance < bestWellDistance) {
                                    bestWellDistance = distance
                                    bestWellRefName = refSample.referenceName
                                    bestWellRefColor = refSample.rgb[refDotIdx]
                                }
                            }
                        }

                        wellNames.add(sample.names[dotIdx])
                        sampleColors.add(sample.rgb[dotIdx])
                        referenceColors.add(bestWellRefColor)
                        wellDistances.add(bestWellDistance)
                        wellClosestRefs.add(bestWellRefName)
                    }
                }

                sample.classificationResults.add(
                    ClassificationResult(
                        closestReferenceName = "Mixed / Per Well",
                        totalDistance = wellDistances.sum(),
                        wellNames = wellNames,
                        sampleColors = sampleColors,
                        referenceColors = referenceColors,
                        wellDistances = wellDistances,
                        wellClosestReferences = wellClosestRefs
                    )
                )
            } catch (e: Exception) {
                Log.e("Classification", "Per color classification failed", e)
            }
        }
    }

    fun runClassification() {
        val ref = referenceDataset
        val new = newDataset
        if (ref == null || new == null) return
        if (comparisonMode == "Whole Card") {
            runWholeCardClassification()
        } else {
            runPerColorClassification()
        }
    }

    fun syncNames() {
        savedNames.clear()
        val combined = mutableListOf<Sample>()
        referenceDataset?.samples?.filter { it.isImage }?.let { combined.addAll(it) }
        newDataset?.samples?.filter { it.isImage }?.let { combined.addAll(it) }
        combined.forEach { sample ->
            savedNames.add(sample.names.toList())
        }
    }

    fun reset() {
        pendingReferences.clear()
        pendingSamples.clear()
        referenceImageUris.clear()
        sampleImageUris.clear()
        temporaryUris = emptyList()
        referenceDataset = null
        newDataset = null
        importedFileName = "data.csv"
        importedFileUri = null
        comparisonMode = "Whole Card"
        savedNames.clear()
        lastIngestedRefUris = emptyList()
        lastIngestedSampleUris = emptyList()
        lastIngestedSelectionStrategy = ""
    }

    fun saveToHistory() {
        val dataset = newDataset ?: return
        val summary = dataset.samples.joinToString("; ") { sample ->
            val result = sample.classificationResults.firstOrNull()
            "${sample.referenceName} -> ${result?.closestReferenceName ?: "N/A"}"
        }
        val csvData = toCsvString(includeHeader = true, datasetChoice = "combined")
        analysisHistory.add(0, AnalysisHistoryEntry(
            fileName = importedFileName,
            summary = summary,
            csvData = csvData,
            distanceMetric = distanceMetric,
            colorMode = colorMode,
            normalizationStrategy = normalizationStrategy
        ))
    }

    fun deleteHistoryEntry(entry: AnalysisHistoryEntry) {
        analysisHistory.remove(entry)
    }

    fun startSimulation(navController: NavController) {
        if (isSimulating) return  // 🔥 prevents double start

        isSimulating = true
        syncNames()

        viewModelScope.launch(Dispatchers.Default) {
            runNavigationSimulation(
                viewModel = this@DatasetModel,
                navController = navController,
                scope = viewModelScope
            )
            isSimulating = false
        }
    }

    fun toCsvString(includeHeader: Boolean = true, datasetChoice: String = "sample"): String {
        val table = if (datasetChoice == "sample") newDataset else referenceDataset
        if (table == null || table.samples.isEmpty()) return ""

        // Calculate the union of all well names across all samples in the dataset, regardless of selection
        val allWellNames = table.samples.flatMap { sample ->
            sample.names.filter { it.isNotBlank() }
        }.distinct().sorted()

        val dyeHeaders = allWellNames.flatMap { name ->
            listOf("${name}_r", "${name}_g", "${name}_b")
        }
        val calHeaders = listOf(
            "cal0_r","cal0_g","cal0_b",
            "cal1_r","cal1_g","cal1_b",
            "cal2_r","cal2_g","cal2_b",
            "cal3_r","cal3_g","cal3_b"
        )
        val header = (listOf("sample_id","reference_name","distance_calculation","similarity_distance")
                + calHeaders + dyeHeaders).joinToString(",")

        val rows = table.samples.mapIndexed { sampleIdx, sample ->
            val result = sample.classificationResults.firstOrNull()
            val totalDist = result?.totalDistance ?: ""
            val refName = result?.closestReferenceName ?: sample.referenceName
            val meta = listOf(sampleIdx.toString(), refName, distanceMetric, totalDist.toString())

            val cal = sample.squares.take(4).flatMap {
                listOf(it.`val`[0], it.`val`[1], it.`val`[2])
            }.map { it.toString() }

            val rgbParts = allWellNames.flatMap { name ->
                val index = sample.names.indexOf(name)
                // Fill with RGB values if well exists, even if not selected
                if (index != -1) {
                    val scalar = sample.rgb[index]
                    listOf(scalar.`val`[0].toString(), scalar.`val`[1].toString(), scalar.`val`[2].toString())
                } else {
                    listOf("", "", "")
                }
            }

            (meta + cal + rgbParts).joinToString(",")
        }

        val body = rows.joinToString("\n")
        return if (includeHeader) "$header\n$body" else body
    }
}
