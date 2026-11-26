#pragma once

#include "DspCommon.h"
#include <array>
#include <cmath>
#include <cstring>

namespace notecraft {

class Biquad {
public:
    void reset() noexcept { z1 = z2 = 0.0f; }

    void setLowShelf(float sr, float freq, float gainDb, float q = 0.707f) noexcept {
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = kTwoPi * clampf(freq, 20.0f, sr * 0.45f) / sr;
        float cosw = std::cos(w0);
        float sinw = std::sin(w0);
        float alpha = sinw / (2.0f * q);
        float twoSqrtAAlpha = 2.0f * std::sqrt(A) * alpha;
        float a0 = (A + 1.0f) + (A - 1.0f) * cosw + twoSqrtAAlpha;
        b0 = (A * ((A + 1.0f) - (A - 1.0f) * cosw + twoSqrtAAlpha)) / a0;
        b1 = (2.0f * A * ((A - 1.0f) - (A + 1.0f) * cosw)) / a0;
        b2 = (A * ((A + 1.0f) - (A - 1.0f) * cosw - twoSqrtAAlpha)) / a0;
        a1 = (-2.0f * ((A - 1.0f) + (A + 1.0f) * cosw)) / a0;
        a2 = ((A + 1.0f) + (A - 1.0f) * cosw - twoSqrtAAlpha) / a0;
    }

    void setHighShelf(float sr, float freq, float gainDb, float q = 0.707f) noexcept {
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = kTwoPi * clampf(freq, 20.0f, sr * 0.45f) / sr;
        float cosw = std::cos(w0);
        float sinw = std::sin(w0);
        float alpha = sinw / (2.0f * q);
        float twoSqrtAAlpha = 2.0f * std::sqrt(A) * alpha;
        float a0 = (A + 1.0f) - (A - 1.0f) * cosw + twoSqrtAAlpha;
        b0 = (A * ((A + 1.0f) + (A - 1.0f) * cosw + twoSqrtAAlpha)) / a0;
        b1 = (-2.0f * A * ((A - 1.0f) + (A + 1.0f) * cosw)) / a0;
        b2 = (A * ((A + 1.0f) + (A - 1.0f) * cosw - twoSqrtAAlpha)) / a0;
        a1 = (2.0f * ((A - 1.0f) - (A + 1.0f) * cosw)) / a0;
        a2 = ((A + 1.0f) - (A - 1.0f) * cosw - twoSqrtAAlpha) / a0;
    }

    void setPeaking(float sr, float freq, float gainDb, float q = 1.0f) noexcept {
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = kTwoPi * clampf(freq, 20.0f, sr * 0.45f) / sr;
        float cosw = std::cos(w0);
        float sinw = std::sin(w0);
        float alpha = sinw / (2.0f * clampf(q, 0.2f, 10.0f));
        float a0 = 1.0f + alpha / A;
        b0 = (1.0f + alpha * A) / a0;
        b1 = (-2.0f * cosw) / a0;
        b2 = (1.0f - alpha * A) / a0;
        a1 = (-2.0f * cosw) / a0;
        a2 = (1.0f - alpha / A) / a0;
    }

    void setLowPass(float sr, float freq, float q = 0.707f) noexcept {
        float w0 = kTwoPi * clampf(freq, 20.0f, sr * 0.45f) / sr;
        float cosw = std::cos(w0);
        float sinw = std::sin(w0);
        float alpha = sinw / (2.0f * clampf(q, 0.3f, 12.0f));
        float a0 = 1.0f + alpha;
        b0 = ((1.0f - cosw) * 0.5f) / a0;
        b1 = (1.0f - cosw) / a0;
        b2 = ((1.0f - cosw) * 0.5f) / a0;
        a1 = (-2.0f * cosw) / a0;
        a2 = (1.0f - alpha) / a0;
    }

    inline float process(float x) noexcept {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }

private:
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1 = 0, z2 = 0;
};

struct EffectScratchBuffers {
    static constexpr int kMaxDelaySamples = 96000;
    static constexpr int kModDelaySamples = 4096;
    static constexpr int kCombCount = 4;
    static constexpr int kAllpassCount = 2;

    std::vector<float> delayBufL;
    std::vector<float> delayBufR;
    std::vector<float> modBufL;
    std::vector<float> modBufR;
    std::array<std::vector<float>, kCombCount> combBufL{};
    std::array<std::vector<float>, kCombCount> combBufR{};
    std::array<std::vector<float>, kAllpassCount> apBufL{};
    std::array<std::vector<float>, kAllpassCount> apBufR{};

