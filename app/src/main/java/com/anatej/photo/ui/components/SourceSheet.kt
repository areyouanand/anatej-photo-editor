package com.anatej.photo.ui.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatej.photo.ui.theme.LocalAnatej

/**
 * Explicit source chooser. We never assume Google Photos: the user decides between the
 * system file browser (SAF) and the Android photo picker (which may be backed by Google
 * Photos, a gallery, or the local media store depending on the device).
 */
@Composable
fun SourceSheet(
    onDismiss: () -> Unit,
    onLibrary: () -> Unit,
    onFiles: () -> Unit,
    onPhotoPicker: () -> Unit
) {
    val t = LocalAnatej.current
    val sheetShape = RoundedCornerShape(topStart = t.cardCorner, topEnd = t.cardCorner)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable { onDismiss() }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(sheetShape)
                .background(t.panel)
                .border(1.dp, t.line, sheetShape)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(t.line)
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoLabel("OPEN FROM", t.accent)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .clickable { onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    MonoLabel("CLOSE", t.textDim)
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choose a source",
                style = MaterialTheme.typography.headlineMedium,
                color = t.text
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Nothing is uploaded. The file is read on this device and never leaves it.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textDim
            )

            Spacer(Modifier.height(16.dp))

            SourceRow(
                number = "01",
                title = "Anatej library",
                body = "On-device MediaStore grid · no cloud provider",
                ticker = "LOCAL",
                onClick = onLibrary
            )
            Spacer(Modifier.height(10.dp))
            SourceRow(
                number = "02",
                title = "Files on this device",
                body = "System file browser · folders, SD card, downloads",
                ticker = "SAF",
                onClick = onFiles
            )
            Spacer(Modifier.height(10.dp))
            SourceRow(
                number = "03",
                title = "Android photo picker",
                body = "Google Photos, Gallery or local media, whichever you choose",
                ticker = "PICKER",
                onClick = onPhotoPicker
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                MonoLabel("NO UPLOAD · NO ACCOUNT · ON-DEVICE ONLY", t.textDim.copy(alpha = 0.7f), size = 8.sp)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SourceRow(
    number: String,
    title: String,
    body: String,
    ticker: String,
    onClick: () -> Unit
) {
    val t = LocalAnatej.current
    val shape = RoundedCornerShape(t.smallCorner)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.panelAlt)
            .border(1.dp, t.line, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelSmall,
            color = t.accent,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = t.text
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ticker,
                    style = MaterialTheme.typography.labelSmall,
                    color = t.textDim.copy(alpha = 0.55f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = t.textDim
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "→",
            style = MaterialTheme.typography.titleLarge,
            color = t.accent
        )
    }
}
