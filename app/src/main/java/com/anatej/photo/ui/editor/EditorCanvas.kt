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
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.anatej.photo.engine.PhotoRenderer
import com.anatej.photo.model.Adjustments
import com.anatej.photo.model.CropRect
import com.anatej.photo.model.Look
import com.anatej.photo.model.PortraitLight
import com.anatej.photo.model.SquareBgStyle
import com.anatej.photo.ui.components.rememberNoiseImage
import com.anatej.photo.ui.theme.LocalAnatej
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun fitRect(canvasWidth: Float, canvasHeight: Float, contentW: Float, contentH: Float): Rect {
    if (contentW <= 0f || contentH <= 0f) return Rect.Zero
    val scale = min(canvasWidth / contentW, canvasHeight / contentH)
    val width = contentW * scale
    val height = contentH * scale
    val left = (canvasWidth - width) / 2f
    val top = (canvasHeight - height) / 2f
    return Rect(left, top, left + width, top + height)
}

internal fun straightenZoom(degrees: Float, width: Float, height: Float): Float {
    val a = abs(degrees)
    if (a <= 0.05f) return 1f
    val rad = Math.toRadians(a.toDouble())
    val c = kotlin.math.cos(rad).toFloat()
    val s = kotlin.math.sin(rad).toFloat()
    val ratio = if (width > 0f && height > 0f) max(width / height, height / width) else 1f
    return (c + ratio * s).coerceIn(1f, 2.6f)
}

private const val HANDLE_NONE = 0
private const val HANDLE_TL = 1
private const val HANDLE_TR = 2
private const val HANDLE_BL = 3
private const val HANDLE_BR = 4
private const val HANDLE_T = 5
private const val HANDLE_B = 6
private const val HANDLE_L = 7
private const val HANDLE_R = 8
private const val HANDLE_MOVE = 9

