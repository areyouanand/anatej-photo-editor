package com.anatej.photo.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatej.photo.ui.theme.DesignMode
import com.anatej.photo.ui.theme.LocalAnatej
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun rememberNoiseImage(): ImageBitmap {
    return remember {
        val size = 160
        val random = Random(19)
        val pixels = IntArray(size * size) {
            val v = 90 + random.nextInt(76)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** Full-screen darkroom backdrop with a warm lens flare and a fine film grain. */
@Composable
fun AnatejBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = LocalAnatej.current
    val noise = rememberNoiseImage()
    val glow = if (t.mode == DesignMode.Signature) 0.10f else 0.16f

    Box(modifier.fillMaxSize().background(t.bg)) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(t.accent.copy(alpha = glow), Color.Transparent),
                    center = Offset(size.width * 0.86f, -size.height * 0.06f),
                    radius = size.width * 1.15f
                )
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, t.panelAlt.copy(alpha = 0.28f))
                )
            )
            drawRect(
                brush = ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)),
                alpha = 0.045f
            )
        }
        content()
    }
}

/** Slow-turning aperture diagram used as the hero graphic. */
@Composable
fun ApertureMark(
    modifier: Modifier = Modifier,
    color: Color,
    animate: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "aperture")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animate) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aperture-angle"
    )

    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f
        val hair = 1.dp.toPx()

        rotate(angle) {
            drawCircle(color.copy(alpha = 0.22f), radius = radius * 0.97f, style = Stroke(hair))
            drawCircle(color.copy(alpha = 0.10f), radius = radius * 0.80f, style = Stroke(hair))

            for (i in 0 until 24) {
                val a = Math.toRadians(i * 15.0)
                val inner = radius * if (i % 6 == 0) 0.86f else 0.935f
                val outer = radius * 0.985f
                drawLine(
                    color = color.copy(alpha = if (i % 6 == 0) 0.42f else 0.18f),
                    start = Offset(cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner),
                    end = Offset(cx + cos(a).toFloat() * outer, cy + sin(a).toFloat() * outer),
                    strokeWidth = hair
                )
            }

            for (i in 0 until 6) {
                drawArc(
                    color = color.copy(alpha = 0.16f),
                    startAngle = i * 60f + 8f,
                    sweepAngle = 52f,
                    useCenter = false,
                    topLeft = Offset(cx - radius * 0.62f, cy - radius * 0.62f),
                    size = Size(radius * 1.24f, radius * 1.24f),
                    style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            drawCircle(
                color = color.copy(alpha = 0.55f),
                radius = radius * 0.11f,
                style = Stroke(1.dp.toPx())
            )
            drawCircle(color = color.copy(alpha = 0.8f), radius = radius * 0.012f)
        }
    }
}

