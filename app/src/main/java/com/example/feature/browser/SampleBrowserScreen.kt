package com.example.feature.browser

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.database.SampleEntity
import com.example.core.design.StudioTopBar
import com.example.core.design.WaveformDisplay
import com.example.core.samples.MicRecordingState
import com.example.ui.theme.LocalStudioTokens
import com.example.ui.theme.StudioRecordRed
import com.example.ui.theme.TrackPalette

@Composable
fun SampleBrowserScreen(
    samples: List<SampleEntity>,
    isMicRecording: Boolean,
    micDurationMs: Long,
    micPeaks: List<Float>,
    onBack: () -> Unit,
    onPreviewSample: (SampleEntity) -> Unit,
    onAssignSample: (SampleEntity) -> Unit,
    onToggleFavorite: (SampleEntity) -> Unit,
    onImportAudioUri: (Uri) -> Unit,
    onStartMicRecord: () -> Unit,
    onStopMicRecord: (String) -> Unit,
    modifier: Modifier = Modifier,
    micState: MicRecordingState = MicRecordingState.READY,
    micError: String? = null
) {
    val tokens = LocalStudioTokens.current
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportAudioUri)
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStartMicRecord()
    }

    val categories = remember(samples) {
        listOf("All", "Favorites", "Recent") + samples.map { it.category }.distinct()
    }

    val filtered = remember(samples, search, selectedCategory) {
        samples.filter { s ->
            val matchSearch = search.isBlank() || s.name.contains(search, ignoreCase = true) || s.folder.contains(search, ignoreCase = true)
            val matchCat = when (selectedCategory) {
                "All" -> true
                "Favorites" -> s.isFavorite
                "Recent" -> true
                else -> s.category == selectedCategory
            }
            matchSearch && matchCat
        }.let { if (selectedCategory == "Recent") it.sortedByDescending { s -> s.lastUsedAt }.take(20) else it }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        StudioTopBar(
            title = "Sample Browser",
            onBack = onBack,
            backTestTag = "sample_browser_back",
            trailingContent = {
                Row {
                    IconButton(
                        onClick = {
                            if (isMicRecording) {
                                onStopMicRecord("Mic ${(System.currentTimeMillis() / 1000) % 1000}")
                            } else {
                                val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (ok) onStartMicRecord() else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(48.dp).testTag("browser_mic_button")
                    ) {
                        Icon(
                            if (isMicRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record Sample",
                            tint = if (isMicRecording) StudioRecordRed else tokens.textSecondary
                        )
                    }
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("audio/*")) },
                        modifier = Modifier.size(48.dp).testTag("browser_import_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Import Audio", tint = TrackPalette[1])
                    }
                }
            }
        )

        if (isMicRecording || micState == MicRecordingState.PROCESSING || (micState == MicRecordingState.ERROR && !micError.isNullOrBlank())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioRecordRed.copy(alpha = 0.14f))
                    .padding(10.dp)
            ) {
                val msg = when {
                    isMicRecording -> "Recording Mic: ${"%.1f".format(micDurationMs / 1000.0)}s"
                    micState == MicRecordingState.PROCESSING -> "Processing & normalizing microphone sample..."
                    else -> micError ?: "Microphone error"
                }
                Text(msg, style = MaterialTheme.typography.labelMedium, color = StudioRecordRed)
                if (isMicRecording) {
                    Spacer(Modifier.height(4.dp))
                    WaveformDisplay(peaks = micPeaks, accentColor = StudioRecordRed, modifier = Modifier.fillMaxWidth().height(48.dp))
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search samples...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("sample_search_input")
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val sel = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (sel) TrackPalette[1] else tokens.padIdle)
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(cat, style = MaterialTheme.typography.labelSmall, color = if (sel) Color.Black else tokens.textSecondary)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.id }) { s ->
                val peaks = remember(s.peaksCsv) { s.peaksCsv.split(",").mapNotNull { it.toFloatOrNull() } }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(tokens.surface)
                        .border(0.5.dp, tokens.border, RoundedCornerShape(4.dp))
                        .clickable { onAssignSample(s) }
                        .padding(8.dp)
                        .testTag("sample_row_${s.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onPreviewSample(s) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview", tint = TrackPalette[1])
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.folder} · ${s.durationMs} ms · ${s.sampleRate / 1000}kHz", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                    }
                    WaveformDisplay(peaks = peaks, accentColor = TrackPalette[3], modifier = Modifier.width(68.dp).height(28.dp))
                    IconButton(onClick = { onToggleFavorite(s) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (s.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (s.isFavorite) TrackPalette[0] else tokens.textMuted
                        )
                    }
                    IconButton(onClick = { onAssignSample(s) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Assign Sample", tint = TrackPalette[2])
                    }
                }
            }
        }
    }
}
