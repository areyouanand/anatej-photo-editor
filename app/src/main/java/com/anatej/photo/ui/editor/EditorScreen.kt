package com.anatej.photo.ui.editor

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import com.anatej.photo.model.Adjustments
import com.anatej.photo.model.EditorUiState
import com.anatej.photo.model.Look
import com.anatej.photo.model.LookSeries
import com.anatej.photo.model.Looks
import com.anatej.photo.ui.components.AnatejBackdrop
import com.anatej.photo.ui.components.AnatejButton
import com.anatej.photo.ui.components.AnatejSwitch
import com.anatej.photo.ui.components.Hairline
import com.anatej.photo.ui.components.MonoLabel
import com.anatej.photo.ui.components.ValueSlider
import com.anatej.photo.ui.theme.DesignMode
import com.anatej.photo.ui.theme.LocalAnatej
import java.util.Locale
import kotlin.math.roundToInt

private enum class EditorTab(val label: String) {
    Auto("AUTO"),
    Light("LIGHT"),
    Adjust("ADJUST"),
    Looks("LOOKS"),
    Crop("CROP"),
    SquarePic("SQUARE")
}

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    state: EditorUiState,
    onBack: () -> Unit,
    onOpenPhoto: () -> Unit
) {
    val tokens = LocalAnatej.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = EditorTab.entries
    val selectedTab = tabs[tabIndex.coerceIn(0, tabs.lastIndex)]
    val snackbar = remember { SnackbarHostState() }
    var showMask by rememberSaveable { mutableStateOf(false) }
    var gestureIsLight by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importLuts(uris)
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            viewModel.clearMessage()
            snackbar.showSnackbar(message)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            viewModel.clearError()
            snackbar.showSnackbar(error)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnatejBackdrop {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                EditorTopBar(
                    state = state,
                    onBack = onBack,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onShare = {
                        viewModel.share { uri ->
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, "Share photograph"))
                            }
                        }
                    },
                    onExport = viewModel::export
                )

                PreviewArea(
                    state = state,
                    viewModel = viewModel,
                    lightEditable = selectedTab == EditorTab.Light && gestureIsLight,
                    cropEditable = selectedTab == EditorTab.Crop,
                    showMask = showMask,
                    onShowMaskChange = { showMask = it },
                    onInteractionMode = { gestureIsLight = it },
                    onOpenPhoto = onOpenPhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                )

                AnalysisRibbon(state)

                ToolTabs(
                    selectedIndex = tabIndex,
                    tabs = tabs,
                    onSelect = { tabIndex = it }
                )

                PanelContainer {
                    when (selectedTab) {
                        EditorTab.Auto -> AutoPanel(state, viewModel)
                        EditorTab.Light -> LightPanel(
                            state = state,
                            viewModel = viewModel,
                            showMask = showMask,
                            onShowMaskChange = { showMask = it },
                            gestureIsLight = gestureIsLight,
                            onInteractionMode = { gestureIsLight = it }
                        )
                        EditorTab.Adjust -> AdjustPanel(state, viewModel)
                        EditorTab.Crop -> CropPanel(state = state, viewModel = viewModel)
                        EditorTab.SquarePic -> SquarePicPanel(state = state, viewModel = viewModel)
                        EditorTab.Looks -> LooksPanel(
                            state = state,
                            viewModel = viewModel,
                            onImportLut = { lutPicker.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun EditorTopBar(
    state: EditorUiState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit
) {
    val t = LocalAnatej.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = t.text
            )
        }
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(
                text = "ANATEJ",
                style = MaterialTheme.typography.labelSmall,
                color = t.accent,
                letterSpacing = 3.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = state.photoName,
                style = MaterialTheme.typography.bodySmall,
                color = t.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        GlyphButton(
            glyph = "↶",
            description = "Undo",
            enabled = state.canUndo,
            onClick = onUndo
        )
        GlyphButton(
            glyph = "↷",
            description = "Redo",
            enabled = state.canRedo,
            onClick = onRedo
        )
        GlyphButton(
            glyph = "↗",
            description = "Share",
            enabled = !state.sharing && !state.saving && state.source != null,
            onClick = onShare
        )
        Spacer(Modifier.width(4.dp))
        AnatejButton(
            text = if (state.saving) "SAVING" else "SAVE",
            onClick = onExport,
            enabled = !state.saving && !state.sharing && state.source != null
        )
    }
}

@Composable
private fun GlyphButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val t = LocalAnatej.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = description }
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) t.text else t.textDim.copy(alpha = 0.28f),
            fontFamily = FontFamily.Serif
        )
    }
}

