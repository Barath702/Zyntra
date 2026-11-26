package com.example.core.project

import com.example.core.common.GlobalQuantization
import com.example.core.common.ScaleType
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class TrackType(val label: String, val engineIndex: Int) {
    DRUM_RACK("Drum Rack", 0),
    SUBTRACTIVE_SYNTH("Synth", 1),
    MELODIC_SAMPLER("Sampler", 2)
}

@Serializable
enum class EffectType(
    val id: Int,
    val displayName: String,
    val paramLabels: List<String>,
    val defaultParams: List<Float>
) {
    NONE(0, "None", listOf("P1", "P2", "P3", "P4", "P5", "P6"), listOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f)),
    PARAMETRIC_EQ(1, "Parametric EQ", listOf("Low", "Mid Freq", "Mid Gain", "Mid Q", "High", "Out"), listOf(0.55f, 0.45f, 0.5f, 0.4f, 0.55f, 0.5f)),
    COMPRESSOR(2, "Compressor", listOf("Thresh", "Ratio", "Attack", "Release", "Makeup", "Knee"), listOf(0.55f, 0.35f, 0.25f, 0.4f, 0.25f, 0.5f)),
    REVERB(3, "Reverb", listOf("Size", "Damp", "Pre-Dly", "Width", "Decay", "Tone"), listOf(0.6f, 0.4f, 0.2f, 0.8f, 0.6f, 0.5f)),
    DELAY(4, "Delay", listOf("Time", "Feedback", "Hi-Cut", "PingPong", "Spread", "Mod"), listOf(0.45f, 0.4f, 0.65f, 0.8f, 0.5f, 0.2f)),
    CHORUS(5, "Chorus", listOf("Rate", "Depth", "Delay", "Feedback", "Width", "Tone"), listOf(0.35f, 0.5f, 0.4f, 0.2f, 0.8f, 0.5f)),
    PHASER(6, "Phaser", listOf("Rate", "Depth", "Feedback", "Stages", "Spread", "Center"), listOf(0.3f, 0.65f, 0.45f, 0.5f, 0.5f, 0.5f)),
    FLANGER(7, "Flanger", listOf("Rate", "Depth", "Feedback", "Manual", "Stereo", "Tone"), listOf(0.25f, 0.55f, 0.6f, 0.3f, 0.5f, 0.5f)),
    SATURATION(8, "Saturation", listOf("Drive", "Bias", "Tone", "Warmth", "Curve", "Trim"), listOf(0.35f, 0.52f, 0.7f, 0.5f, 0.5f, 0.5f)),
    DISTORTION(9, "Distortion", listOf("Drive", "Tone", "Level", "Clip", "Bias", "Presence"), listOf(0.45f, 0.65f, 0.65f, 0.5f, 0.5f, 0.5f)),
    BITCRUSHER(10, "Bitcrusher", listOf("Bits", "Downsamp", "Jitter", "Tone", "Drive", "Trim"), listOf(0.4f, 0.25f, 0.1f, 0.8f, 0.2f, 0.5f)),
    AUTO_FILTER(11, "Auto Filter", listOf("Cutoff", "Reso", "Env Amt", "LFO Rate", "LFO Amt", "Drive"), listOf(0.45f, 0.45f, 0.55f, 0.35f, 0.3f, 0.2f)),
    STEREO_WIDENER(12, "Stereo Widener", listOf("Width", "Haas", "Mid Gain", "Side Gain", "Mono Bass", "Trim"), listOf(0.7f, 0.25f, 0.5f, 0.5f, 0.3f, 0.5f))
}

@Serializable
data class EffectModel(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType = EffectType.NONE,
    val bypassed: Boolean = false,
    val wetDry: Float = 0.4f,
    val presetName: String = "Init",
    val params: List<Float> = type.defaultParams
)

@Serializable
data class SynthParamsModel(
    val osc1Wave: Int = 0,          // 0=Saw, 1=Square, 2=Tri, 3=Sine
    val osc2Wave: Int = 1,
    val oscMix: Float = 0.45f,
    val osc2Detune: Float = 0.54f,
    val subLevel: Float = 0.3f,
    val noiseLevel: Float = 0.0f,
    val fold: Float = 0.0f,
    val warp: Float = 0.15f,
    val filterType: Int = 0,        // 0=LPF, 1=HPF, 2=BPF
    val cutoff: Float = 0.72f,
    val resonance: Float = 0.25f,
    val filterEnvAmt: Float = 0.4f,
    val filterDecay: Float = 0.38f,
    val attack: Float = 0.02f,
    val decay: Float = 0.35f,
    val sustain: Float = 0.72f,
    val release: Float = 0.28f,
    val lfoRate: Float = 0.25f,
    val lfoDepth: Float = 0.05f,
    val lfoTarget: Int = 0,
    val unison: Float = 0.25f,
    val glide: Float = 0.0f
)

