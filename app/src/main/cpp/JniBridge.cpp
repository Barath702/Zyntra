#include "AudioEngine.h"
#include <jni.h>
#include <atomic>
#include <memory>

using namespace notecraft;

namespace {
std::unique_ptr<AudioEngine> gEngine;
std::atomic<float> gExportProgress{0.0f};
std::atomic<bool> gExportCancel{false};

AudioEngine* engine() {
    if (!gEngine) gEngine = std::make_unique<AudioEngine>();
    return gEngine.get();
}
} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeStart(JNIEnv*, jobject, jint sampleRate, jint bufferFrames) {
    return engine()->startStream(sampleRate, bufferFrames) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeStop(JNIEnv*, jobject) {
    if (gEngine) gEngine->stopStream();
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativePostCommand(
    JNIEnv*, jobject, jint type, jint track, jint arg1, jint arg2, jfloat f1, jfloat f2
) {
    EngineCommand cmd{static_cast<CommandType>(type), track, arg1, arg2, f1, f2};
    engine()->postCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeSetTrackEnabled(
    JNIEnv*, jobject, jint track, jboolean enabled, jint trackType
) {
    engine()->setTrackEnabled(track, enabled == JNI_TRUE, trackType);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeSetSynthParams(
    JNIEnv* env, jobject, jint track, jintArray ints, jfloatArray floats
) {
    if (!ints || !floats || env->GetArrayLength(ints) < 4 || env->GetArrayLength(floats) < 18) return;
    jint* iPtr = env->GetIntArrayElements(ints, nullptr);
    jfloat* fPtr = env->GetFloatArrayElements(floats, nullptr);
    if (!iPtr || !fPtr) {
        if (iPtr) env->ReleaseIntArrayElements(ints, iPtr, JNI_ABORT);
        if (fPtr) env->ReleaseFloatArrayElements(floats, fPtr, JNI_ABORT);
        return;
    }
    SynthParams p{};
    p.osc1Wave = iPtr[0];
    p.osc2Wave = iPtr[1];
    p.filterType = iPtr[2];
    p.lfoTarget = iPtr[3];
    p.oscMix = fPtr[0];
    p.osc2Detune = fPtr[1];
    p.subLevel = fPtr[2];
    p.noiseLevel = fPtr[3];
    p.fold = fPtr[4];
    p.warp = fPtr[5];
    p.cutoff = fPtr[6];
    p.resonance = fPtr[7];
    p.filterEnvAmt = fPtr[8];
    p.filterDecay = fPtr[9];
    p.attack = fPtr[10];
    p.decay = fPtr[11];
    p.sustain = fPtr[12];
    p.release = fPtr[13];
    p.lfoRate = fPtr[14];
    p.lfoDepth = fPtr[15];
    p.unison = fPtr[16];
    p.glide = fPtr[17];
    env->ReleaseIntArrayElements(ints, iPtr, JNI_ABORT);
    env->ReleaseFloatArrayElements(floats, fPtr, JNI_ABORT);
    engine()->setSynthParams(track, p);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeSetSamplerParams(
    JNIEnv* env, jobject, jint track, jint sampleSlot, jint rootNote, jboolean loop, jboolean reverse, jfloatArray floats
) {
    if (!floats || env->GetArrayLength(floats) < 13) return;
    jfloat* fPtr = env->GetFloatArrayElements(floats, nullptr);
    if (!fPtr) return;
    SamplerParams p{};
    p.sampleSlot = sampleSlot;
    p.rootNote = rootNote;
    p.loopEnabled = (loop == JNI_TRUE);
    p.reverse = (reverse == JNI_TRUE);
    p.startNorm = fPtr[0];
    p.endNorm = fPtr[1];
    p.loopStartNorm = fPtr[2];
    p.loopEndNorm = fPtr[3];
    p.pitchSemitones = fPtr[4];
    p.playbackRate = fPtr[5];
    p.gain = fPtr[6];
    p.cutoff = fPtr[7];
    p.resonance = fPtr[8];
    p.attack = fPtr[9];
    p.decay = fPtr[10];
    p.sustain = fPtr[11];
    p.release = fPtr[12];
    env->ReleaseFloatArrayElements(floats, fPtr, JNI_ABORT);
    engine()->setSamplerParams(track, p);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeSetDrumPadParams(
    JNIEnv* env, jobject, jint track, jint pad, jint sampleSlot, jint chokeGroup,
    jboolean reverse, jboolean muted, jboolean soloed, jfloatArray floats
) {
    if (!floats || env->GetArrayLength(floats) < 8) return;
    jfloat* fPtr = env->GetFloatArrayElements(floats, nullptr);
    if (!fPtr) return;
    DrumPadParams p{};
    p.sampleSlot = sampleSlot;
    p.chokeGroup = chokeGroup;
    p.reverse = (reverse == JNI_TRUE);
    p.muted = (muted == JNI_TRUE);
    p.soloed = (soloed == JNI_TRUE);
    p.volume = fPtr[0];
    p.pan = fPtr[1];
    p.pitchSemitones = fPtr[2];
    p.decay = fPtr[3];
    p.startNorm = fPtr[4];
    p.endNorm = fPtr[5];
    p.sendAmount = fPtr[6];
    p.toneFilter = fPtr[7];
    env->ReleaseFloatArrayElements(floats, fPtr, JNI_ABORT);
    engine()->setDrumPadParams(track, pad, p);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeSetEffect(
    JNIEnv* env, jobject, jint scope, jint trackOrPad, jint slot, jint effectType,
    jboolean bypassed, jfloat wetDry, jfloatArray params
) {
    if (!params || env->GetArrayLength(params) < 6) return;
    jfloat* fPtr = env->GetFloatArrayElements(params, nullptr);
    if (!fPtr) return;
    EffectConfig cfg{};
    cfg.type = static_cast<EffectType>(effectType);
    cfg.bypassed = (bypassed == JNI_TRUE);
    cfg.wetDry = wetDry;
    for (int i = 0; i < 6; ++i) cfg.p[i] = fPtr[i];
    env->ReleaseFloatArrayElements(params, fPtr, JNI_ABORT);

    if (scope == 0) engine()->setTrackEffect(trackOrPad, slot, cfg);
    else if (scope == 1) engine()->setDrumPadEffect(trackOrPad, slot, 0, cfg);
    else engine()->setMasterEffect(slot, cfg);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeSetDrumPadEffectSlot(
    JNIEnv* env, jobject, jint track, jint pad, jint fxSlot, jint effectType,
    jboolean bypassed, jfloat wetDry, jfloatArray params
) {
    if (!params || env->GetArrayLength(params) < 6) return;
    jfloat* fPtr = env->GetFloatArrayElements(params, nullptr);
    if (!fPtr) return;
    EffectConfig cfg{};
    cfg.type = static_cast<EffectType>(effectType);
    cfg.bypassed = (bypassed == JNI_TRUE);
    cfg.wetDry = wetDry;
    for (int i = 0; i < 6; ++i) cfg.p[i] = fPtr[i];
    env->ReleaseFloatArrayElements(params, fPtr, JNI_ABORT);
    engine()->setDrumPadEffect(track, pad, fxSlot, cfg);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeSetClipData(
    JNIEnv* env, jobject, jint track, jint scene, jboolean exists, jdouble lengthBeats,
    jdouble loopStartBeats, jdouble loopEndBeats, jboolean loop,
    jint noteCount, jintArray noteNums, jfloatArray noteStarts, jfloatArray noteDurs,
    jfloatArray noteVels, jfloatArray noteProbs, jfloatArray noteOffsets,
    jint autoLaneCount, jintArray autoParamIds, jintArray autoPointCounts,
    jfloatArray autoBeats, jfloatArray autoValues
) {
    ClipData clip{};
    clip.exists = (exists == JNI_TRUE);
    clip.lengthBeats = lengthBeats;
    clip.loopStartBeats = std::max(0.0, static_cast<double>(loopStartBeats));
    clip.loopEndBeats = (loopEndBeats > clip.loopStartBeats + 0.05) ? static_cast<double>(loopEndBeats) : lengthBeats;
    clip.loopEnabled = (loop == JNI_TRUE);

    int requestedNotes = std::max(0, std::min(kMaxClipNotes, static_cast<int>(noteCount)));
    if (requestedNotes > 0 && noteNums && noteStarts && noteDurs && noteVels && noteProbs && noteOffsets) {
        int safeCount = requestedNotes;
        safeCount = std::min(safeCount, static_cast<int>(env->GetArrayLength(noteNums)));
        safeCount = std::min(safeCount, static_cast<int>(env->GetArrayLength(noteStarts)));
        safeCount = std::min(safeCount, static_cast<int>(env->GetArrayLength(noteDurs)));
        safeCount = std::min(safeCount, static_cast<int>(env->GetArrayLength(noteVels)));
        safeCount = std::min(safeCount, static_cast<int>(env->GetArrayLength(noteProbs)));
        safeCount = std::min(safeCount, static_cast<int>(env->GetArrayLength(noteOffsets)));

        jint* nPtr = env->GetIntArrayElements(noteNums, nullptr);
        jfloat* sPtr = env->GetFloatArrayElements(noteStarts, nullptr);
        jfloat* dPtr = env->GetFloatArrayElements(noteDurs, nullptr);
        jfloat* vPtr = env->GetFloatArrayElements(noteVels, nullptr);
        jfloat* pPtr = env->GetFloatArrayElements(noteProbs, nullptr);
        jfloat* oPtr = env->GetFloatArrayElements(noteOffsets, nullptr);
        if (nPtr && sPtr && dPtr && vPtr && pPtr && oPtr) {
            clip.noteCount = safeCount;
            for (int i = 0; i < safeCount; ++i) {
                clip.notes[i] = MidiNoteEvent{nPtr[i], sPtr[i], dPtr[i], vPtr[i], pPtr[i], oPtr[i]};
            }
        }
        if (nPtr) env->ReleaseIntArrayElements(noteNums, nPtr, JNI_ABORT);
        if (sPtr) env->ReleaseFloatArrayElements(noteStarts, sPtr, JNI_ABORT);
        if (dPtr) env->ReleaseFloatArrayElements(noteDurs, dPtr, JNI_ABORT);
        if (vPtr) env->ReleaseFloatArrayElements(noteVels, vPtr, JNI_ABORT);
        if (pPtr) env->ReleaseFloatArrayElements(noteProbs, pPtr, JNI_ABORT);
        if (oPtr) env->ReleaseFloatArrayElements(noteOffsets, oPtr, JNI_ABORT);
    }

    int requestedLanes = std::max(0, std::min(kMaxAutomationLanes, static_cast<int>(autoLaneCount)));
    if (requestedLanes > 0 && autoParamIds && autoPointCounts && autoBeats && autoValues) {
        int safeLanes = std::min(requestedLanes, static_cast<int>(env->GetArrayLength(autoParamIds)));
        safeLanes = std::min(safeLanes, static_cast<int>(env->GetArrayLength(autoPointCounts)));
        int maxAutoPts = std::min(static_cast<int>(env->GetArrayLength(autoBeats)),
                                  static_cast<int>(env->GetArrayLength(autoValues)));

        jint* idPtr = env->GetIntArrayElements(autoParamIds, nullptr);
        jint* cntPtr = env->GetIntArrayElements(autoPointCounts, nullptr);
        jfloat* bPtr = env->GetFloatArrayElements(autoBeats, nullptr);
        jfloat* vPtr = env->GetFloatArrayElements(autoValues, nullptr);
        if (idPtr && cntPtr && bPtr && vPtr) {
            clip.autoLaneCount = safeLanes;
            int cursor = 0;
            for (int l = 0; l < safeLanes; ++l) {
                clip.autoLanes[l].targetParamId = idPtr[l];
                int avail = std::max(0, maxAutoPts - cursor);
                int cnt = std::max(0, std::min(avail, std::min(kMaxAutomationPoints, static_cast<int>(cntPtr[l]))));
                clip.autoLanes[l].count = cnt;
                for (int p = 0; p < cnt; ++p) {
                    clip.autoLanes[l].points[p] = AutomationPoint{bPtr[cursor], vPtr[cursor]};
                    cursor++;
                }
            }
        }
        if (idPtr) env->ReleaseIntArrayElements(autoParamIds, idPtr, JNI_ABORT);
        if (cntPtr) env->ReleaseIntArrayElements(autoPointCounts, cntPtr, JNI_ABORT);
        if (bPtr) env->ReleaseFloatArrayElements(autoBeats, bPtr, JNI_ABORT);
        if (vPtr) env->ReleaseFloatArrayElements(autoValues, vPtr, JNI_ABORT);
    }

    engine()->setClipData(track, scene, clip);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeLoadSampleBuffer(
    JNIEnv* env, jobject, jint slot, jint sampleRate, jint channels, jfloatArray pcmData
) {
    if (!pcmData) return;
    jsize len = env->GetArrayLength(pcmData);
    if (len <= 0) return;
    jfloat* ptr = env->GetFloatArrayElements(pcmData, nullptr);
    if (!ptr) return;
    engine()->loadSampleBuffer(slot, sampleRate, channels, ptr, len);
    env->ReleaseFloatArrayElements(pcmData, ptr, JNI_ABORT);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeGetTelemetry(JNIEnv* env, jobject) {
    TransportTelemetry t = engine()->getTelemetry();
    // 40 floats packing telemetry cleanly without object allocation
    jfloat arr[40];
    arr[0] = t.isPlaying ? 1.0f : 0.0f;
    arr[1] = t.isCountingIn ? 1.0f : 0.0f;
    arr[2] = static_cast<float>(t.transportBeat);
    arr[3] = static_cast<float>(t.bar);
    arr[4] = static_cast<float>(t.beat);
    arr[5] = static_cast<float>(t.sixteenth);
    arr[6] = t.masterPeakL;
    arr[7] = t.masterPeakR;
    arr[8] = t.masterClipping ? 1.0f : 0.0f;
    arr[9] = static_cast<float>(t.xrunCount);
    arr[10] = static_cast<float>(t.sampleRate);
    arr[11] = static_cast<float>(t.bufferFrames);
    arr[12] = t.isAAudio ? 1.0f : 0.0f;
    for (int i = 0; i < 8; ++i) arr[13 + i] = t.trackPeaks[i];
    for (int i = 0; i < 8; ++i) arr[21 + i] = static_cast<float>(t.activeClips[i]);
    for (int i = 0; i < 8; ++i) arr[29 + i] = static_cast<float>(t.trackClipBeats[i]);
    arr[37] = static_cast<float>(t.capturedNoteCount);
    arr[38] = t.captureActive ? 1.0f : 0.0f;
    arr[39] = static_cast<float>(t.timeSigNum);

    jfloatArray out = env->NewFloatArray(40);
    env->SetFloatArrayRegion(out, 0, 40, arr);
    return out;
}

JNIEXPORT jintArray JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeGetQueuedClips(JNIEnv* env, jobject) {
    TransportTelemetry t = engine()->getTelemetry();
    jint arr[8];
    for (int i = 0; i < 8; ++i) arr[i] = t.queuedClips[i];
    jintArray out = env->NewIntArray(8);
    env->SetIntArrayRegion(out, 0, 8, arr);
    return out;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeConsumeCapturedNotes(JNIEnv* env, jobject) {
    double startBeat = 0.0;
    auto notes = engine()->consumeCapturedNotes(&startBeat);
    int n = static_cast<int>(notes.size());
    std::vector<jfloat> packed(1 + n * 6);
    packed[0] = static_cast<jfloat>(startBeat);
    for (int i = 0; i < n; ++i) {
        packed[1 + i * 6 + 0] = static_cast<jfloat>(notes[i].trackIndex);
        packed[1 + i * 6 + 1] = static_cast<jfloat>(notes[i].note);
        packed[1 + i * 6 + 2] = static_cast<jfloat>(notes[i].beatOffset);
        packed[1 + i * 6 + 3] = static_cast<jfloat>(notes[i].durationBeats);
        packed[1 + i * 6 + 4] = notes[i].velocity;
        packed[1 + i * 6 + 5] = static_cast<jfloat>(notes[i].transportBeatAtOn);
    }
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(packed.size()));
    env->SetFloatArrayRegion(out, 0, static_cast<jsize>(packed.size()), packed.data());
    return out;
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeClearCaptureBuffer(JNIEnv*, jobject) {
    engine()->clearCaptureBuffer();
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeSetResampling(JNIEnv*, jobject, jboolean enabled) {
    engine()->setResamplingEnabled(enabled == JNI_TRUE);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeDrainResampled(JNIEnv* env, jobject, jint maxFloats) {
    std::vector<float> buf(std::max(1, static_cast<int>(maxFloats)));
    int got = engine()->drainResampledInterleaved(buf.data(), static_cast<int>(buf.size()));
    jfloatArray out = env->NewFloatArray(got);
    if (got > 0) env->SetFloatArrayRegion(out, 0, got, buf.data());
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeRenderOfflineWav(
    JNIEnv* env, jobject, jstring jPath, jint bitDepth, jint sampleRate,
    jint scopeMode, jint sceneIndex, jint trackIndex, jdouble totalBeats
) {
    const char* cPath = env->GetStringUTFChars(jPath, nullptr);
    std::string path(cPath ? cPath : "");
    env->ReleaseStringUTFChars(jPath, cPath);
    gExportProgress.store(0.0f, std::memory_order_relaxed);
    gExportCancel.store(false, std::memory_order_relaxed);
    bool ok = engine()->renderOfflineWav(
        path, bitDepth, sampleRate, scopeMode, sceneIndex, trackIndex, totalBeats,
        &gExportProgress, &gExportCancel
    );
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeGetExportProgress(JNIEnv*, jobject) {
    return gExportProgress.load(std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeCancelExport(JNIEnv*, jobject) {
    gExportCancel.store(true, std::memory_order_release);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeAttachMidiInput(JNIEnv* env, jobject, jobject midiDevice, jint portNumber) {
    engine()->attachMidiInputDevice(env, midiDevice, portNumber);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeDetachMidiInput(JNIEnv*, jobject) {
    engine()->detachMidiInputDevice();
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeAttachMidiOutput(JNIEnv* env, jobject, jobject midiDevice, jint portNumber) {
    engine()->attachMidiOutputDevice(env, midiDevice, portNumber);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeDetachMidiOutput(JNIEnv*, jobject) {
    engine()->detachMidiOutputDevice();
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeSendMidiBytes(JNIEnv* env, jobject, jbyteArray data) {
    if (!data) return;
    jsize len = env->GetArrayLength(data);
    if (len <= 0) return;
    jbyte* ptr = env->GetByteArrayElements(data, nullptr);
    if (!ptr) return;
    engine()->sendMidiBytes(reinterpret_cast<const uint8_t*>(ptr), len);
    env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_example_core_audio_NativeAudioEngine_nativeSetMidiRouting(JNIEnv*, jobject, jint targetTrack, jint channel) {
    engine()->setMidiTargetTrack(targetTrack);
    engine()->setMidiFilterChannel(channel);
}

} // extern "C"