@Composable
private fun PreviewArea(
    state: EditorUiState,
    viewModel: EditorViewModel,
    lightEditable: Boolean,
    cropEditable: Boolean,
    showMask: Boolean,
    onShowMaskChange: (Boolean) -> Unit,
    onInteractionMode: (Boolean) -> Unit,
    onOpenPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(if (cropEditable) 4.dp else t.cardCorner)
    var zoom by remember(state.source) { mutableFloatStateOf(1f) }
    var pan by remember(state.source) { mutableStateOf(Offset.Zero) }
    var holdCompare by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val source = state.source
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val frameModifier = if (source != null && source.width > 0 && source.height > 0 && maxWidth > 0.dp && maxHeight > 0.dp) {
            val targetAspect = when {
                state.squarePic && !(state.compare || holdCompare) -> 1f
                !cropEditable && state.crop.isCropped && !(state.compare || holdCompare) ->
                    ((state.crop.width * source.width) / (state.crop.height * source.height)).coerceIn(0.25f, 4f)
                else -> (source.width.toFloat() / source.height.toFloat()).coerceIn(0.25f, 4f)
            }
            val containerAspect = maxWidth / maxHeight
            val (frameW, frameH) = if (targetAspect >= containerAspect) {
                maxWidth to (maxWidth / targetAspect).coerceIn(140.dp, maxHeight)
            } else {
                (maxHeight * targetAspect).coerceIn(160.dp, maxWidth) to maxHeight
            }
            Modifier
                .size(frameW, frameH)
                .border(1.dp, t.line, shape)
                .clip(shape)
                .background(Color.Black)
        } else {
            Modifier
                .fillMaxSize()
                .border(1.dp, t.line, shape)
                .clip(shape)
                .background(Color.Black)
        }

        Box(modifier = frameModifier) {
        if (source != null) {
            EditorCanvas(
                bitmap = source,
                adjustments = state.adjustments,
                look = state.look,
                lookStrength = state.lookStrength,
                light = state.light,
                lutPreview = state.lutPreview,
                subjectMask = state.subjectMask,
                relightOverlay = state.relightOverlay,
                relightShadow = state.relightShadow,
                bloomOverlay = state.bloomOverlay,
                compare = state.compare || holdCompare,
                lightEditable = lightEditable,
                cropEditable = cropEditable,
                crop = state.crop,
                onCropChange = viewModel::updateCrop,
                squarePic = state.squarePic,
                squareBlur = state.squareBlur,
                squareBgStyle = state.squareBgStyle,
                showMask = showMask,
                zoom = zoom,
                pan = pan,
                onTransform = { newZoom, newPan ->
                    zoom = newZoom
                    pan = newPan
                },
                onCompareHold = { active ->
                    holdCompare = active
                    if (active) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onLightPosition = { x, y ->
                    viewModel.updateLight("light-position") { it.copy(x = x, y = y) }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (cropEditable) Modifier.padding(14.dp) else Modifier)
            )

            if (!cropEditable) {
            PreviewChip(
                text = if (state.compare || holdCompare) "ORIGINAL" else "LIVE GRADE",
                active = !(state.compare || holdCompare),
                onClick = { viewModel.setCompare(!state.compare) },
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
            )

            if (state.light.enabled) {
                PreviewChip(
                    text = if (lightEditable) "DRAG LIGHT" else "LIGHT ON",
                    active = true,
                    onClick = { viewModel.setLightEnabled(false) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                )
            }

            val badge = when {
                state.squarePic -> "SQUARE PIC · ${state.squareBgStyle.label}"
                state.crop.isCropped -> {
                    val cw = (state.crop.width * source.width).roundToInt()
                    val ch = (state.crop.height * source.height).roundToInt()
                    "CROP · $cw × $ch"
                }
                state.lut != null -> "${state.lutName ?: "LUT"} · ${(state.lutStrength * 100).roundToInt()}%"
                state.look.id != Looks.Original.id -> "${state.look.name} · ${(state.lookStrength * 100).roundToInt()}%"
                else -> "${source.width} × ${source.height}"
            }
            PreviewChip(
                text = badge,
                active = state.squarePic || state.crop.isCropped || state.lut != null || state.look.id != Looks.Original.id,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
            )

            ZoomControls(
                zoom = zoom,
                onZoomOut = {
                    zoom = (zoom / 1.5f).coerceAtLeast(1f)
                    if (zoom <= 1.001f) pan = Offset.Zero
                },
                onZoomIn = {
                    zoom = (zoom * 1.5f).coerceAtMost(6f)
                },
                onReset = {
                    zoom = 1f
                    pan = Offset.Zero
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
            )
            }
        } else if (state.loading) {
            LoadingPreview()
        } else {
            EmptyPreview(onOpenPhoto)
        }
        }
    }
}

@Composable
private fun LoadingPreview() {
    val t = LocalAnatej.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = t.accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.height(16.dp))
        MonoLabel("READING FRAME", t.textDim)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Scanning histogram and subject",
            style = MaterialTheme.typography.bodySmall,
            color = t.textDim.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun EmptyPreview(onOpenPhoto: () -> Unit) {
    val t = LocalAnatej.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MonoLabel("NO FRAME LOADED", t.accent)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Open a photograph\nto begin.",
            style = MaterialTheme.typography.headlineMedium,
            color = t.text
        )
        Spacer(Modifier.height(20.dp))
        AnatejButton(text = "Open photo", onClick = onOpenPhoto)
    }
}

@Composable
private fun AnalysisRibbon(state: EditorUiState) {
    val t = LocalAnatej.current
    val analysis = state.analysis
    val accentDot = when {
        analysis == null -> t.textDim.copy(alpha = 0.5f)
        analysis.isDark -> t.accent
        else -> t.good
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accentDot))
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                analysis != null && analysis.subjectConfidence >= 0.25f -> "${analysis.verdict.uppercase()} · SUBJ"
                analysis != null -> analysis.verdict.uppercase()
                state.source != null -> "NOT SCANNED"
                else -> "SCANNING FRAME"
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = t.textDim,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = analysis?.let {
                String.format(
                    Locale.US,
                    "EV %+.2f · S %.0f%%",
                    it.suggestedExposure,
                    it.subjectConfidence * 100
                )
            } ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = t.textDim.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun ToolTabs(
    selectedIndex: Int,
    tabs: List<EditorTab>,
    onSelect: (Int) -> Unit
) {
    val t = LocalAnatej.current
    if (t.mode == DesignMode.Expressive) {
        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            containerColor = Color.Transparent,
            contentColor = t.accent,
            divider = {}
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    text = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (index == selectedIndex) t.accent else t.textDim
                        )
                    }
                )
            }
        }
        return
    }

    val shape = RoundedCornerShape(t.smallCorner)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clip(shape)
            .background(t.panel)
            .border(1.dp, t.line, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val itemShape = RoundedCornerShape(t.smallCorner - 2.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(itemShape)
                    .background(if (selected) t.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "0${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) t.accent else t.textDim.copy(alpha = 0.55f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.sp
                )
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) t.accent else t.textDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PanelContainer(content: @Composable () -> Unit) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(topStart = t.cardCorner, topEnd = t.cardCorner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(296.dp)
            .border(1.dp, t.line, shape)
            .clip(shape)
            .background(t.panel)
    ) {
        content()
    }
}

