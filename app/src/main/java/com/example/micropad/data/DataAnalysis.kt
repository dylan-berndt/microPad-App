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


/**
 * A class for handling sample data captured from an image of a micropad
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
 * @property referenceName Used only if a sample is a reference point, stores the reference data
 */
class Sample(val imageData: Mat?, val balanced: Mat?, var ordering: Bitmap?, val dots: MutableList<Pair<MatOfPoint, Scalar>>) {
    // Grey = 0.299R + 0.587G + 0.114B
    var rgb: List<Scalar> = dots.map { it.second }
    var greyscale: List<Double> =
        rgb.map { 0.299 * it.`val`[0] + 0.587 * it.`val`[1] + 0.114 * it.`val`[2] }
    var names = mutableStateListOf<String>().apply {
        repeat(rgb.size) { add("") }
    }
    var referenceName: String = ""

    fun validateLabels(): Boolean {
        if (names.any { it.isBlank() }) return false
        if (names.toSet().size != names.size) return false
        return true
    }

    fun reassignLabel(index: Int, newLabel: String): Boolean {
        if (index !in names.indices) return false
        if (names.contains(newLabel) && names[index] != newLabel) return false

        names[index] = newLabel
        return true
    }

    // Function to reorder the chosen dots in an image
    // The user should be able to reorder the dots in an image in the case they
    // are ordered incorrectly
    fun reorder(from: Int, to: Int) {
        Collections.swap(dots, from, to)
        Collections.swap(names, from, to)
        Collections.swap(rgb, from, to)
        Collections.swap(greyscale, from, to)

        if (balanced != null) {
            // ordering = drawOrdering(balanced, dots) // Uncomment if drawOrdering exists
        }
    }
}


// TODO: Data validation: Check that all images have same number of dots and allow
// for retaking of images


/**
 * A class for abstracting useful functionality when comparing two datasets
 *
 * @property samples A list of samples compiled in one place, to be used for classification
 */
class SampleDataset(val samples: MutableList<Sample>) {
    fun nameWell(index: Int, name: String): Boolean {
        var worked = true
        for (sample in samples) {
            worked = worked && sample.reassignLabel(index, name)
        }

        return worked
    }

    fun reorderSample(sampleID: Int, from: Int, to: Int) {
        samples[sampleID].reorder(from, to)
    }

    fun fromCSV(uri: Uri, context: Context) {
        val input = context.contentResolver.openInputStream(uri) ?: return
        input.bufferedReader().useLines { lines ->
            samples.clear()

            lines.forEach { line ->
                try {
                    val tokens = line.split(",")

                    val numberOfDots = 6
                    val expectedColumns = numberOfDots * 4

                    if (tokens.size != expectedColumns) {
                        return@forEach
                    }

                    val names = tokens.takeLast(numberOfDots)

                    val colors = tokens.take(numberOfDots * 3)
                        .chunked(3)
                        .map {
                            Scalar(
                                it[0].toDouble(),
                                it[1].toDouble(),
                                it[2].toDouble()
                            )
                        }
                        .toMutableList()

                    val dots = colors.map { Pair(MatOfPoint(), it) }.toMutableList()

                    val sample = Sample(null, null, null, dots)
                    sample.names.clear()
                    sample.names.addAll(names)
                    sample.rgb = colors

                    samples.add(sample)

                } catch (e: Exception) {
                    // Invalid row → skip it
                    return@forEach
                }
            }
        }
    }

    fun classify(
        referenceData: SampleDataset,
        newData: SampleDataset,
        distance: String = "Euclidean",
        mode: String = "RGB"
    ): SampleDataset {
        for (sample in newData.samples) {
            val newNames = sample.rgb.mapIndexed { index, dotColor ->
                val closestRef = referenceData.samples.minByOrNull { refSample ->
                    val refColor = refSample.rgb[index]

                    when (distance) {
                        "Euclidean" -> {
                            if (mode == "RGB") {
                                val dr = dotColor.`val`[0] - refColor.`val`[0]
                                val dg = dotColor.`val`[1] - refColor.`val`[1]
                                val db = dotColor.`val`[2] - refColor.`val`[2]
                                dr * dr + dg * dg + db * db
                            } else {
                                val grayDot = 0.299 * dotColor.`val`[0] +
                                        0.587 * dotColor.`val`[1] +
                                        0.114 * dotColor.`val`[2]
                                val grayRef = 0.299 * refColor.`val`[0] +
                                        0.587 * refColor.`val`[1] +
                                        0.114 * refColor.`val`[2]
                                val diff = grayDot - grayRef
                                diff * diff
                            }
                        }

                        "Manhattan" -> {
                            if (mode == "RGB") {
                                val dr = abs(dotColor.`val`[0] - refColor.`val`[0])
                                val dg = abs(dotColor.`val`[1] - refColor.`val`[1])
                                val db = abs(dotColor.`val`[2] - refColor.`val`[2])
                                dr + dg + db
                            } else {
                                val grayDot = 0.299 * dotColor.`val`[0] +
                                        0.587 * dotColor.`val`[1] +
                                        0.114 * dotColor.`val`[2]
                                val grayRef = 0.299 * refColor.`val`[0] +
                                        0.587 * refColor.`val`[1] +
                                        0.114 * refColor.`val`[2]
                                abs(grayDot - grayRef)
                            }
                        }

                        else -> Double.MAX_VALUE
                    }
                }

                closestRef?.names?.get(index) ?: ""
            }

            sample.names.clear()
            sample.names.addAll(newNames)
        }
        return newData
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
    fun ingest(uris: List<Uri>, context: Context) {
        viewModelScope.launch {
            isLoading = true
            newDataset = ingestImages(uris, context, log=true)
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

    // This is never updated with the samples from classify
    val samples = mutableStateListOf<Sample>()

    /**
     *
     */
    fun toCsvString(): String {
        if (samples.isEmpty()) return ""  // No data

        // Header row
        val header = "sample_name,r_value,g_value,b_value"

        // Data rows
        val dataRows = samples.joinToString(separator = "\n") { sample ->
            val name = sample.names.firstOrNull() ?: "N/A"
            val rgb = sample.rgb.firstOrNull()?.`val` ?: doubleArrayOf(0.0, 0.0, 0.0)
            "$name,${rgb[0]},${rgb[1]},${rgb[2]}"
        }

        return "$header\n$dataRows"
    }
}
