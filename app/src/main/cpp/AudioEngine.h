#pragma once

#include "DspCommon.h"
#include "EffectsDsp.h"
#include "LockFreeQueue.h"
#include "SynthAndSampler.h"
#include <oboe/Oboe.h>
#include <amidi/AMidi.h>
#include <array>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace notecraft {

enum class CommandType : int {
    NoteOn = 0,
    NoteOff = 1,
    PadTrigger = 2,
    PadRelease = 3,
    PitchBend = 4,
    ModWheel = 5,
    Sustain = 6,
    Play = 7,
    Stop = 8,
    Restart = 9,
    SetBpm = 10,
    SetSwing = 11,
    SetMetronome = 12,
    SetCountIn = 13,
    SetQuantization = 14,
    SetTimeSig = 15,
    LaunchClip = 16,
    StopTrackClip = 17,
    LaunchScene = 18,
    SetTrackVolume = 19,
    SetTrackPan = 20,
    SetTrackMute = 21,
    SetTrackSolo = 22,
    SetMasterVolume = 23,
    SetNoteRepeat = 24,
    SetPerformanceProfile = 25,
    SetCaptureRecord = 26,
    AllNotesOff = 27,
    SetCaptureTimeout = 28,
    PreviewNoteOn = 29,
    PreviewNoteOff = 30,
    PreviewPadTrigger = 31,
    PreviewSampleSlot = 32,
    ClearCapture = 33,
    SetCaptureArmed = 34,
    ReleasePreviewNotes = 35
};

struct EngineCommand {
    CommandType type = CommandType::NoteOn;
    int track = 0;
    int arg1 = 0;
    int arg2 = 0;
    float f1 = 0.0f;
    float f2 = 0.0f;
};

struct RepeatHeldNote {
    bool active = false;
    int note = -1;
    float velocity = 0.85f;
    float pitchOffset = 0.0f;
    bool isPad = false;
};

struct TrackState {
    bool enabled = false;
    TrackType type = TrackType::DrumRack;
    float volume = 0.85f;
    float pan = 0.0f;
    bool muted = false;
    bool soloed = false;

    int activeClip = -1;
    int queuedClip = -1;
    double queuedAtBeat = -1.0;
    double clipOriginBeat = 0.0;
    double clipLocalBeat = 0.0;
    double prevClipLocalBeat = -1.0;

    bool noteRepeatEnabled = false;
    double noteRepeatIntervalBeats = 0.25; // 1/16
    static constexpr int kMaxRepeatNotes = 8;
    std::array<RepeatHeldNote, kMaxRepeatNotes> noteRepeatHeld{};
    double nextRepeatBeat = 0.0;

    SubtractiveSynth synth{};
    MelodicSampler sampler{};
    DrumRackEngine drumRack{};
    std::array<DspEffectUnit, kMaxEffects> effects{};
    std::array<ClipData, kMaxScenes> clips{};

    float peakL = 0.0f;
    float peakR = 0.0f;
};

struct EngineKernel {
    float sampleRate = 48000.0f;
    float bpm = 120.0f;
    int timeSigNum = 4;
    int timeSigDen = 4;
    float swing = 0.0f; // 0..1
    bool metronomeEnabled = false;
    int countInBars = 0;
    QuantizeMode globalQuantization = QuantizeMode::OneBar;
    PerformanceProfile profile = PerformanceProfile::Balanced;

    bool isPlaying = false;
    bool isCountingIn = false;
    double countInRemainingBeats = 0.0;
    double transportBeat = 0.0;
    double freeClockBeat = 0.0; // Monotonic beat clock across stopped & playing states
    int lastMetronomeBeat = -1;
    float metroPhase = 0.0f;
    float metroEnv = 0.0f;
    bool metroAccent = false;

    float masterVolume = 0.9f;
    std::array<DspEffectUnit, kMaxEffects> masterEffects{};
    std::array<TrackState, kMaxTracks> tracks{};

    float masterPeakL = 0.0f;
    float masterPeakR = 0.0f;
    bool masterClipping = false;

    // Live & retrospective capture buffer
    static constexpr int kMaxCaptured = 256;
    std::array<CapturedNote, kMaxCaptured> captured{};
    int capturedCount = 0;
    double captureStartBeat = 0.0;
    bool captureActive = false;
    bool captureArmed = true;
    double captureIdleSeconds = 0.0;
    float captureTimeoutSeconds = 12.0f;

