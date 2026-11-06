package com.anatej.photo.model

import android.graphics.Bitmap
import java.util.Locale

/**
 * A complete, self-contained description of the edit that is currently applied.
 * The CPU export renderer and the GPU preview both consume the same numbers, so the
 * saved file matches what is on screen.
 */
data class Adjustments(
    val exposure: Float = 0f,      // EV, -2..+2
    val contrast: Float = 0f,      // -1..+1
    val saturation: Float = 0f,    // -1..+1
    val warmth: Float = 0f,        // -1 (cool) .. +1 (warm)
    val fade: Float = 0f,          // 0..1 lifted blacks
    val vignette: Float = 0f,      // 0..1
    val grain: Float = 0f,         // 0..1
    val sharpness: Float = 0f,     // -1..+1 unsharp mask
    val bloom: Float = 0f          // 0..1 soft highlight glow
) {
    val isNeutral: Boolean
        get() = exposure == 0f && contrast == 0f && saturation == 0f &&
            warmth == 0f && fade == 0f && vignette == 0f && grain == 0f &&
            sharpness == 0f && bloom == 0f

    companion object {
        val Neutral = Adjustments()
    }
}

enum class LightStyle { Softbox, Flashlight }

/**
 * A single portrait light. x/y is the spot centre on the photo (0..1, y down);
 * azimuth/elevation control the light direction, softness the softbox falloff,
 * fill the shadow lift, and spill the light that reaches the background.
 * intensity is displayed 0..100, but the top of the scale (100) only reaches
 * the strength of the old 30% mark — the strongest useful relight, not 100%.
 */
data class PortraitLight(
    val enabled: Boolean = false,
    val x: Float = 0.5f,
    val y: Float = 0.38f,
    val intensity: Float = 42f,
    val radius: Float = 0.6f,
    val warmth: Float = 0.30f,
    val subjectOnly: Boolean = true,
    val azimuth: Float = 30f,
    val elevation: Float = 35f,
    val softness: Float = 0.5f,
    val fill: Float = 0.22f,
    val spill: Float = 0.05f,
    val style: LightStyle = LightStyle.Flashlight
)

/** Crop-to-aspect settings for profile-style exports (1:1 square, stories, ...). */
data class CropConfig(
    val aspect: Float = 0f,     // 0 = off, else width/height (1f = square)
    val zoom: Float = 1f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f
)

enum class LookSeries(val label: String, val ticker: String) {
    Basic("Basic", "CORE"),
    Fujifilm("Fujifilm", "FUJI"),
    Nikon("Nikon", "NIKON"),
    Hasselblad("Hasselblad", "HASSEL")
}

/**
 * Parameterised film look. Real camera profiles are 3D LUTs; this bringup uses a
 * tuned tone/colour recipe per stock that is cheap enough to run offline on any phone.
 * A LUT importer can replace [toAdjustments] later without changing the UI contract.
 */
data class Look(
    val id: String,
    val name: String,
    val series: LookSeries,
    val note: String,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val sharpness: Float = 0f,
    val bloom: Float = 0f
) {
    fun scaled(strength: Float): Look {
        val s = strength.coerceIn(0f, 1f)
        return copy(
            exposure = exposure * s,
            contrast = contrast * s,
            saturation = saturation * s,
            warmth = warmth * s,
            fade = fade * s,
            vignette = vignette * s,
            grain = grain * s,
            sharpness = sharpness * s,
            bloom = bloom * s
        )
    }

    fun toAdjustments(strength: Float = 1f): Adjustments {
        val l = scaled(strength)
        return Adjustments(
            exposure = l.exposure,
            contrast = l.contrast,
            saturation = l.saturation,
            warmth = l.warmth,
            fade = l.fade,
            vignette = l.vignette,
            grain = l.grain,
            sharpness = l.sharpness,
            bloom = l.bloom
        )
    }
}

object Looks {
    val Original = Look(
        id = "core.original",
        name = "Original",
        series = LookSeries.Basic,
        note = "The file as the sensor saw it."
    )