/**
 * The editing viewport. The base grade is a colour matrix (GPU-cheap); when a 3D LUT is
 * active the base is pre-baked on the CPU instead. Portrait light, vignette, bloom, halation
 * and grain are blend-mode overlays, and the crop tool supports interactive corner/edge handles
 * with a non-destructive cropped viewport when outside the Crop tab.
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
    bloomOverlay: Bitmap? = null,
    compare: Boolean,
    lightEditable: Boolean,
    cropEditable: Boolean = false,
    crop: CropRect = CropRect.Full,
    onCropChange: (CropRect) -> Unit = {},
    squarePic: Boolean = false,
    squareBlur: Float = 0.75f,
    squareBgStyle: SquareBgStyle = SquareBgStyle.Blur,
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
    val bloomImage = remember(bloomOverlay) { bloomOverlay?.asImageBitmap() }
    val shadowImage = remember(relightShadow) { relightShadow?.asImageBitmap() }
    val noise = rememberNoiseImage()
    val stock = remember(look, lookStrength) { look.scaled(lookStrength) }
    val filter = remember(adjustments, look, lookStrength) {
        ColorFilter.colorMatrix(
            ColorMatrix(PhotoRenderer.previewMatrix(adjustments, look, lookStrength))
        )
    }
    val halationFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.25f, 0.15f, 0f, 0f, 0f,
                    0.08f, 0.36f, 0f, 0f, 0f,
                    0f, 0.04f, 0.08f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    val vignette = (adjustments.vignette + stock.vignette).coerceIn(0f, 1f)
    val grain = (adjustments.grain + stock.grain).coerceIn(0f, 1f)
    val sharpness = (adjustments.sharpness + stock.sharpness).coerceIn(-1f, 1f)
    val bloom = (adjustments.bloom + stock.bloom).coerceIn(0f, 1f)
    val halation = (adjustments.halation + stock.halation).coerceIn(0f, 1f)
    val lightOn = light.enabled && !compare
    val useMask = light.subjectOnly && maskImage != null

    val zoomNow by rememberUpdatedState(zoom)
    val panNow by rememberUpdatedState(pan)
    val transformNow by rememberUpdatedState(onTransform)
    val cropNow by rememberUpdatedState(crop)
    val cropCallback by rememberUpdatedState(onCropChange)
    val cropEditableNow by rememberUpdatedState(cropEditable)

    val gestureModifier = Modifier
        .pointerInput(bitmap, display, lightEditable, light.enabled, cropEditable) {
            val viewportW = size.width.toFloat()
            val viewportH = size.height.toFloat()
            val fullBase = fitRect(viewportW, viewportH, display.width.toFloat(), display.height.toFloat())

            fun activeBase(): Rect {
                val c = cropNow
                return if (!cropEditableNow && c.isCropped) {
                    fitRect(
                        viewportW,
                        viewportH,
                        c.width * display.width.toFloat(),
                        c.height * display.height.toFloat()
                    )
                } else {
                    fullBase
                }
            }

            fun clampPan(candidate: Offset, zoomValue: Float): Offset {
                val base = activeBase()
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

            fun currentViewRect(): Rect {
                val base = activeBase()
                val w = base.width * zoomNow
                val h = base.height * zoomNow
                val cx = viewportW / 2f + panNow.x
                val cy = viewportH / 2f + panNow.y
                return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            }

            fun currentFullRect(): Rect {
                val vr = currentViewRect()
                val c = cropNow
                if (!cropEditableNow && c.isCropped) {
                    val fw = vr.width / c.width
                    val fh = vr.height / c.height
                    val fl = vr.left - c.left * fw
                    val ft = vr.top - c.top * fh
                    return Rect(fl, ft, fl + fw, ft + fh)
                }
                return vr
            }

            fun placeLight(position: Offset) {
                val rect = currentFullRect()
                if (rect.width <= 0f || rect.height <= 0f) return
                val nx = ((position.x - rect.left) / rect.width).coerceIn(0f, 1f)
                val ny = ((position.y - rect.top) / rect.height).coerceIn(0f, 1f)
                onLightPosition(nx, ny)
            }

            fun detectCropHandle(pos: Offset, rect: Rect, c: CropRect): Int {
                val cl = rect.left + c.left * rect.width
                val ct = rect.top + c.top * rect.height
                val cr = rect.left + c.right * rect.width
                val cb = rect.top + c.bottom * rect.height
                val hit = 34.dp.toPx()

                fun dist(x: Float, y: Float) = hypot(pos.x - x, pos.y - y)
                val dTL = dist(cl, ct)
                val dTR = dist(cr, ct)
                val dBL = dist(cl, cb)
                val dBR = dist(cr, cb)
                val minCorner = min(min(dTL, dTR), min(dBL, dBR))
                if (minCorner <= hit) {
                    return when (minCorner) {
                        dTL -> HANDLE_TL
                        dTR -> HANDLE_TR
                        dBL -> HANDLE_BL
                        else -> HANDLE_BR
                    }
                }
                val inX = pos.x in (cl - hit)..(cr + hit)
                val inY = pos.y in (ct - hit)..(cb + hit)
                if (inX && abs(pos.y - ct) <= hit * 0.75f) return HANDLE_T
                if (inX && abs(pos.y - cb) <= hit * 0.75f) return HANDLE_B
                if (inY && abs(pos.x - cl) <= hit * 0.75f) return HANDLE_L
                if (inY && abs(pos.x - cr) <= hit * 0.75f) return HANDLE_R
                if (pos.x in cl..cr && pos.y in ct..cb) return HANDLE_MOVE
                return HANDLE_MOVE
            }

            fun applyCropDrag(handle: Int, dxNorm: Float, dyNorm: Float) {
                val c = cropNow
                val minSize = 0.10f
                val photoAspect = (display.width.toFloat() / display.height.toFloat()).coerceAtLeast(0.05f)
                val lockedRatio = when {
                    c.aspectLock < 0f -> 1f
                    c.aspectLock > 0f -> (c.aspectLock / photoAspect).coerceAtLeast(0.05f)
                    else -> 0f
                }

                var l = c.left
                var t = c.top
                var r = c.right
                var b = c.bottom

                if (handle == HANDLE_MOVE) {
                    val w = c.width
                    val h = c.height
                    l = (l + dxNorm).coerceIn(0f, 1f - w)
                    t = (t + dyNorm).coerceIn(0f, 1f - h)
                    cropCallback(c.copy(left = l, top = t, right = l + w, bottom = t + h))
                    return
                }

                if (lockedRatio <= 0f) {
                    when (handle) {
                        HANDLE_TL -> {
                            l = (l + dxNorm).coerceIn(0f, r - minSize)
                            t = (t + dyNorm).coerceIn(0f, b - minSize)
                        }
                        HANDLE_TR -> {
                            r = (r + dxNorm).coerceIn(l + minSize, 1f)
                            t = (t + dyNorm).coerceIn(0f, b - minSize)
                        }
                        HANDLE_BL -> {
                            l = (l + dxNorm).coerceIn(0f, r - minSize)
                            b = (b + dyNorm).coerceIn(t + minSize, 1f)
                        }
                        HANDLE_BR -> {
                            r = (r + dxNorm).coerceIn(l + minSize, 1f)
                            b = (b + dyNorm).coerceIn(t + minSize, 1f)
                        }
                        HANDLE_T -> t = (t + dyNorm).coerceIn(0f, b - minSize)
                        HANDLE_B -> b = (b + dyNorm).coerceIn(t + minSize, 1f)
                        HANDLE_L -> l = (l + dxNorm).coerceIn(0f, r - minSize)
                        HANDLE_R -> r = (r + dxNorm).coerceIn(l + minSize, 1f)
                    }
                    cropCallback(c.copy(left = l, top = t, right = r, bottom = b))
                } else {
                    // Aspect-locked resize anchored at the opposite corner or edge center
                    val minW = max(minSize, minSize * lockedRatio)
                    when (handle) {
                        HANDLE_BR, HANDLE_R, HANDLE_B -> {
                            val maxW = min(1f - l, (1f - t) * lockedRatio)
                            val deltaW = if (handle == HANDLE_B) dyNorm * lockedRatio else dxNorm
                            val newW = (r - l + deltaW).coerceIn(minW.coerceAtMost(maxW), maxW)
                            val newH = newW / lockedRatio
                            r = l + newW
                            b = t + newH
                        }
                        HANDLE_TL, HANDLE_L, HANDLE_T -> {
                            val maxW = min(r, b * lockedRatio)
                            val deltaW = if (handle == HANDLE_T) -dyNorm * lockedRatio else -dxNorm
                            val newW = (r - l + deltaW).coerceIn(minW.coerceAtMost(maxW), maxW)
                            val newH = newW / lockedRatio
                            l = r - newW
                            t = b - newH
                        }
                        HANDLE_TR -> {
                            val maxW = min(1f - l, b * lockedRatio)
                            val newW = (r - l + dxNorm).coerceIn(minW.coerceAtMost(maxW), maxW)
                            val newH = newW / lockedRatio
                            r = l + newW
                            t = b - newH
                        }
                        HANDLE_BL -> {
                            val maxW = min(r, (1f - t) * lockedRatio)
                            val newW = (r - l - dxNorm).coerceIn(minW.coerceAtMost(maxW), maxW)
                            val newH = newW / lockedRatio
                            l = r - newW
                            b = t + newH
                        }
                    }
                    cropCallback(c.copy(left = l, top = t, right = r, bottom = b))
                }
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
                var mode = 0 // 0 undecided, 1 light, 2 pan, 3 transform, 4 crop-handle
                var cropHandle = HANDLE_NONE
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
                                    if (cropEditableNow) {
                                        mode = 4
                                        val rectNow = currentViewRect()
                                        cropHandle = detectCropHandle(down.position, rectNow, cropNow)
                                        if (rectNow.width > 0f && rectNow.height > 0f) {
                                            applyCropDrag(
                                                cropHandle,
                                                total.x / rectNow.width,
                                                total.y / rectNow.height
                                            )
                                        }
                                    } else if (lightEditable && light.enabled) {
                                        mode = 1
                                        placeLight(change.position)
                                    } else {
                                        mode = 2
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

                            4 -> {
                                val delta = change.position - change.previousPosition
                                if (delta != Offset.Zero) {
                                    val rectNow = currentViewRect()
                                    if (rectNow.width > 0f && rectNow.height > 0f) {
                                        applyCropDrag(
                                            cropHandle,
                                            delta.x / rectNow.width,
                                            delta.y / rectNow.height
                                        )
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
        }
        .pointerInput(cropEditable) {
            if (cropEditable) return@pointerInput
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
        val showCroppedViewport = !cropEditable && crop.isCropped && !compare
        val effW = if (showCroppedViewport) crop.width * display.width else display.width.toFloat()
        val effH = if (showCroppedViewport) crop.height * display.height else display.height.toFloat()
        val base = fitRect(size.width, size.height, effW, effH)
        if (base.width <= 0f || base.height <= 0f) return@Canvas

        val drawW = base.width * zoom
        val drawH = base.height * zoom
        val centerX = size.width / 2f + pan.x
        val centerY = size.height / 2f + pan.y
        val sqSide = min(size.width, size.height) * zoom
        val sqRect = Rect(
            centerX - sqSide / 2f,
            centerY - sqSide / 2f,
            centerX + sqSide / 2f,
            centerY + sqSide / 2f
        )

        if (squarePic && !compare) {
            clipRect(sqRect.left, sqRect.top, sqRect.right, sqRect.bottom) {
                when (squareBgStyle) {
                    SquareBgStyle.Black -> drawRect(Color.Black, sqRect.topLeft, sqRect.size)
                    SquareBgStyle.White -> drawRect(Color.White, sqRect.topLeft, sqRect.size)
                    SquareBgStyle.Blur -> {
                        val bgImage = bloomImage ?: displayImage
                        val coverScale = max(sqSide / bgImage.width, sqSide / bgImage.height)
                        val bgW = bgImage.width * coverScale
                        val bgH = bgImage.height * coverScale
                        val dstOff = IntOffset(
                            (sqRect.left - (bgW - sqSide) / 2f).roundToInt(),
                            (sqRect.top - (bgH - sqSide) / 2f).roundToInt()
                        )
                        val dstSz = IntSize(bgW.roundToInt(), bgH.roundToInt())
                        drawImage(
                            image = displayImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(displayImage.width, displayImage.height),
                            dstOffset = dstOff,
                            dstSize = dstSz,
                            colorFilter = filter,
                            filterQuality = FilterQuality.Low
                        )
                        if (bloomImage != null) {
                            drawImage(
                                image = bloomImage,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(bloomImage.width, bloomImage.height),
                                dstOffset = dstOff,
                                dstSize = dstSz,
                                alpha = squareBlur.coerceIn(0.15f, 1f),
                                colorFilter = filter,
                                filterQuality = FilterQuality.Medium
                            )
                        }
                    }
                }
            }
        }

        val viewRect = if (squarePic && !compare) {
            val fitScale = min(sqSide / effW, sqSide / effH)
            val fitW2 = effW * fitScale
            val fitH2 = effH * fitScale
            Rect(
                sqRect.left + (sqSide - fitW2) / 2f,
                sqRect.top + (sqSide - fitH2) / 2f,
                sqRect.left + (sqSide + fitW2) / 2f,
                sqRect.top + (sqSide + fitH2) / 2f
            )
        } else {
            Rect(
                centerX - drawW / 2f,
                centerY - drawH / 2f,
                centerX + drawW / 2f,
                centerY + drawH / 2f
            )
        }

        val fullRect = if (showCroppedViewport) {
            val fw = viewRect.width / crop.width
            val fh = viewRect.height / crop.height
            val fl = viewRect.left - crop.left * fw
            val ft = viewRect.top - crop.top * fh
            Rect(fl, ft, fl + fw, ft + fh)
        } else {
            viewRect
        }

        val straightenDeg = if (compare) 0f else crop.straighten
        val straightenScale = straightenZoom(straightenDeg, effW, effH)
        val straightenPivot = if (cropEditable) {
            Offset(
                viewRect.left + (crop.left + crop.right) * 0.5f * viewRect.width,
                viewRect.top + (crop.top + crop.bottom) * 0.5f * viewRect.height
            )
        } else {
            viewRect.center
        }

        clipRect(viewRect.left, viewRect.top, viewRect.right, viewRect.bottom) {
            withTransform({
                if (abs(straightenDeg) > 0.05f) {
                    rotate(straightenDeg, straightenPivot)
                    scale(straightenScale, straightenScale, straightenPivot)
                }
            }) {
            drawImage(
                image = if (compare) sourceImage else displayImage,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(
                    if (compare) bitmap.width else display.width,
                    if (compare) bitmap.height else display.height
                ),
                dstOffset = IntOffset(fullRect.left.roundToInt(), fullRect.top.roundToInt()),
                dstSize = IntSize(fullRect.width.roundToInt(), fullRect.height.roundToInt()),
                colorFilter = if (compare) null else filter,
                filterQuality = FilterQuality.Medium
            )

            if (showMask && !compare && maskImage != null) {
                drawImage(
                    image = maskImage,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(maskImage.width, maskImage.height),
                    dstOffset = IntOffset(fullRect.left.roundToInt(), fullRect.top.roundToInt()),
                    dstSize = IntSize(fullRect.width.roundToInt(), fullRect.height.roundToInt()),
                    colorFilter = ColorFilter.tint(Color(1f, 0.45f, 0.15f, 0.55f)),
                    filterQuality = FilterQuality.Medium
                )
            }

            if (!compare) {
                val minDim = min(fullRect.width, fullRect.height)

                if (lightOn) {
                    val lx = fullRect.left + light.x * fullRect.width
                    val ly = fullRect.top + light.y * fullRect.height
                    val radius = (light.radius.coerceIn(0.08f, 1.6f) * minDim).coerceAtLeast(1f)
                    val intensity = ((light.intensity / 100f) * 0.30f).coerceIn(0f, 1f)

                    val shadowX = fullRect.left + (1f - light.x) * fullRect.width
                    val shadowY = fullRect.top + (1f - light.y) * fullRect.height

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
                            dstOffset = IntOffset(fullRect.left.roundToInt(), fullRect.top.roundToInt()),
                            dstSize = IntSize(fullRect.width.roundToInt(), fullRect.height.roundToInt()),
                            blendMode = BlendMode.Multiply,
                            filterQuality = FilterQuality.Medium
                        )
                    } else if (useMask && maskImage != null) {
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(fullRect, Paint())
                            drawShadow()
                            drawImage(
                                image = maskImage,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(maskImage.width, maskImage.height),
                                dstOffset = IntOffset(fullRect.left.roundToInt(), fullRect.top.roundToInt()),
                                dstSize = IntSize(fullRect.width.roundToInt(), fullRect.height.roundToInt()),
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
                            dstOffset = IntOffset(fullRect.left.roundToInt(), fullRect.top.roundToInt()),
                            dstSize = IntSize(fullRect.width.roundToInt(), fullRect.height.roundToInt()),
                            colorFilter = filter,
                            filterQuality = FilterQuality.Medium
                        )
                    } else if (useMask && maskImage != null) {
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(fullRect, Paint())
                            drawRadialKey()
                            drawImage(
                                image = maskImage,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(maskImage.width, maskImage.height),
                                dstOffset = IntOffset(fullRect.left.roundToInt(), fullRect.top.roundToInt()),
                                dstSize = IntSize(fullRect.width.roundToInt(), fullRect.height.roundToInt()),
                                blendMode = BlendMode.DstIn,
                                filterQuality = FilterQuality.Medium
                            )
                            canvas.restore()
                        }
                    } else {
                        drawRadialKey()
                    }

                    if (lightEditable) {
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
                }

                if (bloomImage != null && (bloom > 0.01f || halation > 0.01f || sharpness > 0.01f)) {
                    if (bloom > 0.01f) {
                        drawImage(
                            image = bloomImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bloomImage.width, bloomImage.height),
                            dstOffset = IntOffset(fullRect.left.roundToInt(), fullRect.top.roundToInt()),
                            dstSize = IntSize(fullRect.width.roundToInt(), fullRect.height.roundToInt()),
                            alpha = (bloom * 0.35f).coerceIn(0f, 0.35f),
                            blendMode = BlendMode.Screen,
                            filterQuality = FilterQuality.Medium
                        )
                    }
                    if (halation > 0.01f) {
                        drawImage(
                            image = bloomImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bloomImage.width, bloomImage.height),
                            dstOffset = IntOffset(fullRect.left.roundToInt(), fullRect.top.roundToInt()),
                            dstSize = IntSize(fullRect.width.roundToInt(), fullRect.height.roundToInt()),
                            alpha = (halation * 0.42f).coerceIn(0f, 0.44f),
                            colorFilter = halationFilter,
                            blendMode = BlendMode.Screen,
                            filterQuality = FilterQuality.Medium
                        )
                    }
                    if (sharpness > 0.01f) {
                        drawImage(
                            image = bloomImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bloomImage.width, bloomImage.height),
                            dstOffset = IntOffset(fullRect.left.roundToInt(), fullRect.top.roundToInt()),
                            dstSize = IntSize(fullRect.width.roundToInt(), fullRect.height.roundToInt()),
                            alpha = (sharpness * 0.5f).coerceIn(0f, 0.5f),
                            blendMode = BlendMode.Overlay,
                            filterQuality = FilterQuality.Medium
                        )
                    }
                }
            }
            } // withTransform

            if (!compare) {
                if (vignette > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.52f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.72f * vignette)
                            ),
                            center = Offset(viewRect.center.x, viewRect.center.y),
                            radius = max(viewRect.width, viewRect.height) * 0.74f
                        ),
                        topLeft = Offset(viewRect.left, viewRect.top),
                        size = Size(viewRect.width, viewRect.height)
                    )
                }

                if (grain > 0f) {
                    drawRect(
                        brush = ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)),
                        topLeft = Offset(viewRect.left, viewRect.top),
                        size = Size(viewRect.width, viewRect.height),
                        alpha = (grain * 0.14f).coerceIn(0f, 0.14f),
                        blendMode = BlendMode.Overlay
                    )
                }
            }
        }

        if (squarePic && !compare) {
            drawRect(
                color = Color.White.copy(alpha = 0.42f),
                topLeft = sqRect.topLeft,
                size = sqRect.size,
                style = Stroke(1.5f)
            )
        }

        // Interactive Crop Overlay when in the CROP tab
        if (cropEditable && !compare) {
            val cropRect = Rect(
                viewRect.left + crop.left * viewRect.width,
                viewRect.top + crop.top * viewRect.height,
                viewRect.left + crop.right * viewRect.width,
                viewRect.top + crop.bottom * viewRect.height
            )
            val dim = Color.Black.copy(alpha = 0.62f)
            if (cropRect.top > viewRect.top) {
                drawRect(dim, Offset(viewRect.left, viewRect.top), Size(viewRect.width, cropRect.top - viewRect.top))
            }
            if (cropRect.bottom < viewRect.bottom) {
                drawRect(dim, Offset(viewRect.left, cropRect.bottom), Size(viewRect.width, viewRect.bottom - cropRect.bottom))
            }
            if (cropRect.left > viewRect.left) {
                drawRect(dim, Offset(viewRect.left, cropRect.top), Size(cropRect.left - viewRect.left, cropRect.height))
            }
            if (cropRect.right < viewRect.right) {
                drawRect(dim, Offset(cropRect.right, cropRect.top), Size(viewRect.right - cropRect.right, cropRect.height))
            }

            // Rule-of-thirds grid
            val gridColor = Color.White.copy(alpha = 0.48f)
            val gridStroke = 1.dp.toPx()
            drawLine(
                gridColor,
                cropRect.topLeft + Offset(cropRect.width / 3f, 0f),
                cropRect.topLeft + Offset(cropRect.width / 3f, cropRect.height),
                gridStroke
            )
            drawLine(
                gridColor,
                cropRect.topLeft + Offset(cropRect.width * 2f / 3f, 0f),
                cropRect.topLeft + Offset(cropRect.width * 2f / 3f, cropRect.height),
                gridStroke
            )
            drawLine(
                gridColor,
                cropRect.topLeft + Offset(0f, cropRect.height / 3f),
                cropRect.topLeft + Offset(cropRect.width, cropRect.height / 3f),
                gridStroke
            )
            drawLine(
                gridColor,
                cropRect.topLeft + Offset(0f, cropRect.height * 2f / 3f),
                cropRect.topLeft + Offset(cropRect.width, cropRect.height * 2f / 3f),
                gridStroke
            )

            // Outer border
            drawRect(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = cropRect.topLeft,
                size = cropRect.size,
                style = Stroke(1.5.dp.toPx())
            )

            // Corner L-handles & edge midpoint handles
            val arm = min(20.dp.toPx(), min(cropRect.width, cropRect.height) * 0.28f)
            val edgeBar = min(16.dp.toPx(), min(cropRect.width, cropRect.height) * 0.22f)
            val handleStroke = 3.5.dp.toPx()
            val handleColor = tokens.accent

            // Top-Left
            drawLine(handleColor, cropRect.topLeft, cropRect.topLeft + Offset(arm, 0f), handleStroke, StrokeCap.Round)
            drawLine(handleColor, cropRect.topLeft, cropRect.topLeft + Offset(0f, arm), handleStroke, StrokeCap.Round)
            // Top-Right
            drawLine(handleColor, cropRect.topRight, cropRect.topRight + Offset(-arm, 0f), handleStroke, StrokeCap.Round)
            drawLine(handleColor, cropRect.topRight, cropRect.topRight + Offset(0f, arm), handleStroke, StrokeCap.Round)
            // Bottom-Left
            drawLine(handleColor, cropRect.bottomLeft, cropRect.bottomLeft + Offset(arm, 0f), handleStroke, StrokeCap.Round)
            drawLine(handleColor, cropRect.bottomLeft, cropRect.bottomLeft + Offset(0f, -arm), handleStroke, StrokeCap.Round)
            // Bottom-Right
            drawLine(handleColor, cropRect.bottomRight, cropRect.bottomRight + Offset(-arm, 0f), handleStroke, StrokeCap.Round)
            drawLine(handleColor, cropRect.bottomRight, cropRect.bottomRight + Offset(0f, -arm), handleStroke, StrokeCap.Round)

            // Edge midpoint bars
            val midX = cropRect.center.x
            val midY = cropRect.center.y
            drawLine(Color.White, Offset(midX - edgeBar / 2f, cropRect.top), Offset(midX + edgeBar / 2f, cropRect.top), handleStroke, StrokeCap.Round)
            drawLine(Color.White, Offset(midX - edgeBar / 2f, cropRect.bottom), Offset(midX + edgeBar / 2f, cropRect.bottom), handleStroke, StrokeCap.Round)
            drawLine(Color.White, Offset(cropRect.left, midY - edgeBar / 2f), Offset(cropRect.left, midY + edgeBar / 2f), handleStroke, StrokeCap.Round)
            drawLine(Color.White, Offset(cropRect.right, midY - edgeBar / 2f), Offset(cropRect.right, midY + edgeBar / 2f), handleStroke, StrokeCap.Round)
        }
    }
}
