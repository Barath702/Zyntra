#include "AudioEngine.h"
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <fstream>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "NoteCraftAudio", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NoteCraftAudio", __VA_ARGS__)

namespace notecraft {

void EngineKernel::init(float sr, const SamplePool* pool) noexcept {
    sampleRate = sr > 8000.0f ? sr : 48000.0f;
    poolPtr_ = pool;
    for (int t = 0; t < kMaxTracks; ++t) {
        tracks[t].synth.init(sampleRate);
        tracks[t].sampler.init(sampleRate, pool);
        tracks[t].drumRack.init(sampleRate, pool);
        for (int e = 0; e < kMaxEffects; ++e) {
            tracks[t].effects[e].init(sampleRate);
        }
    }
    for (int e = 0; e < kMaxEffects; ++e) {
        masterEffects[e].init(sampleRate);
    }
}

void EngineKernel::clearExternalEffectPointers() noexcept {
    for (int t = 0; t < kMaxTracks; ++t) {
        tracks[t].drumRack.clearExternalPointers();
        for (int e = 0; e < kMaxEffects; ++e) {
            tracks[t].effects[e].clearExternalPointerOnly();
        }
    }
    for (int e = 0; e < kMaxEffects; ++e) {
        masterEffects[e].clearExternalPointerOnly();
    }
}

void EngineKernel::destroyExternalEffectBuffers() noexcept {
    for (int t = 0; t < kMaxTracks; ++t) {
        tracks[t].drumRack.destroyExternalBuffers();
        for (int e = 0; e < kMaxEffects; ++e) {
            tracks[t].effects[e].destroyExternalBuffers();
        }
    }
    for (int e = 0; e < kMaxEffects; ++e) {
        masterEffects[e].destroyExternalBuffers();
    }
}

void EngineKernel::clearCaptureInternal() noexcept {
    capturedCount = 0;
    captureActive = false;
    captureIdleSeconds = 0.0;
    captureStartBeat = 0.0;
}

void EngineKernel::resetTransport() noexcept {
    transportBeat = 0.0;
    lastMetronomeBeat = -1;
    isCountingIn = (countInBars > 0);
    countInRemainingBeats = static_cast<double>(countInBars * timeSigNum);
    finalizeOpenCaptureNotes();
    for (auto& tr : tracks) {
        double ls = 0.0;
        if (tr.activeClip >= 0 && tr.activeClip < kMaxScenes && tr.clips[tr.activeClip].exists) {
            ls = std::max(0.0, tr.clips[tr.activeClip].loopStartBeats);
        }
        tr.clipOriginBeat = 0.0;
        tr.clipLocalBeat = ls;
        tr.prevClipLocalBeat = -1.0;
        tr.queuedClip = -1;
        tr.queuedAtBeat = -1.0;
        clearNoteRepeatHolds(tr);
        tr.synth.allNotesOff();
        tr.sampler.allNotesOff();
        tr.drumRack.reset();
    }
}

int EngineKernel::getMaxPolyphony() const noexcept {
    switch (profile) {
        case PerformanceProfile::Low: return 8;
        case PerformanceProfile::High: return 32;
        default: return 16;
    }
}

double EngineKernel::getQuantizeBoundary(double currentBeat) const noexcept {
    double q = 1.0;
    switch (globalQuantization) {
        case QuantizeMode::None: return currentBeat;
        case QuantizeMode::Quarter: q = 1.0; break;
        case QuantizeMode::Half: q = 2.0; break;
        case QuantizeMode::OneBar: q = static_cast<double>(timeSigNum); break;
        case QuantizeMode::TwoBars: q = static_cast<double>(timeSigNum * 2); break;
        case QuantizeMode::FourBars: q = static_cast<double>(timeSigNum * 4); break;
    }
    double next = std::ceil(currentBeat / q) * q;
    if (next <= currentBeat + 0.001) next += q;
    return next;
}

void EngineKernel::triggerTrackNote(TrackState& tr, int note, float vel, bool isPreview) noexcept {
    if (!tr.enabled) return;
    int poly = getMaxPolyphony();
    if (tr.type == TrackType::DrumRack) {
        int pad = note;
        float pitchOffset = 0.0f;
        if (note >= 36 && note < 52) {
            pad = note - 36;
        } else if (note >= 52 && note < 128) {
            pad = 0;
            pitchOffset = static_cast<float>(note - 60);
        }
        tr.drumRack.triggerPad(static_cast<int>(clampf(static_cast<float>(pad), 0.0f, 15.0f)), vel, pitchOffset);
    } else if (tr.type == TrackType::SubtractiveSynth) {
        tr.synth.noteOn(note, vel, poly, isPreview);
    } else {
        tr.sampler.noteOn(note, vel, poly, isPreview);
    }
}

void EngineKernel::releaseTrackNote(TrackState& tr, int note, bool previewOnly) noexcept {
    if (tr.type == TrackType::SubtractiveSynth) {
        tr.synth.noteOff(note, previewOnly);
    } else if (tr.type == TrackType::MelodicSampler) {
        tr.sampler.noteOff(note, previewOnly);
    }
}

int EngineKernel::countOpenCaptureNotes() const noexcept {
    int openCount = 0;
    for (int i = 0; i < capturedCount; ++i) {
        if (captured[i].isOpen) ++openCount;
    }
    return openCount;
}

void EngineKernel::finalizeOpenCaptureNotes() noexcept {
    for (int i = 0; i < capturedCount; ++i) {
        if (captured[i].isOpen) {
            double dur = std::max(0.0625, freeClockBeat - captured[i].startFreeBeat);
            captured[i].durationBeats = dur;
            captured[i].isOpen = false;
        }
    }
}

void EngineKernel::recordCaptureNoteOn(int trackIdx, int note, float vel) noexcept {
    if (!captureArmed) return;
    if (captureActive && !isPlaying && captureTimeoutSeconds > 0.0f &&
        captureIdleSeconds >= static_cast<double>(captureTimeoutSeconds) &&
        countOpenCaptureNotes() == 0) {
        clearCaptureInternal();
    }
    captureIdleSeconds = 0.0;

    // Prevent orphan open note durations if the same pitch is re-triggered before NoteOff
    for (int i = capturedCount - 1; i >= 0; --i) {
        if (captured[i].isOpen && captured[i].trackIndex == trackIdx && captured[i].note == note) {
            captured[i].durationBeats = std::max(0.05, freeClockBeat - captured[i].startFreeBeat);
            captured[i].isOpen = false;
        }
    }

    if (!captureActive || capturedCount == 0) {
        captureActive = true;
        captureStartBeat = freeClockBeat;
        capturedCount = 0;
    }

    if (capturedCount >= kMaxCaptured) {
        // Sliding window: keep most recent kMaxCaptured - 1 notes and rebase captureStartBeat
        for (int i = 0; i < kMaxCaptured - 1; ++i) {
            captured[i] = captured[i + 1];
        }
        capturedCount = kMaxCaptured - 1;
        captureStartBeat = captured[0].startFreeBeat;
        for (int i = 0; i < capturedCount; ++i) {
            captured[i].beatOffset = std::max(0.0, captured[i].startFreeBeat - captureStartBeat);
        }
    }

    double offset = std::max(0.0, freeClockBeat - captureStartBeat);
    double tpBeat = isPlaying ? transportBeat : -1.0;
    captured[capturedCount++] = CapturedNote{
        trackIdx,
        note,
        offset,
        0.25,
        clampf(vel, 0.05f, 1.0f),
        tpBeat,
        freeClockBeat,
        true
    };
}

void EngineKernel::recordCaptureNoteOff(int trackIdx, int note) noexcept {
    for (int i = capturedCount - 1; i >= 0; --i) {
        if (captured[i].trackIndex == trackIdx && captured[i].note == note && captured[i].isOpen) {
            double dur = std::max(0.05, freeClockBeat - captured[i].startFreeBeat);
            captured[i].durationBeats = dur;
            captured[i].isOpen = false;
        }
    }
}

void EngineKernel::registerNoteRepeatHold(TrackState& tr, int note, float vel, float pitchOff, bool isPad) noexcept {
    if (!tr.noteRepeatEnabled) return;
    bool anyActive = false;
    int existingSlot = -1;
    int freeSlot = -1;
    for (int i = 0; i < TrackState::kMaxRepeatNotes; ++i) {
        if (tr.noteRepeatHeld[i].active) {
            anyActive = true;
            if (tr.noteRepeatHeld[i].note == note && tr.noteRepeatHeld[i].isPad == isPad) {
                existingSlot = i;
            }
        } else if (freeSlot < 0) {
            freeSlot = i;
        }
    }
    int target = (existingSlot >= 0) ? existingSlot : freeSlot;
    if (target >= 0) {
        tr.noteRepeatHeld[target] = RepeatHeldNote{true, note, vel, pitchOff, isPad};
    }
    if (!anyActive) {
        tr.nextRepeatBeat = freeClockBeat + tr.noteRepeatIntervalBeats;
    }
}

void EngineKernel::unregisterNoteRepeatHold(TrackState& tr, int note, bool isPad) noexcept {
    for (int i = 0; i < TrackState::kMaxRepeatNotes; ++i) {
        if (tr.noteRepeatHeld[i].active &&
            tr.noteRepeatHeld[i].note == note &&
            tr.noteRepeatHeld[i].isPad == isPad) {
            tr.noteRepeatHeld[i].active = false;
        }
    }
}

void EngineKernel::clearNoteRepeatHolds(TrackState& tr) noexcept {
    for (auto& slot : tr.noteRepeatHeld) {
        slot.active = false;
    }
}

void EngineKernel::applyCommand(const EngineCommand& cmd) noexcept {
    int t = std::max(0, std::min(kMaxTracks - 1, cmd.track));
    switch (cmd.type) {
        case CommandType::NoteOn:
            triggerTrackNote(tracks[t], cmd.arg1, cmd.f1);
            recordCaptureNoteOn(t, cmd.arg1, cmd.f1);
            registerNoteRepeatHold(tracks[t], cmd.arg1, cmd.f1, 0.0f, false);
            break;
        case CommandType::NoteOff:
            releaseTrackNote(tracks[t], cmd.arg1);
            recordCaptureNoteOff(t, cmd.arg1);
            unregisterNoteRepeatHold(tracks[t], cmd.arg1, false);
            break;
        case CommandType::PadTrigger: {
            int pad = std::max(0, std::min(kMaxDrumPads - 1, cmd.arg1));
            float pitchOff = cmd.f2;
            tracks[t].drumRack.triggerPad(pad, cmd.f1, pitchOff);
            recordCaptureNoteOn(t, 36 + pad, cmd.f1);
            registerNoteRepeatHold(tracks[t], pad, cmd.f1, pitchOff, true);
            break;
        }
        case CommandType::PadRelease: {
            int pad = std::max(0, std::min(kMaxDrumPads - 1, cmd.arg1));
            recordCaptureNoteOff(t, 36 + pad);
            unregisterNoteRepeatHold(tracks[t], pad, true);
            break;
        }
        case CommandType::PitchBend:
            tracks[t].synth.setPitchBend(cmd.f1);
            tracks[t].sampler.setPitchBend(cmd.f1);
            break;
        case CommandType::ModWheel:
            tracks[t].synth.setModWheel(cmd.f1);
            break;
        case CommandType::Sustain:
            tracks[t].synth.setSustain(cmd.arg1 != 0);
            tracks[t].sampler.setSustain(cmd.arg1 != 0);
            break;
        case CommandType::Play:
            if (!isPlaying) {
                resetTransport();
                isPlaying = true;
            }
            break;
        case CommandType::Stop:
            isPlaying = false;
            isCountingIn = false;
            resetTransport();
            break;
        case CommandType::Restart:
            resetTransport();
            isPlaying = true;
            break;
        case CommandType::SetBpm:
            bpm = clampf(cmd.f1, 30.0f, 300.0f);
            break;
        case CommandType::SetSwing:
            swing = clampf(cmd.f1, 0.0f, 1.0f);
            break;
        case CommandType::SetMetronome:
            metronomeEnabled = (cmd.arg1 != 0);
            break;
        case CommandType::SetCountIn:
            countInBars = std::max(0, std::min(4, cmd.arg1));
            break;
        case CommandType::SetQuantization:
            globalQuantization = static_cast<QuantizeMode>(std::max(0, std::min(5, cmd.arg1)));
            break;
        case CommandType::SetTimeSig:
            timeSigNum = std::max(1, std::min(12, cmd.arg1));
            timeSigDen = std::max(1, std::min(16, cmd.arg2));
            break;
        case CommandType::LaunchClip: {
            int scene = std::max(0, std::min(kMaxScenes - 1, cmd.arg1));
            if (!isPlaying) {
                transportBeat = 0.0;
                lastMetronomeBeat = -1;
                double ls = std::max(0.0, tracks[t].clips[scene].loopStartBeats);
                tracks[t].activeClip = scene;
                tracks[t].queuedClip = -1;
                tracks[t].queuedAtBeat = -1.0;
                tracks[t].clipOriginBeat = 0.0;
                tracks[t].clipLocalBeat = ls;
                tracks[t].prevClipLocalBeat = -1.0;
                tracks[t].synth.allNotesOff();
                tracks[t].sampler.allNotesOff();
                if (cmd.arg2 != 0) {
                    isCountingIn = (countInBars > 0);
                    countInRemainingBeats = static_cast<double>(countInBars * timeSigNum);
                    isPlaying = true;
                }
            } else if (globalQuantization == QuantizeMode::None) {
                bool anyOtherActive = false;
                for (int ti = 0; ti < kMaxTracks; ++ti) {
                    if (ti != t && tracks[ti].enabled && tracks[ti].activeClip >= 0) {
                        anyOtherActive = true;
                        break;
                    }
                }
                double barBeats = static_cast<double>(std::max(1, timeSigNum));
                double origin = anyOtherActive
                    ? (std::floor(transportBeat / barBeats) * barBeats)
                    : transportBeat;
                double ls = std::max(0.0, tracks[t].clips[scene].loopStartBeats);
                tracks[t].activeClip = scene;
                tracks[t].queuedClip = -1;
                tracks[t].queuedAtBeat = -1.0;
                tracks[t].clipOriginBeat = origin;
                tracks[t].clipLocalBeat = ls;
                tracks[t].prevClipLocalBeat = -1.0;
                tracks[t].synth.allNotesOff();
                tracks[t].sampler.allNotesOff();
            } else {
                tracks[t].queuedClip = scene;
                tracks[t].queuedAtBeat = getQuantizeBoundary(transportBeat);
            }
            break;
        }
        case CommandType::StopTrackClip:
            if (!isPlaying || globalQuantization == QuantizeMode::None) {
                tracks[t].activeClip = -1;
                tracks[t].queuedClip = -1;
                tracks[t].queuedAtBeat = -1.0;
                tracks[t].synth.allNotesOff();
                tracks[t].sampler.allNotesOff();
            } else {
                tracks[t].queuedClip = -2; // sentinel for stop
                tracks[t].queuedAtBeat = getQuantizeBoundary(transportBeat);
            }
            break;
        case CommandType::LaunchScene: {
            int scene = std::max(0, std::min(kMaxScenes - 1, cmd.arg1));
            bool immediate = (!isPlaying || globalQuantization == QuantizeMode::None);
            double boundary = immediate ? (!isPlaying ? 0.0 : transportBeat) : getQuantizeBoundary(transportBeat);
            if (!isPlaying) {
                transportBeat = 0.0;
                lastMetronomeBeat = -1;
                isCountingIn = (countInBars > 0);
                countInRemainingBeats = static_cast<double>(countInBars * timeSigNum);
            }
            for (int ti = 0; ti < kMaxTracks; ++ti) {
                if (!tracks[ti].enabled) continue;
                if (tracks[ti].clips[scene].exists) {
                    if (immediate) {
                        double barBeats = static_cast<double>(std::max(1, timeSigNum));
                        double origin = !isPlaying ? 0.0 : (std::floor(transportBeat / barBeats) * barBeats);
                        double ls = std::max(0.0, tracks[ti].clips[scene].loopStartBeats);
                        tracks[ti].synth.allNotesOff();
                        tracks[ti].sampler.allNotesOff();
                        tracks[ti].activeClip = scene;
                        tracks[ti].queuedClip = -1;
                        tracks[ti].queuedAtBeat = -1.0;
                        tracks[ti].clipOriginBeat = origin;
                        tracks[ti].clipLocalBeat = ls;
                        tracks[ti].prevClipLocalBeat = -1.0;
                    } else {
                        tracks[ti].queuedClip = scene;
                        tracks[ti].queuedAtBeat = boundary;
                    }
                } else {
                    if (immediate) {
                        tracks[ti].activeClip = -1;
                        tracks[ti].queuedClip = -1;
                        tracks[ti].queuedAtBeat = -1.0;
                        tracks[ti].synth.allNotesOff();
                        tracks[ti].sampler.allNotesOff();
                    } else {
                        tracks[ti].queuedClip = -2;
                        tracks[ti].queuedAtBeat = boundary;
                    }
                }
            }
            if (!isPlaying) {
                isPlaying = true;
            }
            break;
        }
        case CommandType::SetTrackVolume:
            tracks[t].volume = clampf(cmd.f1, 0.0f, 1.5f);
            break;
        case CommandType::SetTrackPan:
            tracks[t].pan = clampf(cmd.f1, -1.0f, 1.0f);
            break;
        case CommandType::SetTrackMute:
            tracks[t].muted = (cmd.arg1 != 0);
            break;
        case CommandType::SetTrackSolo:
            tracks[t].soloed = (cmd.arg1 != 0);
            break;
        case CommandType::SetMasterVolume:
            masterVolume = clampf(cmd.f1, 0.0f, 1.5f);
            break;
        case CommandType::SetNoteRepeat:
            tracks[t].noteRepeatEnabled = (cmd.arg1 != 0);
            tracks[t].noteRepeatIntervalBeats = std::max(0.0625, static_cast<double>(cmd.f1));
            if (!tracks[t].noteRepeatEnabled) clearNoteRepeatHolds(tracks[t]);
            break;
        case CommandType::SetPerformanceProfile:
            profile = static_cast<PerformanceProfile>(std::max(0, std::min(2, cmd.arg1)));
            break;
        case CommandType::AllNotesOff:
            finalizeOpenCaptureNotes();
            previewVoice_.active = false;
            for (auto& tr : tracks) {
                clearNoteRepeatHolds(tr);
                tr.synth.allNotesOff();
                tr.sampler.allNotesOff();
            }
            break;
        case CommandType::SetCaptureTimeout:
            captureTimeoutSeconds = clampf(cmd.f1, 0.0f, 120.0f);
            break;
        case CommandType::PreviewNoteOn:
            triggerTrackNote(tracks[t], cmd.arg1, cmd.f1, true);
            break;
        case CommandType::PreviewNoteOff:
            releaseTrackNote(tracks[t], cmd.arg1, true);
            break;
        case CommandType::PreviewPadTrigger: {
            int pad = std::max(0, std::min(kMaxDrumPads - 1, cmd.arg1));
            tracks[t].drumRack.triggerPad(pad, cmd.f1, cmd.f2);
            break;
        }
        case CommandType::PreviewSampleSlot: {
            int slot = cmd.arg1;
            const SampleBuffer* buf = poolPtr_ ? poolPtr_->get(slot) : nullptr;
            if (buf && !buf->data.empty()) {
                previewVoice_.slot = slot;
                previewVoice_.pos = 0.0;
                previewVoice_.gain = clampf(cmd.f1 > 0.0f ? cmd.f1 : 0.85f, 0.05f, 1.2f);
                previewVoice_.active = true;
            } else {
                previewVoice_.active = false;
            }
            break;
        }
        case CommandType::ClearCapture:
            clearCaptureInternal();
            break;
        case CommandType::SetCaptureArmed:
            captureArmed = (cmd.arg1 != 0);
            if (!captureArmed) {
                finalizeOpenCaptureNotes();
            }
            break;
        case CommandType::ReleasePreviewNotes:
            previewVoice_.active = false;
            for (auto& tr : tracks) {
                tr.synth.releasePreviewNotes();
                tr.sampler.releasePreviewNotes();
            }
            break;
        default: break;
    }
}

void EngineKernel::applyTrackAutomation(TrackState& tr, double clipBeat) noexcept {
    if (tr.activeClip < 0 || tr.activeClip >= kMaxScenes) return;
    const auto& clip = tr.clips[tr.activeClip];
    if (!clip.exists) return;

    for (int l = 0; l < clip.autoLaneCount; ++l) {
        const auto& lane = clip.autoLanes[l];
        if (lane.count <= 0) continue;
        float val = lane.points[0].value;
        if (lane.count == 1 || clipBeat <= lane.points[0].beat) {
            val = lane.points[0].value;
        } else if (clipBeat >= lane.points[lane.count - 1].beat) {
            val = lane.points[lane.count - 1].value;
        } else {
            for (int p = 0; p < lane.count - 1; ++p) {
                if (clipBeat >= lane.points[p].beat && clipBeat <= lane.points[p + 1].beat) {
                    float span = std::max(0.0001f, lane.points[p + 1].beat - lane.points[p].beat);
                    float t = static_cast<float>(clipBeat - lane.points[p].beat) / span;
                    val = lane.points[p].value * (1.0f - t) + lane.points[p + 1].value * t;
                    break;
                }
            }
        }
        if (lane.targetParamId == 0) {
            tr.volume = clampf(val * 1.2f, 0.0f, 1.2f);
        } else if (lane.targetParamId == 1) {
            tr.pan = clampf(val * 2.0f - 1.0f, -1.0f, 1.0f);
        } else if (lane.targetParamId >= 4 && lane.targetParamId < 4 + kMaxEffects) {
            tr.effects[lane.targetParamId - 4].setWetDry(val);
        }
    }
}

void EngineKernel::scheduleClipNotes(TrackState& tr, int trackIdx, double beatStart, double beatEnd, double loopStart, double loopEnd, double clipLen) noexcept {
    if (tr.activeClip < 0 || tr.activeClip >= kMaxScenes) return;
    const auto& clip = tr.clips[tr.activeClip];
    if (!clip.exists || clipLen <= 0.0) return;

    double span = std::max(0.25, loopEnd - loopStart);

    for (int i = 0; i < clip.noteCount; ++i) {
        const auto& n = clip.notes[i];
        double noteStart = static_cast<double>(n.startBeat) + static_cast<double>(n.timingOffset);
        double sixteenthIdx = noteStart / 0.25;
        if (swing > 0.001f && std::fabs(std::fmod(sixteenthIdx, 2.0) - 1.0) < 0.2) {
            noteStart += swing * 0.12;
        }
        while (noteStart < 0.0) noteStart += clipLen;
        while (noteStart >= clipLen) noteStart -= clipLen;

        // Notes outside the active loop region do not trigger while loop brace isolates [loopStart, loopEnd)
        if (clip.loopEnabled && (noteStart < loopStart - 0.0005 || noteStart >= loopEnd - 0.0005)) {
            continue;
        }

        double noteEnd = noteStart + std::max(0.05, static_cast<double>(n.durationBeats));
        double noteEndWrapped = noteEnd;
        if (clip.loopEnabled && noteEndWrapped >= loopEnd) {
            noteEndWrapped = loopStart + std::fmod(noteEndWrapped - loopStart, span);
        }

        bool shouldTrigger = (beatStart <= beatEnd)
            ? (noteStart >= beatStart && noteStart < beatEnd)
            : (noteStart >= beatStart || noteStart < beatEnd);

        if (shouldTrigger) {
            if (n.probability >= 0.99f || ((static_cast<int>(transportBeat * 17.0 + i * 31) % 100) / 100.0f) < n.probability) {
                triggerTrackNote(tr, n.note, n.velocity);
            }
        }

        if (tr.type != TrackType::DrumRack) {
            bool shouldRelease = (beatStart <= beatEnd)
                ? (noteEndWrapped >= beatStart && noteEndWrapped < beatEnd)
                : (noteEndWrapped >= beatStart || noteEndWrapped < beatEnd);
            if (shouldRelease) {
                releaseTrackNote(tr, n.note);
            }
        }
    }
}

void EngineKernel::processBlock(float* outInterleaved, int numFrames, int channels) noexcept {
    if (numFrames <= 0 || numFrames > kMaxBlockFrames) return;

    alignas(16) float mixL[kMaxBlockFrames];
    alignas(16) float mixR[kMaxBlockFrames];
    alignas(16) float trL[kMaxBlockFrames];
    alignas(16) float trR[kMaxBlockFrames];

    std::fill(mixL, mixL + numFrames, 0.0f);
    std::fill(mixR, mixR + numFrames, 0.0f);

    double beatsPerSample = static_cast<double>(bpm) / (60.0 * static_cast<double>(sampleRate));
    double blockBeats = beatsPerSample * static_cast<double>(numFrames);
    double blockSeconds = static_cast<double>(numFrames) / std::max(8000.0, static_cast<double>(sampleRate));

    // Monotonic free clock always runs so retrospective capture works identically stopped or playing
    double nextFreeClock = freeClockBeat + blockBeats;

    if (!isPlaying && captureActive && countOpenCaptureNotes() == 0) {
        captureIdleSeconds += blockSeconds;
    }

    // Unified polyphonic Note Repeat engine across playing & stopped states
    for (int ti = 0; ti < kMaxTracks; ++ti) {
        auto& tr = tracks[ti];
        if (!tr.enabled || !tr.noteRepeatEnabled) continue;
        bool hasHeld = false;
        for (const auto& slot : tr.noteRepeatHeld) {
            if (slot.active) { hasHeld = true; break; }
        }
        if (hasHeld && nextFreeClock >= tr.nextRepeatBeat) {
            for (const auto& slot : tr.noteRepeatHeld) {
                if (!slot.active) continue;
                if (slot.isPad) {
                    tr.drumRack.triggerPad(slot.note, slot.velocity, slot.pitchOffset);
                    recordCaptureNoteOn(ti, 36 + slot.note, slot.velocity);
                    recordCaptureNoteOff(ti, 36 + slot.note);
                } else {
                    triggerTrackNote(tr, slot.note, slot.velocity);
                    recordCaptureNoteOn(ti, slot.note, slot.velocity);
                    recordCaptureNoteOff(ti, slot.note);
                }
            }
            while (tr.nextRepeatBeat <= nextFreeClock) {
                tr.nextRepeatBeat += tr.noteRepeatIntervalBeats;
            }
        }
    }

    // Advance transport & quantized clip launches
    if (isPlaying) {
        if (isCountingIn) {
            countInRemainingBeats -= blockBeats;
            int curBeat = static_cast<int>(std::floor(countInRemainingBeats));
            if (curBeat != lastMetronomeBeat) {
                lastMetronomeBeat = curBeat;
                metroEnv = 1.0f;
                metroPhase = 0.0f;
                metroAccent = ((curBeat % timeSigNum) == 0);
            }
            if (countInRemainingBeats <= 0.0) {
                isCountingIn = false;
                transportBeat = 0.0;
                lastMetronomeBeat = -1;
            }
        } else {
            double nextTransportBeat = transportBeat + blockBeats;

            // Check metronome
            if (metronomeEnabled) {
                int beatFloor = static_cast<int>(std::floor(transportBeat));
                if (beatFloor != lastMetronomeBeat) {
                    lastMetronomeBeat = beatFloor;
                    metroEnv = 1.0f;
                    metroPhase = 0.0f;
                    metroAccent = ((beatFloor % timeSigNum) == 0);
                }
            }

            // Check quantized clip launches & active clip progression
            for (int ti = 0; ti < kMaxTracks; ++ti) {
                auto& tr = tracks[ti];
                if (!tr.enabled) continue;

                if (tr.queuedClip != -1 && nextTransportBeat >= tr.queuedAtBeat) {
                    double qBeat = tr.queuedAtBeat;
                    tr.synth.allNotesOff();
                    tr.sampler.allNotesOff();
                    if (tr.queuedClip == -2) {
                        tr.activeClip = -1;
                    } else {
                        tr.activeClip = tr.queuedClip;
                        tr.clipOriginBeat = qBeat;
                        tr.clipLocalBeat = std::max(0.0, tr.clips[tr.activeClip].loopStartBeats);
                        tr.prevClipLocalBeat = -1.0;
                    }
                    tr.queuedClip = -1;
                    tr.queuedAtBeat = -1.0;
                }

                if (tr.activeClip >= 0 && tr.activeClip < kMaxScenes) {
                    const auto& clip = tr.clips[tr.activeClip];
                    if (clip.exists && clip.lengthBeats > 0.0) {
                        double lStart = std::max(0.0, std::min(clip.loopStartBeats, clip.lengthBeats - 0.25));
                        double lEnd = (clip.loopEndBeats > lStart + 0.1)
                            ? std::min(clip.loopEndBeats, clip.lengthBeats)
                            : clip.lengthBeats;
                        double span = std::max(0.25, lEnd - lStart);

                        double elapsedStart = std::max(0.0, transportBeat - tr.clipOriginBeat);
                        double elapsedEnd = std::max(0.0, nextTransportBeat - tr.clipOriginBeat);
                        double sliceBeats = std::max(0.0, elapsedEnd - elapsedStart);

                        double startB = elapsedStart;
                        double endB = elapsedEnd;
                        if (clip.loopEnabled) {
                            startB = lStart + std::fmod(elapsedStart, span);
                            if (tr.prevClipLocalBeat < 0.0 && elapsedStart <= 0.0001) {
                                startB = lStart;
                            }
                            endB = startB + sliceBeats;
                            while (endB >= lEnd) {
                                endB -= span;
                            }
                        }
                        if (sliceBeats > 0.0) {
                            scheduleClipNotes(tr, ti, startB, endB, lStart, lEnd, clip.lengthBeats);
                            applyTrackAutomation(tr, endB);
                        }
                        tr.prevClipLocalBeat = startB;
                        tr.clipLocalBeat = endB;
                        if (!clip.loopEnabled && endB >= clip.lengthBeats) {
                            tr.activeClip = -1;
                            tr.synth.allNotesOff();
                            tr.sampler.allNotesOff();
                        }
                    }
                }
            }

            transportBeat = nextTransportBeat;
        }
    }

    freeClockBeat = nextFreeClock;

    bool anySolo = false;
    for (int ti = 0; ti < kMaxTracks; ++ti) {
        if (tracks[ti].enabled && tracks[ti].soloed) { anySolo = true; break; }
    }

    int maxPoly = getMaxPolyphony();

    // Render all tracks
    for (int ti = 0; ti < kMaxTracks; ++ti) {
        auto& tr = tracks[ti];
        if (!tr.enabled) {
            tr.peakL = tr.peakR = 0.0f;
            continue;
        }

        std::fill(trL, trL + numFrames, 0.0f);
        std::fill(trR, trR + numFrames, 0.0f);

        if (tr.type == TrackType::DrumRack) {
            tr.drumRack.process(trL, trR, numFrames, bpm);
        } else if (tr.type == TrackType::SubtractiveSynth) {
            tr.synth.process(trL, trR, numFrames, maxPoly);
        } else {
            tr.sampler.process(trL, trR, numFrames, maxPoly);
        }

        // Track insert effects
        for (int ei = 0; ei < kMaxEffects; ++ei) {
            tr.effects[ei].process(trL, trR, numFrames, bpm);
        }

        bool audible = !tr.muted && (!anySolo || tr.soloed);
        float panAngle = (clampf(tr.pan, -1.0f, 1.0f) + 1.0f) * 0.25f * kPi;
        float gainL = audible ? (tr.volume * std::cos(panAngle) * 1.4142f) : 0.0f;
        float gainR = audible ? (tr.volume * std::sin(panAngle) * 1.4142f) : 0.0f;

        float pkL = 0.0f, pkR = 0.0f;
        for (int i = 0; i < numFrames; ++i) {
            float l = trL[i] * gainL;
            float r = trR[i] * gainR;
            pkL = std::max(pkL, std::fabs(l));
            pkR = std::max(pkR, std::fabs(r));
            mixL[i] += l;
            mixR[i] += r;
        }
        tr.peakL = pkL;
        tr.peakR = pkR;
    }

    // Master effects
    for (int ei = 0; ei < kMaxEffects; ++ei) {
        masterEffects[ei].process(mixL, mixR, numFrames, bpm);
    }

    // Isolated Sample Browser preview voice (never affects tracks or capture buffer)
    if (previewVoice_.active && poolPtr_) {
        const SampleBuffer* buf = poolPtr_->get(previewVoice_.slot);
        if (!buf || buf->data.empty()) {
            previewVoice_.active = false;
        } else {
            int totalFrames = static_cast<int>(buf->data.size()) / std::max(1, buf->channels);
            if (totalFrames < 2) {
                previewVoice_.active = false;
            } else {
                double rate = static_cast<double>(buf->sampleRate) / std::max(8000.0f, sampleRate);
                float g = previewVoice_.gain;
                for (int i = 0; i < numFrames; ++i) {
                    if (previewVoice_.pos >= totalFrames - 1) {
                        previewVoice_.active = false;
                        break;
                    }
                    int idx0 = static_cast<int>(previewVoice_.pos);
                    float frac = static_cast<float>(previewVoice_.pos - idx0);
                    float l = 0.0f, r = 0.0f;
                    if (buf->channels == 1) {
                        float s = buf->data[idx0] * (1.0f - frac) + buf->data[idx0 + 1] * frac;
                        l = r = s;
                    } else {
                        l = buf->data[idx0 * 2] * (1.0f - frac) + buf->data[(idx0 + 1) * 2] * frac;
                        r = buf->data[idx0 * 2 + 1] * (1.0f - frac) + buf->data[(idx0 + 1) * 2 + 1] * frac;
                    }
                    mixL[i] += l * g;
                    mixR[i] += r * g;
                    previewVoice_.pos += rate;
                }
            }
        }
    }

    // Metronome click synthesis
    if (metroEnv > 0.001f) {
        float mFreq = metroAccent ? 1480.0f : 980.0f;
        float mInc = mFreq / sampleRate;
        for (int i = 0; i < numFrames; ++i) {
            if (metroEnv < 0.001f) break;
            float click = std::sin(kTwoPi * metroPhase) * metroEnv * 0.28f;
            mixL[i] += click;
            mixR[i] += click;
            metroPhase += mInc;
            if (metroPhase >= 1.0f) metroPhase -= 1.0f;
            metroEnv *= 0.996f;
        }
    }

    // Master volume + soft limiter + meter
    float mPkL = 0.0f, mPkR = 0.0f;
    bool clip = false;
    for (int i = 0; i < numFrames; ++i) {
        float l = mixL[i] * masterVolume;
        float r = mixR[i] * masterVolume;
        if (std::fabs(l) > 0.99f || std::fabs(r) > 0.99f) clip = true;
        mPkL = std::max(mPkL, std::fabs(l));
        mPkR = std::max(mPkR, std::fabs(r));
        l = fastTanh(l);
        r = fastTanh(r);
        if (channels >= 2) {
            outInterleaved[i * channels] = l;
            outInterleaved[i * channels + 1] = r;
        } else {
            outInterleaved[i] = 0.5f * (l + r);
        }
    }
    masterPeakL = mPkL;
    masterPeakR = mPkR;
    masterClipping = clip;
}

// ---------------- Live AudioEngine ----------------
AudioEngine::AudioEngine() {
    kernel_.init(48000.0f, &samplePool_);
    shadowKernel_ = std::make_unique<EngineKernel>();
    shadowKernel_->init(48000.0f, &samplePool_);
}

AudioEngine::~AudioEngine() {
    stopStream();
    detachMidiInputDevice();
    detachMidiOutputDevice();
    drainRetireQueue();
    kernel_.destroyExternalEffectBuffers();
}

bool AudioEngine::startStream(int sampleRate, int bufferFrames) {
    std::lock_guard<std::mutex> lock(streamMutex_);
    intentionallyStopped_.store(false, std::memory_order_release);
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }

