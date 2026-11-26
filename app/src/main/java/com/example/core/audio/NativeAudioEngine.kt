package com.example.core.audio

import android.media.midi.MidiDevice

object NativeAudioEngine {
    val isNativeLoaded: Boolean = try {
        System.loadLibrary("notecraft_audio")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    // CommandType indices matching AudioEngine.h
    const val CMD_NOTE_ON = 0
    const val CMD_NOTE_OFF = 1
    const val CMD_PAD_TRIGGER = 2
    const val CMD_PAD_RELEASE = 3
    const val CMD_PITCH_BEND = 4
    const val CMD_MOD_WHEEL = 5
    const val CMD_SUSTAIN = 6
    const val CMD_PLAY = 7
    const val CMD_STOP = 8
    const val CMD_RESTART = 9
    const val CMD_SET_BPM = 10
    const val CMD_SET_SWING = 11
    const val CMD_SET_METRONOME = 12
    const val CMD_SET_COUNTIN = 13
    const val CMD_SET_QUANTIZATION = 14
    const val CMD_SET_TIMESIG = 15
    const val CMD_LAUNCH_CLIP = 16
    const val CMD_STOP_TRACK_CLIP = 17
    const val CMD_LAUNCH_SCENE = 18
    const val CMD_SET_TRACK_VOLUME = 19
    const val CMD_SET_TRACK_PAN = 20
    const val CMD_SET_TRACK_MUTE = 21
    const val CMD_SET_TRACK_SOLO = 22
    const val CMD_SET_MASTER_VOLUME = 23
    const val CMD_SET_NOTE_REPEAT = 24
    const val CMD_SET_PROFILE = 25
    const val CMD_ALL_NOTES_OFF = 27
    const val CMD_SET_CAPTURE_TIMEOUT = 28
    const val CMD_PREVIEW_NOTE_ON = 29
    const val CMD_PREVIEW_NOTE_OFF = 30
    const val CMD_PREVIEW_PAD_TRIGGER = 31
    const val CMD_PREVIEW_SAMPLE_SLOT = 32
    const val CMD_CLEAR_CAPTURE = 33
    const val CMD_SET_CAPTURE_ARMED = 34
    const val CMD_RELEASE_PREVIEW_NOTES = 35

    external fun nativeStart(sampleRate: Int, bufferFrames: Int): Boolean
    external fun nativeStop()
    external fun nativePostCommand(type: Int, track: Int, arg1: Int, arg2: Int, f1: Float, f2: Float)

    external fun nativeSetTrackEnabled(track: Int, enabled: Boolean, trackType: Int)
    external fun nativeSetSynthParams(track: Int, ints: IntArray, floats: FloatArray)
    external fun nativeSetSamplerParams(
        track: Int,
        sampleSlot: Int,
        rootNote: Int,
        loop: Boolean,
        reverse: Boolean,
        floats: FloatArray
    )

    external fun nativeSetDrumPadParams(
        track: Int,
        pad: Int,
        sampleSlot: Int,
        chokeGroup: Int,
        reverse: Boolean,
        muted: Boolean,
        soloed: Boolean,
        floats: FloatArray
    )

    external fun nativeSetEffect(
        scope: Int, // 0=track, 1=pad, 2=master
        trackOrPad: Int,
        slot: Int,
        effectType: Int,
        bypassed: Boolean,
        wetDry: Float,
        params: FloatArray
    )

    external fun nativeSetDrumPadEffectSlot(
        track: Int,
        pad: Int,
        fxSlot: Int,
        effectType: Int,
        bypassed: Boolean,
        wetDry: Float,
        params: FloatArray
    )

    external fun nativeSetClipData(
        track: Int,
        scene: Int,
        exists: Boolean,
        lengthBeats: Double,
        loopStartBeats: Double,
        loopEndBeats: Double,
        loop: Boolean,
        noteCount: Int,
        noteNums: IntArray,
        noteStarts: FloatArray,
        noteDurs: FloatArray,
        noteVels: FloatArray,
        noteProbs: FloatArray,
        noteOffsets: FloatArray,
        autoLaneCount: Int,
        autoParamIds: IntArray,
        autoPointCounts: IntArray,
        autoBeats: FloatArray,
        autoValues: FloatArray
    )

    external fun nativeLoadSampleBuffer(slot: Int, sampleRate: Int, channels: Int, pcmData: FloatArray)
    external fun nativeGetTelemetry(): FloatArray
    external fun nativeGetQueuedClips(): IntArray
    external fun nativeConsumeCapturedNotes(): FloatArray
    external fun nativeClearCaptureBuffer()

    external fun nativeSetResampling(enabled: Boolean)
    external fun nativeDrainResampled(maxFloats: Int): FloatArray

    external fun nativeRenderOfflineWav(
        path: String,
        bitDepth: Int,
        sampleRate: Int,
        scopeMode: Int,
        sceneIndex: Int,
        trackIndex: Int,
        totalBeats: Double
    ): Boolean

    external fun nativeGetExportProgress(): Float
    external fun nativeCancelExport()

    external fun nativeAttachMidiInput(midiDevice: MidiDevice, portNumber: Int)
    external fun nativeDetachMidiInput()
    external fun nativeAttachMidiOutput(midiDevice: MidiDevice, portNumber: Int)
    external fun nativeDetachMidiOutput()
    external fun nativeSendMidiBytes(data: ByteArray)
    external fun nativeSetMidiRouting(targetTrack: Int, channel: Int)
}
