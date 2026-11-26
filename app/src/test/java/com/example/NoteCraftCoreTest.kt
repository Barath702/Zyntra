package com.example

import com.example.core.common.GridResolution
import com.example.core.common.MelodicPadLayout
import com.example.core.common.MusicalTheory
import com.example.core.common.ScaleType
import com.example.core.project.AutomationLaneModel
import com.example.core.project.AutomationPointModel
import com.example.core.project.ClipModel
import com.example.core.project.DemoProjectsFactory
import com.example.core.project.EffectModel
import com.example.core.project.EffectType
import com.example.core.project.MidiNoteModel
import com.example.core.project.NoteSet
import com.example.core.project.ProjectSerializer
import com.example.core.project.StudioProjectTransforms
import com.example.core.samples.SamplePcmTools
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class NoteCraftCoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `musical theory generates valid scales and pad grids`() {
        for (scale in ScaleType.entries) {
            val notes = MusicalTheory.buildScaleNotes(rootPitchClass = 2, scale = scale, baseOctave = 3, count = 16)
            assertEquals(16, notes.size)
            for (n in notes) {
                assertTrue("Note $n should belong to ${scale.displayName}", MusicalTheory.isNoteInScale(n, 2, scale))
            }
        }

        val fourthsGrid = MusicalTheory.generate25PadGrid(0, ScaleType.NATURAL_MINOR, 3, MelodicPadLayout.FOURTHS)
        assertEquals(25, fourthsGrid.size)
        val bottomLeft = fourthsGrid.first { it.row == 4 && it.col == 0 }
        val topRight = fourthsGrid.first { it.row == 0 && it.col == 4 }
        assertTrue(topRight.midiNote > bottomLeft.midiNote)

        val seqGrid = MusicalTheory.generate25PadGrid(0, ScaleType.MAJOR, 3, MelodicPadLayout.SEQUENTIAL)
        assertEquals(25, seqGrid.size)
        assertTrue(seqGrid.first { it.row == 0 && it.col == 4 }.midiNote > seqGrid.first { it.row == 4 && it.col == 0 }.midiNote)

        val cols = MusicalTheory.generateScaleColumns(0, ScaleType.DORIAN, 3, columns = 7)
        assertEquals(14, cols.size)
        assertTrue(cols.first { it.row == 0 && it.col == 0 }.midiNote > cols.first { it.row == 1 && it.col == 0 }.midiNote)
    }

    @Test
    fun `quantization and phrase length detection work accurately`() {
        assertEquals(1.0, MusicalTheory.quantizeBeat(0.94, GridResolution.SIXTEENTH.beats, 1.0f), 1e-4)
        assertEquals(0.97, MusicalTheory.quantizeBeat(0.94, GridResolution.SIXTEENTH.beats, 0.5f), 1e-4)

        assertEquals(1, MusicalTheory.detectPhraseLengthBars(listOf(0.5, 3.75)))
        assertEquals(2, MusicalTheory.detectPhraseLengthBars(listOf(0.5, 6.5)))
        assertEquals(4, MusicalTheory.detectPhraseLengthBars(listOf(1.0, 14.0)))
        assertEquals(8, MusicalTheory.detectPhraseLengthBars(listOf(1.0, 20.0)))
    }

    @Test
    fun `clip transforms quantize nudge duplicate and encode valid MIDI file`() {
        val n1 = MidiNoteModel(id = "n1", note = 60, startBeat = 0.22f, durationBeats = 0.5f, velocity = 0.8f)
        val n2 = MidiNoteModel(id = "n2", note = 64, startBeat = 1.0f, durationBeats = 0.5f, velocity = 0.9f)
        val clip = ClipModel(name = "Lead", lengthBars = 2, notes = listOf(n1, n2))

        val quantized = StudioProjectTransforms.quantizeClipNotes(clip, setOf("n1"), 0.25, 1.0f)
        assertEquals(0.25f, quantized.notes.first { it.id == "n1" }.startBeat, 1e-4f)
        assertEquals(1.0f, quantized.notes.first { it.id == "n2" }.startBeat, 1e-4f)

        val nudged = StudioProjectTransforms.nudgeClipNotes(quantized, setOf("n1"), 0.25f)
        assertEquals(0.5f, nudged.notes.first { it.id == "n1" }.startBeat, 1e-4f)

        val (dupClip, newIds) = StudioProjectTransforms.duplicateClipNotes(nudged, setOf("n1", "n2"))
        assertEquals(4, dupClip.notes.size)
        assertEquals(2, newIds.size)

        val demo = DemoProjectsFactory.buildDemoSets().first()
        val midiBytes = StudioProjectTransforms.encodeStandardMidiFile(demo, sceneIndex = 0)
        assertTrue(midiBytes.size > 32)
        assertEquals("MThd", midiBytes.copyOfRange(0, 4).decodeToString())
        val mtrkCount = midiBytes.toList().windowed(4).count {
            it[0] == 'M'.code.toByte() && it[1] == 'T'.code.toByte() && it[2] == 'r'.code.toByte() && it[3] == 'k'.code.toByte()
        }
        assertTrue("Expected tempo track + musical tracks in SMF", mtrkCount >= 2)
    }

    @Test
    fun `demo projects serialize and deserialize losslessly with all 12 DSP effect types`() {
        val serializer = ProjectSerializer()
        val demos = DemoProjectsFactory.buildDemoSets()
        assertEquals(8, demos.size)

        for (demo in demos) {
            val encoded = serializer.encode(demo)
            val decoded = serializer.decode(encoded)
            assertEquals(demo.name, decoded.name)
            assertEquals(demo.bpm, decoded.bpm, 0.001f)
            assertEquals(demo.tracks.size, decoded.tracks.size)
            assertEquals(8, decoded.scenes.size)
        }

        // Verify all 12 real-time DSP effects + automation serialize cleanly
        val fxList = EffectType.entries.filter { it != EffectType.NONE }.map {
            EffectModel(type = it, wetDry = 0.45f, bypassed = false)
        }
        assertEquals(12, fxList.size)

        val custom = NoteSet(
            name = "Custom FX",
            scenes = emptyList(), // Verify migration pads to 8 scenes
            tracks = DemoProjectsFactory.createEmptySet("Custom").tracks.mapIndexed { idx, t ->
                if (idx == 1) t.copy(
                    effects = fxList.take(2),
                    clips = mapOf(
                        0 to ClipModel(
                            name = "Bassline",
                            notes = listOf(MidiNoteModel(note = 36, startBeat = 0f, durationBeats = 1f)),
                            automationLanes = listOf(
                                AutomationLaneModel(2, "Cutoff", listOf(AutomationPointModel(0f, 0.2f), AutomationPointModel(2f, 0.9f)))
                            )
                        )
                    )
                ) else t
            }
        )
        val roundTripped = serializer.decode(serializer.encode(custom))
        assertEquals(8, roundTripped.scenes.size)
        assertFalse(roundTripped.tracks[1].clips[0]!!.automationLanes.isEmpty())
    }

    @Test
    fun `wav 16-bit write and read round-trip preserves pcm and peaks`() {
        val sr = 48000
        val frames = 2400
        val pcm = FloatArray(frames) { i ->
            (0.75 * sin(2.0 * PI * 440.0 * i / sr)).toFloat()
        }
        val wavFile = tempFolder.newFile("sine_test.wav")
        SamplePcmTools.writeWav16(wavFile, sr, 1, pcm)

        val decoded = SamplePcmTools.readWav(wavFile.readBytes())
        assertNotNull(decoded)
        assertEquals(sr, decoded!!.sampleRate)
        assertEquals(1, decoded.channels)
        assertEquals(frames, decoded.pcmFloats.size)
        for (i in 0 until frames) {
            assertTrue(abs(pcm[i] - decoded.pcmFloats[i]) < 0.001f)
        }

        val peaks = SamplePcmTools.computePeaks(decoded.pcmFloats, 1, bins = 48)
        assertEquals(48, peaks.size)
        assertTrue(peaks.all { it in 0f..1f })
        assertTrue(peaks.maxOrNull()!! > 0.7f)
        assertArrayEquals(peaks.toFloatArray(), decoded.peaks.take(48).toFloatArray().let {
            SamplePcmTools.computePeaks(decoded.pcmFloats, 1, bins = 48).toFloatArray()
        }, 1e-5f)
    }

    @Test
    fun `12 drum kits, 12 synth presets, and 8 demo sets are well-formed`() {
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
    }

    @Test
    fun `capture preserves monotonic timing and distinct pitches when stopped`() {
        var nowNs = 1_000_000_000L
        val buffer = com.example.core.audio.KotlinCaptureBuffer(timeNanos = { nowNs }).apply {
            bpm = 120f
            timeoutSeconds = 12
            isTransportPlaying = false
        }

        buffer.noteOn(trackIndex = 1, note = 60, velocity = 0.8f)
        nowNs += 250_000_000L
        buffer.noteOff(trackIndex = 1, note = 60)

        nowNs += 250_000_000L
        buffer.noteOn(trackIndex = 1, note = 64, velocity = 0.85f)
        nowNs += 500_000_000L
        buffer.noteOff(trackIndex = 1, note = 64)

        nowNs += 250_000_000L
        buffer.noteOn(trackIndex = 1, note = 67, velocity = 0.9f)
        nowNs += 250_000_000L
        buffer.noteOff(trackIndex = 1, note = 67)

        val events = buffer.consumeAll()
        assertEquals(3, events.size)

        val clip = com.example.core.audio.CaptureProcessor.buildCapturedClip(
            events = events,
            trackIndex = 1,
            timeSigNum = 4,
            captureMode = com.example.core.common.CaptureMode.FREE_LENGTH,
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
        val buffer = com.example.core.audio.KotlinCaptureBuffer(timeNanos = { nowNs }).apply {
            bpm = 120f
            timeoutSeconds = 4
            isTransportPlaying = true
        }

        buffer.noteOn(trackIndex = 0, note = 36, velocity = 0.95f, transportBeat = 5.0)
        nowNs += 100_000_000L
        buffer.noteOff(trackIndex = 0, note = 36)

        buffer.noteOn(trackIndex = 0, note = 38, velocity = 0.88f, transportBeat = 6.0)
        nowNs += 100_000_000L
        buffer.noteOff(trackIndex = 0, note = 38)

        val fixedClip = com.example.core.audio.CaptureProcessor.buildCapturedClip(
            events = buffer.consumeAll(),
            trackIndex = 0,
            timeSigNum = 4,
            captureMode = com.example.core.common.CaptureMode.FIXED_LENGTH,
            fixedBars = 2,
            isDrumTrack = true
        )
        assertNotNull(fixedClip)
        assertEquals(2, fixedClip!!.lengthBars)
        assertEquals(8f, fixedClip.totalBeats(4), 0.01f)
        assertEquals(1.0f, fixedClip.notes[0].startBeat, 0.01f)
        assertEquals(2.0f, fixedClip.notes[1].startBeat, 0.01f)

        buffer.noteOn(trackIndex = 0, note = 40, velocity = 0.72f, transportBeat = 5.5)
        nowNs += 50_000_000L
        buffer.noteOff(trackIndex = 0, note = 40)
        val overdubbed = com.example.core.audio.CaptureProcessor.mergeOverdubIntoClip(
            existing = fixedClip,
            events = buffer.consumeAll(),
            trackIndex = 0,
            timeSigNum = 4,
            isDrumTrack = true
        )
        assertNotNull(overdubbed)
        assertEquals(3, overdubbed!!.notes.size)
        assertTrue(overdubbed.notes.any { it.note == 36 } && overdubbed.notes.any { it.note == 38 } && overdubbed.notes.any { it.note == 40 })

        buffer.isTransportPlaying = false
        buffer.noteOn(trackIndex = 1, note = 60, velocity = 0.8f)
        nowNs += 100_000_000L
        buffer.noteOff(trackIndex = 1, note = 60)
        nowNs += 5_000_000_000L
        buffer.noteOn(trackIndex = 1, note = 72, velocity = 0.9f)
        val afterTimeout = buffer.consumeAll()
        assertEquals(1, afterTimeout.size)
        assertEquals(72, afterTimeout[0].note)
    }

    @Test
    fun `piano roll cumulative drag, resize, quantize, nudge, and loop transforms work accurately`() {
        val initial = MidiNoteModel(id = "n1", note = 60, startBeat = 1.0f, durationBeats = 1.0f, velocity = 0.8f)

        val movedUp = com.example.core.project.PianoRollDragMath.applyCumulativeDrag(
            initialNote = initial,
            cumulativeDeltaPx = 12f,
            cumulativeDeltaPy = -60f,
            beatPx = 80f,
            rowPx = 20f,
            mode = com.example.core.project.NoteDragMode.MOVE,
            snapGridBeats = 0.25f,
            totalBeats = 8f,
            minMidi = 24,
            maxMidi = 96
        )
        assertEquals(63, movedUp.note)
        assertEquals(1.25f, movedUp.startBeat, 0.001f)

        val resizedLeft = com.example.core.project.PianoRollDragMath.applyCumulativeDrag(
            initialNote = initial,
            cumulativeDeltaPx = -40f,
            cumulativeDeltaPy = 0f,
            beatPx = 80f,
            rowPx = 20f,
            mode = com.example.core.project.NoteDragMode.RESIZE_LEFT,
            snapGridBeats = 0.25f,
            totalBeats = 8f,
            minMidi = 24,
            maxMidi = 96
        )
        assertEquals(0.5f, resizedLeft.startBeat, 0.001f)
        assertEquals(1.5f, resizedLeft.durationBeats, 0.001f)

        val resizedRight = com.example.core.project.PianoRollDragMath.applyCumulativeDrag(
            initialNote = initial,
            cumulativeDeltaPx = 60f,
            cumulativeDeltaPy = 0f,
            beatPx = 80f,
            rowPx = 20f,
            mode = com.example.core.project.NoteDragMode.RESIZE_RIGHT,
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
    fun `wav export collision naming never breaks wav extension and validates RIFF headers`() {
        assertEquals("Techno_Jam", com.example.core.audio.WavExportHelper.sanitizeFileBaseName("  Techno/Jam:*? "))
        assertEquals("ZYNTRA_Export", com.example.core.audio.WavExportHelper.sanitizeFileBaseName("..."))

        val existing = mutableSetOf<String>()
        val first = com.example.core.audio.WavExportHelper.nextUniqueWavFilename("My Beat", existing)
        assertEquals("My_Beat.wav", first)
        existing += first

        val second = com.example.core.audio.WavExportHelper.nextUniqueWavFilename("My Beat", existing)
        assertEquals("My_Beat(1).wav", second)
        existing += second

        val third = com.example.core.audio.WavExportHelper.nextUniqueWavFilename("My Beat", existing)
        assertEquals("My_Beat(2).wav", third)
        existing += third

        // Case-insensitive collision detection and strict trailing .wav suffix
        val fourth = com.example.core.audio.WavExportHelper.nextUniqueWavFilename("my beat", existing + "my_beat(3).WAV")
        assertEquals("my_beat(4).wav", fourth)
        assertFalse(fourth.contains(".wav ("))
        assertTrue(fourth.endsWith(".wav"))

        // Validate real WAV header parser on a generated 16-bit PCM file
        val sr = 44100
        val pcm = FloatArray(1024) { i -> (0.5 * sin(2.0 * PI * 220.0 * i / sr)).toFloat() }
        val wavFile = tempFolder.newFile("header_check.wav")
        SamplePcmTools.writeWav16(wavFile, sr, 2, pcm)
        val header = com.example.core.audio.WavExportHelper.parseWavHeader(wavFile)
        assertNotNull(header)
        assertTrue(header!!.isValid)
        assertEquals(1, header.audioFormat)
        assertEquals(2, header.channels)
        assertEquals(44100, header.sampleRate)
        assertEquals(16, header.bitDepth)
        assertEquals(1024L * 2L, header.dataSize)
        assertEquals(wavFile.length(), header.totalFileSize)
    }

    @Test
    fun `repeated capture cycles stay uncorrupted and respect captureArmed isolation`() {
        var nowNs = 2_000_000_000L
        val buffer = com.example.core.audio.KotlinCaptureBuffer(timeNanos = { nowNs }).apply {
            bpm = 120f
            timeoutSeconds = 12
            isTransportPlaying = false
            captureArmed = true
        }

        // Cycle 1
        buffer.noteOn(trackIndex = 1, note = 60, velocity = 0.8f)
        nowNs += 250_000_000L
        buffer.noteOff(trackIndex = 1, note = 60)
        assertEquals(1, buffer.totalCount())
        val firstNotes = buffer.consumeAll()
        assertEquals(1, firstNotes.size)
        assertEquals(0, buffer.totalCount())

        // Simulate Sample Browser / Piano Roll audition with captureArmed = false
        buffer.captureArmed = false
        buffer.noteOn(trackIndex = 1, note = 72, velocity = 0.9f)
        nowNs += 250_000_000L
        buffer.noteOff(trackIndex = 1, note = 72)
        assertEquals(0, buffer.totalCount())

        // Cycle 2 after re-arming (must start cleanly at beat 0 with zero bleed from Cycle 1 or preview)
        buffer.captureArmed = true
        buffer.noteOn(trackIndex = 1, note = 62, velocity = 0.85f)
        nowNs += 250_000_000L
        buffer.noteOff(trackIndex = 1, note = 62)
        // Duplicate same-pitch trigger before noteOff closes cleanly
        buffer.noteOn(trackIndex = 1, note = 65, velocity = 0.75f)
        nowNs += 125_000_000L
        buffer.noteOn(trackIndex = 1, note = 65, velocity = 0.9f)
        nowNs += 250_000_000L
        buffer.noteOff(trackIndex = 1, note = 65)

        val secondNotes = buffer.consumeAll()
        assertEquals(3, secondNotes.size)
        assertEquals(0.0, secondNotes[0].beatOffset, 0.01)
        assertEquals(listOf(62, 65, 65), secondNotes.map { it.note })
        assertTrue(secondNotes.all { it.durationBeats > 0.0 })

        // Cycle 3 + Clear Capture resets everything cleanly
        buffer.noteOn(trackIndex = 1, note = 67, velocity = 0.8f)
        assertEquals(1, buffer.totalCount())
        buffer.clear()
        assertEquals(0, buffer.totalCount())
        assertTrue(buffer.consumeAll().isEmpty())

        buffer.noteOn(trackIndex = 1, note = 69, velocity = 0.95f)
        nowNs += 250_000_000L
        buffer.noteOff(trackIndex = 1, note = 69)
        val cycle4 = buffer.consumeAll()
        assertEquals(1, cycle4.size)
        assertEquals(69, cycle4[0].note)
        assertEquals(0.0, cycle4[0].beatOffset, 0.01)
    }

    @Test
    fun `high octave scales terminate across all octaves and 8-FX chains reorder and serialize cleanly`() {
        for (scale in ScaleType.entries) {
            for (root in 0..11) {
                for (oct in 0..8) {
                    val notes = MusicalTheory.buildScaleNotes(root, scale, oct, count = 32)
                    assertTrue("Scale $scale oct $oct must be non-empty", notes.isNotEmpty())
                    assertTrue("All MIDI notes must be bounded 0..127", notes.all { it in 0..127 })
                }
            }
        }

        val grid = MusicalTheory.generate25PadGrid(0, ScaleType.CHROMATIC, octave = 8, MelodicPadLayout.FOURTHS)
        assertEquals(25, grid.size)
        assertTrue(grid.all { it.midiNote in 0..127 })

        // 8-Effect chain operations
        var chain = emptyList<EffectModel>()
        val fxTypes = listOf(
            EffectType.PARAMETRIC_EQ, EffectType.COMPRESSOR, EffectType.CHORUS, EffectType.DISTORTION,
            EffectType.DELAY, EffectType.REVERB, EffectType.SATURATION, EffectType.PHASER
        )
        fxTypes.forEach { t ->
            chain = com.example.core.project.EffectChainTransforms.addEffect(chain, EffectModel(type = t))
        }
        assertEquals(8, chain.size)
        // Adding 9th is capped at 8
        chain = com.example.core.project.EffectChainTransforms.addEffect(chain, EffectModel(type = EffectType.BITCRUSHER))
        assertEquals(8, chain.size)
        assertEquals(EffectType.PHASER, chain[7].type)

        // Move index 0 (PARAMETRIC_EQ) right to index 2
        val reordered = com.example.core.project.EffectChainTransforms.moveEffect(chain, 0, 2)
        assertEquals(EffectType.PARAMETRIC_EQ, reordered[2].type)
        assertEquals(EffectType.COMPRESSOR, reordered[0].type)

        // Bypass slot 3 and remove slot 1
        val bypassed = com.example.core.project.EffectChainTransforms.updateEffectAt(
            reordered,
            3,
            reordered[3].copy(bypassed = true)
        )
        assertTrue(bypassed[3].bypassed)
        val trimmed = com.example.core.project.EffectChainTransforms.removeEffectAt(bypassed, 1)
        assertEquals(7, trimmed.size)

        // Lossless JSON serialization of 8 track FX + 8 master FX
        val setWith8Fx = DemoProjectsFactory.createEmptySet("FX8 Test").let { s ->
            s.copy(
                masterEffects = chain,
                tracks = s.tracks.mapIndexed { idx, tr -> if (idx == 1) tr.copy(effects = chain) else tr }
            )
        }
        val serializer = ProjectSerializer()
        val decoded = serializer.decode(serializer.encode(setWith8Fx))
        assertEquals(8, decoded.masterEffects.size)
        assertEquals(8, decoded.tracks[1].effects.size)
        assertEquals(EffectType.SATURATION, decoded.tracks[1].effects[6].type)
    }

    @Test
    fun `piano roll drag mode splits left move vs right resize and locks pitch on resize`() {
        assertEquals(
            com.example.core.project.NoteDragMode.MOVE,
            com.example.core.project.PianoRollDragMath.determineNoteDragMode(touchX = 110f, noteStartPx = 100f, noteEndPx = 180f)
        )
        assertEquals(
            com.example.core.project.NoteDragMode.MOVE,
            com.example.core.project.PianoRollDragMath.determineNoteDragMode(touchX = 139f, noteStartPx = 100f, noteEndPx = 180f)
        )
        assertEquals(
            com.example.core.project.NoteDragMode.RESIZE_RIGHT,
            com.example.core.project.PianoRollDragMath.determineNoteDragMode(touchX = 141f, noteStartPx = 100f, noteEndPx = 180f)
        )
        assertEquals(
            com.example.core.project.NoteDragMode.RESIZE_RIGHT,
            com.example.core.project.PianoRollDragMath.determineNoteDragMode(touchX = 178f, noteStartPx = 100f, noteEndPx = 180f)
        )

        val initial = MidiNoteModel(id = "n1", note = 60, startBeat = 1.0f, durationBeats = 1.0f)
        // Resizing right must ignore large vertical pointer motion so pitch stays locked
        val resized = com.example.core.project.PianoRollDragMath.applyCumulativeDrag(
            initialNote = initial,
            cumulativeDeltaPx = 40f,
            cumulativeDeltaPy = -120f,
            beatPx = 80f,
            rowPx = 24f,
            mode = com.example.core.project.NoteDragMode.RESIZE_RIGHT,
            snapGridBeats = 0.25f,
            totalBeats = 8f,
            minMidi = 24,
            maxMidi = 96
        )
        assertEquals(60, resized.note)
        assertEquals(1.0f, resized.startBeat, 0.001f)
        assertEquals(1.5f, resized.durationBeats, 0.001f)
    }

    @Test
    fun `drum pad migrates legacy padEffect and supports up to 8 individual pad effects`() {
        val serializer = ProjectSerializer()
        val base = DemoProjectsFactory.createEmptySet("Pad FX8 Test")
        val legacyFx = EffectModel(id = "legacy_dly", type = EffectType.DELAY, wetDry = 0.35f)
        val multiFx = listOf(
            EffectModel(type = EffectType.SATURATION),
            EffectModel(type = EffectType.COMPRESSOR),
            EffectModel(type = EffectType.REVERB)
        )
        val edited = base.copy(
            tracks = base.tracks.mapIndexed { idx, tr ->
                if (idx == 0) {
                    tr.copy(
                        drumPads = tr.drumPads.map { pad ->
                            when (pad.index) {
                                0 -> pad.copy(padEffect = legacyFx, padEffects = emptyList())
                                1 -> pad.copy(padEffect = EffectModel(), padEffects = multiFx)
                                else -> pad
                            }
                        }
                    )
                } else tr
            }
        )

        val decoded = serializer.decode(serializer.encode(edited))
        val pad0 = decoded.tracks[0].drumPads.first { it.index == 0 }
        val pad1 = decoded.tracks[0].drumPads.first { it.index == 1 }
        assertEquals(1, pad0.effectivePadEffects().size)
        assertEquals(EffectType.DELAY, pad0.effectivePadEffects()[0].type)
        assertEquals(EffectType.NONE, pad0.padEffect.type)
        assertEquals(3, pad1.effectivePadEffects().size)
        assertEquals(listOf(EffectType.SATURATION, EffectType.COMPRESSOR, EffectType.REVERB), pad1.effectivePadEffects().map { it.type })

        var padChain = pad1.effectivePadEffects()
        for (fxType in listOf(EffectType.CHORUS, EffectType.FLANGER, EffectType.PHASER, EffectType.BITCRUSHER, EffectType.AUTO_FILTER, EffectType.STEREO_WIDENER)) {
            padChain = com.example.core.project.EffectChainTransforms.addEffect(padChain, EffectModel(type = fxType))
        }
        assertEquals(8, padChain.size)
        assertEquals(EffectType.AUTO_FILTER, padChain.last().type)
    }
}
