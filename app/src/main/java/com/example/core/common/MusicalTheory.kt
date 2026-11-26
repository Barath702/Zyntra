package com.example.core.common

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class ScaleType(val displayName: String, val intervals: List<Int>) {
    MAJOR("Major", listOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR("Natural Minor", listOf(0, 2, 3, 5, 7, 8, 10)),
    HARMONIC_MINOR("Harmonic Minor", listOf(0, 2, 3, 5, 7, 8, 11)),
    MELODIC_MINOR("Melodic Minor", listOf(0, 2, 3, 5, 7, 9, 11)),
    DORIAN("Dorian", listOf(0, 2, 3, 5, 7, 9, 10)),
    PHRYGIAN("Phrygian", listOf(0, 1, 3, 5, 7, 8, 10)),
    LYDIAN("Lydian", listOf(0, 2, 4, 6, 7, 9, 11)),
    MIXOLYDIAN("Mixolydian", listOf(0, 2, 4, 5, 7, 9, 10)),
    PENTATONIC_MAJOR("Pentatonic Major", listOf(0, 2, 4, 7, 9)),
    PENTATONIC_MINOR("Pentatonic Minor", listOf(0, 3, 5, 7, 10)),
    BLUES("Blues", listOf(0, 3, 5, 6, 7, 10)),
    CHROMATIC("Chromatic", listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))
}

@Serializable
enum class MelodicPadLayout(val displayName: String) {
    FOURTHS("Fourths (5x5)"),
    SEQUENTIAL("Sequential (5x5)"),
    SCALE_COLUMNS("Scale Columns")
}

@Serializable
enum class GlobalQuantization(val displayName: String, val index: Int) {
    NONE("Off", 0),
    QUARTER("1/4", 1),
    HALF("1/2", 2),
    ONE_BAR("1 Bar", 3),
    TWO_BARS("2 Bars", 4),
    FOUR_BARS("4 Bars", 5)
}

@Serializable
enum class GridResolution(val label: String, val beats: Double) {
    WHOLE("1/1", 4.0),
    HALF("1/2", 2.0),
    QUARTER("1/4", 1.0),
    EIGHTH("1/8", 0.5),
    SIXTEENTH("1/16", 0.25),
    THIRTY_SECOND("1/32", 0.125),
    EIGHTH_TRIPLET("1/8T", 1.0 / 3.0),
    SIXTEENTH_TRIPLET("1/16T", 1.0 / 6.0)
}

@Serializable
enum class NoteRepeatRate(val label: String, val beats: Double) {
    OFF("OFF", 0.0),
    THIRTY_SECOND_T("1/32T", 1.0 / 12.0),
    THIRTY_SECOND("1/32", 0.125),
    SIXTEENTH_T("1/16T", 1.0 / 6.0),
    SIXTEENTH("1/16", 0.25),
    EIGHTH_T("1/8T", 1.0 / 3.0),
    EIGHTH("1/8", 0.5),
    QUARTER_T("1/4T", 2.0 / 3.0),
    QUARTER("1/4", 1.0)
}

@Serializable
enum class PerformanceProfile(val label: String, val index: Int, val maxVoices: Int) {
    LOW("Low (Battery)", 0, 8),
    BALANCED("Balanced", 1, 16),
    HIGH("High (Studio)", 2, 32)
}

data class MelodicPadCell(
    val row: Int,
    val col: Int,
    val midiNote: Int,
    val label: String,
    val isRoot: Boolean,
    val inScale: Boolean
)

object MusicalTheory {
    val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun noteName(midiNote: Int, withOctave: Boolean = true): String {
        val clamped = midiNote.coerceIn(0, 127)
        val pc = clamped % 12
        val octave = (clamped / 12) - 1
        return if (withOctave) "${NOTE_NAMES[pc]}$octave" else NOTE_NAMES[pc]
    }

    fun isNoteInScale(midiNote: Int, rootPitchClass: Int, scale: ScaleType): Boolean {
        val rel = ((midiNote % 12) - (rootPitchClass % 12) + 12) % 12
        return rel in scale.intervals
    }

