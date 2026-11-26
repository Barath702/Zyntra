package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.audio.CaptureProcessor
import com.example.core.audio.KotlinCaptureBuffer
import com.example.core.common.CaptureMode
import com.example.core.common.GridResolution
import com.example.core.database.NoteCraftDatabase
import com.example.core.project.ClipModel
import com.example.core.project.DemoProjectsFactory
import com.example.core.project.MidiNoteModel
import com.example.core.project.NoteDragMode
import com.example.core.project.NoteSet
import com.example.core.project.PianoRollDragMath
import com.example.core.project.ProjectRepository
import com.example.core.project.ProjectSerializer
import com.example.core.project.StudioProjectTransforms
import com.example.core.project.TrackType
import com.example.core.samples.BuiltInSampleGenerator
import com.example.core.samples.MicRecorder
import com.example.core.samples.MicRecordingState
import com.example.core.samples.SamplePcmTools
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.core.audio.TransportTelemetryState
import com.example.core.audio.WavExportHelper
import com.example.core.common.MelodicPadLayout
import com.example.core.common.NoteRepeatRate
import com.example.core.common.ScaleType
import com.example.core.project.EffectChainTransforms
import com.example.core.project.EffectModel
import com.example.core.project.EffectType
import com.example.feature.drums.DrumRackView
import com.example.feature.instrument.MelodicInstrumentView
import com.example.feature.pianoroll.PianoRollScreen
import com.example.ui.theme.NoteCraftTheme
import org.junit.Rule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: NoteCraftDatabase
    private lateinit var repo: ProjectRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NoteCraftDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ProjectRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name matches ZYNTRA`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("ZYNTRA", appName)
    }

    @Test
    fun `built-in sample generator creates 64 valid wav assets`() {
        val samples = BuiltInSampleGenerator.ensureBuiltInSamples(context)
        assertEquals(64, samples.size)
        val drumCount = samples.count { it.first.id.startsWith("builtin_drum_") }
        assertEquals(16, drumCount)
        val distinctSlots = samples.map { it.first.slotIndex }.toSet()
        assertEquals(64, distinctSlots.size)
        assertTrue(distinctSlots.all { it in 0..63 })
        for ((entity, pcm) in samples) {
            assertTrue("PCM for ${entity.name} should not be empty", pcm.isNotEmpty())
            val wavFile = File(entity.filePath)
            assertTrue("WAV file should exist at ${entity.filePath}", wavFile.exists())
            val decoded = SamplePcmTools.readWav(wavFile.readBytes())
            assertNotNull("WAV file should decode cleanly", decoded)
            assertEquals(48000, decoded!!.sampleRate)
        }
    }

    @Test
    fun `presets drum kits and 8 demo sets are well-formed`() {
        val synthPresets = DemoProjectsFactory.synthPresets()
        assertEquals(12, synthPresets.size)
        val drumKits = DemoProjectsFactory.drumKitPresets()
        assertEquals(12, drumKits.size)
        drumKits.forEach { (kitName, pads) ->
            assertEquals("$kitName should have 16 pads", 16, pads.size)
            pads.forEach { pad ->
                assertTrue("$kitName pad ${pad.name} slot ${pad.sampleSlot} must be within 0..63", pad.sampleSlot in 0..63)
            }
        }
        val demos = DemoProjectsFactory.buildDemoSets()
        assertEquals(8, demos.size)
        assertEquals(8, demos.map { it.id }.toSet().size)
        demos.forEach { demo ->
            assertTrue("${demo.name} should have tracks", demo.tracks.isNotEmpty())
            assertTrue("${demo.name} should contain MIDI notes", demo.tracks.any { t -> t.clips.values.any { it.notes.isNotEmpty() } })
        }

        val emptySet = NoteSet(
            id = "broken_empty",
            name = "Recovered",
            tracks = emptyList()
        )
        val migrated = ProjectSerializer().migrateIfNeeded(emptySet)
        assertEquals(4, migrated.tracks.size)
        assertEquals(8, migrated.scenes.size)
    }

    @Test
    fun `repository seeds 8 demos and supports create rename duplicate autosave delete`() = runBlocking {
        val loadedSamples = repo.initializeIfNeeded()
        assertTrue(loadedSamples.size >= 64)

        val seededProjects = db.projectDao().getAllProjectsOnce()
        assertEquals(8, seededProjects.size)

        // Create new Set
        val created = repo.createNewSet("Studio Sketch", 126f)
        assertEquals("Studio Sketch", created.name)
        assertEquals(126f, created.bpm, 0.01f)
        assertEquals(4, created.tracks.size)
        assertEquals(TrackType.DRUM_RACK, created.tracks[0].type)

        // Reload from disk
        val reloaded = repo.loadSet(created.id)
        assertNotNull(reloaded)
        assertEquals("Studio Sketch", reloaded!!.name)

        // Rename Set
        val renamed = repo.renameSet(created.id, "Midnight Sketch")
        assertNotNull(renamed)
        assertEquals("Midnight Sketch", renamed!!.name)

        // Duplicate Set
        val dup = repo.duplicateSet(created.id)
        assertNotNull(dup)
        assertEquals("Midnight Sketch Copy", dup!!.name)
        assertFalse(dup.id == created.id)

        // Autosave recovery
        repo.writeAutosave(renamed.copy(bpm = 132f))
        val recovered = repo.checkAutosaveRecovery()
        assertNotNull(recovered)
        assertEquals(132f, recovered!!.bpm, 0.01f)
        repo.clearAutosave()
        assertNull(repo.checkAutosaveRecovery())

        // Missing sample detection
        val missingSet = renamed.copy(
            tracks = renamed.tracks.mapIndexed { idx, t ->
                if (idx == 3) t.copy(samplerParams = t.samplerParams.copy(sampleId = "non_existent_user_sample", sampleName = "Ghost Piano")) else t
            }
        )
        val missing = repo.getMissingSamples(missingSet)
        assertEquals(1, missing.size)
        assertTrue(missing[0].contains("Ghost Piano"))

        // Delete Set
        repo.deleteSet(dup.id)
        assertNull(repo.loadSet(dup.id))
    }

    @Test
    fun `capture preserves monotonic timing and distinct pitches when stopped`() {
        var nowNs = 1_000_000_000L
        val buffer = KotlinCaptureBuffer(timeNanos = { nowNs }).apply {
            bpm = 120f // 2 beats per second -> 500ms per beat
            timeoutSeconds = 12
            isTransportPlaying = false
        }

        // Beat 0.0: C4 (60)
        buffer.noteOn(trackIndex = 1, note = 60, velocity = 0.8f)
        nowNs += 250_000_000L // +0.5 beats
        buffer.noteOff(trackIndex = 1, note = 60)

        // Beat 1.0: E4 (64)
        nowNs += 250_000_000L // +0.5 beats -> now at 1.0 beat
        buffer.noteOn(trackIndex = 1, note = 64, velocity = 0.85f)
        nowNs += 500_000_000L // +1.0 beat -> duration ~1.0
        buffer.noteOff(trackIndex = 1, note = 64)

        // Beat 2.5: G4 (67)
        nowNs += 250_000_000L // +0.5 beats -> now at 2.5 beats
        buffer.noteOn(trackIndex = 1, note = 67, velocity = 0.9f)
        nowNs += 250_000_000L
        buffer.noteOff(trackIndex = 1, note = 67)

        val events = buffer.consumeAll()
        assertEquals(3, events.size)

        val clip = CaptureProcessor.buildCapturedClip(
            events = events,
            trackIndex = 1,
            timeSigNum = 4,
            captureMode = CaptureMode.FREE_LENGTH,
            isDrumTrack = false
        )
        assertNotNull(clip)
        assertEquals(3, clip!!.notes.size)
        assertEquals(listOf(60, 64, 67), clip.notes.map { it.note })
        assertEquals(0.0f, clip.notes[0].startBeat, 0.02f)
        assertEquals(1.0f, clip.notes[1].startBeat, 0.02f)
        assertEquals(2.5f, clip.notes[2].startBeat, 0.02f)
        assertEquals(1.0f, clip.notes[1].durationBeats, 0.02f)
    }

    @Test
    fun `capture supports fixed bars, live overdub, and phrase timeout`() {
        var nowNs = 10_000_000_000L
        val buffer = KotlinCaptureBuffer(timeNanos = { nowNs }).apply {
            bpm = 120f
            timeoutSeconds = 4
            isTransportPlaying = true
        }

        // Play drum hits while transport is running at bar 2 (beat 5.0 and 6.0)
        buffer.noteOn(trackIndex = 0, note = 36, velocity = 0.95f, transportBeat = 5.0)
        nowNs += 100_000_000L
        buffer.noteOff(trackIndex = 0, note = 36)

        buffer.noteOn(trackIndex = 0, note = 38, velocity = 0.88f, transportBeat = 6.0)
        nowNs += 100_000_000L
        buffer.noteOff(trackIndex = 0, note = 38)

        val fixedClip = CaptureProcessor.buildCapturedClip(
            events = buffer.consumeAll(),
            trackIndex = 0,
            timeSigNum = 4,
            captureMode = CaptureMode.FIXED_LENGTH,
            fixedBars = 2,
            isDrumTrack = true
        )
        assertNotNull(fixedClip)
        assertEquals(2, fixedClip!!.lengthBars)
        assertEquals(8f, fixedClip.totalBeats(4), 0.01f)
        assertEquals(1.0f, fixedClip.notes[0].startBeat, 0.01f) // beat 5 - barBase 4 = 1.0
        assertEquals(2.0f, fixedClip.notes[1].startBeat, 0.01f) // beat 6 - barBase 4 = 2.0

        // Live Overdub adds a hi-hat without removing existing kick & snare
        buffer.noteOn(trackIndex = 0, note = 40, velocity = 0.72f, transportBeat = 5.5)
        nowNs += 50_000_000L
        buffer.noteOff(trackIndex = 0, note = 40)
        val overdubbed = CaptureProcessor.mergeOverdubIntoClip(
            existing = fixedClip,
            events = buffer.consumeAll(),
            trackIndex = 0,
            timeSigNum = 4,
            isDrumTrack = true
        )
        assertNotNull(overdubbed)
        assertEquals(3, overdubbed!!.notes.size)
        assertTrue(overdubbed.notes.any { it.note == 36 } && overdubbed.notes.any { it.note == 38 } && overdubbed.notes.any { it.note == 40 })

        // Timeout clears stale stopped phrase
        buffer.isTransportPlaying = false
        buffer.noteOn(trackIndex = 1, note = 60, velocity = 0.8f)
        nowNs += 100_000_000L
        buffer.noteOff(trackIndex = 1, note = 60)
        nowNs += 5_000_000_000L // > 4s timeout
        buffer.noteOn(trackIndex = 1, note = 72, velocity = 0.9f)
        val afterTimeout = buffer.consumeAll()
        assertEquals(1, afterTimeout.size)
        assertEquals(72, afterTimeout[0].note)
    }

    @Test
    fun `piano roll cumulative drag, resize, quantize, nudge, and loop transforms work accurately`() {
        val initial = MidiNoteModel(id = "n1", note = 60, startBeat = 1.0f, durationBeats = 1.0f, velocity = 0.8f)

        val movedUp = PianoRollDragMath.applyCumulativeDrag(
            initialNote = initial,
            cumulativeDeltaPx = 12f,
            cumulativeDeltaPy = -60f,
            beatPx = 80f,
            rowPx = 20f,
            mode = NoteDragMode.MOVE,
            snapGridBeats = 0.25f,
            totalBeats = 8f,
            minMidi = 24,
            maxMidi = 96
        )
        assertEquals(63, movedUp.note)
        assertEquals(1.25f, movedUp.startBeat, 0.001f)

        val resizedLeft = PianoRollDragMath.applyCumulativeDrag(
            initialNote = initial,
            cumulativeDeltaPx = -40f,
            cumulativeDeltaPy = 0f,
            beatPx = 80f,
            rowPx = 20f,
            mode = NoteDragMode.RESIZE_LEFT,
            snapGridBeats = 0.25f,
            totalBeats = 8f,
            minMidi = 24,
            maxMidi = 96
        )
        assertEquals(0.5f, resizedLeft.startBeat, 0.001f)
        assertEquals(1.5f, resizedLeft.durationBeats, 0.001f)

        val resizedRight = PianoRollDragMath.applyCumulativeDrag(
            initialNote = initial,
            cumulativeDeltaPx = 60f,
            cumulativeDeltaPy = 0f,
            beatPx = 80f,
            rowPx = 20f,
            mode = NoteDragMode.RESIZE_RIGHT,
            snapGridBeats = 0.25f,
            totalBeats = 8f,
            minMidi = 24,
            maxMidi = 96
        )
        assertEquals(1.0f, resizedRight.startBeat, 0.001f)
        assertEquals(1.75f, resizedRight.durationBeats, 0.001f)

        val offGridClip = ClipModel(
            lengthBars = 1,
            notes = listOf(MidiNoteModel(id = "q1", note = 60, startBeat = 0.62f, durationBeats = 0.5f))
        )
        val q50 = StudioProjectTransforms.quantizeClipNotes(offGridClip, setOf("q1"), GridResolution.QUARTER.beats, strength = 0.5f)
        assertEquals(0.81f, q50.notes[0].startBeat, 0.02f)

        val q100 = StudioProjectTransforms.quantizeClipNotes(offGridClip, setOf("q1"), GridResolution.QUARTER.beats, strength = 1.0f)
        assertEquals(1.0f, q100.notes[0].startBeat, 0.001f)

        val nudged = StudioProjectTransforms.nudgeClipNotes(q100, setOf("q1"), 0.25f)
        assertEquals(1.25f, nudged.notes[0].startBeat, 0.001f)

        val doubled = StudioProjectTransforms.duplicateClipLoop(nudged)
        assertEquals(2, doubled.lengthBars)
        assertEquals(2, doubled.notes.size)
        assertEquals(5.25f, doubled.notes[1].startBeat, 0.001f)
    }

    @Test
    fun `melodic view clear capture X triggers clear not undo and octave 8 switches without hang`() {
        val synthTrack = DemoProjectsFactory.createEmptySet("Test").tracks[1]
        var clearedCount = 0
        var undoCount = 0

        composeRule.setContent {
            NoteCraftTheme {
                MelodicInstrumentView(
                    track = synthTrack,
                    trackIndex = 1,
                    sceneIndex = 0,
                    rootNote = 0,
                    scaleType = ScaleType.NATURAL_MINOR,
                    padLayout = MelodicPadLayout.FOURTHS,
                    showNoteLabels = true,
                    samples = emptyList(),
                    telemetry = TransportTelemetryState(),
                    automationRecording = false,
                    isMicRecording = false,
                    micPeaks = emptyList(),
                    onBack = {},
                    onNoteOn = { _, _ -> },
                    onNoteOff = {},
                    onUpdateSynth = {},
                    onApplySynthPreset = { _, _ -> },
                    onUpdateSampler = {},
                    onAddEffect = {},
                    onUpdateEffect = { _, _ -> },
                    onRemoveEffect = {},
                    onMoveEffect = { _, _ -> },
                    onToggleAutomationRecord = {},
                    onChangeLayout = {},
                    onOpenSampleBrowser = {},
                    onStartMicRecord = {},
                    onStopMicRecord = {},
                    onOpenPianoRoll = {},
                    onCapture = {},
                    onTogglePlay = {},
                    onUndo = { undoCount++ },
                    capturedNoteCount = 3,
                    onClearCapture = { clearedCount++ }
                )
            }
        }

        // Clr text button must not exist anymore; X button must solely invoke onClearCapture
        composeRule.onAllNodesWithText("Clr").assertCountEquals(0)
        composeRule.onNodeWithTag("instrument_clear_capture_button").assertIsDisplayed().performClick()
        assertEquals(1, clearedCount)
        assertEquals(0, undoCount)

        // Switching all the way to top octave (C6) must not hang or crash
        repeat(6) {
            composeRule.onNodeWithTag("melodic_octave_up").performClick()
        }
        composeRule.onNodeWithTag("melodic_octave_label").assertIsDisplayed().assertTextEquals("C6")
    }

    @Test
    fun `piano roll displays icon-only draw select erase tools and working secondary strip`() {
        val baseTrack = DemoProjectsFactory.createEmptySet("PR Test").tracks[1]
        val sampleNote = MidiNoteModel(id = "n_init", note = 60, startBeat = 0.6f, durationBeats = 1f)
        val initialClip = ClipModel(lengthBars = 1, notes = listOf(sampleNote))

        composeRule.setContent {
            NoteCraftTheme {
                var trackState by remember {
                    mutableStateOf(baseTrack.copy(clips = mapOf(0 to initialClip)))
                }
                PianoRollScreen(
                    track = trackState,
                    trackIndex = 1,
                    sceneIndex = 0,
                    telemetry = TransportTelemetryState(),
                    canUndo = true,
                    canRedo = false,
                    onBack = {},
                    onUpdateClip = { _, transform ->
                        val cur = trackState.clips[0] ?: ClipModel()
                        trackState = trackState.copy(clips = trackState.clips + (0 to transform(cur)))
                    },
                    onAuditionNote = { _, _ -> },
                    onAuditionDrumPad = { _, _ -> },
                    onUndo = {},
                    onRedo = {},
                    onTogglePlay = {}
                )
            }
        }

        // Icon-only Draw / Select / Erase pills with NO text labels
        composeRule.onNodeWithTag("pianoroll_tool_draw").assertIsDisplayed()
        composeRule.onNodeWithTag("pianoroll_select_mode").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("pianoroll_tool_erase").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("pianoroll_tool_draw").performClick()
        composeRule.onAllNodesWithText("Draw").assertCountEquals(0)
        composeRule.onAllNodesWithText("Select").assertCountEquals(0)
        composeRule.onAllNodesWithText("Erase").assertCountEquals(0)

        // Secondary bar compact controls work cleanly
        composeRule.onNodeWithTag("pianoroll_quantize_strength_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("pianoroll_quantize_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("pianoroll_velocity_button").performScrollTo().performClick()
        composeRule.onNodeWithText("VEL").assertIsDisplayed()
        composeRule.onNodeWithTag("pianoroll_auto_button").performScrollTo().performClick()
        composeRule.onNodeWithText("No recorded automation in this clip").assertIsDisplayed()
        composeRule.onNodeWithTag("pianoroll_select_all_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("pianoroll_dup_loop_button").performScrollTo().performClick()
        composeRule.onNodeWithText("2 Bars").assertIsDisplayed()
    }

    @Config(qualifiers = "w412dp-h915dp")
    @Test
    fun `synth device and fx tabs support up to 8 effects without name duplication`() {
        val baseTrack = DemoProjectsFactory.createEmptySet("Synth Test").tracks[1].copy(
            effects = listOf(
                EffectModel(id = "fx_delay", type = EffectType.DELAY, params = EffectType.DELAY.defaultParams),
                EffectModel(id = "fx_reverb", type = EffectType.REVERB, params = EffectType.REVERB.defaultParams)
            )
        )

        composeRule.setContent {
            NoteCraftTheme {
                var trackState by remember { mutableStateOf(baseTrack) }
                MelodicInstrumentView(
                    track = trackState,
                    trackIndex = 1,
                    sceneIndex = 0,
                    rootNote = 0,
                    scaleType = ScaleType.NATURAL_MINOR,
                    padLayout = MelodicPadLayout.FOURTHS,
                    showNoteLabels = true,
                    samples = emptyList(),
                    telemetry = TransportTelemetryState(),
                    automationRecording = false,
                    isMicRecording = false,
                    micPeaks = emptyList(),
                    onBack = {},
                    onNoteOn = { _, _ -> },
                    onNoteOff = {},
                    onUpdateSynth = {},
                    onApplySynthPreset = { _, _ -> },
                    onUpdateSampler = {},
                    onAddEffect = { type ->
                        val newFx = EffectModel(type = type, params = type.defaultParams)
                        trackState = trackState.copy(effects = EffectChainTransforms.addEffect(trackState.effects, newFx))
                    },
                    onUpdateEffect = { slot, fx ->
                        trackState = trackState.copy(effects = EffectChainTransforms.updateEffectAt(trackState.effects, slot, fx))
                    },
                    onRemoveEffect = { slot ->
                        trackState = trackState.copy(effects = EffectChainTransforms.removeEffectAt(trackState.effects, slot))
                    },
                    onMoveEffect = { from, to ->
                        trackState = trackState.copy(effects = EffectChainTransforms.moveEffect(trackState.effects, from, to))
                    },
                    onToggleAutomationRecord = {},
                    onChangeLayout = {},
                    onOpenSampleBrowser = {},
                    onStartMicRecord = {},
                    onStopMicRecord = {},
                    onOpenPianoRoll = {},
                    onCapture = {},
                    onTogglePlay = {}
                )
            }
        }

        // Device tab is icon-only (no "Device" text) and FX1/FX2/+ render cleanly
        composeRule.onNodeWithTag("instrument_tab_0").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Device").assertIsDisplayed()
        composeRule.onAllNodesWithText("Device").assertCountEquals(0)
        composeRule.onNodeWithTag("instrument_tab_1").assertIsDisplayed()
        composeRule.onNodeWithTag("instrument_tab_2").assertIsDisplayed()
        composeRule.onNodeWithTag("fx_chain_add_button").assertIsDisplayed()

        // Selecting FX1 shows Delay ONLY in the FX1 tab (no duplicate title inside panel) and keeps Spread/Mod/Dry-Wet knobs
        composeRule.onNodeWithTag("instrument_tab_1").performClick()
        composeRule.onAllNodesWithText("Delay").assertCountEquals(1)
        composeRule.onNodeWithTag("knob_spread").assertIsDisplayed()
        composeRule.onNodeWithTag("knob_mod").assertIsDisplayed()
        composeRule.onNodeWithTag("knob_dry/wet").assertIsDisplayed()

        // Bypass toggle switches ON -> OFF
        composeRule.onNodeWithTag("effect_bypass_switch").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("OFF").assertIsDisplayed()

        // Add FX3 via '+' button (use Saturation so its knobs don't share the "Delay" label)
        composeRule.onNodeWithTag("fx_chain_add_button").performClick()
        composeRule.onNodeWithText("Saturation").performClick()
        composeRule.onNodeWithTag("instrument_tab_3").assertIsDisplayed()
        composeRule.onNodeWithText("FX3").assertIsDisplayed()

        // Reorder FX1 -> FX2 moves Delay cleanly without losing focus, then remove it
        composeRule.onNodeWithTag("instrument_tab_1").performClick()
        composeRule.onNodeWithTag("fx_move_right").performClick()
        composeRule.onNodeWithTag("fx_remove_button").performClick()
        composeRule.onNodeWithTag("instrument_tab_3").assertDoesNotExist()
        composeRule.onAllNodesWithText("Delay (Off)").assertCountEquals(0)
        composeRule.onNodeWithText("Saturation").assertIsDisplayed()

        // Add up to 8 effects (currently 2: Reverb, Saturation -> add 6 more)
        repeat(EffectChainTransforms.MAX_EFFECT_SLOTS - 2) {
            composeRule.onNodeWithTag("fx_chain_add_button").performClick()
            composeRule.onAllNodesWithText("Flanger")[composeRule.onAllNodesWithText("Flanger").fetchSemanticsNodes().lastIndex].performClick()
        }
        composeRule.onNodeWithTag("instrument_tab_8").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("fx_chain_add_button").assertDoesNotExist()
    }

    @Test
    fun `wav export file helper creates sequential (N) wav files on disk and parses header`() {
        val dir = context.filesDir.resolve("export_test").apply { mkdirs() }
        val existing = mutableSetOf<String>()
        val names = (0 until 4).map {
            val n = WavExportHelper.nextUniqueWavFilename("My Song", existing)
            existing += n
            val f = File(dir, n)
            SamplePcmTools.writeWav16(f, 48000, 1, FloatArray(480) { 0.1f })
            val parsed = WavExportHelper.parseWavHeader(f)
            assertNotNull(parsed)
            assertEquals(48000, parsed!!.sampleRate)
            n
        }
        assertEquals(listOf("My_Song.wav", "My_Song(1).wav", "My_Song(2).wav", "My_Song(3).wav"), names)
    }

    @Test
    fun `mic recorder starts in READY state and rejects empty stop`() {
        val mic = MicRecorder()
        assertEquals(MicRecordingState.READY, mic.state.value)
        assertNull(mic.stopAndGetPcm())
    }

    @Config(qualifiers = "w412dp-h915dp")
    @Test
    fun `drum rack renders square 4x4 grid and supports up to 8 individual pad fx`() {
        val baseTrack = DemoProjectsFactory.createEmptySet("Drum FX Test").tracks[0]

        composeRule.setContent {
            NoteCraftTheme {
                var trackState by remember { mutableStateOf(baseTrack) }
                var selectedPad by remember { mutableIntStateOf(0) }
                DrumRackView(
                    track = trackState,
                    trackIndex = 0,
                    sceneIndex = 0,
                    selectedPadIndex = selectedPad,
                    samples = emptyList(),
                    telemetry = TransportTelemetryState(),
                    noteRepeatRate = NoteRepeatRate.OFF,
                    isMicRecording = false,
                    micPeaks = emptyList(),
                    onBack = {},
                    onSelectPad = { selectedPad = it },
                    onTriggerPad = { _, _, _ -> },
                    onReleasePad = {},
                    onUpdatePad = { padIdx, fn ->
                        trackState = trackState.copy(
                            drumPads = trackState.drumPads.map { if (it.index == padIdx) fn(it) else it }
                        )
                    },
                    onApplyKitPreset = { name, pads -> trackState = trackState.copy(presetName = name, drumPads = pads) },
                    onAddKitEffect = {},
                    onUpdateKitEffect = { _, _ -> },
                    onRemoveKitEffect = {},
                    onNoteRepeatChange = {},
                    onOpenSampleBrowserForPad = {},
                    onStartMicRecord = {},
                    onStopMicRecord = { _, _ -> },
                    onUpdateClip = {},
                    onOpenPianoRoll = {},
                    onCapture = {},
                    onTogglePlay = {}
                )
            }
        }

        // Verify 4x4 drum pad grid renders with equal square dimensions in portrait
        val gridBounds = composeRule.onNodeWithTag("drum_pad_grid").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected square drum grid, got ${gridBounds.width}x${gridBounds.height}",
            kotlin.math.abs(gridBounds.width - gridBounds.height) <= 2f
        )

        // Open Pad FX tab and add up to 8 individual effects for Pad 0
        composeRule.onNodeWithTag("drum_tab_2").performClick()
        composeRule.onNodeWithTag("effects_chain_editor").assertIsDisplayed()
        repeat(EffectChainTransforms.MAX_EFFECT_SLOTS) { idx ->
            composeRule.onNodeWithTag("fx_chain_add_button").performClick()
            val items = composeRule.onAllNodesWithText("Delay")
            items[items.fetchSemanticsNodes().lastIndex].performClick()
            composeRule.onNodeWithTag("fx_chain_slot_$idx").performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag("fx_chain_add_button").assertDoesNotExist()
        composeRule.onNodeWithTag("knob_dry/wet").assertIsDisplayed()
    }
}

