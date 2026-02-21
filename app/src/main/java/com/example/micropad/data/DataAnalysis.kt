package com.example.micropad.data

import java.util.Collections

import android.graphics.Bitmap
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar

class Sample(val imageData: Mat, val balanced: Mat, var ordering: Bitmap, val dots: MutableList<Pair<MatOfPoint, Scalar>>, var rgb: List<Scalar>) {
    // Grey = 0.299R + 0.587G + 0.114B
    var greyscale: List<Double> = rgb.map{ 0.299 * it.`val`[0] + 0.587 * it.`val`[1] + 0.144 * it.`val`[2] }
    var names: MutableList<String> = mutableListOf()

    // Function to reorder the chosen dots in an image, needs implemented in the UI
    fun reorder(move: Int, to: Int) {
        Collections.swap(dots, move, to)

        val dotColors = dots.map {
            extractDyeColor(extractContour(balanced, it.first))
        }
        rgb = dotColors
        ordering = drawOrdering(balanced, dots)
        greyscale = rgb.map{ 0.299 * it.`val`[0] + 0.587 * it.`val`[1] + 0.144 * it.`val`[2] }
    }
}

fun compareSamples(sample1: Sample, sample2: Sample, mode: String = "Euclidean", color: String = "RGB") {

}
