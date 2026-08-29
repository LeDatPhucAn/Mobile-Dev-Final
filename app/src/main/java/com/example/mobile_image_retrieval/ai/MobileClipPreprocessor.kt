package com.example.mobile_image_retrieval.ai

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/** Shortest-edge bicubic resize, center crop, RGB conversion, and exporter-defined normalization. */
class MobileClipPreprocessor(private val config: MobileClipModelConfig) {
    data class PreparedImage(val values: FloatArray, val shape: LongArray)

    fun prepare(bitmap: Bitmap): PreparedImage {
        require(bitmap.width > 0 && bitmap.height > 0)
        require(config.imageResizeMode == "shortest")
        require(config.imageInterpolation == "bicubic")
        require(config.imageCenterCrop)

        val size = config.imageInputSize
        val (scaledWidth, scaledHeight) = resizedDimensions(bitmap.width, bitmap.height, size)
        val cropLeft = round((scaledWidth - size) / 2.0).toInt()
        val cropTop = round((scaledHeight - size) / 2.0).toInt()
        val sourcePixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(sourcePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val xSamples = samplingAxis(size, cropLeft, bitmap.width, scaledWidth)
        val ySamples = samplingAxis(size, cropTop, bitmap.height, scaledHeight)
        val output = FloatArray(3 * size * size)
        val planeSize = size * size

        for (outputY in 0 until size) {
            val yOffset = outputY * 4
            for (outputX in 0 until size) {
                val xOffset = outputX * 4
                var red = 0f
                var green = 0f
                var blue = 0f
                for (ky in 0..3) {
                    val sourceRow = ySamples.indices[yOffset + ky] * bitmap.width
                    val yWeight = ySamples.weights[yOffset + ky]
                    for (kx in 0..3) {
                        val pixel = sourcePixels[sourceRow + xSamples.indices[xOffset + kx]]
                        val weight = yWeight * xSamples.weights[xOffset + kx]
                        red += ((pixel ushr 16) and 0xff) * weight
                        green += ((pixel ushr 8) and 0xff) * weight
                        blue += (pixel and 0xff) * weight
                    }
                }
                val pixelIndex = outputY * size + outputX
                output[pixelIndex] = normalize(red, 0)
                output[planeSize + pixelIndex] = normalize(green, 1)
                output[2 * planeSize + pixelIndex] = normalize(blue, 2)
            }
        }
        return PreparedImage(output, longArrayOf(1, 3, size.toLong(), size.toLong()))
    }

    private fun normalize(value255: Float, channel: Int): Float {
        val clipped = value255.coerceIn(0f, 255f)
        val scaled = if (config.divideImageBy255) clipped / 255f else clipped
        return (scaled - config.imageMean[channel]) / config.imageStd[channel]
    }

    private data class SamplingAxis(val indices: IntArray, val weights: FloatArray)

    private fun samplingAxis(outputSize: Int, cropOffset: Int, sourceSize: Int, resizedSize: Int): SamplingAxis {
        val indices = IntArray(outputSize * 4)
        val weights = FloatArray(outputSize * 4)
        val sourcePerOutput = sourceSize.toDouble() / resizedSize
        for (output in 0 until outputSize) {
            val sourcePosition = (output + cropOffset + 0.5) * sourcePerOutput - 0.5
            val base = floor(sourcePosition).toInt()
            var weightSum = 0f
            for (tap in 0..3) {
                val unboundedIndex = base + tap - 1
                val weight = cubic(sourcePosition - unboundedIndex).toFloat()
                indices[output * 4 + tap] = unboundedIndex.coerceIn(0, sourceSize - 1)
                weights[output * 4 + tap] = weight
                weightSum += weight
            }
            if (weightSum != 0f && weightSum != 1f) {
                for (tap in 0..3) weights[output * 4 + tap] /= weightSum
            }
        }
        return SamplingAxis(indices, weights)
    }

    /** Keys cubic convolution with a=-0.5, matching PIL's bicubic reconstruction kernel. */
    private fun cubic(distance: Double): Double {
        val x = abs(distance)
        return when {
            x <= 1.0 -> 1.5 * x * x * x - 2.5 * x * x + 1.0
            x < 2.0 -> -0.5 * x * x * x + 2.5 * x * x - 4.0 * x + 2.0
            else -> 0.0
        }
    }

    private fun resizedDimensions(width: Int, height: Int, shortest: Int): Pair<Int, Int> = when {
        width < height -> shortest to (shortest.toLong() * height / width).toInt().coerceAtLeast(shortest)
        height < width -> (shortest.toLong() * width / height).toInt().coerceAtLeast(shortest) to shortest
        else -> shortest to shortest
    }
}