@Composable
private fun AutoPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val t = LocalAnatej.current
    val analysis = state.analysis

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel("SIGNAL ANALYSIS", t.accent)
            Spacer(Modifier.weight(1f))
            MonoLabel(
                text = when {
                    analysis == null -> "NOT SCANNED"
                    analysis.subjectConfidence >= 0.25f -> "SUBJECT LOCK"
                    else -> "NO SUBJECT"
                },
                color = t.textDim
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = analysis?.verdict ?: "Frame not scanned",
            style = MaterialTheme.typography.titleLarge,
            color = t.text
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = analysis?.suggestion
                ?: "Scan on import is off. Run a manual histogram and subject scan.",
            style = MaterialTheme.typography.bodySmall,
            color = t.textDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(
                label = "MEDIAN",
                value = analysis?.let { "${(it.medianLuma * 100).roundToInt()}%" } ?: "—",
                hint = "luma",
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = "SHADOWS",
                value = analysis?.let { "${(it.darkFraction * 100).roundToInt()}%" } ?: "—",
                hint = "crushed",
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = "RANGE",
                value = analysis?.let { "${(it.dynamicRange * 100).roundToInt()}%" } ?: "—",
                hint = "P5–P95",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        ValueSlider(
            label = "Auto rescue strength",
            value = state.autoRescueStrength,
            range = 0.3f..2f,
            onChange = viewModel::setAutoRescueStrength,
            format = { "${(it * 100).roundToInt()}%" },
            enabled = state.source != null
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnatejButton(
                text = if (analysis == null) "Scan frame" else "Auto-rescue",
                onClick = if (analysis == null) viewModel::rescan else viewModel::autoEnhance,
                modifier = Modifier.weight(1f),
                enabled = state.source != null
            )
            AnatejButton(
                text = "Reset all",
                onClick = viewModel::resetAll,
                modifier = Modifier.weight(1f),
                filled = false
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(t.smallCorner)
    Column(
        modifier = modifier
            .clip(shape)
            .background(t.panelAlt)
            .border(1.dp, t.line, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        MonoLabel(label, t.textDim, size = 8.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = t.text
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = t.textDim.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp
        )
    }
}

private data class LightPreset(
    val name: String,
    val azimuth: Float,
    val elevation: Float,
    val softness: Float,
    val fill: Float,
    val radius: Float,
    val intensity: Float,
    val warmth: Float
)

private val LightPresets = listOf(
    LightPreset("Natural", 30f, 35f, 0.5f, 0.22f, 0.6f, 42f, 0.30f),
    LightPreset("Rembrandt", 45f, 40f, 0.35f, 0.15f, 0.55f, 55f, 0.40f),
    LightPreset("Butterfly", 0f, 55f, 0.6f, 0.35f, 0.45f, 48f, 0.35f),
    LightPreset("Loop", 25f, 50f, 0.55f, 0.30f, 0.5f, 45f, 0.28f),
    LightPreset("Side", 75f, 25f, 0.3f, 0.10f, 0.5f, 60f, 0.45f),
    LightPreset("Top", 0f, 75f, 0.4f, 0.20f, 0.5f, 52f, 0.20f)
)

@Composable
private fun LightPanel(
    state: EditorUiState,
    viewModel: EditorViewModel,
    showMask: Boolean,
    onShowMaskChange: (Boolean) -> Unit,
    gestureIsLight: Boolean,
    onInteractionMode: (Boolean) -> Unit
) {
    val t = LocalAnatej.current
    val light = state.light
    val analysis = state.analysis
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Portrait light",
                    style = MaterialTheme.typography.titleLarge,
                    color = t.text
                )
                Text(
                    text = if (light.enabled) "One slider to brighten the face." +
                        " Advanced tools aim and shape it."
                        else "One slider to brighten the face, OPPO-style.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textDim
                )
            }
            AnatejSwitch(
                checked = light.enabled,
                onCheckedChange = viewModel::setLightEnabled
            )
        }

        Spacer(Modifier.height(6.dp))
        ValueSlider(
            label = "Brighten face",
            value = light.intensity.coerceIn(0f, 100f),
            range = 0f..100f,
            onChange = viewModel::brightenFace,
            format = { "${it.roundToInt()} / 100" },
            enabled = true,
            compact = true
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnatejButton(
                text = "Auto-brighten",
                onClick = viewModel::autoPlaceLight,
                modifier = Modifier.weight(1f),
                enabled = analysis != null
            )
            AnatejButton(
                text = if (showAdvanced) "Hide advanced" else "Advanced tools",
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.weight(1f),
                filled = false
            )
        }

        if (!showAdvanced) {
            // Simple mode: nothing else. The 0–30 slider above is the whole job.
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoLabel(text = "TAP ● 0–100 TO BRIGHTEN THE SUBJECT.", color = t.textDim)
            }
        } else {

        Spacer(Modifier.height(8.dp))

        MicroLabel("KEY PRESETS")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp, horizontal = 1.dp)
        ) {
            items(LightPresets) { preset ->
                PresetChip(
                    name = preset.name,
                    selected = preset.name.equals("Natural", ignoreCase = true) &&
                        kotlin.math.abs(light.azimuth - preset.azimuth) < 1f &&
                        kotlin.math.abs(light.elevation - preset.elevation) < 1f,
                    onClick = {
                        viewModel.updateLight("light-preset") {
                            it.copy(
                                enabled = true,
                                azimuth = preset.azimuth,
                                elevation = preset.elevation,
                                softness = preset.softness,
                                fill = preset.fill,
                                radius = preset.radius,
                                intensity = preset.intensity,
                                warmth = preset.warmth
                            )
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("LIGHT DIRECTION")
            Spacer(Modifier.weight(1f))
            MonoLabel(
                text = "AZ ${light.azimuth.roundToInt()}° · EL ${light.elevation.roundToInt()}°",
                color = t.textDim
            )
        }
        Spacer(Modifier.height(4.dp))
        DirectionPad(
            light = light,
            enabled = light.enabled,
            onDirection = { az, el ->
                viewModel.updateLight("light-direction") {
                    it.copy(azimuth = az, elevation = el)
                }
            },
            modifier = Modifier.fillMaxWidth().height(108.dp)
        )

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("KEY")
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegmentedToggle(
                    label = "◉ LIGHT",
                    selected = gestureIsLight,
                    onClick = { onInteractionMode(true) }
                )
                SegmentedToggle(
                    label = "◐ PAN",
                    selected = !gestureIsLight,
                    onClick = { onInteractionMode(false) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("LIGHT STYLE")
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegmentedToggle(
                    label = "◐ SOFTBOX",
                    selected = light.style == com.anatej.photo.model.LightStyle.Softbox,
                    onClick = { viewModel.updateLight("light-style") { it.copy(style = com.anatej.photo.model.LightStyle.Softbox) } }
                )
                SegmentedToggle(
                    label = "◉ FLASH",
                    selected = light.style == com.anatej.photo.model.LightStyle.Flashlight,
                    onClick = { viewModel.updateLight("light-style") { it.copy(style = com.anatej.photo.model.LightStyle.Flashlight) } }
                )
            }
        }

        ValueSlider(
            label = "Intensity",
            value = light.intensity.coerceIn(0f, 100f),
            range = 0f..100f,
            onChange = { value -> viewModel.updateLight("light-intensity") { it.copy(intensity = value) } },
            format = { "${it.roundToInt()} / 100" },
            enabled = light.enabled,
            compact = true
        )
        ValueSlider(
            label = "Radius",
            value = light.radius,
            range = 0.1f..1.6f,
            onChange = { value -> viewModel.updateLight("light-radius") { it.copy(radius = value) } },
            format = { "${(((it - 0.1f) / 1.5f) * 100).roundToInt()}%" },
            enabled = light.enabled,
            compact = true
        )
        ValueSlider(
            label = "Softness",
            value = light.softness,
            range = 0f..1f,
            onChange = { value -> viewModel.updateLight("light-softness") { it.copy(softness = value) } },
            format = ::percent,
            enabled = light.enabled,
            compact = true
        )
        ValueSlider(
            label = "Fill / shadow lift",
            value = light.fill,
            range = 0f..1f,
            onChange = { value -> viewModel.updateLight("light-fill") { it.copy(fill = value) } },
            format = ::percent,
            enabled = light.enabled,
            compact = true
        )
        ValueSlider(
            label = "Warmth",
            value = light.warmth,
            range = -1f..1f,
            onChange = { value -> viewModel.updateLight("light-warmth") { it.copy(warmth = value) } },
            format = ::signedPercent,
            enabled = light.enabled,
            compact = true
        )

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MonoLabel(
                    text = when {
                        state.maskScanning -> "SUBJECT MASK · SCANNING"
                        state.subjectMask != null -> "SUBJECT MASK · SURFACE"
                        state.relightNormals != null -> "SURFACE RELIGHT · READY"
                        else -> "SUBJECT MASK · UNAVAILABLE"
                    },
                    color = if (state.subjectMask != null || state.relightNormals != null) {
                        t.good.copy(alpha = 0.85f)
                    } else {
                        t.textDim
                    }
                )
            }
            if (state.subjectMask != null) {
                SmallAction(
                    text = if (showMask) "HIDE MASK" else "VIEW MASK",
                    onClick = { onShowMaskChange(!showMask) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(text = "SUBJECT MASKING", color = t.textDim)
            Spacer(Modifier.weight(1f))
            AnatejSwitch(
                checked = light.subjectOnly,
                onCheckedChange = viewModel::setLightSubjectOnly
            )
        }
        if (light.subjectOnly) {
            ValueSlider(
                label = "Background spill",
                value = light.spill,
                range = 0f..1f,
                onChange = { value -> viewModel.updateLight("light-spill") { it.copy(spill = value) } },
                format = ::percent,
                enabled = light.enabled,
                compact = true
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(
                text = "POS ${(light.x * 100).roundToInt()} / ${(light.y * 100).roundToInt()}" +
                    if (analysis != null) " · CONF ${(analysis.subjectConfidence * 100).roundToInt()}%" else "",
                color = t.textDim
            )
            Spacer(Modifier.weight(1f))
            AnatejButton(
                text = "Auto-place",
                onClick = viewModel::autoPlaceLight,
                filled = false,
                enabled = analysis != null
            )
        }
        } // advanced tools
    }
}

@Composable
private fun PresetChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(100)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) t.accent else t.panelAlt)
            .border(1.dp, if (selected) t.accent else t.line, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = name.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.accentInk else t.textDim,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SegmentedToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(100)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) t.accent else t.panelAlt)
            .border(1.dp, if (selected) t.accent else t.line, shape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.accentInk else t.textDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun DirectionPad(
    light: com.anatej.photo.model.PortraitLight,
    enabled: Boolean,
    onDirection: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(t.smallCorner)
    val azimuth = light.azimuth
    val elevation = light.elevation
    Canvas(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(t.panelAlt)
            .border(1.dp, t.line, shape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val nx = ((offset.x / size.width) * 2f - 1f).coerceIn(-1f, 1f)
                        val ny = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        onDirection((nx * 90f).coerceIn(-90f, 90f), (ny * 90f).coerceIn(0f, 90f))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val nx = ((change.position.x / size.width) * 2f - 1f).coerceIn(-1f, 1f)
                        val ny = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        onDirection((nx * 90f).coerceIn(-90f, 90f), (ny * 90f).coerceIn(0f, 90f))
                    }
                )
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawLine(t.line, Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())
        drawLine(t.line, Offset(cx, 0f), Offset(cx, size.height), 1.dp.toPx())
        drawCircle(t.line, radius = size.minDimension * 0.28f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        val px = cx + (azimuth / 90f) * (size.width / 2f - 14.dp.toPx())
        val py = cy - (elevation / 90f) * (size.height / 2f - 10.dp.toPx())
        drawLine(
            Color.White.copy(alpha = 0.55f),
            Offset(cx, cy),
            Offset(px, py),
            1.5.dp.toPx()
        )
        drawCircle(t.accent, radius = 9.dp.toPx(), center = Offset(px, py))
        drawCircle(Color.White.copy(alpha = 0.85f), radius = 9.dp.toPx(), center = Offset(px, py), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        drawCircle(t.accent, radius = 2.5.dp.toPx(), center = Offset(px, py))
        drawCircle(t.accent, radius = 3.dp.toPx(), center = Offset(cx, cy))
        drawLine(Color.White.copy(alpha = 0.4f), Offset(cx, cy - 8.dp.toPx()), Offset(cx, cy), 1.dp.toPx())
    }
}

@Composable
private fun MicroLabel(text: String) {
    val t = LocalAnatej.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = t.textDim,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp
    )
}

@Composable
private fun CropPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val t = LocalAnatej.current
    val crop = state.crop
    val lock = crop.aspectLock
    val source = state.source
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Crop & geometry",
                    style = MaterialTheme.typography.titleLarge,
                    color = t.text
                )
                Text(
                    text = "Drag corner or edge handles on the frame, or drag inside to pan. Switch tabs to preview the cropped frame.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textDim
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        MicroLabel("ASPECT RATIO")
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip("FREE", kotlin.math.abs(lock) < 0.01f) { viewModel.setCropAspect(0f) }
            PresetChip("ORIG", lock < -0.5f) { viewModel.setCropAspect(-1f) }
            PresetChip("1:1", kotlin.math.abs(lock - 1f) < 0.02f) { viewModel.setCropAspect(1f) }
            PresetChip("4:5", kotlin.math.abs(lock - 0.8f) < 0.02f) { viewModel.setCropAspect(0.8f) }
            PresetChip("3:2", kotlin.math.abs(lock - 1.5f) < 0.02f) { viewModel.setCropAspect(1.5f) }
            PresetChip("16:9", kotlin.math.abs(lock - 1.7778f) < 0.03f) { viewModel.setCropAspect(1.7778f) }
            PresetChip("9:16", kotlin.math.abs(lock - 0.5625f) < 0.02f) { viewModel.setCropAspect(0.5625f) }
        }
        Spacer(Modifier.height(10.dp))
        ValueSlider(
            label = "Straighten",
            value = crop.straighten,
            range = -45f..45f,
            onChange = viewModel::setCropStraighten,
            format = { String.format(Locale.US, "%+.1f°", it) },
            compact = true,
            defaultValue = 0f
        )
        Spacer(Modifier.height(6.dp))
        MicroLabel("TRANSFORM")
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnatejButton(
                text = "↻ 90°",
                onClick = viewModel::rotate90,
                modifier = Modifier.weight(1f),
                filled = false
            )
            AnatejButton(
                text = "⇄ Flip",
                onClick = viewModel::flipHorizontal,
                modifier = Modifier.weight(1f),
                filled = false
            )
            AnatejButton(
                text = "Reset",
                onClick = viewModel::resetCrop,
                modifier = Modifier.weight(0.85f),
                filled = false,
                enabled = crop.isCropped || lock != 0f
            )
        }
        Spacer(Modifier.height(10.dp))
        val sizeInfo = if (source != null) {
            val cw = (crop.width * source.width).roundToInt()
            val ch = (crop.height * source.height).roundToInt()
            "$cw × $ch PX"
        } else {
            "FULL FRAME"
        }
        MonoLabel(
            text = if (crop.isCropped) {
                "ACTIVE CROP · $sizeInfo · NON-DESTRUCTIVE"
            } else {
                "FULL FRAME · $sizeInfo · DRAG HANDLES TO CROP"
            },
            color = if (crop.isCropped) t.accent else t.textDim
        )
    }
}