/** Signature / Material 3 Expressive toggle. */
@Composable
fun ThemeToggle(
    mode: DesignMode,
    onChange: (DesignMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(100)
    Row(
        modifier = modifier
            .clip(shape)
            .background(t.panelAlt.copy(alpha = 0.9f))
            .border(1.dp, t.line, shape)
            .padding(3.dp)
    ) {
        DesignMode.entries.forEach { entry ->
            val selected = entry == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(if (selected) t.accent else Color.Transparent)
                    .clickable { onChange(entry) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (entry == DesignMode.Signature) "SIGNATURE" else "M3",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) t.accentInk else t.textDim,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AnatejButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true
) {
    val t = LocalAnatej.current
    if (t.mode == DesignMode.Expressive) {
        if (filled) {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = RoundedCornerShape(100),
                colors = ButtonDefaults.buttonColors(
                    containerColor = t.accent,
                    contentColor = t.accentInk,
                    disabledContainerColor = t.panelAlt,
                    disabledContentColor = t.textDim
                )
            ) {
                Text(text, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = RoundedCornerShape(100),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = t.accent)
            ) {
                Text(text, style = MaterialTheme.typography.labelMedium)
            }
        }
        return
    }

    val shape = RoundedCornerShape(t.smallCorner)
    val background = if (filled) t.accent else Color.Transparent
    val foreground = if (filled) t.accentInk else t.accent
    val base = modifier
        .alpha(if (enabled) 1f else 0.4f)
        .clip(shape)
        .background(background)
    val bordered = if (filled) base else base.border(1.dp, t.accent.copy(alpha = 0.45f), shape)

    Box(
        modifier = bordered
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * One control for both design languages: a tick-marked film slider in Signature mode,
 * a Material 3 slider in Expressive mode.
 */
@Composable
fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val t = LocalAnatej.current
    val expressive = t.mode == DesignMode.Expressive
    val haptics = LocalHapticFeedback.current
    var lastDetent by remember { mutableStateOf(0) }
    val start = range.start
    val end = range.endInclusive
    val trackHeight = if (compact) 24.dp else 30.dp

    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                label,
                style = if (expressive) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelSmall
                },
                color = t.textDim,
                fontFamily = if (expressive) FontFamily.SansSerif else FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            Text(
                format(value),
                style = if (expressive) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelSmall
                },
                color = t.accent,
                fontFamily = if (expressive) FontFamily.SansSerif else FontFamily.Monospace
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .pointerInput(start, end, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun setFromX(x: Float) {
                            val fraction = (x / size.width).coerceIn(0f, 1f)
                            onChange(start + fraction * (end - start))
                        }

                        fun tick(fraction: Float) {
                            val detent = (fraction * 20f).roundToInt()
                            if (detent != lastDetent) {
                                lastDetent = detent
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }

                        // Do not claim the gesture until it is clearly horizontal.
                        // Vertical drags are left unconsumed so the surrounding panel
                        // can scroll even when the finger starts on a slider.
                        var horizontal = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!horizontal) {
                                val slop = viewConfiguration.touchSlop
                                if (dx * dx + dy * dy >= slop * slop) {
                                    if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                        horizontal = true
                                        change.consume()
                                        tick(change.position.x / size.width)
                                        setFromX(change.position.x)
                                    } else {
                                        break
                                    }
                                }
                            } else {
                                change.consume()
                                tick(change.position.x / size.width)
                                setFromX(change.position.x)
                            }
                        }
                    }
                }
        ) {
            val cy = size.height / 2f
            val left = if (expressive) 7.dp.toPx() else 2.dp.toPx()
            val right = size.width - left
            val usable = (right - left).coerceAtLeast(1f)
            val fraction = ((value - start) / (end - start)).coerceIn(0f, 1f)
            val px = left + usable * fraction

            if (expressive) {
                drawLine(
                    color = t.line,
                    start = Offset(left, cy),
                    end = Offset(right, cy),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = t.accent,
                    start = Offset(left, cy),
                    end = Offset(px, cy),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(t.accent.copy(alpha = 0.16f), radius = 13.dp.toPx(), center = Offset(px, cy))
                drawCircle(t.accent, radius = 8.dp.toPx(), center = Offset(px, cy))
                drawCircle(t.accentInk, radius = 2.dp.toPx(), center = Offset(px, cy))
            } else {
                drawLine(t.line, Offset(left, cy), Offset(right, cy), 1.dp.toPx())

                val zeroInside = start < 0f && end > 0f
                for (i in 0..10) {
                    val x = left + usable * i / 10f
                    val strong = i == 0 || i == 10 || (zeroInside && i == 5)
                    drawLine(
                        color = t.textDim.copy(alpha = if (strong) 0.55f else 0.22f),
                        start = Offset(
                            x,
                            cy + if (strong) 4.dp.toPx() else 3.dp.toPx()
                        ),
                        end = Offset(
                            x,
                            cy + if (strong) (if (compact) 7.dp.toPx() else 9.dp.toPx())
                            else (if (compact) 6.dp.toPx() else 7.dp.toPx())
                        ),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                drawLine(t.accent, Offset(left, cy), Offset(px, cy), 2.dp.toPx())
                drawCircle(t.accent.copy(alpha = 0.16f), radius = 11.dp.toPx(), center = Offset(px, cy))
                drawCircle(t.accent, radius = 4.5.dp.toPx(), center = Offset(px, cy))
                drawCircle(t.accentInk, radius = 1.5.dp.toPx(), center = Offset(px, cy))
            }
        }
    }
}

/** Rotary-style knob. Drag horizontally to change the value. */
@Composable
fun DialKnob(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val t = LocalAnatej.current
    val current by rememberUpdatedState(value)
    val density = LocalDensity.current
    val sizeDp = if (t.mode == DesignMode.Signature) 74.dp else 82.dp
    val stroke = if (t.mode == DesignMode.Signature) 3.dp else 6.dp

    Column(
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var startX = 0f
                    var startValue = 0f
                    detectDragGestures(
                        onDragStart = {
                            startX = it.x
                            startValue = current
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val delta = (change.position.x - startX) / size.width
                            onChange((startValue + delta).coerceIn(0f, 1f))
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val strokePx = stroke.toPx()
                val topLeft = Offset(strokePx / 2f, strokePx / 2f)
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                drawArc(
                    color = t.line,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round)
                )
                drawArc(
                    color = t.accent,
                    startAngle = 135f,
                    sweepAngle = 270f * value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round)
                )
                val angle = Math.toRadians((135f + 270f * value).toDouble())
                val radius = (size.minDimension - strokePx) / 2f
                val point = Offset(
                    x = size.width / 2f + cos(angle).toFloat() * radius,
                    y = size.height / 2f + sin(angle).toFloat() * radius
                )
                drawCircle(t.accentInk, radius = (strokePx * 0.62f).coerceAtLeast(2.dp.toPx()), center = point)
            }
            Text(
                text = (value * 100).roundToInt().toString(),
                style = MaterialTheme.typography.titleMedium,
                color = t.text,
                modifier = Modifier.align(Alignment.Center),
                fontFamily = if (t.mode == DesignMode.Signature) FontFamily.Monospace else FontFamily.SansSerif
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = t.textDim,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AnatejSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalAnatej.current
    if (t.mode == DesignMode.Expressive) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = t.accentInk,
                checkedTrackColor = t.accent,
                uncheckedThumbColor = t.textDim,
                uncheckedTrackColor = t.panelAlt,
                uncheckedBorderColor = t.line
            )
        )
        return
    }

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 3.dp,
        label = "switch-thumb"
    )
    val trackWidth = 48.dp
    val trackHeight = 28.dp
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(shape)
            .background(if (checked) t.accent else t.panelAlt)
            .border(1.dp, if (checked) t.accent else t.line, shape)
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .align(Alignment.CenterStart)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) t.accentInk else t.textDim)
        )
    }
}

/** Small square indicator echoing film-sprocket holes. */
@Composable
fun PerforationStrip(
    modifier: Modifier = Modifier,
    color: Color,
    holes: Int = 28
) {
    Canvas(modifier) {
        val holeWidth = 5.dp.toPx()
        val holeHeight = 3.5.dp.toPx()
        val step = size.width / holes
        for (i in 0 until holes) {
            drawRoundRect(
                color = color,
                topLeft = Offset(i * step + (step - holeWidth) / 2f, (size.height - holeHeight) / 2f),
                size = Size(holeWidth, holeHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
            )
        }
    }
}

@Composable
fun MonoLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = 9.sp
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing
    )
}

@Composable
fun Hairline(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun VerticalSpacer(height: Dp) {
    Spacer(Modifier.height(height))
}
