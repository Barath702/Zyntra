package com.example.feature.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.TransportTelemetryState
import com.example.core.design.LevelMeterBar
import com.example.core.project.NoteSet
import com.example.ui.theme.LocalStudioTokens
import com.example.ui.theme.StudioDanger
import com.example.ui.theme.trackAccentColor
import kotlin.math.log10
import kotlin.math.roundToInt

@Composable
fun MixerPanel(
    set: NoteSet,
    telemetry: TransportTelemetryState,
    onVolumeChange: (Int, Float) -> Unit,
    onPanChange: (Int, Float) -> Unit,
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onOpenMasterFx: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(256.dp)
            .background(tokens.surface)
            .border(0.5.dp, tokens.border)
            .padding(8.dp)
            .testTag("mixer_panel")
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            set.tracks.forEachIndexed { idx, track ->
                val accent = trackAccentColor(track.colorIndex)
                val peak = telemetry.trackPeaks.getOrElse(idx) { 0f }
                Column(
                    modifier = Modifier
                        .width(74.dp)
                        .fillMaxHeight()
                        .background(tokens.bg, RoundedCornerShape(4.dp))
                        .border(0.5.dp, tokens.border, RoundedCornerShape(4.dp))
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (track.muted) Color(0xFFF59E0B) else tokens.padIdle)
                                .clickable { onToggleMute(idx) }
                                .testTag("mixer_mute_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("M", style = MaterialTheme.typography.labelSmall, color = if (track.muted) Color.Black else tokens.textPrimary)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (track.soloed) Color(0xFF22D3EE) else tokens.padIdle)
                                .clickable { onToggleSolo(idx) }
                                .testTag("mixer_solo_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("S", style = MaterialTheme.typography.labelSmall, color = if (track.soloed) Color.Black else tokens.textPrimary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Pan ${((track.pan * 50).roundToInt())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                        fontSize = 9.sp
                    )
                    Slider(
                        value = track.pan,
                        onValueChange = { onPanChange(idx, it) },
                        valueRange = -1f..1f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        modifier = Modifier.height(22.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LevelMeterBar(
                            level = peak,
                            accentColor = accent,
                            modifier = Modifier.width(12.dp).fillMaxHeight()
                        )
                        Spacer(Modifier.width(8.dp))
                        // Vertical-feeling level slider
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val db = if (track.volume < 0.01f) "-∞" else "${(20f * log10(track.volume)).roundToInt()}dB"
                            Text(db, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary, fontSize = 9.sp)
                            Slider(
                                value = track.volume,
                                onValueChange = { onVolumeChange(idx, it) },
                                valueRange = 0f..1.2f,
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(6.dp))
        // Master Strip
        Column(
            modifier = Modifier
                .width(76.dp)
                .fillMaxHeight()
                .background(tokens.surfaceElevated, RoundedCornerShape(4.dp))
                .border(0.5.dp, if (telemetry.masterClipping) StudioDanger else tokens.border, RoundedCornerShape(4.dp))
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MASTER",
                style = MaterialTheme.typography.labelMedium,
                color = if (telemetry.masterClipping) StudioDanger else tokens.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tokens.padIdle)
                    .clickable(onClick = onOpenMasterFx)
                    .testTag("master_fx_button"),
                contentAlignment = Alignment.Center
            ) {
                Text("FX", style = MaterialTheme.typography.labelSmall, color = tokens.textPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LevelMeterBar(
                    level = telemetry.masterPeakL,
                    accentColor = Color(0xFF2ED47A),
                    isClipping = telemetry.masterClipping,
                    modifier = Modifier.width(10.dp).fillMaxHeight()
                )
                LevelMeterBar(
                    level = telemetry.masterPeakR,
                    accentColor = Color(0xFF2ED47A),
                    isClipping = telemetry.masterClipping,
                    modifier = Modifier.width(10.dp).fillMaxHeight()
                )
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = set.masterVolume,
                onValueChange = onMasterVolumeChange,
                valueRange = 0f..1.2f,
                modifier = Modifier.height(24.dp)
            )
        }
    }
}
