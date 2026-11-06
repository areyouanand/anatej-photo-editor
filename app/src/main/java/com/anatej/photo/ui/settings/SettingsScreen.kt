package com.anatej.photo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import com.anatej.photo.BuildConfig
import com.anatej.photo.ui.components.AnatejBackdrop
import com.anatej.photo.ui.components.AnatejButton
import com.anatej.photo.ui.components.AnatejSwitch
import com.anatej.photo.ui.components.ApertureMark
import com.anatej.photo.ui.components.Hairline
import com.anatej.photo.ui.components.MonoLabel
import com.anatej.photo.ui.components.ValueSlider
import android.net.Uri
import androidx.compose.ui.text.style.TextDecoration
import com.anatej.photo.ui.theme.DesignMode
import com.anatej.photo.ui.theme.LocalAnatej

@Composable
fun SettingsScreen(
    mode: DesignMode,
    onModeChange: (DesignMode) -> Unit,
    scanOnImport: Boolean,
    onScanOnImportChange: (Boolean) -> Unit,
    subjectMasking: Boolean,
    onSubjectMaskingChange: (Boolean) -> Unit,
    exportQuality: Int,
    onExportQualityChange: (Int) -> Unit,
    onBack: () -> Unit,
    onRestoreDefaults: () -> Unit
) {
    val t = LocalAnatej.current

    AnatejBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = t.text
                    )
                }
                Spacer(Modifier.size(4.dp))
                Column {
                    MonoLabel("ANATEJ / SETTINGS", t.accent)
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textDim,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = "Studio settings",
                style = MaterialTheme.typography.displayMedium,
                color = t.text
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tune the workspace, the scan pipeline and how the editor behaves. Everything is stored on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = t.textDim,
                modifier = Modifier.widthIn(max = 460.dp)
            )

            Spacer(Modifier.height(26.dp))
            SectionLabel("01", "APPEARANCE")
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DesignCard(
                    title = "Signature",
                    subtitle = "Darkroom · copper · serif",
                    selected = mode == DesignMode.Signature,
                    background = Color(0xFF0A0A0B),
                    accent = Color(0xFFE8A56A),
                    ink = Color(0xFFF2EDE3),
                    onClick = { onModeChange(DesignMode.Signature) },
                    modifier = Modifier.weight(1f)
                )
                DesignCard(
                    title = "Material 3",
                    subtitle = "Expressive · tonal · rounded",
                    selected = mode == DesignMode.Expressive,
                    background = Color(0xFF121016),
                    accent = Color(0xFFF6C892),
                    ink = Color(0xFFB8C7E8),
                    onClick = { onModeChange(DesignMode.Expressive) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("02", "EDITING")
            Spacer(Modifier.height(6.dp))

            SettingRow(
                title = "Scan on import",
                body = "Analyse the histogram and find a subject immediately.",
                checked = scanOnImport,
                onCheckedChange = onScanOnImportChange
            )
            Hairline(t.line.copy(alpha = 0.7f))
            SettingRow(
                title = "Subject masking",
                body = "Constrain portrait light to the detected person using the bundled segmentation model.",
                checked = subjectMasking,
                onCheckedChange = onSubjectMaskingChange
            )
            Hairline(t.line.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            ValueSlider(
                label = "Export quality",
                value = exportQuality.toFloat(),
                range = 70f..100f,
                onChange = { onExportQualityChange(it.roundToInt()) },
                format = { "${it.roundToInt()}%" },
                enabled = true,
                compact = true
            )
            Text(
                text = "JPEG quality written to Pictures/Anatej. 95% is balanced; 100% is largest.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textDim
            )
            Hairline(t.line.copy(alpha = 0.7f))

            Spacer(Modifier.height(24.dp))
            SectionLabel("03", "ABOUT")
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(t.panelAlt)
                        .border(1.dp, t.line, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ApertureMark(
                        modifier = Modifier.size(54.dp),
                        color = t.accent.copy(alpha = 0.85f),
                        animate = false
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(
                        text = "Anatej Photo Editor",
                        style = MaterialTheme.typography.titleLarge,
                        color = t.text
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.DEBUG) "debug" else "release"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textDim,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Offline portrait & film studio",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textDim
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "A quiet photo editor for portraits and film. Relight a face, rescue a dark frame, finish it with a classic stock or a 3D LUT. Nothing leaves the phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = t.textDim
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutBadge("100% OFFLINE")
                AboutBadge("NO ACCOUNT")
                AboutBadge("NO CLOUD")
            }

            Spacer(Modifier.height(20.dp))
            AboutFact(
                "PROCESSING",
                "96 px histogram scan · ML Kit selfie segmentation · MiDaS-small depth normals with feathered subject blending · 3D .cube LUTs · CPU export renderer."
            )
            Hairline(t.line.copy(alpha = 0.7f))
            AboutFact(
                "PRIVACY",
                "No INTERNET permission. No analytics, accounts, uploads or cloud providers. The local MediaStore is read only when you open the library."
            )
            Hairline(t.line.copy(alpha = 0.7f))
            AboutFact(
                "EXPORT",
                "JPEG at ${exportQuality}% into Pictures/Anatej. Source files are never modified."
            )
            Hairline(t.line.copy(alpha = 0.7f))
            AboutFact(
                "CREDITS",
                "Kotlin · Jetpack Compose · AndroidX · Google ML Kit · MiDaS-small depth model (MIT, bundled in the APK)."
            )
            Hairline(t.line.copy(alpha = 0.7f))
            AboutFact(
                "DEVELOPER",
                "@areyouanand"
            )
            Hairline(t.line.copy(alpha = 0.7f))
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                MonoLabel("GITHUB", t.accent, size = 8.sp)
                Spacer(Modifier.height(4.dp))
                val context = LocalContext.current
                Text(
                    text = "github.com/areyouanand/anatej-photo-editor",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.accent,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/areyouanand/anatej-photo-editor")))
                    }
                )
            }

            Spacer(Modifier.height(22.dp))
            AnatejButton(
                text = "Restore defaults",
                onClick = onRestoreDefaults,
                filled = false
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "ANATEJ · MADE ON-DEVICE · © 2026",
                style = MaterialTheme.typography.labelSmall,
                color = t.textDim.copy(alpha = 0.65f),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.3.sp
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SectionLabel(number: String, title: String) {
    val t = LocalAnatej.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(number, t.accent)
            Spacer(Modifier.size(10.dp))
            MonoLabel(title, t.textDim)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(t.accent.copy(alpha = 0.6f))
            )
        }
        Spacer(Modifier.height(8.dp))
        Hairline(t.line)
    }
}

@Composable
private fun DesignCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    background: Color,
    accent: Color,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(if (selected) t.cardCorner else t.smallCorner)
    Column(
        modifier = modifier
            .clip(shape)
            .background(t.panelAlt)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) t.accent else t.line,
                shape = shape
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(background)
                    .border(1.dp, t.line, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .border(1.dp, if (selected) t.accent else t.line, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(t.accent))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = t.text
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = t.textDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(background))
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(accent))
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ink))
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val t = LocalAnatej.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = t.text
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = t.textDim
            )
        }
        Spacer(Modifier.size(14.dp))
        AnatejSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutFact(label: String, body: String) {
    val t = LocalAnatej.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        MonoLabel(label, t.accent, size = 8.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = t.textDim
        )
    }
}

@Composable
private fun AboutBadge(text: String) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(100)
    Box(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, t.line, shape)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = t.textDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp
        )
    }
}