@Serializable
data class SamplerParamsModel(
    val sampleId: String = "builtin_rhodes",
    val sampleName: String = "Electric Keys",
    val sampleSlot: Int = 16,
    val rootNote: Int = 60,
    val startNorm: Float = 0.0f,
    val endNorm: Float = 1.0f,
    val loopEnabled: Boolean = false,
    val loopStartNorm: Float = 0.15f,
    val loopEndNorm: Float = 0.85f,
    val reverse: Boolean = false,
    val pitchSemitones: Float = 0.0f,
    val playbackRate: Float = 1.0f,
    val gain: Float = 0.85f,
    val cutoff: Float = 0.95f,
    val resonance: Float = 0.1f,
    val attack: Float = 0.01f,
    val decay: Float = 0.45f,
    val sustain: Float = 0.85f,
    val release: Float = 0.3f
)

@Serializable
data class DrumPadModel(
    val index: Int,
    val name: String,
    val colorIndex: Int = 0,
    val sampleId: String = "",
    val sampleSlot: Int = -1,
    val volume: Float = 0.85f,
    val pan: Float = 0.0f,
    val pitchSemitones: Float = 0.0f,
    val decay: Float = 1.0f,
    val startNorm: Float = 0.0f,
    val endNorm: Float = 1.0f,
    val reverse: Boolean = false,
    val muted: Boolean = false,
    val soloed: Boolean = false,
    val chokeGroup: Int = 0,
    val sendAmount: Float = 0.2f,
    val toneFilter: Float = 0.92f,
    val padEffect: EffectModel = EffectModel(type = EffectType.NONE),
    val padEffects: List<EffectModel> = emptyList()
) {
    fun effectivePadEffects(): List<EffectModel> = when {
        padEffects.isNotEmpty() -> padEffects.take(EffectChainTransforms.MAX_EFFECT_SLOTS)
        padEffect.type != EffectType.NONE -> listOf(padEffect)
        else -> emptyList()
    }
}

@Serializable
data class MidiNoteModel(
    val id: String = UUID.randomUUID().toString(),
    val note: Int,
    val startBeat: Float,
    val durationBeats: Float = 0.25f,
    val velocity: Float = 0.85f,
    val probability: Float = 1.0f,
    val timingOffset: Float = 0.0f
)

@Serializable
data class AutomationPointModel(
    val beat: Float,
    val value: Float
)

@Serializable
data class AutomationLaneModel(
    val targetParamId: Int, // 0=Vol, 1=Pan, 2=Cutoff, 3=Reso, 4=FX1Mix, 5=FX2Mix
    val label: String,
    val points: List<AutomationPointModel> = emptyList()
)

@Serializable
data class ClipModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Clip",
    val colorIndex: Int = 0,
    val lengthBars: Int = 1,
    val loopEnabled: Boolean = true,
    val loopStartBeats: Float = 0f,
    val loopEndBeats: Float? = null,
    val notes: List<MidiNoteModel> = emptyList(),
    val automationLanes: List<AutomationLaneModel> = emptyList()
) {
    fun totalBeats(timeSigNum: Int = 4): Float =
        (lengthBars.coerceIn(1, 16) * timeSigNum.coerceIn(1, 12)).toFloat()

    fun effectiveLoopStart(timeSigNum: Int = 4): Float {
        val tot = totalBeats(timeSigNum)
        return loopStartBeats.coerceIn(0f, (tot - 0.25f).coerceAtLeast(0f))
    }

    fun effectiveLoopEnd(timeSigNum: Int = 4): Float {
        val tot = totalBeats(timeSigNum)
        val start = effectiveLoopStart(timeSigNum)
        return (loopEndBeats ?: tot).coerceIn((start + 0.25f).coerceAtMost(tot), tot)
    }
}

@Serializable
data class SceneModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Scene",
    val colorIndex: Int = 0
)

@Serializable
data class TrackModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorIndex: Int,
    val type: TrackType,
    val presetName: String,
    val volume: Float = 0.85f,
    val pan: Float = 0.0f,
    val muted: Boolean = false,
    val soloed: Boolean = false,
    val synthParams: SynthParamsModel = SynthParamsModel(),
    val samplerParams: SamplerParamsModel = SamplerParamsModel(),
    val drumPads: List<DrumPadModel> = emptyList(),
    val effects: List<EffectModel> = emptyList(),
    val clips: Map<Int, ClipModel> = emptyMap() // sceneIndex -> ClipModel
)

@Serializable
data class NoteSet(
    val schemaVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Set",
    val bpm: Float = 120f,
    val timeSigNum: Int = 4,
    val timeSigDen: Int = 4,
    val rootNote: Int = 0, // 0..11
    val scaleType: ScaleType = ScaleType.NATURAL_MINOR,
    val swing: Float = 0.0f,
    val globalQuantization: GlobalQuantization = GlobalQuantization.ONE_BAR,
    val masterVolume: Float = 0.9f,
    val masterEffects: List<EffectModel> = listOf(
        EffectModel(type = EffectType.COMPRESSOR, bypassed = true, wetDry = 0.35f, presetName = "Glue"),
        EffectModel(type = EffectType.REVERB, bypassed = true, wetDry = 0.18f, presetName = "Room")
    ),
    val scenes: List<SceneModel> = (1..8).map { SceneModel(name = "Scene $it") },
    val tracks: List<TrackModel> = emptyList(),
    val artworkSeed: Int = (1000..9999).random(),
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