private fun Float.nf1(): String = String.format(java.util.Locale.US, "%.1f", this)

@Composable
private fun SquarePicPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val t = LocalAnatej.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Square Pic",
                    style = MaterialTheme.typography.titleLarge,
                    color = t.text
                )
                Text(
                    text = "Fit the entire photo inside a 1:1 square frame without cutting anything off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textDim
                )
            }
            AnatejSwitch(
                checked = state.squarePic,
                onCheckedChange = viewModel::setSquarePic
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MicroLabel("FRAME FILL")
            Spacer(Modifier.weight(1f))
            com.anatej.photo.model.SquareBgStyle.entries.forEach { style ->
                PresetChip(
                    name = style.label,
                    selected = state.squareBgStyle == style,
                    onClick = {
                        if (!state.squarePic) viewModel.setSquarePic(true)
                        viewModel.setSquareBgStyle(style)
                    }
                )
            }
        }
        if (state.squareBgStyle == com.anatej.photo.model.SquareBgStyle.Blur) {
            Spacer(Modifier.height(6.dp))
            ValueSlider(
                label = "Blur intensity",
                value = state.squareBlur,
                range = 0.15f..1f,
                onChange = viewModel::setSquareBlur,
                format = ::percent,
                enabled = state.squarePic,
                compact = true,
                defaultValue = 0.75f
            )
        }
        Spacer(Modifier.height(8.dp))
        AnatejButton(
            text = if (state.squarePic) "Disable Square Pic" else "Enable Square Pic",
            onClick = { viewModel.setSquarePic(!state.squarePic) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        MonoLabel(
            text = if (state.squarePic) "LIVE · 1:1 SQUARE (${state.squareBgStyle.label} BARS)" else "OFF · EXPORT KEEPS ASPECT RATIO",
            color = if (state.squarePic) t.accent else t.textDim
        )
    }
}

