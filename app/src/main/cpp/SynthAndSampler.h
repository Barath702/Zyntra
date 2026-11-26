#pragma once

#include "DspCommon.h"
#include "EffectsDsp.h"
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>

#include <mutex>

namespace notecraft {

constexpr int kMaxSampleSlots = 256;

class SamplePool {
public:
    ~SamplePool() {
        for (int i = 0; i < kMaxSampleSlots; ++i) {
            delete slots_[i].exchange(nullptr, std::memory_order_acq_rel);
        }
        std::lock_guard<std::mutex> lock(poolMutex_);
        for (auto* ptr : retired_) {
            delete ptr;
        }
        retired_.clear();
    }

    void setSample(int slot, int sampleRate, int channels, const float* data, int numSamples) {
        if (slot < 0 || slot >= kMaxSampleSlots || !data || numSamples <= 0) return;
        auto* buf = new SampleBuffer();
        buf->sampleRate = sampleRate > 0 ? sampleRate : 48000;
        buf->channels = (channels == 2) ? 2 : 1;
        buf->data.assign(data, data + numSamples);
        const SampleBuffer* old = slots_[slot].exchange(buf, std::memory_order_release);
        if (old) {
            std::lock_guard<std::mutex> lock(poolMutex_);
            retired_.push_back(old);
            if (retired_.size() > 256) {
                delete retired_.front();
                retired_.erase(retired_.begin());
            }
        }
    }

    const SampleBuffer* get(int slot) const noexcept {
        if (slot < 0 || slot >= kMaxSampleSlots) return nullptr;
        return slots_[slot].load(std::memory_order_acquire);
    }

private:
    std::array<std::atomic<const SampleBuffer*>, kMaxSampleSlots> slots_{};
    std::vector<const SampleBuffer*> retired_;
    std::mutex poolMutex_;
};

// ---------------- ADSR Envelope ----------------
class AdsrEnv {
public:
    enum class Stage { Idle, Attack, Decay, Sustain, Release };

    void trigger() noexcept {
        stage_ = Stage::Attack;
    }

    void release() noexcept {
        if (stage_ != Stage::Idle) {
            stage_ = Stage::Release;
        }
    }

    void reset() noexcept {
        stage_ = Stage::Idle;
        value_ = 0.0f;
    }

    bool isActive() const noexcept { return stage_ != Stage::Idle; }

    float process(float sr, float aSec, float dSec, float sus, float rSec) noexcept {
        switch (stage_) {
            case Stage::Idle:
                value_ = 0.0f;
                return 0.0f;
            case Stage::Attack: {
                float step = 1.0f / std::max(1.0f, aSec * sr);
                value_ += step;
                if (value_ >= 1.0f) {
                    value_ = 1.0f;
                    stage_ = Stage::Decay;
                }
                return value_;
            }
            case Stage::Decay: {
                float coeff = std::exp(-4.0f / std::max(1.0f, dSec * sr));
                value_ = sus + (value_ - sus) * coeff;
                if (std::fabs(value_ - sus) < 0.001f) {
                    value_ = sus;
                    stage_ = Stage::Sustain;
                }
                return value_;
            }
            case Stage::Sustain:
                value_ = sus;
                return value_;
            case Stage::Release: {
                float coeff = std::exp(-4.5f / std::max(1.0f, rSec * sr));
                value_ *= coeff;
                if (value_ < 0.0005f) {
                    value_ = 0.0f;
                    stage_ = Stage::Idle;
                }
                return value_;
            }
        }
        return 0.0f;
    }

private:
    Stage stage_ = Stage::Idle;
    float value_ = 0.0f;
};

// ---------------- Subtractive Synthesizer ----------------
class SubtractiveSynth {
public:
    void init(float sampleRate) noexcept {
        sampleRate_ = sampleRate;
        reset();
    }

    void reset() noexcept {
        for (auto& v : voices_) v = Voice{};
        lfoPhase_ = 0.0f;
        lastMidiNote_ = -1.0f;
    }

