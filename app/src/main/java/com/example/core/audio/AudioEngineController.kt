package com.example.core.audio

import android.content.Context
import android.net.Uri
import com.example.core.common.CaptureMode
import com.example.core.common.MusicalTheory
import com.example.core.common.SettingsState
import com.example.core.database.SampleEntity
import com.example.core.project.ClipModel
import com.example.core.project.DrumPadModel
import com.example.core.project.EffectChainTransforms
import com.example.core.project.EffectModel
import com.example.core.project.EffectType
import com.example.core.project.MidiNoteModel
import com.example.core.project.NoteSet
import com.example.core.project.SamplerParamsModel
import com.example.core.project.SynthParamsModel
import com.example.core.project.TrackModel
import com.example.core.project.TrackType
import com.example.core.samples.DecodedSample
import com.example.core.samples.SamplePcmTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.floor

data class TransportTelemetryState(
    val isPlaying: Boolean = false,
    val isCountingIn: Boolean = false,
    val transportBeat: Double = 0.0,
    val timeSigNum: Int = 4,
    val bar: Int = 1,
    val beat: Int = 1,
    val sixteenth: Int = 1,
    val masterPeakL: Float = 0f,
    val masterPeakR: Float = 0f,
    val masterClipping: Boolean = false,
    val trackPeaks: List<Float> = List(8) { 0f },
    val activeClips: List<Int> = List(8) { -1 },
    val queuedClips: List<Int> = List(8) { -1 },
    val trackClipBeats: List<Double> = List(8) { 0.0 },
    val capturedNoteCount: Int = 0,
    val captureActive: Boolean = false,
    val xrunCount: Int = 0,
    val sampleRate: Int = 48000,
    val bufferFrames: Int = 256,
    val isAAudio: Boolean = true
)

data class CapturedPerformanceEvent(
    val trackIndex: Int,
    val note: Int,
    val beatOffset: Double,
    val durationBeats: Double,
    val velocity: Float,
    val transportBeatAtOn: Double = -1.0
)

/**
 * Pure Kotlin monotonic capture buffer that backs Capture whenever the native audio
 * thread is unavailable (e.g. JVM unit tests / headless mode) and mirrors capture
 * availability immediately on UI events.
 */
class KotlinCaptureBuffer(
    private val timeNanos: () -> Long = { System.nanoTime() }
) {
    private data class OpenEntry(
        val trackIndex: Int,
        val note: Int,
        val startBeat: Double,
        val startNanos: Long,
        val velocity: Float,
        val transportBeatAtOn: Double
    )

    private val lock = Any()
    private val finished = mutableListOf<CapturedPerformanceEvent>()
    private val openNotes = mutableListOf<OpenEntry>()
    private var phraseStartBeat = 0.0
    private var lastActivityNanos = 0L
    private var active = false
    private var virtualBeatCursor = 0.0
    private var lastClockNanos = 0L

    var bpm: Float = 120f
    var timeoutSeconds: Int = 12
    var isTransportPlaying: Boolean = false
    var captureArmed: Boolean = true

    private fun currentClockBeat(nowNanos: Long): Double {
        if (lastClockNanos == 0L) {
            lastClockNanos = nowNanos
            return virtualBeatCursor
        }
        val elapsedSec = ((nowNanos - lastClockNanos).coerceAtLeast(0L)) / 1_000_000_000.0
        virtualBeatCursor += elapsedSec * (bpm.coerceIn(30f, 300f) / 60.0)
        lastClockNanos = nowNanos
        return virtualBeatCursor
    }

    fun advanceVirtualBeats(deltaBeats: Double) {
        synchronized(lock) {
            if (deltaBeats > 0.0) {
                virtualBeatCursor += deltaBeats
                lastClockNanos = timeNanos()
            }
        }
    }

    fun noteOn(trackIndex: Int, note: Int, velocity: Float, transportBeat: Double = -1.0) {
        synchronized(lock) {
            if (!captureArmed) return
            val clampedNote = note.coerceIn(0, 127)
            val now = timeNanos()
            val beatNow = currentClockBeat(now)
            if (active && !isTransportPlaying && timeoutSeconds > 0 && openNotes.isEmpty() && lastActivityNanos > 0L) {
                val idleSec = (now - lastActivityNanos) / 1_000_000_000.0
                if (idleSec >= timeoutSeconds) {
                    finished.clear()
                    active = false
                }
            }
            if (!active || (finished.isEmpty() && openNotes.isEmpty())) {
                active = true
                phraseStartBeat = beatNow
                finished.clear()
                openNotes.clear()
            }
            // Close any duplicate open note with matching track+pitch before opening a new one
            val dupIdx = openNotes.indexOfLast { it.trackIndex == trackIndex && it.note == clampedNote }
            if (dupIdx >= 0) {
                val prev = openNotes.removeAt(dupIdx)
                val prevDur = (beatNow - prev.startBeat).coerceAtLeast(0.0625)
                finished.add(
                    CapturedPerformanceEvent(
                        trackIndex = prev.trackIndex,
                        note = prev.note,
                        beatOffset = (prev.startBeat - phraseStartBeat).coerceAtLeast(0.0),
                        durationBeats = prevDur,
                        velocity = prev.velocity,
                        transportBeatAtOn = prev.transportBeatAtOn
                    )
                )
            }
            lastActivityNanos = now
            openNotes.add(
                OpenEntry(
                    trackIndex = trackIndex,
                    note = clampedNote,
                    startBeat = beatNow,
                    startNanos = now,
                    velocity = velocity.coerceIn(0.05f, 1f),
                    transportBeatAtOn = if (isTransportPlaying) transportBeat else -1.0
                )
            )
            // Keep bounded window (256 notes max)
            while (finished.size + openNotes.size > 256 && finished.isNotEmpty()) {
                finished.removeAt(0)
            }
        }
    }

    fun noteOff(trackIndex: Int, note: Int) {
        synchronized(lock) {
            val now = timeNanos()
            val beatNow = currentClockBeat(now)
            val idx = openNotes.indexOfLast { it.trackIndex == trackIndex && it.note == note.coerceIn(0, 127) }
            if (idx >= 0) {
                lastActivityNanos = now
                val entry = openNotes.removeAt(idx)
                val dur = (beatNow - entry.startBeat).coerceAtLeast(0.0625)
                finished.add(
                    CapturedPerformanceEvent(
                        trackIndex = entry.trackIndex,
                        note = entry.note,
                        beatOffset = (entry.startBeat - phraseStartBeat).coerceAtLeast(0.0),
                        durationBeats = dur,
                        velocity = entry.velocity,
                        transportBeatAtOn = entry.transportBeatAtOn
                    )
                )
            }
        }
    }

    fun finalizeOpenNotes() {
        synchronized(lock) {
            val now = timeNanos()
            val beatNow = currentClockBeat(now)
            for (entry in openNotes) {
                val dur = (beatNow - entry.startBeat).coerceAtLeast(0.0625)
                finished.add(
                    CapturedPerformanceEvent(
                        trackIndex = entry.trackIndex,
                        note = entry.note,
                        beatOffset = (entry.startBeat - phraseStartBeat).coerceAtLeast(0.0),
                        durationBeats = dur,
                        velocity = entry.velocity,
                        transportBeatAtOn = entry.transportBeatAtOn
                    )
                )
            }
            openNotes.clear()
        }
    }

    fun peekCountForTrack(trackIndex: Int): Int = synchronized(lock) {
        finished.count { it.trackIndex == trackIndex } + openNotes.count { it.trackIndex == trackIndex }
    }

    fun totalCount(): Int = synchronized(lock) { finished.size + openNotes.size }

    fun consumeAll(): List<CapturedPerformanceEvent> {
        synchronized(lock) {
            finalizeOpenNotes()
            val out = finished.sortedWith(compareBy({ it.beatOffset }, { it.note }))
            clearInternalLocked()
            return out
        }
    }

    fun clear() {
        synchronized(lock) {
            clearInternalLocked()
        }
    }

    private fun clearInternalLocked() {
        finished.clear()
        openNotes.clear()
        active = false
        phraseStartBeat = 0.0
        lastActivityNanos = 0L
        virtualBeatCursor = 0.0
        lastClockNanos = 0L
    }
}