    val all: List<Look> = listOf(
        Original,
        // Basic
        Look("core.vivid", "Vivid", LookSeries.Basic, "Digital punch: strong colour, crisp edges.", saturation = 0.34f, contrast = 0.16f, sharpness = 0.30f, vignette = 0.06f),
        Look("core.soft", "Soft", LookSeries.Basic, "Airy highlights, gentle roll-off.", contrast = -0.16f, fade = 0.16f, warmth = 0.08f, bloom = 0.18f, sharpness = -0.12f),
        Look("core.mono", "Mono", LookSeries.Basic, "Silver print: neutral, micro-contrast.", saturation = -1f, contrast = 0.18f, sharpness = 0.22f, grain = 0.06f),
        Look("core.noir", "Noir", LookSeries.Basic, "Deep blacks, hard contrast, heavy falloff.", saturation = -1f, contrast = 0.42f, sharpness = 0.20f, vignette = 0.34f, grain = 0.20f),
        Look("core.fade", "Fade", LookSeries.Basic, "Matte lifted blacks, halation bloom.", contrast = -0.14f, saturation = -0.10f, fade = 0.46f, bloom = 0.28f, grain = 0.10f, sharpness = -0.10f),

        // Fujifilm stocks — colour science, tone curve, grain/sharp character
        Look("fuji.provia", "PROVIA", LookSeries.Fujifilm, "Balanced transparency. Clean detail.", saturation = 0.10f, contrast = 0.08f, warmth = -0.02f, sharpness = 0.12f, vignette = 0.05f, bloom = 0.04f),
        Look("fuji.velvia", "VELVIA", LookSeries.Fujifilm, "Saturated landscape dye. Deep skies.", saturation = 0.44f, contrast = 0.26f, warmth = 0.05f, sharpness = 0.38f, vignette = 0.14f, bloom = 0.03f),
        Look("fuji.chrome", "CLASSIC CHROME", LookSeries.Fujifilm, "Muted documentary colour, film grain.", saturation = -0.24f, contrast = 0.14f, warmth = -0.08f, fade = 0.08f, grain = 0.10f, sharpness = 0.15f),
        Look("fuji.neg", "CLASSIC NEG", LookSeries.Fujifilm, "Warm shadows, halation, editorial skin.", saturation = -0.08f, contrast = -0.02f, warmth = 0.16f, fade = 0.22f, grain = 0.14f, bloom = 0.16f, sharpness = -0.05f),
        Look("fuji.astia", "ASTIA", LookSeries.Fujifilm, "Soft portrait slide. Flattering skin.", saturation = 0.08f, contrast = -0.04f, warmth = 0.10f, fade = 0.05f, bloom = 0.10f, sharpness = -0.06f),
        Look("fuji.acros", "ACROS", LookSeries.Fujifilm, "Fine-grain monochrome emulsion.", saturation = -1f, contrast = 0.28f, grain = 0.18f, sharpness = 0.32f, vignette = 0.10f, bloom = 0.05f),
        Look("fuji.rt", "REALA ACE", LookSeries.Fujifilm, "Modern neg: accurate, gentle highlights.", saturation = 0.02f, contrast = 0.02f, warmth = 0.02f, fade = 0.04f, bloom = 0.06f, sharpness = 0.05f),

        // Nikon picture controls — clean, low-grain, digital detail
        Look("nikon.standard", "NIKON STANDARD", LookSeries.Nikon, "Clean, slightly cool rendering.", saturation = 0.04f, contrast = 0.14f, warmth = -0.03f, sharpness = 0.14f, vignette = 0.04f),
        Look("nikon.neutral", "NEUTRAL", LookSeries.Nikon, "Flat starting point for grading.", saturation = -0.08f, contrast = 0.02f, sharpness = 0.05f),
        Look("nikon.vivid", "VIVID", LookSeries.Nikon, "Hard colour, confident contrast.", saturation = 0.32f, contrast = 0.22f, warmth = -0.04f, sharpness = 0.34f, vignette = 0.08f),
        Look("nikon.mono", "MONOCHROME", LookSeries.Nikon, "Crisp reportage black and white.", saturation = -1f, contrast = 0.20f, sharpness = 0.28f, grain = 0.05f),
        Look("nikon.warm", "NIKKOR WARM", LookSeries.Nikon, "Golden-hour glass. Warm flare.", warmth = 0.18f, saturation = 0.08f, contrast = 0.06f, bloom = 0.12f, sharpness = 0.08f),
        Look("nikon.flat", "FLAT", LookSeries.Nikon, "Low contrast, lifted shadows for RAW edits.", contrast = -0.18f, saturation = -0.12f, fade = 0.14f, sharpness = 0.05f),

        // Hasselblad natural colour — medium format, luminous, minimal grain
        Look("hasselblad.natural", "NATURAL", LookSeries.Hasselblad, "Hasselblad Natural Colour.", contrast = 0.04f, saturation = 0.03f, warmth = 0.06f, bloom = 0.08f, sharpness = 0.06f),
        Look("hasselblad.portrait", "PORTRAIT", LookSeries.Hasselblad, "Quiet contrast, luminous skin.", saturation = -0.04f, contrast = -0.05f, warmth = 0.13f, fade = 0.05f, bloom = 0.15f, sharpness = -0.07f),
        Look("hasselblad.landscape", "LANDSCAPE", LookSeries.Hasselblad, "Open shadows, rich foliage.", saturation = 0.18f, contrast = 0.10f, warmth = 0.04f, bloom = 0.05f, sharpness = 0.14f),
        Look("hasselblad.bw", "B&W", LookSeries.Hasselblad, "Medium-format monochrome.", saturation = -1f, contrast = 0.22f, grain = 0.07f, sharpness = 0.16f),
        Look("hasselblad.skin", "SKIN TONE", LookSeries.Hasselblad, "Warm, lifted, flattering.", warmth = 0.22f, fade = 0.09f, contrast = -0.08f, bloom = 0.14f, sharpness = -0.05f),
        Look("hasselblad.x2d", "X2D", LookSeries.Hasselblad, "X2D flagship: surgical detail.", contrast = 0.06f, saturation = 0.05f, warmth = 0.03f, sharpness = 0.24f, vignette = 0.06f)
    )

