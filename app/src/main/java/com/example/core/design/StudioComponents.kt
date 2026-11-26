package com.example.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.project.ClipModel
import com.example.core.project.TrackType
import com.example.ui.theme.LocalStudioTokens
import com.example.ui.theme.TrackPalette
import kotlin.math.abs

@Composable
fun StudioTopBar(
    title: String,
    titleColor: Color = LocalStudioTokens.current.textPrimary,
    onBack: (() -> Unit)? = null,
    backTestTag: String = "back_button",
    trailingContent: @Composable (() -> Unit)? = null
) {
    val tokens = LocalStudioTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(tokens.surface)
            .border(0.5.dp, tokens.border)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(backTestTag)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = tokens.textSecondary
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
fun RotaryKnob(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    defaultValue: Float = 0.5f,
    valueFormatter: (Float) -> String = { "${(it * 100).toInt()}%" },
    testTag: String = "knob_${label.lowercase().replace(" ", "_")}"
) {
    val tokens = LocalStudioTokens.current
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var currentVal by remember(value) { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    val latestOnChange by rememberUpdatedState(onValueChange)

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
            .pointerInput(defaultValue) {
                detectTapGestures(
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentVal = defaultValue.coerceIn(0f, 1f)
                        latestOnChange(currentVal)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val delta = (-dragAmount.y + dragAmount.x * 0.5f) / 220f
                        currentVal = (currentVal + delta).coerceIn(0f, 1f)
                        latestOnChange(currentVal)
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDragging) accentColor else tokens.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 3.2.dp.toPx()
                val pad = strokeW / 2f + 2.dp.toPx()
                val arcSize = Size(this.size.width - pad * 2, this.size.height - pad * 2)
                val topLeft = Offset(pad, pad)
                drawArc(
                    color = tokens.padIdle,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentColor,
                    startAngle = 135f,
                    sweepAngle = 270f * currentVal,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
            Text(
                text = valueFormatter(currentVal),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDragging) tokens.textPrimary else tokens.textMuted,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun TransportRingButton(
    isPlaying: Boolean,
    phaseProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White,
    testTag: String = "transport_play_button"
) {
    val tokens = LocalStudioTokens.current
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(38.dp)) {
            drawCircle(
                color = tokens.border,
                style = Stroke(width = 2.dp.toPx())
            )
            if (isPlaying) {
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * phaseProgress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Icon(
            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Stop" else "Play",
            tint = tokens.textPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun TrackTypeIcon(
    type: TrackType,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val icon = when (type) {
        TrackType.DRUM_RACK -> Icons.Default.GridView
        TrackType.SUBTRACTIVE_SYNTH -> Icons.Default.GraphicEq
        TrackType.MELODIC_SAMPLER -> Icons.Default.Piano
    }
    Icon(
        imageVector = icon,
        contentDescription = type.label,
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun MiniClipPreview(
    clip: ClipModel,
    accentColor: Color,
    playheadNorm: Float? = null,
    timeSigNum: Int = 4,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(1.dp))) {
        if (size.width <= 1f || size.height <= 1f) return@Canvas
        val tsNum = timeSigNum.coerceIn(1, 12)
        val loopStart = clip.effectiveLoopStart(tsNum)
        val loopEnd = clip.effectiveLoopEnd(tsNum)
        val spanBeats = (loopEnd - loopStart).coerceAtLeast(1f)
        val notes = clip.notes
        if (notes.isNotEmpty()) {
            val minNote = (notes.minOf { it.note } - 2).coerceAtLeast(0)
            val maxNote = (notes.maxOf { it.note } + 2).coerceAtMost(127)
            val range = (maxNote - minNote).coerceAtLeast(12).toFloat()
            val noteH = 2.5.dp.toPx().coerceAtMost(size.height * 0.25f)

            for (n in notes) {
                val noteEnd = n.startBeat + n.durationBeats
                if (noteEnd <= loopStart || n.startBeat >= loopEnd) continue
                val clippedStart = n.startBeat.coerceIn(loopStart, loopEnd)
                val clippedEnd = noteEnd.coerceIn(clippedStart, loopEnd)
                val rawX = ((clippedStart - loopStart) / spanBeats) * size.width
                val rawW = (((clippedEnd - clippedStart) / spanBeats) * size.width).coerceAtLeast(2.dp.toPx())
                val x = rawX.coerceIn(0f, (size.width - 1f).coerceAtLeast(0f))
                val w = rawW.coerceIn(1f, (size.width - x).coerceAtLeast(1f))
                val yNorm = 1f - ((n.note - minNote).toFloat() / range)
                val y = (yNorm * (size.height - noteH - 1f)).coerceIn(0f, (size.height - noteH).coerceAtLeast(0f))
                drawRect(
                    color = accentColor.copy(alpha = 0.85f),
                    topLeft = Offset(x, y),
                    size = Size(w, noteH)
                )
            }
        }
        if (playheadNorm != null) {
            val px = playheadNorm.coerceIn(0f, 1f) * size.width
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(px, 0f),
                end = Offset(px, size.height),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

@Composable
fun WaveformDisplay(
    peaks: List<Float>,
    accentColor: Color,
    startNorm: Float = 0f,
    endNorm: Float = 1f,
    playheadNorm: Float? = null,
    emptyText: String? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF09090B), RoundedCornerShape(3.dp))
            .border(0.5.dp, tokens.border, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val midY = size.height / 2f
            drawLine(
                color = tokens.border,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1f
            )

            if (peaks.isNotEmpty()) {
                val barW = size.width / peaks.size.toFloat()
                peaks.forEachIndexed { idx, pk ->
                    val normX = idx.toFloat() / peaks.size.toFloat()
                    val inRange = normX in startNorm..endNorm
                    val h = (pk.coerceIn(0.02f, 1f) * size.height * 0.86f).coerceAtLeast(2f)
                    drawRect(
                        color = if (inRange) accentColor else accentColor.copy(alpha = 0.25f),
                        topLeft = Offset(idx * barW, midY - h / 2f),
                        size = Size((barW * 0.72f).coerceAtLeast(1.5f), h)
                    )
                }
            }

            if (peaks.isNotEmpty()) {
                val sx = startNorm.coerceIn(0f, 1f) * size.width
                val ex = endNorm.coerceIn(0f, 1f) * size.width
                if (startNorm > 0.005f) {
                    drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(sx, size.height))
                    drawLine(Color.White, Offset(sx, 0f), Offset(sx, size.height), 2f)
                }
                if (endNorm < 0.995f) {
                    drawRect(Color.Black.copy(alpha = 0.5f), Offset(ex, 0f), Size(size.width - ex, size.height))
                    drawLine(Color.White, Offset(ex, 0f), Offset(ex, size.height), 2f)
                }
            }
            if (playheadNorm != null) {
                val px = playheadNorm.coerceIn(0f, 1f) * size.width
                drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), 2f)
            }
        }
        if (peaks.isEmpty() && !emptyText.isNullOrBlank()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun LevelMeterBar(
    level: Float,
    accentColor: Color,
    isClipping: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(tokens.bg)
            .border(0.5.dp, tokens.border, RoundedCornerShape(2.dp))
    ) {
        val clamped = level.coerceIn(0f, 1f)
        val fillH = size.height * clamped
        val color = when {
            isClipping || clamped > 0.95f -> Color(0xFFEF4444)
            clamped > 0.78f -> Color(0xFFFBBF24)
            else -> accentColor
        }
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height - fillH),
            size = Size(size.width, fillH)
        )
    }
}

@Composable
fun GenerativeSetArtwork(
    seed: Int,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(tokens.bg)
            .border(0.5.dp, tokens.border, RoundedCornerShape(4.dp))
    ) {
        val c1 = TrackPalette[abs(seed) % TrackPalette.size]
        val c2 = TrackPalette[abs(seed / 7 + 2) % TrackPalette.size]
        val cols = 4
        val rows = 4
        val cellW = size.width / cols
        val cellH = size.height / rows
        var bits = seed
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                bits = bits * 1103515245 + 12345
                val active = ((bits ushr 16) and 3) != 0
                if (active) {
                    val col = if (((bits ushr 18) and 1) == 0) c1 else c2
                    drawRect(
                        color = col.copy(alpha = 0.85f),
                        topLeft = Offset(c * cellW + 2f, r * cellH + 2f),
                        size = Size(cellW - 4f, cellH - 4f)
                    )
                }
            }
        }
    }
}