@Composable
private fun AdjustPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val t = LocalAnatej.current
    val adjustments = state.adjustments

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Tone & colour",
                style = MaterialTheme.typography.titleLarge,
                color = t.text
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .clickable { viewModel.resetAdjustments() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                MonoLabel("RESET", t.accent)
            }
        }

        ValueSlider(
            label = "Exposure",
            value = adjustments.exposure,
            range = -2f..2f,
            onChange = { value -> viewModel.updateAdjustments("exposure") { it.copy(exposure = value) } },
            format = { String.format(Locale.US, "%+.2f EV", it) }
        )
        ValueSlider(
            label = "Contrast",
            value = adjustments.contrast,
            range = -1f..1f,
            onChange = { value -> viewModel.updateAdjustments("contrast") { it.copy(contrast = value) } },
            format = ::signedPercent
        )
        ValueSlider(
            label = "Highlights",
            value = adjustments.highlights,
            range = -1f..1f,
            onChange = { value -> viewModel.updateAdjustments("highlights") { it.copy(highlights = value) } },
            format = ::signedPercent
        )
        ValueSlider(
            label = "Shadows",
            value = adjustments.shadows,
            range = -1f..1f,
            onChange = { value -> viewModel.updateAdjustments("shadows") { it.copy(shadows = value) } },
            format = ::signedPercent
        )
        ValueSlider(
            label = "Whites",
            value = adjustments.whites,
            range = -1f..1f,
            onChange = { value -> viewModel.updateAdjustments("whites") { it.copy(whites = value) } },
            format = ::signedPercent
        )
        ValueSlider(
            label = "Blacks",
            value = adjustments.blacks,
            range = -1f..1f,
            onChange = { value -> viewModel.updateAdjustments("blacks") { it.copy(blacks = value) } },
            format = ::signedPercent
        )
        ValueSlider(
            label = "Saturation",
            value = adjustments.saturation,
            range = -1f..1f,
            onChange = { value -> viewModel.updateAdjustments("saturation") { it.copy(saturation = value) } },
            format = ::signedPercent
        )
        ValueSlider(
            label = "Warmth",
            value = adjustments.warmth,
            range = -1f..1f,
            onChange = { value -> viewModel.updateAdjustments("warmth") { it.copy(warmth = value) } },
            format = ::signedPercent
        )
        ValueSlider(
            label = "Fade",
            value = adjustments.fade,
            range = 0f..1f,
            onChange = { value -> viewModel.updateAdjustments("fade") { it.copy(fade = value) } },
            format = ::percent
        )
        ValueSlider(
            label = "Vignette",
            value = adjustments.vignette,
            range = 0f..1f,
            onChange = { value -> viewModel.updateAdjustments("vignette") { it.copy(vignette = value) } },
            format = ::percent
        )
        ValueSlider(
            label = "Grain",
            value = adjustments.grain,
            range = 0f..1f,
            onChange = { value -> viewModel.updateAdjustments("grain") { it.copy(grain = value) } },
            format = ::percent
        )
        ValueSlider(
            label = "Sharpness",
            value = adjustments.sharpness,
            range = -1f..1f,
            onChange = { value -> viewModel.updateAdjustments("sharpness") { it.copy(sharpness = value) } },
            format = ::percent
        )
        ValueSlider(
            label = "Bloom",
            value = adjustments.bloom,
            range = 0f..1f,
            onChange = { value -> viewModel.updateAdjustments("bloom") { it.copy(bloom = value) } },
            format = ::percent
        )
        ValueSlider(
            label = "Film halation",
            value = adjustments.halation,
            range = 0f..1f,
            onChange = { value -> viewModel.updateAdjustments("halation") { it.copy(halation = value) } },
            format = ::percent
        )
        ValueSlider(
            label = "Shadow tint (Teal ↔ Amber)",
            value = adjustments.shadowTint,
            range = -1f..1f,
            onChange = { value -> viewModel.updateAdjustments("shadow-tint") { it.copy(shadowTint = value) } },
            format = ::signedPercent
        )
        ValueSlider(
            label = "Highlight tint (Cool ↔ Gold)",
            value = adjustments.highlightTint,
            range = -1f..1f,
            onChange = { value -> viewModel.updateAdjustments("highlight-tint") { it.copy(highlightTint = value) } },
            format = ::signedPercent
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun LooksPanel(
    state: EditorUiState,
    viewModel: EditorViewModel,
    onImportLut: () -> Unit
) {
    val t = LocalAnatej.current
    var seriesIndex by rememberSaveable { mutableIntStateOf(state.look.series.ordinal) }
    val series = LookSeries.entries[seriesIndex.coerceIn(0, LookSeries.entries.lastIndex)]
    val favouriteLooks = remember(state.favouriteLooks) {
        state.favouriteLooks.mapNotNull { Looks.byId[it] }.sortedBy { it.name }
    }
    val favouriteLuts = remember(state.favouriteLuts, state.savedLuts) {
        state.savedLuts.filter { it.id in state.favouriteLuts }
    }

    LaunchedEffect(series) {
        if (series == LookSeries.Lut) {
            viewModel.refreshSavedLuts()
            viewModel.scheduleLutThumbs()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LookSeries.entries.forEachIndexed { index, item ->
                SeriesChip(
                    label = item.label,
                    ticker = item.ticker,
                    selected = index == seriesIndex,
                    onClick = { seriesIndex = index }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (series == LookSeries.Lut) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    MonoLabel("3D LUT · .CUBE", t.accent)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.lut?.let { "${state.lutName ?: it.title} · ${it.size}³" } ?: "Import or pick a .cube LUT",
                        style = MaterialTheme.typography.titleMedium,
                        color = t.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (state.savedLuts.isNotEmpty()) "${state.savedLuts.size} LUTs available · tap a card or import from Downloads/LUTs"
                        else "Select one or multiple 33³ / 65³ .cube files from Downloads/LUTs",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallAction(
                        text = "IMPORT .CUBE",
                        onClick = onImportLut
                    )
                    if (state.lut != null) {
                        SmallAction(
                            text = "CLEAR",
                            onClick = viewModel::clearLut
                        )
                    }
                }
            }

            if (state.savedLuts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.savedLuts, key = { it.id }) { entry ->
                        val active = state.lut != null && (state.lutName.equals(entry.name, ignoreCase = true) || state.lut.title.equals(entry.name, ignoreCase = true))
                        LutCard(
                            name = entry.name,
                            thumbnail = state.lutThumbs[entry.id],
                            selected = active,
                            favourite = entry.id in state.favouriteLuts,
                            onClick = { viewModel.selectSavedLut(entry) },
                            onFavourite = { viewModel.toggleFavouriteLut(entry.id) }
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        PreviewChip(
                            text = "NO LUTS YET",
                            active = false,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }

            if (state.lut != null) {
                Spacer(Modifier.height(8.dp))
                ValueSlider(
                    label = "LUT strength",
                    value = state.lutStrength,
                    range = 0f..1f,
                    onChange = viewModel::setLutStrength,
                    format = ::percent,
                    modifier = Modifier.padding(horizontal = 18.dp),
                    defaultValue = 1f
                )
            }
        } else if (series == LookSeries.Favourites) {
            val hasAny = favouriteLooks.isNotEmpty() || favouriteLuts.isNotEmpty()
            if (!hasAny) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp)) {
                    MonoLabel("NO FAVOURITES YET", t.accent)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tap the ☆ on any look or 3D LUT card to pin it here for one-tap access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textDim
                    )
                }
            }
            if (favouriteLooks.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MicroLabel("FAVOURITE LOOKS")
                    Spacer(Modifier.weight(1f))
                    MonoLabel("${favouriteLooks.size}", t.accent)
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(favouriteLooks, key = { "fav-" + it.id }) { look ->
                        LookCard(
                            look = look,
                            thumbnail = state.thumbs[look.id],
                            selected = state.look.id == look.id,
                            favourite = true,
                            onClick = { viewModel.setLook(look) },
                            onFavourite = { viewModel.toggleFavouriteLook(look.id) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (favouriteLuts.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MicroLabel("FAVOURITE LUTS")
                    Spacer(Modifier.weight(1f))
                    MonoLabel("${favouriteLuts.size}", t.accent)
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(favouriteLuts, key = { "fav-lut-" + it.id }) { entry ->
                        val active = state.lut != null &&
                            (state.lutName.equals(entry.name, ignoreCase = true) ||
                                state.lut.title.equals(entry.name, ignoreCase = true))
                        LutCard(
                            name = entry.name,
                            thumbnail = state.lutThumbs[entry.id],
                            selected = active,
                            favourite = true,
                            onClick = { viewModel.selectSavedLut(entry) },
                            onFavourite = { viewModel.toggleFavouriteLut(entry.id) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (hasAny) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.look.id != Looks.Original.id) state.look.name
                        else state.lutName ?: "Original",
                        style = MaterialTheme.typography.titleMedium,
                        color = t.text
                    )
                    Spacer(Modifier.weight(1f))
                    SmallAction(
                        text = "OPEN ALL LOOKS",
                        onClick = { seriesIndex = LookSeries.Cinema.ordinal }
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(Looks.forSeries(series), key = { it.id }) { look ->
                    LookCard(
                        look = look,
                        thumbnail = state.thumbs[look.id],
                        selected = state.look.id == look.id,
                        favourite = look.id in state.favouriteLooks,
                        onClick = { viewModel.setLook(look) },
                        onFavourite = { viewModel.toggleFavouriteLook(look.id) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.look.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = t.text
                    )
                    Text(
                        text = state.look.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val recipeTags = buildList {
                        if (state.look.grain > 0.01f) add("GRAIN")
                        if (state.look.bloom > 0.01f) add("BLOOM")
                        if (state.look.halation > 0.01f) add("HALATION")
                        if (kotlin.math.abs(state.look.shadowTint) > 0.01f || kotlin.math.abs(state.look.highlightTint) > 0.01f) add("SPLIT-TONE")
                        if (state.look.vignette > 0.01f) add("VIGNETTE")
                        if (state.look.fade > 0.01f) add("MATTE")
                    }
                    if (recipeTags.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        MonoLabel(recipeTags.joinToString(" · "), t.accent.copy(alpha = 0.85f))
                    }
                    if (state.look.id != Looks.Original.id) {
                        Spacer(Modifier.height(2.dp))
                        MonoLabel("TAP AGAIN TO DISABLE", t.textDim.copy(alpha = 0.7f))
                    }
                }
                if (state.look.id != Looks.Original.id) {
                    MonoLabel("${(state.lookStrength * 100).roundToInt()}%", t.accent)
                }
            }

            if (state.look.id != Looks.Original.id) {
                ValueSlider(
                    label = "Stock strength",
                    value = state.lookStrength,
                    range = 0f..1f,
                    onChange = viewModel::setLookStrength,
                    format = ::percent,
                    modifier = Modifier.padding(horizontal = 18.dp),
                    defaultValue = 1f
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(100)
    Box(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, t.accent.copy(alpha = 0.5f), shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        MonoLabel(text, t.accent)
    }
}

@Composable
private fun SeriesChip(
    label: String,
    ticker: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(100)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) t.accent else t.panelAlt)
            .border(1.dp, if (selected) t.accent else t.line, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.accentInk else t.textDim,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = ticker,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.accentInk.copy(alpha = 0.65f) else t.textDim.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp
        )
    }
}

@Composable
private fun LookCard(
    look: Look,
    thumbnail: android.graphics.Bitmap?,
    selected: Boolean,
    favourite: Boolean = false,
    onClick: () -> Unit,
    onFavourite: (() -> Unit)? = null
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(t.smallCorner)
    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(shape)
                .background(t.panelAlt)
                .border(if (selected) 2.dp else 1.dp, if (selected) t.accent else t.line, shape)
        ) {
            if (thumbnail != null) {
                val image = remember(thumbnail) { thumbnail.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = look.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(t.accent)
                )
            }
            if (onFavourite != null) {
                FavouriteBadge(
                    favourite = favourite,
                    onClick = onFavourite,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = look.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.accent else t.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp
        )
    }
}

/** Small star toggle pinned to a card corner. Filled = favourited. */
@Composable
private fun FavouriteBadge(
    favourite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalAnatej.current
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.52f))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (favourite) "★" else "☆",
            style = MaterialTheme.typography.labelMedium,
            color = if (favourite) t.accent else Color.White.copy(alpha = 0.82f),
            fontSize = 12.sp
        )
    }
}

/** A saved .cube rendered through the current frame, with the same affordances as LookCard. */
@Composable
private fun LutCard(
    name: String,
    thumbnail: android.graphics.Bitmap?,
    selected: Boolean,
    favourite: Boolean,
    onClick: () -> Unit,
    onFavourite: () -> Unit
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(t.smallCorner)
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(shape)
                .background(t.panelAlt)
                .border(if (selected) 2.dp else 1.dp, if (selected) t.accent else t.line, shape)
        ) {
            if (thumbnail != null) {
                val image = remember(thumbnail) { thumbnail.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "CUBE",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textDim.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp
                    )
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(t.accent)
                )
            }
            FavouriteBadge(
                favourite = favourite,
                onClick = onFavourite,
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.accent else t.textDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp
        )
    }
}

@Composable
private fun ZoomControls(
    zoom: Float,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(100)
    val canOut = zoom > 1.001f
    val canIn = zoom < 5.999f
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.46f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(enabled = canOut) { onZoomOut() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "−",
                style = MaterialTheme.typography.titleMedium,
                color = if (canOut) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.28f),
                fontFamily = FontFamily.SansSerif
            )
        }
        Box(
            modifier = Modifier
                .clip(shape)
                .clickable { onReset() }
                .padding(horizontal = 5.dp, vertical = 4.dp)
        ) {
            Text(
                String.format(Locale.US, "%.1f×", zoom),
                style = MaterialTheme.typography.labelSmall,
                color = t.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(enabled = canIn) { onZoomIn() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "+",
                style = MaterialTheme.typography.titleMedium,
                color = if (canIn) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.28f),
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun PreviewChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(100)
    var base = modifier
        .clip(shape)
        .background(Color.Black.copy(alpha = 0.56f))
        .border(
            width = 1.dp,
            color = if (active) t.accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.16f),
            shape = shape
        )
    if (onClick != null) {
        base = base.clickable { onClick() }
    }
    Row(
        modifier = base.padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (active) t.accent else Color.White.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) t.accent else Color.White.copy(alpha = 0.82f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp
        )
    }
}

private fun signedPercent(value: Float): String {
    val rounded = (value * 100).roundToInt()
    return when {
        rounded > 0 -> "+$rounded%"
        else -> "$rounded%"
    }
}

private fun percent(value: Float): String = "${(value * 100).roundToInt()}%"
