package com.example.core.project

import com.example.core.common.MusicalTheory
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Pure cumulative drag math for the Piano Roll so tiny per-frame touch deltas never drop to zero.
 */
enum class NoteDragMode {
    MOVE,
    RESIZE_RIGHT,
    RESIZE_LEFT
}

enum class PianoRollTool(val label: String) {
    DRAW("Draw"),
    SELECT("Select"),
    ERASE("Erase")
}

object PianoRollDragMath {
    /**
     * Right ~50% of a note's visual width triggers RESIZE_RIGHT; left ~50% triggers MOVE.
     */
    fun determineNoteDragMode(touchX: Float, noteStartPx: Float, noteEndPx: Float): NoteDragMode {
        val visualWidth = (noteEndPx - noteStartPx).coerceAtLeast(12f)
        val midpoint = noteStartPx + visualWidth * 0.50f
        return if (touchX >= midpoint) NoteDragMode.RESIZE_RIGHT else NoteDragMode.MOVE
    }

    fun applyCumulativeDrag(
        initialNote: MidiNoteModel,
        cumulativeDeltaPx: Float,
        cumulativeDeltaPy: Float,
        beatPx: Float,
        rowPx: Float,
        mode: NoteDragMode,
        snapGridBeats: Float,
        totalBeats: Float,
        minMidi: Int,
        maxMidi: Int
    ): MidiNoteModel {
        val safeBeatPx = beatPx.coerceAtLeast(1f)
        val safeRowPx = rowPx.coerceAtLeast(1f)
        val rawDeltaBeats = cumulativeDeltaPx / safeBeatPx
        val grid = snapGridBeats.coerceAtLeast(0.0625f)
        val snappedDeltaBeats = if (snapGridBeats > 0f) {
            (rawDeltaBeats / grid).roundToInt() * grid
        } else {
            rawDeltaBeats
        }
        val minDur = 0.125f

        return when (mode) {
            NoteDragMode.MOVE -> {
                val deltaPitch = (-cumulativeDeltaPy / safeRowPx).roundToInt()
                val nextMidi = (initialNote.note + deltaPitch).coerceIn(minMidi, maxMidi)
                val maxStart = (totalBeats - initialNote.durationBeats).coerceAtLeast(0f)
                val nextStart = (initialNote.startBeat + snappedDeltaBeats).coerceIn(0f, maxStart)
                initialNote.copy(note = nextMidi, startBeat = nextStart)
            }
            NoteDragMode.RESIZE_RIGHT -> {
                val maxDur = (totalBeats - initialNote.startBeat).coerceAtLeast(minDur)
                val nextDur = (initialNote.durationBeats + snappedDeltaBeats).coerceIn(minDur, maxDur)
                initialNote.copy(durationBeats = nextDur)
            }
            NoteDragMode.RESIZE_LEFT -> {
                val rightEdge = (initialNote.startBeat + initialNote.durationBeats).coerceAtMost(totalBeats)
                val maxLeft = (rightEdge - minDur).coerceAtLeast(0f)
                val nextStart = (initialNote.startBeat + snappedDeltaBeats).coerceIn(0f, maxLeft)
                val nextDur = (rightEdge - nextStart).coerceAtLeast(minDur)
                initialNote.copy(startBeat = nextStart, durationBeats = nextDur)
            }
        }
    }
}

object EffectChainTransforms {
    const val MAX_EFFECT_SLOTS = 8

    fun addEffect(list: List<EffectModel>, fx: EffectModel): List<EffectModel> {
        if (list.size >= MAX_EFFECT_SLOTS) return list
        return list + fx
    }

    fun updateEffectAt(list: List<EffectModel>, slot: Int, fx: EffectModel): List<EffectModel> {
        if (slot !in 0 until MAX_EFFECT_SLOTS) return list
        val mutable = list.toMutableList()
        while (mutable.size <= slot) {
            if (mutable.size == slot) {
                mutable.add(fx)
                return mutable.take(MAX_EFFECT_SLOTS)
            }
            mutable.add(EffectModel(type = EffectType.NONE))
        }
        mutable[slot] = fx
        return mutable.take(MAX_EFFECT_SLOTS)
    }

