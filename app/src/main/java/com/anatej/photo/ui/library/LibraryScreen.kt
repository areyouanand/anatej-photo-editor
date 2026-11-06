package com.anatej.photo.ui.library

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatej.photo.data.LibraryImage
import com.anatej.photo.data.PhotoLibrary
import com.anatej.photo.ui.components.AnatejBackdrop
import com.anatej.photo.ui.components.AnatejButton
import com.anatej.photo.ui.components.MonoLabel
import com.anatej.photo.ui.theme.LocalAnatej
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onPick: (Uri) -> Unit
) {
    val context = LocalContext.current
    val t = LocalAnatej.current

    var granted by remember { mutableStateOf(PhotoLibrary.canRead(context)) }
    var images by remember { mutableStateOf<List<LibraryImage>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val openSystemSettings: () -> Unit = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = PhotoLibrary.canRead(context)
        refreshKey++
    }

    LaunchedEffect(granted, refreshKey) {
        if (!granted) {
            loaded = false
            return@LaunchedEffect
        }
        loaded = false
        images = PhotoLibrary.load(context)
        loaded = true
    }

    AnatejBackdrop {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
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
                Column(Modifier.weight(1f)) {
                    MonoLabel("ON-DEVICE LIBRARY", t.accent)
                    Text(
                        text = if (granted) "${images.size} frames" else "Local media access",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textDim
                    )
                }
                if (granted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .border(1.dp, t.line, RoundedCornerShape(100))
                            .clickable { refreshKey++ }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        MonoLabel("REFRESH", t.textDim)
                    }
                }
            }

            if (!granted) {
                PermissionPanel(
                    onAllow = { permissionLauncher.launch(PhotoLibrary.requiredPermissions()) },
                    onOpenSettings = openSystemSettings
                )
            } else if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = t.accent, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                }
            } else if (images.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MonoLabel("NO PHOTOS FOUND", t.accent)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "The local MediaStore is empty, or Android only granted access to photos you have not selected yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.textDim,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    AnatejButton(
                        text = "Allow more photos",
                        onClick = { permissionLauncher.launch(PhotoLibrary.requiredPermissions()) },
                        filled = false
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .clickable { openSystemSettings() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        MonoLabel("MANAGE IN SYSTEM SETTINGS", t.textDim)
                    }
                }
            } else {
                if (PhotoLibrary.isPartialAccess(context)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MonoLabel("PARTIAL ACCESS · SELECTED PHOTOS ONLY", t.textDim)
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clickable { permissionLauncher.launch(PhotoLibrary.requiredPermissions()) }
                                .padding(4.dp)
                        ) {
                            MonoLabel("CHANGE", t.accent)
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(images, key = { it.id }) { image ->
                        LibraryTile(image = image, onClick = { onPick(image.uri) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPanel(
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val t = LocalAnatej.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MonoLabel("ANATEJ LIBRARY", t.accent)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Browse your photos\nwithout leaving the app.",
            style = MaterialTheme.typography.headlineMedium,
            color = t.text,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Anatej reads thumbnails from Android's local media index. Nothing is uploaded and no cloud provider is contacted.",
            style = MaterialTheme.typography.bodySmall,
            color = t.textDim,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        AnatejButton(text = "Allow library access", onClick = onAllow)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .clickable { onOpenSettings() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            MonoLabel("OPEN SYSTEM SETTINGS", t.textDim)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Permission: READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE (Android 12 and below)",
            style = MaterialTheme.typography.labelSmall,
            color = t.textDim.copy(alpha = 0.55f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LibraryTile(image: LibraryImage, onClick: () -> Unit) {
    val context = LocalContext.current
    val t = LocalAnatej.current
    var bitmap by remember(image.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(image.uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(
                    image.uri,
                    Size(420, 420),
                    null
                )
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(t.smallCorner))
            .background(t.panelAlt)
            .clickable { onClick() }
    ) {
        val preview = bitmap
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = image.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            MonoLabel("…", t.textDim, modifier = Modifier.align(Alignment.Center))
        }
        if (image.width > 0 && image.height > 0) {
            Text(
                text = "${image.width}×${image.height}",
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                fontFamily = FontFamily.Monospace,
                fontSize = 7.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
