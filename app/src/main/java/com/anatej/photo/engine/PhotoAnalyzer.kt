package com.anatej.photo.engine

import android.graphics.Bitmap
import com.anatej.photo.model.PhotoAnalysis
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Fully local photo analysis.
 *
 * 1. Build a luminance histogram on a 96 px proxy.
 * 2. Estimate exposure / dynamic range from percentiles.
 * 3. Find the subject with a skin-tone weighted centroid, falling back to a
 *    centre-weighted saliency centroid when no skin is present.
 *
 * The output drives both the "Auto rescue" recipe and the default placement of the
 * portrait light. This is deliberately model-free so the app stays small and offline.
 */
object PhotoAnalyzer {

    fun analyze(bitmap: Bitmap): PhotoAnalysis {
        val targetW = 96
        val targetH = max(1, (bitmap.height * (targetW.toFloat() / bitmap.width)).roundToInt())
        val proxy = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val w = proxy.width
        val h = proxy.height
        val pixels = IntArray(w * h)
        proxy.getPixels(pixels, 0, w, 0, 0, w, h)
        if (proxy !== bitmap) proxy.recycle()

        val histogram = IntArray(256)
        var lumaSum = 0f
        var darkCount = 0
        var clippedCount = 0

        var skinCount = 0
        var skinX = 0.0
        var skinY = 0.0
        var skinWeight = 0.0
        var skinLumaWeighted = 0.0

        var salX = 0.0
        var salY = 0.0
        var salWeight = 0.0

        val invW = 1f / w
        val invH = 1f / h

        for (y in 0 until h) {
            val ny = (y + 0.5f) * invH
            val cy = ny - 0.5f
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                lumaSum += luma
                histogram[(luma * 255f).roundToInt().coerceIn(0, 255)]++

                if (luma < 0.16f) darkCount++
                if (luma > 0.975f || r > 250 && g > 250 && b > 250) clippedCount++

                // Skin-tone test in YCbCr. Broad enough for a range of skin tones,
                // narrow enough to reject most warm walls and wood.
                val ycc = 0.299f * r + 0.587f * g + 0.114f * b
                val cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
                val cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b
                if (ycc > 42f && cb in 76f..136f && cr in 132f..181f) {
                    val wgt = (1f - abs(luma - 0.58f)).coerceIn(0.15f, 1f)
                    skinCount++
                    skinWeight += wgt
                    skinX += (x + 0.5f) * wgt
                    skinY += (y + 0.5f) * wgt
                    skinLumaWeighted += luma * wgt
                }

                // Centre-weighted saliency fallback: depth of the frame plus midtone bias.
                val nx = (x + 0.5f) * invW
                val cx = nx - 0.5f
                val centre = exp(-(cx * cx + cy * cy) * 3.2f)
                val sal = centre * (abs(luma - 0.48f) + 0.08f)
                salWeight += sal
                salX += (x + 0.5f) * sal
                salY += (y + 0.5f) * sal
            }
        }

        val total = (w * h).toFloat()
        val meanLuma = lumaSum / total
        val darkFraction = darkCount / total
        val clippedFraction = clippedCount / total
        val skinFraction = skinCount / total

        val median = percentile(histogram, 0.50f)
        val p05 = percentile(histogram, 0.05f)
        val p95 = percentile(histogram, 0.95f)
        val dynamicRange = (p95 - p05).coerceIn(0f, 1f)

        val useSkin = skinFraction > 0.012f && skinWeight > 0.0
        val subjectX: Float
        val subjectY: Float
        val confidence: Float
        if (useSkin) {
            subjectX = (skinX / skinWeight / w).toFloat().coerceIn(0f, 1f)
            subjectY = (skinY / skinWeight / h).toFloat().coerceIn(0f, 1f)
            // Skin coverage of ~12% of the frame is a strong portrait signal.
            confidence = (skinFraction * 7f).coerceIn(0.25f, 1f)
        } else if (salWeight > 0.0) {
            subjectX = (salX / salWeight / w).toFloat().coerceIn(0f, 1f)
            subjectY = (salY / salWeight / h).toFloat().coerceIn(0f, 1f)
            confidence = 0.18f
        } else {
            subjectX = 0.5f
            subjectY = 0.4f
            confidence = 0f
        }

        val subjectLuma = if (skinWeight > 0.0) {
            (skinLumaWeighted / skinWeight).toFloat()
        } else {
            meanLuma
        }

        // Exposure suggestion. A whole-frame median alone is easily fooled by a dark
        // background around a well-lit face, so when a subject is detected we grade the
        // face itself. Fall back to the histogram for landscapes and flat frames.
        val target = 0.46f
        val histogramEv = log2(target / max(median, 0.035f)) * 0.85f
        val subjectEv = log2(0.55f / max(subjectLuma, 0.06f)).coerceIn(-1.2f, 2.2f)
        var suggestedExposure = when {
            useSkin && subjectLuma < 0.32f -> histogramEv * 0.5f + subjectEv * 0.5f
            useSkin && subjectLuma < 0.45f -> subjectEv
            useSkin -> min(histogramEv, subjectEv)
            else -> histogramEv + if (darkFraction > 0.55f) 0.25f else 0f
        }
        // Protect highlights when there is no reliable subject to anchor the exposure.
        if (!useSkin && p95 > 0.90f && clippedFraction > 0.05f) {
            suggestedExposure = min(suggestedExposure, 0.1f)
        }
        if (!useSkin && p95 > 0.975f) {
            suggestedExposure = min(suggestedExposure, -0.1f)
        }
        suggestedExposure = suggestedExposure.coerceIn(-1.1f, 2.3f)

        val suggestedContrast = when {
            dynamicRange < 0.34f -> 0.20f
            dynamicRange < 0.5f -> 0.10f
            dynamicRange > 0.8f -> -0.06f
            else -> 0.04f
        }

        val meanSaturation = estimateSaturation(pixels)
        val suggestedSaturation = when {
            meanSaturation < 0.18f -> 0.18f
            meanSaturation > 0.5f -> -0.04f
            else -> 0.06f
        }

        return PhotoAnalysis(
            meanLuma = meanLuma,
            medianLuma = median,
            p05 = p05,
            p95 = p95,
            darkFraction = darkFraction,
            clippedFraction = clippedFraction,
            subjectX = subjectX,
            subjectY = subjectY,
            subjectConfidence = confidence,
            subjectLuma = subjectLuma,
            skinFraction = skinFraction,
            suggestedExposure = suggestedExposure,
            suggestedContrast = suggestedContrast,
            suggestedSaturation = suggestedSaturation
        )
    }

    private fun percentile(histogram: IntArray, fraction: Float): Float {
        val total = histogram.sum()
        if (total == 0) return 0.5f
        val goal = (total * fraction).roundToInt()
        var acc = 0
        for (i in histogram.indices) {
            acc += histogram[i]
            if (acc >= goal) return i / 255f
        }
        return 1f
    }

    private fun estimateSaturation(pixels: IntArray): Float {
        if (pixels.isEmpty()) return 0f
        var sum = 0f
        val step = max(1, pixels.size / 4096)
        var samples = 0
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val mx = max(r, max(g, b)).toFloat()
            val mn = min(r, min(g, b)).toFloat()
            sum += if (mx <= 0f) 0f else (mx - mn) / mx
            samples++
            i += step
        }
        return if (samples == 0) 0f else sum / samples
    }
}