    fun removeEffectAt(list: List<EffectModel>, slot: Int): List<EffectModel> {
        if (slot !in list.indices) return list
        return list.filterIndexed { i, _ -> i != slot }
    }

    fun moveEffect(list: List<EffectModel>, from: Int, to: Int): List<EffectModel> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val mutable = list.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return mutable
    }

    fun swapEffects(list: List<EffectModel>, a: Int, b: Int): List<EffectModel> {
        if (a !in list.indices || b !in list.indices || a == b) return list
        val mutable = list.toMutableList()
        val tmp = mutable[a]
        mutable[a] = mutable[b]
        mutable[b] = tmp
        return mutable
    }
}

/**
 * Pure immutable operations on NoteSet, Clips, Automation, and Standard MIDI File export.
 */
object StudioProjectTransforms {

    fun quantizeClipNotes(
        clip: ClipModel,
        noteIds: Set<String>,
        gridBeats: Double,
        strength: Float = 1.0f,
        timeSigNum: Int = 4
    ): ClipModel {
        val totalBeats = clip.totalBeats(timeSigNum)
        val updated = clip.notes.map { n ->
            if (noteIds.isEmpty() || n.id in noteIds) {
                val q = MusicalTheory.quantizeBeat(n.startBeat.toDouble(), gridBeats, strength).toFloat()
                val safeStart = q.coerceIn(0f, (totalBeats - 0.05f).coerceAtLeast(0f))
                val maxDur = (totalBeats - safeStart).coerceAtLeast(0.0625f)
                n.copy(startBeat = safeStart, durationBeats = n.durationBeats.coerceAtMost(maxDur))
            } else n
        }.sortedWith(compareBy({ it.startBeat }, { it.note }))
        return clip.copy(notes = updated)
    }

    fun nudgeClipNotes(
        clip: ClipModel,
        noteIds: Set<String>,
        deltaBeats: Float,
        timeSigNum: Int = 4
    ): ClipModel {
        val totalBeats = clip.totalBeats(timeSigNum)
        val updated = clip.notes.map { n ->
            if (noteIds.isEmpty() || n.id in noteIds) {
                val maxStart = (totalBeats - n.durationBeats.coerceAtMost(totalBeats)).coerceAtLeast(0f)
                val nb = (n.startBeat + deltaBeats).coerceIn(0f, maxStart)
                n.copy(startBeat = nb)
            } else n
        }.sortedWith(compareBy({ it.startBeat }, { it.note }))
        return clip.copy(notes = updated)
    }

    fun duplicateClipNotes(
        clip: ClipModel,
        noteIds: Set<String>,
        timeSigNum: Int = 4
    ): Pair<ClipModel, Set<String>> {
        val targets = if (noteIds.isEmpty()) clip.notes else clip.notes.filter { it.id in noteIds }
        if (targets.isEmpty()) return clip to emptySet()
        val totalBeats = clip.totalBeats(timeSigNum)
        val minStart = targets.minOf { it.startBeat }
        val maxEnd = targets.maxOf { it.startBeat + it.durationBeats }
        val span = (maxEnd - minStart).coerceAtLeast(0.25f)
        val newIds = mutableSetOf<String>()
        val clones = targets.map { n ->
            val id = UUID.randomUUID().toString()
            newIds.add(id)
            val ns = (n.startBeat + span).coerceIn(0f, (totalBeats - 0.05f).coerceAtLeast(0f))
            val dur = n.durationBeats.coerceAtMost((totalBeats - ns).coerceAtLeast(0.0625f))
            n.copy(id = id, startBeat = ns, durationBeats = dur)
        }
        val merged = (clip.notes + clones).sortedWith(compareBy({ it.startBeat }, { it.note }))
        return clip.copy(notes = merged) to newIds
    }