    void setParams(const SynthParams& p) noexcept { params_ = p; }
    void setPitchBend(float semitones) noexcept { pitchBend_ = semitones; }
    void setModWheel(float amt) noexcept { modWheel_ = clampf(amt, 0.0f, 1.0f); }
    void setSustain(bool down) noexcept {
        sustainDown_ = down;
        if (!down) {
            for (auto& v : voices_) {
                if (v.active && v.sustained) {
                    v.ampEnv.release();
                    v.filtEnv.release();
                    v.sustained = false;
                }
            }
        }
    }

    void noteOn(int midiNote, float velocity, int maxPolyphony, bool isPreview = false) noexcept {
        int limit = std::min(kMaxVoices, std::max(4, maxPolyphony));
        int chosen = -1;
        uint64_t oldestAge = UINT64_MAX;
        for (int i = 0; i < limit; ++i) {
            if (voices_[i].active && voices_[i].midiNote == midiNote && voices_[i].isPreview == isPreview) {
                chosen = i;
                break;
            }
            if (!voices_[i].active) {
                chosen = i;
                break;
            }
            if (voices_[i].age < oldestAge) {
                oldestAge = voices_[i].age;
                chosen = i;
            }
        }
        if (chosen < 0) chosen = 0;

        auto& v = voices_[chosen];
        v.active = true;
        v.isPreview = isPreview;
        v.midiNote = midiNote;
        v.targetNote = static_cast<float>(midiNote);
        if (lastMidiNote_ < 0.0f || params_.glide < 0.01f) {
            v.currentNote = v.targetNote;
        } else {
            v.currentNote = lastMidiNote_;
        }
        lastMidiNote_ = v.targetNote;
        v.velocity = clampf(velocity, 0.05f, 1.0f);
        v.sustained = false;
        v.age = ++ageCounter_;
        v.ampEnv.trigger();
        v.filtEnv.trigger();
    }

    void noteOff(int midiNote, bool previewOnly = false) noexcept {
        for (auto& v : voices_) {
            if (v.active && v.midiNote == midiNote && (!previewOnly || v.isPreview)) {
                if (sustainDown_ && !v.isPreview) {
                    v.sustained = true;
                } else {
                    v.ampEnv.release();
                    v.filtEnv.release();
                }
            }
        }
    }

    void releasePreviewNotes() noexcept {
        for (auto& v : voices_) {
            if (v.active && v.isPreview) {
                v.ampEnv.release();
                v.filtEnv.release();
                v.sustained = false;
            }
        }
    }

    void allNotesOff() noexcept {
        for (auto& v : voices_) {
            if (v.active) {
                v.ampEnv.release();
                v.filtEnv.release();
                v.sustained = false;
            }
        }
    }

