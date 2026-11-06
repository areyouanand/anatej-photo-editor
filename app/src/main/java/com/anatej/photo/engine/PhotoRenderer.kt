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
        val highlights = adjustments.highlights.coerceIn(-1f, 1f)
        val shadows = adjustments.shadows.coerceIn(-1f, 1f)
        val whites = adjustments.whites.coerceIn(-1f, 1f)
        val blacks = adjustments.blacks.coerceIn(-1f, 1f)
        val saturation = (adjustments.saturation + stock.saturation).coerceIn(-1f, 1f)
        val warmth = (adjustments.warmth + stock.warmth).coerceIn(-1f, 1f)
        val fade = (adjustments.fade + stock.fade).coerceIn(0f, 1f)
        val vignette = (adjustments.vignette + stock.vignette).coerceIn(0f, 1f)
        val grain = (adjustments.grain + stock.grain).coerceIn(0f, 1f)
        val sharpness = (adjustments.sharpness + stock.sharpness).coerceIn(-1f, 1f)
        val bloom = (adjustments.bloom + stock.bloom).coerceIn(0f, 1f)
        val halation = (adjustments.halation + stock.halation).coerceIn(0f, 1f)
        val shadowTint = (adjustments.shadowTint + stock.shadowTint).coerceIn(-1f, 1f)
        val highlightTint = (adjustments.highlightTint + stock.highlightTint).coerceIn(-1f, 1f)

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
        val sr = shadowTint * 20f
        val sg = -shadowTint * 4f
        val sb = -shadowTint * 22f
        val hr = highlightTint * 24f
        val hg = highlightTint * 7f
        val hb = -highlightTint * 24f
        val splitRScale = 1f + (hr - sr) / 255f
        val splitGScale = 1f + (hg - sg) / 255f
        val splitBScale = 1f + (hb - sb) / 255f

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

                // Highlights, shadows, whites & blacks recovery
                if (highlights != 0f || shadows != 0f || whites != 0f || blacks != 0f) {
                    val nl = ((0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f).coerceIn(0f, 1f)
                    val invNl = 1f - nl
                    val shW = invNl * invNl
                    val hiW = nl * nl
                    val blkW = shW * invNl
                    val whtW = hiW * nl
                    val toneDelta = shadows * 46f * shW + highlights * 46f * hiW +
                        blacks * 34f * blkW + whites * 34f * whtW
                    r += toneDelta
                    g += toneDelta
                    b += toneDelta
                }

                // Saturation
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = lum + (r - lum) * saturationScale
                g = lum + (g - lum) * saturationScale
                b = lum + (b - lum) * saturationScale

                // Matte fade
                r = r * fadeScale + fadeLift
                g = g * fadeScale + fadeLift
                b = b * fadeScale + fadeLift

                // Split-toning (shadow/highlight tint)
                r = r * splitRScale + sr
                g = g * splitGScale + sg
                b = b * splitBScale + sb

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
                        val gain = 1f + amount * 2.1f
                        val tr = r * gain * lr
                        val tg = g * gain * lg
                        val tb = b * gain * lb
                        val ro = (tr * 255f) / (255f + (tr - r).coerceAtLeast(0f) * 0.45f)
                        val go = (tg * 255f) / (255f + (tg - g).coerceAtLeast(0f) * 0.45f)
                        val bo = (tb * 255f) / (255f + (tb - b).coerceAtLeast(0f) * 0.45f)
                        r += (ro - r) * amount
                        g += (go - g) * amount
                        b += (bo - b) * amount
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

        if (bakeEffects && (sharpness > 0.01f || sharpness < -0.01f || bloom > 0.01f || halation > 0.01f)) {
            applyEnhancements(pixels, w, h, sharpness, bloom, halation)
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
        val highlights = adjustments.highlights.coerceIn(-1f, 1f)
        val shadows = adjustments.shadows.coerceIn(-1f, 1f)
        val whites = adjustments.whites.coerceIn(-1f, 1f)
        val blacks = adjustments.blacks.coerceIn(-1f, 1f)
        val saturation = (adjustments.saturation + stock.saturation).coerceIn(-1f, 1f)
        val warmth = (adjustments.warmth + stock.warmth).coerceIn(-1f, 1f)
        val fade = (adjustments.fade + stock.fade).coerceIn(0f, 1f)
        val shadowTint = (adjustments.shadowTint + stock.shadowTint).coerceIn(-1f, 1f)
        val highlightTint = (adjustments.highlightTint + stock.highlightTint).coerceIn(-1f, 1f)

        val gain = 2f.pow(exposure)
        val rg = gain * (1f + warmth * 0.18f)
        val gg = gain * (1f - kotlin.math.abs(warmth) * 0.02f)
        val bg = gain * (1f - warmth * 0.18f)
        val contrastScale = 1f + contrast * 0.9f
        val fadeScale = 1f - fade * 0.34f
        val fadeLift = fade * 23f
        val sr = shadowTint * 20f
        val sg = -shadowTint * 4f
        val sb = -shadowTint * 22f
        val hr = highlightTint * 24f
        val hg = highlightTint * 7f
        val hb = -highlightTint * 24f
        val splitRScale = 1f + (hr - sr) / 255f
        val splitGScale = 1f + (hg - sg) / 255f
        val splitBScale = 1f + (hb - sb) / 255f

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
        val toneLow = shadows * 32f + blacks * 28f
        val toneHigh = highlights * 32f + whites * 28f
        val toneSlope = 1f + (toneHigh - toneLow) / 255f
        val toneMatrix = floatArrayOf(
            toneSlope, 0f, 0f, 0f, toneLow,
            0f, toneSlope, 0f, 0f, toneLow,
            0f, 0f, toneSlope, 0f, toneLow,
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
        val splitMatrix = floatArrayOf(
            splitRScale, 0f, 0f, 0f, sr,
            0f, splitGScale, 0f, 0f, sg,
            0f, 0f, splitBScale, 0f, sb,
            0f, 0f, 0f, 1f, 0f
        )

        var result = identity()
        result = concat(result, gainMatrix)
        result = concat(result, contrastMatrix)
        result = concat(result, toneMatrix)
        result = concat(result, saturationMatrix)
        result = concat(result, fadeMatrix)
        result = concat(result, splitMatrix)
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

    /** Unsharp mask + highlight bloom + red-orange film halation on the baked pixel buffer. */
    private fun applyEnhancements(
        pixels: IntArray,
        w: Int,
        h: Int,
        sharpness: Float,
        bloom: Float,
        halation: Float
    ) {
        val r = FloatArray(w * h)
        val g = FloatArray(w * h)
        val b = FloatArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            r[i] = ((c shr 16) and 0xFF).toFloat()
            g[i] = ((c shr 8) and 0xFF).toFloat()
            b[i] = (c and 0xFF).toFloat()
        }
        val br = blurChannel(r, w, h, 2)
        val bg = blurChannel(g, w, h, 2)
        val bb = blurChannel(b, w, h, 2)
        val cr = blurChannel(br, w, h, 3)
        val cg = blurChannel(bg, w, h, 3)
        val cb = blurChannel(bb, w, h, 3)
        for (i in pixels.indices) {
            var ro = r[i]; var go = g[i]; var bo = b[i]
            if (sharpness > 0.01f) {
                val k = sharpness * 1.6f
                ro += k * (ro - br[i])
                go += k * (go - bg[i])
                bo += k * (bo - bb[i])
            } else if (sharpness < -0.01f) {
                val k = -sharpness * 0.7f
                ro = ro * (1f - k) + br[i] * k
                go = go * (1f - k) + bg[i] * k
                bo = bo * (1f - k) + bb[i] * k
            }
            if (bloom > 0.01f) {
                // Highlight-weighted soft glow: blurrier pass (cr/cg/cb) added under.
                val gl = (0.2126f * ro + 0.7152f * go + 0.0722f * bo) / 255f
                val weight = bloom * 0.42f * (gl * gl)
                ro += cr[i] * weight
                go += cg[i] * weight
                bo += cb[i] * weight
            }
            if (halation > 0.01f) {
                // Red-orange film base scatter around bright highlights.
                val hl = (0.2126f * cr[i] + 0.7152f * cg[i] + 0.0722f * cb[i]) / 255f
                val hw = halation * 0.56f * (hl * hl)
                ro += cr[i] * hw * 1.15f
                go += cg[i] * hw * 0.36f
                bo += cb[i] * hw * 0.06f
            }
            pixels[i] = (0xFF shl 24) or (ro.roundToInt().coerceIn(0, 255) shl 16) or
                (go.roundToInt().coerceIn(0, 255) shl 8) or bo.roundToInt().coerceIn(0, 255)
        }
    }

    private fun blurChannel(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val horiz = FloatArray(w * h)
        val win = (radius * 2 + 1).toFloat()
        for (y in 0 until h) {
            var sum = 0f
            for (i in -radius..radius) sum += src[y * w + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                horiz[y * w + x] = sum / win
                sum += src[y * w + (x + radius + 1).coerceIn(0, w - 1)] - src[y * w + (x - radius).coerceIn(0, w - 1)]
            }
        }
        val out = FloatArray(w * h)
        for (x in 0 until w) {
            var sum = 0f
            for (i in -radius..radius) sum += horiz[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / win
                sum += horiz[(y + radius + 1).coerceIn(0, h - 1) * w + x] - horiz[(y - radius).coerceIn(0, h - 1) * w + x]
            }
        }
        return out
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