    static bool needsBuffers(EffectType type) noexcept {
        return type == EffectType::Delay ||
               type == EffectType::Chorus ||
               type == EffectType::Flanger ||
               type == EffectType::Reverb;
    }

    void prepareForType(EffectType type) {
        if (type == EffectType::Delay) {
            if (delayBufL.size() != kMaxDelaySamples) delayBufL.assign(kMaxDelaySamples, 0.0f);
            if (delayBufR.size() != kMaxDelaySamples) delayBufR.assign(kMaxDelaySamples, 0.0f);
        } else if (type == EffectType::Chorus || type == EffectType::Flanger) {
            if (modBufL.size() != kModDelaySamples) modBufL.assign(kModDelaySamples, 0.0f);
            if (modBufR.size() != kModDelaySamples) modBufR.assign(kModDelaySamples, 0.0f);
        } else if (type == EffectType::Reverb) {
            for (auto& c : combBufL) if (c.size() != 1700) c.assign(1700, 0.0f);
            for (auto& c : combBufR) if (c.size() != 1700) c.assign(1700, 0.0f);
            for (auto& a : apBufL) if (a.size() != 600) a.assign(600, 0.0f);
            for (auto& a : apBufR) if (a.size() != 600) a.assign(600, 0.0f);
        }
    }

    bool hasBuffersFor(EffectType type) const noexcept {
        if (type == EffectType::Delay) {
            return delayBufL.size() >= kMaxDelaySamples && delayBufR.size() >= kMaxDelaySamples;
        }
        if (type == EffectType::Chorus || type == EffectType::Flanger) {
            return modBufL.size() >= kModDelaySamples && modBufR.size() >= kModDelaySamples;
        }
        if (type == EffectType::Reverb) {
            return combBufL[0].size() >= 1700 && apBufL[0].size() >= 600;
        }
        return true;
    }

    void clearZero() noexcept {
        if (!delayBufL.empty()) std::fill(delayBufL.begin(), delayBufL.end(), 0.0f);
        if (!delayBufR.empty()) std::fill(delayBufR.begin(), delayBufR.end(), 0.0f);
        if (!modBufL.empty()) std::fill(modBufL.begin(), modBufL.end(), 0.0f);
        if (!modBufR.empty()) std::fill(modBufR.begin(), modBufR.end(), 0.0f);
        for (auto& c : combBufL) if (!c.empty()) std::fill(c.begin(), c.end(), 0.0f);
        for (auto& c : combBufR) if (!c.empty()) std::fill(c.begin(), c.end(), 0.0f);
        for (auto& a : apBufL) if (!a.empty()) std::fill(a.begin(), a.end(), 0.0f);
        for (auto& a : apBufR) if (!a.empty()) std::fill(a.begin(), a.end(), 0.0f);
    }
};

class DspEffectUnit {
public:
    static constexpr int kMaxDelaySamples = EffectScratchBuffers::kMaxDelaySamples;
    static constexpr int kModDelaySamples = EffectScratchBuffers::kModDelaySamples;
    static constexpr int kCombCount = EffectScratchBuffers::kCombCount;
    static constexpr int kAllpassCount = EffectScratchBuffers::kAllpassCount;

    void init(float sampleRate) noexcept {
        sampleRate_ = sampleRate;
        reset();
    }

    void reset() noexcept {
        for (auto& b : eqL_) b.reset();
        for (auto& b : eqR_) b.reset();
        lpL_.reset();
        lpR_.reset();
        envFollower_ = 0.0f;
        lfoPhase_ = 0.0f;
        delayWriteIdx_ = 0;
        modWriteIdx_ = 0;
        ownBufs_.clearZero();
        if (externalBufs_) externalBufs_->clearZero();
        combIdx_.fill(0);
        apIdx_.fill(0);
        combStateL_.fill(0.0f);
        combStateR_.fill(0.0f);
        phaserZ1L_.fill(0.0f);
        phaserZ1R_.fill(0.0f);
        crushHoldL_ = crushHoldR_ = 0.0f;
        crushCounter_ = 0;
        svfIc1L_ = svfIc2L_ = svfIc1R_ = svfIc2R_ = 0.0f;
    }