    requestedSampleRate_ = (sampleRate == 44100) ? 44100 : 48000;
    requestedBufferFrames_ = std::max(64, std::min(2048, bufferFrames));

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(oboe::ChannelCount::Stereo)
           ->setSampleRate(requestedSampleRate_)
           ->setFramesPerDataCallback(requestedBufferFrames_)
           ->setDataCallback(this)
           ->setErrorCallback(this);

    oboe::Result res = builder.openStream(stream_);
    if (res != oboe::Result::OK || !stream_) {
        stream_.reset();
        oboe::AudioStreamBuilder fallback;
        fallback.setDirection(oboe::Direction::Output)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Shared)
                ->setFormat(oboe::AudioFormat::Float)
                ->setChannelCount(oboe::ChannelCount::Stereo)
                ->setSampleRate(requestedSampleRate_)
                ->setDataCallback(this)
                ->setErrorCallback(this);
        res = fallback.openStream(stream_);
        if (res != oboe::Result::OK || !stream_) {
            LOGE("Failed to open Oboe stream: %s", oboe::convertToText(res));
            return false;
        }
    }

    int burst = std::max(64, stream_->getFramesPerBurst());
    stream_->setBufferSizeInFrames(std::min(stream_->getBufferCapacityInFrames(), burst * 2));

    int sr = stream_->getSampleRate();
    int bf = burst;
    actualSampleRate_.store(sr, std::memory_order_relaxed);
    actualBufferFrames_.store(bf, std::memory_order_relaxed);
    actualAAudio_.store(stream_->getAudioApi() == oboe::AudioApi::AAudio, std::memory_order_relaxed);
    kernel_.init(static_cast<float>(sr), &samplePool_);
    {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        if (shadowKernel_) shadowKernel_->init(static_cast<float>(sr), &samplePool_);
    }

    res = stream_->requestStart();
    if (res != oboe::Result::OK) {
        LOGE("Failed to start Oboe stream: %s", oboe::convertToText(res));
        stream_->close();
        stream_.reset();
        return false;
    }
    LOGI("Oboe stream started: sr=%d, burst=%d, api=%d", sr, bf, static_cast<int>(stream_->getAudioApi()));
    return true;
}

