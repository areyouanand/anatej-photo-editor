package com.anatej.photo.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.anatej.photo.model.Adjustments
import com.anatej.photo.model.Look
import com.anatej.photo.model.Lut3D
import com.anatej.photo.model.PortraitLight
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Offline CPU renderer used for thumbnails and final export.
 *
 * The on-screen preview uses the same parameters through a ColorMatrix plus canvas
 * overlays, so the exported JPEG matches the preview closely while staying fast.
 */
object PhotoRenderer {

    fun render(
        source: Bitmap,
        adjustments: Adjustments,
        look: Look,
        lookStrength: Float,
        light: PortraitLight,
        maxDim: Int,
        lut: Lut3D? = null,
        lutStrength: Float = 1f,
        subjectMask: Bitmap? = null,
        relightNormals: Bitmap? = null,
        bakeEffects: Boolean = true
    ): Bitmap {
        val stock = look.scaled(lookStrength)
        val exposure = (adjustments.exposure + stock.exposure).coerceIn(-3f, 3f)
        val contrast = (adjustments.contrast + stock.contrast).coerceIn(-0.95f, 1f)
        val saturation = (adjustments.saturation + stock.saturation).coerceIn(-1f, 1f)
        val warmth = (adjustments.warmth + stock.warmth).coerceIn(-1f, 1f)
        val fade = (adjustments.fade + stock.fade).coerceIn(0f, 1f)
        val vignette = (adjustments.vignette + stock.vignette).coerceIn(0f, 1f)
        val grain = (adjustments.grain + stock.grain).coerceIn(0f, 1f)

        val out = scaleBitmap(source, maxDim)
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)

        val gain = 2f.pow(exposure)
        val rg = gain * (1f + warmth * 0.18f)
        val gg = gain * (1f - kotlin.math.abs(warmth) * 0.02f)
        val bg = gain * (1f - warmth * 0.18f)
        val contrastScale = 1f + contrast * 0.9f
        val saturationScale = (1f + saturation).coerceIn(0f, 2.2f)
        val fadeScale = 1f - fade * 0.34f
        val fadeLift = fade * 23f

        val lightOn = bakeEffects && light.enabled && light.intensity > 0.001f
        val activeLut = if (lutStrength > 0.001f) lut else null
        val lutOut = FloatArray(3)
        val maskAlpha: IntArray? = if (lightOn && light.subjectOnly && subjectMask != null) {
            val scaled = if (subjectMask.width == w && subjectMask.height == h) {
                subjectMask
            } else {
                Bitmap.createScaledBitmap(subjectMask, w, h, true)
            }
            val maskPixels = IntArray(w * h)
            scaled.getPixels(maskPixels, 0, w, 0, 0, w, h)
            if (scaled !== subjectMask) scaled.recycle()
            maskPixels
        } else {
            null
        }
        val normalPixels: IntArray? = if (lightOn && relightNormals != null) {
            val scaled = if (relightNormals.width == w && relightNormals.height == h) {
                relightNormals
            } else {
                Bitmap.createScaledBitmap(relightNormals, w, h, true)
            }
            val normals = IntArray(w * h)
            scaled.getPixels(normals, 0, w, 0, 0, w, h)
            if (scaled !== relightNormals) scaled.recycle()
            normals
        } else {
            null
        }
        val minDim = min(w, h).toFloat()
        val lx = light.x * w
        val ly = light.y * h
        val (lr, lg, lb) = RelightMap.warmRgb(light.warmth)

        val invW = 1f / w
        val invH = 1f / h