object CaptureProcessor {
    /**
     * Converts raw monotonic capture events into a musical ClipModel for [trackIndex].
     * Preserves relative performance timing without collapsing notes onto beat 0 or altering pitches.
     */
    fun buildCapturedClip(
        events: List<CapturedPerformanceEvent>,
        trackIndex: Int,
        timeSigNum: Int = 4,
        captureMode: CaptureMode = CaptureMode.FREE_LENGTH,
        fixedBars: Int? = null,
        quantizeGridBeats: Double = 0.0,
        quantizeStrength: Float = 0.0f,
        isDrumTrack: Boolean = false
    ): ClipModel? {
        val tsNum = timeSigNum.coerceIn(1, 12)
        val filtered = events.filter { it.trackIndex == trackIndex }
        if (filtered.isEmpty()) return null

        val sorted = filtered.sortedWith(compareBy({ it.beatOffset }, { it.note }))
        val allPlaying = sorted.all { it.transportBeatAtOn >= 0.0 }

        // Anchor beat positions accurately:
        // - If played while transport was rolling, preserve bar/beat alignment within the running cycle
        // - If played while stopped, preserve exact elapsed spacing relative to the first played note (beat 0.0)
        val baseTransportBarBeat = if (allPlaying) {
            val firstTp = sorted.first().transportBeatAtOn
            floor(firstTp / tsNum.toDouble()) * tsNum.toDouble()
        } else 0.0

        val rawAnchored = sorted.map { ev ->
            val rawPos = if (allPlaying) {
                (ev.transportBeatAtOn - baseTransportBarBeat).coerceAtLeast(0.0)
            } else {
                ev.beatOffset.coerceAtLeast(0.0)
            }
            val snappedPos = if (quantizeGridBeats > 0.0 && quantizeStrength > 0.001f) {
                MusicalTheory.quantizeBeat(rawPos, quantizeGridBeats, quantizeStrength).coerceAtLeast(0.0)
            } else {
                rawPos
            }
            val safeDur = if (isDrumTrack) {
                ev.durationBeats.toFloat().coerceIn(0.125f, 1.0f)
            } else {
                ev.durationBeats.toFloat().coerceIn(0.08f, 16.0f)
            }
            MidiNoteModel(
                id = UUID.randomUUID().toString(),
                note = ev.note.coerceIn(0, 127),
                startBeat = snappedPos.toFloat(),
                durationBeats = safeDur,
                velocity = ev.velocity.coerceIn(0.05f, 1f)
            )
        }

        val resolvedBars = when (captureMode) {
            CaptureMode.FIXED_LENGTH -> (fixedBars ?: 4).coerceIn(1, 16)
            CaptureMode.FREE_LENGTH -> fixedBars?.coerceIn(1, 16)
                ?: MusicalTheory.detectPhraseLengthBars(
                    rawAnchored.map { (it.startBeat + it.durationBeats).toDouble() },
                    beatsPerBar = tsNum
                )
        }

        val totalBeats = (resolvedBars * tsNum).toFloat()
        val finalNotes = rawAnchored.map { n ->
            val wrappedStart = ((n.startBeat % totalBeats) + totalBeats) % totalBeats
            val clampedDur = n.durationBeats.coerceAtMost((totalBeats - wrappedStart).coerceAtLeast(0.08f))
            n.copy(startBeat = wrappedStart, durationBeats = clampedDur)
        }.sortedWith(compareBy({ it.startBeat }, { it.note }))

        return ClipModel(
            name = "Captured",
            lengthBars = resolvedBars,
            loopEnabled = true,
            loopStartBeats = 0f,
            loopEndBeats = totalBeats,
            notes = finalNotes
        )
    }