void AudioEngine::stopStream() {
    intentionallyStopped_.store(true, std::memory_order_release);
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

void AudioEngine::restartStream() {
    if (intentionallyStopped_.load(std::memory_order_acquire)) return;
    startStream(requestedSampleRate_, requestedBufferFrames_);
}

void AudioEngine::postCommand(const EngineCommand& cmd) noexcept {
    cmdQueue_.push(cmd);
    if (cmd.type == CommandType::SetBpm ||
        cmd.type == CommandType::SetSwing ||
        cmd.type == CommandType::SetTimeSig ||
        cmd.type == CommandType::SetQuantization ||
        cmd.type == CommandType::SetTrackVolume ||
        cmd.type == CommandType::SetTrackPan ||
        cmd.type == CommandType::SetTrackMute ||
        cmd.type == CommandType::SetTrackSolo ||
        cmd.type == CommandType::SetMasterVolume ||
        cmd.type == CommandType::SetPerformanceProfile ||
        cmd.type == CommandType::SetCaptureTimeout ||
        cmd.type == CommandType::LaunchClip ||
        cmd.type == CommandType::StopTrackClip ||
        cmd.type == CommandType::LaunchScene) {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        if (shadowKernel_) {
            shadowKernel_->applyCommand(cmd);
            shadowKernel_->isPlaying = false;
            shadowKernel_->isCountingIn = false;
        }
    }
}

void AudioEngine::drainRetireQueue() {
    std::lock_guard<std::mutex> rl(retireMutex_);
    RetireEntry entry{};
    while (retireQueue_.pop(entry)) {
        if (entry.ptr && entry.deleter) {
            entry.deleter(entry.ptr);
        }
    }
}

namespace {
template <typename T>
void typedDelete(void* p) noexcept {
    delete static_cast<T*>(p);
}
}

void AudioEngine::setTrackEnabled(int track, bool enabled, int trackType) {
    int t = std::max(0, std::min(kMaxTracks - 1, track));
    {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        if (shadowKernel_) {
            shadowKernel_->tracks[t].enabled = enabled;
            shadowKernel_->tracks[t].type = static_cast<TrackType>(std::max(0, std::min(2, trackType)));
        }
    }
    PendingUpdate u{};
    u.type = StructUpdateType::TrackConfig;
    u.track = t;
    u.flag = enabled;
    u.intVal = trackType;
    structQueue_.push(u);
}

void AudioEngine::setSynthParams(int track, const SynthParams& p) {
    drainRetireQueue();
    int t = std::max(0, std::min(kMaxTracks - 1, track));
    {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        if (shadowKernel_) shadowKernel_->tracks[t].synth.setParams(p);
    }
    auto* box = new SynthParams(p);
    PendingUpdate u{StructUpdateType::SynthParams, t, 0, false, 0, box};
    if (!structQueue_.push(u)) delete box;
}

void AudioEngine::setSamplerParams(int track, const SamplerParams& p) {
    drainRetireQueue();
    int t = std::max(0, std::min(kMaxTracks - 1, track));
    {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        if (shadowKernel_) shadowKernel_->tracks[t].sampler.setParams(p);
    }
    auto* box = new SamplerParams(p);
    PendingUpdate u{StructUpdateType::SamplerParams, t, 0, false, 0, box};
    if (!structQueue_.push(u)) delete box;
}

void AudioEngine::setDrumPadParams(int track, int pad, const DrumPadParams& p) {
    drainRetireQueue();
    int t = std::max(0, std::min(kMaxTracks - 1, track));
    {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        if (shadowKernel_) shadowKernel_->tracks[t].drumRack.setPadParams(pad, p);
    }
    auto* box = new DrumPadParams(p);
    PendingUpdate u{StructUpdateType::DrumPadParams, t, pad, false, 0, box};
    if (!structQueue_.push(u)) delete box;
}

void AudioEngine::setTrackEffect(int track, int slot, const EffectConfig& cfg) {
    drainRetireQueue();
    int t = std::max(0, std::min(kMaxTracks - 1, track));
    bool needNewBufs = false;
    {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        if (shadowKernel_ && slot >= 0 && slot < kMaxEffects) {
            EffectType prevType = shadowKernel_->tracks[t].effects[slot].getConfig().type;
            shadowKernel_->tracks[t].effects[slot].setConfig(cfg);
            needNewBufs = EffectScratchBuffers::needsBuffers(cfg.type) && prevType != cfg.type;
        }
    }
    auto* payload = new EffectUpdatePayload();
    payload->cfg = cfg;
    if (needNewBufs) {
        payload->preAllocatedBufs = new EffectScratchBuffers();
        payload->preAllocatedBufs->prepareForType(cfg.type);
    }
    PendingUpdate u{StructUpdateType::TrackEffect, t, slot, false, 0, payload};
    if (!structQueue_.push(u)) {
        delete payload->preAllocatedBufs;
        delete payload;
    }
}

void AudioEngine::setDrumPadEffect(int track, int pad, int slot, const EffectConfig& cfg) {
    drainRetireQueue();
    int t = std::max(0, std::min(kMaxTracks - 1, track));
    bool needNewBufs = false;
    {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        if (shadowKernel_ && pad >= 0 && pad < kMaxDrumPads && slot >= 0 && slot < kMaxEffects) {
            EffectType prevType = shadowKernel_->tracks[t].drumRack.getPadEffectConfig(pad, slot).type;
            shadowKernel_->tracks[t].drumRack.setPadEffect(pad, slot, cfg);
            needNewBufs = EffectScratchBuffers::needsBuffers(cfg.type) && prevType != cfg.type;
        }
    }
    auto* payload = new EffectUpdatePayload();
    payload->cfg = cfg;
    if (needNewBufs) {
        payload->preAllocatedBufs = new EffectScratchBuffers();
        payload->preAllocatedBufs->prepareForType(cfg.type);
    }
    PendingUpdate u{StructUpdateType::DrumPadEffect, t, pad, false, slot, payload};
    if (!structQueue_.push(u)) {
        delete payload->preAllocatedBufs;
        delete payload;
    }
}

void AudioEngine::setMasterEffect(int slot, const EffectConfig& cfg) {
    drainRetireQueue();
    bool needNewBufs = false;
    {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        if (shadowKernel_ && slot >= 0 && slot < kMaxEffects) {
            EffectType prevType = shadowKernel_->masterEffects[slot].getConfig().type;
            shadowKernel_->masterEffects[slot].setConfig(cfg);
            needNewBufs = EffectScratchBuffers::needsBuffers(cfg.type) && prevType != cfg.type;
        }
    }
    auto* payload = new EffectUpdatePayload();
    payload->cfg = cfg;
    if (needNewBufs) {
        payload->preAllocatedBufs = new EffectScratchBuffers();
        payload->preAllocatedBufs->prepareForType(cfg.type);
    }
    PendingUpdate u{StructUpdateType::MasterEffect, 0, slot, false, 0, payload};
    if (!structQueue_.push(u)) {
        delete payload->preAllocatedBufs;
        delete payload;
    }
}

void AudioEngine::setClipData(int track, int scene, const ClipData& clip) {
    drainRetireQueue();
    int t = std::max(0, std::min(kMaxTracks - 1, track));
    {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        if (shadowKernel_ && scene >= 0 && scene < kMaxScenes) {
            shadowKernel_->tracks[t].clips[scene] = clip;
        }
    }
    auto* box = new ClipData(clip);
    PendingUpdate u{StructUpdateType::ClipData, t, scene, false, 0, box};
    if (!structQueue_.push(u)) delete box;
}

void AudioEngine::loadSampleBuffer(int slot, int sampleRate, int channels, const float* data, int numSamples) {
    samplePool_.setSample(slot, sampleRate, channels, data, numSamples);
}

void AudioEngine::applyPendingStructUpdates() noexcept {
    PendingUpdate u{};
    while (structQueue_.pop(u)) {
        int t = std::max(0, std::min(kMaxTracks - 1, u.track));
        switch (u.type) {
            case StructUpdateType::TrackConfig:
                kernel_.tracks[t].enabled = u.flag;
                kernel_.tracks[t].type = static_cast<TrackType>(std::max(0, std::min(2, u.intVal)));
                break;
            case StructUpdateType::SynthParams:
                if (u.payload) {
                    kernel_.tracks[t].synth.setParams(*static_cast<SynthParams*>(u.payload));
                    retireQueue_.push(RetireEntry{u.payload, &typedDelete<SynthParams>});
                }
                break;
            case StructUpdateType::SamplerParams:
                if (u.payload) {
                    kernel_.tracks[t].sampler.setParams(*static_cast<SamplerParams*>(u.payload));
                    retireQueue_.push(RetireEntry{u.payload, &typedDelete<SamplerParams>});
                }
                break;
            case StructUpdateType::DrumPadParams:
                if (u.payload) {
                    kernel_.tracks[t].drumRack.setPadParams(u.index, *static_cast<DrumPadParams*>(u.payload));
                    retireQueue_.push(RetireEntry{u.payload, &typedDelete<DrumPadParams>});
                }
                break;
            case StructUpdateType::TrackEffect:
                if (u.payload) {
                    auto* ep = static_cast<EffectUpdatePayload*>(u.payload);
                    if (u.index >= 0 && u.index < kMaxEffects) {
                        EffectScratchBuffers* retired = nullptr;
                        kernel_.tracks[t].effects[u.index].applyRtConfig(ep->cfg, ep->preAllocatedBufs, retired);
                        if (retired) {
                            retireQueue_.push(RetireEntry{retired, &typedDelete<EffectScratchBuffers>});
                        }
                    } else if (ep->preAllocatedBufs) {
                        retireQueue_.push(RetireEntry{ep->preAllocatedBufs, &typedDelete<EffectScratchBuffers>});
                    }
                    retireQueue_.push(RetireEntry{ep, &typedDelete<EffectUpdatePayload>});
                }
                break;
            case StructUpdateType::DrumPadEffect:
                if (u.payload) {
                    auto* ep = static_cast<EffectUpdatePayload*>(u.payload);
                    EffectScratchBuffers* retired = nullptr;
                    kernel_.tracks[t].drumRack.applyRtPadEffect(u.index, u.intVal, ep->cfg, ep->preAllocatedBufs, retired);
                    if (retired) {
                        retireQueue_.push(RetireEntry{retired, &typedDelete<EffectScratchBuffers>});
                    }
                    retireQueue_.push(RetireEntry{ep, &typedDelete<EffectUpdatePayload>});
                }
                break;
            case StructUpdateType::MasterEffect:
                if (u.payload) {
                    auto* ep = static_cast<EffectUpdatePayload*>(u.payload);
                    if (u.index >= 0 && u.index < kMaxEffects) {
                        EffectScratchBuffers* retired = nullptr;
                        kernel_.masterEffects[u.index].applyRtConfig(ep->cfg, ep->preAllocatedBufs, retired);
                        if (retired) {
                            retireQueue_.push(RetireEntry{retired, &typedDelete<EffectScratchBuffers>});
                        }
                    } else if (ep->preAllocatedBufs) {
                        retireQueue_.push(RetireEntry{ep->preAllocatedBufs, &typedDelete<EffectScratchBuffers>});
                    }
                    retireQueue_.push(RetireEntry{ep, &typedDelete<EffectUpdatePayload>});
                }
                break;
            case StructUpdateType::ClipData:
                if (u.payload && u.index >= 0 && u.index < kMaxScenes) {
                    kernel_.tracks[t].clips[u.index] = *static_cast<ClipData*>(u.payload);
                    retireQueue_.push(RetireEntry{u.payload, &typedDelete<ClipData>});
                }
                break;
        }
    }
}

void AudioEngine::pollHardwareMidi() noexcept {
    AMidiOutputPort* port = midiOutPort_.load(std::memory_order_acquire);
    if (!port) return;

    uint8_t buf[64];
    int32_t opCode = 0;
    size_t numBytes = 0;
    int64_t timestamp = 0;
    int32_t count = AMidiOutputPort_receive(port, &opCode, buf, sizeof(buf), &numBytes, &timestamp);
    while (count > 0) {
        if (opCode == AMIDI_OPCODE_DATA && numBytes >= 2) {
            uint8_t status = buf[0] & 0xF0;
            int ch = buf[0] & 0x0F;
            int filter = midiFilterChannel_.load(std::memory_order_relaxed);
            if (filter < 0 || filter == ch) {
                int targetTrack = midiTargetTrack_.load(std::memory_order_relaxed);
                if (status == 0x90 && numBytes >= 3) {
                    int note = buf[1] & 0x7F;
                    int vel = buf[2] & 0x7F;
                    EngineCommand c{};
                    c.track = targetTrack;
                    c.arg1 = note;
                    c.f1 = static_cast<float>(vel) / 127.0f;
                    c.type = (vel > 0) ? CommandType::NoteOn : CommandType::NoteOff;
                    kernel_.applyCommand(c);
                } else if (status == 0x80 && numBytes >= 3) {
                    EngineCommand c{CommandType::NoteOff, targetTrack, buf[1] & 0x7F, 0, 0.0f, 0.0f};
                    kernel_.applyCommand(c);
                } else if (status == 0xE0 && numBytes >= 3) {
                    int pb = ((buf[2] & 0x7F) << 7) | (buf[1] & 0x7F);
                    float semi = (static_cast<float>(pb - 8192) / 8192.0f) * 2.0f;
                    EngineCommand c{CommandType::PitchBend, targetTrack, 0, 0, semi, 0.0f};
                    kernel_.applyCommand(c);
                } else if (status == 0xB0 && numBytes >= 3) {
                    int cc = buf[1] & 0x7F;
                    int val = buf[2] & 0x7F;
                    if (cc == 1) {
                        EngineCommand c{CommandType::ModWheel, targetTrack, 0, 0, static_cast<float>(val) / 127.0f, 0.0f};
                        kernel_.applyCommand(c);
                    } else if (cc == 64) {
                        EngineCommand c{CommandType::Sustain, targetTrack, val >= 64 ? 1 : 0, 0, 0.0f, 0.0f};
                        kernel_.applyCommand(c);
                    }
                }
            }
        }
        count = AMidiOutputPort_receive(port, &opCode, buf, sizeof(buf), &numBytes, &timestamp);
    }
}

void AudioEngine::updateTelemetry() noexcept {
    tPlaying_.store(kernel_.isPlaying, std::memory_order_relaxed);
    tCountingIn_.store(kernel_.isCountingIn, std::memory_order_relaxed);
    tBeat_.store(kernel_.transportBeat, std::memory_order_relaxed);
    tTimeSigNum_.store(std::max(1, kernel_.timeSigNum), std::memory_order_relaxed);
    tMasterPeakL_.store(kernel_.masterPeakL, std::memory_order_relaxed);
    tMasterPeakR_.store(kernel_.masterPeakR, std::memory_order_relaxed);
    tMasterClip_.store(kernel_.masterClipping, std::memory_order_relaxed);
    for (int i = 0; i < kMaxTracks; ++i) {
        tTrackPeaks_[i].store(std::max(kernel_.tracks[i].peakL, kernel_.tracks[i].peakR), std::memory_order_relaxed);
        tActiveClips_[i].store(kernel_.tracks[i].activeClip, std::memory_order_relaxed);
        tQueuedClips_[i].store(kernel_.tracks[i].queuedClip, std::memory_order_relaxed);
        tTrackClipBeats_[i].store(kernel_.tracks[i].clipLocalBeat, std::memory_order_relaxed);
    }
    tCapturedCount_.store(kernel_.capturedCount, std::memory_order_relaxed);
    tCaptureActive_.store(kernel_.captureActive, std::memory_order_relaxed);
    telemetrySeq_.fetch_add(1, std::memory_order_release);
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* audioStream,
    void* audioData,
    int32_t numFrames
) {
    if (!audioStream || !audioData || numFrames <= 0) {
        return oboe::DataCallbackResult::Continue;
    }
    float* out = static_cast<float*>(audioData);
    int channels = std::max(1, static_cast<int>(audioStream->getChannelCount()));

    auto xrunRes = audioStream->getXRunCount();
    if (xrunRes && xrunRes.value() > xrunCount_.load(std::memory_order_relaxed)) {
        xrunCount_.store(xrunRes.value(), std::memory_order_relaxed);
    }

    applyPendingStructUpdates();
    pollHardwareMidi();

    EngineCommand cmd{};
    while (cmdQueue_.pop(cmd)) {
        kernel_.applyCommand(cmd);
    }

    int remaining = numFrames;
    int offset = 0;
    while (remaining > 0) {
        int chunk = std::min(remaining, kMaxBlockFrames);
        kernel_.processBlock(out + offset * channels, chunk, channels);
        offset += chunk;
        remaining -= chunk;
    }

    if (resamplingEnabled_.load(std::memory_order_relaxed)) {
        int totalFloats = numFrames * channels;
        for (int i = 0; i < totalFloats; ++i) {
            resampleRing_.push(out[i]);
        }
    }

    if (captureDumpRequested_.load(std::memory_order_acquire)) {
        kernel_.finalizeOpenCaptureNotes();
        captureSnapshotCount_ = kernel_.capturedCount;
        captureSnapshotStartBeat_ = kernel_.captureStartBeat;
        for (int i = 0; i < kernel_.capturedCount; ++i) {
            captureSnapshot_[i] = kernel_.captured[i];
        }
        kernel_.clearCaptureInternal();
        captureDumpRequested_.store(false, std::memory_order_release);
        captureDumpReady_.store(true, std::memory_order_release);
    }
    if (captureClearRequested_.exchange(false, std::memory_order_acq_rel)) {
        kernel_.clearCaptureInternal();
    }

    updateTelemetry();
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
    if (intentionallyStopped_.load(std::memory_order_acquire)) {
        return;
    }
    LOGE("Oboe stream error after close: %s, restarting", oboe::convertToText(error));
    if (error == oboe::Result::ErrorDisconnected) {
        restartStream();
    }
}

std::vector<CapturedNote> AudioEngine::consumeCapturedNotes(double* outStartBeat) {
    drainRetireQueue();
    if (!stream_) {
        kernel_.finalizeOpenCaptureNotes();
        std::vector<CapturedNote> out;
        for (int i = 0; i < kernel_.capturedCount; ++i) out.push_back(kernel_.captured[i]);
        if (outStartBeat) *outStartBeat = kernel_.captureStartBeat;
        kernel_.clearCaptureInternal();
        return out;
    }
    captureDumpReady_.store(false, std::memory_order_release);
    captureDumpRequested_.store(true, std::memory_order_release);
    for (int spin = 0; spin < 50; ++spin) {
        if (captureDumpReady_.load(std::memory_order_acquire)) break;
        struct timespec ts{0, 2000000L}; // 2ms
        nanosleep(&ts, nullptr);
    }
    std::vector<CapturedNote> out;
    if (captureDumpReady_.load(std::memory_order_acquire)) {
        for (int i = 0; i < captureSnapshotCount_; ++i) out.push_back(captureSnapshot_[i]);
        if (outStartBeat) *outStartBeat = captureSnapshotStartBeat_;
    }
    return out;
}

void AudioEngine::clearCaptureBuffer() {
    if (!stream_) {
        kernel_.clearCaptureInternal();
        return;
    }
    postCommand(EngineCommand{CommandType::ClearCapture, 0, 0, 0, 0.0f, 0.0f});
}

void AudioEngine::setResamplingEnabled(bool enabled) {
    if (enabled) resampleRing_.clear();
    resamplingEnabled_.store(enabled, std::memory_order_release);
}

int AudioEngine::drainResampledInterleaved(float* dst, int maxFloats) {
    int count = 0;
    float v = 0.0f;
    while (count < maxFloats && resampleRing_.pop(v)) {
        dst[count++] = v;
    }
    return count;
}

TransportTelemetry AudioEngine::getTelemetry() const noexcept {
    TransportTelemetry t{};
    t.isPlaying = tPlaying_.load(std::memory_order_relaxed);
    t.isCountingIn = tCountingIn_.load(std::memory_order_relaxed);
    t.transportBeat = tBeat_.load(std::memory_order_relaxed);
    int tsNum = std::max(1, tTimeSigNum_.load(std::memory_order_relaxed));
    t.timeSigNum = tsNum;
    double totalBeats = std::max(0.0, t.transportBeat);
    int wholeBeats = static_cast<int>(std::floor(totalBeats));
    t.bar = (wholeBeats / tsNum) + 1;
    t.beat = (wholeBeats % tsNum) + 1;
    t.sixteenth = static_cast<int>(std::floor((totalBeats - wholeBeats) * 4.0)) + 1;
    t.masterPeakL = tMasterPeakL_.load(std::memory_order_relaxed);
    t.masterPeakR = tMasterPeakR_.load(std::memory_order_relaxed);
    t.masterClipping = tMasterClip_.load(std::memory_order_relaxed);
    for (int i = 0; i < kMaxTracks; ++i) {
        t.trackPeaks[i] = tTrackPeaks_[i].load(std::memory_order_relaxed);
        t.activeClips[i] = tActiveClips_[i].load(std::memory_order_relaxed);
        t.queuedClips[i] = tQueuedClips_[i].load(std::memory_order_relaxed);
        t.trackClipBeats[i] = tTrackClipBeats_[i].load(std::memory_order_relaxed);
    }
    t.capturedNoteCount = tCapturedCount_.load(std::memory_order_relaxed);
    t.captureActive = tCaptureActive_.load(std::memory_order_relaxed);
    t.xrunCount = xrunCount_.load(std::memory_order_relaxed);
    t.sampleRate = actualSampleRate_.load(std::memory_order_relaxed);
    t.bufferFrames = actualBufferFrames_.load(std::memory_order_relaxed);
    t.isAAudio = actualAAudio_.load(std::memory_order_relaxed);
    return t;
}

void AudioEngine::attachMidiInputDevice(JNIEnv* env, jobject midiDevice, int portNumber) {
    detachMidiInputDevice();
    if (AMidiDevice_fromJava(env, midiDevice, &midiInDevice_) == AMEDIA_OK) {
        AMidiOutputPort* port = nullptr;
        if (AMidiOutputPort_open(midiInDevice_, portNumber, &port) == AMEDIA_OK) {
            midiOutPort_.store(port, std::memory_order_release);
        }
    }
}

void AudioEngine::detachMidiInputDevice() {
    AMidiOutputPort* port = midiOutPort_.exchange(nullptr, std::memory_order_acq_rel);
    if (port) AMidiOutputPort_close(port);
    if (midiInDevice_) {
        AMidiDevice_release(midiInDevice_);
        midiInDevice_ = nullptr;
    }
}

void AudioEngine::attachMidiOutputDevice(JNIEnv* env, jobject midiDevice, int portNumber) {
    detachMidiOutputDevice();
    std::lock_guard<std::mutex> lock(midiSendMutex_);
    if (AMidiDevice_fromJava(env, midiDevice, &midiOutDevice_) == AMEDIA_OK) {
        AMidiInputPort_open(midiOutDevice_, portNumber, &midiInPort_);
    }
}

void AudioEngine::detachMidiOutputDevice() {
    std::lock_guard<std::mutex> lock(midiSendMutex_);
    if (midiInPort_) {
        AMidiInputPort_close(midiInPort_);
        midiInPort_ = nullptr;
    }
    if (midiOutDevice_) {
        AMidiDevice_release(midiOutDevice_);
        midiOutDevice_ = nullptr;
    }
}

void AudioEngine::sendMidiBytes(const uint8_t* bytes, int count) {
    std::lock_guard<std::mutex> lock(midiSendMutex_);
    if (midiInPort_ && bytes && count > 0) {
        AMidiInputPort_send(midiInPort_, bytes, count);
    }
}

void AudioEngine::setMidiTargetTrack(int track) {
    midiTargetTrack_.store(std::max(0, std::min(kMaxTracks - 1, track)), std::memory_order_relaxed);
}

void AudioEngine::setMidiFilterChannel(int channel) {
    midiFilterChannel_.store(channel, std::memory_order_relaxed);
}

// ---------------- Offline WAV Renderer ----------------
static void writeLe16(std::ofstream& f, uint16_t v) {
    uint8_t b[2] = {static_cast<uint8_t>(v & 0xFF), static_cast<uint8_t>((v >> 8) & 0xFF)};
    f.write(reinterpret_cast<const char*>(b), 2);
}

static void writeLe32(std::ofstream& f, uint32_t v) {
    uint8_t b[4] = {
        static_cast<uint8_t>(v & 0xFF),
        static_cast<uint8_t>((v >> 8) & 0xFF),
        static_cast<uint8_t>((v >> 16) & 0xFF),
        static_cast<uint8_t>((v >> 24) & 0xFF)
    };
    f.write(reinterpret_cast<const char*>(b), 4);
}

bool AudioEngine::renderOfflineWav(
    const std::string& outputPath,
    int bitDepth,
    int sampleRate,
    int scopeMode,
    int sceneIndex,
    int trackIndex,
    double totalBeats,
    std::atomic<float>* progressOut,
    std::atomic<bool>* cancelFlag
) {
    int sr = (sampleRate == 44100) ? 44100 : 48000;
    int bits = (bitDepth == 24 || bitDepth == 32) ? bitDepth : 16;
    int channels = 2;

    std::unique_ptr<EngineKernel> offline;
    {
        std::lock_guard<std::mutex> sl(shadowMutex_);
        offline = std::make_unique<EngineKernel>(shadowKernel_ ? *shadowKernel_ : kernel_);
    }
    offline->clearExternalEffectPointers();
    offline->init(static_cast<float>(sr), &samplePool_);
    for (int t = 0; t < kMaxTracks; ++t) {
        offline->tracks[t].drumRack.prepareLocalEffects();
        for (int e = 0; e < kMaxEffects; ++e) {
            offline->tracks[t].effects[e].setConfig(offline->tracks[t].effects[e].getConfig());
        }
    }
    for (int e = 0; e < kMaxEffects; ++e) {
        offline->masterEffects[e].setConfig(offline->masterEffects[e].getConfig());
    }
    offline->countInBars = 0;
    offline->metronomeEnabled = false;
    offline->resetTransport();
    offline->isCountingIn = false;
    offline->isPlaying = true;

    if (scopeMode == 1 && sceneIndex >= 0 && sceneIndex < kMaxScenes) {
        for (int t = 0; t < kMaxTracks; ++t) {
            int ac = offline->tracks[t].clips[sceneIndex].exists ? sceneIndex : -1;
            offline->tracks[t].activeClip = ac;
            offline->tracks[t].queuedClip = -1;
            offline->tracks[t].clipOriginBeat = 0.0;
            offline->tracks[t].clipLocalBeat = (ac >= 0) ? std::max(0.0, offline->tracks[t].clips[ac].loopStartBeats) : 0.0;
            offline->tracks[t].prevClipLocalBeat = -1.0;
        }
    } else if (scopeMode == 2 && trackIndex >= 0 && trackIndex < kMaxTracks) {
        for (int t = 0; t < kMaxTracks; ++t) {
            if (t != trackIndex) offline->tracks[t].enabled = false;
        }
    }

    double seconds = (totalBeats * 60.0) / std::max(30.0f, offline->bpm);
    uint64_t totalFrames = static_cast<uint64_t>(std::max(1.0, seconds * sr));
    uint32_t bytesPerSample = static_cast<uint32_t>(bits / 8);
    uint32_t dataSize = static_cast<uint32_t>(totalFrames * channels * bytesPerSample);

    std::ofstream out(outputPath, std::ios::binary | std::ios::trunc);
    if (!out.is_open()) return false;

    // Write WAV header
    out.write("RIFF", 4);
    writeLe32(out, 36 + dataSize);
    out.write("WAVE", 4);
    out.write("fmt ", 4);
    writeLe32(out, 16);
    writeLe16(out, bits == 32 ? 3 : 1); // 3 = IEEE float, 1 = PCM
    writeLe16(out, static_cast<uint16_t>(channels));
    writeLe32(out, static_cast<uint32_t>(sr));
    writeLe32(out, static_cast<uint32_t>(sr * channels * bytesPerSample));
    writeLe16(out, static_cast<uint16_t>(channels * bytesPerSample));
    writeLe16(out, static_cast<uint16_t>(bits));
    out.write("data", 4);
    writeLe32(out, dataSize);

    constexpr int kRenderBlock = 512;
    std::vector<float> block(kRenderBlock * channels, 0.0f);
    std::vector<uint8_t> pcmBytes(kRenderBlock * channels * bytesPerSample, 0);

    uint64_t rendered = 0;
    uint64_t actualDataBytes = 0;
    while (rendered < totalFrames) {
        if (cancelFlag && cancelFlag->load(std::memory_order_acquire)) {
            out.close();
            return false;
        }
        int frames = static_cast<int>(std::min<uint64_t>(kRenderBlock, totalFrames - rendered));
        offline->processBlock(block.data(), frames, channels);

        int count = frames * channels;
        if (bits == 16) {
            for (int i = 0; i < count; ++i) {
                float s = clampf(block[i], -1.0f, 1.0f);
                int16_t v = static_cast<int16_t>(std::lrintf(s * 32767.0f));
                pcmBytes[i * 2] = static_cast<uint8_t>(v & 0xFF);
                pcmBytes[i * 2 + 1] = static_cast<uint8_t>((v >> 8) & 0xFF);
            }
            std::streamsize nbytes = static_cast<std::streamsize>(count * 2);
            out.write(reinterpret_cast<const char*>(pcmBytes.data()), nbytes);
            actualDataBytes += static_cast<uint64_t>(nbytes);
        } else if (bits == 24) {
            for (int i = 0; i < count; ++i) {
                float s = clampf(block[i], -1.0f, 1.0f);
                int32_t v = static_cast<int32_t>(std::lrintf(s * 8388607.0f));
                pcmBytes[i * 3] = static_cast<uint8_t>(v & 0xFF);
                pcmBytes[i * 3 + 1] = static_cast<uint8_t>((v >> 8) & 0xFF);
                pcmBytes[i * 3 + 2] = static_cast<uint8_t>((v >> 16) & 0xFF);
            }
            std::streamsize nbytes = static_cast<std::streamsize>(count * 3);
            out.write(reinterpret_cast<const char*>(pcmBytes.data()), nbytes);
            actualDataBytes += static_cast<uint64_t>(nbytes);
        } else {
            std::streamsize nbytes = static_cast<std::streamsize>(count * sizeof(float));
            out.write(reinterpret_cast<const char*>(block.data()), nbytes);
            actualDataBytes += static_cast<uint64_t>(nbytes);
        }
        rendered += frames;
        if (progressOut) {
            progressOut->store(static_cast<float>(rendered) / static_cast<float>(totalFrames), std::memory_order_relaxed);
        }
    }

    // Patch exact RIFF and data chunk sizes before final flush & close
    uint32_t finalDataSize = static_cast<uint32_t>(actualDataBytes);
    out.seekp(4, std::ios::beg);
    writeLe32(out, 36 + finalDataSize);
    out.seekp(40, std::ios::beg);
    writeLe32(out, finalDataSize);

    out.flush();
    bool ok = !out.fail();
    out.close();
    return ok;
}

} // namespace notecraft
