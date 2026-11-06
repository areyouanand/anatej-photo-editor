package com.anatej.photo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DesignMode { Signature, Expressive }

@Immutable
data class AnatejTokens(
    val mode: DesignMode,
    val bg: Color,
    val panel: Color,
    val panelAlt: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val text: Color,
    val textDim: Color,
    val good: Color,
    val danger: Color,
    val smallCorner: Dp,
    val cardCorner: Dp,
    val labelFont: FontFamily,
    val displayFont: FontFamily
)

private val SignaturePalette = AnatejTokens(
    mode = DesignMode.Signature,
    bg = Color(0xFF0A0A0B),
    panel = Color(0xFF131312),
    panelAlt = Color(0xFF1B1A17),
    line = Color(0xFF2D2A25),
    accent = Color(0xFFE8A56A),
    accentInk = Color(0xFF1A1108),
    text = Color(0xFFF2EDE3),
    textDim = Color(0xFF8D857A),
    good = Color(0xFF8FB996),
    danger = Color(0xFFD97A66),
    smallCorner = 8.dp,
    cardCorner = 18.dp,
    labelFont = FontFamily.Monospace,
    displayFont = FontFamily.Serif
)

private val ExpressivePalette = AnatejTokens(
    mode = DesignMode.Expressive,
    bg = Color(0xFF121016),
    panel = Color(0xFF1D1A23),
    panelAlt = Color(0xFF292532),
    line = Color(0xFF3B3546),
    accent = Color(0xFFF6C892),
    accentInk = Color(0xFF2B1B07),
    text = Color(0xFFF3EDE4),
    textDim = Color(0xFFADA3BB),
    good = Color(0xFF9CD3AB),
    danger = Color(0xFFFFB4A4),
    smallCorner = 20.dp,
    cardCorner = 32.dp,
    labelFont = FontFamily.SansSerif,
    displayFont = FontFamily.SansSerif
)

val LocalAnatej = staticCompositionLocalOf { SignaturePalette }

private val SignatureTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 58.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.8).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.0).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp
    )
)

private val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.6).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 33.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.3.sp
    )
)

private val SignatureScheme = darkColorScheme(
    primary = SignaturePalette.accent,
    onPrimary = SignaturePalette.accentInk,
    secondary = Color(0xFF9FB7A4),
    onSecondary = Color(0xFF0E1A10),
    tertiary = Color(0xFFC9A7A0),
    background = SignaturePalette.bg,
    onBackground = SignaturePalette.text,
    surface = SignaturePalette.panel,
    onSurface = SignaturePalette.text,
    surfaceVariant = SignaturePalette.panelAlt,
    onSurfaceVariant = SignaturePalette.textDim,
    outline = SignaturePalette.line,
    error = SignaturePalette.danger
)

private val ExpressiveScheme = darkColorScheme(
    primary = ExpressivePalette.accent,
    onPrimary = ExpressivePalette.accentInk,
    primaryContainer = Color(0xFF4A3520),
    onPrimaryContainer = Color(0xFFFFDDBB),
    secondary = Color(0xFFB8C7E8),
    onSecondary = Color(0xFF12203A),
    secondaryContainer = Color(0xFF2C3A55),
    onSecondaryContainer = Color(0xFFD9E3FF),
    tertiary = Color(0xFFE8B8C8),
    onTertiary = Color(0xFF3A1624),
    background = ExpressivePalette.bg,
    onBackground = ExpressivePalette.text,
    surface = ExpressivePalette.panel,
    onSurface = ExpressivePalette.text,
    surfaceVariant = ExpressivePalette.panelAlt,
    onSurfaceVariant = ExpressivePalette.textDim,
    outline = ExpressivePalette.line,
    error = ExpressivePalette.danger
)

@Composable
fun AnatejTheme(mode: DesignMode, content: @Composable () -> Unit) {
    val tokens = if (mode == DesignMode.Signature) SignaturePalette else ExpressivePalette
    val scheme = if (mode == DesignMode.Signature) SignatureScheme else ExpressiveScheme
    val typography = if (mode == DesignMode.Signature) SignatureTypography else ExpressiveTypography
    val shapes = if (mode == DesignMode.Signature) {
        Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        )
    } else {
        Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(18.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(32.dp),
            extraLarge = RoundedCornerShape(40.dp)
        )
    }

    CompositionLocalProvider(LocalAnatej provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes
        ) {
            CompositionLocalProvider(LocalContentColor provides tokens.text) {
                content()
            }
        }
    }
}