    void process(float* outL, float* outR, int numFrames, int maxPolyphony) noexcept {
        float lfoRateHz = 0.15f + params_.lfoRate * 14.0f;
        float lfoInc = lfoRateHz / sampleRate_;
        float totalLfoDepth = clampf(params_.lfoDepth + modWheel_ * 0.45f, 0.0f, 1.0f);

        float atk = 0.002f + params_.attack * 1.5f;
        float dec = 0.02f + params_.decay * 2.0f;
        float sus = clampf(params_.sustain, 0.0f, 1.0f);
        float rel = 0.01f + params_.release * 2.5f;
        float fDec = 0.02f + params_.filterDecay * 2.0f;

        float glideCoeff = (params_.glide < 0.01f)
            ? 1.0f
            : (1.0f - std::exp(-1.0f / (sampleRate_ * (0.005f + params_.glide * 0.35f))));

        int limit = std::min(kMaxVoices, std::max(4, maxPolyphony));
        float unisonAmt = params_.unison;

        for (int i = 0; i < numFrames; ++i) {
            float lfo = std::sin(kTwoPi * lfoPhase_);
            lfoPhase_ += lfoInc;
            if (lfoPhase_ >= 1.0f) lfoPhase_ -= 1.0f;

            float sumL = 0.0f;
            float sumR = 0.0f;

            for (int vi = 0; vi < limit; ++vi) {
                auto& v = voices_[vi];
                if (!v.active) continue;

                float envAmp = v.ampEnv.process(sampleRate_, atk, dec, sus, rel);
                float envFilt = v.filtEnv.process(sampleRate_, 0.003f, fDec, 0.1f, rel);
                if (!v.ampEnv.isActive()) {
                    v.active = false;
                    continue;
                }

                v.currentNote += (v.targetNote - v.currentNote) * glideCoeff;
                float pitchMod = pitchBend_;
                if (params_.lfoTarget == 1) pitchMod += lfo * totalLfoDepth * 2.0f;

                float baseFreq = midiToFreq(v.currentNote + pitchMod);
                float dt1 = clampf(baseFreq / sampleRate_, 0.0001f, 0.45f);
                float detuneSemi = (params_.osc2Detune - 0.5f) * 2.0f; // -1..+1 semitone
                float freq2 = midiToFreq(v.currentNote + pitchMod + detuneSemi);
                float dt2 = clampf(freq2 / sampleRate_, 0.0001f, 0.45f);

                float osc1 = renderOsc(v.phase1, dt1, params_.osc1Wave);
                float osc2 = renderOsc(v.phase2, dt2, params_.osc2Wave);
                v.phase1 += dt1; if (v.phase1 >= 1.0f) v.phase1 -= 1.0f;
                v.phase2 += dt2; if (v.phase2 >= 1.0f) v.phase2 -= 1.0f;

                // Optional unison detuned voice pair
                float uniL = 0.0f, uniR = 0.0f;
                if (unisonAmt > 0.05f) {
                    float dtU1 = clampf(midiToFreq(v.currentNote + pitchMod + unisonAmt * 0.18f) / sampleRate_, 0.0001f, 0.45f);
                    float dtU2 = clampf(midiToFreq(v.currentNote + pitchMod - unisonAmt * 0.18f) / sampleRate_, 0.0001f, 0.45f);
                    uniL = renderOsc(v.phaseU1, dtU1, params_.osc1Wave) * unisonAmt * 0.5f;
                    uniR = renderOsc(v.phaseU2, dtU2, params_.osc1Wave) * unisonAmt * 0.5f;
                    v.phaseU1 += dtU1; if (v.phaseU1 >= 1.0f) v.phaseU1 -= 1.0f;
                    v.phaseU2 += dtU2; if (v.phaseU2 >= 1.0f) v.phaseU2 -= 1.0f;
                }

                // Sub oscillator (-1 octave)
                float dtSub = dt1 * 0.5f;
                float sub = std::sin(kTwoPi * v.phaseSub) * params_.subLevel;
                v.phaseSub += dtSub; if (v.phaseSub >= 1.0f) v.phaseSub -= 1.0f;

                // White noise
                noiseSeed_ = noiseSeed_ * 1664525u + 1013904223u;
                float noise = (static_cast<float>(noiseSeed_ >> 9) / 4194304.0f - 1.0f) * params_.noiseLevel * 0.4f;

                float monoOsc = osc1 * (1.0f - params_.oscMix) + osc2 * params_.oscMix + sub + noise;

                // Wave fold & warp shaping
                if (params_.warp > 0.01f) {
                    monoOsc = fastTanh(monoOsc * (1.0f + params_.warp * 4.0f));
                }
                if (params_.fold > 0.01f) {
                    monoOsc = std::sin(monoOsc * (1.0f + params_.fold * 4.5f));
                }

                float sigL = monoOsc + uniL;
                float sigR = monoOsc + uniR;

                // SVF Filter
                float cutoffNorm = clampf(params_.cutoff + envFilt * params_.filterEnvAmt * 0.65f +
                    (params_.lfoTarget == 0 ? lfo * totalLfoDepth * 0.35f : 0.0f), 0.01f, 0.99f);
                float fc = clampf(normalizedToCutoffHz(cutoffNorm), 35.0f, sampleRate_ * 0.42f);
                float g = std::tan(kPi * fc / sampleRate_);
                float q = 0.6f + params_.resonance * 8.0f;
                float k = 1.0f / q;
                float a1 = 1.0f / (1.0f + g * (g + k));
                float a2 = g * a1;
                float a3 = g * a2;

                auto filterCh = [&](float x, float& ic1, float& ic2) {
                    float v3 = x - ic2;
                    float v1 = a1 * ic1 + a2 * v3;
                    float v2 = ic2 + a2 * ic1 + a3 * v3;
                    ic1 = 2.0f * v1 - ic1;
                    ic2 = 2.0f * v2 - ic2;
                    if (params_.filterType == 1) return x - k * v1 - v2; // HP
                    if (params_.filterType == 2) return v1;              // BP
                    return v2;                                           // LP
                };

                sigL = filterCh(sigL, v.ic1L, v.ic2L);
                sigR = filterCh(sigR, v.ic1R, v.ic2R);

                float ampMod = (params_.lfoTarget == 2) ? (1.0f - totalLfoDepth * 0.5f * (0.5f - 0.5f * lfo)) : 1.0f;
                float gain = envAmp * v.velocity * ampMod * 0.25f;
                sumL += sigL * gain;
                sumR += sigR * gain;
            }

            outL[i] += sumL;
            outR[i] += sumR;
        }
    }

private:
    struct Voice {
        bool active = false;
        bool isPreview = false;
        int midiNote = 60;
        float currentNote = 60.0f;
        float targetNote = 60.0f;
        float velocity = 0.8f;
        bool sustained = false;
        uint64_t age = 0;
        float phase1 = 0.0f, phase2 = 0.0f, phaseSub = 0.0f, phaseU1 = 0.0f, phaseU2 = 0.0f;
        float ic1L = 0.0f, ic2L = 0.0f, ic1R = 0.0f, ic2R = 0.0f;
        AdsrEnv ampEnv{};
        AdsrEnv filtEnv{};
    };

