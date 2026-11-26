package com.example.core.project

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ProjectSerializer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(set: NoteSet): String = json.encodeToString(set)

    fun decode(rawJson: String): NoteSet {
        val parsed = json.decodeFromString<NoteSet>(rawJson)
        return migrateIfNeeded(parsed)
    }

    fun migrateIfNeeded(set: NoteSet): NoteSet {
        var out = set
        if (out.tracks.isEmpty()) {
            out = out.copy(tracks = DemoProjectsFactory.createEmptySet(out.name.ifBlank { "Untitled Set" }).tracks)
        }
        if (out.scenes.size < 8) {
            val padded = out.scenes.toMutableList()
            while (padded.size < 8) padded.add(SceneModel(name = "Scene ${padded.size + 1}"))
            out = out.copy(scenes = padded)
        }
        val tsNum = out.timeSigNum.coerceIn(1, 12)
        val sanitizedTracks = out.tracks.map { tr ->
            val migratedPads = tr.drumPads.map { pad ->
                val padChain = pad.effectivePadEffects()
                pad.copy(
                    padEffect = EffectModel(type = EffectType.NONE),
                    padEffects = padChain
                )
            }
            tr.copy(
                drumPads = migratedPads,
                clips = tr.clips.mapValues { (_, clip) ->
                    val bars = clip.lengthBars.coerceIn(1, 16)
                    val totalB = (bars * tsNum).toFloat()
                    val ls = clip.loopStartBeats.coerceIn(0f, (totalB - 0.25f).coerceAtLeast(0f))
                    val le = clip.loopEndBeats?.coerceIn((ls + 0.25f).coerceAtMost(totalB), totalB)
                    clip.copy(
                        lengthBars = bars,
                        loopStartBeats = ls,
                        loopEndBeats = le,
                        notes = clip.notes.sortedWith(compareBy({ it.startBeat }, { it.note }))
                    )
                }
            )
        }
        return out.copy(
            schemaVersion = 1,
            timeSigNum = tsNum,
            tracks = sanitizedTracks
        )
    }

    fun saveToFile(set: NoteSet, file: File) {
        file.parentFile?.mkdirs()
        file.writeText(encode(set))
    }

    fun loadFromFile(file: File): NoteSet? {
        if (!file.exists()) return null
        return runCatching { decode(file.readText()) }.getOrNull()
    }

    fun detectMissingSamples(set: NoteSet, knownSampleIds: Set<String>): List<String> {
        val missing = mutableListOf<String>()
        for (track in set.tracks) {
            if (track.type == TrackType.MELODIC_SAMPLER) {
                val sid = track.samplerParams.sampleId
                if (sid.isNotBlank() && !sid.startsWith("builtin_") && sid !in knownSampleIds) {
                    missing.add("${track.name}: ${track.samplerParams.sampleName}")
                }
            }
            for (pad in track.drumPads) {
                if (pad.sampleId.isNotBlank() && !pad.sampleId.startsWith("builtin_") && pad.sampleId !in knownSampleIds) {
                    missing.add("${track.name} / ${pad.name}: ${pad.sampleId}")
                }
            }
        }
        return missing
    }
}