    // Off-RT-thread configuration (used for shadowKernel_ and offline WAV export)
    void setConfig(const EffectConfig& cfg) {
        bool typeChanged = (cfg_.type != cfg.type);
        cfg_ = cfg;
        ownBufs_.prepareForType(cfg.type);
        if (typeChanged) {
            delayWriteIdx_ = 0;
            modWriteIdx_ = 0;
            combIdx_.fill(0);
            apIdx_.fill(0);
            combStateL_.fill(0.0f);
            combStateR_.fill(0.0f);
            phaserZ1L_.fill(0.0f);
            phaserZ1R_.fill(0.0f);
        }
    }

    // Real-time audio-thread configuration: zero allocation, zero deallocation!
    void applyRtConfig(const EffectConfig& cfg, EffectScratchBuffers* preAllocatedBufs, EffectScratchBuffers*& retiredOut) noexcept {
        bool typeChanged = (cfg_.type != cfg.type);
        cfg_ = cfg;
        retiredOut = nullptr;
        if (preAllocatedBufs != nullptr) {
            retiredOut = externalBufs_;
            externalBufs_ = preAllocatedBufs;
        }
        if (typeChanged) {
            delayWriteIdx_ = 0;
            modWriteIdx_ = 0;
            combIdx_.fill(0);
            apIdx_.fill(0);
            combStateL_.fill(0.0f);
            combStateR_.fill(0.0f);
            phaserZ1L_.fill(0.0f);
            phaserZ1R_.fill(0.0f);
            if (externalBufs_) externalBufs_->clearZero();
        }
    }

    void clearExternalPointerOnly() noexcept {
        externalBufs_ = nullptr;
    }

    void destroyExternalBuffers() noexcept {
        delete externalBufs_;
        externalBufs_ = nullptr;
    }

    void setWetDry(float wetDry) noexcept {
        cfg_.wetDry = clampf(wetDry, 0.0f, 1.0f);
    }

    const EffectConfig& getConfig() const noexcept { return cfg_; }

    void process(float* left, float* right, int numFrames, float bpm) noexcept {
        if (cfg_.type == EffectType::None || cfg_.bypassed) return;
        float wet = clampf(cfg_.wetDry, 0.0f, 1.0f);
        float dry = 1.0f - wet;

        switch (cfg_.type) {
            case EffectType::ParametricEQ: processEQ(left, right, numFrames); break;
            case EffectType::Compressor: processCompressor(left, right, numFrames, wet, dry); break;
            case EffectType::Reverb: processReverb(left, right, numFrames, wet, dry); break;
            case EffectType::Delay: processDelay(left, right, numFrames, bpm, wet, dry); break;
            case EffectType::Chorus: processChorus(left, right, numFrames, wet, dry); break;
            case EffectType::Phaser: processPhaser(left, right, numFrames, wet, dry); break;
            case EffectType::Flanger: processFlanger(left, right, numFrames, wet, dry); break;
            case EffectType::Saturation: processSaturation(left, right, numFrames, wet, dry); break;
            case EffectType::Distortion: processDistortion(left, right, numFrames, wet, dry); break;
            case EffectType::Bitcrusher: processBitcrusher(left, right, numFrames, wet, dry); break;
            case EffectType::AutoFilter: processAutoFilter(left, right, numFrames, wet, dry); break;
            case EffectType::StereoWidener: processWidener(left, right, numFrames); break;
            default: break;
        }
    }

private:
    float sampleRate_ = 48000.0f;
    EffectConfig cfg_{};

    std::array<Biquad, 3> eqL_{};
    std::array<Biquad, 3> eqR_{};
    Biquad lpL_{}, lpR_{};

    float envFollower_ = 0.0f;
    float lfoPhase_ = 0.0f;

    EffectScratchBuffers ownBufs_{};
    EffectScratchBuffers* externalBufs_ = nullptr;

    EffectScratchBuffers& bufs() noexcept {
        return externalBufs_ ? *externalBufs_ : ownBufs_;
    }

    int delayWriteIdx_ = 0;
    int modWriteIdx_ = 0;

    static constexpr int kCombLens[kCombCount] = {1557, 1617, 1491, 1422};
    static constexpr int kApLens[kAllpassCount] = {556, 441};
    std::array<int, kCombCount> combIdx_{};
    std::array<int, kAllpassCount> apIdx_{};
    std::array<float, kCombCount> combStateL_{};
    std::array<float, kCombCount> combStateR_{};

