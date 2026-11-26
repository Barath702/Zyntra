package com.example.feature.instrument

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.audio.TransportTelemetryState
import com.example.core.common.MelodicPadCell
import com.example.core.common.MelodicPadLayout
import com.example.core.common.MusicalTheory
import com.example.core.common.NoteRepeatRate
import com.example.core.common.ScaleType
import com.example.core.database.SampleEntity
import com.example.core.design.MiniClipPreview
import com.example.core.design.RotaryKnob
import com.example.core.design.StudioTopBar
import com.example.core.design.TransportRingButton
import com.example.core.design.WaveformDisplay
import com.example.core.project.DemoProjectsFactory
import com.example.core.project.EffectChainTransforms
import com.example.core.project.EffectModel
import com.example.core.project.EffectType
import com.example.core.project.SamplerParamsModel
import com.example.core.project.SynthParamsModel
import com.example.core.project.TrackModel
import com.example.core.project.TrackType
import com.example.core.samples.MicRecordingState
import com.example.ui.theme.LocalStudioTokens
import com.example.ui.theme.StudioRecordRed
import com.example.ui.theme.trackAccentColor
import kotlin.math.roundToInt

@Composable
fun MelodicInstrumentView(
    track: TrackModel,
    trackIndex: Int,
    sceneIndex: Int,
    rootNote: Int,
    scaleType: ScaleType,
    padLayout: MelodicPadLayout,
    showNoteLabels: Boolean,
    samples: List<SampleEntity>,
    telemetry: TransportTelemetryState,
    automationRecording: Boolean,
    isMicRecording: Boolean,
    micPeaks: List<Float>,
    onBack: () -> Unit,
    onNoteOn: (Int, Float) -> Unit,
    onNoteOff: (Int) -> Unit,
    onUpdateSynth: ((SynthParamsModel) -> SynthParamsModel) -> Unit,
    onApplySynthPreset: (String, SynthParamsModel) -> Unit,
    onUpdateSampler: ((SamplerParamsModel) -> SamplerParamsModel) -> Unit,
    onAddEffect: (EffectType) -> Unit,
    onUpdateEffect: (Int, EffectModel) -> Unit,
    onRemoveEffect: (Int) -> Unit,
    onMoveEffect: (Int, Int) -> Unit,
    onToggleAutomationRecord: () -> Unit,
    onChangeLayout: (MelodicPadLayout) -> Unit,
    onOpenSampleBrowser: () -> Unit,
    onStartMicRecord: () -> Unit,
    onStopMicRecord: (String) -> Unit,
    onOpenPianoRoll: () -> Unit,
    onCapture: () -> Unit,
    onTogglePlay: () -> Unit,
    onUndo: () -> Unit = {},
    modifier: Modifier = Modifier,
    timeSigNum: Int = 4,
    noteRepeatRate: NoteRepeatRate = NoteRepeatRate.OFF,
    onNoteRepeatChange: (NoteRepeatRate) -> Unit = {},
    micState: MicRecordingState = MicRecordingState.READY,
    micDurationMs: Long = 0L,
    micError: String? = null,
    capturedNoteCount: Int = 0,
    onClearSamplerSample: () -> Unit = {},
    onAuditionSampler: () -> Unit = {},
    onClearCapture: () -> Unit = {},
    onCaptureOverdub: () -> Unit = {}
) {
    val tsNum = timeSigNum.coerceIn(1, 12)
    val tokens = LocalStudioTokens.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val accent = trackAccentColor(track.colorIndex)

    var activeTab by remember(track.id) { mutableIntStateOf(0) } // 0=Device, 1..N=FX1..FXn
    val safeTab = activeTab.coerceIn(0, track.effects.size)

    var showAddFxMenu by remember { mutableStateOf(false) }
    var octave by remember { mutableIntStateOf(3) }
    var showNoteRepeatRail by remember { mutableStateOf(false) }
    val tabScrollState = rememberScrollState()

    LaunchedEffect(safeTab, track.effects.size) {
        if (safeTab != activeTab) activeTab = safeTab
        if (safeTab >= 3) {
            tabScrollState.animateScrollTo(tabScrollState.maxValue)
        } else if (safeTab == 0) {
            tabScrollState.animateScrollTo(0)
        }
    }

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
            title = track.presetName,
            titleColor = accent,
            onBack = onBack,
            backTestTag = "instrument_back_button",
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleAutomationRecord,
                        modifier = Modifier.size(48.dp).testTag("automation_rec_button")
                    ) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = "Record Automation",
                            tint = if (automationRecording) StudioRecordRed else tokens.textMuted
                        )
                    }
                    if (track.type == TrackType.MELODIC_SAMPLER) {
                        IconButton(
                            onClick = onOpenSampleBrowser,
                            modifier = Modifier.size(48.dp).testTag("sampler_browser_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Load Sample", tint = tokens.textSecondary)
                        }
                    }
                }
            }
        )

        // Responsive Workspace (Portrait stacked vs Landscape/Tablet split)
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= 680.dp

            val controlsAndClipPane: @Composable (Modifier) -> Unit = { paneMod ->
                Column(modifier = paneMod) {
                    // Compact Studio Navigation Bar: [Device Icon] | [FX1] | [FX2] | ... | [+]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .background(tokens.surface)
                            .border(0.5.dp, tokens.border),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxHeight()
                                .horizontalScroll(tabScrollState),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Device Tab (waveform/synth icon only)
                            val devSelected = safeTab == 0
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .fillMaxHeight()
                                    .background(if (devSelected) tokens.bg else tokens.surface)
                                    .clickable { activeTab = 0 }
                                    .testTag("instrument_tab_0")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Device",
                                    tint = if (devSelected) accent else tokens.textSecondary,
                                    modifier = Modifier.size(20.dp).align(Alignment.Center)
                                )
                                if (devSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(accent)
                                            .align(Alignment.BottomCenter)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(0.5.dp)
                                        .fillMaxHeight()
                                        .background(tokens.border)
                                        .align(Alignment.CenterEnd)
                                )
                            }

                            // Dynamic Effect Tabs: FX1, FX2, FX3...
                            track.effects.forEachIndexed { slotIdx, fx ->
                                val tabIdx = slotIdx + 1
                                val sel = safeTab == tabIdx
                                val fxLabel = if (fx.type == EffectType.NONE) "Empty" else fx.type.displayName
                                Box(
                                    modifier = Modifier
                                        .widthIn(min = 68.dp, max = 108.dp)
                                        .fillMaxHeight()
                                        .background(if (sel) tokens.bg else tokens.surface)
                                        .clickable { activeTab = tabIdx }
                                        .testTag("instrument_tab_$tabIdx")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "FX${slotIdx + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            color = when {
                                                sel -> accent
                                                fx.bypassed -> tokens.textMuted
                                                else -> tokens.textPrimary
                                            },
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (fx.bypassed && fx.type != EffectType.NONE) "$fxLabel (Off)" else fxLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = when {
                                                sel -> accent.copy(alpha = 0.85f)
                                                else -> tokens.textMuted
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (sel) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .background(accent)
                                                .align(Alignment.BottomCenter)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(0.5.dp)
                                            .fillMaxHeight()
                                            .background(tokens.border)
                                            .align(Alignment.CenterEnd)
                                    )
                                }
                            }
                        }

                        // '+' Add Effect Button at the end of the navigation bar
                        if (track.effects.size < EffectChainTransforms.MAX_EFFECT_SLOTS) {
                            Box(modifier = Modifier.fillMaxHeight()) {
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .fillMaxHeight()
                                        .background(tokens.surface)
                                        .clickable { showAddFxMenu = true }
                                        .testTag("fx_chain_add_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Effect",
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(0.5.dp)
                                            .fillMaxHeight()
                                            .background(tokens.border)
                                            .align(Alignment.CenterEnd)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAddFxMenu,
                                    onDismissRequest = { showAddFxMenu = false }
                                ) {
                                    EffectType.entries.filter { it != EffectType.NONE }.forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text(t.displayName) },
                                            onClick = {
                                                showAddFxMenu = false
                                                onAddEffect(t)
                                                activeTab = (track.effects.size + 1).coerceAtMost(EffectChainTransforms.MAX_EFFECT_SLOTS)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Dynamic Control Panel (strictly above Clip Strip and Piano Pads)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF09090B))
                    ) {
                        val selectedSlot = safeTab - 1
                        val selectedFx = if (safeTab > 0) track.effects.getOrNull(selectedSlot) else null
                        if (safeTab == 0 || selectedFx == null) {
                            if (track.type == TrackType.SUBTRACTIVE_SYNTH) {
                                SubtractiveSynthControls(
                                    presetName = track.presetName,
                                    params = track.synthParams,
                                    accent = accent,
                                    onUpdate = onUpdateSynth,
                                    onApplyPreset = onApplySynthPreset
                                )
                            } else {
                                val sample = if (track.samplerParams.sampleSlot >= 0) {
                                    samples.firstOrNull { it.id == track.samplerParams.sampleId }
                                        ?: samples.firstOrNull { it.slotIndex == track.samplerParams.sampleSlot }
                                } else null
                                val hasSample = track.samplerParams.sampleSlot >= 0 && sample != null
                                val peaks = remember(sample, hasSample, isMicRecording, micPeaks) {
                                    when {
                                        isMicRecording -> micPeaks
                                        hasSample -> sample.peaksCsv.split(",").mapNotNull { it.toFloatOrNull() }
                                        else -> emptyList()
                                    }
                                }
                                MelodicSamplerControls(
                                    params = track.samplerParams,
                                    hasSample = hasSample,
                                    peaks = peaks,
                                    accent = accent,
                                    isMicRecording = isMicRecording,
                                    micState = micState,
                                    micDurationMs = micDurationMs,
                                    micError = micError,
                                    onToggleMic = {
                                        if (isMicRecording) onStopMicRecord("${track.name} Rec")
                                        else {
                                            val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                            if (ok) onStartMicRecord() else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    onAuditionSample = onAuditionSampler,
                                    onBrowseSample = onOpenSampleBrowser,
                                    onClearSample = onClearSamplerSample,
                                    onUpdate = onUpdateSampler
                                )
                            }
                        } else {
                            key(selectedSlot, selectedFx.id) {
                                EffectEditorPanel(
                                    effect = selectedFx,
                                    accentColor = accent,
                                    onUpdateEffect = { onUpdateEffect(selectedSlot, it) },
                                    onRemoveEffect = {
                                        val remaining = track.effects.size - 1
                                        onRemoveEffect(selectedSlot)
                                        activeTab = if (remaining <= 0) 0 else (selectedSlot + 1).coerceAtMost(remaining)
                                    },
                                    onMoveLeft = if (selectedSlot > 0) {
                                        {
                                            onMoveEffect(selectedSlot, selectedSlot - 1)
                                            activeTab = selectedSlot
                                        }
                                    } else null,
                                    onMoveRight = if (selectedSlot < track.effects.lastIndex) {
                                        {
                                            onMoveEffect(selectedSlot, selectedSlot + 1)
                                            activeTab = selectedSlot + 2
                                        }
                                    } else null,
                                    showTypeTitle = false,
                                    fillHeight = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Middle Clip Strip + Compact Scale/Layout Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(tokens.surface)
                            .border(0.5.dp, tokens.border),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(onClick = onOpenPianoRoll)
                                .testTag("instrument_clip_preview_strip")
                        ) {
                            if (activeClip != null) {
                                val loopStart = activeClip.effectiveLoopStart(tsNum)
                                val loopEnd = activeClip.effectiveLoopEnd(tsNum)
                                val span = (loopEnd - loopStart).coerceAtLeast(1f)
                                val clipBeat = telemetry.trackClipBeats.getOrElse(trackIndex) { telemetry.transportBeat }.toFloat()
                                val playhead = if (isPlayingThisClip) ((clipBeat - loopStart) / span).coerceIn(0f, 1f) else null
                                MiniClipPreview(
                                    clip = activeClip,
                                    accentColor = accent,
                                    playheadNorm = playhead,
                                    timeSigNum = tsNum,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            } else {
                                Text(
                                    text = "${MusicalTheory.NOTE_NAMES[rootNote % 12]} ${scaleType.displayName} · Tap for Piano Roll",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (showNoteRepeatRail || noteRepeatRate != NoteRepeatRate.OFF) accent.copy(alpha = 0.22f) else tokens.padIdle)
                                    .border(0.5.dp, if (noteRepeatRate != NoteRepeatRate.OFF) accent else tokens.border, RoundedCornerShape(3.dp))
                                    .clickable { showNoteRepeatRail = !showNoteRepeatRail }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("melodic_repeat_toggle")
                            ) {
                                Text(
                                    text = if (noteRepeatRate == NoteRepeatRate.OFF) "NR" else noteRepeatRate.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (noteRepeatRate != NoteRepeatRate.OFF) accent else tokens.textSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(tokens.padIdle)
                                    .clickable {
                                        val next = MelodicPadLayout.entries[(padLayout.ordinal + 1) % MelodicPadLayout.entries.size]
                                        onChangeLayout(next)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("melodic_layout_toggle")
                            ) {
                                Text(
                                    text = when (padLayout) {
                                        MelodicPadLayout.FOURTHS -> "5x5 4ths"
                                        MelodicPadLayout.SEQUENTIAL -> "5x5 Seq"
                                        MelodicPadLayout.SCALE_COLUMNS -> "Cols"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                            IconButton(
                                onClick = { octave = (octave - 1).coerceAtLeast(1) },
                                modifier = Modifier.size(36.dp).testTag("melodic_octave_down")
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Octave Down", tint = tokens.textSecondary)
                            }
                            Text("C$octave", style = MaterialTheme.typography.labelSmall, color = tokens.textPrimary, modifier = Modifier.testTag("melodic_octave_label"))
                            IconButton(
                                onClick = { octave = (octave + 1).coerceAtMost(6) },
                                modifier = Modifier.size(36.dp).testTag("melodic_octave_up")
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Octave Up", tint = tokens.textSecondary)
                            }
                        }
                    }
                }
            }

            val padsPane: @Composable (Modifier) -> Unit = { paneMod ->
                Row(modifier = paneMod.background(tokens.bg)) {
                    if (showNoteRepeatRail || noteRepeatRate != NoteRepeatRate.OFF) {
                        Column(
                            modifier = Modifier
                                .width(54.dp)
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
                                        .height(34.dp)
                                        .background(if (sel) accent else Color.Transparent)
                                        .border(0.5.dp, tokens.border)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onNoteRepeatChange(rate)
                                        }
                                        .testTag("melodic_note_repeat_${rate.name}"),
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
                    }
                    MelodicMultitouchPads(
                        rootNote = rootNote,
                        scaleType = scaleType,
                        octave = octave,
                        layout = padLayout,
                        showLabels = showNoteLabels,
                        accent = accent,
                        onNoteOn = { note, vel ->
                            onNoteOn(note, vel)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onNoteOff = onNoteOff,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    controlsAndClipPane(Modifier.weight(0.46f).fillMaxHeight())
                    padsPane(Modifier.weight(0.54f).fillMaxHeight())
                }
            } else {
                val targetPadHeight = if (padLayout == MelodicPadLayout.SCALE_COLUMNS) {
                    (maxWidth * 0.58f).coerceAtMost(maxHeight * 0.54f)
                } else {
                    maxWidth.coerceAtMost(maxHeight * 0.58f)
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    controlsAndClipPane(Modifier.weight(1f).fillMaxWidth())
                    padsPane(Modifier.fillMaxWidth().height(targetPadHeight))
                }
            }
        }

        // Bottom Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.surface)
                .border(0.5.dp, tokens.border)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onClearCapture,
                enabled = capturedNoteCount > 0,
                modifier = Modifier.size(48.dp).testTag("instrument_clear_capture_button")
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
                    .background(if (capturedNoteCount > 0) accent.copy(alpha = 0.16f) else tokens.bg)
                    .border(0.5.dp, if (capturedNoteCount > 0) accent else tokens.border, RoundedCornerShape(4.dp))
                    .clickable(onClick = onCapture)
                    .testTag("instrument_capture_button"),
                contentAlignment = Alignment.Center
            ) {
                val capLabel = if (capturedNoteCount > 0) "Capture ($capturedNoteCount)" else "Capture"
                Text(capLabel, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1)
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
                        .testTag("instrument_overdub_button"),
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
                testTag = "instrument_play_button"
            )
        }
    }
}

@Composable
private fun SubtractiveSynthControls(
    presetName: String,
    params: SynthParamsModel,
    accent: Color,
    onUpdate: ((SynthParamsModel) -> SynthParamsModel) -> Unit,
    onApplyPreset: (String, SynthParamsModel) -> Unit
) {
    val tokens = LocalStudioTokens.current
    val waves = remember { listOf("Saw", "Sqr", "Tri", "Sin") }
    val filters = remember { listOf("LPF", "HPF", "BPF") }
    val presets = remember { DemoProjectsFactory.synthPresets() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fitsEvenly = maxHeight >= 216.dp
        val knobSize = if (maxHeight >= 250.dp) 56.dp else 52.dp
        val colMod = if (fitsEvenly) {
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp)
        }
        Column(
            modifier = colMod,
            verticalArrangement = if (fitsEvenly) Arrangement.SpaceEvenly else Arrangement.spacedBy(7.dp)
        ) {
            // Quick Synth Preset Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Patch:", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                presets.forEach { spec ->
                    val sel = presetName == spec.name
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (sel) accent.copy(alpha = 0.22f) else tokens.surface)
                            .border(0.5.dp, if (sel) accent else tokens.border, RoundedCornerShape(3.dp))
                            .clickable { onApplyPreset(spec.name, spec.params) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("synth_preset_${spec.name.lowercase().replace(" ", "_")}")
                    ) {
                        Text(spec.name, style = MaterialTheme.typography.labelSmall, color = if (sel) accent else tokens.textSecondary)
                    }
                }
            }

            // 2x4 Equal Rotary Grid (uniform column weights for balanced spacing)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RotaryKnob("Filter Cutoff", params.cutoff, { v -> onUpdate { it.copy(cutoff = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.72f)
                RotaryKnob("Filter Reso", params.resonance, { v -> onUpdate { it.copy(resonance = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.25f)
                RotaryKnob("Osc Mix", params.oscMix, { v -> onUpdate { it.copy(oscMix = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.45f)
                RotaryKnob("Fold", params.fold, { v -> onUpdate { it.copy(fold = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0f)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RotaryKnob("Filter Decay", params.filterDecay, { v -> onUpdate { it.copy(filterDecay = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.38f)
                RotaryKnob("Release", params.release, { v -> onUpdate { it.copy(release = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.28f)
                RotaryKnob("Warp", params.warp, { v -> onUpdate { it.copy(warp = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.15f)
                RotaryKnob("Unison", params.unison, { v -> onUpdate { it.copy(unison = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.25f)
            }
            // Compact Wave / Filter / Sub / Glide / LFO Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChipButton("O1: ${waves.getOrElse(params.osc1Wave) { "Saw" }}", accent) {
                    onUpdate { it.copy(osc1Wave = (it.osc1Wave + 1) % 4) }
                }
                ChipButton("O2: ${waves.getOrElse(params.osc2Wave) { "Sqr" }}", accent) {
                    onUpdate { it.copy(osc2Wave = (it.osc2Wave + 1) % 4) }
                }
                ChipButton(filters.getOrElse(params.filterType) { "LPF" }, accent) {
                    onUpdate { it.copy(filterType = (it.filterType + 1) % 3) }
                }
                ChipButton("Sub ${(params.subLevel * 100).roundToInt()}%", tokens.textSecondary) {
                    onUpdate { it.copy(subLevel = if (it.subLevel > 0.5f) 0f else it.subLevel + 0.25f) }
                }
                ChipButton("Glide ${(params.glide * 100).roundToInt()}%", tokens.textSecondary) {
                    onUpdate { it.copy(glide = if (it.glide > 0.5f) 0f else it.glide + 0.25f) }
                }
                ChipButton("LFO ${(params.lfoDepth * 100).roundToInt()}%", tokens.textSecondary) {
                    onUpdate { it.copy(lfoDepth = if (it.lfoDepth > 0.5f) 0f else it.lfoDepth + 0.2f) }
                }
            }
        }
    }
}

@Composable
private fun MelodicSamplerControls(
    params: SamplerParamsModel,
    hasSample: Boolean,
    peaks: List<Float>,
    accent: Color,
    isMicRecording: Boolean,
    micState: MicRecordingState,
    micDurationMs: Long,
    micError: String?,
    onToggleMic: () -> Unit,
    onAuditionSample: () -> Unit,
    onBrowseSample: () -> Unit,
    onClearSample: () -> Unit,
    onUpdate: ((SamplerParamsModel) -> SamplerParamsModel) -> Unit
) {
    val tokens = LocalStudioTokens.current
    var zoomWaveform by remember { mutableStateOf(false) }
    val displayPeaks = remember(peaks, zoomWaveform, params.startNorm, params.endNorm, isMicRecording) {
        if (!zoomWaveform || isMicRecording || peaks.size < 8) peaks
        else {
            val sIdx = (params.startNorm * peaks.size).toInt().coerceIn(0, peaks.lastIndex)
            val eIdx = (params.endNorm * peaks.size).toInt().coerceIn(sIdx + 1, peaks.size)
            peaks.subList(sIdx, eIdx)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fitsEvenly = maxHeight >= 216.dp
        val knobSize = if (maxHeight >= 250.dp) 52.dp else 48.dp
        val colMod = if (fitsEvenly) {
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp)
        }
        Column(
            modifier = colMod,
            verticalArrangement = if (fitsEvenly) Arrangement.SpaceEvenly else Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val statusText = when {
                    isMicRecording -> "REC ${"%.1f".format(micDurationMs / 1000.0)}s"
                    micState == MicRecordingState.PROCESSING -> "Processing Mic..."
                    micState == MicRecordingState.ERROR && !micError.isNullOrBlank() -> micError
                    hasSample -> params.sampleName
                    else -> "No Sample Loaded"
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
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasSample) {
                        IconButton(onClick = onAuditionSample, modifier = Modifier.size(32.dp).testTag("sampler_play_btn")) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Audition Sample", tint = accent, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { zoomWaveform = !zoomWaveform }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom Trim", tint = if (zoomWaveform) accent else tokens.textSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onClearSample, modifier = Modifier.size(32.dp).testTag("sampler_clear_btn")) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Sample", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(
                        onClick = onToggleMic,
                        modifier = Modifier.size(36.dp).testTag("sampler_mic_btn")
                    ) {
                        Icon(
                            if (isMicRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record Sample",
                            tint = if (isMicRecording) StudioRecordRed else tokens.textPrimary
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                WaveformDisplay(
                    peaks = displayPeaks,
                    accentColor = if (isMicRecording) StudioRecordRed else accent,
                    startNorm = if (zoomWaveform && !isMicRecording) 0f else params.startNorm,
                    endNorm = if (zoomWaveform && !isMicRecording) 1f else params.endNorm,
                    emptyText = if (!isMicRecording && !hasSample) "No sample loaded — Record Mic or Browse" else null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RotaryKnob("Start", params.startNorm, { v -> onUpdate { it.copy(startNorm = v.coerceAtMost(it.endNorm - 0.02f)) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0f)
                RotaryKnob("End", params.endNorm, { v -> onUpdate { it.copy(endNorm = v.coerceAtLeast(it.startNorm + 0.02f)) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 1f)
                RotaryKnob("Pitch", (params.pitchSemitones + 24f) / 48f, { v -> onUpdate { it.copy(pitchSemitones = v * 48f - 24f) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.5f, valueFormatter = { "${(it * 48f - 24f).roundToInt()}st" })
                RotaryKnob("Cutoff", params.cutoff, { v -> onUpdate { it.copy(cutoff = v) } }, accent, modifier = Modifier.weight(1f), size = knobSize, defaultValue = 0.95f)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChipButton(if (params.loopEnabled) "Loop: ON" else "Loop: OFF", if (params.loopEnabled) accent else tokens.textSecondary) {
                    onUpdate { it.copy(loopEnabled = !it.loopEnabled) }
                }
                ChipButton(if (params.reverse) "Rev: ON" else "Rev: OFF", if (params.reverse) accent else tokens.textSecondary) {
                    onUpdate { it.copy(reverse = !it.reverse) }
                }
                ChipButton("Gain ${(params.gain * 100).roundToInt()}%", tokens.textSecondary) {
                    onUpdate { it.copy(gain = if (it.gain >= 0.98f) 0.5f else (it.gain + 0.15f).coerceAtMost(1f)) }
                }
                ChipButton("Root: ${MusicalTheory.noteName(params.rootNote)}", tokens.textSecondary) {
                    onUpdate { it.copy(rootNote = if (it.rootNote >= 72) 48 else it.rootNote + 1) }
                }
                ChipButton(if (hasSample) "Replace Sample" else "Browse Samples", accent, onClick = onBrowseSample)
            }
        }
    }
}

@Composable
private fun ChipButton(label: String, color: Color, onClick: () -> Unit) {
    val tokens = LocalStudioTokens.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(tokens.padIdle)
            .border(0.5.dp, tokens.border, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun MelodicMultitouchPads(
    rootNote: Int,
    scaleType: ScaleType,
    octave: Int,
    layout: MelodicPadLayout,
    showLabels: Boolean,
    accent: Color,
    onNoteOn: (Int, Float) -> Unit,
    onNoteOff: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    val (rows, cols, cells) = remember(rootNote, scaleType, octave, layout) {
        when (layout) {
            MelodicPadLayout.SCALE_COLUMNS -> Triple(2, 7, MusicalTheory.generateScaleColumns(rootNote, scaleType, octave, 7))
            else -> Triple(5, 5, MusicalTheory.generate25PadGrid(rootNote, scaleType, octave, layout))
        }
    }

    val pointerNotes = remember { mutableStateMapOf<Long, Int>() }
    val activeNotes = remember { mutableStateMapOf<Int, Int>() } // midiNote -> refCount
    var gridSize by remember { mutableStateOf(IntSize.Zero) }

    val latestCells by rememberUpdatedState(cells)
    val latestRows by rememberUpdatedState(rows)
    val latestCols by rememberUpdatedState(cols)
    val latestOnNoteOn by rememberUpdatedState(onNoteOn)
    val latestOnNoteOff by rememberUpdatedState(onNoteOff)

    LaunchedEffect(octave, rootNote, scaleType, layout) {
        if (activeNotes.isNotEmpty()) {
            activeNotes.keys.toList().forEach { latestOnNoteOff(it) }
        }
        pointerNotes.clear()
        activeNotes.clear()
    }

    DisposableEffect(Unit) {
        onDispose {
            activeNotes.keys.toList().forEach { latestOnNoteOff(it) }
            pointerNotes.clear()
            activeNotes.clear()
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
                        val curCells = latestCells
                        val rCount = latestRows.coerceAtLeast(1)
                        val cCount = latestCols.coerceAtLeast(1)
                        if (curCells.isEmpty()) continue
                        for (change in event.changes) {
                            val c = ((change.position.x / w) * cCount).toInt().coerceIn(0, cCount - 1)
                            val r = ((change.position.y / h) * rCount).toInt().coerceIn(0, rCount - 1)
                            val cell: MelodicPadCell = curCells.getOrNull(r * cCount + c) ?: continue
                            val localY = ((change.position.y / (h / rCount)) - r).coerceIn(0f, 1f)
                            val velocity = (0.45f + (1f - localY) * 0.55f).coerceIn(0.35f, 1f)

                            if (change.pressed && !change.previousPressed) {
                                pointerNotes[change.id.value] = cell.midiNote
                                activeNotes[cell.midiNote] = (activeNotes[cell.midiNote] ?: 0) + 1
                                latestOnNoteOn(cell.midiNote, velocity)
                                change.consume()
                            } else if (change.pressed && change.previousPressed) {
                                val prev = pointerNotes[change.id.value]
                                if (prev != null && prev != cell.midiNote) {
                                    // Smooth glissando across pads
                                    val prevCnt = (activeNotes[prev] ?: 1) - 1
                                    if (prevCnt <= 0) {
                                        activeNotes.remove(prev)
                                        latestOnNoteOff(prev)
                                    } else activeNotes[prev] = prevCnt

                                    pointerNotes[change.id.value] = cell.midiNote
                                    activeNotes[cell.midiNote] = (activeNotes[cell.midiNote] ?: 0) + 1
                                    latestOnNoteOn(cell.midiNote, velocity)
                                }
                                change.consume()
                            } else if (!change.pressed) {
                                val prev = pointerNotes.remove(change.id.value)
                                if (prev != null) {
                                    val cnt = (activeNotes[prev] ?: 1) - 1
                                    if (cnt <= 0) {
                                        activeNotes.remove(prev)
                                        latestOnNoteOff(prev)
                                    } else activeNotes[prev] = cnt
                                }
                                change.consume()
                            }
                        }
                    }
                }
            }
            .testTag("melodic_pad_grid")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until rows) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until cols) {
                        val cell = cells[r * cols + c]
                        val pressed = (activeNotes[cell.midiNote] ?: 0) > 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                                .background(
                                    color = when {
                                        pressed -> accent
                                        cell.isRoot -> tokens.surfaceElevated
                                        else -> tokens.padIdle
                                    },
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .border(0.5.dp, if (cell.isRoot && !pressed) accent.copy(alpha = 0.45f) else tokens.border, RoundedCornerShape(2.dp))
                                .padding(4.dp)
                                .testTag("melodic_pad_${r}_${c}")
                        ) {
                            if (showLabels) {
                                Text(
                                    text = cell.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = if (pressed) Color.Black else if (cell.isRoot) accent else tokens.textSecondary,
                                    modifier = Modifier.align(Alignment.BottomStart)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
