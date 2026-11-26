package com.example.feature.settings

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.audio.TransportTelemetryState
import com.example.core.common.GlobalQuantization
import com.example.core.common.MelodicPadLayout
import com.example.core.common.MusicalTheory
import com.example.core.common.PerformanceProfile
import com.example.core.common.ScaleType
import com.example.core.common.SettingsState
import com.example.core.design.StudioTopBar
import com.example.core.midi.MidiDeviceSummary
import com.example.core.project.EffectModel
import com.example.core.project.NoteSet
import com.example.feature.instrument.EffectEditorPanel
import com.example.ui.theme.LocalStudioTokens
import com.example.ui.theme.TrackPalette

@Composable
fun SettingsScreen(
    settings: SettingsState,
    telemetry: TransportTelemetryState,
    midiDevices: List<MidiDeviceSummary>,
    connectedMidiInput: String?,
    connectedMidiOutput: String?,
    midiSupported: Boolean,
    onBack: () -> Unit,
    onUpdateSettings: ((SettingsState) -> SettingsState) -> Unit,
    onConnectMidiInput: (Int) -> Unit,
    onDisconnectMidiInput: () -> Unit,
    onConnectMidiOutput: (Int) -> Unit,
    onDisconnectMidiOutput: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        StudioTopBar(title = "Studio Settings", onBack = onBack, backTestTag = "settings_back_button")
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsSection("Display & Theme") {
                ToggleRow("High-Contrast Studio Theme", settings.highContrastTheme) { v ->
                    onUpdateSettings { it.copy(highContrastTheme = v) }
                }
                ToggleRow("Show Melodic Pad Note Labels", settings.showPadNoteLabels) { v ->
                    onUpdateSettings { it.copy(showPadNoteLabels = v) }
                }
                ToggleRow("Show Drum Sample Labels", settings.showSampleLabels) { v ->
                    onUpdateSettings { it.copy(showSampleLabels = v) }
                }
            }

            SettingsSection("Native Audio Engine (Oboe)") {
                ChipSelectRow(
                    label = "Sample Rate",
                    options = listOf(44100 to "44.1 kHz", 48000 to "48 kHz"),
                    selected = settings.sampleRate
                ) { sr -> onUpdateSettings { it.copy(sampleRate = sr) } }

                ChipSelectRow(
                    label = "Buffer Size",
                    options = listOf(128 to "128", 256 to "256", 512 to "512", 1024 to "1024"),
                    selected = settings.bufferFrames
                ) { bf -> onUpdateSettings { it.copy(bufferFrames = bf) } }

                ChipSelectRow(
                    label = "Performance Profile",
                    options = PerformanceProfile.entries.map { it to it.label },
                    selected = settings.performanceProfile
                ) { prof -> onUpdateSettings { it.copy(performanceProfile = prof) } }

                // Diagnostics Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.bg, RoundedCornerShape(4.dp))
                        .border(0.5.dp, tokens.border, RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Text("Engine Diagnostics", style = MaterialTheme.typography.labelMedium, color = TrackPalette[1])
                    Spacer(Modifier.height(4.dp))
                    val latencyMs = (telemetry.bufferFrames * 1000f / telemetry.sampleRate.coerceAtLeast(1))
                    Text(
                        text = "Backend: ${if (telemetry.isAAudio) "AAudio (Low Latency)" else "OpenSL ES"} · ${telemetry.sampleRate} Hz · ${telemetry.bufferFrames} frames (~${"%.1f".format(latencyMs)} ms) · XRuns: ${telemetry.xrunCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                }
            }

            SettingsSection("Musical Defaults") {
                ToggleRow("Metronome Enabled", settings.metronomeEnabled) { v ->
                    onUpdateSettings { it.copy(metronomeEnabled = v) }
                }
                ChipSelectRow(
                    label = "Count-In",
                    options = listOf(0 to "Off", 1 to "1 Bar", 2 to "2 Bars"),
                    selected = settings.countInBars
                ) { c -> onUpdateSettings { it.copy(countInBars = c) } }

                ChipSelectRow(
                    label = "Default Quantization",
                    options = GlobalQuantization.entries.map { it to it.displayName },
                    selected = settings.defaultQuantization
                ) { q -> onUpdateSettings { it.copy(defaultQuantization = q) } }

                ChipSelectRow(
                    label = "Melodic Pad Layout",
                    options = MelodicPadLayout.entries.map { it to it.displayName },
                    selected = settings.melodicLayout
                ) { l -> onUpdateSettings { it.copy(melodicLayout = l) } }

                ChipSelectRow(
                    label = "Default Key",
                    options = MusicalTheory.NOTE_NAMES.mapIndexed { i, n -> i to n },
                    selected = settings.defaultRootNote
                ) { r -> onUpdateSettings { it.copy(defaultRootNote = r) } }

                ChipSelectRow(
                    label = "Default Scale",
                    options = ScaleType.entries.map { it to it.displayName },
                    selected = settings.defaultScale
                ) { s -> onUpdateSettings { it.copy(defaultScale = s) } }
            }

            SettingsSection("MIDI Capture & Overdub") {
                ChipSelectRow(
                    label = "Capture Length Mode",
                    options = com.example.core.common.CaptureMode.entries.map { it to it.label },
                    selected = settings.captureMode
                ) { m -> onUpdateSettings { it.copy(captureMode = m) } }

                if (settings.captureMode == com.example.core.common.CaptureMode.FIXED_LENGTH) {
                    ChipSelectRow(
                        label = "Fixed Capture Bars",
                        options = listOf(1 to "1 Bar", 2 to "2 Bars", 4 to "4 Bars", 8 to "8 Bars", 16 to "16 Bars"),
                        selected = settings.fixedCaptureBars
                    ) { b -> onUpdateSettings { it.copy(fixedCaptureBars = b) } }
                }

                ChipSelectRow(
                    label = "Capture Phrase Timeout (Idle)",
                    options = listOf(4 to "4s", 8 to "8s", 12 to "12s", 24 to "24s", 0 to "Off"),
                    selected = settings.captureTimeoutSec
                ) { t -> onUpdateSettings { it.copy(captureTimeoutSec = t) } }
            }

            SettingsSection("MIDI Hardware (USB & Bluetooth)") {
                if (!midiSupported) {
                    Text("Android MIDI service unavailable on this device.", style = MaterialTheme.typography.bodyMedium, color = tokens.textMuted)
                } else {
                    Text(
                        text = "Input: ${connectedMidiInput ?: "None"} · Output: ${connectedMidiOutput ?: "None"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrackPalette[1]
                    )
                    ChipSelectRow(
                        label = "Input Channel",
                        options = listOf(-1 to "Omni") + (0..3).map { it to "Ch ${it + 1}" },
                        selected = settings.midiFilterChannel
                    ) { ch -> onUpdateSettings { it.copy(midiFilterChannel = ch) } }

                    if (midiDevices.isEmpty()) {
                        Text("No external MIDI devices connected. Plug in USB MIDI or pair BLE MIDI.", style = MaterialTheme.typography.bodyMedium, color = tokens.textMuted)
                    } else {
                        midiDevices.forEach { dev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.bg, RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dev.name, style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary)
                                    Text("${dev.manufacturer} · ${if (dev.isBluetooth) "BLE" else "USB"}", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (dev.hasOutputFromDevice) {
                                        OutlinedButton(onClick = {
                                            if (connectedMidiInput == dev.name) onDisconnectMidiInput() else onConnectMidiInput(dev.id)
                                        }) {
                                            Text(if (connectedMidiInput == dev.name) "Disconnect In" else "Use In")
                                        }
                                    }
                                    if (dev.hasInputToDevice) {
                                        OutlinedButton(onClick = {
                                            if (connectedMidiOutput == dev.name) onDisconnectMidiOutput() else onConnectMidiOutput(dev.id)
                                        }) {
                                            Text(if (connectedMidiOutput == dev.name) "Disconnect Out" else "Use Out")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsSection("Autosave & Interoperability") {
                ChipSelectRow(
                    label = "Autosave Interval",
                    options = listOf(10 to "10s", 15 to "15s", 30 to "30s", 60 to "60s"),
                    selected = settings.autosaveIntervalSec
                ) { sec -> onUpdateSettings { it.copy(autosaveIntervalSec = sec) } }
                Text(
                    text = "Project Format: ZYNTRA Open JSON (.notecraft.json) · Standard MIDI (.mid) & WAV stems supported.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }

            SettingsSection("About ZYNTRA") {
                Text(
                    text = "ZYNTRA v1.0.0 · Native Oboe C++17 DSP Engine · 100% Offline Studio",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val tokens = LocalStudioTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, tokens.border, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TrackPalette[1])
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = LocalStudioTokens.current.textPrimary)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChipSelectRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    val tokens = LocalStudioTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (value, text) ->
                val isSel = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isSel) TrackPalette[1] else tokens.padIdle)
                        .border(0.5.dp, if (isSel) TrackPalette[1] else tokens.border, RoundedCornerShape(3.dp))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text, style = MaterialTheme.typography.labelSmall, color = if (isSel) Color.Black else tokens.textPrimary)
                }
            }
        }
    }
}

@Composable
fun ExportAudioDialog(
    set: NoteSet,
    selectedSceneIndex: Int,
    selectedTrackIndex: Int,
    exportProgress: Float?,
    lastExportResult: com.example.core.audio.ExportedWavResult?,
    onExportWavDownloads: (Int, Int, Int, Int, Int, Int) -> Unit,
    onExportWavFolder: (Uri, Int, Int, Int, Int, Int, Int) -> Unit,
    onExportMidi: (Uri, Int) -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokens = LocalStudioTokens.current
    var scopeMode by remember { mutableIntStateOf(0) } // 0=Full, 1=Scene, 2=Track
    var bitDepth by remember { mutableIntStateOf(16) } // 16-bit PCM universally playable on Android media players
    var sampleRate by remember { mutableIntStateOf(48000) }
    var bars by remember { mutableIntStateOf(4) }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            onExportWavFolder(treeUri, bitDepth, sampleRate, scopeMode, selectedSceneIndex, selectedTrackIndex, bars)
        }
    }
    val midLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/midi")) { uri ->
        if (uri != null) onExportMidi(uri, selectedSceneIndex)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Audio & MIDI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChipSelectRow(
                    "Scope",
                    listOf(0 to "Full Set", 1 to "Scene ${selectedSceneIndex + 1}", 2 to "Track ${selectedTrackIndex + 1}"),
                    scopeMode
                ) { scopeMode = it }

                ChipSelectRow(
                    "WAV Format",
                    listOf(16 to "16-bit PCM", 24 to "24-bit PCM", 32 to "32-bit Float"),
                    bitDepth
                ) { bitDepth = it }

                ChipSelectRow(
                    "Sample Rate",
                    listOf(44100 to "44.1 kHz", 48000 to "48 kHz"),
                    sampleRate
                ) { sampleRate = it }

                ChipSelectRow(
                    "Length",
                    listOf(2 to "2 Bars", 4 to "4 Bars", 8 to "8 Bars", 16 to "16 Bars"),
                    bars
                ) { bars = it }

                if (exportProgress != null) {
                    LinearProgressIndicator(progress = { exportProgress }, modifier = Modifier.fillMaxWidth())
                    Text("Rendering offline DSP: ${(exportProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                } else {
                    Text(
                        text = "Saves as ${com.example.core.audio.WavExportHelper.sanitizeFileBaseName(set.name)}.wav (or (1).wav, (2).wav) in Downloads/ZYNTRA.",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }

                if (lastExportResult != null && exportProgress == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrackPalette[1].copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, TrackPalette[1], RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exported: ${lastExportResult.displayName}", style = MaterialTheme.typography.labelMedium, color = TrackPalette[1])
                            Text("Location: ${lastExportResult.folderLabel}", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                        }
                        TextButton(
                            onClick = {
                                val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(lastExportResult.uri, "audio/wav")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(viewIntent) }
                            }
                        ) {
                            Text("Play")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (exportProgress != null) {
                OutlinedButton(onClick = onCancelExport) { Text("Cancel Render") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        val safeMid = com.example.core.audio.WavExportHelper.sanitizeFileBaseName(set.name)
                        midLauncher.launch("$safeMid.mid")
                    }) {
                        Text("MIDI")
                    }
                    OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                        Text("Folder")
                    }
                    Button(
                        onClick = { onExportWavDownloads(bitDepth, sampleRate, scopeMode, selectedSceneIndex, selectedTrackIndex, bars) },
                        modifier = Modifier.testTag("confirm_export_wav")
                    ) { Text("Export WAV") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun MasterEffectsDialog(
    masterEffects: List<EffectModel>,
    onAddMasterEffect: (com.example.core.project.EffectType) -> Unit,
    onUpdateMasterEffect: (Int, EffectModel) -> Unit,
    onRemoveMasterEffect: (Int) -> Unit,
    onMoveMasterEffect: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Master Effects Chain (Up to 8)") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                com.example.feature.instrument.EffectsChainEditor(
                    effects = masterEffects,
                    accentColor = TrackPalette[1],
                    onAddEffect = onAddMasterEffect,
                    onUpdateEffect = onUpdateMasterEffect,
                    onRemoveEffect = onRemoveMasterEffect,
                    onMoveEffect = onMoveMasterEffect
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}