    std::array<float, 4> phaserZ1L_{};
    std::array<float, 4> phaserZ1R_{};

    float crushHoldL_ = 0.0f, crushHoldR_ = 0.0f;
    int crushCounter_ = 0;
    float svfIc1L_ = 0.0f, svfIc2L_ = 0.0f, svfIc1R_ = 0.0f, svfIc2R_ = 0.0f;

    void processEQ(float* L, float* R, int n) noexcept {
        float lowDb = (cfg_.p[0] - 0.5f) * 24.0f;
        float midFreq = 200.0f * std::pow(20.0f, cfg_.p[1]); // 200..4000 Hz
        float midDb = (cfg_.p[2] - 0.5f) * 24.0f;
        float midQ = 0.5f + cfg_.p[3] * 3.5f;
        float highDb = (cfg_.p[4] - 0.5f) * 24.0f;

        eqL_[0].setLowShelf(sampleRate_, 120.0f, lowDb);
        eqR_[0].setLowShelf(sampleRate_, 120.0f, lowDb);
        eqL_[1].setPeaking(sampleRate_, midFreq, midDb, midQ);
        eqR_[1].setPeaking(sampleRate_, midFreq, midDb, midQ);
        eqL_[2].setHighShelf(sampleRate_, 5500.0f, highDb);
        eqR_[2].setHighShelf(sampleRate_, 5500.0f, highDb);

        for (int i = 0; i < n; ++i) {
            float l = eqL_[2].process(eqL_[1].process(eqL_[0].process(L[i])));
            float r = eqR_[2].process(eqR_[1].process(eqR_[0].process(R[i])));
            L[i] = L[i] * (1.0f - cfg_.wetDry) + l * cfg_.wetDry;
            R[i] = R[i] * (1.0f - cfg_.wetDry) + r * cfg_.wetDry;
        }
    }

    void processCompressor(float* L, float* R, int n, float wet, float dry) noexcept {
        float threshLin = std::pow(10.0f, (-36.0f + cfg_.p[0] * 34.0f) / 20.0f);
        float ratio = 1.0f + cfg_.p[1] * 11.0f;
        float atk = std::exp(-1.0f / (sampleRate_ * (0.001f + cfg_.p[2] * 0.08f)));
        float rel = std::exp(-1.0f / (sampleRate_ * (0.02f + cfg_.p[3] * 0.4f)));
        float makeup = std::pow(10.0f, (cfg_.p[4] * 18.0f) / 20.0f);

        for (int i = 0; i < n; ++i) {
            float peak = std::max(std::fabs(L[i]), std::fabs(R[i]));
            float coef = (peak > envFollower_) ? atk : rel;
            envFollower_ = coef * envFollower_ + (1.0f - coef) * peak;

            float gain = 1.0f;
            if (envFollower_ > threshLin && envFollower_ > 1e-6f) {
                float over = envFollower_ / threshLin;
                float target = std::pow(over, (1.0f / ratio) - 1.0f);
                gain = target;
            }
            float outL = L[i] * gain * makeup;
            float outR = R[i] * gain * makeup;
            L[i] = L[i] * dry + outL * wet;
            R[i] = R[i] * dry + outR * wet;
        }
    }

    void processReverb(float* L, float* R, int n, float wet, float dry) noexcept {
        auto& eb = bufs();
        if (eb.combBufL[0].size() < 1700 || eb.apBufL[0].size() < 600) return;
        float room = 0.72f + cfg_.p[0] * 0.25f;
        float damp = 0.1f + cfg_.p[1] * 0.7f;
        float dampInv = 1.0f - damp;
        float stereoSpread = 23.0f;

        for (int i = 0; i < n; ++i) {
            float inM = (L[i] + R[i]) * 0.25f;
            float outL = 0.0f;
            float outR = 0.0f;

            for (int c = 0; c < kCombCount; ++c) {
                int lenL = kCombLens[c];
                int lenR = lenL + static_cast<int>(stereoSpread);
                int idx = combIdx_[c];
                int idxR = idx % lenR;

                float yL = eb.combBufL[c][idx];
                float yR = eb.combBufR[c][idxR];
                combStateL_[c] = yL * dampInv + combStateL_[c] * damp;
                combStateR_[c] = yR * dampInv + combStateR_[c] * damp;

                eb.combBufL[c][idx] = inM + combStateL_[c] * room;
                eb.combBufR[c][idxR] = inM + combStateR_[c] * room;

                outL += yL;
                outR += yR;
                combIdx_[c] = (idx + 1) % lenL;
            }

            for (int a = 0; a < kAllpassCount; ++a) {
                int len = kApLens[a];
                int idx = apIdx_[a];
                float bufOutL = eb.apBufL[a][idx];
                float bufOutR = eb.apBufR[a][idx];
                eb.apBufL[a][idx] = outL + bufOutL * 0.5f;
                eb.apBufR[a][idx] = outR + bufOutR * 0.5f;
                outL = bufOutL - outL * 0.5f;
                outR = bufOutR - outR * 0.5f;
                apIdx_[a] = (idx + 1) % len;
            }

            L[i] = L[i] * dry + outL * wet;
            R[i] = R[i] * dry + outR * wet;
        }
    }