    fun buildScaleNotes(rootPitchClass: Int, scale: ScaleType, baseOctave: Int, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val safeRoot = ((rootPitchClass % 12) + 12) % 12
        val safeOctave = baseOctave.coerceIn(0, 8)
        val intervals = scale.intervals.ifEmpty { listOf(0) }
        val baseMidi = ((safeOctave + 1) * 12 + safeRoot).coerceIn(0, 127)

        // Generate ascending scale degrees starting at baseMidi up to MIDI 127, bounded safely
        val raw = ArrayList<Int>(count)
        for (octaveOffset in 0..10) {
            for (iv in intervals) {
                val n = baseMidi + octaveOffset * 12 + iv
                if (n in 0..127) {
                    raw.add(n)
                    if (raw.size >= count) return raw
                }
            }
        }

        // If high octave truncated available upper notes, step down by octaves preserving scale intervals
        if (raw.isEmpty()) {
            val fallbackMidi = (safeRoot + 60).coerceIn(0, 127)
            raw.add(fallbackMidi)
        }
        val highest = raw.last()
        var stepDown = 1
        while (raw.size < count && stepDown <= 12) {
            val downOctaveBase = baseMidi - stepDown * 12
            if (downOctaveBase >= 0) {
                for (iv in intervals.asReversed()) {
                    val candidate = downOctaveBase + iv
                    if (candidate in 0..127 && candidate !in raw) {
                        raw.add(0, candidate)
                        if (raw.size >= count) return raw
                    }
                }
            } else {
                break
            }
            stepDown++
        }

        // Guarantee exact requested size without ever hanging even for tiny/edge counts
        while (raw.size < count) {
            raw.add(highest)
        }
        return raw
    }

    /**
     * Generates a 25-pad (5x5) or 7-column melodic performance pad matrix.
     * Row 0 is top, row 4 is bottom (lower pitches at bottom-left, higher pitches at top-right).
     */
    fun generate25PadGrid(
        rootPitchClass: Int,
        scale: ScaleType,
        octave: Int,
        layout: MelodicPadLayout
    ): List<MelodicPadCell> {
        val scaleNotes = buildScaleNotes(rootPitchClass, scale, octave, 40)
        val cells = ArrayList<MelodicPadCell>(25)
        for (row in 0 until 5) {
            val visualRowFromBottom = 4 - row
            for (col in 0 until 5) {
                val idx = when (layout) {
                    MelodicPadLayout.FOURTHS -> visualRowFromBottom * 3 + col
                    MelodicPadLayout.SEQUENTIAL -> visualRowFromBottom * 5 + col
                    MelodicPadLayout.SCALE_COLUMNS -> visualRowFromBottom * 3 + col
                }.coerceIn(0, scaleNotes.lastIndex)
                val midi = scaleNotes[idx]
                val isRoot = (midi % 12) == (rootPitchClass % 12)
                cells.add(
                    MelodicPadCell(
                        row = row,
                        col = col,
                        midiNote = midi,
                        label = noteName(midi, withOctave = true),
                        isRoot = isRoot,
                        inScale = true
                    )
                )
            }
        }
        return cells
    }

    fun generateScaleColumns(
        rootPitchClass: Int,
        scale: ScaleType,
        octave: Int,
        columns: Int = 7
    ): List<MelodicPadCell> {
        val notes = buildScaleNotes(rootPitchClass, scale, octave, columns * 2)
        val out = ArrayList<MelodicPadCell>(columns * 2)
        // Top row: upper octave, bottom row: base octave (matches Ableton Note vertical pad strips)
        for (row in 0 until 2) {
            for (col in 0 until columns) {
                val idx = if (row == 0) col + columns else col
                val midi = notes[idx.coerceIn(0, notes.lastIndex)]
                out.add(
                    MelodicPadCell(
                        row = row,
                        col = col,
                        midiNote = midi,
                        label = noteName(midi, withOctave = true),
                        isRoot = (midi % 12) == (rootPitchClass % 12),
                        inScale = true
                    )
                )
            }
        }
        return out
    }

    fun quantizeBeat(beat: Double, gridBeats: Double, strength: Float = 1.0f): Double {
        if (gridBeats <= 0.0) return beat
        val snapped = (beat / gridBeats).roundToInt() * gridBeats
        return beat + (snapped - beat) * strength.coerceIn(0f, 1f)
    }

    fun detectPhraseLengthBars(noteEndBeats: List<Double>, beatsPerBar: Int = 4): Int {
        if (noteEndBeats.isEmpty()) return 1
        val maxBeat = noteEndBeats.maxOrNull() ?: beatsPerBar.toDouble()
        val rawBars = kotlin.math.ceil(maxBeat / beatsPerBar.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        return when {
            rawBars <= 1 -> 1
            rawBars <= 2 -> 2
            rawBars <= 4 -> 4
            rawBars <= 8 -> 8
            else -> 16
        }
    }
}

@Serializable
enum class CaptureMode(val label: String) {
    FREE_LENGTH("Free Length"),
    FIXED_LENGTH("Fixed Bars")
}