    fun pasteClipNotes(
        clip: ClipModel,
        clipboard: List<MidiNoteModel>,
        targetStartBeat: Float,
        timeSigNum: Int = 4
    ): Pair<ClipModel, Set<String>> {
        if (clipboard.isEmpty()) return clip to emptySet()
        val totalBeats = clip.totalBeats(timeSigNum)
        val anchor = clipboard.minOf { it.startBeat }
        val newIds = mutableSetOf<String>()
        val clones = clipboard.map { n ->
            val id = UUID.randomUUID().toString()
            newIds.add(id)
            val shifted = (targetStartBeat + (n.startBeat - anchor)).coerceIn(0f, (totalBeats - 0.0625f).coerceAtLeast(0f))
            val dur = n.durationBeats.coerceAtMost((totalBeats - shifted).coerceAtLeast(0.0625f))
            n.copy(id = id, startBeat = shifted, durationBeats = dur)
        }
        val merged = (clip.notes + clones).sortedWith(compareBy({ it.startBeat }, { it.note }))
        return clip.copy(notes = merged) to newIds
    }

    fun duplicateClipLoop(clip: ClipModel, timeSigNum: Int = 4): ClipModel {
        val tsNum = timeSigNum.coerceIn(1, 12)
        val loopStart = clip.effectiveLoopStart(tsNum)
        val loopEnd = clip.effectiveLoopEnd(tsNum)
        val loopSpan = (loopEnd - loopStart).coerceAtLeast(1f)
        val neededEnd = loopEnd + loopSpan
        val neededBars = ceil(neededEnd / tsNum).toInt().coerceIn(clip.lengthBars, 16)
        val maxBeats = (neededBars * tsNum).toFloat()

        val sourceNotes = clip.notes.filter { it.startBeat >= loopStart - 0.001f && it.startBeat < loopEnd - 0.001f }
        val duplicated = sourceNotes.mapNotNull { n ->
            val shifted = n.startBeat + loopSpan
            if (shifted >= maxBeats) null
            else n.copy(
                id = UUID.randomUUID().toString(),
                startBeat = shifted,
                durationBeats = n.durationBeats.coerceAtMost((maxBeats - shifted).coerceAtLeast(0.0625f))
            )
        }
        val newLoopEnd = neededEnd.coerceAtMost(maxBeats)
        return clip.copy(
            lengthBars = neededBars,
            loopStartBeats = loopStart,
            loopEndBeats = newLoopEnd,
            notes = (clip.notes + duplicated).sortedWith(compareBy({ it.startBeat }, { it.note }))
        )
    }

    fun cropClipToLoop(clip: ClipModel, timeSigNum: Int = 4): ClipModel {
        val tsNum = timeSigNum.coerceIn(1, 12)
        val loopStart = clip.effectiveLoopStart(tsNum)
        val loopEnd = clip.effectiveLoopEnd(tsNum)
        val span = (loopEnd - loopStart).coerceAtLeast(1f)
        val newBars = ceil(span / tsNum).toInt().coerceIn(1, 16)
        val newTotal = (newBars * tsNum).toFloat()

        val croppedNotes = clip.notes
            .filter { it.startBeat >= loopStart - 0.001f && it.startBeat < loopEnd - 0.001f }
            .map { n ->
                val rel = (n.startBeat - loopStart).coerceIn(0f, (newTotal - 0.0625f).coerceAtLeast(0f))
                val dur = n.durationBeats.coerceAtMost((newTotal - rel).coerceAtLeast(0.0625f))
                n.copy(startBeat = rel, durationBeats = dur)
            }
            .sortedWith(compareBy({ it.startBeat }, { it.note }))

        return clip.copy(
            lengthBars = newBars,
            loopStartBeats = 0f,
            loopEndBeats = span.coerceAtMost(newTotal),
            notes = croppedNotes
        )
    }

