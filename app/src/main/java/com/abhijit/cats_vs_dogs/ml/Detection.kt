package com.abhijit.cats_vs_dogs.ml

import android.graphics.RectF

data class Detection(
    val box: RectF,
    val label: String,
    val confidence: Float
)