    void processDelay(float* L, float* R, int n, float bpm, float wet, float dry) noexcept {
        auto& eb = bufs();
        if (eb.delayBufL.size() < kMaxDelaySamples || eb.delayBufR.size() < kMaxDelaySamples) return;
        float beatSec = 60.0f / clampf(bpm, 30.0f, 300.0f);
        float mults[5] = {0.25f, 0.5f, 0.75f, 1.0f, 1.5f};
        int sel = clampf(cfg_.p[0], 0.0f, 0.99f) * 5.0f;
        float delaySec = beatSec * mults[sel];
        int delaySampL = std::min(kMaxDelaySamples - 2, std::max(64, static_cast<int>(delaySec * sampleRate_)));
        int delaySampR = cfg_.p[3] > 0.5f ? std::min(kMaxDelaySamples - 2, static_cast<int>(delaySampL * 0.75f)) : delaySampL;
        float fb = clampf(cfg_.p[1] * 0.88f, 0.0f, 0.88f);
        lpL_.setLowPass(sampleRate_, 800.0f + cfg_.p[2] * 14000.0f);
        lpR_.setLowPass(sampleRate_, 800.0f + cfg_.p[2] * 14000.0f);

        for (int i = 0; i < n; ++i) {
            int readL = (delayWriteIdx_ - delaySampL + kMaxDelaySamples) % kMaxDelaySamples;
            int readR = (delayWriteIdx_ - delaySampR + kMaxDelaySamples) % kMaxDelaySamples;
            float dL = lpL_.process(eb.delayBufL[readL]);
            float dR = lpR_.process(eb.delayBufR[readR]);
            if (cfg_.p[3] > 0.5f) {
                eb.delayBufL[delayWriteIdx_] = L[i] + dR * fb;
                eb.delayBufR[delayWriteIdx_] = R[i] + dL * fb;
            } else {
                eb.delayBufL[delayWriteIdx_] = L[i] + dL * fb;
                eb.delayBufR[delayWriteIdx_] = R[i] + dR * fb;
            }
            delayWriteIdx_ = (delayWriteIdx_ + 1) % kMaxDelaySamples;
            L[i] = L[i] * dry + dL * wet;
            R[i] = R[i] * dry + dR * wet;
        }
    }

    void processChorus(float* L, float* R, int n, float wet, float dry) noexcept {
        auto& eb = bufs();
        if (eb.modBufL.size() < kModDelaySamples || eb.modBufR.size() < kModDelaySamples) return;
        float rate = 0.2f + cfg_.p[0] * 4.5f;
        float depth = 2.0f + cfg_.p[1] * 14.0f; // ms
        float baseDelay = 12.0f;
        float phaseInc = rate / sampleRate_;

        for (int i = 0; i < n; ++i) {
            eb.modBufL[modWriteIdx_] = L[i];
            eb.modBufR[modWriteIdx_] = R[i];
            float sL = std::sin(kTwoPi * lfoPhase_);
            float sR = std::sin(kTwoPi * (lfoPhase_ + 0.25f));

            auto readInterp = [&](const std::vector<float>& buf, float ms) {
                float samp = ms * 0.001f * sampleRate_;
                int idx0 = (modWriteIdx_ - static_cast<int>(samp) + kModDelaySamples) % kModDelaySamples;
                int idx1 = (idx0 - 1 + kModDelaySamples) % kModDelaySamples;
                float frac = samp - std::floor(samp);
                return buf[idx0] * (1.0f - frac) + buf[idx1] * frac;
            };

            float cL = readInterp(eb.modBufL, baseDelay + (0.5f + 0.5f * sL) * depth);
            float cR = readInterp(eb.modBufR, baseDelay + (0.5f + 0.5f * sR) * depth);
            modWriteIdx_ = (modWriteIdx_ + 1) % kModDelaySamples;
            lfoPhase_ += phaseInc;
            if (lfoPhase_ >= 1.0f) lfoPhase_ -= 1.0f;

            L[i] = L[i] * dry + cL * wet;
            R[i] = R[i] * dry + cR * wet;
        }
    }

