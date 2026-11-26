package com.example.feature.drums

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.audio.TransportTelemetryState
import com.example.core.common.CaptureMode
import com.example.core.common.GridResolution
import com.example.core.common.NoteRepeatRate
import com.example.core.database.SampleEntity
import com.example.core.design.MiniClipPreview
import com.example.core.design.RotaryKnob
import com.example.core.design.StudioTopBar
import com.example.core.design.TransportRingButton
import com.example.core.design.WaveformDisplay
import com.example.core.project.ClipModel
import com.example.core.project.DemoProjectsFactory
import com.example.core.project.DrumPadModel
import com.example.core.project.EffectChainTransforms
import com.example.core.project.EffectModel
import com.example.core.project.EffectType
import com.example.core.project.MidiNoteModel
import com.example.core.project.TrackModel
import com.example.core.samples.MicRecordingState
import com.example.feature.instrument.EffectsChainEditor
import com.example.feature.library.RenameDialog
import com.example.ui.theme.LocalStudioTokens
import com.example.ui.theme.StudioRecordRed
import com.example.ui.theme.TrackPalette
import com.example.ui.theme.trackAccentColor
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DrumRackView(
    track: TrackModel,
    trackIndex: Int,
    sceneIndex: Int,
    selectedPadIndex: Int,
    samples: List<SampleEntity>,
    telemetry: TransportTelemetryState,
    noteRepeatRate: NoteRepeatRate,
    isMicRecording: Boolean,
    micPeaks: List<Float>,
    onBack: () -> Unit,
    onSelectPad: (Int) -> Unit,
    onTriggerPad: (Int, Float, Float) -> Unit,
    onReleasePad: (Int) -> Unit,
    onPreviewPad: (Int, Float) -> Unit = { _, _ -> },
    onUpdatePad: (Int, (DrumPadModel) -> DrumPadModel) -> Unit,
    onApplyKitPreset: (String, List<DrumPadModel>) -> Unit,
    onAddKitEffect: (EffectType) -> Unit = {},
    onUpdateKitEffect: (Int, EffectModel) -> Unit,
    onRemoveKitEffect: (Int) -> Unit,
    onMoveKitEffect: (Int, Int) -> Unit = { _, _ -> },
    onNoteRepeatChange: (NoteRepeatRate) -> Unit,
    onOpenSampleBrowserForPad: (Int) -> Unit,
    onStartMicRecord: () -> Unit,
    onStopMicRecord: (String, Int) -> Unit,
    onUpdateClip: ((ClipModel) -> ClipModel) -> Unit,
    onOpenPianoRoll: () -> Unit,
    onCapture: () -> Unit,
    onTogglePlay: () -> Unit,
    onUndo: () -> Unit = {},
    modifier: Modifier = Modifier,
    timeSigNum: Int = 4,
    micState: MicRecordingState = MicRecordingState.READY,
    micDurationMs: Long = 0L,
    micError: String? = null,
    capturedNoteCount: Int = 0,
    captureMode: CaptureMode = CaptureMode.FREE_LENGTH,
    onClearPadSample: (Int) -> Unit = {},
    onClearCapture: () -> Unit = {},
    onCaptureOverdub: () -> Unit = {},
    onAddPadEffect: (Int, EffectType) -> Unit = { pIdx, fxType ->
        val newFx = EffectModel(type = fxType, bypassed = false, params = fxType.defaultParams, presetName = "Default")
        onUpdatePad(pIdx) { pad ->
            pad.copy(
                padEffect = EffectModel(type = EffectType.NONE),
                padEffects = EffectChainTransforms.addEffect(pad.effectivePadEffects(), newFx)
            )
        }
    },
    onUpdatePadEffect: (Int, Int, EffectModel) -> Unit = { pIdx, slot, fx ->
        onUpdatePad(pIdx) { pad ->
            pad.copy(
                padEffect = EffectModel(type = EffectType.NONE),
                padEffects = EffectChainTransforms.updateEffectAt(pad.effectivePadEffects(), slot, fx)
            )
        }
    },
    onRemovePadEffect: (Int, Int) -> Unit = { pIdx, slot ->
        onUpdatePad(pIdx) { pad ->
            pad.copy(
                padEffect = EffectModel(type = EffectType.NONE),
                padEffects = EffectChainTransforms.removeEffectAt(pad.effectivePadEffects(), slot)
            )
        }
    },
    onMovePadEffect: (Int, Int, Int) -> Unit = { pIdx, from, to ->
        onUpdatePad(pIdx) { pad ->
            pad.copy(
                padEffect = EffectModel(type = EffectType.NONE),
                padEffects = EffectChainTransforms.moveEffect(pad.effectivePadEffects(), from, to)
            )
        }
    }
) {
    val tsNum = timeSigNum.coerceIn(1, 12)
    val tokens = LocalStudioTokens.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val pads = track.drumPads
    val selectedPad = pads.getOrNull(selectedPadIndex) ?: pads.firstOrNull()
    val trackAccent = trackAccentColor(track.colorIndex)
    val padAccent = selectedPad?.let { trackAccentColor(it.colorIndex) } ?: trackAccent

    var activeTab by remember { mutableIntStateOf(0) } // 0=Pads, 1=Sample, 2=PadFX, 3=KitFX, 4=Seq
    var sixteenPitchMode by remember { mutableStateOf(false) }
    var renamePadTarget by remember { mutableStateOf<Int?>(null) }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStartMicRecord()
    }

    val activeClip = track.clips[sceneIndex]
    val isPlayingThisClip = telemetry.isPlaying && telemetry.activeClips.getOrElse(trackIndex) { -1 } == sceneIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        StudioTopBar(
            title = selectedPad?.name ?: track.presetName,
            titleColor = padAccent,
            onBack = onBack,
            backTestTag = "drum_back_button",
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (sixteenPitchMode) padAccent else tokens.padIdle)
                            .clickable { sixteenPitchMode = !sixteenPitchMode }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("drum_16_pitch_toggle")
                    ) {
                        Text(
                            text = "16P",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sixteenPitchMode) Color.Black else tokens.textSecondary
                        )
                    }
                    IconButton(
                        onClick = { onOpenSampleBrowserForPad(selectedPadIndex) },
                        modifier = Modifier.size(48.dp).testTag("drum_sample_browser_btn")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Sample Browser", tint = tokens.textSecondary)
                    }
                }
            }
        )

        // Responsive Studio Workspace (Portrait stacked vs Landscape/Tablet split)
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= 680.dp

            val tabsAndControlSection: @Composable (Modifier) -> Unit = { paneMod ->
                Column(modifier = paneMod) {
                    // 5 Compact Studio Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(tokens.surface)
                            .border(0.5.dp, tokens.border)
                    ) {
                        val tabSpecs = listOf(
                            Triple(0, "Pads", Icons.Default.GridView),
                            Triple(1, "Sample", Icons.Default.GraphicEq),
                            Triple(2, "Pad FX", Icons.Default.Tune),
                            Triple(3, "Kit FX", Icons.Default.Tune),
                            Triple(4, "Seq", Icons.Default.Piano)
                        )
                        tabSpecs.forEach { (idx, lbl, icon) ->
                            val sel = activeTab == idx
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(if (sel) tokens.bg else tokens.surface)
                                    .border(0.5.dp, tokens.border)
                                    .clickable { activeTab = idx }
                                    .testTag("drum_tab_$idx"),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (idx < 2) {
                                    Icon(icon, contentDescription = lbl, tint = if (sel) padAccent else tokens.textSecondary, modifier = Modifier.size(16.dp))
                                } else {
                                    Text(
                                        text = lbl,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (sel) padAccent else tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF0A0A0C))
                    ) {
                        when (activeTab) {
                            0 -> if (selectedPad != null) {
                                PadParametersSection(
                                    pad = selectedPad,
                                    kitName = track.presetName,
                                    accent = padAccent,
                                    onUpdate = { fn -> onUpdatePad(selectedPad.index, fn) },
                                    onApplyKitPreset = onApplyKitPreset,
                                    onRename = { renamePadTarget = selectedPad.index }
                                )
                            }
                            1 -> if (selectedPad != null) {
                                val sampleEntity = if (selectedPad.sampleSlot >= 0) {
                                    samples.firstOrNull { it.id == selectedPad.sampleId }
                                        ?: samples.firstOrNull { it.slotIndex == selectedPad.sampleSlot }
                                } else null
                                val hasSample = selectedPad.sampleSlot >= 0 && sampleEntity != null
                                val peaks = remember(sampleEntity, hasSample, isMicRecording, micPeaks) {
                                    when {
                                        isMicRecording -> micPeaks
                                        hasSample -> sampleEntity.peaksCsv.split(",").mapNotNull { it.toFloatOrNull() }
                                        else -> emptyList()
                                    }
                                }
                                PadSampleWaveformSection(
                                    pad = selectedPad,
                                    hasSample = hasSample,
                                    peaks = peaks,
                                    accent = padAccent,
                                    isMicRecording = isMicRecording,
                                    micState = micState,
                                    micDurationMs = micDurationMs,
                                    micError = micError,
                                    onToggleMicRecord = {
                                        if (isMicRecording) {
                                            onStopMicRecord("${selectedPad.name} Mic", selectedPad.index)
                                        } else {
                                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) onStartMicRecord() else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    onPreviewSample = { onPreviewPad(selectedPad.index, 0.9f) },
                                    onBrowseSample = { onOpenSampleBrowserForPad(selectedPad.index) },
                                    onClearSample = { onClearPadSample(selectedPad.index) },
                                    onUpdate = { fn -> onUpdatePad(selectedPad.index, fn) }
                                )
                            }
                            2 -> if (selectedPad != null) {
                                key(selectedPad.index) {
                                    EffectsChainEditor(
                                        effects = selectedPad.effectivePadEffects(),
                                        accentColor = padAccent,
                                        onAddEffect = { fxType -> onAddPadEffect(selectedPad.index, fxType) },
                                        onUpdateEffect = { slot, fx -> onUpdatePadEffect(selectedPad.index, slot, fx) },
                                        onRemoveEffect = { slot -> onRemovePadEffect(selectedPad.index, slot) },
                                        onMoveEffect = { from, to -> onMovePadEffect(selectedPad.index, from, to) },
                                        fillHeight = true,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            3 -> {
                                EffectsChainEditor(
                                    effects = track.effects,
                                    accentColor = trackAccent,
                                    onAddEffect = onAddKitEffect,
                                    onUpdateEffect = onUpdateKitEffect,
                                    onRemoveEffect = onRemoveKitEffect,
                                    onMoveEffect = onMoveKitEffect,
                                    fillHeight = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            4 -> {
                                DrumStepSequencerPanel(
                                    clip = activeClip,
                                    trackIndex = trackIndex,
                                    selectedPadIndex = selectedPadIndex,
                                    telemetry = telemetry,
                                    isPlayingThisClip = isPlayingThisClip,
                                    timeSigNum = tsNum,
                                    accent = padAccent,
                                    onUpdateClip = onUpdateClip
                                )
                            }
                        }
                    }

                    // Clip Strip (Tappable to open Piano Roll / Drum Grid)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(tokens.surface)
                            .border(0.5.dp, tokens.border)
                            .clickable(onClick = onOpenPianoRoll)
                            .testTag("drum_clip_preview_strip")
                    ) {
                        if (activeClip != null) {
                            val loopStart = activeClip.effectiveLoopStart(tsNum)
                            val loopEnd = activeClip.effectiveLoopEnd(tsNum)
                            val span = (loopEnd - loopStart).coerceAtLeast(1f)
                            val clipBeat = telemetry.trackClipBeats.getOrElse(trackIndex) { telemetry.transportBeat }.toFloat()
                            val playhead = if (isPlayingThisClip) ((clipBeat - loopStart) / span).coerceIn(0f, 1f) else null
                            MiniClipPreview(
                                clip = activeClip,
                                accentColor = trackAccent,
                                playheadNorm = playhead,
                                timeSigNum = tsNum,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        } else {
                            Text(
                                text = "Tap to open Drum MIDI Grid",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            val padsAndRepeatSection: @Composable (Modifier) -> Unit = { paneMod ->
                Row(modifier = paneMod.background(tokens.bg)) {
                    Column(
                        modifier = Modifier
                            .width(56.dp)
                            .fillMaxHeight()
                            .background(tokens.surface)
                            .border(0.5.dp, tokens.border)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NoteRepeatRate.entries.forEach { rate ->
                            val sel = rate == noteRepeatRate
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .background(if (sel) padAccent else Color.Transparent)
                                    .border(0.5.dp, tokens.border)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNoteRepeatChange(rate)
                                    }
                                    .testTag("note_repeat_${rate.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = rate.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sel) Color.Black else tokens.textSecondary
                                )
                            }
                        }
                    }

                    DrumPadGrid4x4(
                        pads = pads,
                        selectedPadIndex = selectedPadIndex,
                        sixteenPitchMode = sixteenPitchMode,
                        accentColor = padAccent,
                        onPadDown = { padIdx, vel, pitchOff ->
                            // Trigger audio FIRST for minimal touch-to-sound latency
                            onTriggerPad(if (sixteenPitchMode) selectedPadIndex else padIdx, vel, pitchOff)
                            if (!sixteenPitchMode && padIdx != selectedPadIndex) onSelectPad(padIdx)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onPadUp = { padIdx -> onReleasePad(if (sixteenPitchMode) selectedPadIndex else padIdx) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    tabsAndControlSection(Modifier.weight(0.46f).fillMaxHeight())
                    padsAndRepeatSection(Modifier.weight(0.54f).fillMaxHeight())
                }
            } else {
                val gridWidth = (maxWidth - 56.dp).coerceAtLeast(160.dp)
                val targetPadHeight = gridWidth.coerceAtMost(maxHeight * 0.56f)
                Column(modifier = Modifier.fillMaxSize()) {
                    tabsAndControlSection(Modifier.weight(1f).fillMaxWidth())
                    padsAndRepeatSection(Modifier.fillMaxWidth().height(targetPadHeight))
                }
            }
        }

        // Bottom Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(tokens.surface)
                .border(0.5.dp, tokens.border)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onClearCapture,
                enabled = capturedNoteCount > 0,
                modifier = Modifier.size(48.dp).testTag("drum_clear_capture_button")
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear Capture",
                    tint = if (capturedNoteCount > 0) tokens.textPrimary else tokens.textMuted
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (capturedNoteCount > 0) padAccent.copy(alpha = 0.16f) else tokens.bg)
                    .border(0.5.dp, if (capturedNoteCount > 0) padAccent else tokens.border, RoundedCornerShape(4.dp))
                    .clickable(onClick = onCapture)
                    .testTag("drum_capture_button"),
                contentAlignment = Alignment.Center
            ) {
                val capLabel = if (capturedNoteCount > 0) "Capture ($capturedNoteCount)" else "Capture"
                Text(capLabel, style = MaterialTheme.typography.labelMedium, color = padAccent, maxLines = 1)
            }
            if (activeClip != null && capturedNoteCount > 0) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(tokens.padIdle)
                        .border(0.5.dp, tokens.border, RoundedCornerShape(4.dp))
                        .clickable(onClick = onCaptureOverdub)
                        .padding(horizontal = 10.dp)
                        .testTag("drum_overdub_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+Dub", style = MaterialTheme.typography.labelSmall, color = tokens.textPrimary)
                }
            }
            TransportRingButton(
                isPlaying = telemetry.isPlaying,
                phaseProgress = ((telemetry.beat - 1) + (telemetry.sixteenth - 1) / 4f) / tsNum.toFloat(),
                onClick = onTogglePlay,
                accentColor = Color.White,
                testTag = "drum_play_button"
            )
        }
    }

    renamePadTarget?.let { pIdx ->
        val p = pads.getOrNull(pIdx)
        if (p != null) {
            RenameDialog("Rename Pad", p.name, { renamePadTarget = null }) { newName ->
                renamePadTarget = null
                onUpdatePad(pIdx) { it.copy(name = newName) }
            }
        }
    }
}

@Composable
private fun DrumPadGrid4x4(
    pads: List<DrumPadModel>,
    selectedPadIndex: Int,
    sixteenPitchMode: Boolean,
    accentColor: Color,
    onPadDown: (Int, Float, Float) -> Unit,
    onPadUp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    val activePointers = remember { mutableStateMapOf<Long, Int>() }
    val pressedCounts = remember { mutableStateMapOf<Int, Int>() }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }

    val latestSixteenPitch by rememberUpdatedState(sixteenPitchMode)
    val latestOnPadDown by rememberUpdatedState(onPadDown)
    val latestOnPadUp by rememberUpdatedState(onPadUp)

    DisposableEffect(Unit) {
        onDispose {
            pressedCounts.keys.toList().forEach { latestOnPadUp(it) }
            activePointers.clear()
            pressedCounts.clear()
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { gridSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (gridSize.width <= 0 || gridSize.height <= 0) continue
                        val w = gridSize.width.toFloat()
                        val h = gridSize.height.toFloat()
                        val pitch16 = latestSixteenPitch
                        for (change in event.changes) {
                            val col = ((change.position.x / w) * 4f).toInt().coerceIn(0, 3)
                            val row = ((change.position.y / h) * 4f).toInt().coerceIn(0, 3)
                            val visualRowFromBottom = 3 - row
                            val padIdx = visualRowFromBottom * 4 + col
                            // Y-position inside pad cell determines velocity (0.45..1.0)
                            val localY = ((change.position.y / (h / 4f)) - row).coerceIn(0f, 1f)
                            val velocity = (0.45f + (1f - localY) * 0.55f).coerceIn(0.35f, 1f)

                            if (change.pressed && !change.previousPressed) {
                                activePointers[change.id.value] = padIdx
                                pressedCounts[padIdx] = (pressedCounts[padIdx] ?: 0) + 1
                                val pitchOffset = if (pitch16) (padIdx - 5).toFloat() else 0f
                                latestOnPadDown(padIdx, velocity, pitchOffset)
                                change.consume()
                            } else if (!change.pressed) {
                                val prev = activePointers.remove(change.id.value)
                                if (prev != null) {
                                    val cnt = (pressedCounts[prev] ?: 1) - 1
                                    if (cnt <= 0) {
                                        pressedCounts.remove(prev)
                                        latestOnPadUp(prev)
                                    } else {
                                        pressedCounts[prev] = cnt
                                    }
                                }
                                change.consume()
                            }
                        }
                    }
                }
            }
            .testTag("drum_pad_grid")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0 until 4) {
                val visualRowFromBottom = 3 - row
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until 4) {
                        val padIdx = visualRowFromBottom * 4 + col
                        val pad = pads.getOrNull(padIdx)
                        val isPressed = (pressedCounts[padIdx] ?: 0) > 0
                        val isSel = !sixteenPitchMode && padIdx == selectedPadIndex
                        val pColor = if (sixteenPitchMode) accentColor else trackAccentColor(pad?.colorIndex ?: 0)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.5.dp)
                                .background(
                                    color = when {
                                        isPressed -> pColor
                                        isSel -> pColor.copy(alpha = 0.25f)
                                        else -> tokens.padIdle
                                    },
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .border(
                                    width = if (isSel) 1.5.dp else 0.5.dp,
                                    color = if (isSel) pColor else tokens.border,
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .padding(6.dp)
                                .testTag("drum_pad_$padIdx")
                        ) {
                            Text(
                                text = if (sixteenPitchMode) "${(padIdx - 5).let { if (it >= 0) "+$it" else "$it" }} st"
                                else pad?.name ?: "Pad ${padIdx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPressed) Color.Black else tokens.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadParametersSection(
    pad: DrumPadModel,
    kitName: String,
    accent: Color,
    onUpdate: ((DrumPadModel) -> DrumPadModel) -> Unit,
    onApplyKitPreset: (String, List<DrumPadModel>) -> Unit,
    onRename: () -> Unit
) {
    val tokens = LocalStudioTokens.current
    val kitPresets = remember { DemoProjectsFactory.drumKitPresets() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val distributeEvenly = maxHeight >= 220.dp
        val knobSize = if (distributeEvenly && maxHeight >= 260.dp) 56.dp else if (distributeEvenly) 52.dp else 48.dp
        Column(
            modifier = if (distributeEvenly) {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            } else {
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quick Kit Switcher Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kit:", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                kitPresets.forEach { (presetTitle, presetPads) ->
                    val sel = kitName == presetTitle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (sel) accent.copy(alpha = 0.22f) else tokens.surface)
                            .border(0.5.dp, if (sel) accent else tokens.border, RoundedCornerShape(3.dp))
                            .clickable { onApplyKitPreset(presetTitle, presetPads) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("kit_preset_${presetTitle.lowercase().replace(" ", "_")}")
                    ) {
                        Text(presetTitle, style = MaterialTheme.typography.labelSmall, color = if (sel) accent else tokens.textSecondary)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (pad.muted) Color(0xFFF59E0B) else tokens.padIdle)
                            .clickable { onUpdate { it.copy(muted = !it.muted) } }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("M", style = MaterialTheme.typography.labelSmall, color = if (pad.muted) Color.Black else tokens.textPrimary) }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (pad.reverse) accent else tokens.padIdle)
                            .clickable { onUpdate { it.copy(reverse = !it.reverse) } }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("REV", style = MaterialTheme.typography.labelSmall, color = if (pad.reverse) Color.Black else tokens.textPrimary) }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(tokens.padIdle)
                            .clickable { onUpdate { it.copy(chokeGroup = (it.chokeGroup + 1) % 5) } }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("Chk:${if (pad.chokeGroup == 0) "Off" else pad.chokeGroup}", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(tokens.padIdle)
                            .clickable { onUpdate { it.copy(colorIndex = (it.colorIndex + 1) % TrackPalette.size) } }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("Color", style = MaterialTheme.typography.labelSmall, color = accent) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(tokens.padIdle)
                            .clickable(onClick = onRename)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("Rename", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary) }
                }
            }

            Column(
                modifier = if (distributeEvenly) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                verticalArrangement = if (distributeEvenly) Arrangement.SpaceEvenly else Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RotaryKnob("Volume", pad.volume, { v -> onUpdate { it.copy(volume = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.85f)
                    RotaryKnob("Pitch", (pad.pitchSemitones + 24f) / 48f, { v -> onUpdate { it.copy(pitchSemitones = v * 48f - 24f) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.5f, valueFormatter = { "${(it * 48f - 24f).roundToInt()}st" })
                    RotaryKnob("Pan", (pad.pan + 1f) / 2f, { v -> onUpdate { it.copy(pan = v * 2f - 1f) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.5f, valueFormatter = { "${((it * 2f - 1f) * 50).roundToInt()}" })
                    RotaryKnob("Decay", pad.decay, { v -> onUpdate { it.copy(decay = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 1f)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RotaryKnob("Tone", pad.toneFilter, { v -> onUpdate { it.copy(toneFilter = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.92f)
                    RotaryKnob("Send", pad.sendAmount, { v -> onUpdate { it.copy(sendAmount = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.2f)
                    RotaryKnob("Start", pad.startNorm, { v -> onUpdate { it.copy(startNorm = v.coerceAtMost(it.endNorm - 0.02f)) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0f)
                    RotaryKnob("End", pad.endNorm, { v -> onUpdate { it.copy(endNorm = v.coerceAtLeast(it.startNorm + 0.02f)) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 1f)
                }
            }
        }
    }
}

@Composable
private fun PadSampleWaveformSection(
    pad: DrumPadModel,
    hasSample: Boolean,
    peaks: List<Float>,
    accent: Color,
    isMicRecording: Boolean,
    micState: MicRecordingState,
    micDurationMs: Long,
    micError: String?,
    onToggleMicRecord: () -> Unit,
    onPreviewSample: () -> Unit,
    onBrowseSample: () -> Unit,
    onClearSample: () -> Unit,
    onUpdate: ((DrumPadModel) -> DrumPadModel) -> Unit
) {
    val tokens = LocalStudioTokens.current
    var zoomWaveform by remember { mutableStateOf(false) }
    val displayPeaks = remember(peaks, zoomWaveform, pad.startNorm, pad.endNorm, isMicRecording) {
        if (!zoomWaveform || isMicRecording || peaks.size < 8) peaks
        else {
            val sIdx = (pad.startNorm * peaks.size).toInt().coerceIn(0, peaks.lastIndex)
            val eIdx = (pad.endNorm * peaks.size).toInt().coerceIn(sIdx + 1, peaks.size)
            peaks.subList(sIdx, eIdx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Status & Quick Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val statusText = when {
                isMicRecording -> "REC ${"%.1f".format(micDurationMs / 1000.0)}s"
                micState == MicRecordingState.PROCESSING -> "Processing Mic..."
                micState == MicRecordingState.ERROR && !micError.isNullOrBlank() -> micError
                hasSample -> pad.name
                else -> "Empty Pad"
            }
            val statusColor = when {
                isMicRecording || micState == MicRecordingState.ERROR -> StudioRecordRed
                micState == MicRecordingState.PROCESSING -> Color(0xFFFBBF24)
                else -> tokens.textSecondary
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasSample) {
                    IconButton(onClick = onPreviewSample, modifier = Modifier.size(34.dp).testTag("drum_sample_play_btn")) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Audition Pad Sample", tint = accent, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { zoomWaveform = !zoomWaveform }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom Trim Range", tint = if (zoomWaveform) accent else tokens.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onClearSample, modifier = Modifier.size(34.dp).testTag("drum_sample_clear_btn")) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Pad Sample", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = onToggleMicRecord,
                    modifier = Modifier.size(36.dp).testTag("drum_mic_record_button")
                ) {
                    Icon(
                        imageVector = if (isMicRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Record Microphone Sample",
                        tint = if (isMicRecording) StudioRecordRed else tokens.textPrimary
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            WaveformDisplay(
                peaks = displayPeaks,
                accentColor = if (isMicRecording) StudioRecordRed else accent,
                startNorm = if (zoomWaveform && !isMicRecording) 0f else pad.startNorm,
                endNorm = if (zoomWaveform && !isMicRecording) 1f else pad.endNorm,
                emptyText = if (!isMicRecording && !hasSample) "No sample assigned — Tap Mic or Browse" else null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Trim", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
            Slider(
                value = pad.startNorm,
                onValueChange = { s -> onUpdate { it.copy(startNorm = s.coerceAtMost(it.endNorm - 0.02f)) } },
                enabled = hasSample,
                modifier = Modifier.weight(1f).height(24.dp)
            )
            Slider(
                value = pad.endNorm,
                onValueChange = { e -> onUpdate { it.copy(endNorm = e.coerceAtLeast(it.startNorm + 0.02f)) } },
                enabled = hasSample,
                modifier = Modifier.weight(1f).height(24.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(tokens.padIdle)
                    .clickable(onClick = onBrowseSample)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(if (hasSample) "Replace" else "Browse", style = MaterialTheme.typography.labelSmall, color = accent)
            }
        }
    }
}

@Composable
private fun DrumStepSequencerPanel(
    clip: ClipModel?,
    trackIndex: Int,
    selectedPadIndex: Int,
    telemetry: TransportTelemetryState,
    isPlayingThisClip: Boolean,
    timeSigNum: Int,
    accent: Color,
    onUpdateClip: ((ClipModel) -> ClipModel) -> Unit
) {
    val tokens = LocalStudioTokens.current
    var currentBar by remember { mutableIntStateOf(0) }
    var selectedStep by remember { mutableIntStateOf(0) }
    var stepRes by remember { mutableStateOf(GridResolution.SIXTEENTH) }

    val tsNum = timeSigNum.coerceIn(1, 12)
    val targetNote = 36 + selectedPadIndex
    val bars = (clip?.lengthBars ?: 1).coerceIn(1, 16)
    val safeBar = currentBar.coerceIn(0, bars - 1)
    if (safeBar != currentBar) currentBar = safeBar

    val stepsPerBar = (tsNum / stepRes.beats).roundToInt().coerceIn(4, 32)
    val stepBeatSize = tsNum.toFloat() / stepsPerBar.toFloat()
    val clipBeat = telemetry.trackClipBeats.getOrElse(trackIndex) { 0.0 }.toFloat()
    val activePlayStepGlobal = if (isPlayingThisClip) (clipBeat / stepBeatSize).toInt() else -1

    val barPresets = listOf(1, 2, 4, 8, 16)
    val seqResolutions = listOf(GridResolution.EIGHTH, GridResolution.SIXTEENTH, GridResolution.THIRTY_SECOND, GridResolution.SIXTEENTH_TRIPLET)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (b in 0 until bars) {
                    val sel = currentBar == b
                    val playingBar = isPlayingThisClip && (activePlayStepGlobal / stepsPerBar) == b
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (sel) accent else if (playingBar) accent.copy(alpha = 0.25f) else tokens.padIdle)
                            .border(0.5.dp, if (playingBar) Color.White else tokens.border, RoundedCornerShape(3.dp))
                            .clickable { currentBar = b }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) { Text("B${b + 1}", style = MaterialTheme.typography.labelSmall, color = if (sel) Color.Black else tokens.textSecondary) }
                }
            }
            Spacer(Modifier.width(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(tokens.padIdle)
                        .clickable {
                            val idx = seqResolutions.indexOf(stepRes)
                            stepRes = seqResolutions[(idx + 1) % seqResolutions.size]
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) { Text(stepRes.label, style = MaterialTheme.typography.labelSmall, color = accent) }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(tokens.padIdle)
                        .clickable {
                            onUpdateClip { c ->
                                val nextIdx = (barPresets.indexOfFirst { it >= c.lengthBars }.coerceAtLeast(0) + 1) % barPresets.size
                                val nextBars = barPresets[nextIdx]
                                c.copy(lengthBars = nextBars, loopEndBeats = (nextBars * tsNum).toFloat())
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) { Text("${bars}B", style = MaterialTheme.typography.labelSmall, color = tokens.textPrimary) }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(tokens.padIdle)
                        .clickable {
                            onUpdateClip { c ->
                                val barStart = currentBar * tsNum.toFloat()
                                val barEnd = barStart + tsNum.toFloat()
                                val targetBar = (currentBar + 1).coerceAtMost(15)
                                val newLength = maxOf(c.lengthBars, targetBar + 1).coerceIn(1, 16)
                                val copiedNotes = c.notes.filter {
                                    it.note == targetNote && it.startBeat >= barStart && it.startBeat < barEnd
                                }.map {
                                    it.copy(
                                        id = UUID.randomUUID().toString(),
                                        startBeat = it.startBeat + tsNum.toFloat()
                                    )
                                }
                                val targetStart = targetBar * tsNum.toFloat()
                                val targetEnd = targetStart + tsNum.toFloat()
                                val preserved = c.notes.filterNot {
                                    it.note == targetNote && it.startBeat >= targetStart && it.startBeat < targetEnd
                                }
                                c.copy(
                                    lengthBars = newLength,
                                    loopEndBeats = (newLength * tsNum).toFloat(),
                                    notes = (preserved + copiedNotes).sortedWith(compareBy({ it.startBeat }, { it.note }))
                                )
                            }
                            if (currentBar < 15) currentBar += 1
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Bar", tint = tokens.textSecondary, modifier = Modifier.size(14.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(tokens.padIdle)
                        .clickable {
                            onUpdateClip { c -> c.copy(notes = c.notes.filterNot { it.note == targetNote }) }
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) { Text("Clear", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444)) }
            }
        }

        // Step rows (8 columns per row)
        val rowCount = ((stepsPerBar + 7) / 8).coerceIn(1, 4)
        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (row in 0 until rowCount) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (col in 0 until 8) {
                        val step = row * 8 + col
                        if (step >= stepsPerBar) {
                            Spacer(Modifier.weight(1f))
                            continue
                        }
                        val stepBeat = currentBar * tsNum.toFloat() + step * stepBeatSize
                        val existing = clip?.notes?.firstOrNull {
                            it.note == targetNote && abs(it.startBeat - stepBeat) < (stepBeatSize * 0.4f)
                        }
                        val active = existing != null
                        val isPlayStep = activePlayStepGlobal == (currentBar * stepsPerBar + step)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        active && isPlayStep -> Color.White
                                        active -> accent
                                        isPlayStep -> accent.copy(alpha = 0.28f)
                                        else -> tokens.padIdle
                                    }
                                )
                                .border(
                                    width = if (isPlayStep || selectedStep == step) 1.5.dp else 0.5.dp,
                                    color = if (isPlayStep) Color.White else if (selectedStep == step) accent else tokens.border,
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .clickable {
                                    selectedStep = step
                                    onUpdateClip { c ->
                                        if (active) {
                                            c.copy(notes = c.notes.filterNot { it.id == existing.id })
                                        } else {
                                            val updatedNotes = (c.notes + MidiNoteModel(
                                                id = UUID.randomUUID().toString(),
                                                note = targetNote,
                                                startBeat = stepBeat,
                                                durationBeats = stepBeatSize.coerceAtLeast(0.125f),
                                                velocity = 0.88f
                                            )).sortedWith(compareBy({ it.startBeat }, { it.note }))
                                            c.copy(notes = updatedNotes)
                                        }
                                    }
                                }
                                .testTag("step_seq_${step}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${step + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active || (active && isPlayStep)) Color.Black else if (isPlayStep) Color.White else tokens.textMuted
                            )
                        }
                    }
                }
            }
        }

        // Per-step Velocity & Probability for selected active step
        val selBeat = currentBar * tsNum.toFloat() + selectedStep * stepBeatSize
        val selNote = clip?.notes?.firstOrNull { it.note == targetNote && abs(it.startBeat - selBeat) < (stepBeatSize * 0.4f) }
        if (selNote != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Vel ${(selNote.velocity * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                Slider(
                    value = selNote.velocity,
                    onValueChange = { v ->
                        onUpdateClip { c -> c.copy(notes = c.notes.map { if (it.id == selNote.id) it.copy(velocity = v) else it }) }
                    },
                    valueRange = 0.1f..1f,
                    modifier = Modifier.weight(1f).height(20.dp)
                )
                Text("Prob ${(selNote.probability * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                Slider(
                    value = selNote.probability,
                    onValueChange = { p ->
                        onUpdateClip { c -> c.copy(notes = c.notes.map { if (it.id == selNote.id) it.copy(probability = p) else it }) }
                    },
                    valueRange = 0.1f..1f,
                    modifier = Modifier.weight(1f).height(20.dp)
                )
            }
        }
    }
}