    float sampleRate_ = 48000.0f;
    SynthParams params_{};
    std::array<Voice, kMaxVoices> voices_{};
    uint64_t ageCounter_ = 0;
    uint32_t noiseSeed_ = 0x12345678u;
    float lfoPhase_ = 0.0f;
    float lastMidiNote_ = -1.0f;
    float pitchBend_ = 0.0f;
    float modWheel_ = 0.0f;
    bool sustainDown_ = false;

    static float renderOsc(float p, float dt, int wave) noexcept {
        switch (wave) {
            case 0: { // Saw
                float s = 2.0f * p - 1.0f;
                return s - polyBlep(p, dt);
            }
            case 1: { // Square
                float s = (p < 0.5f) ? 1.0f : -1.0f;
                s += polyBlep(p, dt);
                s -= polyBlep(std::fmod(p + 0.5f, 1.0f), dt);
                return s;
            }
            case 2: // Triangle
                return 4.0f * std::fabs(p - 0.5f) - 1.0f;
            default: // Sine
                return std::sin(kTwoPi * p);
        }
    }
};

// ---------------- Melodic Sampler ----------------
class MelodicSampler {
public:
    void init(float sampleRate, const SamplePool* pool) noexcept {
        sampleRate_ = sampleRate;
        pool_ = pool;
        reset();
    }

    void reset() noexcept {
        for (auto& v : voices_) v = Voice{};
    }

    void setParams(const SamplerParams& p) noexcept { params_ = p; }
    void setPitchBend(float semitones) noexcept { pitchBend_ = semitones; }
    void setSustain(bool down) noexcept {
        sustainDown_ = down;
        if (!down) {
            for (auto& v : voices_) {
                if (v.active && v.sustained) {
                    v.env.release();
                    v.sustained = false;
                }
            }
        }
    }