    void processPhaser(float* L, float* R, int n, float wet, float dry) noexcept {
        float rate = 0.15f + cfg_.p[0] * 4.0f;
        float depth = cfg_.p[1];
        float fb = cfg_.p[2] * 0.7f;
        float phaseInc = rate / sampleRate_;

        for (int i = 0; i < n; ++i) {
            float lfo = 0.5f + 0.5f * std::sin(kTwoPi * lfoPhase_);
            float freq = 250.0f + lfo * depth * 3200.0f;
            float a1 = (1.0f - std::tan(kPi * freq / sampleRate_)) / (1.0f + std::tan(kPi * freq / sampleRate_));

            float xL = L[i] + phaserZ1L_[3] * fb;
            float xR = R[i] + phaserZ1R_[3] * fb;
            for (int s = 0; s < 4; ++s) {
                float yL = -a1 * xL + phaserZ1L_[s];
                phaserZ1L_[s] = xL + a1 * yL;
                xL = yL;
                float yR = -a1 * xR + phaserZ1R_[s];
                phaserZ1R_[s] = xR + a1 * yR;
                xR = yR;
            }
            lfoPhase_ += phaseInc;
            if (lfoPhase_ >= 1.0f) lfoPhase_ -= 1.0f;

            L[i] = L[i] * dry + xL * wet;
            R[i] = R[i] * dry + xR * wet;
        }
    }

    void processFlanger(float* L, float* R, int n, float wet, float dry) noexcept {
        auto& eb = bufs();
        if (eb.modBufL.size() < kModDelaySamples || eb.modBufR.size() < kModDelaySamples) return;
        float rate = 0.1f + cfg_.p[0] * 3.0f;
        float depthMs = 1.0f + cfg_.p[1] * 6.0f;
        float fb = (cfg_.p[2] - 0.5f) * 1.5f;
        float phaseInc = rate / sampleRate_;

        for (int i = 0; i < n; ++i) {
            float lfo = 0.5f + 0.5f * std::sin(kTwoPi * lfoPhase_);
            float samp = (0.5f + lfo * depthMs) * 0.001f * sampleRate_;
            int idx0 = (modWriteIdx_ - static_cast<int>(samp) + kModDelaySamples) % kModDelaySamples;
            float fL = eb.modBufL[idx0];
            float fR = eb.modBufR[idx0];

            eb.modBufL[modWriteIdx_] = L[i] + clampf(fL * fb, -0.95f, 0.95f);
            eb.modBufR[modWriteIdx_] = R[i] + clampf(fR * fb, -0.95f, 0.95f);
            modWriteIdx_ = (modWriteIdx_ + 1) % kModDelaySamples;
            lfoPhase_ += phaseInc;
            if (lfoPhase_ >= 1.0f) lfoPhase_ -= 1.0f;

            L[i] = L[i] * dry + (L[i] + fL) * 0.5f * wet;
            R[i] = R[i] * dry + (R[i] + fR) * 0.5f * wet;
        }
    }

    void processSaturation(float* L, float* R, int n, float wet, float dry) noexcept {
        float drive = 1.0f + cfg_.p[0] * 12.0f;
        float bias = (cfg_.p[1] - 0.5f) * 0.25f;
        lpL_.setLowPass(sampleRate_, 2500.0f + cfg_.p[2] * 14000.0f);
        lpR_.setLowPass(sampleRate_, 2500.0f + cfg_.p[2] * 14000.0f);
        for (int i = 0; i < n; ++i) {
            float sL = lpL_.process(fastTanh((L[i] + bias) * drive) - fastTanh(bias * drive));
            float sR = lpR_.process(fastTanh((R[i] + bias) * drive) - fastTanh(bias * drive));
            L[i] = L[i] * dry + sL * wet;
            R[i] = R[i] * dry + sR * wet;
        }
    }

