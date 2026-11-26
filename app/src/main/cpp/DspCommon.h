#pragma once

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <vector>

namespace notecraft {

constexpr int kMaxTracks = 8;
constexpr int kMaxScenes = 8;
constexpr int kMaxDrumPads = 16;
constexpr int kMaxVoices = 32;
constexpr int kMaxEffects = 8;
constexpr int kMaxClipNotes = 256;
constexpr int kMaxAutomationPoints = 64;
constexpr int kMaxAutomationLanes = 12;
constexpr int kMaxBlockFrames = 2048;
constexpr float kPi = 3.14159265358979323846f;
constexpr float kTwoPi = 6.28318530717958647692f;

enum class TrackType : int {
    DrumRack = 0,
    SubtractiveSynth = 1,
    MelodicSampler = 2
};

enum class WaveformType : int {
    Saw = 0,
    Square = 1,
    Triangle = 2,
    Sine = 3
};

enum class FilterType : int {
    LowPass = 0,
    HighPass = 1,
    BandPass = 2
};

enum class QuantizeMode : int {
    None = 0,
    Quarter = 1,
    Half = 2,
    OneBar = 3,
    TwoBars = 4,
    FourBars = 5
};

enum class PerformanceProfile : int {
    Low = 0,
    Balanced = 1,
    High = 2
};

enum class EffectType : int {
    None = 0,
    ParametricEQ = 1,
    Compressor = 2,
    Reverb = 3,
    Delay = 4,
    Chorus = 5,
    Phaser = 6,
    Flanger = 7,
    Saturation = 8,
    Distortion = 9,
    Bitcrusher = 10,
    AutoFilter = 11,
    StereoWidener = 12
};

struct EffectConfig {
    EffectType type = EffectType::None;
    bool bypassed = false;
    float wetDry = 0.5f;
    float p[6] = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
};

struct SynthParams {
    int osc1Wave = 0;       // WaveformType
    int osc2Wave = 1;       // WaveformType
    float oscMix = 0.5f;    // 0 = Osc1, 1 = Osc2
    float osc2Detune = 0.1f;// -1..1 (normalized) -> +/- 24 cents + semitones
    float subLevel = 0.25f;
    float noiseLevel = 0.0f;
    float fold = 0.0f;
    float warp = 0.0f;
    int filterType = 0;     // FilterType
    float cutoff = 0.72f;   // normalized 0..1 -> 40Hz..18kHz
    float resonance = 0.22f;// 0..1
    float filterEnvAmt = 0.35f;
    float filterDecay = 0.35f;
    float attack = 0.02f;
    float decay = 0.35f;
    float sustain = 0.75f;
    float release = 0.25f;
    float lfoRate = 0.25f;
    float lfoDepth = 0.0f;
    int lfoTarget = 0;      // 0=Filter, 1=Pitch, 2=Amp
    float unison = 0.0f;    // 0..1
    float glide = 0.0f;     // 0..1
};

struct SamplerParams {
    int sampleSlot = -1;
    int rootNote = 60;
    float startNorm = 0.0f;
    float endNorm = 1.0f;
    bool loopEnabled = false;
    float loopStartNorm = 0.0f;
    float loopEndNorm = 1.0f;
    bool reverse = false;
    float pitchSemitones = 0.0f; // -24..24
    float playbackRate = 1.0f;   // 0.25..2.0
    float gain = 0.85f;
    float cutoff = 1.0f;
    float resonance = 0.1f;
    float attack = 0.01f;
    float decay = 0.4f;
    float sustain = 0.85f;
    float release = 0.25f;
};

struct DrumPadParams {
    int sampleSlot = -1;
    float volume = 0.85f;
    float pan = 0.0f;       // -1..1
    float pitchSemitones = 0.0f;
    float decay = 1.0f;     // 0.05..1.0 (1.0 = full sample)
    float startNorm = 0.0f;
    float endNorm = 1.0f;
    bool reverse = false;
    bool muted = false;
    bool soloed = false;
    int chokeGroup = 0;     // 0 = none, 1..4
    float sendAmount = 0.2f;
    float toneFilter = 0.8f; // 0..1 LP cutoff
};

struct MidiNoteEvent {
    int note = 60;          // 0..127 (or pad 0..15 for drum rack)
    float startBeat = 0.0f;
    float durationBeats = 0.25f;
    float velocity = 0.85f; // 0..1
    float probability = 1.0f;
    float timingOffset = 0.0f; // -0.08..+0.08 beats
};

struct AutomationPoint {
    float beat = 0.0f;
    float value = 0.5f;
};

struct AutomationLane {
    int targetParamId = -1; // 0=Vol, 1=Pan, 2=Cutoff, 3=Reso, 4=FX1Mix, 5=FX2Mix
    int count = 0;
    std::array<AutomationPoint, kMaxAutomationPoints> points{};
};

struct ClipData {
    bool exists = false;
    double lengthBeats = 4.0;
    double loopStartBeats = 0.0;
    double loopEndBeats = 4.0;
    bool loopEnabled = true;
    int noteCount = 0;
    std::array<MidiNoteEvent, kMaxClipNotes> notes{};
    int autoLaneCount = 0;
    std::array<AutomationLane, kMaxAutomationLanes> autoLanes{};
};

struct CapturedNote {
    int trackIndex = 0;
    int note = 60;
    double beatOffset = 0.0;
    double durationBeats = 0.25;
    float velocity = 0.85f;
    double transportBeatAtOn = -1.0;
    double startFreeBeat = 0.0;
    bool isOpen = false;
};

struct SampleBuffer {
    int sampleRate = 48000;
    int channels = 1;
    std::vector<float> data; // interleaved or mono
};

inline float midiToFreq(float midiNote) noexcept {
    return 440.0f * std::pow(2.0f, (midiNote - 69.0f) / 12.0f);
}

inline float clampf(float x, float lo, float hi) noexcept {
    return x < lo ? lo : (x > hi ? hi : x);
}

inline float fastTanh(float x) noexcept {
    float x2 = x * x;
    return x * (27.0f + x2) / (27.0f + 9.0f * x2);
}

inline float polyBlep(float t, float dt) noexcept {
    if (t < dt) {
        t /= dt;
        return t + t - t * t - 1.0f;
    } else if (t > 1.0f - dt) {
        t = (t - 1.0f) / dt;
        return t * t + t + t + 1.0f;
    }
    return 0.0f;
}

inline float normalizedToCutoffHz(float norm) noexcept {
    float clamped = clampf(norm, 0.0f, 1.0f);
    return 35.0f * std::pow(520.0f, clamped); // 35 Hz .. ~18.2 kHz
}

} // namespace notecraft
