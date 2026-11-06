package com.anatej.photo.model

import android.graphics.Bitmap
import java.util.Locale

/**
 * A complete, self-contained description of the edit that is currently applied.
 * The CPU export renderer and the GPU preview both consume the same numbers, so the
 * saved file matches what is on screen.
 */
data class Adjustments(
    val exposure: Float = 0f,       // EV, -2..+2
    val contrast: Float = 0f,       // -1..+1
    val highlights: Float = 0f,     // -1..+1 highlight recovery / boost
    val shadows: Float = 0f,        // -1..+1 shadow lift / deepen
    val whites: Float = 0f,         // -1..+1 white point
    val blacks: Float = 0f,         // -1..+1 black point
    val saturation: Float = 0f,     // -1..+1
    val warmth: Float = 0f,         // -1 (cool) .. +1 (warm)
    val fade: Float = 0f,           // 0..1 lifted blacks
    val vignette: Float = 0f,       // 0..1
    val grain: Float = 0f,          // 0..1
    val sharpness: Float = 0f,      // -1..+1 unsharp mask
    val bloom: Float = 0f,          // 0..1 soft highlight glow
    val halation: Float = 0f,       // 0..1 red-orange film halation on highlights
    val shadowTint: Float = 0f,     // -1 (teal/cyan shadows) .. +1 (warm amber shadows)
    val highlightTint: Float = 0f   // -1 (cool blue/magenta highlights) .. +1 (warm gold highlights)
) {
    val isNeutral: Boolean
        get() = exposure == 0f && contrast == 0f && highlights == 0f && shadows == 0f &&
            whites == 0f && blacks == 0f && saturation == 0f &&
            warmth == 0f && fade == 0f && vignette == 0f && grain == 0f &&
            sharpness == 0f && bloom == 0f && halation == 0f &&
            shadowTint == 0f && highlightTint == 0f

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

/**
 * Normalized non-destructive crop box (0..1 in source photo coordinates).
 * [aspectLock] is 0f for Freeform, -1f for Original photo ratio, or W/H (> 0f).
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val aspectLock: Float = 0f,
    val straighten: Float = 0f
) {
    val width: Float get() = (right - left).coerceAtLeast(0.05f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.05f)
    val isCropped: Boolean
        get() = left > 0.002f || top > 0.002f || right < 0.998f || bottom < 0.998f ||
            kotlin.math.abs(straighten) > 0.05f

    companion object {
        val Full = CropRect()
    }
}

enum class SquareBgStyle(val label: String) {
    Blur("BLUR"),
    Black("BLACK"),
    White("WHITE")
}

data class LutFileEntry(
    val id: String,
    val name: String,
    val path: String
)

enum class LookSeries(val label: String, val ticker: String) {
    Favourites("Favourites", "STAR"),
    Cinema("Cinema", "CINE"),
    Fujifilm("Fujifilm", "FUJI"),
    Kodak("Kodak", "FILM"),
    Basic("Basic", "CORE"),
    Nikon("Nikon & Leica", "OPTIC"),
    Hasselblad("Hasselblad", "HNCS"),
    Lut("3D LUT", "CUBE")
}

/**
 * Parameterised film & cinema look combining tone curve, colour science, split-toning,
 * highlight bloom, red-orange halation, film grain, optical vignette, and micro-contrast.
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
    val bloom: Float = 0f,
    val halation: Float = 0f,
    val shadowTint: Float = 0f,
    val highlightTint: Float = 0f
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
            bloom = bloom * s,
            halation = halation * s,
            shadowTint = shadowTint * s,
            highlightTint = highlightTint * s
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
            bloom = l.bloom,
            halation = l.halation,
            shadowTint = l.shadowTint,
            highlightTint = l.highlightTint
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
        // Cinema stocks — multi-layered split-toning, halation, bloom, grain & vignette
        Look("cine.2383", "KODAK 2383", LookSeries.Cinema, "Cinema print stock: teal shadows, warm gold highs, fine grain.", contrast = 0.22f, saturation = 0.08f, warmth = 0.06f, fade = 0.06f, vignette = 0.22f, grain = 0.14f, sharpness = 0.12f, bloom = 0.18f, halation = 0.22f, shadowTint = -0.38f, highlightTint = 0.34f),
        Look("cine.800t", "800T NIGHT", LookSeries.Cinema, "Tungsten night stock: cool shadows, red-orange halation glow.", exposure = 0.06f, contrast = 0.14f, saturation = 0.04f, warmth = -0.14f, fade = 0.10f, vignette = 0.28f, grain = 0.24f, sharpness = -0.06f, bloom = 0.32f, halation = 0.52f, shadowTint = -0.45f, highlightTint = 0.28f),
        Look("cine.tealorange", "BLOCKBUSTER", LookSeries.Cinema, "Teal shadow split, golden skin tones, anamorphic vignette.", contrast = 0.24f, saturation = 0.14f, warmth = 0.08f, vignette = 0.26f, grain = 0.10f, sharpness = 0.22f, bloom = 0.14f, shadowTint = -0.52f, highlightTint = 0.42f),
        Look("cine.promist", "PRO-MIST 1/4", LookSeries.Cinema, "Black diffusion glass: creamy highlight bloom, lifted blacks.", contrast = -0.12f, saturation = -0.04f, warmth = 0.10f, fade = 0.24f, vignette = 0.14f, grain = 0.12f, sharpness = -0.24f, bloom = 0.48f, halation = 0.20f, highlightTint = 0.22f),
        Look("cine.bleach", "BLEACH BYPASS", LookSeries.Cinema, "Silver retention: metallic grit, crushed blacks, heavy grain.", contrast = 0.38f, saturation = -0.48f, warmth = -0.06f, vignette = 0.34f, grain = 0.28f, sharpness = 0.36f, bloom = 0.10f, shadowTint = -0.20f),
        Look("cine.a24", "INDIE 35MM", LookSeries.Cinema, "Muted editorial palette, matte shadows, organic 35mm grain.", contrast = -0.06f, saturation = -0.16f, warmth = 0.12f, fade = 0.28f, vignette = 0.18f, grain = 0.20f, sharpness = -0.08f, bloom = 0.24f, halation = 0.18f, shadowTint = -0.18f, highlightTint = 0.26f),
        Look("cine.noir", "SIN CITY NOIR", LookSeries.Cinema, "High-contrast silver B&W with glowing mist highlights.", saturation = -1f, contrast = 0.44f, fade = 0.06f, vignette = 0.38f, grain = 0.26f, sharpness = 0.28f, bloom = 0.28f),

        // Fujifilm film simulations
        Look("fuji.provia", "PROVIA", LookSeries.Fujifilm, "Standard slide: balanced transparency, crisp micro-contrast.", saturation = 0.12f, contrast = 0.10f, warmth = -0.02f, sharpness = 0.16f, vignette = 0.08f, bloom = 0.06f, grain = 0.05f),
        Look("fuji.velvia", "VELVIA 50", LookSeries.Fujifilm, "Vivid reversal dye: deep shadows, lush greens & skies.", saturation = 0.46f, contrast = 0.28f, warmth = 0.06f, sharpness = 0.34f, vignette = 0.18f, bloom = 0.08f, shadowTint = -0.14f, highlightTint = 0.12f),
        Look("fuji.chrome", "CLASSIC CHROME", LookSeries.Fujifilm, "Kodachrome-inspired: muted colour, cool shadows, crisp grain.", saturation = -0.26f, contrast = 0.18f, warmth = -0.06f, fade = 0.12f, grain = 0.14f, sharpness = 0.18f, vignette = 0.14f, shadowTint = -0.22f, highlightTint = 0.14f),
        Look("fuji.neg", "CLASSIC NEG", LookSeries.Fujifilm, "Superia street stock: amber highs, cool shadows, halation.", saturation = -0.06f, contrast = 0.08f, warmth = 0.14f, fade = 0.20f, grain = 0.18f, bloom = 0.20f, halation = 0.24f, sharpness = -0.04f, shadowTint = -0.26f, highlightTint = 0.30f),
        Look("fuji.nostalgic", "NOSTALGIC NEG", LookSeries.Fujifilm, "New American Color: rich amber highlights, deep shadow tone.", saturation = 0.06f, contrast = 0.04f, warmth = 0.22f, fade = 0.14f, grain = 0.16f, bloom = 0.24f, halation = 0.18f, vignette = 0.16f, shadowTint = 0.18f, highlightTint = 0.36f),
        Look("fuji.eterna", "ETERNA CINE", LookSeries.Fujifilm, "Motion picture negative: wide latitude, soft shadows, bloom.", saturation = -0.22f, contrast = -0.14f, warmth = -0.04f, fade = 0.24f, grain = 0.12f, bloom = 0.22f, vignette = 0.12f, shadowTint = -0.24f, highlightTint = 0.12f),
        Look("fuji.astia", "ASTIA SOFT", LookSeries.Fujifilm, "Portrait slide: silky skin roll-off, gentle highlight glow.", saturation = 0.10f, contrast = -0.06f, warmth = 0.10f, fade = 0.08f, bloom = 0.18f, sharpness = -0.08f, vignette = 0.08f, highlightTint = 0.16f),
        Look("fuji.acros", "ACROS 100", LookSeries.Fujifilm, "Fine-grain monochrome with rich shadow gradation.", saturation = -1f, contrast = 0.30f, grain = 0.20f, sharpness = 0.32f, vignette = 0.16f, bloom = 0.08f),

        // Kodak & Analog stocks
        Look("kodak.portra400", "PORTRA 400", LookSeries.Kodak, "Warm golden skin tones, airy highlights, organic grain.", exposure = 0.05f, contrast = -0.04f, saturation = 0.06f, warmth = 0.18f, fade = 0.14f, vignette = 0.12f, grain = 0.16f, bloom = 0.22f, halation = 0.16f, sharpness = -0.06f, shadowTint = 0.10f, highlightTint = 0.28f),
        Look("kodak.gold200", "GOLD 200", LookSeries.Kodak, "Sun-drenched nostalgia: warm cast, glowing highlights, grain.", exposure = 0.04f, contrast = 0.06f, saturation = 0.16f, warmth = 0.26f, fade = 0.16f, vignette = 0.18f, grain = 0.22f, bloom = 0.26f, halation = 0.24f, highlightTint = 0.36f),
        Look("kodak.ektar100", "EKTAR 100", LookSeries.Kodak, "Ultra-fine grain negative: punchy reds & blues, high clarity.", contrast = 0.22f, saturation = 0.34f, warmth = 0.08f, vignette = 0.14f, grain = 0.08f, sharpness = 0.24f, bloom = 0.08f, shadowTint = -0.16f, highlightTint = 0.20f),
        Look("kodak.trix400", "TRI-X 400", LookSeries.Kodak, "Iconic reportage B&W: gritty silver grain, deep blacks.", saturation = -1f, contrast = 0.36f, fade = 0.08f, vignette = 0.24f, grain = 0.32f, sharpness = 0.28f, bloom = 0.10f),
        Look("kodak.hp5", "ILFORD HP5", LookSeries.Kodak, "Classic British B&W: soft midtones, lifted matte shadows.", saturation = -1f, contrast = 0.14f, fade = 0.22f, vignette = 0.16f, grain = 0.26f, sharpness = 0.12f, bloom = 0.14f),

        // Basic looks
        Original,
        Look("core.vivid", "Vivid", LookSeries.Basic, "Digital punch: strong colour, crisp edges, subtle vignette.", saturation = 0.34f, contrast = 0.16f, sharpness = 0.30f, vignette = 0.10f, bloom = 0.06f),
        Look("core.soft", "Soft Glow", LookSeries.Basic, "Airy highlights, gentle roll-off, dreamy bloom.", contrast = -0.16f, fade = 0.18f, warmth = 0.10f, bloom = 0.28f, sharpness = -0.14f, highlightTint = 0.16f),
        Look("core.golden", "Golden Hour", LookSeries.Basic, "Late-afternoon sun: amber flare, warm shadows, soft bloom.", exposure = 0.04f, contrast = 0.06f, saturation = 0.14f, warmth = 0.28f, fade = 0.10f, vignette = 0.16f, bloom = 0.26f, halation = 0.20f, shadowTint = 0.16f, highlightTint = 0.34f),
        Look("core.mono", "Silver Mono", LookSeries.Basic, "Neutral silver print with micro-contrast and fine grain.", saturation = -1f, contrast = 0.20f, sharpness = 0.24f, grain = 0.10f, vignette = 0.12f),
        Look("core.fade", "Matte Fade", LookSeries.Basic, "Lifted smoky blacks, halation bloom, vintage grain.", contrast = -0.14f, saturation = -0.12f, fade = 0.44f, bloom = 0.28f, halation = 0.18f, grain = 0.14f, sharpness = -0.10f),

        // Nikon & Leica optical profiles
        Look("nikon.standard", "NIKON STD", LookSeries.Nikon, "Clean optical rendering, natural contrast, crisp edges.", saturation = 0.06f, contrast = 0.14f, warmth = -0.03f, sharpness = 0.18f, vignette = 0.06f),
        Look("nikon.vivid", "NIKON VIVID", LookSeries.Nikon, "Hard colour, confident contrast, micro-sharpness.", saturation = 0.32f, contrast = 0.22f, warmth = -0.04f, sharpness = 0.34f, vignette = 0.12f),
        Look("nikon.warm", "NIKKOR GOLD", LookSeries.Nikon, "Vintage Nikkor coating: warm flare, soft highlight bloom.", warmth = 0.20f, saturation = 0.10f, contrast = 0.08f, bloom = 0.22f, halation = 0.14f, vignette = 0.14f, sharpness = 0.10f, highlightTint = 0.24f),
        Look("leica.chrome", "LEICA CHROME", LookSeries.Nikon, "Summilux character: brassy warmth, natural vignette, bite.", contrast = 0.20f, saturation = -0.10f, warmth = 0.08f, fade = 0.06f, vignette = 0.26f, grain = 0.10f, sharpness = 0.26f, bloom = 0.12f, shadowTint = -0.14f, highlightTint = 0.18f),
        Look("leica.mono", "MONOCHROM", LookSeries.Nikon, "Dedicated B&W sensor look: surgical clarity, deep falloff.", saturation = -1f, contrast = 0.32f, vignette = 0.28f, grain = 0.08f, sharpness = 0.38f, bloom = 0.08f),

        // Hasselblad Natural Colour Solution
        Look("hasselblad.natural", "HNCS NATURAL", LookSeries.Hasselblad, "Hasselblad Natural Colour: true skin, zero harshness.", contrast = 0.06f, saturation = 0.04f, warmth = 0.06f, bloom = 0.10f, sharpness = 0.10f, vignette = 0.06f),
        Look("hasselblad.portrait", "HNCS PORTRAIT", LookSeries.Hasselblad, "Medium-format portrait: luminous skin, creamy roll-off.", saturation = -0.02f, contrast = -0.06f, warmth = 0.14f, fade = 0.06f, bloom = 0.22f, sharpness = -0.08f, vignette = 0.10f, highlightTint = 0.18f),
        Look("hasselblad.landscape", "HNCS LANDSCAPE", LookSeries.Hasselblad, "Medium-format depth: rich foliage, crisp detail, vignette.", saturation = 0.20f, contrast = 0.14f, warmth = 0.04f, bloom = 0.06f, sharpness = 0.22f, vignette = 0.12f, shadowTint = -0.10f),
        Look("hasselblad.bw", "MEDIUM B&W", LookSeries.Hasselblad, "16-bit tonal monochrome: smooth gradients, fine grain.", saturation = -1f, contrast = 0.24f, grain = 0.08f, sharpness = 0.20f, vignette = 0.16f, bloom = 0.08f),
        Look("hasselblad.x2d", "X2D STUDIO", LookSeries.Hasselblad, "Flagship studio rendering: micro-contrast, subtle vignette.", contrast = 0.10f, saturation = 0.06f, warmth = 0.04f, sharpness = 0.28f, vignette = 0.10f, bloom = 0.06f)
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
    val savedLuts: List<LutFileEntry> = emptyList(),
    val lutThumbs: Map<String, Bitmap> = emptyMap(),
    val favouriteLooks: Set<String> = emptySet(),
    val favouriteLuts: Set<String> = emptySet(),
    val subjectMask: Bitmap? = null,
    val relightNormals: Bitmap? = null,
    val relightOverlay: Bitmap? = null,
    val relightShadow: Bitmap? = null,
    val bloomOverlay: Bitmap? = null,
    val autoRescueStrength: Float = 1f,
    val crop: CropRect = CropRect.Full,
    val squarePic: Boolean = false,
    val squareBlur: Float = 0.75f,
    val squareBgStyle: SquareBgStyle = SquareBgStyle.Blur,
    val maskScanning: Boolean = false,
    val thumbs: Map<String, Bitmap> = emptyMap(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val sharing: Boolean = false,
    val compare: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    val isEdited: Boolean
        get() = !adjustments.isNeutral || look.id != Looks.Original.id ||
            light.enabled || lut != null || crop.isCropped || squarePic
}
