package com.anatej.photo.ui.editor

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anatej.photo.engine.CubeLutParser
import com.anatej.photo.engine.DepthEstimator
import com.anatej.photo.engine.PhotoAnalyzer
import com.anatej.photo.engine.PhotoRenderer
import com.anatej.photo.engine.RelightMap
import com.anatej.photo.engine.SubjectSegmenter
import com.anatej.photo.data.SettingsStore
import com.anatej.photo.model.Adjustments
import com.anatej.photo.model.CropRect
import com.anatej.photo.model.EditorUiState
import com.anatej.photo.model.Look
import com.anatej.photo.model.Looks
import com.anatej.photo.model.Lut3D
import com.anatej.photo.model.LutFileEntry
import com.anatej.photo.model.PortraitLight
import com.anatej.photo.model.SquareBgStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)

    private val _state = MutableStateFlow(EditorUiState(autoRescueStrength = settingsStore.autoRescueStrength))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val resolver = application.contentResolver
    private val filesDir = application.filesDir
    private val cacheDir = application.cacheDir
    private val packageName = application.packageName
    private val segmenter = SubjectSegmenter()
    private val depthEstimator = DepthEstimator(application)
    private var sourceExif: Map<String, String> = emptyMap()

    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()
    private var lastHistoryKey: String? = null
    private var lastHistoryAt = 0L

    private var loadJob: Job? = null
    private var maskJob: Job? = null
    private var depthJob: Job? = null
    private var lutJob: Job? = null
    private var overlayJob: Job? = null
    private var blurJob: Job? = null
    private var previewGeneration = 0
    private var overlayGeneration = 0
    private var blurGeneration = 0
    private var normalGeneration = 0
    private var depthMap: DepthEstimator.DepthMap? = null

    init {
        loadPersistedLut()
        refreshSavedLuts()
    }

    fun loadPhoto(uri: Uri, scan: Boolean = true, subjectMasking: Boolean = true) {
        loadJob?.cancel()
        maskJob?.cancel()
        depthJob?.cancel()
        lutJob?.cancel()
        depthMap = null
        undoStack.clear()
        redoStack.clear()
        lastHistoryKey = null

        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    source = null,
                    analysis = null,
                    thumbs = emptyMap(),
                    subjectMask = null,
                    relightNormals = null,
                    relightOverlay = null,
                    relightShadow = null,
                    bloomOverlay = null,
                    maskScanning = false,
                    lutPreview = null,
                    compare = false,
                    canUndo = false,
                    canRedo = false
                )
            }

            val loaded = runCatching { withContext(Dispatchers.IO) { decode(uri) } }
            loaded.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "That file could not be opened."
                    )
                }
            }
            val photo = loaded.getOrNull() ?: return@launch
            sourceExif = photo.exif

            val analysis = if (scan) {
                runCatching {
                    withContext(Dispatchers.Default) { PhotoAnalyzer.analyze(photo.bitmap) }
                }.getOrNull()
            } else {
                null
            }
            val thumbs = runCatching {
                withContext(Dispatchers.Default) { buildThumbs(photo.bitmap) }
            }.getOrDefault(emptyMap())

            _state.update {
                it.copy(
                    source = photo.bitmap,
                    photoName = photo.name,
                    analysis = analysis,
                    adjustments = Adjustments.Neutral,
                    look = Looks.Original,
                    lookStrength = 1f,
                    light = PortraitLight(),
                    crop = CropRect.Full,
                    squarePic = false,
                    thumbs = thumbs,
                    loading = false,
                    saving = false,
                    compare = false,
                    maskScanning = subjectMasking,
                    error = null,
                    message = if (scan) "Frame scanned on-device" else "Frame loaded"
                )
            }

            scheduleLutPreview()
            scheduleRelightOverlay()
            scheduleBlurOverlay()
            prepareNormals(photo.bitmap, null)
            estimateDepth(photo.bitmap)
            if (subjectMasking) {
                segment(photo.bitmap)
            }
        }
    }

    fun rescan() {
        val source = _state.value.source ?: return
        viewModelScope.launch {
            _state.update { it.copy(message = "Scanning frame…") }
            val analysis = runCatching {
                withContext(Dispatchers.Default) { PhotoAnalyzer.analyze(source) }
            }.getOrNull()
            if (_state.value.source === source) {
                _state.update {
                    it.copy(
                        analysis = analysis,
                        message = if (analysis != null) "Scan complete" else "Scan failed"
                    )
                }
            }
        }
    }

    private fun segment(bitmap: Bitmap) {
        maskJob?.cancel()
        maskJob = viewModelScope.launch {
            val mask = runCatching { segmenter.segment(bitmap) }.getOrNull()
            if (_state.value.source === bitmap) {
                _state.update { it.copy(subjectMask = mask, maskScanning = false) }
                prepareNormals(bitmap, mask)
            }
        }
    }

    private fun estimateDepth(source: Bitmap) {
        depthJob?.cancel()
        depthJob = viewModelScope.launch {
            val depth = runCatching { depthEstimator.estimate(source) }.getOrNull()
            if (depth != null && _state.value.source === source) {
                depthMap = depth
                prepareNormals(source, _state.value.subjectMask)
            }
        }
    }

    private fun prepareNormals(source: Bitmap, mask: Bitmap?) {
        val generation = ++normalGeneration
        val depth = depthMap
        android.util.Log.d(
            "AnatejDepth",
            if (depth != null) "building normals from MiDaS depth" else "building normals from luminance fallback"
        )
        viewModelScope.launch {
            val normals = runCatching {
                withContext(Dispatchers.Default) {
                    if (depth != null) {
                        RelightMap.buildFromDepth(source, depth, mask)
                    } else {
                        RelightMap.build(source, mask)
                    }
                }
            }.getOrNull() ?: return@launch
            if (generation == normalGeneration && _state.value.source === source) {
                _state.update { it.copy(relightNormals = normals) }
                scheduleRelightOverlay()
            }
        }
    }

    private fun scheduleRelightOverlay() {
        val snapshot = _state.value
        val normals = snapshot.relightNormals
        val light = snapshot.light
        if (!light.enabled || normals == null) {
            if (snapshot.relightOverlay != null || snapshot.relightShadow != null) {
                _state.update { it.copy(relightOverlay = null, relightShadow = null) }
            }
            return
        }

        val generation = ++overlayGeneration
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(16)
            val layers = withContext(Dispatchers.Default) { RelightMap.layers(normals, light, snapshot.lutPreview ?: snapshot.source) }
            if (generation == overlayGeneration) {
                _state.update {
                    it.copy(
                        relightOverlay = layers?.light,
                        relightShadow = layers?.shadow
                    )
                }
            }
        }
    }

    private fun scheduleBlurOverlay() {
        val snapshot = _state.value
        val source = snapshot.source
        val scaledLook = snapshot.look.scaled(snapshot.lookStrength)
        val bloom = snapshot.adjustments.bloom + scaledLook.bloom
        val halation = snapshot.adjustments.halation + scaledLook.halation
        val sharpness = snapshot.adjustments.sharpness + scaledLook.sharpness
        val needSquareBlur = snapshot.squarePic && snapshot.squareBgStyle == SquareBgStyle.Blur
        if (source == null || (bloom <= 0.01f && halation <= 0.01f && sharpness <= 0.01f && !needSquareBlur)) {
            if (snapshot.bloomOverlay != null) _state.update { it.copy(bloomOverlay = null) }
            return
        }
        val generation = ++blurGeneration
        blurJob?.cancel()
        blurJob = viewModelScope.launch {
            delay(24)
            val overlay = withContext(Dispatchers.Default) { RelightMap.blurredCopy(source, 384) }
            if (generation == blurGeneration && _state.value.source === source) {
                _state.update { it.copy(bloomOverlay = overlay) }
            }
        }
    }

    private fun record(key: String, force: Boolean = false) {
        if (_state.value.source == null) return
        val now = SystemClock.elapsedRealtime()
        if (!force && key == lastHistoryKey && now - lastHistoryAt < 700L) {
            lastHistoryAt = now
            return
        }
        undoStack.addLast(snapshot())
        while (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        lastHistoryKey = key
        lastHistoryAt = now
        _state.update { it.copy(canUndo = true, canRedo = false) }
    }

    private fun snapshot() = EditSnapshot(
        adjustments = _state.value.adjustments,
        lookId = _state.value.look.id,
        lookStrength = _state.value.lookStrength,
        light = _state.value.light,
        lut = _state.value.lut,
        lutName = _state.value.lutName,
        lutStrength = _state.value.lutStrength,
        crop = _state.value.crop,
        squarePic = _state.value.squarePic
    )

    private fun applySnapshot(snapshot: EditSnapshot) {
        _state.update {
            it.copy(
                adjustments = snapshot.adjustments,
                look = Looks.byId[snapshot.lookId] ?: Looks.Original,
                lookStrength = snapshot.lookStrength,
                light = snapshot.light,
                lut = snapshot.lut,
                lutName = snapshot.lutName,
                lutStrength = snapshot.lutStrength,
                crop = snapshot.crop,
                squarePic = snapshot.squarePic
            )
        }
        scheduleLutPreview()
        scheduleRelightOverlay()
        scheduleBlurOverlay()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        applySnapshot(previous)
        lastHistoryKey = null
        _state.update {
            it.copy(canUndo = undoStack.isNotEmpty(), canRedo = true)
        }
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        applySnapshot(next)
        lastHistoryKey = null
        _state.update {
            it.copy(canUndo = true, canRedo = redoStack.isNotEmpty())
        }
    }

    fun updateAdjustments(key: String, transform: (Adjustments) -> Adjustments) {
        record(key)
        _state.update { it.copy(adjustments = transform(it.adjustments)) }
        scheduleBlurOverlay()
    }

    fun resetAdjustments() {
        record("reset-adjustments", force = true)
        _state.update { it.copy(adjustments = Adjustments.Neutral) }
    }

    fun setLook(look: Look) {
        record("look", force = true)
        _state.update { state ->
            state.copy(
                look = look,
                lookStrength = if (look.id == Looks.Original.id) 1f else state.lookStrength
            )
        }
        scheduleBlurOverlay()
    }

    fun setLookStrength(strength: Float) {
        record("look-strength")
        _state.update { it.copy(lookStrength = strength.coerceIn(0f, 1f)) }
        scheduleBlurOverlay()
    }

    fun updateLight(key: String, transform: (PortraitLight) -> PortraitLight) {
        record(key)
        _state.update { it.copy(light = transform(it.light)) }
        scheduleRelightOverlay()
    }

    fun setLightEnabled(enabled: Boolean) {
        record("light-enabled", force = true)
        _state.update { state ->
            val analysis = state.analysis
            val untouched = state.light.x == 0.5f && state.light.y == 0.38f
            val light = if (enabled && analysis != null && untouched) {
                state.light.copy(
                    enabled = true,
                    x = analysis.subjectX,
                    y = (analysis.subjectY - 0.06f).coerceIn(0.06f, 0.94f)
                )
            } else {
                state.light.copy(enabled = enabled)
            }
            state.copy(light = light)
        }
        scheduleRelightOverlay()
    }

    fun setLightSubjectOnly(subjectOnly: Boolean) {
        record("light-subject-only", force = true)
        _state.update {
            it.copy(
                light = it.light.copy(
                    subjectOnly = subjectOnly,
                    spill = if (subjectOnly) it.light.spill else 1f
                )
            )
        }
        scheduleRelightOverlay()
    }

    /** OPPO-style one-slider brighten: place on the subject and lift with the 0..100 slider. */
    fun brightenFace(value: Float) {
        record("brighten-face")
        _state.update { state ->
            val analysis = state.analysis
            val untouched = state.light.x == 0.5f && state.light.y == 0.38f
            val light = if (analysis != null && untouched) {
                state.light.copy(
                    enabled = true,
                    intensity = value.coerceIn(0f, 100f),
                    x = analysis.subjectX,
                    y = (analysis.subjectY - 0.06f).coerceIn(0.06f, 0.94f),
                    azimuth = 30f,
                    elevation = 35f
                )
            } else {
                state.light.copy(
                    enabled = true,
                    intensity = value.coerceIn(0f, 100f)
                )
            }
            state.copy(light = light)
        }
        scheduleRelightOverlay()
    }

    fun autoPlaceLight() {
        record("auto-place", force = true)
        _state.update { state ->
            val analysis = state.analysis ?: return@update state
            state.copy(
                light = state.light.copy(
                    enabled = true,
                    x = analysis.subjectX,
                    y = (analysis.subjectY - 0.06f).coerceIn(0.05f, 0.95f),
                    azimuth = 30f,
                    elevation = 35f
                )
            )
        }
        scheduleRelightOverlay()
    }

    fun updateCrop(crop: CropRect) {
        record("crop-drag")
        val l = crop.left.coerceIn(0f, 0.92f)
        val t = crop.top.coerceIn(0f, 0.92f)
        val r = crop.right.coerceIn(l + 0.08f, 1f)
        val b = crop.bottom.coerceIn(t + 0.08f, 1f)
        _state.update { it.copy(crop = crop.copy(left = l, top = t, right = r, bottom = b)) }
    }

    fun setCropAspect(aspectLock: Float) {
        record("crop-aspect", force = true)
        _state.update { state ->
            val src = state.source
            if (aspectLock == 0f || src == null || src.width <= 0 || src.height <= 0) {
                state.copy(crop = state.crop.copy(aspectLock = 0f))
            } else {
                val photoAspect = src.width.toFloat() / src.height.toFloat()
                val targetAspect = if (aspectLock < 0f) photoAspect else aspectLock
                val normRatio = (targetAspect / photoAspect).coerceAtLeast(0.05f)
                val cx = (state.crop.left + state.crop.right) * 0.5f
                val cy = (state.crop.top + state.crop.bottom) * 0.5f
                val curAreaScale = kotlin.math.sqrt((state.crop.width * state.crop.height).coerceIn(0.15f, 1f))
                val maxW = if (normRatio >= 1f) 1f else normRatio
                val maxH = if (normRatio >= 1f) 1f / normRatio else 1f
                val scale = if (aspectLock < 0f) 1f else curAreaScale.coerceIn(0.55f, 1f)
                val nw = (maxW * scale).coerceIn(0.08f, 1f)
                val nh = (maxH * scale).coerceIn(0.08f, 1f)
                val left = (cx - nw * 0.5f).coerceIn(0f, 1f - nw)
                val top = (cy - nh * 0.5f).coerceIn(0f, 1f - nh)
                state.copy(
                    crop = CropRect(
                        left = left,
                        top = top,
                        right = left + nw,
                        bottom = top + nh,
                        aspectLock = aspectLock
                    )
                )
            }
        }
    }

    fun rotate90() {
        val current = _state.value
        val src = current.source ?: return
        record("rotate-90", force = true)
        viewModelScope.launch {
            val rotated = withContext(Dispatchers.Default) {
                val m = android.graphics.Matrix().apply { postRotate(90f) }
                Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            }
            val rotatedMask = current.subjectMask?.let { mask ->
                withContext(Dispatchers.Default) {
                    val m = android.graphics.Matrix().apply { postRotate(90f) }
                    Bitmap.createBitmap(mask, 0, 0, mask.width, mask.height, m, true)
                }
            }
            val c = current.crop
            val newAspect = when {
                c.aspectLock > 0f -> 1f / c.aspectLock
                else -> c.aspectLock
            }
            val rotatedCrop = CropRect(
                left = 1f - c.bottom,
                top = c.left,
                right = 1f - c.top,
                bottom = c.right,
                aspectLock = newAspect
            )
            _state.update { st ->
                st.copy(
                    source = rotated,
                    subjectMask = rotatedMask,
                    crop = rotatedCrop,
                    light = st.light.copy(
                        x = 1f - st.light.y,
                        y = st.light.x
                    )
                )
            }
            prepareNormals(rotated, rotatedMask)
            scheduleLutPreview()
            scheduleBlurOverlay()
        }
    }

    fun flipHorizontal() {
        val current = _state.value
        val src = current.source ?: return
        record("flip-h", force = true)
        viewModelScope.launch {
            val flipped = withContext(Dispatchers.Default) {
                val m = android.graphics.Matrix().apply { postScale(-1f, 1f, src.width / 2f, src.height / 2f) }
                Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            }
            val flippedMask = current.subjectMask?.let { mask ->
                withContext(Dispatchers.Default) {
                    val m = android.graphics.Matrix().apply { postScale(-1f, 1f, mask.width / 2f, mask.height / 2f) }
                    Bitmap.createBitmap(mask, 0, 0, mask.width, mask.height, m, true)
                }
            }
            val c = current.crop
            val flippedCrop = c.copy(
                left = 1f - c.right,
                right = 1f - c.left
            )
            _state.update { st ->
                st.copy(
                    source = flipped,
                    subjectMask = flippedMask,
                    crop = flippedCrop,
                    light = st.light.copy(x = 1f - st.light.x)
                )
            }
            prepareNormals(flipped, flippedMask)
            scheduleLutPreview()
            scheduleBlurOverlay()
        }
    }

    fun setCropStraighten(degrees: Float) {
        record("crop-straighten")
        _state.update { state ->
            state.copy(crop = state.crop.copy(straighten = degrees.coerceIn(-45f, 45f)))
        }
    }

    fun setSquarePic(enabled: Boolean) {
        record("square-pic", force = true)
        _state.update { it.copy(squarePic = enabled) }
        scheduleBlurOverlay()
    }

    fun setSquareBlur(blur: Float) {
        _state.update { it.copy(squareBlur = blur.coerceIn(0.15f, 1f)) }
    }

    fun setSquareBgStyle(style: SquareBgStyle) {
        _state.update { it.copy(squareBgStyle = style) }
        scheduleBlurOverlay()
    }

    fun resetCrop() {
        record("crop-reset", force = true)
        _state.update { it.copy(crop = CropRect.Full) }
    }

    fun setAutoRescueStrength(strength: Float) {
        settingsStore.autoRescueStrength = strength.coerceIn(0.3f, 2f)
        _state.update { it.copy(autoRescueStrength = settingsStore.autoRescueStrength) }
    }

    fun autoEnhance() {
        val analysis = _state.value.analysis ?: return
        record("auto-enhance", force = true)
        _state.update { state ->
            val k = state.autoRescueStrength.coerceIn(0.3f, 2f)
            val adjustments = Adjustments(
                exposure = (analysis.suggestedExposure * k).coerceIn(-2f, 2f),
                contrast = (analysis.suggestedContrast * k).coerceIn(-1f, 1f),
                saturation = (analysis.suggestedSaturation * k).coerceIn(-1f, 1f),
                warmth = if (analysis.skinFraction > 0.03f) 0.06f * k else 0f
            )
            val light = if (analysis.subjectConfidence >= 0.25f && analysis.subjectLuma < 0.45f) {
                PortraitLight(
                    enabled = true,
                    x = analysis.subjectX,
                    y = (analysis.subjectY - 0.06f).coerceIn(0.06f, 0.94f),
                    intensity = ((24f + (0.45f - analysis.subjectLuma) * 55f) * k).coerceIn(0f, 100f),
                    radius = 0.58f,
                    warmth = 0.30f
                )
            } else {
                state.light
            }
            state.copy(
                adjustments = adjustments,
                look = Looks.Original,
                lookStrength = 1f,
                light = light,
                message = "Auto rescue applied"
            )
        }
        scheduleRelightOverlay()
    }

    fun resetAll() {
        record("reset-all", force = true)
        _state.update {
            it.copy(
                adjustments = Adjustments.Neutral,
                look = Looks.Original,
                lookStrength = 1f,
                light = PortraitLight(),
                crop = CropRect.Full,
                squarePic = false,
                lut = null,
                lutName = null,
                lutStrength = 1f,
                lutPreview = null
            )
        }
        scheduleRelightOverlay()
        scheduleBlurOverlay()
        deletePersistedLut()
    }

    fun setCompare(compare: Boolean) {
        _state.update { it.copy(compare = compare) }
    }

    fun importLut(uri: Uri) {
        importLuts(listOf(uri))
    }

    fun importLuts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(filesDir, "luts").apply { mkdirs() }
                    var lastLut: Lut3D? = null
                    var importedCount = 0
                    for (uri in uris) {
                        val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: continue
                        val displayName = (queryDisplayName(uri) ?: "Imported LUT").substringBeforeLast('.')
                        val lut = runCatching { CubeLutParser.parse(text, displayName) }.getOrNull() ?: continue
                        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(48).ifBlank { "lut_$importedCount" }
                        File(dir, "$safeName.cube").writeText(text)
                        File(dir, "active.cube").writeText(text)
                        lastLut = lut
                        importedCount++
                    }
                    val lut = lastLut ?: error("No valid 3D .cube LUT found.")
                    Pair(lut, importedCount)
                }
            }
            refreshSavedLuts()
            result.onSuccess { (lut, count) ->
                record("lut-import", force = true)
                _state.update {
                    it.copy(
                        lut = lut,
                        lutName = lut.title,
                        lutStrength = 1f,
                        message = if (count > 1) "$count LUTs imported · active: ${lut.title}"
                        else "LUT loaded · ${lut.size}³ · ${lut.title}"
                    )
                }
                scheduleLutPreview()
            }.onFailure { error ->
                _state.update { it.copy(message = "LUT rejected: ${error.message}") }
            }
        }
    }

    fun selectSavedLut(entry: LutFileEntry) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = File(entry.path)
                    val text = file.readText()
                    val lut = CubeLutParser.parse(text, entry.name)
                    val dir = File(filesDir, "luts").apply { mkdirs() }
                    File(dir, "active.cube").writeText(text)
                    lut
                }
            }
            result.onSuccess { lut ->
                record("lut-select", force = true)
                _state.update {
                    it.copy(
                        lut = lut,
                        lutName = entry.name,
                        lutStrength = if (it.lut == null) 1f else it.lutStrength,
                        message = "LUT · ${entry.name}"
                    )
                }
                scheduleLutPreview()
            }.onFailure { error ->
                _state.update { it.copy(message = "Could not load LUT: ${error.message}") }
            }
        }
    }

    fun refreshSavedLuts() {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = ArrayList<LutFileEntry>()
            val seen = HashSet<String>()
            val internalDir = File(filesDir, "luts")
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LUTs")
            for (dir in listOf(internalDir, downloadDir)) {
                val files = runCatching {
                    dir.listFiles { f -> f.isFile && f.extension.equals("cube", ignoreCase = true) && f.name != "active.cube" }
                }.getOrNull() ?: continue
                for (f in files.sortedBy { it.name.lowercase(Locale.US) }) {
                    val cleanName = f.nameWithoutExtension.replace('_', ' ').trim()
                    val key = cleanName.lowercase(Locale.US)
                    if (seen.add(key)) {
                        entries.add(LutFileEntry(id = f.absolutePath, name = cleanName, path = f.absolutePath))
                    }
                }
            }
            _state.update { it.copy(savedLuts = entries) }
        }
    }

    fun setLutStrength(strength: Float) {
        record("lut-strength")
        _state.update { it.copy(lutStrength = strength.coerceIn(0f, 1f)) }
        scheduleLutPreview()
    }

    fun clearLut() {
        record("lut-clear", force = true)
        _state.update {
            it.copy(
                lut = null,
                lutName = null,
                lutStrength = 1f,
                lutPreview = null,
                message = "LUT removed"
            )
        }
        deletePersistedLut()
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun export() {
        val snapshot = _state.value
        val source = snapshot.source ?: return
        if (snapshot.saving) return

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val outcome = runCatching {
                val rendered = withContext(Dispatchers.Default) {
                    PhotoRenderer.render(
                        source = source,
                        adjustments = snapshot.adjustments,
                        look = snapshot.look,
                        lookStrength = snapshot.lookStrength,
                        light = snapshot.light,
                        maxDim = 3000,
                        lut = snapshot.lut,
                        lutStrength = snapshot.lutStrength,
                        subjectMask = snapshot.subjectMask,
                        relightNormals = snapshot.relightNormals
                    )
                }
                try {
                    val cropped = cropBitmap(rendered, snapshot.crop)
                    val finalBitmap = if (snapshot.squarePic) {
                        padToSquare(cropped, snapshot.squareBgStyle, snapshot.squareBlur)
                    } else {
                        cropped
                    }
                    withContext(Dispatchers.IO) { saveToGallery(finalBitmap) }
                    if (finalBitmap !== cropped) finalBitmap.recycle()
                    if (cropped !== rendered) cropped.recycle()
                } finally {
                    rendered.recycle()
                }
            }
            _state.update {
                it.copy(
                    saving = false,
                    message = outcome.fold(
                        onSuccess = { "Saved to Pictures/Anatej" },
                        onFailure = { error -> "Export failed: ${error.message}" }
                    )
                )
            }
        }
    }

    fun share(onReady: (Uri) -> Unit) {
        val snapshot = _state.value
        val source = snapshot.source ?: return
        if (snapshot.sharing || snapshot.saving) return

        viewModelScope.launch {
            _state.update { it.copy(sharing = true) }
            val outcome = runCatching {
                val rendered = withContext(Dispatchers.Default) {
                    PhotoRenderer.render(
                        source = source,
                        adjustments = snapshot.adjustments,
                        look = snapshot.look,
                        lookStrength = snapshot.lookStrength,
                        light = snapshot.light,
                        maxDim = 3000,
                        lut = snapshot.lut,
                        lutStrength = snapshot.lutStrength,
                        subjectMask = snapshot.subjectMask,
                        relightNormals = snapshot.relightNormals
                    )
                }
                try {
                    val cropped = cropBitmap(rendered, snapshot.crop)
                    val finalBitmap = if (snapshot.squarePic) {
                        padToSquare(cropped, snapshot.squareBgStyle, snapshot.squareBlur)
                    } else {
                        cropped
                    }
                    val shareUri = withContext(Dispatchers.IO) { saveToShareCache(finalBitmap) }
                    if (finalBitmap !== cropped) finalBitmap.recycle()
                    if (cropped !== rendered) cropped.recycle()
                    shareUri
                } finally {
                    rendered.recycle()
                }
            }
            _state.update {
                it.copy(
                    sharing = false,
                    message = outcome.exceptionOrNull()?.let { err -> "Share failed: ${err.message}" }
                )
            }
            outcome.getOrNull()?.let(onReady)
        }
    }

    /** CPU-baked preview used only when a 3D LUT is active (the GPU matrix cannot sample one). */
    private fun scheduleLutPreview() {
        val snapshot = _state.value
        val source = snapshot.source
        val lut = snapshot.lut
        if (source == null || lut == null) {
            if (snapshot.lutPreview != null) {
                _state.update { it.copy(lutPreview = null) }
            }
            return
        }

        val generation = ++previewGeneration
        lutJob?.cancel()
        lutJob = viewModelScope.launch {
            delay(90)
            val preview = withContext(Dispatchers.Default) {
                PhotoRenderer.render(
                    source = source,
                    adjustments = Adjustments.Neutral,
                    look = Looks.Original,
                    lookStrength = 1f,
                    light = PortraitLight(enabled = false),
                    maxDim = 1280,
                    lut = lut,
                    lutStrength = snapshot.lutStrength,
                    bakeEffects = false
                )
            }
            if (generation == previewGeneration) {
                _state.update { it.copy(lutPreview = preview) }
            }
        }
    }

    private fun decode(uri: Uri): LoadedPhoto {
        val exifMap = LinkedHashMap<String, String>()
        runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                for (tag in SAFE_EXIF_TAGS) {
                    val v = exif.getAttribute(tag)
                    if (!v.isNullOrBlank()) exifMap[tag] = v
                }
            }
        }
        val source = ImageDecoder.createSource(resolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_DECODE_DIM) {
                val scale = MAX_DECODE_DIM.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1)
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
        val name = queryDisplayName(uri) ?: "Untitled frame"
        return LoadedPhoto(bitmap, name.take(48), exifMap)
    }

    private fun queryDisplayName(uri: Uri): String? {
        val fromProvider = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        if (!fromProvider.isNullOrBlank()) return fromProvider
        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
    }

    private fun buildThumbs(source: Bitmap): Map<String, Bitmap> {
        val map = LinkedHashMap<String, Bitmap>(Looks.all.size)
        for (look in Looks.all) {
            map[look.id] = PhotoRenderer.render(
                source = source,
                adjustments = Adjustments.Neutral,
                look = look,
                lookStrength = 1f,
                light = PortraitLight(enabled = false),
                maxDim = THUMB_DIM
            )
        }
        return map
    }

    private fun cropBitmap(
        source: Bitmap,
        crop: CropRect
    ): Bitmap {
        if (!crop.isCropped || source.width < 2 || source.height < 2) return source
        val left = (crop.left.coerceIn(0f, 0.95f) * source.width).roundToInt().coerceIn(0, source.width - 1)
        val top = (crop.top.coerceIn(0f, 0.95f) * source.height).roundToInt().coerceIn(0, source.height - 1)
        val right = (crop.right.coerceIn(crop.left + 0.05f, 1f) * source.width).roundToInt().coerceIn(left + 1, source.width)
        val bottom = (crop.bottom.coerceIn(crop.top + 0.05f, 1f) * source.height).roundToInt().coerceIn(top + 1, source.height)
        val outW = (right - left).coerceAtLeast(1)
        val outH = (bottom - top).coerceAtLeast(1)
        if (kotlin.math.abs(crop.straighten) <= 0.05f) {
            return Bitmap.createBitmap(source, left, top, outW, outH)
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val cx = left + outW * 0.5f
        val cy = top + outH * 0.5f
        val sZoom = straightenZoom(crop.straighten, outW.toFloat(), outH.toFloat())
        val m = android.graphics.Matrix().apply {
            postTranslate(-cx, -cy)
            postRotate(crop.straighten)
            postScale(sZoom, sZoom)
            postTranslate(outW * 0.5f, outH * 0.5f)
        }
        canvas.drawBitmap(
            source,
            m,
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        )
        return out
    }

    /** SquarePic: fit the full frame into a square, with blurred or solid bars. */
    private fun padToSquare(
        source: Bitmap,
        bgStyle: SquareBgStyle = SquareBgStyle.Blur,
        blurStrength: Float = 0.75f
    ): Bitmap {
        val side = maxOf(source.width, source.height)
        if (side == 0 || (source.width == side && source.height == side)) return source
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        when (bgStyle) {
            SquareBgStyle.Black -> c.drawColor(android.graphics.Color.BLACK)
            SquareBgStyle.White -> c.drawColor(android.graphics.Color.WHITE)
            SquareBgStyle.Blur -> {
                val cover = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
                val coverScale = maxOf(side.toFloat() / source.width, side.toFloat() / source.height)
                val cw = source.width * coverScale
                val ch = source.height * coverScale
                val coverDst = android.graphics.RectF((side - cw) / 2f, (side - ch) / 2f, (side + cw) / 2f, (side + ch) / 2f)
                android.graphics.Canvas(cover).drawBitmap(source, null, coverDst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                val sampleDim = (180 - (blurStrength.coerceIn(0.15f, 1f) * 116f)).roundToInt().coerceIn(56, 180)
                val b1 = RelightMap.blurredCopy(cover, sampleDim)
                val b2 = RelightMap.blurredCopy(b1, sampleDim)
                val blurred = RelightMap.blurredCopy(b2, sampleDim)
                b1.recycle(); b2.recycle()
                val stretched = Bitmap.createScaledBitmap(blurred, side, side, true)
                c.drawBitmap(stretched, 0f, 0f, null)
                if (stretched !== blurred) stretched.recycle()
                blurred.recycle()
                cover.recycle()
            }
        }
        val fitScale = minOf(
            side / source.width.toFloat(),
            side / source.height.toFloat()
        )
        val dw = (source.width * fitScale).roundToInt().coerceIn(1, side)
        val dh = (source.height * fitScale).roundToInt().coerceIn(1, side)
        val rect = android.graphics.Rect((side - dw) / 2, (side - dh) / 2, (side + dw) / 2, (side + dh) / 2)
        c.drawBitmap(source, null, rect, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun maxOf(a: Int, b: Int) = if (a > b) a else b

    private fun saveToGallery(bitmap: Bitmap) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Anatej_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Anatej")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore rejected the file")

        try {
            val stream = resolver.openOutputStream(uri) ?: error("No output stream")
            stream.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, settingsStore.exportQuality, output)) {
                    error("JPEG encoder failed")
                }
            }
            if (settingsStore.preserveExif && sourceExif.isNotEmpty()) {
                runCatching {
                    resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        for ((tag, value) in sourceExif) {
                            exif.setAttribute(tag, value)
                        }
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Anatej Photo Editor")
                        exif.saveAttributes()
                    }
                }
            }
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }

        val done = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        resolver.update(uri, done, null, null)
    }

    private fun saveToShareCache(bitmap: Bitmap): Uri {
        val dir = File(cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "Anatej_Share.jpg")
        file.outputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, settingsStore.exportQuality, output)) {
                error("JPEG encoder failed")
            }
        }
        if (settingsStore.preserveExif && sourceExif.isNotEmpty()) {
            runCatching {
                val exif = ExifInterface(file)
                for ((tag, value) in sourceExif) {
                    exif.setAttribute(tag, value)
                }
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Anatej Photo Editor")
                exif.saveAttributes()
            }
        }
        return FileProvider.getUriForFile(getApplication(), "$packageName.fileprovider", file)
    }

    private fun loadPersistedLut() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(File(filesDir, "luts"), "active.cube")
            if (!file.exists()) return@launch
            runCatching { CubeLutParser.parse(file.readText(), "Saved LUT") }
                .onSuccess { lut ->
                    _state.update { it.copy(lut = lut, lutName = lut.title) }
                }
        }
    }

    private fun deletePersistedLut() {
        runCatching { File(File(filesDir, "luts"), "active.cube").delete() }
    }

    override fun onCleared() {
        super.onCleared()
        segmenter.close()
        depthEstimator.close()
        loadJob?.cancel()
        maskJob?.cancel()
        depthJob?.cancel()
        lutJob?.cancel()
        overlayJob?.cancel()
    }

    private data class EditSnapshot(
        val adjustments: Adjustments,
        val lookId: String,
        val lookStrength: Float,
        val light: PortraitLight,
        val lut: Lut3D?,
        val lutName: String?,
        val lutStrength: Float,
        val crop: CropRect,
        val squarePic: Boolean
    )

    private data class LoadedPhoto(
        val bitmap: Bitmap,
        val name: String,
        val exif: Map<String, String> = emptyMap()
    )

    private companion object {
        const val MAX_DECODE_DIM = 2560
        const val THUMB_DIM = 144
        val SAFE_EXIF_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH,
            "LensMake",
            "LensModel"
        )
    }
}
