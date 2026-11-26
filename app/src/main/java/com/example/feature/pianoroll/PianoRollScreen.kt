package com.example.feature.pianoroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.TransportTelemetryState
import com.example.core.common.GridResolution
import com.example.core.common.MusicalTheory
import com.example.core.common.ScaleType
import com.example.core.design.StudioTopBar
import com.example.core.design.TransportRingButton
import com.example.core.project.ClipModel
import com.example.core.project.MidiNoteModel
import com.example.core.project.NoteDragMode
import com.example.core.project.PianoRollDragMath
import com.example.core.project.PianoRollTool
import com.example.core.project.StudioProjectTransforms
import com.example.core.project.TrackModel
import com.example.core.project.TrackType
import com.example.ui.theme.LocalStudioTokens
import com.example.ui.theme.trackAccentColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PianoRollScreen(
    track: TrackModel,
    trackIndex: Int,
    sceneIndex: Int,
    telemetry: TransportTelemetryState,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUpdateClip: (Boolean, (ClipModel) -> ClipModel) -> Unit,
    onAuditionNote: (Int, Float) -> Unit,
    onAuditionNoteOff: (Int) -> Unit = {},
    onAuditionDrumPad: (Int, Float) -> Unit,
    onReleaseAllAuditions: () -> Unit = {},
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    timeSigNum: Int = 4,
    rootNote: Int = 0,
    scaleType: ScaleType = ScaleType.NATURAL_MINOR,
    onCheckpointUndo: () -> Unit = {}
) {
    val tokens = LocalStudioTokens.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val accent = trackAccentColor(track.colorIndex)
    val isDrum = track.type == TrackType.DRUM_RACK
    val tsNum = timeSigNum.coerceIn(1, 12)
    val clip = track.clips[sceneIndex] ?: ClipModel(
        name = "${track.name} ${sceneIndex + 1}",
        colorIndex = track.colorIndex,
        lengthBars = 1,
        loopStartBeats = 0f,
        loopEndBeats = tsNum.toFloat()
    )

    val minMidi = if (isDrum) 36 else 24
    val maxMidi = if (isDrum) 51 else 96
    val totalRows = (maxMidi - minMidi) + 1

    var gridRes by remember { mutableStateOf(GridResolution.SIXTEENTH) }
    var beatWidthDp by remember { mutableFloatStateOf(92f) }
    var rowHeightDp by remember { mutableFloatStateOf(if (isDrum) 34f else 24f) }
    var activeTool by remember { mutableStateOf(PianoRollTool.DRAW) }
    var lastNoteDurationBeats by remember { mutableFloatStateOf(gridRes.beats.toFloat().coerceAtLeast(0.125f)) }
    var showVelocity by remember { mutableStateOf(false) }
    var showAutomation by remember { mutableStateOf(false) }
    var autoFollowPlayhead by remember { mutableStateOf(true) }
    var quantizeStrength by remember { mutableFloatStateOf(1.0f) }
    var selectedNoteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboardNotes by remember { mutableStateOf<List<MidiNoteModel>>(emptyList()) }
    var viewportWidthPx by remember { mutableFloatStateOf(600f) }
    var marqueeRect by remember { mutableStateOf<Rect?>(null) }

    val auditionJobs = remember { mutableStateMapOf<Int, Job>() }

    fun triggerTimedNoteAudition(midi: Int, velocity: Float, durationMs: Long = 220L) {
        if (isDrum) {
            onAuditionDrumPad(midi - 36, velocity)
            return
        }
        auditionJobs.remove(midi)?.cancel()
        onAuditionNoteOff(midi)
        onAuditionNote(midi, velocity)
        auditionJobs[midi] = scope.launch {
            delay(durationMs)
            onAuditionNoteOff(midi)
            auditionJobs.remove(midi)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            auditionJobs.values.forEach { it.cancel() }
            auditionJobs.clear()
            onReleaseAllAuditions()
        }
    }

    val hScroll = rememberScrollState()
    val initialScrollRow = remember(clip.id, isDrum) {
        if (isDrum) 0 else {
            val avgNote = clip.notes.map { it.note }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 60
            val targetRow = (maxMidi - avgNote - 6).coerceIn(0, (totalRows - 10).coerceAtLeast(0))
            with(density) { (targetRow * rowHeightDp).dp.roundToPx() }
        }
    }
    val vScroll = rememberScrollState(initial = initialScrollRow)

    val totalBeats = clip.totalBeats(tsNum).coerceAtLeast(tsNum.toFloat())
    val loopStart = clip.effectiveLoopStart(tsNum)
    val loopEnd = clip.effectiveLoopEnd(tsNum)
    val canvasWidthDp = (totalBeats * beatWidthDp).dp
    val canvasHeightDp = (totalRows * rowHeightDp).dp
    val beatPx = with(density) { beatWidthDp.dp.toPx() }
    val rowPx = with(density) { rowHeightDp.dp.toPx() }

    val isPlayingThisClip = telemetry.isPlaying && telemetry.activeClips.getOrElse(trackIndex) { -1 } == sceneIndex
    val playheadBeat = if (isPlayingThisClip) {
        val reported = telemetry.trackClipBeats.getOrElse(trackIndex) { -1.0 }
        val b = if (reported >= 0.0) reported.toFloat() else (telemetry.transportBeat % totalBeats).toFloat()
        b.coerceIn(0f, totalBeats)
    } else null

    LaunchedEffect(playheadBeat, autoFollowPlayhead, isPlayingThisClip, beatPx) {
        if (isPlayingThisClip && autoFollowPlayhead && playheadBeat != null && viewportWidthPx > 0f) {
            val px = playheadBeat * beatPx
            val curLeft = hScroll.value.toFloat()
            val curRight = curLeft + viewportWidthPx
            if (px < curLeft + 24f || px > curRight - 64f) {
                hScroll.scrollTo((px - viewportWidthPx * 0.25f).roundToInt().coerceAtLeast(0))
            }
        }
    }

    val latestClip by rememberUpdatedState(clip)
    val latestGrid by rememberUpdatedState(gridRes)
    val latestTool by rememberUpdatedState(activeTool)
    val latestLastDur by rememberUpdatedState(lastNoteDurationBeats)
    val latestSelected by rememberUpdatedState(selectedNoteIds)
    val latestTotalBeats by rememberUpdatedState(totalBeats)
    val latestBeatPx by rememberUpdatedState(beatPx)
    val latestRowPx by rememberUpdatedState(rowPx)

    val barPresets = remember { listOf(1, 2, 4, 8, 16) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        StudioTopBar(
            title = "${track.presetName} · ${clip.name}",
            titleColor = accent,
            onBack = onBack,
            backTestTag = "pianoroll_back_button",
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { autoFollowPlayhead = !autoFollowPlayhead },
                        modifier = Modifier.size(48.dp).testTag("pianoroll_follow_button")
                    ) {
                        Icon(
                            Icons.Default.CenterFocusStrong,
                            contentDescription = "Auto-follow Playhead",
                            tint = if (autoFollowPlayhead) accent else tokens.textMuted
                        )
                    }
                    IconButton(
                        onClick = {
                            beatWidthDp = if (beatWidthDp > 120f) 68f else if (beatWidthDp < 84f) 104f else 148f
                        },
                        modifier = Modifier.size(48.dp).testTag("pianoroll_zoom_button")
                    ) {
                        Icon(Icons.Default.OpenInFull, contentDescription = "Cycle Zoom", tint = tokens.textSecondary)
                    }
                }
            }
        )

        // Grid Resolution (including triplets), Clip Length (1..16 Bars), Loop Mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(tokens.surface)
                .border(0.5.dp, tokens.border)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            GridResolution.entries.forEach { res ->
                val sel = res == gridRes
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (sel) accent else tokens.padIdle)
                        .clickable {
                            gridRes = res
                            lastNoteDurationBeats = res.beats.toFloat().coerceAtLeast(0.125f)
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .testTag("grid_res_${res.name}")
                ) {
                    Text(res.label, style = MaterialTheme.typography.labelSmall, color = if (sel) Color.Black else tokens.textSecondary)
                }
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(tokens.padIdle)
                    .clickable {
                        onUpdateClip(true) { c ->
                            val cur = c.lengthBars
                            val nextBars = barPresets.firstOrNull { it > cur } ?: 1
                            val nextTot = (nextBars * tsNum).toFloat()
                            c.copy(
                                lengthBars = nextBars,
                                loopStartBeats = c.loopStartBeats.coerceIn(0f, (nextTot - tsNum).coerceAtLeast(0f)),
                                loopEndBeats = nextTot
                            )
                        }
                    }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("clip_length_button")
            ) {
                Text("${clip.lengthBars} Bars", style = MaterialTheme.typography.labelSmall, color = tokens.textPrimary)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (clip.loopEnabled) accent.copy(alpha = 0.25f) else tokens.padIdle)
                    .clickable { onUpdateClip(true) { c -> c.copy(loopEnabled = !c.loopEnabled) } }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(if (clip.loopEnabled) "Loop" else "1-Shot", style = MaterialTheme.typography.labelSmall, color = accent)
            }
        }

        // Interactive Bar & Loop Brace Ruler Header (only consumes pointer when dragging start/end flags so horizontal scroll works freely)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(tokens.surface)
                .border(0.5.dp, tokens.border)
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .border(0.5.dp, tokens.border),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (clip.loopEnabled) "${(loopEnd - loopStart).roundToInt()}b" else "1-Shot",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontSize = 9.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(hScroll)
            ) {
                Canvas(
                    modifier = Modifier
                        .width(canvasWidthDp)
                        .fillMaxHeight()
                        .pointerInput(totalBeats, tsNum) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val bp = latestBeatPx.coerceAtLeast(1f)
                                val c = latestClip
                                val curLs = c.effectiveLoopStart(tsNum)
                                val curLe = c.effectiveLoopEnd(tsNum)
                                val lsPx = curLs * bp
                                val lePx = curLe * bp
                                val handle = when {
                                    abs(down.position.x - lsPx) <= 28f -> 1
                                    abs(down.position.x - lePx) <= 28f -> 2
                                    else -> 0
                                }
                                if (handle == 0) {
                                    // Yield completely to parent horizontalScroll
                                    return@awaitEachGesture
                                }
                                var deltaX = 0f
                                var checkpointed = false
                                var lastX = down.position.x
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    val dx = ch.position.x - lastX
                                    lastX = ch.position.x
                                    deltaX += dx
                                    if (abs(deltaX) > 2f) {
                                        if (!checkpointed) {
                                            checkpointed = true
                                            onCheckpointUndo()
                                        }
                                        ch.consume()
                                        val deltaBeats = (deltaX / bp).roundToInt().toFloat()
                                        onUpdateClip(false) { cur ->
                                            val tot = cur.totalBeats(tsNum)
                                            if (handle == 1) {
                                                val ns = (curLs + deltaBeats).coerceIn(0f, (curLe - 1f).coerceAtLeast(0f))
                                                cur.copy(loopStartBeats = ns)
                                            } else {
                                                val ne = (curLe + deltaBeats).coerceIn((curLs + 1f).coerceAtMost(tot), tot)
                                                cur.copy(loopEndBeats = ne)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val lsX = loopStart * beatPx
                    val leX = loopEnd * beatPx
                    if (clip.loopEnabled) {
                        drawRect(
                            color = accent.copy(alpha = 0.22f),
                            topLeft = Offset(lsX, 0f),
                            size = Size((leX - lsX).coerceAtLeast(4f), size.height)
                        )
                        drawRect(
                            color = accent,
                            topLeft = Offset(lsX, 0f),
                            size = Size((leX - lsX).coerceAtLeast(4f), 4f)
                        )
                        val startPath = Path().apply {
                            moveTo(lsX, 0f)
                            lineTo(lsX + 10f, 0f)
                            lineTo(lsX, 12f)
                            close()
                        }
                        drawPath(startPath, accent)
                        val endPath = Path().apply {
                            moveTo(leX, 0f)
                            lineTo(leX - 10f, 0f)
                            lineTo(leX, 12f)
                            close()
                        }
                        drawPath(endPath, accent)
                    }

                    val beatsInt = totalBeats.roundToInt()
                    for (b in 0 until beatsInt) {
                        val x = b * beatPx
                        drawLine(
                            color = if (b % tsNum == 0) tokens.textSecondary else tokens.border,
                            start = Offset(x, 8f),
                            end = Offset(x, size.height),
                            strokeWidth = if (b % tsNum == 0) 2f else 1f
                        )
                    }
                    if (playheadBeat != null) {
                        val px = playheadBeat * beatPx
                        drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), 2.5f)
                    }
                }
                Row(modifier = Modifier.width(canvasWidthDp).fillMaxHeight().padding(top = 8.dp)) {
                    val beatsInt = totalBeats.roundToInt()
                    for (b in 0 until beatsInt) {
                        val bar = (b / tsNum) + 1
                        val beatInBar = (b % tsNum) + 1
                        Box(modifier = Modifier.width(beatWidthDp.dp).padding(start = 4.dp)) {
                            Text(
                                text = if (beatInBar == 1) "$bar" else "$bar.$beatInBar",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (beatInBar == 1) tokens.textPrimary else tokens.textMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        // Piano Keys / Drum Labels + Interactive Note Grid
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .verticalScroll(vScroll)
                    .background(tokens.surface)
            ) {
                for (rowIdx in 0 until totalRows) {
                    val midi = maxMidi - rowIdx
                    val isBlack = (midi % 12) in listOf(1, 3, 6, 8, 10)
                    val inScale = isDrum || MusicalTheory.isNoteInScale(midi, rootNote, scaleType)
                    val isRoot = !isDrum && (midi % 12) == (rootNote % 12)
                    val label = if (isDrum) {
                        track.drumPads.getOrNull(midi - 36)?.name ?: "Pad ${midi - 35}"
                    } else {
                        MusicalTheory.noteName(midi, true)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeightDp.dp)
                            .background(
                                when {
                                    isRoot -> accent.copy(alpha = 0.22f)
                                    !isDrum && isBlack -> Color(0xFF131317)
                                    inScale -> tokens.surfaceElevated
                                    else -> Color(0xFF151519)
                                }
                            )
                            .border(0.5.dp, tokens.border)
                            .pointerInput(midi, isDrum) {
                                detectTapGestures(
                                    onPress = {
                                        if (isDrum) {
                                            onAuditionDrumPad(midi - 36, 0.88f)
                                        } else {
                                            auditionJobs.remove(midi)?.cancel()
                                            onAuditionNote(midi, 0.85f)
                                            // Safety auto-stop failsafe in case scroll intercepts release
                                            val guardJob = scope.launch {
                                                delay(900L)
                                                onAuditionNoteOff(midi)
                                            }
                                            try {
                                                tryAwaitRelease()
                                            } finally {
                                                guardJob.cancel()
                                                onAuditionNoteOff(midi)
                                            }
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isRoot -> accent
                                !isDrum && midi % 12 == 0 -> tokens.textPrimary
                                inScale -> tokens.textSecondary
                                else -> tokens.textMuted
                            },
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val vpWidthPx = with(density) { maxWidth.toPx() }
                LaunchedEffect(vpWidthPx) { viewportWidthPx = vpWidthPx }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Dedicated 2-finger pinch-to-zoom handler
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        val zoom = event.calculateZoom()
                                        if (zoom != 1f && !zoom.isNaN()) {
                                            beatWidthDp = (beatWidthDp * zoom).coerceIn(36f, 220f)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                        .horizontalScroll(hScroll)
                        .verticalScroll(vScroll)
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(canvasWidthDp)
                            .height(canvasHeightDp)
                            .pointerInput(maxMidi, minMidi, isDrum) {
                                val touchSlop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val bp = latestBeatPx.coerceAtLeast(1f)
                                    val rp = latestRowPx.coerceAtLeast(1f)
                                    val tb = latestTotalBeats
                                    val curClip = latestClip
                                    val tool = latestTool
                                    val grid = latestGrid.beats.toFloat().coerceAtLeast(0.125f)

                                    fun findHitNote(pos: Offset): MidiNoteModel? {
                                        val beatPos = pos.x / bp
                                        val rowIdx = floor(pos.y / rp).toInt().coerceIn(0, totalRows - 1)
                                        val midi = maxMidi - rowIdx
                                        return curClip.notes.lastOrNull { n ->
                                            val visualEndBeat = max(n.startBeat + n.durationBeats, n.startBeat + (12f / bp))
                                            n.note == midi && beatPos >= n.startBeat && beatPos <= visualEndBeat
                                        }
                                    }

                                    val downHit = findHitNote(down.position)

                                    if (tool == PianoRollTool.ERASE) {
                                        var checkpointed = false
                                        val erasedIds = mutableSetOf<String>()
                                        fun eraseAt(pos: Offset) {
                                            val bPos = pos.x / bp
                                            val rIdx = floor(pos.y / rp).toInt().coerceIn(0, totalRows - 1)
                                            val m = maxMidi - rIdx
                                            val target = latestClip.notes.lastOrNull { n ->
                                                val visualEndBeat = max(n.startBeat + n.durationBeats, n.startBeat + (12f / bp))
                                                n.id !in erasedIds && n.note == m && bPos >= n.startBeat && bPos <= visualEndBeat
                                            }
                                            if (target != null) {
                                                if (!checkpointed) {
                                                    checkpointed = true
                                                    onCheckpointUndo()
                                                }
                                                erasedIds.add(target.id)
                                                selectedNoteIds = selectedNoteIds - target.id
                                                onUpdateClip(false) { c ->
                                                    c.copy(notes = c.notes.filterNot { it.id == target.id })
                                                }
                                            }
                                        }

                                        if (downHit != null) {
                                            down.consume()
                                            eraseAt(down.position)
                                            while (true) {
                                                val ev = awaitPointerEvent()
                                                if (ev.changes.count { it.pressed } > 1) break
                                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!ch.pressed) break
                                                ch.consume()
                                                eraseAt(ch.position)
                                            }
                                        } else {
                                            // Empty start in Erase: wait for slop; if user drags across notes, erase them; if single empty tap, do nothing
                                            var dragStarted = false
                                            while (true) {
                                                val ev = awaitPointerEvent()
                                                if (ev.changes.count { it.pressed } > 1) break
                                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!ch.pressed) break
                                                val dist = (ch.position - down.position).getDistance()
                                                if (!dragStarted && dist > touchSlop) {
                                                    dragStarted = true
                                                }
                                                if (dragStarted) {
                                                    ch.consume()
                                                    eraseAt(ch.position)
                                                }
                                            }
                                        }
                                        return@awaitEachGesture
                                    }

                                    // DRAW or SELECT mode
                                    if (downHit != null) {
                                        down.consume()
                                        val noteStartPx = downHit.startBeat * bp
                                        val noteEndPx = (downHit.startBeat + downHit.durationBeats).let { endB ->
                                            max(endB * bp, noteStartPx + 12f)
                                        }
                                        val mode = PianoRollDragMath.determineNoteDragMode(
                                            touchX = down.position.x,
                                            noteStartPx = noteStartPx,
                                            noteEndPx = noteEndPx
                                        )
                                        var dragging = false
                                        var dragSnapshot: Map<String, MidiNoteModel> = emptyMap()
                                        var lastAudPitch = downHit.note

                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            if (ev.changes.count { it.pressed } > 1) {
                                                // Abort single-note drag when pinch-zoom starts
                                                break
                                            }
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!ch.pressed) {
                                                if (!dragging) {
                                                    // Clean tap on existing note
                                                    if (tool == PianoRollTool.SELECT) {
                                                        val nextSel = if (downHit.id in latestSelected) {
                                                            latestSelected - downHit.id
                                                        } else {
                                                            latestSelected + downHit.id
                                                        }
                                                        selectedNoteIds = nextSel
                                                        if (downHit.id in nextSel) {
                                                            lastNoteDurationBeats = downHit.durationBeats.coerceAtLeast(0.125f)
                                                            triggerTimedNoteAudition(downHit.note, downHit.velocity)
                                                        }
                                                    } else {
                                                        selectedNoteIds = setOf(downHit.id)
                                                        lastNoteDurationBeats = downHit.durationBeats.coerceAtLeast(0.125f)
                                                        triggerTimedNoteAudition(downHit.note, downHit.velocity)
                                                    }
                                                } else if (mode == NoteDragMode.RESIZE_RIGHT) {
                                                    val resized = latestClip.notes.firstOrNull { it.id == downHit.id }
                                                    if (resized != null) {
                                                        lastNoteDurationBeats = resized.durationBeats.coerceAtLeast(0.125f)
                                                    }
                                                }
                                                break
                                            }

                                            val cumDelta = ch.position - down.position
                                            if (!dragging && cumDelta.getDistance() > touchSlop) {
                                                dragging = true
                                                val targetIds = if (downHit.id in latestSelected) latestSelected else {
                                                    selectedNoteIds = setOf(downHit.id)
                                                    setOf(downHit.id)
                                                }
                                                dragSnapshot = latestClip.notes.filter { it.id in targetIds }.associateBy { it.id }
                                                onCheckpointUndo()
                                            }
                                            if (dragging && dragSnapshot.isNotEmpty()) {
                                                ch.consume()
                                                val primaryInit = dragSnapshot[downHit.id]
                                                if (primaryInit != null && mode == NoteDragMode.MOVE) {
                                                    val previewPrimary = PianoRollDragMath.applyCumulativeDrag(
                                                        initialNote = primaryInit,
                                                        cumulativeDeltaPx = cumDelta.x,
                                                        cumulativeDeltaPy = cumDelta.y,
                                                        beatPx = bp,
                                                        rowPx = rp,
                                                        mode = mode,
                                                        snapGridBeats = grid,
                                                        totalBeats = tb,
                                                        minMidi = minMidi,
                                                        maxMidi = maxMidi
                                                    )
                                                    if (previewPrimary.note != lastAudPitch) {
                                                        lastAudPitch = previewPrimary.note
                                                        triggerTimedNoteAudition(previewPrimary.note, previewPrimary.velocity)
                                                    }
                                                }
                                                onUpdateClip(false) { c ->
                                                    c.copy(
                                                        notes = c.notes.map { n ->
                                                            val init = dragSnapshot[n.id] ?: return@map n
                                                            PianoRollDragMath.applyCumulativeDrag(
                                                                initialNote = init,
                                                                cumulativeDeltaPx = cumDelta.x,
                                                                cumulativeDeltaPy = cumDelta.y,
                                                                beatPx = bp,
                                                                rowPx = rp,
                                                                mode = mode,
                                                                snapGridBeats = grid,
                                                                totalBeats = tb,
                                                                minMidi = minMidi,
                                                                maxMidi = maxMidi
                                                            )
                                                        }.sortedWith(compareBy({ it.startBeat }, { it.note }))
                                                    )
                                                }
                                            }
                                        }
                                        return@awaitEachGesture
                                    }

                                    // Started on EMPTY grid area
                                    if (tool == PianoRollTool.DRAW) {
                                        // DO NOT consume down/move so horizontalScroll + verticalScroll work seamlessly across bars!
                                        var movedBeyondSlop = false
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            if (ev.changes.count { it.pressed } > 1) {
                                                movedBeyondSlop = true
                                                break
                                            }
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            if ((ch.position - down.position).getDistance() > touchSlop) {
                                                movedBeyondSlop = true
                                                break
                                            }
                                            if (ch.isConsumed) {
                                                movedBeyondSlop = true
                                                break
                                            }
                                            if (!ch.pressed) {
                                                break
                                            }
                                        }
                                        if (!movedBeyondSlop) {
                                            // Clean tap on empty space -> Draw note using remembered duration
                                            val tappedBeat = (down.position.x / bp).coerceIn(0f, (tb - 0.05f).coerceAtLeast(0f))
                                            val tappedRow = floor(down.position.y / rp).toInt().coerceIn(0, totalRows - 1)
                                            val tappedMidi = maxMidi - tappedRow
                                            val snappedBeat = (floor(tappedBeat / grid) * grid).coerceIn(0f, (tb - 0.125f).coerceAtLeast(0f))
                                            val newDur = latestLastDur.coerceIn(0.125f, (tb - snappedBeat).coerceAtLeast(0.125f))
                                            val newNote = MidiNoteModel(
                                                id = UUID.randomUUID().toString(),
                                                note = tappedMidi,
                                                startBeat = snappedBeat,
                                                durationBeats = newDur,
                                                velocity = 0.85f
                                            )
                                            selectedNoteIds = setOf(newNote.id)
                                            onUpdateClip(true) { c ->
                                                c.copy(notes = (c.notes + newNote).sortedWith(compareBy({ it.startBeat }, { it.note })))
                                            }
                                            triggerTimedNoteAudition(tappedMidi, 0.85f)
                                        }
                                    } else {
                                        // PianoRollTool.SELECT on empty space:
                                        // - Tapping empty area does NOTHING (never creates notes!)
                                        // - Dragging draws marquee selection box to multi-select notes cleanly
                                        var draggingMarquee = false
                                        down.consume()
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            if (ev.changes.count { it.pressed } > 1) {
                                                marqueeRect = null
                                                break
                                            }
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!ch.pressed) {
                                                if (draggingMarquee) {
                                                    val rect = marqueeRect
                                                    if (rect != null) {
                                                        val picked = curClip.notes.filter { n ->
                                                            if (n.note !in minMidi..maxMidi) return@filter false
                                                            val r = maxMidi - n.note
                                                            val nx1 = n.startBeat * bp
                                                            val nx2 = nx1 + max(n.durationBeats * bp, 12f)
                                                            val ny1 = r * rp + 2f
                                                            val ny2 = (r + 1) * rp - 2f
                                                            nx2 >= rect.left && nx1 <= rect.right && ny2 >= rect.top && ny1 <= rect.bottom
                                                        }
                                                        selectedNoteIds = picked.map { it.id }.toSet()
                                                        picked.lastOrNull()?.let {
                                                            lastNoteDurationBeats = it.durationBeats.coerceAtLeast(0.125f)
                                                        }
                                                    }
                                                }
                                                marqueeRect = null
                                                break
                                            }
                                            val dist = (ch.position - down.position).getDistance()
                                            if (!draggingMarquee && dist > touchSlop) {
                                                draggingMarquee = true
                                            }
                                            if (draggingMarquee) {
                                                ch.consume()
                                                val left = min(down.position.x, ch.position.x)
                                                val right = max(down.position.x, ch.position.x)
                                                val top = min(down.position.y, ch.position.y)
                                                val bottom = max(down.position.y, ch.position.y)
                                                marqueeRect = Rect(left, top, right, bottom)
                                            }
                                        }
                                    }
                                }
                            }
                            .testTag("pianoroll_grid_canvas")
                    ) {
                        // Row backgrounds with subtle scale guide
                        for (r in 0 until totalRows) {
                            val midi = maxMidi - r
                            val isBlack = !isDrum && ((midi % 12) in listOf(1, 3, 6, 8, 10))
                            val inScale = isDrum || MusicalTheory.isNoteInScale(midi, rootNote, scaleType)
                            val bg = when {
                                isBlack -> Color(0xFF111115)
                                !isDrum && inScale -> Color(0xFF1B1B21)
                                r % 2 == 0 -> Color(0xFF18181D)
                                else -> Color(0xFF141419)
                            }
                            drawRect(bg, Offset(0f, r * rowPx), Size(size.width, rowPx))
                            drawLine(tokens.border, Offset(0f, (r + 1) * rowPx), Offset(size.width, (r + 1) * rowPx), 1f)
                        }

                        // Grid vertical lines
                        val gridStep = gridRes.beats.toFloat().coerceAtLeast(0.125f)
                        var bCursor = 0f
                        while (bCursor <= totalBeats + 0.001f) {
                            val x = bCursor * beatPx
                            val isBar = (bCursor % tsNum.toFloat()) < 0.02f || (tsNum.toFloat() - (bCursor % tsNum.toFloat())) < 0.02f
                            val isBeat = (bCursor % 1f) < 0.02f || (1f - (bCursor % 1f)) < 0.02f
                            drawLine(
                                color = when {
                                    isBar -> tokens.textMuted
                                    isBeat -> tokens.border.copy(alpha = 0.9f)
                                    else -> tokens.border.copy(alpha = 0.35f)
                                },
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = if (isBar) 2f else 1f
                            )
                            bCursor += gridStep
                        }

                        // Dim regions outside active loop brace
                        if (clip.loopEnabled) {
                            if (loopStart > 0.01f) {
                                drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, 0f), Size(loopStart * beatPx, size.height))
                            }
                            if (loopEnd < totalBeats - 0.01f) {
                                val lx = loopEnd * beatPx
                                drawRect(Color.Black.copy(alpha = 0.35f), Offset(lx, 0f), Size((size.width - lx).coerceAtLeast(0f), size.height))
                            }
                        }

                        // MIDI Notes with velocity fill + right-half resize grip indicator
                        for (n in clip.notes) {
                            if (n.note !in minMidi..maxMidi) continue
                            val row = maxMidi - n.note
                            val x = n.startBeat * beatPx
                            val y = row * rowPx + 2f
                            val w = (n.durationBeats * beatPx).coerceAtLeast(12f)
                            val h = (rowPx - 4f).coerceAtLeast(6f)
                            val isSel = n.id in selectedNoteIds
                            val insideLoop = !clip.loopEnabled || (n.startBeat >= loopStart - 0.01f && n.startBeat < loopEnd)
                            val velAlpha = (0.4f + n.velocity * 0.6f) * (if (insideLoop) 1f else 0.4f)
                            val noteColor = if (isSel) Color(0xFF22D3EE) else accent.copy(alpha = velAlpha)
                            drawRect(noteColor, Offset(x, y), Size(w, h))
                            if (isSel) {
                                drawRect(Color.White, Offset(x, y), Size(w, h), style = Stroke(2.5f))
                            }
                            // Right-edge resize handle cue (visual affordance for rightmost ~50% drag zone)
                            drawRect(Color.White.copy(alpha = 0.85f), Offset(x + w - 4f, y + 2f), Size(2.5f, (h - 4f).coerceAtLeast(2f)))
                            if (w > 22f) {
                                drawRect(Color.White.copy(alpha = 0.45f), Offset(x + w - 8f, y + 4f), Size(1.5f, (h - 8f).coerceAtLeast(2f)))
                            }
                        }

                        // Marquee selection box
                        marqueeRect?.let { rect ->
                            drawRect(
                                color = Color(0xFF22D3EE).copy(alpha = 0.16f),
                                topLeft = rect.topLeft,
                                size = rect.size
                            )
                            drawRect(
                                color = Color(0xFF22D3EE),
                                topLeft = rect.topLeft,
                                size = rect.size,
                                style = Stroke(2f)
                            )
                        }

                        if (playheadBeat != null) {
                            val px = playheadBeat * beatPx
                            drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), 2.5f)
                        }
                    }
                }
            }
        }

        // Visual Velocity Lane + Selected Velocity Slider
        if (showVelocity) {
            val selNote = clip.notes.firstOrNull { it.id in selectedNoteIds }
            var sliderDragging by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.surface)
                    .border(0.5.dp, tokens.border)
            ) {
                Row(modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Box(
                        modifier = Modifier.width(56.dp).fillMaxHeight().border(0.5.dp, tokens.border),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("VEL", style = MaterialTheme.typography.labelSmall, color = accent, fontSize = 9.sp)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                        Canvas(modifier = Modifier.width(canvasWidthDp).fillMaxHeight()) {
                            for (n in clip.notes) {
                                val x = n.startBeat * beatPx
                                val barH = (n.velocity.coerceIn(0.05f, 1f) * (size.height - 8f))
                                val isSel = n.id in selectedNoteIds
                                val c = if (isSel) Color(0xFF22D3EE) else accent.copy(alpha = 0.75f)
                                drawLine(c, Offset(x + 4f, size.height), Offset(x + 4f, size.height - barH), 3f)
                                drawCircle(c, radius = 4f, center = Offset(x + 4f, size.height - barH))
                            }
                            if (playheadBeat != null) {
                                val px = playheadBeat * beatPx
                                drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), 1.5f)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (selNote != null) "Vel ${(selNote.velocity * 127).roundToInt()}" else "Tap a note to edit velocity",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                    if (selNote != null) {
                        Slider(
                            value = selNote.velocity,
                            onValueChange = { v ->
                                if (!sliderDragging) {
                                    sliderDragging = true
                                    onCheckpointUndo()
                                }
                                onUpdateClip(false) { c ->
                                    c.copy(notes = c.notes.map { if (it.id in selectedNoteIds) it.copy(velocity = v) else it })
                                }
                            },
                            onValueChangeFinished = { sliderDragging = false },
                            valueRange = 0.08f..1f,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Automation Lane Preview Strip
        if (showAutomation) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(tokens.surface)
                    .border(0.5.dp, tokens.border)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val lanes = clip.automationLanes
                if (lanes.isEmpty()) {
                    Text("No recorded automation in this clip", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                } else {
                    Text("Auto:", style = MaterialTheme.typography.labelSmall, color = accent)
                    lanes.forEach { lane ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(tokens.padIdle)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("${lane.label} (${lane.points.size})", style = MaterialTheme.typography.labelSmall, color = tokens.textPrimary)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    SmallToolBtn(
                        label = "Clear",
                        icon = Icons.Default.DeleteSweep,
                        tooltip = "Clear Automation",
                        color = Color(0xFFEF4444),
                        testTag = "pianoroll_clear_auto_button"
                    ) {
                        onUpdateClip(true) { c -> c.copy(automationLanes = emptyList()) }
                    }
                }
            }
        }

        // Visual divider between piano roll grid / lanes and bottom control dock
        HorizontalDivider(thickness = 1.dp, color = tokens.border)

        // Secondary Editing Controls Strip (icon-first + compact labels/tooltips, smooth horizontal scroll)
        val allNotesSelected = selectedNoteIds.size == clip.notes.size && clip.notes.isNotEmpty()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(tokens.surfaceElevated)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallToolBtn(
                label = "Q",
                icon = Icons.Default.GridOn,
                tooltip = "Quantize notes (${if (quantizeStrength >= 0.99f) "100%" else "50%"})",
                color = accent,
                testTag = "pianoroll_quantize_button"
            ) {
                onUpdateClip(true) { c ->
                    StudioProjectTransforms.quantizeClipNotes(c, selectedNoteIds, gridRes.beats, quantizeStrength, tsNum)
                }
            }
            SmallToolBtn(
                label = if (quantizeStrength >= 0.99f) "100%" else "50%",
                icon = null,
                tooltip = "Toggle quantize strength (50% / 100%)",
                color = tokens.textSecondary,
                testTag = "pianoroll_quantize_strength_button"
            ) {
                quantizeStrength = if (quantizeStrength >= 0.99f) 0.5f else 1.0f
            }
            SmallToolBtn(
                label = null,
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                tooltip = "Nudge notes left",
                color = tokens.textSecondary,
                testTag = "pianoroll_nudge_left_button"
            ) {
                onUpdateClip(true) { c -> StudioProjectTransforms.nudgeClipNotes(c, selectedNoteIds, -gridRes.beats.toFloat(), tsNum) }
            }
            SmallToolBtn(
                label = null,
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                tooltip = "Nudge notes right",
                color = tokens.textSecondary,
                testTag = "pianoroll_nudge_right_button"
            ) {
                onUpdateClip(true) { c -> StudioProjectTransforms.nudgeClipNotes(c, selectedNoteIds, gridRes.beats.toFloat(), tsNum) }
            }
            SmallToolBtn(
                label = "Dup",
                icon = Icons.Default.ControlPointDuplicate,
                tooltip = "Duplicate selected notes",
                color = tokens.textPrimary,
                testTag = "pianoroll_dup_notes_button"
            ) {
                onUpdateClip(true) { c ->
                    val (updated, newSel) = StudioProjectTransforms.duplicateClipNotes(c, selectedNoteIds, tsNum)
                    selectedNoteIds = newSel
                    updated
                }
            }
            SmallToolBtn(
                label = "2x",
                icon = Icons.Default.Repeat,
                tooltip = "Duplicate loop length",
                color = accent,
                testTag = "pianoroll_dup_loop_button"
            ) {
                onUpdateClip(true) { c -> StudioProjectTransforms.duplicateClipLoop(c, tsNum) }
            }
            SmallToolBtn(
                label = "Crop",
                icon = Icons.Default.Crop,
                tooltip = "Crop clip to loop",
                color = tokens.textSecondary,
                testTag = "pianoroll_crop_loop_button"
            ) {
                onUpdateClip(true) { c -> StudioProjectTransforms.cropClipToLoop(c, tsNum) }
            }
            SmallToolBtn(
                label = "Clear",
                icon = Icons.Default.DeleteSweep,
                tooltip = "Clear notes inside loop",
                color = Color(0xFFEF4444),
                testTag = "pianoroll_clear_loop_button"
            ) {
                onUpdateClip(true) { c -> StudioProjectTransforms.clearClipLoop(c, tsNum) }
                selectedNoteIds = emptySet()
            }
            SmallToolBtn(
                label = "Loop",
                icon = Icons.Default.CenterFocusWeak,
                tooltip = "Zoom to loop region",
                color = tokens.textSecondary,
                testTag = "pianoroll_zoom_loop_button"
            ) {
                val loopSpan = (loopEnd - loopStart).coerceAtLeast(1f)
                val targetBeatPx = (viewportWidthPx / loopSpan).coerceIn(
                    with(density) { 24.dp.toPx() },
                    with(density) { 240.dp.toPx() }
                )
                beatWidthDp = with(density) { targetBeatPx.toDp().value }.coerceIn(24f, 240f)
                scope.launch { hScroll.scrollTo((loopStart * targetBeatPx).roundToInt().coerceAtLeast(0)) }
            }
            SmallToolBtn(
                label = "Clip",
                icon = Icons.Default.ZoomOutMap,
                tooltip = "Zoom to entire clip",
                color = tokens.textSecondary,
                testTag = "pianoroll_zoom_clip_button"
            ) {
                val targetBeatPx = (viewportWidthPx / totalBeats).coerceIn(
                    with(density) { 24.dp.toPx() },
                    with(density) { 240.dp.toPx() }
                )
                beatWidthDp = with(density) { targetBeatPx.toDp().value }.coerceIn(24f, 240f)
                scope.launch { hScroll.scrollTo(0) }
            }
            SmallToolBtn(
                label = "Vel",
                icon = Icons.Default.Equalizer,
                tooltip = "Toggle velocity lane",
                color = if (showVelocity) accent else tokens.textSecondary,
                active = showVelocity,
                activeColor = accent,
                testTag = "pianoroll_velocity_button"
            ) {
                showVelocity = !showVelocity
            }
            SmallToolBtn(
                label = "Auto",
                icon = Icons.AutoMirrored.Filled.ShowChart,
                tooltip = "Toggle automation lane",
                color = if (showAutomation) accent else tokens.textSecondary,
                active = showAutomation,
                activeColor = accent,
                testTag = "pianoroll_auto_button"
            ) {
                showAutomation = !showAutomation
            }
            SmallToolBtn(
                label = if (allNotesSelected) "None" else "All",
                icon = if (allNotesSelected) Icons.Default.Deselect else Icons.Default.DoneAll,
                tooltip = if (allNotesSelected) "Deselect all notes" else "Select all notes",
                color = if (allNotesSelected) Color(0xFF22D3EE) else tokens.textSecondary,
                active = allNotesSelected,
                activeColor = Color(0xFF22D3EE),
                testTag = "pianoroll_select_all_button"
            ) {
                selectedNoteIds = if (allNotesSelected) emptySet() else clip.notes.map { it.id }.toSet()
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = tokens.border)

        // Bottom Toolbar: evenly distributed controls & equal-size icon-only Draw / Select / Erase tools
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.surface)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(56.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BottomToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                enabled = canUndo,
                tint = if (canUndo) tokens.textPrimary else tokens.textMuted,
                tag = "pianoroll_undo_button",
                onClick = onUndo,
                modifier = Modifier.weight(1f)
            )
            BottomToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                enabled = canRedo,
                tint = if (canRedo) tokens.textPrimary else tokens.textMuted,
                tag = "pianoroll_redo_button",
                onClick = onRedo,
                modifier = Modifier.weight(1f)
            )

            // Icon-only Draw | Select | Erase Segmented Group (equal sizes, evenly distributed)
            Row(
                modifier = Modifier
                    .weight(3.2f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(tokens.bg)
                    .border(0.5.dp, tokens.border, RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolModePill(
                    icon = Icons.Default.Edit,
                    contentDescription = "Draw",
                    selected = activeTool == PianoRollTool.DRAW,
                    activeColor = accent,
                    tag = "pianoroll_tool_draw",
                    onClick = { activeTool = PianoRollTool.DRAW },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                ToolModePill(
                    icon = Icons.Default.HighlightAlt,
                    contentDescription = "Select",
                    selected = activeTool == PianoRollTool.SELECT,
                    activeColor = Color(0xFF22D3EE),
                    tag = "pianoroll_select_mode",
                    onClick = { activeTool = PianoRollTool.SELECT },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                ToolModePill(
                    icon = EraserIcon,
                    contentDescription = "Erase",
                    selected = activeTool == PianoRollTool.ERASE,
                    activeColor = Color(0xFFEF4444),
                    tag = "pianoroll_tool_erase",
                    onClick = { activeTool = PianoRollTool.ERASE },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            BottomToolbarIconButton(
                icon = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                enabled = clip.notes.isNotEmpty(),
                tint = if (clip.notes.isNotEmpty()) tokens.textSecondary else tokens.textMuted,
                tag = "pianoroll_copy_button",
                onClick = {
                    val toCopy = if (selectedNoteIds.isNotEmpty()) clip.notes.filter { it.id in selectedNoteIds } else clip.notes
                    clipboardNotes = toCopy
                },
                modifier = Modifier.weight(1f)
            )
            BottomToolbarIconButton(
                icon = Icons.Default.ContentPaste,
                contentDescription = "Paste",
                enabled = clipboardNotes.isNotEmpty(),
                tint = if (clipboardNotes.isNotEmpty()) accent else tokens.textMuted,
                tag = "pianoroll_paste_button",
                onClick = {
                    if (clipboardNotes.isNotEmpty()) {
                        val targetBeat = playheadBeat ?: clip.notes.filter { it.id in selectedNoteIds }.maxOfOrNull { it.startBeat + it.durationBeats } ?: loopStart
                        onUpdateClip(true) { c ->
                            val (updated, newSel) = StudioProjectTransforms.pasteClipNotes(c, clipboardNotes, targetBeat, tsNum)
                            selectedNoteIds = newSel
                            updated
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            BottomToolbarIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Delete Notes",
                enabled = selectedNoteIds.isNotEmpty(),
                tint = if (selectedNoteIds.isNotEmpty()) Color(0xFFEF4444) else tokens.textMuted,
                tag = "pianoroll_delete_button",
                onClick = {
                    if (selectedNoteIds.isNotEmpty()) {
                        onUpdateClip(true) { c -> c.copy(notes = c.notes.filterNot { it.id in selectedNoteIds }) }
                        selectedNoteIds = emptySet()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                TransportRingButton(
                    isPlaying = telemetry.isPlaying,
                    phaseProgress = ((telemetry.beat - 1) + (telemetry.sixteenth - 1) / 4f) / tsNum.toFloat(),
                    onClick = onTogglePlay,
                    accentColor = Color.White,
                    testTag = "pianoroll_play_button"
                )
            }
        }
    }
}

private val EraserIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Eraser",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(15.14f, 3.64f)
            lineTo(20.36f, 8.86f)
            curveTo(21.14f, 9.64f, 21.14f, 10.91f, 20.36f, 11.69f)
            lineTo(11.36f, 20.69f)
            lineTo(2.94f, 20.69f)
            lineTo(2.94f, 18.69f)
            lineTo(8.11f, 18.69f)
            lineTo(3.64f, 14.22f)
            curveTo(2.86f, 13.44f, 2.86f, 12.17f, 3.64f, 11.39f)
            lineTo(12.31f, 3.64f)
            curveTo(13.09f, 2.86f, 14.36f, 2.86f, 15.14f, 3.64f)
            close()
            moveTo(6.47f, 14.22f)
            lineTo(10.61f, 18.36f)
            lineTo(13.92f, 15.05f)
            lineTo(9.78f, 10.91f)
            close()
        }
    }.build()
}

@Composable
private fun BottomToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    tint: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolModePill(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    activeColor: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(contentDescription) } },
            state = rememberTooltipState(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) activeColor.copy(alpha = 0.24f) else tokens.padIdle)
                    .border(
                        width = if (selected) 1.dp else 0.5.dp,
                        color = if (selected) activeColor else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable(onClick = onClick)
                    .testTag(tag),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (selected) activeColor else tokens.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallToolBtn(
    label: String?,
    icon: ImageVector?,
    tooltip: String,
    color: Color,
    active: Boolean = false,
    activeColor: Color = color,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val tokens = LocalStudioTokens.current
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (active) activeColor.copy(alpha = 0.22f) else tokens.padIdle)
                .border(
                    width = if (active) 1.dp else 0.5.dp,
                    color = if (active) activeColor else tokens.border,
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = if (label != null) 8.dp else 7.dp)
                .let { if (testTag != null) it.testTag(testTag) else it },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = tooltip,
                    tint = color,
                    modifier = Modifier.size(15.dp)
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontSize = 10.sp
                )
            }
        }
    }
}
