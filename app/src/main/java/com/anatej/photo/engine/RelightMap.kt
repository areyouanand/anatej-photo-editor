package com.anatej.photo.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.anatej.photo.model.PortraitLight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class RelightLayers(
    val light: Bitmap?,
    val shadow: Bitmap?
)

/** Per-pixel lit amounts shared by preview ([RelightMap.layers]) and export ([PhotoRenderer]). */
data class LightSample(
    val light: Float,
    val shade: Float,
    val spec: Float
)

/**
 * Estimates a surface normal map from the image's luminance height field and renders
 * directional portrait light from it.
 *
 * This is not a learned depth model: it treats luminance as a smooth height field, blurs
 * it, and derives normals from the gradients. Highlights and facial relief therefore
 * catch the key light directionally instead of receiving a flat radial wash. The subject
 * mask is stored in the normal map's alpha channel so both preview and export can cull
 * background lighting without a separate texture.
 */
object RelightMap {

    fun build(source: Bitmap, subjectMask: Bitmap?, maxDim: Int = 384): Bitmap {
        val sourceLongest = maxOf(source.width, source.height)
        val scale = min(1f, maxDim.toFloat() / sourceLongest.coerceAtLeast(1))
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)

        val scaledSource = if (source.width == w && source.height == h) {
            source
        } else {
            Bitmap.createScaledBitmap(source, w, h, true)
        }
        val scaledMask = subjectMask?.let {
            if (it.width == w && it.height == h) it else Bitmap.createScaledBitmap(it, w, h, true)
        }

        val pixels = IntArray(w * h)
        scaledSource.getPixels(pixels, 0, w, 0, 0, w, h)
        val maskPixels = scaledMask?.let {
            IntArray(w * h).also { mask -> it.getPixels(mask, 0, w, 0, 0, w, h) }
        }

        // sqrt() lifts shadow detail before differentiation, which gives dark portraits
        // usable facial relief instead of an almost-flat normal field.
        val luma = FloatArray(w * h) { index ->
            val c = pixels[index]
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            sqrt((0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f))
        }
        val blurred = blur5(luma, w, h)
        val maskAlpha = featherAlpha(maskPixels, w, h, radius = 16)
        val out = normalsFromHeight(blurred, w, h, maskAlpha, strength = 14f)

        if (scaledSource !== source) scaledSource.recycle()
        if (scaledMask != null && scaledMask !== subjectMask) scaledMask.recycle()

        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Builds a normal map from MiDaS inverse-depth. The model's 256x256 square field is
     * stretched back to the source aspect, heavily blurred, and differentiated. Gradient
     * clamping prevents hair/background depth discontinuities from producing halos.
     */
    fun buildFromDepth(
        source: Bitmap,
        depth: DepthEstimator.DepthMap,
        subjectMask: Bitmap?,
        maxDim: Int = 384
    ): Bitmap {
        val sourceLongest = maxOf(source.width, source.height)
        val scale = min(1f, maxDim.toFloat() / sourceLongest.coerceAtLeast(1))
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)

        val sorted = depth.values.copyOf()
        sorted.sort()
        val lo = sorted[(sorted.size * 0.02f).toInt().coerceIn(0, sorted.lastIndex)]
        val hi = sorted[(sorted.size * 0.98f).toInt().coerceIn(0, sorted.lastIndex)]
        val range = (hi - lo).takeIf { it > 0.00001f } ?: 1f