    void noteOn(int midiNote, float velocity, int maxPolyphony, bool isPreview = false) noexcept {
        int limit = std::min(kMaxVoices, std::max(4, maxPolyphony));
        int chosen = -1;
        uint64_t oldest = UINT64_MAX;
        for (int i = 0; i < limit; ++i) {
            if (!voices_[i].active) { chosen = i; break; }
            if (voices_[i].age < oldest) { oldest = voices_[i].age; chosen = i; }
        }
        if (chosen < 0) chosen = 0;
        const SampleBuffer* buf = pool_ ? pool_->get(params_.sampleSlot) : nullptr;
        auto& v = voices_[chosen];
        v.active = true;
        v.isPreview = isPreview;
        v.midiNote = midiNote;
        v.velocity = clampf(velocity, 0.05f, 1.0f);
        v.sustained = false;
        v.age = ++ageCounter_;
        v.env.trigger();
        if (buf && !buf->data.empty()) {
            int totalFrames = static_cast<int>(buf->data.size()) / std::max(1, buf->channels);
            if (totalFrames >= 2) {
                float sNorm = clampf(params_.startNorm, 0.0f, 0.98f);
                float eNorm = clampf(params_.endNorm, sNorm + 0.01f, 1.0f);
                v.pos = params_.reverse ? (eNorm * (totalFrames - 1)) : (sNorm * (totalFrames - 1));
            } else {
                v.pos = 0.0;
                v.active = false;
            }
        } else {
            v.pos = 0.0;
            v.active = false;
        }
        v.ic1L = v.ic2L = v.ic1R = v.ic2R = 0.0f;
    }

    void noteOff(int midiNote, bool previewOnly = false) noexcept {
        for (auto& v : voices_) {
            if (v.active && v.midiNote == midiNote && (!previewOnly || v.isPreview)) {
                if (sustainDown_ && !v.isPreview) v.sustained = true;
                else v.env.release();
            }
        }
    }

    void releasePreviewNotes() noexcept {
        for (auto& v : voices_) {
            if (v.active && v.isPreview) { v.env.release(); v.sustained = false; }
        }
    }

    void allNotesOff() noexcept {
        for (auto& v : voices_) {
            if (v.active) { v.env.release(); v.sustained = false; }
        }
    }

    void process(float* outL, float* outR, int numFrames, int maxPolyphony) noexcept {
        const SampleBuffer* buf = pool_ ? pool_->get(params_.sampleSlot) : nullptr;
        if (!buf || buf->data.empty()) return;

        int totalFrames = static_cast<int>(buf->data.size()) / buf->channels;
        if (totalFrames < 2) return;

        float sNorm = clampf(params_.startNorm, 0.0f, 0.98f);
        float eNorm = clampf(params_.endNorm, sNorm + 0.01f, 1.0f);
        float lStartNorm = clampf(params_.loopStartNorm, sNorm, eNorm - 0.01f);
        float lEndNorm = clampf(params_.loopEndNorm, lStartNorm + 0.01f, eNorm);
        double startFrame = sNorm * (totalFrames - 1);
        double endFrame = eNorm * (totalFrames - 1);
        double loopStart = lStartNorm * (totalFrames - 1);
        double loopEnd = lEndNorm * (totalFrames - 1);

        float atk = 0.002f + params_.attack * 1.2f;
        float dec = 0.02f + params_.decay * 2.0f;
        float sus = clampf(params_.sustain, 0.0f, 1.0f);
        float rel = 0.01f + params_.release * 2.0f;

        float fc = clampf(normalizedToCutoffHz(params_.cutoff), 50.0f, sampleRate_ * 0.42f);
        float g = std::tan(kPi * fc / sampleRate_);
        float k = 1.0f / (0.6f + params_.resonance * 7.0f);
        float a1 = 1.0f / (1.0f + g * (g + k));
        float a2 = g * a1;
        float a3 = g * a2;

        int limit = std::min(kMaxVoices, std::max(4, maxPolyphony));
        float srRatio = static_cast<float>(buf->sampleRate) / sampleRate_;

        for (int i = 0; i < numFrames; ++i) {
            float sumL = 0.0f, sumR = 0.0f;
            for (int vi = 0; vi < limit; ++vi) {
                auto& v = voices_[vi];
                if (!v.active) continue;

                float env = v.env.process(sampleRate_, atk, dec, sus, rel);
                if (!v.env.isActive()) { v.active = false; continue; }

                double pitchRatio = std::pow(2.0, (v.midiNote - params_.rootNote + params_.pitchSemitones + pitchBend_) / 12.0);
                double step = pitchRatio * params_.playbackRate * srRatio * (params_.reverse ? -1.0 : 1.0);

                if (v.pos < startFrame || v.pos >= endFrame) {
                    if (params_.loopEnabled && loopEnd > loopStart + 1.0) {
                        v.pos = params_.reverse ? loopEnd : loopStart;
                    } else {
                        v.active = false;
                        continue;
                    }
                }

                int idx0 = std::min(totalFrames - 2, std::max(0, static_cast<int>(v.pos)));
                float frac = static_cast<float>(v.pos - idx0);
                float l = 0.0f, r = 0.0f;
                if (buf->channels == 1) {
                    float s = buf->data[idx0] * (1.0f - frac) + buf->data[idx0 + 1] * frac;
                    l = r = s;
                } else {
                    l = buf->data[idx0 * 2] * (1.0f - frac) + buf->data[(idx0 + 1) * 2] * frac;
                    r = buf->data[idx0 * 2 + 1] * (1.0f - frac) + buf->data[(idx0 + 1) * 2 + 1] * frac;
                }

                if (params_.cutoff < 0.99f) {
                    auto filt = [&](float x, float& ic1, float& ic2) {
                        float v3 = x - ic2;
                        float v1 = a1 * ic1 + a2 * v3;
                        float v2 = ic2 + a2 * ic1 + a3 * v3;
                        ic1 = 2.0f * v1 - ic1;
                        ic2 = 2.0f * v2 - ic2;
                        return v2;
                    };
                    l = filt(l, v.ic1L, v.ic2L);
                    r = filt(r, v.ic1R, v.ic2R);
                }

                float gain = env * v.velocity * params_.gain * 0.7f;
                sumL += l * gain;
                sumR += r * gain;

                v.pos += step;
                if (params_.loopEnabled && loopEnd > loopStart + 1.0) {
                    if (!params_.reverse && v.pos >= loopEnd) v.pos = loopStart + (v.pos - loopEnd);
                    else if (params_.reverse && v.pos <= loopStart) v.pos = loopEnd - (loopStart - v.pos);
                }
            }
            outL[i] += sumL;
            outR[i] += sumR;
        }
    }

private:
    struct Voice {
        bool active = false;
        bool isPreview = false;
        int midiNote = 60;
        float velocity = 0.8f;
        bool sustained = false;
        uint64_t age = 0;
        double pos = 0.0;
        float ic1L = 0.0f, ic2L = 0.0f, ic1R = 0.0f, ic2R = 0.0f;
        AdsrEnv env{};
    };