    /**
     * Merges captured performance notes into an existing clip (Live Overdub) without overwriting
     * existing notes or losing timing alignment.
     */
    fun mergeOverdubIntoClip(
        existing: ClipModel,
        events: List<CapturedPerformanceEvent>,
        trackIndex: Int,
        timeSigNum: Int = 4,
        quantizeGridBeats: Double = 0.0,
        quantizeStrength: Float = 0.0f,
        isDrumTrack: Boolean = false
    ): ClipModel? {
        val tsNum = timeSigNum.coerceIn(1, 12)
        val filtered = events.filter { it.trackIndex == trackIndex }
        if (filtered.isEmpty()) return null

        val loopStart = existing.effectiveLoopStart(tsNum)
        val loopEnd = existing.effectiveLoopEnd(tsNum)
        val loopSpan = (loopEnd - loopStart).coerceAtLeast(tsNum.toFloat())
        val sorted = filtered.sortedWith(compareBy({ it.beatOffset }, { it.note }))
        val allPlaying = sorted.all { it.transportBeatAtOn >= 0.0 }

        val newNotes = sorted.map { ev ->
            val rawPos = if (allPlaying) {
                val rel = ((ev.transportBeatAtOn - loopStart) % loopSpan + loopSpan) % loopSpan
                loopStart + rel
            } else {
                val rel = (ev.beatOffset % loopSpan)
                loopStart + rel
            }
            val snapped = if (quantizeGridBeats > 0.0 && quantizeStrength > 0.001f) {
                MusicalTheory.quantizeBeat(rawPos, quantizeGridBeats, quantizeStrength)
            } else rawPos
            val finalStart = snapped.toFloat().coerceIn(0f, (existing.totalBeats(tsNum) - 0.0625f).coerceAtLeast(0f))
            val maxDur = (existing.totalBeats(tsNum) - finalStart).coerceAtLeast(0.08f)
            val dur = if (isDrumTrack) ev.durationBeats.toFloat().coerceIn(0.125f, 1f) else ev.durationBeats.toFloat().coerceIn(0.08f, maxDur)
            MidiNoteModel(
                id = UUID.randomUUID().toString(),
                note = ev.note.coerceIn(0, 127),
                startBeat = finalStart,
                durationBeats = dur,
                velocity = ev.velocity.coerceIn(0.05f, 1f)
            )
        }

        val merged = (existing.notes + newNotes)
            .sortedWith(compareBy({ it.startBeat }, { it.note }))
        return existing.copy(notes = merged)
    }
}