    fun clearClipLoop(clip: ClipModel, timeSigNum: Int = 4): ClipModel {
        val tsNum = timeSigNum.coerceIn(1, 12)
        val loopStart = clip.effectiveLoopStart(tsNum)
        val loopEnd = clip.effectiveLoopEnd(tsNum)
        val kept = clip.notes.filterNot { it.startBeat >= loopStart - 0.001f && it.startBeat < loopEnd - 0.001f }
        return clip.copy(notes = kept)
    }

    fun encodeStandardMidiFile(set: NoteSet, sceneIndex: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val ppqn = 480
        // Header chunk MThd
        out.write("MThd".toByteArray())
        writeInt32BE(out, 6)
        writeInt16BE(out, 1) // Format 1
        val activeTracks = set.tracks.filter { it.clips[sceneIndex] != null }
        writeInt16BE(out, activeTracks.size + 1)
        writeInt16BE(out, ppqn)

        // Track 0: Tempo
        val tempoTrack = ByteArrayOutputStream()
        val usPerQuarter = (60_000_000.0 / set.bpm.coerceIn(30f, 300f)).toInt()
        tempoTrack.write(0x00) // delta 0
        tempoTrack.write(byteArrayOf(0xFF.toByte(), 0x51, 0x03, ((usPerQuarter ushr 16) and 0xFF).toByte(), ((usPerQuarter ushr 8) and 0xFF).toByte(), (usPerQuarter and 0xFF).toByte()))
        tempoTrack.write(byteArrayOf(0x00, 0xFF.toByte(), 0x2F, 0x00))
        writeTrackChunk(out, tempoTrack.toByteArray())

        // Musical tracks
        activeTracks.forEachIndexed { idx, track ->
            val clip = track.clips[sceneIndex]!!
            val ch = if (track.type == TrackType.DRUM_RACK) 9 else (idx % 15).let { if (it >= 9) it + 1 else it }
            data class RawEvt(val tick: Int, val status: Int, val d1: Int, val d2: Int)
            val evts = mutableListOf<RawEvt>()
            for (n in clip.notes) {
                val startTick = (n.startBeat * ppqn).toInt().coerceAtLeast(0)
                val endTick = ((n.startBeat + n.durationBeats) * ppqn).toInt().coerceAtLeast(startTick + 10)
                val vel = (n.velocity * 127f).toInt().coerceIn(1, 127)
                val pitch = n.note.coerceIn(0, 127)
                evts.add(RawEvt(startTick, 0x90 or ch, pitch, vel))
                evts.add(RawEvt(endTick, 0x80 or ch, pitch, 0))
            }
            evts.sortBy { it.tick }
            val trBytes = ByteArrayOutputStream()
            var prevTick = 0
            for (e in evts) {
                writeVarLen(trBytes, e.tick - prevTick)
                trBytes.write(e.status)
                trBytes.write(e.d1)
                trBytes.write(e.d2)
                prevTick = e.tick
            }
            trBytes.write(byteArrayOf(0x00, 0xFF.toByte(), 0x2F, 0x00))
            writeTrackChunk(out, trBytes.toByteArray())
        }

        return out.toByteArray()
    }

    private fun writeTrackChunk(out: ByteArrayOutputStream, data: ByteArray) {
        out.write("MTrk".toByteArray())
        writeInt32BE(out, data.size)
        out.write(data)
    }

    private fun writeInt32BE(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeInt16BE(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeVarLen(out: ByteArrayOutputStream, value: Int) {
        var v = value.coerceAtLeast(0)
        var buffer = v and 0x7F
        while ((v ushr 7).also { v = it } > 0) {
            buffer = buffer shl 8
            buffer = buffer or ((v and 0x7F) or 0x80)
        }
        while (true) {
            out.write(buffer and 0xFF)
            if ((buffer and 0x80) != 0) buffer = buffer ushr 8 else break
        }
    }
}
