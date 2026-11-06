package com.anatej.photo.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.anatej.photo.engine.PhotoRenderer
import com.anatej.photo.model.Adjustments
import com.anatej.photo.model.Look
import com.anatej.photo.model.PortraitLight
import com.anatej.photo.ui.components.rememberNoiseImage
import com.anatej.photo.ui.theme.LocalAnatej
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun contentRect(canvasWidth: Float, canvasHeight: Float, bitmap: Bitmap): Rect {
    if (bitmap.width == 0 || bitmap.height == 0) return Rect.Zero
    val scale = min(canvasWidth / bitmap.width, canvasHeight / bitmap.height)
    val width = bitmap.width * scale
    val height = bitmap.height * scale
    val left = (canvasWidth - width) / 2f
    val top = (canvasHeight - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/**
 * The editing viewport. The base grade is a colour matrix (GPU-cheap); when a 3D LUT is
 * active the base is pre-baked on the CPU instead. Portrait light, vignette and grain are
 * blend-mode overlays, and the light is masked to the subject when segmentation is ready.
 */
@Composable
fun EditorCanvas(
    bitmap: Bitmap,
    adjustments: Adjustments,
    look: Look,
    lookStrength: Float,
    light: PortraitLight,
    lutPreview: Bitmap?,
    subjectMask: Bitmap?,
    relightOverlay: Bitmap?,
    relightShadow: Bitmap?,
    compare: Boolean,
    lightEditable: Boolean,
    showMask: Boolean = false,
    zoom: Float,
    pan: Offset,
    onTransform: (Float, Offset) -> Unit,
    onCompareHold: (Boolean) -> Unit,
    onLightPosition: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnatej.current
    val display = lutPreview ?: bitmap
    val sourceImage = remember(bitmap) { bitmap.asImageBitmap() }
    val displayImage = remember(display) { display.asImageBitmap() }
    val maskImage = remember(subjectMask) { subjectMask?.asImageBitmap() }
    val overlayImage = remember(relightOverlay) { relightOverlay?.asImageBitmap() }
    val shadowImage = remember(relightShadow) { relightShadow?.asImageBitmap() }
    val noise = rememberNoiseImage()
    val stock = remember(look, lookStrength) { look.scaled(lookStrength) }
    val filter = remember(adjustments, look, lookStrength) {
        ColorFilter.colorMatrix(
            ColorMatrix(PhotoRenderer.previewMatrix(adjustments, look, lookStrength))
        )
    }
    val vignette = (adjustments.vignette + stock.vignette).coerceIn(0f, 1f)
    val grain = (adjustments.grain + stock.grain).coerceIn(0f, 1f)
    val lightOn = light.enabled && !compare
    val useMask = light.subjectOnly && maskImage != null

    val zoomNow by rememberUpdatedState(zoom)
    val panNow by rememberUpdatedState(pan)
    val transformNow by rememberUpdatedState(onTransform)

    val gestureModifier = Modifier
        .pointerInput(bitmap, display, lightEditable, light.enabled) {
            val viewportW = size.width.toFloat()
            val viewportH = size.height.toFloat()
            val base = contentRect(viewportW, viewportH, display)

            fun clampPan(candidate: Offset, zoomValue: Float): Offset {
                if (zoomValue <= 1.001f || base.width <= 0f || base.height <= 0f) {
                    return Offset.Zero
                }
                val maxX = ((base.width * zoomValue - viewportW) / 2f).coerceAtLeast(0f)
                val maxY = ((base.height * zoomValue - viewportH) / 2f).coerceAtLeast(0f)
                return Offset(
                    candidate.x.coerceIn(-maxX, maxX),
                    candidate.y.coerceIn(-maxY, maxY)
                )
            }

            fun currentRect(): Rect {
                val w = base.width * zoomNow
                val h = base.height * zoomNow
                val cx = viewportW / 2f + panNow.x
                val cy = viewportH / 2f + panNow.y
                return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            }

            fun placeLight(position: Offset) {
                val rect = currentRect()
                if (rect.width <= 0f || rect.height <= 0f) return
                val nx = ((position.x - rect.left) / rect.width).coerceIn(0f, 1f)
                val ny = ((position.y - rect.top) / rect.height).coerceIn(0f, 1f)
                onLightPosition(nx, ny)
            }

            fun transform(centroid: Offset, panChange: Offset, zoomChange: Float) {
                val oldZoom = zoomNow.coerceAtLeast(0.001f)
                val newZoom = (zoomNow * zoomChange).coerceIn(1f, 6f)
                val centerX = viewportW / 2f
                val centerY = viewportH / 2f
                val oldCx = centerX + panNow.x
                val oldCy = centerY + panNow.y
                val scale = newZoom / oldZoom
                val newCx = centroid.x + (oldCx - centroid.x) * scale + panChange.x
                val newCy = centroid.y + (oldCy - centroid.y) * scale + panChange.y
                transformNow(
                    newZoom,
                    clampPan(Offset(newCx - centerX, newCy - centerY), newZoom)
                )
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var mode = 0 // 0 undecided, 1 light, 2 pan, 3 transform
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break

                    if (pressed.size >= 2) {
                        mode = 3
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            transform(centroid, panChange, zoomChange)
                        }
                        event.changes.forEach { it.consume() }
                    } else {
                        val change = pressed.first()
                        when (mode) {
                            3 -> {
                                val delta = change.position - change.previousPosition
                                if (delta != Offset.Zero) {
                                    transformNow(
                                        zoomNow,
                                        clampPan(panNow + delta, zoomNow)
                                    )
                                    change.consume()
                                }
                            }

                            0 -> {
                                val total = change.position - down.position
                                if (total.getDistance() > viewConfiguration.touchSlop) {
                                    mode = if (lightEditable && light.enabled) 1 else 2
                                    if (mode == 1) {
                                        placeLight(change.position)
                                    } else {
                                        transformNow(
                                            zoomNow,
                                            clampPan(panNow + total, zoomNow)
                                        )
                                    }
                                    change.consume()
                                }
                            }

                            1 -> {
                                placeLight(change.position)
                                change.consume()
                            }

                            2 -> {
                                val delta = change.position - change.previousPosition
                                if (delta != Offset.Zero) {
                                    transformNow(
                                        zoomNow,
                                        clampPan(panNow + delta, zoomNow)
                                    )
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    val result = kotlinx.coroutines.withTimeoutOrNull(
                        viewConfiguration.longPressTimeoutMillis
                    ) {
                        tryAwaitRelease()
                    }
                    if (result == null) {
                        onCompareHold(true)
                        tryAwaitRelease()
                        onCompareHold(false)
                    }
                }
            )
        }

    Canvas(modifier = modifier.then(gestureModifier)) {
        val base = contentRect(size.width, size.height, display)
        if (base.width <= 0f || base.height <= 0f) return@Canvas

        val drawW = base.width * zoom
        val drawH = base.height * zoom
        val centerX = size.width / 2f + pan.x
        val centerY = size.height / 2f + pan.y
        val rect = Rect(
            centerX - drawW / 2f,
            centerY - drawH / 2f,
            centerX + drawW / 2f,
            centerY + drawH / 2f
        )

        drawImage(
            image = if (compare) sourceImage else displayImage,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(
                if (compare) bitmap.width else display.width,
                if (compare) bitmap.height else display.height
            ),
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
            colorFilter = if (compare) null else filter,
            filterQuality = FilterQuality.Medium
        )

        if (showMask && !compare && maskImage != null) {
            clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                drawImage(
                    image = maskImage,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(maskImage.width, maskImage.height),
                    dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                    dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                    colorFilter = ColorFilter.tint(Color(1f, 0.45f, 0.15f, 0.55f)),
                    filterQuality = FilterQuality.Medium
                )
            }
        }

        if (!compare) {
            clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                val minDim = min(rect.width, rect.height)

                if (lightOn) {
                    val lx = rect.left + light.x * rect.width
                    val ly = rect.top + light.y * rect.height
                    val radius = (light.radius.coerceIn(0.08f, 1.6f) * minDim).coerceAtLeast(1f)
                    val intensity = ((light.intensity / 100f) * 0.30f).coerceIn(0f, 1f)

                    val shadowX = rect.left + (1f - light.x) * rect.width
                    val shadowY = rect.top + (1f - light.y) * rect.height

                    val drawShadow: DrawScope.() -> Unit = {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = intensity * 0.18f),
                                    Color.Transparent
                                ),
                                center = Offset(shadowX, shadowY),
                                radius = radius * 1.1f
                            ),
                            radius = radius * 1.1f,
                            center = Offset(shadowX, shadowY),
                            blendMode = BlendMode.Multiply
                        )
                    }

                    val drawRadialKey: DrawScope.() -> Unit = {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(1f, 0.89f, 0.72f, alpha = intensity * 0.34f),
                                    Color(1f, 0.84f, 0.63f, alpha = intensity * 0.12f),
                                    Color.Transparent
                                ),
                                center = Offset(lx, ly),
                                radius = radius * 0.85f
                            ),
                            radius = radius * 0.85f,
                            center = Offset(lx, ly),
                            blendMode = BlendMode.Screen
                        )
                    }

                    if (shadowImage != null) {
                        drawImage(
                            image = shadowImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(shadowImage.width, shadowImage.height),
                            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                            blendMode = BlendMode.Multiply,
                            filterQuality = FilterQuality.Medium
                        )
                    } else if (useMask && maskImage != null) {
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(rect, Paint())
                            drawShadow()
                            drawImage(
                                image = maskImage,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(maskImage.width, maskImage.height),
                                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                                dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                                blendMode = BlendMode.DstIn,
                                filterQuality = FilterQuality.Medium
                            )
                            canvas.restore()
                        }
                    } else {
                        drawShadow()
                    }

                    if (overlayImage != null) {
                        drawImage(
                            image = overlayImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(overlayImage.width, overlayImage.height),
                            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                            blendMode = BlendMode.Screen,
                            filterQuality = FilterQuality.Medium
                        )
                    } else if (useMask && maskImage != null) {
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(rect, Paint())
                            drawRadialKey()
                            drawImage(
                                image = maskImage,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(maskImage.width, maskImage.height),
                                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                                dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                                blendMode = BlendMode.DstIn,
                                filterQuality = FilterQuality.Medium
                            )
                            canvas.restore()
                        }
                    } else {
                        drawRadialKey()
                    }

                    // Placement reticle
                    val reticle = Offset(lx, ly)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = 13.dp.toPx(),
                        center = reticle,
                        style = Stroke(1.dp.toPx())
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.55f),
                        Offset(reticle.x - 18.dp.toPx(), reticle.y),
                        Offset(reticle.x + 18.dp.toPx(), reticle.y),
                        1.dp.toPx()
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.55f),
                        Offset(reticle.x, reticle.y - 18.dp.toPx()),
                        Offset(reticle.x, reticle.y + 18.dp.toPx()),
                        1.dp.toPx()
                    )
                    drawCircle(tokens.accent, radius = 2.5.dp.toPx(), center = reticle)
                }

                if (vignette > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.52f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.72f * vignette)
                            ),
                            center = Offset(rect.center.x, rect.center.y),
                            radius = max(rect.width, rect.height) * 0.74f
                        ),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height)
                    )
                }

                if (grain > 0f) {
                    drawRect(
                        brush = ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        alpha = (grain * 0.14f).coerceIn(0f, 0.14f),
                        blendMode = BlendMode.Overlay
                    )
                }
            }
        }
    }
}