    float sampleRate_ = 48000.0f;
    const SamplePool* pool_ = nullptr;
    SamplerParams params_{};
    std::array<Voice, kMaxVoices> voices_{};
    uint64_t ageCounter_ = 0;
    float pitchBend_ = 0.0f;
    bool sustainDown_ = false;
};

// ---------------- 16-Pad Drum Rack ----------------
class DrumRackEngine {
public:
    void init(float sampleRate, const SamplePool* pool) noexcept {
        sampleRate_ = sampleRate;
        pool_ = pool;
        for (int i = 0; i < kMaxDrumPads; ++i) {
            padVoices_[i] = PadVoice{};
            padFiltersL_[i].reset();
            padFiltersR_[i].reset();
            for (int e = 0; e < kMaxEffects; ++e) {
                padEffects_[i][e].init(sampleRate);
            }
        }
    }

    void reset() noexcept {
        for (int i = 0; i < kMaxDrumPads; ++i) {
            padVoices_[i].active = false;
            for (int e = 0; e < kMaxEffects; ++e) {
                padEffects_[i][e].reset();
            }
        }
    }

    void setPadParams(int pad, const DrumPadParams& p) noexcept {
        if (pad < 0 || pad >= kMaxDrumPads) return;
        padParams_[pad] = p;
    }

    void clearExternalPointers() noexcept {
        for (int i = 0; i < kMaxDrumPads; ++i) {
            for (int e = 0; e < kMaxEffects; ++e) {
                padEffects_[i][e].clearExternalPointerOnly();
            }
        }
    }

    void destroyExternalBuffers() noexcept {
        for (int i = 0; i < kMaxDrumPads; ++i) {
            for (int e = 0; e < kMaxEffects; ++e) {
                padEffects_[i][e].destroyExternalBuffers();
            }
        }
    }