    void processDistortion(float* L, float* R, int n, float wet, float dry) noexcept {
        float drive = 1.0f + cfg_.p[0] * 30.0f;
        float level = 0.3f + cfg_.p[2] * 0.7f;
        lpL_.setLowPass(sampleRate_, 1200.0f + cfg_.p[1] * 12000.0f);
        lpR_.setLowPass(sampleRate_, 1200.0f + cfg_.p[1] * 12000.0f);
        for (int i = 0; i < n; ++i) {
            float dL = clampf(L[i] * drive, -1.0f, 1.0f);
            float dR = clampf(R[i] * drive, -1.0f, 1.0f);
            dL = lpL_.process(dL) * level;
            dR = lpR_.process(dR) * level;
            L[i] = L[i] * dry + dL * wet;
            R[i] = R[i] * dry + dR * wet;
        }
    }

    void processBitcrusher(float* L, float* R, int n, float wet, float dry) noexcept {
        float bits = 16.0f - cfg_.p[0] * 13.0f; // 16..3 bits
        float steps = std::pow(2.0f, bits);
        int holdSamples = 1 + static_cast<int>(cfg_.p[1] * 32.0f);
        for (int i = 0; i < n; ++i) {
            if (crushCounter_ == 0) {
                crushHoldL_ = std::round(L[i] * steps) / steps;
                crushHoldR_ = std::round(R[i] * steps) / steps;
            }
            crushCounter_ = (crushCounter_ + 1) % holdSamples;
            L[i] = L[i] * dry + crushHoldL_ * wet;
            R[i] = R[i] * dry + crushHoldR_ * wet;
        }
    }

    void processAutoFilter(float* L, float* R, int n, float wet, float dry) noexcept {
        float baseCutoff = normalizedToCutoffHz(cfg_.p[0]);
        float reso = 0.5f + cfg_.p[1] * 6.0f;
        float envAmt = cfg_.p[2] * 6000.0f;
        float lfoRate = 0.2f + cfg_.p[3] * 8.0f;
        float lfoAmt = cfg_.p[4] * 4000.0f;
        float phaseInc = lfoRate / sampleRate_;

        for (int i = 0; i < n; ++i) {
            float peak = std::max(std::fabs(L[i]), std::fabs(R[i]));
            envFollower_ = 0.995f * envFollower_ + 0.005f * peak;
            float lfo = 0.5f + 0.5f * std::sin(kTwoPi * lfoPhase_);
            lfoPhase_ += phaseInc;
            if (lfoPhase_ >= 1.0f) lfoPhase_ -= 1.0f;

            float fc = clampf(baseCutoff + envFollower_ * envAmt + lfo * lfoAmt, 40.0f, sampleRate_ * 0.42f);
            float g = std::tan(kPi * fc / sampleRate_);
            float k = 1.0f / reso;
            float a1 = 1.0f / (1.0f + g * (g + k));
            float a2 = g * a1;
            float a3 = g * a2;

            float v3L = L[i] - svfIc2L_;
            float v1L = a1 * svfIc1L_ + a2 * v3L;
            float v2L = svfIc2L_ + a2 * svfIc1L_ + a3 * v3L;
            svfIc1L_ = 2.0f * v1L - svfIc1L_;
            svfIc2L_ = 2.0f * v2L - svfIc2L_;

            float v3R = R[i] - svfIc2R_;
            float v1R = a1 * svfIc1R_ + a2 * v3R;
            float v2R = svfIc2R_ + a2 * svfIc1R_ + a3 * v3R;
            svfIc1R_ = 2.0f * v1R - svfIc1R_;
            svfIc2R_ = 2.0f * v2R - svfIc2R_;

            L[i] = L[i] * dry + v2L * wet;
            R[i] = R[i] * dry + v2R * wet;
        }
    }

    void processWidener(float* L, float* R, int n) noexcept {
        float width = cfg_.p[0] * 2.0f; // 0 = mono, 1 = normal, 2 = ultra wide
        for (int i = 0; i < n; ++i) {
            float mid = 0.5f * (L[i] + R[i]);
            float side = 0.5f * (L[i] - R[i]) * width;
            L[i] = mid + side;
            R[i] = mid - side;
        }
    }
};

} // namespace notecraft
