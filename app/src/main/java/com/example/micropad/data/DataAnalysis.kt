package com.example.micropad.data

import java.util.Collections

import android.graphics.Bitmap
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar


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
    var greyscale: List<Double> = rgb.map{ 0.299 * it.`val`[0] + 0.587 * it.`val`[1] + 0.144 * it.`val`[2] }
    var names: MutableList<String> = rgb.map{ "" }.toMutableList()

    var referenceName: String = ""

    // Function to reorder the chosen dots in an image
    // The user should be able to reorder the dots in an image in the case they
    // are ordered incorrectly
    fun reorder(from: Int, to: Int) {
        Collections.swap(dots, from, to)
        Collections.swap(names, from, to)
        Collections.swap(rgb, from, to)
        Collections.swap(greyscale, from, to)

        if (balanced != null) {
            ordering = drawOrdering(balanced, dots)
        }
    }

    // TODO: Data validation: Check that all images have same number of dots and allow
    // for retaking of images
}


/**
 * A class for abstracting useful functionality when comparing two datasets
 *
 * @property samples A list of samples compiled in one place, to be used for classification
 */
class SampleDataset(val samples: MutableList<Sample>) {
    fun nameWell(index: Int, name: String) {
        for (sample in samples) {
            sample.names[index] = name
        }
    }

    fun reorderSample(sampleID: Int, from: Int, to: Int) {
        samples[sampleID].reorder(from, to)
    }

    fun fromCSV() {
        // TODO: This part, use the CsvImportHelper and all that
    }

    fun toCSV() {
        // TODO: This part
    }
}

fun classify(referenceData: SampleDataset, newData: SampleDataset,
             distance: String = "Euclidean", mode: String = "RGB"): SampleDataset {
    // TODO: Implement all the classification logic with multiple distance functions
    return newData
}