        val depthPixels = IntArray(depth.size * depth.size)
        for (i in depthPixels.indices) {
            val normalized = ((depth.values[i] - lo) / range).coerceIn(0f, 1f)
            val v = (normalized * 255f).roundToInt().coerceIn(0, 255)
            depthPixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val depthBitmap = Bitmap.createBitmap(
            depthPixels,
            depth.size,
            depth.size,
            Bitmap.Config.ARGB_8888
        )
        // Cover-fit the square depth field onto the source aspect (crop, don't stretch),
        // so depth geometry is not squeezed on 3:2 / 9:16 frames.
        val stretched = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val coverScale = maxOf(w.toFloat(), h.toFloat()) / depth.size.toFloat()
        val dw = depth.size * coverScale
        val dh = depth.size * coverScale
        val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        Canvas(stretched).drawBitmap(depthBitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        depthBitmap.recycle()

        val resizedPixels = IntArray(w * h)
        stretched.getPixels(resizedPixels, 0, w, 0, 0, w, h)
        stretched.recycle()
        val depthHeight = FloatArray(w * h) { index ->
            ((resizedPixels[index] shr 16) and 0xFF) / 255f
        }
        // One light blur keeps facial volume; luminance detail is re-mixed at low weight
        // so skin/eyes keep micro-relief without hair-edge halos.
        val blurredDepth = blur5(depthHeight, w, h)

        val scaledMask = subjectMask?.let {
            if (it.width == w && it.height == h) it else Bitmap.createScaledBitmap(it, w, h, true)
        }
        val maskPixels = scaledMask?.let {
            IntArray(w * h).also { mask -> it.getPixels(mask, 0, w, 0, 0, w, h) }
        }
        val maskAlpha = featherAlpha(maskPixels, w, h, radius = 16)
        val depthNormals = normalsFromHeight(blurredDepth, w, h, maskAlpha, strength = 26f)

        val sourcePixels = IntArray(w * h)
        val scaledSource = if (source.width == w && source.height == h) {
            source
        } else {
            Bitmap.createScaledBitmap(source, w, h, true)
        }
        scaledSource.getPixels(sourcePixels, 0, w, 0, 0, w, h)
        if (scaledSource !== source) scaledSource.recycle()
        val luma = FloatArray(w * h) { index ->
            val c = sourcePixels[index]
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            sqrt((0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f))
        }
        val lumaNormals = normalsFromHeight(blur5(blur5(luma, w, h), w, h), w, h, maskAlpha, strength = 6f)
        val out = mixNormals(depthNormals, lumaNormals, maskAlpha, depthWeight = 0.82f)

        if (scaledMask != null && scaledMask !== subjectMask) scaledMask.recycle()

        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun normalsFromHeight(
        height: FloatArray,
        w: Int,
        h: Int,
        maskAlpha: IntArray?,
        strength: Float
    ): IntArray {
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val left = height[y * w + maxOf(0, x - 1)]
                val right = height[y * w + minOf(w - 1, x + 1)]
                val top = height[maxOf(0, y - 1) * w + x]
                val bottom = height[minOf(h - 1, y + 1) * w + x]
                val topLeft = height[maxOf(0, y - 1) * w + maxOf(0, x - 1)]
                val topRight = height[maxOf(0, y - 1) * w + minOf(w - 1, x + 1)]
                val bottomLeft = height[minOf(h - 1, y + 1) * w + maxOf(0, x - 1)]
                val bottomRight = height[minOf(h - 1, y + 1) * w + minOf(w - 1, x + 1)]

                val gx = (topRight + 2f * right + bottomRight) -
                    (topLeft + 2f * left + bottomLeft)
                val gy = (bottomLeft + 2f * bottom + bottomRight) -
                    (topLeft + 2f * top + topRight)

                // Clamp the depth gradient so hair/background discontinuities cannot
                // produce extreme sideways normals and bright halos.
                val clampedGx = (gx * strength).coerceIn(-1.15f, 1.15f)
                val clampedGy = (gy * strength).coerceIn(-1.15f, 1.15f)
                var nx = -clampedGx
                var ny = -clampedGy
                var nz = 1f
                val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
                nx /= length
                ny /= length
                nz /= length

                val r = ((nx * 0.5f + 0.5f) * 255f).roundToInt().coerceIn(0, 255)
                val g = ((ny * 0.5f + 0.5f) * 255f).roundToInt().coerceIn(0, 255)
                val b = ((nz * 0.5f + 0.5f) * 255f).roundToInt().coerceIn(0, 255)
                val a = maskAlpha?.get(y * w + x) ?: 255
                out[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    /** Bipolar warmth: -1 is cool headlight-neutral, +1 is amber tungsten. */
    fun warmRgb(warmth: Float): Triple<Float, Float, Float> {
        val w = warmth.coerceIn(-1f, 1f)
        return if (w >= 0f) {
            Triple(1f, 1f - w * 0.20f, 1f - w * 0.45f)
        } else {
            val a = -w
            Triple(1f - a * 0.15f, 1f - a * 0.03f, 1f)
        }
    }

    private fun mixNormals(
        depthNormals: IntArray,
        lumaNormals: IntArray,
        maskAlpha: IntArray?,
        depthWeight: Float
    ): IntArray {
        val out = IntArray(depthNormals.size)
        val lumaWeight = 1f - depthWeight
        for (i in out.indices) {
            val dn = depthNormals[i]
            val ln = lumaNormals[i]
            var nx = (((dn shr 16) and 0xFF) / 127.5f - 1f) * depthWeight +
                (((ln shr 16) and 0xFF) / 127.5f - 1f) * lumaWeight
            var ny = (((dn shr 8) and 0xFF) / 127.5f - 1f) * depthWeight +
                (((ln shr 8) and 0xFF) / 127.5f - 1f) * lumaWeight
            var nz = ((dn and 0xFF) / 127.5f - 1f) * depthWeight +
                ((ln and 0xFF) / 127.5f - 1f) * lumaWeight
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
            nx /= len; ny /= len; nz /= len
            val r = ((nx * 0.5f + 0.5f) * 255f).roundToInt().coerceIn(0, 255)
            val g = ((ny * 0.5f + 0.5f) * 255f).roundToInt().coerceIn(0, 255)
            val b = ((nz * 0.5f + 0.5f) * 255f).roundToInt().coerceIn(0, 255)
            val a = maskAlpha?.get(i) ?: 255
            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    /**
     * Shared lighting model for preview and export. [dx]/[dy] are pixel-to-spot offsets
     * already divided by minDim; [nx]/[ny]/[nz] is the surface normal; [mask] is 0..1.
     * Returns diffuse+fill+spec light, shadow, and spec amounts (all 0..1).
     */
    fun sampleLight(
        nx: Float, ny: Float, nz: Float,
        mask: Float,
        dx: Float, dy: Float,
        light: PortraitLight
    ): LightSample {
        val radius = light.radius.coerceIn(0.08f, 1.6f)
        // Intensity is displayed 0..100; the top (100) equals the old 30% mark,
        // so even at max the relight stays gentle instead of blowing out.
        val intensity = ((light.intensity / 100f) * 0.30f).coerceIn(0f, 1f)
        val softness = light.softness.coerceIn(0f, 1f)
        val fill = light.fill.coerceIn(0f, 1f)
        val dist = sqrt(dx * dx + dy * dy)

        // Softbox falloff: 1 at the centre, tightens with hardness, no long hazy tail.
        val exponent = 2.2f + (1f - softness) * 4.0f
        val ratio = dist / radius
        val falloff = 1f / (1f + (ratio * ratio).pow(exponent * 0.5f))
        if (falloff <= 0.003f) return LightSample(0f, 0f, 0f)

        // Directional key from azimuth/elevation, blended with the positional pull
        // toward the spot so dragging the spot still moves the highlight.
        val el = Math.toRadians(light.elevation.coerceIn(0f, 90f).toDouble()).toFloat()
        val az = Math.toRadians(light.azimuth.coerceIn(-90f, 90f).toDouble()).toFloat()
        val dX = sin(az) * cos(el); val dY = -sin(el); val dZ = cos(el)
        val pLen = sqrt(dx * dx + dy * dy + 0.64f).coerceAtLeast(0.0001f)
        val pX = dx / pLen; val pY = dy / pLen; val pZ = 0.8f / pLen
        var lx = dX * 0.72f + pX * 0.48f
        var ly = dY * 0.72f + pY * 0.48f
        var lz = dZ * 0.72f + pZ * 0.48f
        val lLen = sqrt(lx * lx + ly * ly + lz * lz).coerceAtLeast(0.0001f)
        lx /= lLen; ly /= lLen; lz /= lLen

        val ndlRaw = nx * lx + ny * ly + nz * lz
        // Wrap diffuse for skin translucency; 0.35 keeps the shadow side from going dead black.
        run {
            val wrapAmt = 0.35f
            val wrap = ((ndlRaw + wrapAmt) / (1f + wrapAmt)).coerceIn(0f, 1f)
            val shaped = wrap.pow(1.4f)

            // Filmic intensity: 10% is now almost imperceptible (instead of a visible
            // veil), and the final soft clip rolls off highlights rather than clipping
            // the face to a flat white blob at high intensity.
            val eff = (intensity.pow(1.6f) * 1.2f).coerceIn(0f, 1.6f)

            val spill = light.spill.coerceIn(0f, 1f)
            val m = if (light.subjectOnly) spill + (1f - spill) * mask else 1f

            val diffuse = shaped * eff * falloff * m
            // Opposing fill lift, strongest on the shadow side.
            val fillAmt = eff * falloff * m * fill * 0.35f * (1f - wrap * 0.5f)

            // Tight specular on skin only (never into the background spill).
            var hx = lx; var hy = ly; var hz = lz + 1f
            val hLen = sqrt(hx * hx + hy * hy + hz * hz).coerceAtLeast(0.0001f)
            hx /= hLen; hy /= hLen; hz /= hLen
            val ndh = (nx * hx + ny * hy + nz * hz).coerceAtLeast(0f)
            val spec = ndh.pow(28f) * 0.18f * eff * falloff * mask

            val shadowStrength = 0.32f * (1f - fill * 0.6f)
            val shade = (1f - wrap).pow(1.3f) * eff * falloff * mask * shadowStrength

            val raw = diffuse + fillAmt + spec
            val softLit = raw / (1f + raw * 0.30f)
            return LightSample(
                light = softLit.coerceIn(0f, 1f),
                shade = shade.coerceIn(0f, 1f),
                spec = 0f
            )
        }
    }

    /**
     * Renders an additive warm-light layer (BlendMode.Screen) and a multiplicative shadow
     * layer (BlendMode.Multiply), both sampled from [normals].
     */
    fun layers(normals: Bitmap, light: PortraitLight): RelightLayers? {
        if (!light.enabled || light.intensity <= 0.001f) return null
        val w = normals.width
        val h = normals.height
        if (w == 0 || h == 0) return null

        val pixels = IntArray(w * h)
        normals.getPixels(pixels, 0, w, 0, 0, w, h)
        val lightOut = IntArray(w * h)
        val shadowOut = IntArray(w * h)

        val minDim = min(w, h).toFloat().coerceAtLeast(1f)
        val lx = light.x * w
        val ly = light.y * h

        val (warmR, warmG, warmB) = warmRgb(light.warmth).let { rgb ->
            Triple(
                (rgb.first * 255f).roundToInt().coerceIn(0, 255),
                (rgb.second * 255f).roundToInt().coerceIn(0, 255),
                (rgb.third * 255f).roundToInt().coerceIn(0, 255)
            )
        }

        for (y in 0 until h) {
            val py = y + 0.5f
            for (x in 0 until w) {
                val index = y * w + x
                val n = pixels[index]
                val mask = ((n ushr 24) and 0xFF) / 255f

                val nx = (((n shr 16) and 0xFF) / 127.5f) - 1f
                val ny = (((n shr 8) and 0xFF) / 127.5f) - 1f
                val nz = ((n and 0xFF) / 127.5f) - 1f

                val dx = (lx - (x + 0.5f)) / minDim
                val dy = (ly - py) / minDim
                val sample = sampleLight(nx, ny, nz, mask, dx, dy, light)
                if (sample.light <= 0.002f && sample.shade <= 0.002f && sample.spec <= 0.002f) continue

                val amount = (sample.light + sample.spec).coerceIn(0f, 1f)
                val alpha = (amount * 255f).roundToInt()
                if (alpha > 0) {
                    lightOut[index] = (alpha shl 24) or (warmR shl 16) or (warmG shl 8) or warmB
                }

                val shadeAlpha = (sample.shade * 255f).roundToInt()
                if (shadeAlpha > 0) {
                    shadowOut[index] = shadeAlpha shl 24
                }
            }
        }

        return RelightLayers(
            light = Bitmap.createBitmap(lightOut, w, h, Bitmap.Config.ARGB_8888),
            shadow = Bitmap.createBitmap(shadowOut, w, h, Bitmap.Config.ARGB_8888)
        )
    }

    private fun featherAlpha(maskPixels: IntArray?, w: Int, h: Int, radius: Int): IntArray? {
        if (maskPixels == null) return null
        val src = FloatArray(w * h) {
            ((maskPixels[it] ushr 24) and 0xFF).toFloat()
        }
        val horizontal = FloatArray(w * h)
        val window = radius * 2 + 1
        for (y in 0 until h) {
            var sum = 0f
            for (i in -radius..radius) {
                sum += src[y * w + i.coerceIn(0, w - 1)]
            }
            for (x in 0 until w) {
                horizontal[y * w + x] = sum / window
                sum += src[y * w + (x + radius + 1).coerceIn(0, w - 1)] -
                    src[y * w + (x - radius).coerceIn(0, w - 1)]
            }
        }
        val out = IntArray(w * h)
        for (x in 0 until w) {
            var sum = 0f
            for (i in -radius..radius) {
                sum += horizontal[i.coerceIn(0, h - 1) * w + x]
            }
            for (y in 0 until h) {
                out[y * w + x] = (sum / window).roundToInt().coerceIn(0, 255)
                sum += horizontal[(y + radius + 1).coerceIn(0, h - 1) * w + x] -
                    horizontal[(y - radius).coerceIn(0, h - 1) * w + x]
            }
        }
        return out
    }

    private fun blur5(source: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (dy in -2..2) {
                    val yy = (y + dy).coerceIn(0, h - 1)
                    for (dx in -2..2) {
                        val xx = (x + dx).coerceIn(0, w - 1)
                        sum += source[yy * w + xx]
                    }
                }
                out[y * w + x] = sum / 25f
            }
        }
        return out
    }
}