    val byId: Map<String, Look> = all.associateBy { it.id }

    fun forSeries(series: LookSeries): List<Look> = all.filter { it.series == series }
}

/**
 * Result of the on-device scan. Everything here is derived from the pixels themselves:
 * a luminance histogram plus a skin-tone weighted centroid that acts as a lightweight
 * subject detector. No model download, no network.
 */
data class PhotoAnalysis(
    val meanLuma: Float,
    val medianLuma: Float,
    val p05: Float,
    val p95: Float,
    val darkFraction: Float,
    val clippedFraction: Float,
    val subjectX: Float,
    val subjectY: Float,
    val subjectConfidence: Float,
    val subjectLuma: Float,
    val skinFraction: Float,
    val suggestedExposure: Float,
    val suggestedContrast: Float,
    val suggestedSaturation: Float
) {
    val dynamicRange: Float get() = (p95 - p05).coerceIn(0f, 1f)
    val isDark: Boolean get() = medianLuma < 0.38f || darkFraction > 0.42f
    val isBright: Boolean get() = medianLuma > 0.72f || clippedFraction > 0.18f

    val verdict: String
        get() = when {
            isDark && dynamicRange < 0.45f -> "Underexposed, flat shadows"
            isDark -> "Underexposed frame"
            isBright -> "High-key, clipped highlights"
            dynamicRange < 0.35f -> "Low-contrast frame"
            else -> "Balanced exposure"
        }

    val suggestion: String
        get() = buildString {
            if (suggestedExposure > 0.05f) {
                append("Lift ")
                append(String.format(Locale.US, "%+.1f", suggestedExposure))
                append(" EV")
            } else if (suggestedExposure < -0.05f) {
                append("Pull ")
                append(String.format(Locale.US, "%+.1f", suggestedExposure))
                append(" EV")
            } else {
                append("Hold exposure")
            }
            if (subjectConfidence >= 0.25f) {
                append(" · subject at ")
                append((subjectX * 100).toInt())
                append("%, ")
                append((subjectY * 100).toInt())
                append("%")
            } else {
                append(" · no clear subject")
            }
        }
}

data class EditorUiState(
    val source: Bitmap? = null,
    val photoName: String = "Untitled frame",
    val analysis: PhotoAnalysis? = null,
    val adjustments: Adjustments = Adjustments.Neutral,
    val look: Look = Looks.Original,
    val lookStrength: Float = 1f,
    val light: PortraitLight = PortraitLight(),
    val lut: Lut3D? = null,
    val lutName: String? = null,
    val lutStrength: Float = 1f,
    val lutPreview: Bitmap? = null,
    val subjectMask: Bitmap? = null,
    val relightNormals: Bitmap? = null,
    val relightOverlay: Bitmap? = null,
    val relightShadow: Bitmap? = null,
    val bloomOverlay: Bitmap? = null,
    val autoRescueStrength: Float = 1f,
    val cropAspect: Float = 0f,
    val cropZoom: Float = 1f,
    val cropCenterX: Float = 0.5f,
    val cropCenterY: Float = 0.5f,
    val squarePic: Boolean = false,
    val maskScanning: Boolean = false,
    val thumbs: Map<String, Bitmap> = emptyMap(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val compare: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    val isEdited: Boolean
        get() = !adjustments.isNeutral || look.id != Looks.Original.id ||
            light.enabled || lut != null
}