    // Dedicated non-mutating sample preview player (used by Sample Browser)
    struct SamplePreviewVoice {
        bool active = false;
        int slot = -1;
        double pos = 0.0;
        float gain = 0.85f;
    };
    SamplePreviewVoice previewVoice_{};
    const SamplePool* poolPtr_ = nullptr;

    void init(float sr, const SamplePool* pool) noexcept;
    void clearExternalEffectPointers() noexcept;
    void destroyExternalEffectBuffers() noexcept;
    void clearCaptureInternal() noexcept;
    void resetTransport() noexcept;
    int getMaxPolyphony() const noexcept;
    void applyCommand(const EngineCommand& cmd) noexcept;
    void processBlock(float* outInterleaved, int numFrames, int channels) noexcept;
    void finalizeOpenCaptureNotes() noexcept;

private:
    double getQuantizeBoundary(double currentBeat) const noexcept;
    void scheduleClipNotes(TrackState& tr, int trackIdx, double beatStart, double beatEnd, double loopStart, double loopEnd, double clipLen) noexcept;
    void applyTrackAutomation(TrackState& tr, double clipBeat) noexcept;
    void triggerTrackNote(TrackState& tr, int note, float vel, bool isPreview = false) noexcept;
    void releaseTrackNote(TrackState& tr, int note, bool previewOnly = false) noexcept;
    void recordCaptureNoteOn(int trackIdx, int note, float vel) noexcept;
    void recordCaptureNoteOff(int trackIdx, int note) noexcept;
    int countOpenCaptureNotes() const noexcept;
    void registerNoteRepeatHold(TrackState& tr, int note, float vel, float pitchOff, bool isPad) noexcept;
    void unregisterNoteRepeatHold(TrackState& tr, int note, bool isPad) noexcept;
    void clearNoteRepeatHolds(TrackState& tr) noexcept;
};

struct TransportTelemetry {
    bool isPlaying = false;
    bool isCountingIn = false;
    double transportBeat = 0.0;
    int timeSigNum = 4;
    int bar = 1;
    int beat = 1;
    int sixteenth = 1;
    float masterPeakL = 0.0f;
    float masterPeakR = 0.0f;
    bool masterClipping = false;
    std::array<float, kMaxTracks> trackPeaks{};
    std::array<int, kMaxTracks> activeClips{};
    std::array<int, kMaxTracks> queuedClips{};
    std::array<double, kMaxTracks> trackClipBeats{};
    int capturedNoteCount = 0;
    bool captureActive = false;
    int xrunCount = 0;
    int sampleRate = 48000;
    int bufferFrames = 256;
    bool isAAudio = true;
};

class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine() override;

    bool startStream(int sampleRate, int bufferFrames);
    void stopStream();
    void restartStream();

    void postCommand(const EngineCommand& cmd) noexcept;

    // Control-thread safe state updates (pushed via lock-free pointer or synced when stream stopped)
    void setTrackEnabled(int track, bool enabled, int trackType);
    void setSynthParams(int track, const SynthParams& p);
    void setSamplerParams(int track, const SamplerParams& p);
    void setDrumPadParams(int track, int pad, const DrumPadParams& p);
    void setTrackEffect(int track, int slot, const EffectConfig& cfg);
    void setDrumPadEffect(int track, int pad, int slot, const EffectConfig& cfg);
    void setMasterEffect(int slot, const EffectConfig& cfg);
    void setClipData(int track, int scene, const ClipData& clip);
    void loadSampleBuffer(int slot, int sampleRate, int channels, const float* data, int numSamples);

    // Capture buffer retrieval
    std::vector<CapturedNote> consumeCapturedNotes(double* outStartBeat);
    void clearCaptureBuffer();

    // Resampling (post-master internal audio capture)
    void setResamplingEnabled(bool enabled);
    int drainResampledInterleaved(float* dst, int maxFloats);

    // Offline WAV Export (uses isolated EngineKernel so it never disturbs live Oboe)
    bool renderOfflineWav(
        const std::string& outputPath,
        int bitDepth,        // 16, 24, or 32 (float)
        int sampleRate,
        int scopeMode,       // 0 = Full Set (selected scenes), 1 = Single Scene, 2 = Single Track
        int sceneIndex,
        int trackIndex,
        double totalBeats,
        std::atomic<float>* progressOut,
        std::atomic<bool>* cancelFlag
    );

    // AMidi hardware integration
    void attachMidiInputDevice(JNIEnv* env, jobject midiDevice, int portNumber);
    void detachMidiInputDevice();
    void attachMidiOutputDevice(JNIEnv* env, jobject midiDevice, int portNumber);
    void detachMidiOutputDevice();
    void sendMidiBytes(const uint8_t* bytes, int count);
    void setMidiTargetTrack(int track);
    void setMidiFilterChannel(int channel); // -1 = Omni, 0..15

    TransportTelemetry getTelemetry() const noexcept;

    // Oboe callbacks
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void* audioData,
        int32_t numFrames
    ) override;

    void onErrorAfterClose(oboe::AudioStream* audioStream, oboe::Result error) override;

