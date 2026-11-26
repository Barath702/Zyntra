package com.example.core.common

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "notecraft_settings")

data class SettingsState(
    val highContrastTheme: Boolean = false,
    val sampleRate: Int = 48000,
    val bufferFrames: Int = 256,
    val performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val metronomeEnabled: Boolean = false,
    val countInBars: Int = 0,
    val defaultQuantization: GlobalQuantization = GlobalQuantization.ONE_BAR,
    val defaultRootNote: Int = 0,
    val defaultScale: ScaleType = ScaleType.NATURAL_MINOR,
    val showPadNoteLabels: Boolean = true,
    val showSampleLabels: Boolean = true,
    val melodicLayout: MelodicPadLayout = MelodicPadLayout.FOURTHS,
    val midiFilterChannel: Int = -1, // -1 = Omni
    val autosaveIntervalSec: Int = 15,
    val captureTimeoutSec: Int = 12,
    val captureMode: CaptureMode = CaptureMode.FREE_LENGTH,
    val fixedCaptureBars: Int = 4,
    val onboardingCompleted: Boolean = false
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val SAMPLE_RATE = intPreferencesKey("sample_rate")
        val BUFFER_FRAMES = intPreferencesKey("buffer_frames")
        val PROFILE = intPreferencesKey("perf_profile")
        val METRONOME = booleanPreferencesKey("metronome")
        val COUNT_IN = intPreferencesKey("count_in")
        val QUANTIZATION = intPreferencesKey("quantization")
        val ROOT_NOTE = intPreferencesKey("root_note")
        val SCALE_ORD = intPreferencesKey("scale_ord")
        val PAD_LABELS = booleanPreferencesKey("pad_labels")
        val SAMPLE_LABELS = booleanPreferencesKey("sample_labels")
        val MELODIC_LAYOUT = intPreferencesKey("melodic_layout")
        val MIDI_CHANNEL = intPreferencesKey("midi_channel")
        val AUTOSAVE_SEC = intPreferencesKey("autosave_sec")
        val CAPTURE_TIMEOUT_SEC = intPreferencesKey("capture_timeout_sec")
        val CAPTURE_MODE = intPreferencesKey("capture_mode")
        val FIXED_CAPTURE_BARS = intPreferencesKey("fixed_capture_bars")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
    }

    val settingsFlow: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            highContrastTheme = prefs[Keys.HIGH_CONTRAST] ?: false,
            sampleRate = prefs[Keys.SAMPLE_RATE] ?: 48000,
            bufferFrames = prefs[Keys.BUFFER_FRAMES] ?: 256,
            performanceProfile = PerformanceProfile.entries.getOrElse(prefs[Keys.PROFILE] ?: 1) { PerformanceProfile.BALANCED },
            metronomeEnabled = prefs[Keys.METRONOME] ?: false,
            countInBars = prefs[Keys.COUNT_IN] ?: 0,
            defaultQuantization = GlobalQuantization.entries.getOrElse(prefs[Keys.QUANTIZATION] ?: 3) { GlobalQuantization.ONE_BAR },
            defaultRootNote = prefs[Keys.ROOT_NOTE] ?: 0,
            defaultScale = ScaleType.entries.getOrElse(prefs[Keys.SCALE_ORD] ?: 1) { ScaleType.NATURAL_MINOR },
            showPadNoteLabels = prefs[Keys.PAD_LABELS] ?: true,
            showSampleLabels = prefs[Keys.SAMPLE_LABELS] ?: true,
            melodicLayout = MelodicPadLayout.entries.getOrElse(prefs[Keys.MELODIC_LAYOUT] ?: 0) { MelodicPadLayout.FOURTHS },
            midiFilterChannel = prefs[Keys.MIDI_CHANNEL] ?: -1,
            autosaveIntervalSec = prefs[Keys.AUTOSAVE_SEC] ?: 15,
            captureTimeoutSec = prefs[Keys.CAPTURE_TIMEOUT_SEC] ?: 12,
            captureMode = CaptureMode.entries.getOrElse(prefs[Keys.CAPTURE_MODE] ?: 0) { CaptureMode.FREE_LENGTH },
            fixedCaptureBars = (prefs[Keys.FIXED_CAPTURE_BARS] ?: 4).coerceIn(1, 16),
            onboardingCompleted = prefs[Keys.ONBOARDING] ?: false
        )
    }

    suspend fun update(transform: (SettingsState) -> SettingsState) {
        context.dataStore.edit { prefs ->
            val cur = SettingsState(
                highContrastTheme = prefs[Keys.HIGH_CONTRAST] ?: false,
                sampleRate = prefs[Keys.SAMPLE_RATE] ?: 48000,
                bufferFrames = prefs[Keys.BUFFER_FRAMES] ?: 256,
                performanceProfile = PerformanceProfile.entries.getOrElse(prefs[Keys.PROFILE] ?: 1) { PerformanceProfile.BALANCED },
                metronomeEnabled = prefs[Keys.METRONOME] ?: false,
                countInBars = prefs[Keys.COUNT_IN] ?: 0,
                defaultQuantization = GlobalQuantization.entries.getOrElse(prefs[Keys.QUANTIZATION] ?: 3) { GlobalQuantization.ONE_BAR },
                defaultRootNote = prefs[Keys.ROOT_NOTE] ?: 0,
                defaultScale = ScaleType.entries.getOrElse(prefs[Keys.SCALE_ORD] ?: 1) { ScaleType.NATURAL_MINOR },
                showPadNoteLabels = prefs[Keys.PAD_LABELS] ?: true,
                showSampleLabels = prefs[Keys.SAMPLE_LABELS] ?: true,
                melodicLayout = MelodicPadLayout.entries.getOrElse(prefs[Keys.MELODIC_LAYOUT] ?: 0) { MelodicPadLayout.FOURTHS },
                midiFilterChannel = prefs[Keys.MIDI_CHANNEL] ?: -1,
                autosaveIntervalSec = prefs[Keys.AUTOSAVE_SEC] ?: 15,
                captureTimeoutSec = prefs[Keys.CAPTURE_TIMEOUT_SEC] ?: 12,
                captureMode = CaptureMode.entries.getOrElse(prefs[Keys.CAPTURE_MODE] ?: 0) { CaptureMode.FREE_LENGTH },
                fixedCaptureBars = (prefs[Keys.FIXED_CAPTURE_BARS] ?: 4).coerceIn(1, 16),
                onboardingCompleted = prefs[Keys.ONBOARDING] ?: false
            )
            val next = transform(cur)
            prefs[Keys.HIGH_CONTRAST] = next.highContrastTheme
            prefs[Keys.SAMPLE_RATE] = next.sampleRate
            prefs[Keys.BUFFER_FRAMES] = next.bufferFrames
            prefs[Keys.PROFILE] = next.performanceProfile.ordinal
            prefs[Keys.METRONOME] = next.metronomeEnabled
            prefs[Keys.COUNT_IN] = next.countInBars
            prefs[Keys.QUANTIZATION] = next.defaultQuantization.ordinal
            prefs[Keys.ROOT_NOTE] = next.defaultRootNote
            prefs[Keys.SCALE_ORD] = next.defaultScale.ordinal
            prefs[Keys.PAD_LABELS] = next.showPadNoteLabels
            prefs[Keys.SAMPLE_LABELS] = next.showSampleLabels
            prefs[Keys.MELODIC_LAYOUT] = next.melodicLayout.ordinal
            prefs[Keys.MIDI_CHANNEL] = next.midiFilterChannel
            prefs[Keys.AUTOSAVE_SEC] = next.autosaveIntervalSec
            prefs[Keys.CAPTURE_TIMEOUT_SEC] = next.captureTimeoutSec
            prefs[Keys.CAPTURE_MODE] = next.captureMode.ordinal
            prefs[Keys.FIXED_CAPTURE_BARS] = next.fixedCaptureBars.coerceIn(1, 16)
            prefs[Keys.ONBOARDING] = next.onboardingCompleted
        }
    }
}
