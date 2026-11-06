package com.anatej.photo.ui.editor

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import com.anatej.photo.model.EditorUiState
import com.anatej.photo.model.Look
import com.anatej.photo.model.Looks
import com.anatej.photo.model.Lut3D
import com.anatej.photo.model.PortraitLight
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
    private val segmenter = SubjectSegmenter()
    private val depthEstimator = DepthEstimator(application)

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
            val layers = withContext(Dispatchers.Default) { RelightMap.layers(normals, light) }
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
        val bloom = (snapshot.adjustments.bloom + snapshot.look.scaled(snapshot.lookStrength).bloom)
        val sharpness = (snapshot.adjustments.sharpness + snapshot.look.scaled(snapshot.lookStrength).sharpness)
        if (source == null || (bloom <= 0.01f && sharpness <= 0.01f)) {
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
        lutStrength = _state.value.lutStrength
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
                lutStrength = snapshot.lutStrength
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
    }

    fun setLookStrength(strength: Float) {
        record("look-strength")
        _state.update { it.copy(lookStrength = strength.coerceIn(0f, 1f)) }
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

    fun setCrop(aspect: Float) {
        record("crop-aspect")
        _state.update { it.copy(cropAspect = aspect.coerceIn(0f, 3f)) }
    }

    fun setCropZoom(zoom: Float) {
        record("crop-zoom")
        _state.update { it.copy(cropZoom = zoom.coerceIn(1f, 3f)) }
    }

    fun setCropCenter(x: Float, y: Float) {
        _state.update { it.copy(cropCenterX = x.coerceIn(0.05f, 0.95f), cropCenterY = y.coerceIn(0.05f, 0.95f)) }
    }

    fun resetCrop() {
        record("crop-reset", force = true)
        _state.update { it.copy(cropAspect = 0f, cropZoom = 1f, cropCenterX = 0.5f, cropCenterY = 0.5f) }
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
                lut = null,
                lutName = null,
                lutStrength = 1f,
                lutPreview = null
            )
        }
        scheduleRelightOverlay()
        deletePersistedLut()
    }

    fun setCompare(compare: Boolean) {
        _state.update { it.copy(compare = compare) }
    }

    fun importLut(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read that file.")
                    val displayName = queryDisplayName(uri) ?: "Imported LUT"
                    val lut = CubeLutParser.parse(text, displayName.substringBeforeLast('.'))
                    val dir = File(filesDir, "luts").apply { mkdirs() }
                    File(dir, "active.cube").writeText(text)
                    lut
                }
            }
            result.onSuccess { lut ->
                record("lut-import", force = true)
                _state.update {
                    it.copy(
                        lut = lut,
                        lutName = lut.title,
                        lutStrength = 1f,
                        message = "LUT loaded · ${lut.size}³ · ${lut.title}"
                    )
                }
                scheduleLutPreview()
            }.onFailure { error ->
                _state.update { it.copy(message = "LUT rejected: ${error.message}") }
            }
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
                    val finalBitmap = cropBitmap(rendered, snapshot.cropAspect, snapshot.cropZoom, snapshot.cropCenterX, snapshot.cropCenterY)
                    withContext(Dispatchers.IO) { saveToGallery(finalBitmap) }
                    if (finalBitmap !== rendered) finalBitmap.recycle()
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
        return LoadedPhoto(bitmap, name.take(48))
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
        aspect: Float,
        zoom: Float,
        centerX: Float,
        centerY: Float
    ): Bitmap {
        if (aspect <= 0.01f || source.width < 2 || source.height < 2) return source
        val safeZoom = zoom.coerceIn(1f, 3f)
        // Largest rect of the target aspect that fits inside the source, then zoom into it.
        val fitH = minOf(source.height.toFloat(), source.width / aspect)
        val fitW = fitH * aspect
        val w = (fitW / safeZoom).roundToInt().coerceIn(1, source.width)
        val h = (fitH / safeZoom).roundToInt().coerceIn(1, source.height)
        val left = ((centerX * source.width) - w / 2f).roundToInt().coerceIn(0, source.width - w)
        val top = ((centerY * source.height) - h / 2f).roundToInt().coerceIn(0, source.height - h)
        return Bitmap.createBitmap(source, left, top, w, h)
    }

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
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }

        val done = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        resolver.update(uri, done, null, null)
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
        val lutStrength: Float
    )

    private data class LoadedPhoto(val bitmap: Bitmap, val name: String)

    private companion object {
        const val MAX_DECODE_DIM = 2560
        const val THUMB_DIM = 144
    }
}