class AudioEngineController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var telemetryJob: Job? = null
    private var resampleJob: Job? = null

    private val _telemetry = MutableStateFlow(TransportTelemetryState())
    val telemetry: StateFlow<TransportTelemetryState> = _telemetry.asStateFlow()

    private val _isStarted = MutableStateFlow(false)
    val isStarted: StateFlow<Boolean> = _isStarted.asStateFlow()

    private val _isResampling = MutableStateFlow(false)
    val isResampling: StateFlow<Boolean> = _isResampling.asStateFlow()

    private val _exportProgress = MutableStateFlow<Float?>(null)
    val exportProgress: StateFlow<Float?> = _exportProgress.asStateFlow()

    private val _capturedNoteCount = MutableStateFlow(0)
    val capturedNoteCount: StateFlow<Int> = _capturedNoteCount.asStateFlow()

    private val resampledFloats = mutableListOf<Float>()
    val kotlinCaptureBuffer = KotlinCaptureBuffer()
    private var currentTimeSigNum: Int = 4

    fun startEngine(settings: SettingsState) {
        kotlinCaptureBuffer.timeoutSeconds = settings.captureTimeoutSec
        if (!NativeAudioEngine.isNativeLoaded) {
            _isStarted.value = false
            return
        }
        val ok = NativeAudioEngine.nativeStart(settings.sampleRate, settings.bufferFrames)
        _isStarted.value = ok
        applySettings(settings)
        startTelemetryPolling()
    }

    fun stopEngine() {
        telemetryJob?.cancel()
        resampleJob?.cancel()
        _isResampling.value = false
        if (!NativeAudioEngine.isNativeLoaded) {
            _isStarted.value = false
            return
        }
        NativeAudioEngine.nativeStop()
        _isStarted.value = false
    }

    fun applySettings(settings: SettingsState) {
        kotlinCaptureBuffer.timeoutSeconds = settings.captureTimeoutSec
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(
            NativeAudioEngine.CMD_SET_PROFILE, 0, settings.performanceProfile.index, 0, 0f, 0f
        )
        NativeAudioEngine.nativePostCommand(
            NativeAudioEngine.CMD_SET_METRONOME, 0, if (settings.metronomeEnabled) 1 else 0, 0, 0f, 0f
        )
        NativeAudioEngine.nativePostCommand(
            NativeAudioEngine.CMD_SET_COUNTIN, 0, settings.countInBars, 0, 0f, 0f
        )
        NativeAudioEngine.nativePostCommand(
            NativeAudioEngine.CMD_SET_QUANTIZATION, 0, settings.defaultQuantization.index, 0, 0f, 0f
        )
        NativeAudioEngine.nativePostCommand(
            NativeAudioEngine.CMD_SET_CAPTURE_TIMEOUT, 0, 0, 0, settings.captureTimeoutSec.toFloat(), 0f
        )
    }

    fun loadSamplesIntoEngine(samples: List<Pair<SampleEntity, FloatArray>>) {
        if (!NativeAudioEngine.isNativeLoaded) return
        for ((entity, pcm) in samples) {
            if (pcm.isNotEmpty()) {
                NativeAudioEngine.nativeLoadSampleBuffer(
                    entity.slotIndex, entity.sampleRate, entity.channels, pcm
                )
            }
        }
    }

    fun loadSingleSample(entity: SampleEntity, pcm: FloatArray) {
        if (!NativeAudioEngine.isNativeLoaded) return
        if (pcm.isNotEmpty()) {
            NativeAudioEngine.nativeLoadSampleBuffer(
                entity.slotIndex, entity.sampleRate, entity.channels, pcm
            )
        }
    }

    fun syncSet(set: NoteSet) {
        currentTimeSigNum = set.timeSigNum.coerceIn(1, 12)
        kotlinCaptureBuffer.bpm = set.bpm
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_BPM, 0, 0, 0, set.bpm, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_SWING, 0, 0, 0, set.swing, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_TIMESIG, 0, currentTimeSigNum, set.timeSigDen, 0f, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_QUANTIZATION, 0, set.globalQuantization.index, 0, 0f, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_MASTER_VOLUME, 0, 0, 0, set.masterVolume, 0f)

        for (slot in 0 until 8) {
            val fx = set.masterEffects.getOrNull(slot) ?: EffectModel()
            sendEffect(scope = 2, trackOrPad = 0, slot = slot, fx = fx)
        }

        for (ti in 0 until 8) {
            val tr = set.tracks.getOrNull(ti)
            if (tr == null) {
                NativeAudioEngine.nativeSetTrackEnabled(ti, false, 0)
                continue
            }
            syncTrack(ti, tr, currentTimeSigNum)
        }
    }

    fun syncTrack(ti: Int, tr: TrackModel, timeSigNum: Int = currentTimeSigNum) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativeSetTrackEnabled(ti, true, tr.type.engineIndex)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_TRACK_VOLUME, ti, 0, 0, tr.volume, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_TRACK_PAN, ti, 0, 0, tr.pan, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_TRACK_MUTE, ti, if (tr.muted) 1 else 0, 0, 0f, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_TRACK_SOLO, ti, if (tr.soloed) 1 else 0, 0, 0f, 0f)

        when (tr.type) {
            TrackType.SUBTRACTIVE_SYNTH -> syncSynthParams(ti, tr.synthParams)
            TrackType.MELODIC_SAMPLER -> syncSamplerParams(ti, tr.samplerParams)
            TrackType.DRUM_RACK -> {
                tr.drumPads.take(16).forEach { pad ->
                    syncDrumPad(ti, pad)
                }
            }
        }

        for (slot in 0 until 8) {
            val fx = tr.effects.getOrNull(slot) ?: EffectModel()
            sendEffect(scope = 0, trackOrPad = ti, slot = slot, fx = fx)
        }

        for (scene in 0 until 8) {
            syncClip(ti, scene, tr.clips[scene], tr.type == TrackType.DRUM_RACK, timeSigNum)
        }
    }

    fun syncSynthParams(ti: Int, p: SynthParamsModel) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativeSetSynthParams(
            ti,
            intArrayOf(p.osc1Wave, p.osc2Wave, p.filterType, p.lfoTarget),
            floatArrayOf(
                p.oscMix, p.osc2Detune, p.subLevel, p.noiseLevel, p.fold, p.warp,
                p.cutoff, p.resonance, p.filterEnvAmt, p.filterDecay,
                p.attack, p.decay, p.sustain, p.release, p.lfoRate, p.lfoDepth, p.unison, p.glide
            )
        )
    }

    fun syncSamplerParams(ti: Int, p: SamplerParamsModel) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativeSetSamplerParams(
            ti, p.sampleSlot, p.rootNote, p.loopEnabled, p.reverse,
            floatArrayOf(
                p.startNorm, p.endNorm, p.loopStartNorm, p.loopEndNorm,
                p.pitchSemitones, p.playbackRate, p.gain, p.cutoff, p.resonance,
                p.attack, p.decay, p.sustain, p.release
            )
        )
    }

    fun syncDrumPad(ti: Int, pad: DrumPadModel) {
        if (!NativeAudioEngine.isNativeLoaded) return
        val pi = pad.index
        if (pi !in 0..15) return
        NativeAudioEngine.nativeSetDrumPadParams(
            ti, pi, pad.sampleSlot, pad.chokeGroup, pad.reverse, pad.muted, pad.soloed,
            floatArrayOf(
                pad.volume, pad.pan, pad.pitchSemitones, pad.decay,
                pad.startNorm, pad.endNorm, pad.sendAmount, pad.toneFilter
            )
        )
        val padFx = pad.effectivePadEffects()
        for (fxSlot in 0 until EffectChainTransforms.MAX_EFFECT_SLOTS) {
            sendDrumPadEffect(
                trackIdx = ti,
                padIdx = pi,
                fxSlot = fxSlot,
                fx = padFx.getOrNull(fxSlot) ?: EffectModel(type = EffectType.NONE)
            )
        }
    }

    fun syncClip(
        trackIdx: Int,
        sceneIdx: Int,
        clip: ClipModel?,
        isDrum: Boolean,
        timeSigNum: Int = currentTimeSigNum
    ) {
        if (!NativeAudioEngine.isNativeLoaded) return
        if (clip == null) {
            NativeAudioEngine.nativeSetClipData(
                trackIdx, sceneIdx, false, timeSigNum.toDouble(), 0.0, timeSigNum.toDouble(), true,
                0, IntArray(0), FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0),
                0, IntArray(0), IntArray(0), FloatArray(0), FloatArray(0)
            )
            return
        }
        val notes = clip.notes.take(256)
        val n = notes.size
        val nums = IntArray(n) { notes[it].note }
        val starts = FloatArray(n) { notes[it].startBeat }
        val durs = FloatArray(n) { notes[it].durationBeats }
        val vels = FloatArray(n) { notes[it].velocity }
        val probs = FloatArray(n) { notes[it].probability }
        val offs = FloatArray(n) { notes[it].timingOffset }

        val lanes = clip.automationLanes.take(12)
        val laneIds = IntArray(lanes.size) { lanes[it].targetParamId }
        val laneCounts = IntArray(lanes.size) { lanes[it].points.size.coerceAtMost(64) }
        val flatBeats = mutableListOf<Float>()
        val flatVals = mutableListOf<Float>()
        lanes.forEach { lane ->
            lane.points.take(64).sortedBy { it.beat }.forEach { pt ->
                flatBeats.add(pt.beat)
                flatVals.add(pt.value)
            }
        }

        val totBeats = clip.totalBeats(timeSigNum).toDouble()
        val loopStart = clip.effectiveLoopStart(timeSigNum).toDouble()
        val loopEnd = clip.effectiveLoopEnd(timeSigNum).toDouble()

        NativeAudioEngine.nativeSetClipData(
            trackIdx, sceneIdx, true, totBeats, loopStart, loopEnd, clip.loopEnabled,
            n, nums, starts, durs, vels, probs, offs,
            lanes.size, laneIds, laneCounts, flatBeats.toFloatArray(), flatVals.toFloatArray()
        )
    }

    fun sendEffect(scope: Int, trackOrPad: Int, slot: Int, fx: EffectModel) {
        if (!NativeAudioEngine.isNativeLoaded) return
        val arr = FloatArray(6) { i -> fx.params.getOrElse(i) { 0.5f } }
        NativeAudioEngine.nativeSetEffect(scope, trackOrPad, slot, fx.type.id, fx.bypassed, fx.wetDry, arr)
    }

    fun sendDrumPadEffect(trackIdx: Int, padIdx: Int, fxSlot: Int, fx: EffectModel) {
        if (!NativeAudioEngine.isNativeLoaded) return
        val arr = FloatArray(6) { i -> fx.params.getOrElse(i) { 0.5f } }
        NativeAudioEngine.nativeSetDrumPadEffectSlot(trackIdx, padIdx, fxSlot, fx.type.id, fx.bypassed, fx.wetDry, arr)
    }

    fun togglePlay() {
        val nextPlaying = !_telemetry.value.isPlaying
        kotlinCaptureBuffer.isTransportPlaying = nextPlaying
        if (!NativeAudioEngine.isNativeLoaded) {
            _telemetry.value = _telemetry.value.copy(isPlaying = nextPlaying, isCountingIn = false)
            return
        }
        val cmd = if (!nextPlaying) NativeAudioEngine.CMD_STOP else NativeAudioEngine.CMD_PLAY
        NativeAudioEngine.nativePostCommand(cmd, 0, 0, 0, 0f, 0f)
    }

    fun stopPlayback() {
        kotlinCaptureBuffer.isTransportPlaying = false
        kotlinCaptureBuffer.finalizeOpenNotes()
        if (!NativeAudioEngine.isNativeLoaded) {
            _telemetry.value = _telemetry.value.copy(
                isPlaying = false,
                isCountingIn = false,
                activeClips = List(8) { -1 },
                queuedClips = List(8) { -1 },
                trackPeaks = List(8) { 0f },
                masterPeakL = 0f,
                masterPeakR = 0f
            )
            return
        }
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_STOP, 0, 0, 0, 0f, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_ALL_NOTES_OFF, 0, 0, 0, 0f, 0f)
        for (ti in 0 until 8) {
            NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_STOP_TRACK_CLIP, ti, 0, 0, 0f, 0f)
            NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_NOTE_REPEAT, ti, 0, 0, 0.25f, 0f)
        }
        _telemetry.value = _telemetry.value.copy(
            isPlaying = false,
            isCountingIn = false,
            activeClips = List(8) { -1 },
            queuedClips = List(8) { -1 },
            trackPeaks = List(8) { 0f },
            masterPeakL = 0f,
            masterPeakR = 0f
        )
    }

    /**
     * Stops transport, stops all running clips, silences all voices & note-repeat holds,
     * and clears capture buffer cleanly when exiting a Set back to the library or backgrounding.
     */
    fun stopAndResetProjectTransport() {
        stopPlayback()
        clearCaptureBuffer()
        if (NativeAudioEngine.isNativeLoaded) {
            if (_isResampling.value) {
                stopResampling()
            }
        }
    }

    fun restart() {
        kotlinCaptureBuffer.isTransportPlaying = true
        if (!NativeAudioEngine.isNativeLoaded) {
            _telemetry.value = _telemetry.value.copy(isPlaying = true, transportBeat = 0.0, bar = 1, beat = 1, sixteenth = 1)
            return
        }
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_RESTART, 0, 0, 0, 0f, 0f)
    }

    fun setBpm(bpm: Float) {
        kotlinCaptureBuffer.bpm = bpm
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_BPM, 0, 0, 0, bpm, 0f)
    }

    fun setSwing(swing: Float) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_SWING, 0, 0, 0, swing, 0f)
    }

    fun setMetronome(enabled: Boolean) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_METRONOME, 0, if (enabled) 1 else 0, 0, 0f, 0f)
    }

    fun setCaptureTimeout(seconds: Int) {
        kotlinCaptureBuffer.timeoutSeconds = seconds
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_CAPTURE_TIMEOUT, 0, 0, 0, seconds.toFloat(), 0f)
    }

    fun launchClip(track: Int, scene: Int, startTransportIfStopped: Boolean = true) {
        if (!NativeAudioEngine.isNativeLoaded) {
            val active = _telemetry.value.activeClips.toMutableList()
            if (track in 0..7) active[track] = scene
            _telemetry.value = _telemetry.value.copy(
                activeClips = active,
                isPlaying = _telemetry.value.isPlaying || startTransportIfStopped
            )
            kotlinCaptureBuffer.isTransportPlaying = _telemetry.value.isPlaying
            return
        }
        NativeAudioEngine.nativePostCommand(
            NativeAudioEngine.CMD_LAUNCH_CLIP, track, scene, if (startTransportIfStopped) 1 else 0, 0f, 0f
        )
    }

    fun stopTrackClip(track: Int) {
        if (!NativeAudioEngine.isNativeLoaded) {
            val active = _telemetry.value.activeClips.toMutableList()
            if (track in 0..7) active[track] = -1
            _telemetry.value = _telemetry.value.copy(activeClips = active)
            return
        }
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_STOP_TRACK_CLIP, track, 0, 0, 0f, 0f)
    }

    fun launchScene(scene: Int) {
        if (!NativeAudioEngine.isNativeLoaded) {
            _telemetry.value = _telemetry.value.copy(isPlaying = true)
            kotlinCaptureBuffer.isTransportPlaying = true
            return
        }
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_LAUNCH_SCENE, 0, scene, 0, 0f, 0f)
    }

    fun setCaptureArmed(armed: Boolean) {
        kotlinCaptureBuffer.captureArmed = armed
        if (!armed) {
            kotlinCaptureBuffer.finalizeOpenNotes()
        }
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(
            NativeAudioEngine.CMD_SET_CAPTURE_ARMED, 0, if (armed) 1 else 0, 0, 0f, 0f
        )
    }

    fun noteOn(track: Int, note: Int, velocity: Float) {
        if (kotlinCaptureBuffer.captureArmed) {
            kotlinCaptureBuffer.isTransportPlaying = _telemetry.value.isPlaying
            kotlinCaptureBuffer.noteOn(track, note, velocity, _telemetry.value.transportBeat)
            if (!_isStarted.value || !NativeAudioEngine.isNativeLoaded) {
                _capturedNoteCount.value = kotlinCaptureBuffer.totalCount()
            }
        }
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_NOTE_ON, track, note, 0, velocity, 0f)
    }

    fun noteOff(track: Int, note: Int) {
        kotlinCaptureBuffer.noteOff(track, note)
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_NOTE_OFF, track, note, 0, 0f, 0f)
    }

    fun triggerDrumPad(track: Int, pad: Int, velocity: Float, pitchOffsetSemi: Float = 0f) {
        if (kotlinCaptureBuffer.captureArmed) {
            kotlinCaptureBuffer.isTransportPlaying = _telemetry.value.isPlaying
            kotlinCaptureBuffer.noteOn(track, 36 + pad.coerceIn(0, 15), velocity, _telemetry.value.transportBeat)
            if (!_isStarted.value || !NativeAudioEngine.isNativeLoaded) {
                _capturedNoteCount.value = kotlinCaptureBuffer.totalCount()
            }
        }
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_PAD_TRIGGER, track, pad, 0, velocity, pitchOffsetSemi)
    }

    fun releaseDrumPad(track: Int, pad: Int) {
        kotlinCaptureBuffer.noteOff(track, 36 + pad.coerceIn(0, 15))
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_PAD_RELEASE, track, pad, 0, 0f, 0f)
    }

    // Isolated audition/preview APIs (NEVER record into capture buffer and NEVER interrupt playback voices of matching pitch)
    fun auditionNote(track: Int, note: Int, velocity: Float) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_PREVIEW_NOTE_ON, track, note, 0, velocity, 0f)
    }

    fun auditionNoteOff(track: Int, note: Int) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_PREVIEW_NOTE_OFF, track, note, 0, 0f, 0f)
    }

    fun auditionDrumPad(track: Int, pad: Int, velocity: Float, pitchOffsetSemi: Float = 0f) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_PREVIEW_PAD_TRIGGER, track, pad, 0, velocity, pitchOffsetSemi)
    }

    fun previewSampleSlot(slotIndex: Int, gain: Float = 0.85f) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_PREVIEW_SAMPLE_SLOT, 0, slotIndex, 0, gain, 0f)
    }

    fun releaseAllPreviewNotes() {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_RELEASE_PREVIEW_NOTES, 0, 0, 0, 0f, 0f)
    }

    fun setNoteRepeat(track: Int, enabled: Boolean, intervalBeats: Double) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(
            NativeAudioEngine.CMD_SET_NOTE_REPEAT, track, if (enabled) 1 else 0, 0, intervalBeats.toFloat(), 0f
        )
    }

    fun setTrackMix(track: Int, volume: Float, pan: Float, muted: Boolean, soloed: Boolean) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_TRACK_VOLUME, track, 0, 0, volume, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_TRACK_PAN, track, 0, 0, pan, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_TRACK_MUTE, track, if (muted) 1 else 0, 0, 0f, 0f)
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_TRACK_SOLO, track, if (soloed) 1 else 0, 0, 0f, 0f)
    }

    fun setMasterVolume(vol: Float) {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_SET_MASTER_VOLUME, 0, 0, 0, vol, 0f)
    }

    fun clearCaptureBuffer() {
        kotlinCaptureBuffer.clear()
        _capturedNoteCount.value = 0
        _telemetry.value = _telemetry.value.copy(capturedNoteCount = 0, captureActive = false)
        if (NativeAudioEngine.isNativeLoaded) {
            NativeAudioEngine.nativePostCommand(NativeAudioEngine.CMD_CLEAR_CAPTURE, 0, 0, 0, 0f, 0f)
            NativeAudioEngine.nativeClearCaptureBuffer()
        }
    }

    private fun consumeEvents(): List<CapturedPerformanceEvent> {
        if (NativeAudioEngine.isNativeLoaded && _isStarted.value) {
            val packed = NativeAudioEngine.nativeConsumeCapturedNotes()
            kotlinCaptureBuffer.clear()
            _capturedNoteCount.value = 0
            _telemetry.value = _telemetry.value.copy(capturedNoteCount = 0, captureActive = false)
            if (packed.size >= 7) {
                val count = (packed.size - 1) / 6
                val list = ArrayList<CapturedPerformanceEvent>(count)
                for (i in 0 until count) {
                    val base = 1 + i * 6
                    list.add(
                        CapturedPerformanceEvent(
                            trackIndex = packed[base].toInt(),
                            note = packed[base + 1].toInt(),
                            beatOffset = packed[base + 2].toDouble(),
                            durationBeats = packed[base + 3].toDouble(),
                            velocity = packed[base + 4],
                            transportBeatAtOn = packed[base + 5].toDouble()
                        )
                    )
                }
                return list
            }
            return emptyList()
        }
        val fallback = kotlinCaptureBuffer.consumeAll()
        _capturedNoteCount.value = 0
        return fallback
    }

    /**
     * Converts recent live performance events into an accurate, sorted ClipModel.
     */
    fun captureRecentPerformanceToClip(
        trackIndex: Int,
        quantizeGridBeats: Double = 0.0,
        quantizeStrength: Float = 0.0f,
        fixedBars: Int? = null,
        captureMode: CaptureMode = CaptureMode.FREE_LENGTH,
        timeSigNum: Int = currentTimeSigNum,
        isDrumTrack: Boolean = false
    ): ClipModel? {
        val events = consumeEvents()
        if (events.isEmpty()) return null
        return CaptureProcessor.buildCapturedClip(
            events = events,
            trackIndex = trackIndex,
            timeSigNum = timeSigNum,
            captureMode = captureMode,
            fixedBars = fixedBars,
            quantizeGridBeats = quantizeGridBeats,
            quantizeStrength = quantizeStrength,
            isDrumTrack = isDrumTrack
        )
    }

    fun overdubRecentPerformanceIntoClip(
        existing: ClipModel,
        trackIndex: Int,
        quantizeGridBeats: Double = 0.0,
        quantizeStrength: Float = 0.0f,
        timeSigNum: Int = currentTimeSigNum,
        isDrumTrack: Boolean = false
    ): ClipModel? {
        val events = consumeEvents()
        if (events.isEmpty()) return null
        return CaptureProcessor.mergeOverdubIntoClip(
            existing = existing,
            events = events,
            trackIndex = trackIndex,
            timeSigNum = timeSigNum,
            quantizeGridBeats = quantizeGridBeats,
            quantizeStrength = quantizeStrength,
            isDrumTrack = isDrumTrack
        )
    }

    fun startResampling() {
        if (!NativeAudioEngine.isNativeLoaded || _isResampling.value) return
        synchronized(resampledFloats) { resampledFloats.clear() }
        NativeAudioEngine.nativeSetResampling(true)
        _isResampling.value = true
        resampleJob = scope.launch {
            while (isActive && _isResampling.value) {
                val chunk = NativeAudioEngine.nativeDrainResampled(8192)
                if (chunk.isNotEmpty()) {
                    synchronized(resampledFloats) {
                        for (f in chunk) resampledFloats.add(f)
                    }
                }
                delay(25)
            }
        }
    }

    fun stopResampling(): DecodedSample? {
        if (!NativeAudioEngine.isNativeLoaded || !_isResampling.value) return null
        _isResampling.value = false
        NativeAudioEngine.nativeSetResampling(false)
        resampleJob?.cancel()
        val remaining = NativeAudioEngine.nativeDrainResampled(32768)
        val arr = synchronized(resampledFloats) {
            for (f in remaining) resampledFloats.add(f)
            resampledFloats.toFloatArray()
        }
        if (arr.isEmpty()) return null
        val sr = _telemetry.value.sampleRate.coerceAtLeast(44100)
        val frames = arr.size / 2
        val durMs = (frames * 1000L) / sr
        return DecodedSample(sr, 2, arr, SamplePcmTools.computePeaks(arr, 2), durMs)
    }

    suspend fun exportWavToUri(
        destinationUri: Uri,
        bitDepth: Int,
        sampleRate: Int,
        scopeMode: Int, // 0=FullSet, 1=Scene, 2=Track
        sceneIndex: Int,
        trackIndex: Int,
        totalBeats: Double
    ): Boolean = withContext(Dispatchers.IO) {
        if (!NativeAudioEngine.isNativeLoaded) return@withContext false
        val tmp = File.createTempFile("notecraft_export_", ".wav", context.cacheDir)
        _exportProgress.value = 0f
        val poller = launch {
            while (isActive) {
                _exportProgress.value = NativeAudioEngine.nativeGetExportProgress()
                delay(60)
            }
        }
        try {
            val ok = NativeAudioEngine.nativeRenderOfflineWav(
                tmp.absolutePath, bitDepth, sampleRate, scopeMode, sceneIndex, trackIndex, totalBeats
            )
            poller.cancel()
            if (!ok) return@withContext false
            val copied = WavExportHelper.copyAndFlushWavToUri(context, tmp, destinationUri)
            if (copied) _exportProgress.value = 1f
            copied
        } finally {
            poller.cancel()
            tmp.delete()
            delay(300)
            _exportProgress.value = null
        }
    }

    suspend fun exportWavToDownloads(
        projectName: String,
        bitDepth: Int,
        sampleRate: Int,
        scopeMode: Int,
        sceneIndex: Int,
        trackIndex: Int,
        totalBeats: Double
    ): ExportedWavResult? = withContext(Dispatchers.IO) {
        if (!NativeAudioEngine.isNativeLoaded) return@withContext null
        val tmp = File.createTempFile("notecraft_export_", ".wav", context.cacheDir)
        _exportProgress.value = 0f
        val poller = launch {
            while (isActive) {
                _exportProgress.value = NativeAudioEngine.nativeGetExportProgress()
                delay(60)
            }
        }
        try {
            val ok = NativeAudioEngine.nativeRenderOfflineWav(
                tmp.absolutePath, bitDepth, sampleRate, scopeMode, sceneIndex, trackIndex, totalBeats
            )
            poller.cancel()
            if (!ok) return@withContext null
            val res = WavExportHelper.saveWavFileToDownloads(context, tmp, projectName)
            if (res != null) _exportProgress.value = 1f
            res
        } finally {
            poller.cancel()
            tmp.delete()
            delay(300)
            _exportProgress.value = null
        }
    }

    suspend fun exportWavToFolderTree(
        treeUri: Uri,
        projectName: String,
        bitDepth: Int,
        sampleRate: Int,
        scopeMode: Int,
        sceneIndex: Int,
        trackIndex: Int,
        totalBeats: Double
    ): ExportedWavResult? = withContext(Dispatchers.IO) {
        if (!NativeAudioEngine.isNativeLoaded) return@withContext null
        val tmp = File.createTempFile("notecraft_export_", ".wav", context.cacheDir)
        _exportProgress.value = 0f
        val poller = launch {
            while (isActive) {
                _exportProgress.value = NativeAudioEngine.nativeGetExportProgress()
                delay(60)
            }
        }
        try {
            val ok = NativeAudioEngine.nativeRenderOfflineWav(
                tmp.absolutePath, bitDepth, sampleRate, scopeMode, sceneIndex, trackIndex, totalBeats
            )
            poller.cancel()
            if (!ok) return@withContext null
            val res = WavExportHelper.saveWavFileToTreeUri(context, tmp, treeUri, projectName)
            if (res != null) _exportProgress.value = 1f
            res
        } finally {
            poller.cancel()
            tmp.delete()
            delay(300)
            _exportProgress.value = null
        }
    }

    fun cancelExport() {
        if (!NativeAudioEngine.isNativeLoaded) return
        NativeAudioEngine.nativeCancelExport()
    }

    private fun startTelemetryPolling() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                val arr = NativeAudioEngine.nativeGetTelemetry()
                val queued = NativeAudioEngine.nativeGetQueuedClips()
                var activeAudio = false
                if (arr.size >= 40 && queued.size >= 8) {
                    val isPlaying = arr[0] > 0.5f
                    kotlinCaptureBuffer.isTransportPlaying = isPlaying
                    val peakL = if (arr[6] < 0.001f) 0f else arr[6]
                    val peakR = if (arr[7] < 0.001f) 0f else arr[7]
                    val trackPeaks = List(8) { idx ->
                        val pk = arr[13 + idx]
                        if (pk < 0.001f) 0f else pk
                    }
                    val capCount = arr[37].toInt().coerceAtLeast(0)
                    val capActive = arr[38] > 0.5f
                    _capturedNoteCount.value = capCount
                    activeAudio = isPlaying || peakL > 0f || peakR > 0f || trackPeaks.any { it > 0f }
                    val next = TransportTelemetryState(
                        isPlaying = isPlaying,
                        isCountingIn = arr[1] > 0.5f,
                        transportBeat = arr[2].toDouble(),
                        timeSigNum = arr[39].toInt().coerceIn(1, 12),
                        bar = arr[3].toInt().coerceAtLeast(1),
                        beat = arr[4].toInt().coerceAtLeast(1),
                        sixteenth = arr[5].toInt().coerceAtLeast(1),
                        masterPeakL = peakL,
                        masterPeakR = peakR,
                        masterClipping = arr[8] > 0.5f,
                        xrunCount = arr[9].toInt(),
                        sampleRate = arr[10].toInt(),
                        bufferFrames = arr[11].toInt(),
                        isAAudio = arr[12] > 0.5f,
                        trackPeaks = trackPeaks,
                        activeClips = List(8) { arr[21 + it].toInt() },
                        queuedClips = queued.toList(),
                        trackClipBeats = List(8) { arr[29 + it].toDouble() },
                        capturedNoteCount = capCount,
                        captureActive = capActive
                    )
                    if (next != _telemetry.value) {
                        _telemetry.value = next
                    }
                }
                delay(if (activeAudio) 25 else 80)
            }
        }
    }
}