    void setPadEffect(int pad, int slot, const EffectConfig& cfg) {
        if (pad < 0 || pad >= kMaxDrumPads || slot < 0 || slot >= kMaxEffects) return;
        padEffects_[pad][slot].setConfig(cfg);
    }

    EffectConfig getPadEffectConfig(int pad, int slot) const noexcept {
        if (pad < 0 || pad >= kMaxDrumPads || slot < 0 || slot >= kMaxEffects) return EffectConfig{};
        return padEffects_[pad][slot].getConfig();
    }

    void prepareLocalEffects() {
        for (int i = 0; i < kMaxDrumPads; ++i) {
            for (int e = 0; e < kMaxEffects; ++e) {
                padEffects_[i][e].setConfig(padEffects_[i][e].getConfig());
            }
        }
    }

    void applyRtPadEffect(int pad, int slot, const EffectConfig& cfg, EffectScratchBuffers* preBufs, EffectScratchBuffers*& retiredOut) noexcept {
        if (pad < 0 || pad >= kMaxDrumPads || slot < 0 || slot >= kMaxEffects) {
            retiredOut = preBufs;
            return;
        }
        padEffects_[pad][slot].applyRtConfig(cfg, preBufs, retiredOut);
    }

    void triggerPad(int pad, float velocity, float pitchOffsetSemi = 0.0f) noexcept {
        if (pad < 0 || pad >= kMaxDrumPads) return;
        const auto& p = padParams_[pad];
        if (p.muted) return;

        if (p.chokeGroup > 0) {
            for (int i = 0; i < kMaxDrumPads; ++i) {
                if (i != pad && padParams_[i].chokeGroup == p.chokeGroup) {
                    padVoices_[i].active = false;
                }
            }
        }

        const SampleBuffer* buf = pool_ ? pool_->get(p.sampleSlot) : nullptr;
        auto& v = padVoices_[pad];
        v.active = true;
        v.velocity = clampf(velocity, 0.05f, 1.0f);
        v.pitchOffset = pitchOffsetSemi;
        v.elapsedSec = 0.0f;
        v.synthPhase = 0.0f;
        if (buf && !buf->data.empty()) {
            int totalFrames = static_cast<int>(buf->data.size()) / std::max(1, buf->channels);
            if (totalFrames >= 2) {
                float sNorm = clampf(p.startNorm, 0.0f, 0.98f);
                float eNorm = clampf(p.endNorm, sNorm + 0.01f, 1.0f);
                v.pos = p.reverse ? (eNorm * (totalFrames - 1)) : (sNorm * (totalFrames - 1));
                v.usingSynthFallback = false;
            } else {
                v.pos = 0.0;
                v.usingSynthFallback = true;
            }
        } else {
            v.pos = 0.0;
            v.usingSynthFallback = true;
        }
    }

