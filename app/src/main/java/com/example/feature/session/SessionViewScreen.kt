package com.example.feature.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.TransportTelemetryState
import com.example.core.common.GlobalQuantization
import com.example.core.common.MusicalTheory
import com.example.core.common.ScaleType
import com.example.core.design.MiniClipPreview
import com.example.core.design.StudioTopBar
import com.example.core.design.TrackTypeIcon
import com.example.core.design.TransportRingButton
import com.example.core.project.NoteSet
import com.example.core.project.TrackType
import com.example.feature.library.RenameDialog
import com.example.feature.mixer.MixerPanel
import com.example.ui.theme.LocalStudioTokens
import com.example.ui.theme.StudioRecordRed
import com.example.ui.theme.TrackPalette
import com.example.ui.theme.trackAccentColor
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionViewScreen(
    set: NoteSet,
    telemetry: TransportTelemetryState,
    metronomeEnabled: Boolean,
    isResampling: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    missingSamples: List<String>,
    onBackToLibrary: () -> Unit,
    onOpenTrackInstrument: (Int) -> Unit,
    onOpenClipPianoRoll: (Int, Int) -> Unit,
    onAddTrack: (TrackType) -> Unit,
    onRenameTrack: (Int, String) -> Unit,
    onDuplicateTrack: (Int) -> Unit,
    onDeleteTrack: (Int) -> Unit,
    onChangeTrackColor: (Int, Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onCreateOrOpenClip: (Int, Int) -> Unit,
    onLaunchClip: (Int, Int) -> Unit,
    onStopTrackClip: (Int) -> Unit,
    onDuplicateClip: (Int, Int) -> Unit,
    onRenameClip: (Int, Int, String) -> Unit,
    onChangeClipColor: (Int, Int, Int) -> Unit,
    onDeleteClip: (Int, Int) -> Unit,
    onLaunchScene: (Int) -> Unit,
    onRenameScene: (Int, String) -> Unit,
    onDuplicateScene: (Int) -> Unit,
    onDeleteScene: (Int) -> Unit,
    onSwapScenes: (Int, Int) -> Unit,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onBpmChange: (Float) -> Unit,
    onSwingChange: (Float) -> Unit,
    onToggleMetronome: () -> Unit,
    onQuantizationChange: (GlobalQuantization) -> Unit,
    onKeyScaleChange: (Int, ScaleType) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCapture: () -> Unit,
    onToggleResample: () -> Unit,
    onSaveSet: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSampleBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMasterFx: () -> Unit,
    onTrackVolume: (Int, Float) -> Unit,
    onTrackPan: (Int, Float) -> Unit,
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
    onMasterVolume: (Float) -> Unit,
    onDismissMissingSamples: () -> Unit,
    modifier: Modifier = Modifier,
    capturedNoteCount: Int = 0
) {
    val tokens = LocalStudioTokens.current
    val haptic = LocalHapticFeedback.current
    var showMixer by remember { mutableStateOf(false) }
    var showTempoDialog by remember { mutableStateOf(false) }
    var showAddTrackDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    var contextClip by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var renameClipTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var contextTrack by remember { mutableStateOf<Int?>(null) }
    var renameTrackTarget by remember { mutableStateOf<Int?>(null) }
    var contextScene by remember { mutableStateOf<Int?>(null) }
    var renameSceneTarget by remember { mutableStateOf<Int?>(null) }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        StudioTopBar(
            title = set.name,
            onBack = onBackToLibrary,
            backTestTag = "session_back_button",
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${set.bpm.roundToInt()} BPM · ${MusicalTheory.NOTE_NAMES[set.rootNote % 12]}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TrackPalette[1],
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(tokens.bg)
                            .border(0.5.dp, tokens.border, RoundedCornerShape(3.dp))
                            .clickable { showTempoDialog = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("tempo_key_button")
                    )
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(48.dp).testTag("session_menu_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = "Session Menu", tint = tokens.textSecondary)
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Save Set") },
                                leadingIcon = { Icon(Icons.Default.Save, null) },
                                onClick = { showMoreMenu = false; onSaveSet() }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Audio / MIDI") },
                                leadingIcon = { Icon(Icons.Default.IosShare, null) },
                                onClick = { showMoreMenu = false; onOpenExport() }
                            )
                            DropdownMenuItem(
                                text = { Text("Sample Browser") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                                onClick = { showMoreMenu = false; onOpenSampleBrowser() }
                            )
                            DropdownMenuItem(
                                text = { Text("Restart Transport") },
                                onClick = { showMoreMenu = false; onRestart() }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { showMoreMenu = false; onOpenSettings() }
                            )
                        }
                    }
                }
            }
        )

        if (missingSamples.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF7C2D12))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Missing samples: ${missingSamples.take(2).joinToString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissMissingSamples) { Text("Dismiss", color = Color.White) }
            }
        }

        // Session Grid
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 960.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            // Top Track Headers Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(tokens.surface)
                    .border(0.5.dp, tokens.border)
            ) {
                // Top-left corner: Bar.Beat readout
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight()
                        .border(0.5.dp, tokens.border),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${telemetry.bar}.${telemetry.beat}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (telemetry.isPlaying) TrackPalette[1] else tokens.textMuted
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                ) {
                    set.tracks.forEachIndexed { ti, track ->
                        val accent = trackAccentColor(track.colorIndex)
                        Box(
                            modifier = Modifier
                                .width(92.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, tokens.border)
                                .combinedClickable(
                                    onClick = { onOpenTrackInstrument(ti) },
                                    onLongClick = { contextTrack = ti }
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("track_header_$ti")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TrackTypeIcon(
                                        type = track.type,
                                        tint = if (track.muted) tokens.textMuted else accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = track.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (track.muted) tokens.textMuted else tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = track.presetName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (track.muted || track.soloed) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (track.soloed) TrackPalette[1] else Color(0xFFF59E0B))
                                )
                            }
                            DropdownMenu(
                                expanded = contextTrack == ti,
                                onDismissRequest = { contextTrack = null }
                            ) {
                                DropdownMenuItem(text = { Text("Open Instrument") }, onClick = { contextTrack = null; onOpenTrackInstrument(ti) })
                                DropdownMenuItem(text = { Text("Rename Track") }, onClick = { contextTrack = null; renameTrackTarget = ti })
                                DropdownMenuItem(text = { Text("Duplicate Track") }, onClick = { contextTrack = null; onDuplicateTrack(ti) })
                                DropdownMenuItem(text = { Text("Cycle Color") }, onClick = { contextTrack = null; onChangeTrackColor(ti, (track.colorIndex + 1) % TrackPalette.size) })
                                if (ti > 0) {
                                    DropdownMenuItem(text = { Text("Move Left") }, onClick = { contextTrack = null; onMoveTrack(ti, ti - 1) })
                                }
                                if (ti < set.tracks.lastIndex) {
                                    DropdownMenuItem(text = { Text("Move Right") }, onClick = { contextTrack = null; onMoveTrack(ti, ti + 1) })
                                }
                                if (set.tracks.size > 1) {
                                    DropdownMenuItem(text = { Text("Delete Track") }, onClick = { contextTrack = null; onDeleteTrack(ti) })
                                }
                            }
                        }
                    }

                    if (set.tracks.size < 8) {
                        Box(
                            modifier = Modifier
                                .width(54.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, tokens.border)
                                .clickable { showAddTrackDialog = true }
                                .testTag("add_track_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Track", tint = tokens.textSecondary)
                        }
                    }
                }
            }

            // 8 Scenes x Tracks Clip Matrix
            Row(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(vScroll)
            ) {
                // Scene Launch Column
                Column(modifier = Modifier.width(44.dp)) {
                    for (si in 0 until 8) {
                        val scene = set.scenes.getOrNull(si)
                        val anyActiveInScene = telemetry.activeClips.any { it == si }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .background(if (anyActiveInScene && telemetry.isPlaying) tokens.surfaceElevated else tokens.surface)
                                .border(0.5.dp, tokens.border)
                                .combinedClickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onLaunchScene(si)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        contextScene = si
                                    }
                                )
                                .testTag("scene_launch_$si"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${si + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (anyActiveInScene && telemetry.isPlaying) TrackPalette[1] else tokens.textSecondary
                            )
                            DropdownMenu(
                                expanded = contextScene == si,
                                onDismissRequest = { contextScene = null }
                            ) {
                                DropdownMenuItem(text = { Text("Launch Scene ${si + 1}") }, onClick = { contextScene = null; onLaunchScene(si) })
                                DropdownMenuItem(text = { Text("Rename (${scene?.name ?: "Scene"})") }, onClick = { contextScene = null; renameSceneTarget = si })
                                DropdownMenuItem(text = { Text("Duplicate Scene") }, onClick = { contextScene = null; onDuplicateScene(si) })
                                if (si > 0) DropdownMenuItem(text = { Text("Move Up") }, onClick = { contextScene = null; onSwapScenes(si, si - 1) })
                                if (si < 7) DropdownMenuItem(text = { Text("Move Down") }, onClick = { contextScene = null; onSwapScenes(si, si + 1) })
                                DropdownMenuItem(text = { Text("Clear Scene Clips") }, onClick = { contextScene = null; onDeleteScene(si) })
                            }
                        }
                    }
                }

                // Clip Columns
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                ) {
                    set.tracks.forEachIndexed { ti, track ->
                        val activeScene = telemetry.activeClips.getOrElse(ti) { -1 }
                        val queuedScene = telemetry.queuedClips.getOrElse(ti) { -1 }
                        Column(modifier = Modifier.width(92.dp)) {
                            for (si in 0 until 8) {
                                val clip = track.clips[si]
                                val isActive = telemetry.isPlaying && activeScene == si
                                val isQueued = telemetry.isPlaying && queuedScene == si
                                val clipAccent = trackAccentColor(clip?.colorIndex ?: track.colorIndex)
                                val cellBg = when {
                                    clip == null -> tokens.padIdle.copy(alpha = 0.45f)
                                    isActive -> clipAccent
                                    isQueued -> clipAccent.copy(alpha = 0.72f)
                                    else -> clipAccent.copy(alpha = 0.48f)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp)
                                        .padding(1.dp)
                                        .background(cellBg, RoundedCornerShape(2.dp))
                                        .border(
                                            width = if (isActive || isQueued) 1.dp else 0.5.dp,
                                            color = if (isActive) Color.White else if (isQueued) TrackPalette[1] else tokens.border,
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                if (clip == null) {
                                                    if (activeScene >= 0 && telemetry.isPlaying) {
                                                        onStopTrackClip(ti)
                                                    } else {
                                                        onCreateOrOpenClip(ti, si)
                                                    }
                                                } else {
                                                    onLaunchClip(ti, si)
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                contextClip = ti to si
                                            }
                                        )
                                        .testTag("clip_slot_${ti}_${si}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (clip == null) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Empty or stop slot",
                                            tint = tokens.textMuted.copy(alpha = 0.6f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else {
                                        val tsNum = set.timeSigNum.coerceIn(1, 12)
                                        val loopStart = clip.effectiveLoopStart(tsNum)
                                        val loopEnd = clip.effectiveLoopEnd(tsNum)
                                        val span = (loopEnd - loopStart).coerceAtLeast(1f)
                                        val clipBeat = telemetry.trackClipBeats.getOrElse(ti) { telemetry.transportBeat }.toFloat()
                                        val playhead = if (isActive) ((clipBeat - loopStart) / span).coerceIn(0f, 1f) else null
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            MiniClipPreview(
                                                clip = clip,
                                                accentColor = if (isActive) Color.Black.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.55f),
                                                playheadNorm = playhead,
                                                timeSigNum = tsNum,
                                                modifier = Modifier.fillMaxSize().padding(4.dp)
                                            )
                                            Text(
                                                text = clip.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 8.sp,
                                                color = Color.Black.copy(alpha = if (isActive) 0.8f else 0.55f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = contextClip == (ti to si),
                                        onDismissRequest = { contextClip = null }
                                    ) {
                                        if (clip == null) {
                                            DropdownMenuItem(text = { Text("Create Clip") }, onClick = { contextClip = null; onCreateOrOpenClip(ti, si) })
                                            DropdownMenuItem(text = { Text("Stop Track") }, onClick = { contextClip = null; onStopTrackClip(ti) })
                                        } else {
                                            DropdownMenuItem(text = { Text("Play Clip") }, onClick = { contextClip = null; onLaunchClip(ti, si) })
                                            DropdownMenuItem(text = { Text("Edit MIDI / Notes") }, onClick = { contextClip = null; onOpenClipPianoRoll(ti, si) })
                                            DropdownMenuItem(text = { Text("Duplicate Clip") }, onClick = { contextClip = null; onDuplicateClip(ti, si) })
                                            DropdownMenuItem(text = { Text("Rename Clip") }, onClick = { contextClip = null; renameClipTarget = ti to si })
                                            DropdownMenuItem(text = { Text("Cycle Color") }, onClick = { contextClip = null; onChangeClipColor(ti, si, (clip.colorIndex + 1) % TrackPalette.size) })
                                            DropdownMenuItem(text = { Text("Delete Clip") }, onClick = { contextClip = null; onDeleteClip(ti, si) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (set.tracks.size < 8) {
                        Spacer(Modifier.width(54.dp))
                    }
                }
            }
        }

        // Toggleable Mixer Panel
        AnimatedVisibility(visible = showMixer) {
            MixerPanel(
                set = set,
                telemetry = telemetry,
                onVolumeChange = onTrackVolume,
                onPanChange = onTrackPan,
                onToggleMute = onToggleMute,
                onToggleSolo = onToggleSolo,
                onMasterVolumeChange = onMasterVolume,
                onOpenMasterFx = onOpenMasterFx
            )
        }

        // Compact Studio Bottom Toolbar (Matches Ableton Note bottom bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(tokens.surface)
                .border(0.5.dp, tokens.border)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(48.dp).testTag("undo_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) tokens.textPrimary else tokens.textMuted
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(48.dp).testTag("redo_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) tokens.textPrimary else tokens.textMuted
                )
            }
            // Capture Button
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (capturedNoteCount > 0) TrackPalette[1].copy(alpha = 0.22f) else tokens.padIdle)
                    .border(0.5.dp, if (capturedNoteCount > 0) TrackPalette[1] else tokens.border, RoundedCornerShape(4.dp))
                    .clickable(onClick = onCapture)
                    .padding(horizontal = 12.dp)
                    .testTag("session_capture_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (capturedNoteCount > 0) "Capture ($capturedNoteCount)" else "Capture",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (capturedNoteCount > 0) TrackPalette[1] else tokens.textPrimary
                )
            }
            // Resample Master Output Button
            IconButton(
                onClick = onToggleResample,
                modifier = Modifier.size(48.dp).testTag("resample_button")
            ) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = "Internal Resample",
                    tint = if (isResampling) StudioRecordRed else tokens.textSecondary
                )
            }
            // Mixer Toggle
            IconButton(
                onClick = { showMixer = !showMixer },
                modifier = Modifier.size(48.dp).testTag("toggle_mixer_button")
            ) {
                Icon(
                    Icons.Default.Equalizer,
                    contentDescription = "Toggle Mixer",
                    tint = if (showMixer) TrackPalette[1] else tokens.textSecondary
                )
            }
            TransportRingButton(
                isPlaying = telemetry.isPlaying,
                phaseProgress = ((telemetry.beat - 1) + (telemetry.sixteenth - 1) / 4f) / set.timeSigNum.toFloat(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTogglePlay()
                },
                accentColor = Color.White,
                testTag = "session_play_button"
            )
        }
    }

    if (showTempoDialog) {
        TempoAndMusicalDialog(
            set = set,
            metronomeEnabled = metronomeEnabled,
            onBpmChange = onBpmChange,
            onSwingChange = onSwingChange,
            onToggleMetronome = onToggleMetronome,
            onQuantizationChange = onQuantizationChange,
            onKeyScaleChange = onKeyScaleChange,
            onDismiss = { showTempoDialog = false }
        )
    }

    if (showAddTrackDialog) {
        AlertDialog(
            onDismissRequest = { showAddTrackDialog = false },
            title = { Text("Add Instrument Track") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackType.entries.forEach { t ->
                        Button(
                            onClick = { showAddTrackDialog = false; onAddTrack(t) },
                            modifier = Modifier.fillMaxWidth().testTag("add_track_type_${t.name}")
                        ) { Text(t.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddTrackDialog = false }) { Text("Cancel") } }
        )
    }

    renameTrackTarget?.let { ti ->
        val tr = set.tracks.getOrNull(ti)
        if (tr != null) {
            RenameDialog("Rename Track", tr.name, { renameTrackTarget = null }) { newName ->
                renameTrackTarget = null
                onRenameTrack(ti, newName)
            }
        }
    }

    renameClipTarget?.let { (ti, si) ->
        val clip = set.tracks.getOrNull(ti)?.clips?.get(si)
        if (clip != null) {
            RenameDialog("Rename Clip", clip.name, { renameClipTarget = null }) { newName ->
                renameClipTarget = null
                onRenameClip(ti, si, newName)
            }
        }
    }

    renameSceneTarget?.let { si ->
        val scene = set.scenes.getOrNull(si)
        if (scene != null) {
            RenameDialog("Rename Scene ${si + 1}", scene.name, { renameSceneTarget = null }) { newName ->
                renameSceneTarget = null
                onRenameScene(si, newName)
            }
        }
    }
}

@Composable
private fun TempoAndMusicalDialog(
    set: NoteSet,
    metronomeEnabled: Boolean,
    onBpmChange: (Float) -> Unit,
    onSwingChange: (Float) -> Unit,
    onToggleMetronome: () -> Unit,
    onQuantizationChange: (GlobalQuantization) -> Unit,
    onKeyScaleChange: (Int, ScaleType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transport & Scale") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("BPM: ${set.bpm.roundToInt()}", style = MaterialTheme.typography.labelMedium)
                Slider(value = set.bpm, onValueChange = onBpmChange, valueRange = 40f..220f)

                Text("Swing: ${(set.swing * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = set.swing, onValueChange = onSwingChange, valueRange = 0f..1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Metronome", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = metronomeEnabled, onCheckedChange = { onToggleMetronome() })
                }

                Text("Launch Quantization", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlobalQuantization.entries.forEach { q ->
                        val sel = q == set.globalQuantization
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (sel) TrackPalette[1] else Color(0xFF2B2B31))
                                .clickable { onQuantizationChange(q) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(q.displayName, style = MaterialTheme.typography.labelSmall, color = if (sel) Color.Black else Color.White)
                        }
                    }
                }

                Text("Root Note", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MusicalTheory.NOTE_NAMES.forEachIndexed { idx, name ->
                        val sel = idx == set.rootNote % 12
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (sel) TrackPalette[1] else Color(0xFF2B2B31))
                                .clickable { onKeyScaleChange(idx, set.scaleType) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(name, style = MaterialTheme.typography.labelSmall, color = if (sel) Color.Black else Color.White)
                        }
                    }
                }

                Text("Scale", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ScaleType.entries.forEach { sc ->
                        val sel = sc == set.scaleType
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (sel) TrackPalette[1] else Color(0xFF2B2B31))
                                .clickable { onKeyScaleChange(set.rootNote, sc) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(sc.displayName, style = MaterialTheme.typography.labelSmall, color = if (sel) Color.Black else Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}