        for (y in 0 until h) {
            val ny = (y + 0.5f) * invH
            val vy = ny - 0.5f
            for (x in 0 until w) {
                val index = y * w + x
                val c = pixels[index]

                var r = ((c shr 16) and 0xFF).toFloat()
                var g = ((c shr 8) and 0xFF).toFloat()
                var b = (c and 0xFF).toFloat()

                // LUT first: the preview bakes it into the base frame, then the live
                // colour matrix grades on top. Keeping the same order in export makes
                // LUT + slider changes instantaneous in the editor.
                if (activeLut != null) {
                    activeLut.sample(r / 255f, g / 255f, b / 255f, lutOut)
                    val mix = lutStrength.coerceIn(0f, 1f)
                    r += (lutOut[0] * 255f - r) * mix
                    g += (lutOut[1] * 255f - g) * mix
                    b += (lutOut[2] * 255f - b) * mix
                }

                // Exposure + white balance
                r *= rg
                g *= gg
                b *= bg

                // Contrast around mid grey
                r = (r - 127.5f) * contrastScale + 127.5f
                g = (g - 127.5f) * contrastScale + 127.5f
                b = (b - 127.5f) * contrastScale + 127.5f

                // Saturation
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = lum + (r - lum) * saturationScale
                g = lum + (g - lum) * saturationScale
                b = lum + (b - lum) * saturationScale

                // Matte fade
                r = r * fadeScale + fadeLift
                g = g * fadeScale + fadeLift
                b = b * fadeScale + fadeLift

                if (lightOn) {
                    val maskValue: Float
                    val nx: Float
                    val ny: Float
                    val nz: Float
                    if (normalPixels != null) {
                        val n = normalPixels[index]
                        maskValue = ((n ushr 24) and 0xFF) / 255f
                        nx = (((n shr 16) and 0xFF) / 127.5f) - 1f
                        ny = (((n shr 8) and 0xFF) / 127.5f) - 1f
                        nz = ((n and 0xFF) / 127.5f) - 1f
                    } else {
                        maskValue = if (maskAlpha != null) {
                            (maskAlpha[index] ushr 24) / 255f
                        } else {
                            1f
                        }
                        nx = 0f; ny = 0f; nz = 1f
                    }
                    val dx = (lx - (x + 0.5f)) / minDim
                    val dy = (ly - (y + 0.5f)) / minDim
                    val sample = RelightMap.sampleLight(nx, ny, nz, maskValue, dx, dy, light)
                    val amount = (sample.light + sample.spec).coerceIn(0f, 1f)
                    if (amount > 0.002f) {
                        r += (255f - r) * amount * lr
                        g += (255f - g) * amount * lg
                        b += (255f - b) * amount * lb
                    }
                    if (sample.shade > 0.002f) {
                        val shade = sample.shade
                        r *= (1f - shade)
                        g *= (1f - shade)
                        b *= (1f - shade)
                    }
                }

                if (bakeEffects && vignette > 0f) {
                    val nx = (x + 0.5f) * invW
                    val vx = nx - 0.5f
                    val d = sqrt(vx * vx + vy * vy) / 0.70710677f
                    val t = ((d - 0.28f) / 0.72f).coerceIn(0f, 1f)
                    val smooth = t * t * (3f - 2f * t)
                    val factor = 1f - vignette * 0.62f * smooth
                    r *= factor
                    g *= factor
                    b *= factor
                }

                if (bakeEffects && grain > 0f) {
                    var hash = x * 374761393 + y * 668265263
                    hash = hash xor (hash shr 13)
                    hash *= 1274126177
                    val n = (((hash xor (hash shr 16)) and 0xFF) / 255f) - 0.5f
                    val noise = n * grain * 34f
                    r += noise
                    g += noise
                    b += noise
                }

                val ri = r.roundToInt().coerceIn(0, 255)
                val gi = g.roundToInt().coerceIn(0, 255)
                val bi = b.roundToInt().coerceIn(0, 255)
                pixels[index] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }

        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * 4x5 colour matrix for the live preview. Mirrors gain -> contrast -> saturation -> fade.
     */
    fun previewMatrix(adjustments: Adjustments, look: Look, lookStrength: Float): FloatArray {
        val stock = look.scaled(lookStrength)
        val exposure = (adjustments.exposure + stock.exposure).coerceIn(-3f, 3f)
        val contrast = (adjustments.contrast + stock.contrast).coerceIn(-0.95f, 1f)
        val saturation = (adjustments.saturation + stock.saturation).coerceIn(-1f, 1f)
        val warmth = (adjustments.warmth + stock.warmth).coerceIn(-1f, 1f)
        val fade = (adjustments.fade + stock.fade).coerceIn(0f, 1f)

        val gain = 2f.pow(exposure)
        val rg = gain * (1f + warmth * 0.18f)
        val gg = gain * (1f - kotlin.math.abs(warmth) * 0.02f)
        val bg = gain * (1f - warmth * 0.18f)
        val contrastScale = 1f + contrast * 0.9f
        val fadeScale = 1f - fade * 0.34f
        val fadeLift = fade * 23f

        val gainMatrix = floatArrayOf(
            rg, 0f, 0f, 0f, 0f,
            0f, gg, 0f, 0f, 0f,
            0f, 0f, bg, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val offset = 127.5f * (1f - contrastScale)
        val contrastMatrix = floatArrayOf(
            contrastScale, 0f, 0f, 0f, offset,
            0f, contrastScale, 0f, 0f, offset,
            0f, 0f, contrastScale, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )
        val sat = saturation.coerceIn(-1f, 1f) + 1f
        val invSat = 1f - sat
        val lr = 0.2126f * invSat
        val lg = 0.7152f * invSat
        val lb = 0.0722f * invSat
        val saturationMatrix = floatArrayOf(
            lr + sat, lg, lb, 0f, 0f,
            lr, lg + sat, lb, 0f, 0f,
            lr, lg, lb + sat, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val fadeMatrix = floatArrayOf(
            fadeScale, 0f, 0f, 0f, fadeLift,
            0f, fadeScale, 0f, 0f, fadeLift,
            0f, 0f, fadeScale, 0f, fadeLift,
            0f, 0f, 0f, 1f, 0f
        )

        var result = identity()
        result = concat(result, gainMatrix)
        result = concat(result, contrastMatrix)
        result = concat(result, saturationMatrix)
        result = concat(result, fadeMatrix)
        return result
    }

    private fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    /** Returns [next] ∘ [after] (i.e. apply [after] first, then [next]). */
    private fun concat(after: FloatArray, next: FloatArray): FloatArray {
        val result = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = if (col == 4) next[row * 5 + 4] else 0f
                for (k in 0 until 4) {
                    sum += next[row * 5 + k] * after[k * 5 + col]
                }
                result[row * 5 + col] = sum
            }
        }
        return result
    }

    private fun scaleBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val longest = max(source.width, source.height)
        val scale = if (maxDim > 0) min(1f, maxDim.toFloat() / longest) else 1f
        val w = max(1, (source.width * scale).roundToInt())
        val h = max(1, (source.height * scale).roundToInt())
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(
            source,
            null,
            Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        return out
    }
}
