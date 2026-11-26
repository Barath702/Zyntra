package com.example.core.project

import com.example.core.common.ScaleType

object DemoProjectsFactory {

    fun defaultDrumPads(kitVariant: Int = 0): List<DrumPadModel> {
        val names = listOf(
            "Kick", "Snare", "Clap", "Rim",
            "Closed Hat", "Open Hat", "Pedal Hat", "Tamb",
            "Low Tom", "Mid Tom", "High Tom", "Cowbell",
            "Perc 1", "Perc 2", "Crash", "Ride"
        )
        return names.mapIndexed { idx, label ->
            val choke = when (idx) {
                4, 5, 6 -> 1 // Hats share choke group 1
                else -> 0
            }
            val pitch = when (kitVariant) {
                1 -> if (idx == 0) -2f else 2f
                2 -> if (idx == 1) -3f else 0f
                else -> 0f
            }
            DrumPadModel(
                index = idx,
                name = label,
                colorIndex = if (idx < 4) 0 else if (idx < 8) 3 else 1,
                sampleId = "builtin_drum_$idx",
                sampleSlot = idx,
                volume = if (idx == 0) 0.95f else if (idx in 4..6) 0.72f else 0.82f,
                pitchSemitones = pitch,
                chokeGroup = choke
            )
        }
    }

    data class SynthPresetSpec(val name: String, val params: SynthParamsModel)

    fun synthPresets(): List<SynthPresetSpec> = listOf(
        SynthPresetSpec(
            "Analog Poly",
            SynthParamsModel(osc1Wave = 0, osc2Wave = 2, oscMix = 0.48f, osc2Detune = 0.56f, cutoff = 0.70f, resonance = 0.24f, unison = 0.35f, release = 0.35f)
        ),
        SynthPresetSpec(
            "Sub Bass",
            SynthParamsModel(osc1Wave = 3, osc2Wave = 1, oscMix = 0.25f, subLevel = 0.85f, cutoff = 0.38f, resonance = 0.28f, filterEnvAmt = 0.4f, decay = 0.3f, sustain = 0.7f, release = 0.16f, unison = 0.1f)
        ),
        SynthPresetSpec(
            "Cyan Arp",
            SynthParamsModel(osc1Wave = 0, osc2Wave = 1, oscMix = 0.52f, osc2Detune = 0.58f, cutoff = 0.76f, resonance = 0.35f, filterEnvAmt = 0.58f, filterDecay = 0.28f, decay = 0.28f, sustain = 0.35f, release = 0.22f, unison = 0.42f)
        ),
        SynthPresetSpec(
            "Warm Pad",
            SynthParamsModel(osc1Wave = 0, osc2Wave = 2, oscMix = 0.55f, osc2Detune = 0.62f, cutoff = 0.54f, resonance = 0.18f, attack = 0.22f, decay = 0.6f, sustain = 0.85f, release = 0.65f, unison = 0.65f, lfoDepth = 0.14f)
        ),
        SynthPresetSpec(
            "Acid Lead",
            SynthParamsModel(osc1Wave = 0, osc2Wave = 0, oscMix = 0.3f, fold = 0.35f, warp = 0.42f, cutoff = 0.44f, resonance = 0.68f, filterEnvAmt = 0.75f, filterDecay = 0.25f, glide = 0.28f, unison = 0.2f)
        ),
        SynthPresetSpec(
            "FM Bell",
            SynthParamsModel(osc1Wave = 3, osc2Wave = 2, oscMix = 0.58f, fold = 0.22f, warp = 0.62f, cutoff = 0.85f, resonance = 0.15f, attack = 0.005f, decay = 0.55f, sustain = 0.22f, release = 0.55f, unison = 0.2f)
        ),
        SynthPresetSpec(
            "FM Reese",
            SynthParamsModel(osc1Wave = 0, osc2Wave = 0, oscMix = 0.5f, osc2Detune = 0.66f, subLevel = 0.6f, fold = 0.25f, cutoff = 0.46f, resonance = 0.32f, unison = 0.58f, sustain = 0.85f, release = 0.24f)
        ),
        SynthPresetSpec(
            "Deep Chord",
            SynthParamsModel(osc1Wave = 0, osc2Wave = 1, oscMix = 0.45f, osc2Detune = 0.54f, cutoff = 0.48f, resonance = 0.26f, filterEnvAmt = 0.52f, filterDecay = 0.32f, attack = 0.012f, decay = 0.42f, sustain = 0.45f, release = 0.32f, unison = 0.4f)
        ),
        SynthPresetSpec(
            "Crystal Pluck",
            SynthParamsModel(osc1Wave = 2, osc2Wave = 3, oscMix = 0.42f, warp = 0.34f, cutoff = 0.82f, resonance = 0.22f, filterEnvAmt = 0.45f, filterDecay = 0.22f, decay = 0.32f, sustain = 0.15f, release = 0.35f, unison = 0.32f)
        ),
        SynthPresetSpec(
            "Hollow Keys",
            SynthParamsModel(osc1Wave = 1, osc2Wave = 3, oscMix = 0.4f, cutoff = 0.62f, resonance = 0.14f, attack = 0.015f, decay = 0.52f, sustain = 0.55f, release = 0.42f, lfoDepth = 0.08f)
        ),
        SynthPresetSpec(
            "UKG Organ",
            SynthParamsModel(osc1Wave = 3, osc2Wave = 1, oscMix = 0.46f, osc2Detune = 0.56f, fold = 0.18f, subLevel = 0.45f, cutoff = 0.58f, resonance = 0.2f, attack = 0.004f, decay = 0.28f, sustain = 0.6f, release = 0.14f)
        ),
        SynthPresetSpec(
            "Tape Drift",
            SynthParamsModel(osc1Wave = 2, osc2Wave = 0, oscMix = 0.38f, osc2Detune = 0.59f, cutoff = 0.52f, resonance = 0.18f, attack = 0.04f, decay = 0.48f, sustain = 0.72f, release = 0.48f, lfoDepth = 0.22f, lfoRate = 0.18f, unison = 0.45f)
        )
    )

    private fun DrumPadModel.withSample(name: String, id: String, slot: Int, pitch: Float = 0f, decay: Float = this.decay, tone: Float = this.toneFilter, vol: Float = this.volume): DrumPadModel =
        copy(name = name, sampleId = id, sampleSlot = slot, pitchSemitones = pitch, decay = decay, toneFilter = tone, volume = vol)