    void process(float* outL, float* outR, int numFrames, float bpm) noexcept {
        bool anySolo = false;
        for (int i = 0; i < kMaxDrumPads; ++i) {
            if (padParams_[i].soloed) { anySolo = true; break; }
        }

        alignas(16) float padBufL[kMaxBlockFrames];
        alignas(16) float padBufR[kMaxBlockFrames];

        for (int pad = 0; pad < kMaxDrumPads; ++pad) {
            auto& v = padVoices_[pad];
            if (!v.active) continue;
            const auto& p = padParams_[pad];
            if (p.muted || (anySolo && !p.soloed)) {
                v.active = false;
                continue;
            }

            std::fill(padBufL, padBufL + numFrames, 0.0f);
            std::fill(padBufR, padBufR + numFrames, 0.0f);

            float panAngle = (clampf(p.pan, -1.0f, 1.0f) + 1.0f) * 0.25f * kPi;
            float panL = std::cos(panAngle);
            float panR = std::sin(panAngle);
            float decaySec = 0.04f + p.decay * 2.5f;

            if (!v.usingSynthFallback) {
                const SampleBuffer* buf = pool_ ? pool_->get(p.sampleSlot) : nullptr;
                if (!buf || buf->data.empty()) {
                    v.active = false;
                    continue;
                }
                int totalFrames = static_cast<int>(buf->data.size()) / std::max(1, buf->channels);
                if (totalFrames < 2) {
                    v.active = false;
                    continue;
                }
                float sNorm = clampf(p.startNorm, 0.0f, 0.98f);
                float eNorm = clampf(p.endNorm, sNorm + 0.01f, 1.0f);
                double sFrame = sNorm * (totalFrames - 1);
                double eFrame = eNorm * (totalFrames - 1);
                double rate = std::pow(2.0, (p.pitchSemitones + v.pitchOffset) / 12.0) *
                              (static_cast<double>(buf->sampleRate) / sampleRate_) *
                              (p.reverse ? -1.0 : 1.0);

                for (int i = 0; i < numFrames; ++i) {
                    if (v.pos < sFrame || v.pos >= eFrame) {
                        v.active = false;
                        break;
                    }
                    int idx0 = std::min(totalFrames - 2, std::max(0, static_cast<int>(v.pos)));
                    float frac = static_cast<float>(v.pos - idx0);
                    float l = 0.0f, r = 0.0f;
                    if (buf->channels == 1) {
                        float s = buf->data[idx0] * (1.0f - frac) + buf->data[idx0 + 1] * frac;
                        l = r = s;
                    } else {
                        l = buf->data[idx0 * 2] * (1.0f - frac) + buf->data[(idx0 + 1) * 2] * frac;
                        r = buf->data[idx0 * 2 + 1] * (1.0f - frac) + buf->data[(idx0 + 1) * 2 + 1] * frac;
                    }
                    float env = (p.decay < 0.98f) ? std::exp(-v.elapsedSec / decaySec) : 1.0f;
                    float g = p.volume * v.velocity * env;
                    padBufL[i] = l * g * panL;
                    padBufR[i] = r * g * panR;
                    v.pos += rate;
                    v.elapsedSec += 1.0f / sampleRate_;
                }
            } else {
                // Clean synthesized emergency fallback if buffer not yet assigned
                float freq = 55.0f + pad * 32.0f;
                for (int i = 0; i < numFrames; ++i) {
                    float env = std::exp(-v.elapsedSec / 0.14f);
                    if (env < 0.001f) { v.active = false; break; }
                    float s = std::sin(kTwoPi * v.synthPhase) * env * p.volume * v.velocity * 0.6f;
                    v.synthPhase += freq / sampleRate_;
                    if (v.synthPhase >= 1.0f) v.synthPhase -= 1.0f;
                    padBufL[i] = s * panL;
                    padBufR[i] = s * panR;
                    v.elapsedSec += 1.0f / sampleRate_;
                }
            }

            if (p.toneFilter < 0.98f) {
                float fc = normalizedToCutoffHz(p.toneFilter);
                padFiltersL_[pad].setLowPass(sampleRate_, fc);
                padFiltersR_[pad].setLowPass(sampleRate_, fc);
                for (int i = 0; i < numFrames; ++i) {
                    padBufL[i] = padFiltersL_[pad].process(padBufL[i]);
                    padBufR[i] = padFiltersR_[pad].process(padBufR[i]);
                }
            }

            for (int s = 0; s < kMaxEffects; ++s) {
                padEffects_[pad][s].process(padBufL, padBufR, numFrames, bpm);
            }

            for (int i = 0; i < numFrames; ++i) {
                outL[i] += padBufL[i];
                outR[i] += padBufR[i];
            }
        }
    }

private:
    struct PadVoice {
        bool active = false;
        float velocity = 1.0f;
        float pitchOffset = 0.0f;
        double pos = 0.0;
        float elapsedSec = 0.0f;
        float synthPhase = 0.0f;
        bool usingSynthFallback = false;
    };

    float sampleRate_ = 48000.0f;
    const SamplePool* pool_ = nullptr;
    std::array<DrumPadParams, kMaxDrumPads> padParams_{};
    std::array<PadVoice, kMaxDrumPads> padVoices_{};
    std::array<Biquad, kMaxDrumPads> padFiltersL_{};
    std::array<Biquad, kMaxDrumPads> padFiltersR_{};
    std::array<std::array<DspEffectUnit, kMaxEffects>, kMaxDrumPads> padEffects_{};
};

} // namespace notecraft
