package com.example.micropad.data.model

import org.opencv.core.Mat
import org.opencv.core.Scalar

data class Roi(
    val id: Int,
    val region: Mat,
    val extractedColor: Scalar,
    var label: DyeLabel? = null
)