    fun drumKitPresets(): List<Pair<String, List<DrumPadModel>>> {
        val core909 = defaultDrumPads(0)

        val deep808 = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("808 Sub", "builtin_perc_808_kick", 20, decay = 1.0f)
                1 -> pad.withSample("808 Snr", "builtin_perc_808_snare", 21)
                4 -> pad.withSample("Shaker", "builtin_perc_shaker", 22)
                12 -> pad.withSample("Wood", "builtin_perc_woodblock", 23)
                13 -> pad.withSample("Zap FX", "builtin_perc_zap", 25)
                else -> pad.copy(pitchSemitones = -2f)
            }
        }

        val trapKit = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("Trap 808", "builtin_trap_808_boom", 32, decay = 1.0f, vol = 0.98f)
                1 -> pad.withSample("Trap Snr", "builtin_trap_snare", 35)
                2 -> pad.withSample("Snap Clap", "builtin_trap_clap", 33)
                3 -> pad.withSample("Lo Rim", "builtin_lofi_rim", 39, pitch = 2f)
                4 -> pad.withSample("Trap Hat", "builtin_trap_hat", 34)
                5 -> pad.withSample("Open Hat", "builtin_drum_5", 5, pitch = 3f)
                6 -> pad.withSample("Shaker", "builtin_perc_shaker", 22)
                12 -> pad.withSample("Laser", "builtin_perc_zap", 25)
                13 -> pad.withSample("Sub Drop", "builtin_fx_subdrop", 31)
                else -> pad.copy(pitchSemitones = 1f)
            }
        }

        val hipHopKit = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("Vinyl Kick", "builtin_hiphop_kick", 36, vol = 0.96f)
                1 -> pad.withSample("Crunch Snr", "builtin_hiphop_snare", 37)
                2 -> pad.withSample("Dust Clap", "builtin_drum_2", 2, pitch = -3f, tone = 0.72f)
                3 -> pad.withSample("Wood Rim", "builtin_lofi_rim", 39)
                4 -> pad.withSample("Tape Hat", "builtin_lofi_hat", 38)
                7 -> pad.withSample("Shaker", "builtin_perc_shaker", 22, tone = 0.78f)
                12 -> pad.withSample("Kalimba", "builtin_kalimba", 28)
                13 -> pad.withSample("Lo-Fi Keys", "builtin_lofi_piano", 30)
                else -> pad.copy(pitchSemitones = -2f, toneFilter = 0.80f)
            }
        }

        val lofiKit = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("Warm Kick", "builtin_hiphop_kick", 36, pitch = -2f, tone = 0.70f)
                1 -> pad.withSample("Brush Snr", "builtin_acoustic_snare", 51, pitch = -2f, tone = 0.72f)
                2 -> pad.withSample("Soft Clap", "builtin_drum_2", 2, pitch = -4f, tone = 0.65f)
                3 -> pad.withSample("Wood Rim", "builtin_lofi_rim", 39, tone = 0.75f)
                4 -> pad.withSample("Tape Hat", "builtin_lofi_hat", 38, tone = 0.72f)
                5 -> pad.withSample("Soft Open", "builtin_drum_5", 5, pitch = -3f, tone = 0.68f)
                7 -> pad.withSample("Shaker", "builtin_perc_shaker", 22, tone = 0.68f)
                12 -> pad.withSample("Tape Pno", "builtin_lofi_piano", 30)
                13 -> pad.withSample("Wurli Hit", "builtin_keys_wurli", 60)
                else -> pad.copy(pitchSemitones = -3f, toneFilter = 0.70f)
            }
        }

        val houseKit = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("House Kick", "builtin_house_kick", 40, vol = 0.96f)
                1 -> pad.withSample("909 Snare", "builtin_drum_1", 1, pitch = 1f)
                2 -> pad.withSample("House Clap", "builtin_house_clap", 41)
                4 -> pad.withSample("Metal Hat", "builtin_techno_hat", 43)
                5 -> pad.withSample("909 Open", "builtin_drum_5", 5, pitch = 1f)
                7 -> pad.withSample("Shaker", "builtin_perc_shaker", 22)
                8 -> pad.withSample("Conga", "builtin_organic_conga", 48)
                12 -> pad.withSample("House Stab", "builtin_deep_chord_stab", 44)
                else -> pad
            }
        }

        val technoKit = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("Rumble Kck", "builtin_techno_kick", 42, decay = 0.92f, vol = 0.98f)
                1 -> pad.withSample("Hard Snare", "builtin_drum_1", 1, pitch = 2f)
                2 -> pad.withSample("House Clap", "builtin_house_clap", 41, pitch = -1f)
                3 -> pad.withSample("Micro Clk", "builtin_minimal_click", 45)
                4 -> pad.withSample("909 Hat", "builtin_techno_hat", 43)
                8 -> pad.withSample("Sine Tom", "builtin_minimal_sub", 47)
                12 -> pad.withSample("FM Hit", "builtin_exp_fm_hit", 55)
                13 -> pad.withSample("Noise Swp", "builtin_fx_riser", 63)
                else -> pad.copy(pitchSemitones = 1f)
            }
        }

        val deepHouseKit = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("Deep Kick", "builtin_house_kick", 40, pitch = -2f, tone = 0.80f)
                1 -> pad.withSample("808 Snr", "builtin_perc_808_snare", 21, pitch = -1f)
                2 -> pad.withSample("Soft Clap", "builtin_house_clap", 41, tone = 0.78f)
                4 -> pad.withSample("Tape Hat", "builtin_lofi_hat", 38)
                7 -> pad.withSample("Shaker", "builtin_perc_shaker", 22)
                8 -> pad.withSample("Conga", "builtin_organic_conga", 48)
                9 -> pad.withSample("Bongo Hi", "builtin_perc_bongo", 24)
                12 -> pad.withSample("Deep Stab", "builtin_deep_chord_stab", 44)
                13 -> pad.withSample("Rhodes", "builtin_rhodes", 16)
                else -> pad.copy(toneFilter = 0.82f)
            }
        }

        val minimalPerc = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("Sine Kick", "builtin_minimal_sub", 47, pitch = -5f, vol = 0.95f)
                1 -> pad.withSample("Micro Clk", "builtin_minimal_click", 45)
                2 -> pad.withSample("Clave", "builtin_organic_clave", 49)
                3 -> pad.withSample("Wood Rim", "builtin_lofi_rim", 39)
                4 -> pad.withSample("FM Blip", "builtin_minimal_blip", 46)
                5 -> pad.withSample("Trap Hat", "builtin_trap_hat", 34, pitch = -2f)
                8 -> pad.withSample("Sine Tom", "builtin_minimal_sub", 47)
                9 -> pad.withSample("Woodblock", "builtin_perc_woodblock", 23)
                12 -> pad.withSample("Glitch", "builtin_exp_glitch", 54)
                else -> pad.copy(toneFilter = 0.86f)
            }
        }

        val organicPerc = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("Warm Kick", "builtin_acoustic_kick", 50, pitch = -3f, tone = 0.78f)
                1 -> pad.withSample("Brush Snr", "builtin_acoustic_snare", 51)
                2 -> pad.withSample("Clave", "builtin_organic_clave", 49)
                3 -> pad.withSample("Wood Rim", "builtin_lofi_rim", 39)
                4 -> pad.withSample("Shaker", "builtin_perc_shaker", 22)
                8 -> pad.withSample("Conga", "builtin_organic_conga", 48)
                9 -> pad.withSample("Bongo Hi", "builtin_perc_bongo", 24)
                10 -> pad.withSample("Wood", "builtin_perc_woodblock", 23)
                12 -> pad.withSample("Kalimba", "builtin_kalimba", 28)
                13 -> pad.withSample("Marimba", "builtin_keys_marimba", 61)
                else -> pad.copy(toneFilter = 0.84f)
            }
        }

        val acousticKit = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("Ac Kick", "builtin_acoustic_kick", 50, vol = 0.95f)
                1 -> pad.withSample("Brush Snr", "builtin_acoustic_snare", 51)
                2 -> pad.withSample("Wood Rim", "builtin_lofi_rim", 39)
                4 -> pad.withSample("Ac Hat", "builtin_acoustic_hat", 52)
                5 -> pad.withSample("Open Hat", "builtin_drum_5", 5, tone = 0.85f)
                7 -> pad.withSample("Shaker", "builtin_perc_shaker", 22)
                15 -> pad.withSample("Jazz Ride", "builtin_acoustic_ride", 53)
                else -> pad.copy(toneFilter = 0.88f)
            }
        }

        val experimentalKit = core909.mapIndexed { idx, pad ->
            when (idx) {
                0 -> pad.withSample("Sub Drop", "builtin_fx_subdrop", 31, pitch = 4f)
                1 -> pad.withSample("FM Hit", "builtin_exp_fm_hit", 55)
                2 -> pad.withSample("Glitch", "builtin_exp_glitch", 54)
                3 -> pad.withSample("Micro Clk", "builtin_minimal_click", 45, pitch = 5f)
                4 -> pad.withSample("FM Blip", "builtin_minimal_blip", 46, pitch = 3f)
                5 -> pad.withSample("Noise Burst", "builtin_exp_noise_burst", 56)
                8 -> pad.withSample("Laser Zap", "builtin_perc_zap", 25)
                12 -> pad.withSample("Brass Bell", "builtin_bell", 29)
                13 -> pad.withSample("Riser FX", "builtin_fx_riser", 63)
                else -> pad.copy(pitchSemitones = 4f)
            }
        }

        return listOf(
            "909 Core Kit" to core909,
            "808 Deep Kit" to deep808,
            "Trap Kit" to trapKit,
            "Hip-Hop Kit" to hipHopKit,
            "Lo-Fi Kit" to lofiKit,
            "House Kit" to houseKit,
            "Techno Kit" to technoKit,
            "Deep House Kit" to deepHouseKit,
            "Minimal Perc" to minimalPerc,
            "Organic Perc" to organicPerc,
            "Acoustic Kit" to acousticKit,
            "Experimental Kit" to experimentalKit
        )
    }

    private fun kitByName(name: String): List<DrumPadModel> =
        drumKitPresets().firstOrNull { it.first == name }?.second ?: defaultDrumPads(0)

    private fun synthByName(name: String): SynthParamsModel =
        synthPresets().firstOrNull { it.name == name }?.params ?: SynthParamsModel()

    fun createEmptySet(name: String, bpm: Float = 120f): NoteSet {
        return NoteSet(
            name = name,
            bpm = bpm,
            rootNote = 0,
            scaleType = ScaleType.NATURAL_MINOR,
            tracks = listOf(
                TrackModel(
                    name = "Drums",
                    colorIndex = 0,
                    type = TrackType.DRUM_RACK,
                    presetName = "909 Core Kit",
                    drumPads = defaultDrumPads(0),
                    effects = listOf(
                        EffectModel(type = EffectType.SATURATION, bypassed = false, wetDry = 0.25f, presetName = "Drum Glue")
                    )
                ),
                TrackModel(
                    name = "Bass",
                    colorIndex = 2,
                    type = TrackType.SUBTRACTIVE_SYNTH,
                    presetName = "Sub Bass",
                    synthParams = synthByName("Sub Bass")
                ),
                TrackModel(
                    name = "Synth",
                    colorIndex = 1,
                    type = TrackType.SUBTRACTIVE_SYNTH,
                    presetName = "Analog Poly",
                    synthParams = synthByName("Analog Poly"),
                    effects = listOf(
                        EffectModel(type = EffectType.CHORUS, bypassed = false, wetDry = 0.35f, presetName = "Wide"),
                        EffectModel(type = EffectType.REVERB, bypassed = false, wetDry = 0.3f, presetName = "Studio")
                    )
                ),
                TrackModel(
                    name = "Keys",
                    colorIndex = 4,
                    type = TrackType.MELODIC_SAMPLER,
                    presetName = "Electric Keys",
                    samplerParams = SamplerParamsModel(
                        sampleId = "builtin_rhodes", sampleName = "Electric Keys", sampleSlot = 16,
                        attack = 0.01f, release = 0.38f, cutoff = 0.9f
                    ),
                    effects = listOf(
                        EffectModel(type = EffectType.DELAY, bypassed = false, wetDry = 0.25f, presetName = "Quarter Ping")
                    )
                )
            )
        )
    }

    fun buildDemoSets(): List<NoteSet> = listOf(
        createNeonPulseSet(),
        createDeepMotionSet(),
        createMidnightDriveSet(),
        createDustyLoopsSet(),
        createFutureBounceSet(),
        createMinimalRitualSet(),
        createOrganicMotionSet(),
        createTrapSequenceSet()
    )

    private fun createNeonPulseSet(): NoteSet {
        val drumNotes1 = buildList {
            for (b in 0 until 4) {
                add(MidiNoteModel(note = 36, startBeat = b.toFloat(), durationBeats = 0.25f, velocity = 0.95f))
                add(MidiNoteModel(note = 40, startBeat = b + 0.5f, durationBeats = 0.2f, velocity = 0.78f))
                add(MidiNoteModel(note = 40, startBeat = b + 0.25f, durationBeats = 0.15f, velocity = 0.48f))
                add(MidiNoteModel(note = 40, startBeat = b + 0.75f, durationBeats = 0.15f, velocity = 0.58f))
            }
            add(MidiNoteModel(note = 38, startBeat = 1.0f, durationBeats = 0.25f, velocity = 0.9f))
            add(MidiNoteModel(note = 38, startBeat = 3.0f, durationBeats = 0.25f, velocity = 0.92f))
            add(MidiNoteModel(note = 37, startBeat = 1.0f, durationBeats = 0.25f, velocity = 0.65f))
            add(MidiNoteModel(note = 37, startBeat = 3.0f, durationBeats = 0.25f, velocity = 0.65f))
        }
        val drumNotes2 = drumNotes1 + listOf(
            MidiNoteModel(note = 41, startBeat = 3.5f, durationBeats = 0.25f, velocity = 0.8f),
            MidiNoteModel(note = 44, startBeat = 3.75f, durationBeats = 0.2f, velocity = 0.75f)
        )
        val bassNotes = listOf(
            MidiNoteModel(note = 36, startBeat = 0f, durationBeats = 0.75f, velocity = 0.9f),
            MidiNoteModel(note = 36, startBeat = 1f, durationBeats = 0.5f, velocity = 0.82f),
            MidiNoteModel(note = 39, startBeat = 1.5f, durationBeats = 0.5f, velocity = 0.85f),
            MidiNoteModel(note = 43, startBeat = 2f, durationBeats = 0.75f, velocity = 0.9f),
            MidiNoteModel(note = 41, startBeat = 3f, durationBeats = 0.5f, velocity = 0.85f),
            MidiNoteModel(note = 39, startBeat = 3.5f, durationBeats = 0.45f, velocity = 0.8f)
        )
        val leadNotes = listOf(
            MidiNoteModel(note = 60, startBeat = 0f, durationBeats = 0.5f, velocity = 0.85f),
            MidiNoteModel(note = 63, startBeat = 0.75f, durationBeats = 0.5f, velocity = 0.8f),
            MidiNoteModel(note = 67, startBeat = 1.5f, durationBeats = 0.75f, velocity = 0.9f),
            MidiNoteModel(note = 70, startBeat = 2.5f, durationBeats = 0.5f, velocity = 0.82f),
            MidiNoteModel(note = 67, startBeat = 3.25f, durationBeats = 0.5f, velocity = 0.78f)
        )
        val chordNotes = listOf(
            MidiNoteModel(note = 60, startBeat = 0f, durationBeats = 1.8f, velocity = 0.72f),
            MidiNoteModel(note = 63, startBeat = 0f, durationBeats = 1.8f, velocity = 0.72f),
            MidiNoteModel(note = 67, startBeat = 0f, durationBeats = 1.8f, velocity = 0.72f),
            MidiNoteModel(note = 58, startBeat = 2f, durationBeats = 1.8f, velocity = 0.72f),
            MidiNoteModel(note = 62, startBeat = 2f, durationBeats = 1.8f, velocity = 0.72f),
            MidiNoteModel(note = 65, startBeat = 2f, durationBeats = 1.8f, velocity = 0.72f)
        )

        val base = createEmptySet("Neon Pulse", bpm = 124f).copy(
            id = "demo_neon_pulse",
            isDemo = true,
            artworkSeed = 2048,
            scenes = listOf(
                SceneModel(name = "Intro", colorIndex = 1),
                SceneModel(name = "Main Drop", colorIndex = 0),
                SceneModel(name = "Break", colorIndex = 4)
            ) + (4..8).map { SceneModel(name = "Scene $it") }
        )
        return base.copy(
            tracks = listOf(
                base.tracks[0].copy(
                    presetName = "909 Core Kit",
                    clips = mapOf(
                        0 to ClipModel(name = "Pulse Beat", colorIndex = 0, lengthBars = 1, notes = drumNotes1),
                        1 to ClipModel(name = "Drop Beat", colorIndex = 0, lengthBars = 1, notes = drumNotes2)
                    )
                ),
                base.tracks[1].copy(
                    presetName = "Sub Bass",
                    clips = mapOf(
                        0 to ClipModel(name = "Sub Riff", colorIndex = 2, lengthBars = 1, notes = bassNotes),
                        1 to ClipModel(name = "Driving Bass", colorIndex = 2, lengthBars = 1, notes = bassNotes)
                    )
                ),
                base.tracks[2].copy(
                    presetName = "Cyan Arp",
                    synthParams = synthByName("Cyan Arp"),
                    clips = mapOf(
                        1 to ClipModel(name = "Neon Hook", colorIndex = 1, lengthBars = 1, notes = leadNotes)
                    )
                ),
                base.tracks[3].copy(
                    presetName = "Electric Keys",
                    clips = mapOf(
                        0 to ClipModel(name = "Warm Chords", colorIndex = 4, lengthBars = 1, notes = chordNotes),
                        1 to ClipModel(name = "Warm Chords", colorIndex = 4, lengthBars = 1, notes = chordNotes),
                        2 to ClipModel(name = "Break Keys", colorIndex = 4, lengthBars = 1, notes = chordNotes)
                    )
                )
            )
        )
    }

    private fun createDeepMotionSet(): NoteSet {
        val houseBeat = (0..3).flatMap { b ->
            listOf(
                MidiNoteModel(note = 36, startBeat = b.toFloat(), durationBeats = 0.25f, velocity = 0.96f),
                MidiNoteModel(note = 40, startBeat = b + 0.5f, durationBeats = 0.2f, velocity = 0.76f),
                MidiNoteModel(note = 43, startBeat = b + 0.75f, durationBeats = 0.18f, velocity = 0.58f)
            )
        } + listOf(
            MidiNoteModel(note = 38, startBeat = 1f, durationBeats = 0.2f, velocity = 0.88f),
            MidiNoteModel(note = 38, startBeat = 3f, durationBeats = 0.2f, velocity = 0.9f),
            MidiNoteModel(note = 44, startBeat = 2.75f, durationBeats = 0.2f, velocity = 0.65f)
        )
        val ukgBass = listOf(
            MidiNoteModel(note = 33, startBeat = 0f, durationBeats = 0.6f, velocity = 0.9f),
            MidiNoteModel(note = 33, startBeat = 0.75f, durationBeats = 0.4f, velocity = 0.82f),
            MidiNoteModel(note = 36, startBeat = 1.5f, durationBeats = 0.45f, velocity = 0.85f),
            MidiNoteModel(note = 38, startBeat = 2.25f, durationBeats = 0.5f, velocity = 0.88f),
            MidiNoteModel(note = 40, startBeat = 3f, durationBeats = 0.6f, velocity = 0.85f)
        )
        val stabs = listOf(
            MidiNoteModel(note = 57, startBeat = 0.75f, durationBeats = 0.4f, velocity = 0.8f),
            MidiNoteModel(note = 60, startBeat = 0.75f, durationBeats = 0.4f, velocity = 0.78f),
            MidiNoteModel(note = 64, startBeat = 0.75f, durationBeats = 0.4f, velocity = 0.78f),
            MidiNoteModel(note = 57, startBeat = 2.5f, durationBeats = 0.45f, velocity = 0.82f),
            MidiNoteModel(note = 60, startBeat = 2.5f, durationBeats = 0.45f, velocity = 0.8f),
            MidiNoteModel(note = 64, startBeat = 2.5f, durationBeats = 0.45f, velocity = 0.8f)
        )
        val base = createEmptySet("Deep Motion", bpm = 122f).copy(
            id = "demo_deep_motion",
            isDemo = true,
            rootNote = 9,
            scaleType = ScaleType.NATURAL_MINOR,
            swing = 0.14f,
            artworkSeed = 3190,
            scenes = listOf(
                SceneModel(name = "Warm Up", colorIndex = 1),
                SceneModel(name = "Groove", colorIndex = 2),
                SceneModel(name = "Late Club", colorIndex = 5)
            ) + (4..8).map { SceneModel(name = "Scene $it") }
        )
        return base.copy(
            tracks = listOf(
                base.tracks[0].copy(
                    presetName = "Deep House Kit",
                    drumPads = kitByName("Deep House Kit"),
                    clips = mapOf(
                        0 to ClipModel(name = "4x4 Pulse", colorIndex = 0, notes = houseBeat.filter { it.note != 44 }),
                        1 to ClipModel(name = "Deep Pocket", colorIndex = 0, notes = houseBeat)
                    )
                ),
                base.tracks[1].copy(
                    presetName = "UKG Organ",
                    synthParams = synthByName("UKG Organ"),
                    clips = mapOf(
                        0 to ClipModel(name = "Organ Sub", colorIndex = 2, notes = ukgBass),
                        1 to ClipModel(name = "Organ Sub", colorIndex = 2, notes = ukgBass)
                    )
                ),
                base.tracks[2].copy(
                    presetName = "Deep Chord",
                    synthParams = synthByName("Deep Chord"),
                    effects = listOf(
                        EffectModel(type = EffectType.DELAY, wetDry = 0.36f, presetName = "Dub Echo"),
                        EffectModel(type = EffectType.REVERB, wetDry = 0.40f, presetName = "Club Hall")
                    ),
                    clips = mapOf(
                        1 to ClipModel(name = "Minor Stab", colorIndex = 1, notes = stabs),
                        2 to ClipModel(name = "Echo Stabs", colorIndex = 1, notes = stabs)
                    )
                ),
                base.tracks[3].copy(
                    presetName = "Deep Stab",
                    samplerParams = SamplerParamsModel(sampleId = "builtin_deep_chord_stab", sampleName = "Deep House Stab", sampleSlot = 44, rootNote = 48, release = 0.45f),
                    clips = mapOf(2 to ClipModel(name = "Stab Wash", colorIndex = 4, notes = listOf(
                        MidiNoteModel(note = 48, startBeat = 0f, durationBeats = 1.5f, velocity = 0.78f),
                        MidiNoteModel(note = 45, startBeat = 2f, durationBeats = 1.5f, velocity = 0.74f)
                    )))
                )
            )
        )
    }

    private fun createMidnightDriveSet(): NoteSet {
        val beat = (0..3).flatMap { b ->
            listOf(
                MidiNoteModel(note = 36, startBeat = b.toFloat(), durationBeats = 0.25f, velocity = 0.94f),
                MidiNoteModel(note = 40, startBeat = b + 0.5f, durationBeats = 0.2f, velocity = 0.75f)
            )
        } + listOf(
            MidiNoteModel(note = 37, startBeat = 1f, durationBeats = 0.25f, velocity = 0.92f),
            MidiNoteModel(note = 37, startBeat = 3f, durationBeats = 0.25f, velocity = 0.94f)
        )
        val arpBass = (0 until 16).map { s ->
            val pitch = when {
                s < 8 -> 33
                s < 12 -> 36
                else -> 38
            }
            MidiNoteModel(note = pitch, startBeat = s * 0.25f, durationBeats = 0.2f, velocity = if (s % 2 == 0) 0.9f else 0.72f)
        }
        val melody = listOf(
            MidiNoteModel(note = 69, startBeat = 0f, durationBeats = 1.0f, velocity = 0.85f),
            MidiNoteModel(note = 72, startBeat = 1.25f, durationBeats = 0.75f, velocity = 0.8f),
            MidiNoteModel(note = 76, startBeat = 2.0f, durationBeats = 1.25f, velocity = 0.9f),
            MidiNoteModel(note = 74, startBeat = 3.5f, durationBeats = 0.45f, velocity = 0.82f)
        )
        val base = createEmptySet("Midnight Drive", bpm = 118f).copy(
            id = "demo_midnight_drive",
            isDemo = true,
            rootNote = 9,
            scaleType = ScaleType.NATURAL_MINOR,
            artworkSeed = 5127,
            scenes = listOf(
                SceneModel(name = "Highway", colorIndex = 6),
                SceneModel(name = "Neon Chorus", colorIndex = 1),
                SceneModel(name = "Outro", colorIndex = 4)
            ) + (4..8).map { SceneModel(name = "Scene $it") }
        )
        return base.copy(
            tracks = listOf(
                base.tracks[0].copy(presetName = "909 Core Kit", clips = mapOf(0 to ClipModel(name = "Retro Gate", colorIndex = 0, notes = beat), 1 to ClipModel(name = "Retro Gate", colorIndex = 0, notes = beat))),
                base.tracks[1].copy(presetName = "FM Reese", synthParams = synthByName("FM Reese"), clips = mapOf(0 to ClipModel(name = "16th Drive", colorIndex = 2, notes = arpBass), 1 to ClipModel(name = "16th Drive", colorIndex = 2, notes = arpBass))),
                base.tracks[2].copy(presetName = "Analog Poly", synthParams = synthByName("Analog Poly"), clips = mapOf(1 to ClipModel(name = "Night Lead", colorIndex = 1, notes = melody))),
                base.tracks[3].copy(presetName = "Warm String Pad", samplerParams = SamplerParamsModel(sampleId = "builtin_pad", sampleName = "Warm String Pad", sampleSlot = 17, attack = 0.08f, release = 0.65f), clips = mapOf(0 to ClipModel(name = "Pad Bed", colorIndex = 4, notes = listOf(MidiNoteModel(note = 57, startBeat = 0f, durationBeats = 3.8f, velocity = 0.68f))), 2 to ClipModel(name = "Outro Pad", colorIndex = 4, notes = listOf(MidiNoteModel(note = 57, startBeat = 0f, durationBeats = 3.8f, velocity = 0.65f)))))
            )
        )
    }

    private fun createDustyLoopsSet(): NoteSet {
        val beat = listOf(
            MidiNoteModel(note = 36, startBeat = 0f, durationBeats = 0.25f, velocity = 0.92f),
            MidiNoteModel(note = 36, startBeat = 1.75f, durationBeats = 0.25f, velocity = 0.8f),
            MidiNoteModel(note = 36, startBeat = 2.5f, durationBeats = 0.25f, velocity = 0.86f),
            MidiNoteModel(note = 37, startBeat = 1.0f, durationBeats = 0.2f, velocity = 0.84f),
            MidiNoteModel(note = 37, startBeat = 3.0f, durationBeats = 0.2f, velocity = 0.86f),
            MidiNoteModel(note = 40, startBeat = 0.5f, durationBeats = 0.2f, velocity = 0.56f),
            MidiNoteModel(note = 40, startBeat = 1.5f, durationBeats = 0.2f, velocity = 0.62f),
            MidiNoteModel(note = 40, startBeat = 2.5f, durationBeats = 0.2f, velocity = 0.56f),
            MidiNoteModel(note = 40, startBeat = 3.5f, durationBeats = 0.2f, velocity = 0.66f)
        )
        val keys = listOf(
            MidiNoteModel(note = 65, startBeat = 0f, durationBeats = 1.75f, velocity = 0.72f),
            MidiNoteModel(note = 68, startBeat = 0f, durationBeats = 1.75f, velocity = 0.72f),
            MidiNoteModel(note = 72, startBeat = 0f, durationBeats = 1.75f, velocity = 0.75f),
            MidiNoteModel(note = 63, startBeat = 2f, durationBeats = 1.75f, velocity = 0.72f),
            MidiNoteModel(note = 67, startBeat = 2f, durationBeats = 1.75f, velocity = 0.72f),
            MidiNoteModel(note = 70, startBeat = 2f, durationBeats = 1.75f, velocity = 0.75f)
        )
        val base = createEmptySet("Dusty Loops", bpm = 88f).copy(
            id = "demo_dusty_loops",
            isDemo = true,
            swing = 0.35f,
            rootNote = 5,
            scaleType = ScaleType.DORIAN,
            artworkSeed = 4419,
            scenes = listOf(
                SceneModel(name = "Tape A", colorIndex = 3),
                SceneModel(name = "B-Side", colorIndex = 4)
            ) + (3..8).map { SceneModel(name = "Scene $it") }
        )
        return base.copy(
            tracks = listOf(
                base.tracks[0].copy(
                    presetName = "Lo-Fi Kit",
                    drumPads = kitByName("Lo-Fi Kit"),
                    effects = listOf(EffectModel(type = EffectType.BITCRUSHER, wetDry = 0.16f, presetName = "12-bit Dust")),
                    clips = mapOf(0 to ClipModel(name = "Dusty Groove", colorIndex = 3, notes = beat), 1 to ClipModel(name = "Vinyl Pocket", colorIndex = 3, notes = beat))
                ),
                base.tracks[1].copy(
                    presetName = "Sub Bass",
                    clips = mapOf(0 to ClipModel(name = "Tape Sub", colorIndex = 2, notes = listOf(
                        MidiNoteModel(note = 41, startBeat = 0f, durationBeats = 1.5f, velocity = 0.85f),
                        MidiNoteModel(note = 39, startBeat = 2f, durationBeats = 1.5f, velocity = 0.85f)
                    )))
                ),
                base.tracks[2].copy(
                    presetName = "Tape Drift",
                    synthParams = synthByName("Tape Drift"),
                    clips = mapOf(1 to ClipModel(name = "Warble Top", colorIndex = 1, notes = listOf(
                        MidiNoteModel(note = 77, startBeat = 0.5f, durationBeats = 0.75f, velocity = 0.66f),
                        MidiNoteModel(note = 75, startBeat = 2.5f, durationBeats = 0.75f, velocity = 0.68f)
                    )))
                ),
                base.tracks[3].copy(
                    presetName = "Lo-Fi Piano",
                    samplerParams = SamplerParamsModel(sampleId = "builtin_lofi_piano", sampleName = "Lo-Fi Tape Piano", sampleSlot = 30, release = 0.45f, cutoff = 0.78f),
                    effects = listOf(EffectModel(type = EffectType.CHORUS, wetDry = 0.42f, presetName = "Tape Wobble")),
                    clips = mapOf(0 to ClipModel(name = "Lo-Fi Chords", colorIndex = 4, notes = keys), 1 to ClipModel(name = "Lo-Fi Chords", colorIndex = 4, notes = keys))
                )
            )
        )
    }

    private fun createFutureBounceSet(): NoteSet {
        val bounceBeat = listOf(
            MidiNoteModel(note = 36, startBeat = 0f, durationBeats = 0.25f, velocity = 0.96f),
            MidiNoteModel(note = 36, startBeat = 1.5f, durationBeats = 0.25f, velocity = 0.88f),
            MidiNoteModel(note = 36, startBeat = 2.75f, durationBeats = 0.25f, velocity = 0.9f),
            MidiNoteModel(note = 38, startBeat = 1f, durationBeats = 0.25f, velocity = 0.9f),
            MidiNoteModel(note = 38, startBeat = 3f, durationBeats = 0.25f, velocity = 0.92f),
            MidiNoteModel(note = 40, startBeat = 0.5f, durationBeats = 0.15f, velocity = 0.74f),
            MidiNoteModel(note = 40, startBeat = 1.5f, durationBeats = 0.15f, velocity = 0.74f),
            MidiNoteModel(note = 40, startBeat = 2.5f, durationBeats = 0.15f, velocity = 0.74f),
            MidiNoteModel(note = 40, startBeat = 3.5f, durationBeats = 0.15f, velocity = 0.74f)
        )
        val bounceBass = listOf(
            MidiNoteModel(note = 43, startBeat = 0f, durationBeats = 0.35f, velocity = 0.9f),
            MidiNoteModel(note = 43, startBeat = 0.75f, durationBeats = 0.35f, velocity = 0.86f),
            MidiNoteModel(note = 46, startBeat = 1.5f, durationBeats = 0.35f, velocity = 0.88f),
            MidiNoteModel(note = 48, startBeat = 2.25f, durationBeats = 0.4f, velocity = 0.9f),
            MidiNoteModel(note = 46, startBeat = 3.25f, durationBeats = 0.45f, velocity = 0.85f)
        )
        val lead = listOf(
            MidiNoteModel(note = 67, startBeat = 0.25f, durationBeats = 0.3f, velocity = 0.84f),
            MidiNoteModel(note = 70, startBeat = 0.75f, durationBeats = 0.3f, velocity = 0.82f),
            MidiNoteModel(note = 74, startBeat = 1.5f, durationBeats = 0.5f, velocity = 0.9f),
            MidiNoteModel(note = 72, startBeat = 2.5f, durationBeats = 0.3f, velocity = 0.85f),
            MidiNoteModel(note = 70, startBeat = 3.25f, durationBeats = 0.4f, velocity = 0.82f)
        )
        val base = createEmptySet("Future Bounce", bpm = 128f).copy(
            id = "demo_future_bounce",
            isDemo = true,
            rootNote = 7,
            scaleType = ScaleType.NATURAL_MINOR,
            swing = 0.22f,
            artworkSeed = 6402
        )
        return base.copy(
            tracks = listOf(
                base.tracks[0].copy(presetName = "House Kit", drumPads = kitByName("House Kit"), clips = mapOf(0 to ClipModel(name = "2-Step Bounce", colorIndex = 0, notes = bounceBeat))),
                base.tracks[1].copy(presetName = "UKG Organ", synthParams = synthByName("UKG Organ"), clips = mapOf(0 to ClipModel(name = "Bounce Sub", colorIndex = 2, notes = bounceBass))),
                base.tracks[2].copy(presetName = "Crystal Pluck", synthParams = synthByName("Crystal Pluck"), clips = mapOf(0 to ClipModel(name = "Lead Hook", colorIndex = 1, notes = lead))),
                base.tracks[3].copy(presetName = "Warm Wurli", samplerParams = SamplerParamsModel(sampleId = "builtin_keys_wurli", sampleName = "Warm Wurli Keys", sampleSlot = 60, release = 0.35f), clips = mapOf(0 to ClipModel(name = "Wurli Stab", colorIndex = 4, notes = listOf(MidiNoteModel(note = 67, startBeat = 1f, durationBeats = 0.4f, velocity = 0.76f), MidiNoteModel(note = 65, startBeat = 3f, durationBeats = 0.4f, velocity = 0.76f)))))
            )
        )
    }

    private fun createMinimalRitualSet(): NoteSet {
        val minBeat = (0..3).flatMap { b ->
            listOf(
                MidiNoteModel(note = 36, startBeat = b.toFloat(), durationBeats = 0.25f, velocity = 0.95f),
                MidiNoteModel(note = 40, startBeat = b + 0.5f, durationBeats = 0.15f, velocity = 0.7f),
                MidiNoteModel(note = 39, startBeat = b + 0.75f, durationBeats = 0.12f, velocity = 0.55f)
            )
        } + listOf(
            MidiNoteModel(note = 38, startBeat = 1.5f, durationBeats = 0.2f, velocity = 0.8f),
            MidiNoteModel(note = 44, startBeat = 3.25f, durationBeats = 0.2f, velocity = 0.78f)
        )
        val rolling = (0..15).map { s ->
            MidiNoteModel(note = if (s % 4 == 3) 40 else 33, startBeat = s * 0.25f, durationBeats = 0.18f, velocity = if (s % 4 == 0) 0.45f else 0.88f)
        }
        val base = createEmptySet("Minimal Ritual", bpm = 126f).copy(
            id = "demo_minimal_ritual",
            isDemo = true,
            rootNote = 9,
            scaleType = ScaleType.PHRYGIAN,
            artworkSeed = 7831
        )
        return base.copy(
            tracks = listOf(
                base.tracks[0].copy(presetName = "Minimal Perc", drumPads = kitByName("Minimal Perc"), clips = mapOf(0 to ClipModel(name = "Micro Pulse", colorIndex = 5, notes = minBeat))),
                base.tracks[1].copy(presetName = "Acid Lead", synthParams = synthByName("Acid Lead"), clips = mapOf(0 to ClipModel(name = "Acid Roll", colorIndex = 2, notes = rolling))),
                base.tracks[2].copy(presetName = "FM Bell", synthParams = synthByName("FM Bell"), effects = listOf(EffectModel(type = EffectType.DELAY, wetDry = 0.45f, presetName = "Dub Echo")), clips = mapOf(0 to ClipModel(name = "Hypno Ping", colorIndex = 1, notes = listOf(MidiNoteModel(note = 69, startBeat = 0.75f, durationBeats = 0.25f, velocity = 0.84f), MidiNoteModel(note = 70, startBeat = 2.75f, durationBeats = 0.25f, velocity = 0.76f)))))
            )
        )
    }

    private fun createOrganicMotionSet(): NoteSet {
        val orgBeat = listOf(
            MidiNoteModel(note = 36, startBeat = 0f, durationBeats = 0.25f, velocity = 0.88f),
            MidiNoteModel(note = 36, startBeat = 2.25f, durationBeats = 0.25f, velocity = 0.8f),
            MidiNoteModel(note = 44, startBeat = 1.0f, durationBeats = 0.2f, velocity = 0.76f),
            MidiNoteModel(note = 45, startBeat = 1.75f, durationBeats = 0.2f, velocity = 0.74f),
            MidiNoteModel(note = 38, startBeat = 3.0f, durationBeats = 0.2f, velocity = 0.8f),
            MidiNoteModel(note = 40, startBeat = 0.5f, durationBeats = 0.15f, velocity = 0.55f),
            MidiNoteModel(note = 40, startBeat = 1.5f, durationBeats = 0.15f, velocity = 0.6f),
            MidiNoteModel(note = 40, startBeat = 2.5f, durationBeats = 0.15f, velocity = 0.55f),
            MidiNoteModel(note = 40, startBeat = 3.5f, durationBeats = 0.15f, velocity = 0.65f)
        )
        val arpNotes = listOf(62, 64, 66, 69, 74, 69, 66, 64).mapIndexed { i, n ->
            MidiNoteModel(note = n, startBeat = i * 0.5f, durationBeats = 0.4f, velocity = 0.78f)
        }
        val base = createEmptySet("Organic Motion", bpm = 108f).copy(
            id = "demo_organic_motion",
            isDemo = true,
            rootNote = 2,
            scaleType = ScaleType.PENTATONIC_MAJOR,
            swing = 0.18f,
            artworkSeed = 9104
        )
        return base.copy(
            tracks = listOf(
                base.tracks[0].copy(presetName = "Organic Perc", drumPads = kitByName("Organic Perc"), clips = mapOf(0 to ClipModel(name = "Wood & Skin", colorIndex = 3, notes = orgBeat))),
                base.tracks[1].copy(presetName = "Sub Bass", clips = mapOf(0 to ClipModel(name = "Warm Root", colorIndex = 2, notes = listOf(MidiNoteModel(note = 38, startBeat = 0f, durationBeats = 1.8f, velocity = 0.85f), MidiNoteModel(note = 45, startBeat = 2f, durationBeats = 1.8f, velocity = 0.8f))))),
                base.tracks[2].copy(presetName = "Hollow Keys", synthParams = synthByName("Hollow Keys"), effects = listOf(EffectModel(type = EffectType.REVERB, wetDry = 0.52f, presetName = "Shimmer")), clips = mapOf(0 to ClipModel(name = "Marimba Arp", colorIndex = 6, notes = arpNotes))),
                base.tracks[3].copy(presetName = "Kalimba", samplerParams = SamplerParamsModel(sampleId = "builtin_kalimba", sampleName = "Kalimba Mallet", sampleSlot = 28, release = 0.42f), clips = mapOf(0 to ClipModel(name = "Kalimba Motif", colorIndex = 4, notes = arpNotes)))
            )
        )
    }

    private fun createTrapSequenceSet(): NoteSet {
        val trapBeat = buildList {
            add(MidiNoteModel(note = 36, startBeat = 0f, durationBeats = 0.75f, velocity = 0.98f))
            add(MidiNoteModel(note = 36, startBeat = 1.5f, durationBeats = 0.5f, velocity = 0.88f))
            add(MidiNoteModel(note = 36, startBeat = 2.75f, durationBeats = 0.5f, velocity = 0.94f))
            add(MidiNoteModel(note = 37, startBeat = 2.0f, durationBeats = 0.25f, velocity = 0.95f))
            add(MidiNoteModel(note = 38, startBeat = 2.0f, durationBeats = 0.25f, velocity = 0.85f))
            for (s in 0 until 16) {
                add(MidiNoteModel(note = 40, startBeat = s * 0.25f, durationBeats = 0.12f, velocity = if (s in 12..15) 0.88f else 0.65f))
            }
            add(MidiNoteModel(note = 40, startBeat = 3.125f, durationBeats = 0.1f, velocity = 0.72f))
            add(MidiNoteModel(note = 40, startBeat = 3.375f, durationBeats = 0.1f, velocity = 0.74f))
        }
        val trapLead = listOf(
            MidiNoteModel(note = 64, startBeat = 0f, durationBeats = 0.5f, velocity = 0.84f),
            MidiNoteModel(note = 67, startBeat = 0.5f, durationBeats = 0.5f, velocity = 0.8f),
            MidiNoteModel(note = 71, startBeat = 1.0f, durationBeats = 0.75f, velocity = 0.88f),
            MidiNoteModel(note = 72, startBeat = 2.0f, durationBeats = 0.75f, velocity = 0.86f),
            MidiNoteModel(note = 71, startBeat = 3.0f, durationBeats = 0.75f, velocity = 0.82f)
        )
        val base = createEmptySet("Trap Sequence", bpm = 140f).copy(
            id = "demo_trap_sequence",
            isDemo = true,
            rootNote = 4,
            scaleType = ScaleType.PHRYGIAN,
            artworkSeed = 8842
        )
        return base.copy(
            tracks = listOf(
                base.tracks[0].copy(presetName = "Trap Kit", drumPads = kitByName("Trap Kit"), clips = mapOf(0 to ClipModel(name = "Half-Time 808", colorIndex = 0, notes = trapBeat))),
                base.tracks[1].copy(presetName = "Sub Bass", clips = mapOf(0 to ClipModel(name = "808 Glide", colorIndex = 2, notes = listOf(MidiNoteModel(note = 40, startBeat = 0f, durationBeats = 1.5f, velocity = 0.94f), MidiNoteModel(note = 41, startBeat = 2.5f, durationBeats = 1.25f, velocity = 0.9f))))),
                base.tracks[2].copy(presetName = "FM Bell", synthParams = synthByName("FM Bell"), effects = listOf(EffectModel(type = EffectType.REVERB, wetDry = 0.48f, presetName = "Dark Hall")), clips = mapOf(0 to ClipModel(name = "Bell Arps", colorIndex = 1, notes = trapLead))),
                base.tracks[3].copy(presetName = "Brass Bell", samplerParams = SamplerParamsModel(sampleId = "builtin_bell", sampleName = "Brass Bell", sampleSlot = 29, release = 0.55f), clips = mapOf(0 to ClipModel(name = "Counter Bell", colorIndex = 4, notes = trapLead)))
            )
        )
    }
}

