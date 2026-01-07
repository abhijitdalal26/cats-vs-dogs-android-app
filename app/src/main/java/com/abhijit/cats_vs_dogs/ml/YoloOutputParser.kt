package com.abhijit.cats_vs_dogs.ml


// uploadButton
// Once the classifier finished it classification result is passes here
// Prediction is done but need to extract the result from the outputBuffer
object YoloOutputParser {
    private const val CONFIDENCE_THRESHOLD = 0.45f // Only show result if model is > 45% sure

    fun postProcessing(output: Array<Array<FloatArray>>): Pair<String, Float>? {

        var bestScore = 0f;
        var bestLabel: String? = null

        // output[0] is the first (and only) image
        // output[0][4] contains Cat scores for all 8400 boxes
        // output[0][5] contains Dog scores for all 8400 boxes

        for (i in 0 until 8400) {
            val catScore = output[0][4][i]
            val dogScore = output[0][5][i]

            // Check Cat
            if (catScore > bestScore) {
                bestScore = catScore
                bestLabel = "Cat"
            }
            // Check Dog
            if (dogScore > bestScore) {
                bestScore = dogScore
                bestLabel = "Dog"
            }

        }
        return if (bestLabel != null && bestScore > CONFIDENCE_THRESHOLD) {
            Pair(bestLabel, bestScore)
        } else {
            null
        }
    }
}