private:
    enum class StructUpdateType {
        TrackConfig,
        SynthParams,
        SamplerParams,
        DrumPadParams,
        TrackEffect,
        DrumPadEffect,
        MasterEffect,
        ClipData
    };

    struct EffectUpdatePayload {
        EffectConfig cfg{};
        EffectScratchBuffers* preAllocatedBufs = nullptr;
    };

    struct PendingUpdate {
        StructUpdateType type;
        int track = 0;
        int index = 0;
        bool flag = false;
        int intVal = 0;
        void* payload = nullptr;
    };

    struct RetireEntry {
        void* ptr = nullptr;
        void (*deleter)(void*) = nullptr;
    };

    SamplePool samplePool_{};
    EngineKernel kernel_{};
    std::unique_ptr<EngineKernel> shadowKernel_;
    std::mutex shadowMutex_;
    std::mutex retireMutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::mutex streamMutex_;

    int requestedSampleRate_ = 48000;
    int requestedBufferFrames_ = 256;
    std::atomic<int> actualSampleRate_{48000};
    std::atomic<int> actualBufferFrames_{256};
    std::atomic<bool> actualAAudio_{true};
    std::atomic<int> xrunCount_{0};

    SpscRingBuffer<EngineCommand, 2048> cmdQueue_;
    SpscRingBuffer<PendingUpdate, 1024> structQueue_;
    SpscRingBuffer<RetireEntry, 1024> retireQueue_;

    // Telemetry snapshot updated on audio thread
    mutable std::atomic<uint64_t> telemetrySeq_{0};
    std::atomic<bool> tPlaying_{false};
    std::atomic<bool> tCountingIn_{false};
    std::atomic<double> tBeat_{0.0};
    std::atomic<int> tTimeSigNum_{4};
    std::atomic<float> tMasterPeakL_{0.0f};
    std::atomic<float> tMasterPeakR_{0.0f};
    std::atomic<bool> tMasterClip_{false};
    std::array<std::atomic<float>, kMaxTracks> tTrackPeaks_{};
    std::array<std::atomic<int>, kMaxTracks> tActiveClips_{};
    std::array<std::atomic<int>, kMaxTracks> tQueuedClips_{};
    std::array<std::atomic<double>, kMaxTracks> tTrackClipBeats_{};
    std::atomic<int> tCapturedCount_{0};
    std::atomic<bool> tCaptureActive_{false};
    std::atomic<bool> intentionallyStopped_{false};

    // Internal resampling lock-free ring (stereo interleaved floats)
    std::atomic<bool> resamplingEnabled_{false};
    static constexpr size_t kResampleCap = 131072; // power of 2
    SpscRingBuffer<float, kResampleCap> resampleRing_;

    // Capture snapshot
    std::atomic<bool> captureDumpRequested_{false};
    std::atomic<bool> captureDumpReady_{false};
    std::array<CapturedNote, EngineKernel::kMaxCaptured> captureSnapshot_{};
    int captureSnapshotCount_ = 0;
    double captureSnapshotStartBeat_ = 0.0;
    std::atomic<bool> captureClearRequested_{false};

    // AMidi
    AMidiDevice* midiInDevice_ = nullptr;
    std::atomic<AMidiOutputPort*> midiOutPort_{nullptr};
    AMidiDevice* midiOutDevice_ = nullptr;
    AMidiInputPort* midiInPort_ = nullptr;
    std::atomic<int> midiTargetTrack_{0};
    std::atomic<int> midiFilterChannel_{-1};
    std::mutex midiSendMutex_;

    void applyPendingStructUpdates() noexcept;
    void drainRetireQueue();
    void pollHardwareMidi() noexcept;
    void updateTelemetry() noexcept;
};

} // namespace notecraft
