package com.anatej.photo

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anatej.photo.data.SettingsStore
import com.anatej.photo.ui.components.SourceSheet
import com.anatej.photo.ui.editor.EditorScreen
import com.anatej.photo.ui.editor.EditorViewModel
import com.anatej.photo.ui.home.HomeScreen
import com.anatej.photo.ui.library.LibraryScreen
import com.anatej.photo.ui.settings.SettingsScreen
import com.anatej.photo.ui.theme.AnatejTheme
import com.anatej.photo.ui.theme.DesignMode

@Composable
fun AnatejApp(pendingUri: MutableState<Uri?>? = null) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }

    var designName by remember { mutableStateOf(settings.designMode.name) }
    val mode = runCatching { DesignMode.valueOf(designName) }.getOrDefault(DesignMode.Signature)
    var scanOnImport by remember { mutableStateOf(settings.scanOnImport) }
    var subjectMasking by remember { mutableStateOf(settings.subjectMasking) }
    var exportQuality by remember { mutableStateOf(settings.exportQuality) }
    var preserveExif by remember { mutableStateOf(settings.preserveExif) }

    var screen by rememberSaveable { mutableStateOf("home") }
    var returnScreen by rememberSaveable { mutableStateOf("home") }
    var showSources by rememberSaveable { mutableStateOf(false) }

    val viewModel: EditorViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(pendingUri?.value) {
        val uri = pendingUri?.value ?: return@LaunchedEffect
        pendingUri.value = null
        viewModel.loadPhoto(uri, scanOnImport, subjectMasking)
        screen = "editor"
    }

    val acceptUri: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            viewModel.loadPhoto(uri, scanOnImport, subjectMasking)
            screen = "editor"
        }
        showSources = false
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        acceptUri(uri)
    }
    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        acceptUri(uri)
    }

    val openPhoto: () -> Unit = { showSources = true }
    val openSettings: () -> Unit = {
        returnScreen = screen
        screen = "settings"
    }
    val openLibrary: () -> Unit = { screen = "library" }

    AnatejTheme(mode = mode) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "screen"
            ) { target ->
                when (target) {
                    "editor" -> EditorScreen(
                        viewModel = viewModel,
                        state = state,
                        onBack = { screen = "home" },
                        onOpenPhoto = openPhoto
                    )

                    "library" -> LibraryScreen(
                        onBack = { screen = "home" },
                        onPick = { uri ->
                            viewModel.loadPhoto(uri, scanOnImport, subjectMasking)
                            screen = "editor"
                        }
                    )

                    "settings" -> SettingsScreen(
                        mode = mode,
                        onModeChange = { newMode ->
                            designName = newMode.name
                            settings.designMode = newMode
                        },
                        scanOnImport = scanOnImport,
                        onScanOnImportChange = { value ->
                            scanOnImport = value
                            settings.scanOnImport = value
                        },
                        subjectMasking = subjectMasking,
                        onSubjectMaskingChange = { value ->
                            subjectMasking = value
                            settings.subjectMasking = value
                        },
                        exportQuality = exportQuality,
                        onExportQualityChange = { value ->
                            exportQuality = value
                            settings.exportQuality = value
                        },
                        preserveExif = preserveExif,
                        onPreserveExifChange = { value ->
                            preserveExif = value
                            settings.preserveExif = value
                        },
                        onBack = { screen = returnScreen },
                        onRestoreDefaults = {
                            settings.restoreDefaults()
                            designName = settings.designMode.name
                            scanOnImport = settings.scanOnImport
                            subjectMasking = settings.subjectMasking
                            exportQuality = settings.exportQuality
                            preserveExif = settings.preserveExif
                        }
                    )

                    else -> HomeScreen(
                        hasSession = state.source != null,
                        onOpenPhoto = openPhoto,
                        onResume = { screen = "editor" },
                        onOpenSettings = openSettings,
                        onOpenLibrary = openLibrary
                    )
                }
            }

            if (showSources) {
                SourceSheet(
                    onDismiss = { showSources = false },
                    onLibrary = {
                        showSources = false
                        screen = "library"
                    },
                    onFiles = { filesPicker.launch(arrayOf("image/*")) },
                    onPhotoPicker = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }
        }
    }

    BackHandler(enabled = showSources || screen != "home") {
        when {
            showSources -> showSources = false
            screen == "settings" -> screen = returnScreen
            screen == "library" -> screen = "home"
            screen == "editor" -> screen = "home"
        }
    }
}
