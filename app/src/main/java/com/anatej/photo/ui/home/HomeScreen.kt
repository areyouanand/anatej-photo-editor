package com.anatej.photo.ui.home

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatej.photo.ui.components.AnatejBackdrop
import com.anatej.photo.ui.components.ApertureMark
import com.anatej.photo.ui.components.Hairline
import com.anatej.photo.ui.components.MonoLabel
import com.anatej.photo.ui.components.PerforationStrip
import com.anatej.photo.ui.theme.DesignMode
import com.anatej.photo.ui.theme.LocalAnatej

private data class Feature(val number: String, val title: String, val body: String, val tag: String)

private val features = listOf(
    Feature("01", "Portrait light", "Relight the subject after the shot. Drag to place, then tune intensity, radius and warmth.", "SCAN + LIGHT"),
    Feature("02", "Auto rescue", "A histogram-aware repair for underexposed frames. Lifts shadows without flattening skin.", "AUTO"),
    Feature("03", "Film stocks", "Camera-inspired looks from Fujifilm, Nikon and Hasselblad, plus a clean basic set.", "FILM")
)

@Composable
fun HomeScreen(
    hasSession: Boolean,
    onOpenPhoto: () -> Unit,
    onResume: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit
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
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = "ANATEJ",
                        style = MaterialTheme.typography.labelMedium,
                        color = t.accent,
                        letterSpacing = 6.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "PHOTO EDITOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textDim,
                        letterSpacing = 2.4.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = t.text,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(252.dp)
            ) {
                ApertureMark(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(286.dp)
                        .offset(x = 56.dp, y = (-18).dp),
                    color = t.accent.copy(alpha = if (t.mode == DesignMode.Signature) 0.62f else 0.48f)
                )
                Column(Modifier.align(Alignment.BottomStart)) {
                    MonoLabel("OFFLINE DARKROOM · NO. 001", t.accent)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Light, shaped",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 44.sp,
                            lineHeight = 46.sp
                        ),
                        color = t.text
                    )
                    Text(
                        text = "by hand.",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 44.sp,
                            lineHeight = 46.sp
                        ),
                        color = t.accent
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "A quiet photo editor for portraits and film. Relight a face, rescue a dark frame, finish it with a classic stock.",
                style = MaterialTheme.typography.bodyMedium,
                color = t.textDim,
                modifier = Modifier.widthIn(max = 420.dp)
            )

            Spacer(Modifier.height(26.dp))

            val cardShape = RoundedCornerShape(t.cardCorner)
            val cardColor = if (t.mode == DesignMode.Expressive) t.panelAlt else t.panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(
                        Brush.linearGradient(
                            listOf(cardColor, cardColor.copy(alpha = 0.86f))
                        )
                    )
                    .border(1.dp, if (t.mode == DesignMode.Expressive) Color.Transparent else t.line, cardShape)
                    .clickable { onOpenPhoto() }
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(t.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineMedium,
                        color = t.accentInk,
                        fontSize = 28.sp
                    )
                }
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    MonoLabel("BEGIN A SESSION", t.textDim)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Open a photograph",
                        style = MaterialTheme.typography.titleLarge,
                        color = t.text
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "JPEG · PNG · HEIC · max 51 MP decode",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textDim,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "→",
                    style = MaterialTheme.typography.headlineMedium,
                    color = t.accent
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .clickable { onOpenLibrary() }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoLabel("BROWSE OFFLINE LIBRARY", t.accent)
                Spacer(Modifier.size(6.dp))
                Text("→", style = MaterialTheme.typography.labelMedium, color = t.accent)
            }

            if (hasSession) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .clickable { onResume() }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonoLabel("CONTINUE LAST FRAME", t.accent)
                    Spacer(Modifier.size(6.dp))
                    Text("→", style = MaterialTheme.typography.labelMedium, color = t.accent)
                }
            }

            Spacer(Modifier.height(30.dp))
            PerforationStrip(
                modifier = Modifier.fillMaxWidth().height(9.dp),
                color = t.line,
                holes = 32
            )
            Spacer(Modifier.height(18.dp))

            features.forEachIndexed { index, feature ->
                FeatureRow(feature)
                if (index != features.lastIndex) Hairline(t.line.copy(alpha = 0.7f))
            }

            Spacer(Modifier.height(26.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("100% OFFLINE")
                Badge("NO ACCOUNT")
                Badge("NO CLOUD")
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "ANATEJ · BUILT ON-DEVICE · V0.1",
                style = MaterialTheme.typography.labelSmall,
                color = t.textDim.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FeatureRow(feature: Feature) {
    val t = LocalAnatej.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = feature.number,
            style = MaterialTheme.typography.labelSmall,
            color = t.accent,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                color = t.text
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = feature.body,
                style = MaterialTheme.typography.bodySmall,
                color = t.textDim
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = feature.tag,
            style = MaterialTheme.typography.labelSmall,
            color = t.textDim.copy(alpha = 0.75f),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun Badge(text: String) {
    val t = LocalAnatej.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .border(1.dp, t.line, RoundedCornerShape(100))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = t.textDim,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
    }
}
