package com.example.core.project

import android.content.Context
import android.net.Uri
import com.example.core.common.MusicalTheory
import com.example.core.database.NoteCraftDatabase
import com.example.core.database.ProjectEntity
import com.example.core.database.SampleEntity
import com.example.core.samples.AudioDecoder
import com.example.core.samples.BuiltInSampleGenerator
import com.example.core.samples.DecodedSample
import com.example.core.samples.SamplePcmTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectRepository(
    private val context: Context,
    private val db: NoteCraftDatabase,
    private val serializer: ProjectSerializer = ProjectSerializer()
) {
    private val setsDir = File(context.filesDir, "sets").apply { mkdirs() }
    private val autosaveFile = File(context.filesDir, "autosave.notecraft.json")
    private val samplesDir = File(context.filesDir, "samples/user").apply { mkdirs() }

    val allProjects: Flow<List<ProjectEntity>> = db.projectDao().observeAllProjects()
    val allSamples: Flow<List<SampleEntity>> = db.sampleDao().observeAllSamples()

    suspend fun initializeIfNeeded(): List<Pair<SampleEntity, FloatArray>> = withContext(Dispatchers.IO) {
        val loadedSamples = BuiltInSampleGenerator.ensureBuiltInSamples(context)
        val existingSamples = db.sampleDao().getAllSamplesOnce()
        val existingIds = existingSamples.map { it.id }.toSet()
        for ((entity, _) in loadedSamples) {
            if (entity.id !in existingIds) {
                db.sampleDao().upsertSample(entity)
            }
        }

        // Load user samples from disk
        val allDecoded = loadedSamples.toMutableList()
        for (s in existingSamples) {
            if (!s.id.startsWith("builtin_")) {
                val f = File(s.filePath)
                if (f.exists()) {
                    SamplePcmTools.readWav(f.readBytes())?.let { dec ->
                        allDecoded.add(s to dec.pcmFloats)
                    }
                }
            }
        }

        // Seed demo projects on first launch or add new factory demos when only legacy demos exist
        val existingProjects = db.projectDao().getAllProjectsOnce()
        val existingProjIds = existingProjects.map { it.id }.toSet()
        if (existingProjects.isEmpty()) {
            for (demo in DemoProjectsFactory.buildDemoSets()) {
                saveSet(demo)
            }
        } else if (existingProjects.all { it.isDemo } && "demo_deep_motion" !in existingProjIds) {
            for (demo in DemoProjectsFactory.buildDemoSets()) {
                if (demo.id !in existingProjIds) {
                    saveSet(demo)
                }
            }
        }
        allDecoded
    }

    suspend fun saveSet(set: NoteSet): NoteSet = withContext(Dispatchers.IO) {
        val updated = set.copy(modifiedAt = System.currentTimeMillis())
        val file = File(setsDir, "${updated.id}.notecraft.json")
        serializer.saveToFile(updated, file)
        val keyLabel = "${MusicalTheory.NOTE_NAMES[updated.rootNote % 12]} ${updated.scaleType.displayName}"
        db.projectDao().upsertProject(
            ProjectEntity(
                id = updated.id,
                name = updated.name,
                bpm = updated.bpm,
                keyLabel = keyLabel,
                trackCount = updated.tracks.size,
                artworkSeed = updated.artworkSeed,
                isDemo = updated.isDemo,
                filePath = file.absolutePath,
                createdAt = updated.createdAt,
                modifiedAt = updated.modifiedAt
            )
        )
        clearAutosave()
        updated
    }

    suspend fun loadSet(id: String): NoteSet? = withContext(Dispatchers.IO) {
        val file = File(setsDir, "$id.notecraft.json")
        serializer.loadFromFile(file)
    }

    suspend fun createNewSet(name: String, bpm: Float): NoteSet {
        val set = DemoProjectsFactory.createEmptySet(name.ifBlank { "Untitled Set" }, bpm)
        return saveSet(set)
    }

    suspend fun duplicateSet(id: String): NoteSet? = withContext(Dispatchers.IO) {
        val orig = loadSet(id) ?: return@withContext null
        val copy = orig.copy(
            id = UUID.randomUUID().toString(),
            name = "${orig.name} Copy",
            isDemo = false,
            artworkSeed = (1000..9999).random(),
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
        saveSet(copy)
    }

    suspend fun renameSet(id: String, newName: String): NoteSet? {
        val orig = loadSet(id) ?: return null
        return saveSet(orig.copy(name = newName.trim().ifEmpty { orig.name }))
    }

    suspend fun deleteSet(id: String) = withContext(Dispatchers.IO) {
        File(setsDir, "$id.notecraft.json").delete()
        db.projectDao().deleteProjectById(id)
    }

    suspend fun writeAutosave(set: NoteSet) = withContext(Dispatchers.IO) {
        serializer.saveToFile(set, autosaveFile)
    }

    suspend fun checkAutosaveRecovery(): NoteSet? = withContext(Dispatchers.IO) {
        if (autosaveFile.exists()) serializer.loadFromFile(autosaveFile) else null
    }

    suspend fun clearAutosave() = withContext(Dispatchers.IO) {
        if (autosaveFile.exists()) autosaveFile.delete()
    }

    suspend fun exportProjectToUri(set: NoteSet, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = serializer.encode(set)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(encoded.toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
    }

    suspend fun importProjectFromUri(uri: Uri): NoteSet? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext null
            val decoded = serializer.decode(raw).copy(
                id = UUID.randomUUID().toString(),
                isDemo = false,
                modifiedAt = System.currentTimeMillis()
            )
            saveSet(decoded)
        }.getOrNull()
    }

    suspend fun importAudioSample(uri: Uri, category: String = "Imported", folder: String = "User Imports"): Pair<SampleEntity, FloatArray>? =
        withContext(Dispatchers.IO) {
            val (rawName, decoded) = AudioDecoder.decodeFromUri(context, uri) ?: return@withContext null
            saveDecodedSample(rawName.substringBeforeLast('.'), decoded, category, folder)
        }

    suspend fun saveDecodedSample(
        name: String,
        decoded: DecodedSample,
        category: String,
        folder: String
    ): Pair<SampleEntity, FloatArray> = withContext(Dispatchers.IO) {
        val existing = db.sampleDao().getAllSamplesOnce()
        val usedSlots = existing.map { it.slotIndex }.toSet()
        // Built-in samples occupy slots 0..63; reserve 80..254 for user recordings/imports
        val nextSlot = (80 until 255).firstOrNull { it !in usedSlots } ?: 80

        val id = "user_${UUID.randomUUID().toString().take(8)}"
        val wavFile = File(samplesDir, "$id.wav")
        SamplePcmTools.writeWav16(wavFile, decoded.sampleRate, decoded.channels, decoded.pcmFloats)

        val entity = SampleEntity(
            id = id,
            name = name.ifBlank { "Sample $nextSlot" },
            category = category,
            folder = folder,
            filePath = wavFile.absolutePath,
            durationMs = decoded.durationMs,
            sampleRate = decoded.sampleRate,
            channels = decoded.channels,
            slotIndex = nextSlot,
            isFavorite = false,
            lastUsedAt = System.currentTimeMillis(),
            peaksCsv = decoded.peaks.joinToString(",")
        )
        db.sampleDao().upsertSample(entity)
        entity to decoded.pcmFloats
    }

    suspend fun toggleSampleFavorite(id: String, fav: Boolean) {
        db.sampleDao().setFavorite(id, fav)
    }

    suspend fun touchSample(id: String) {
        db.sampleDao().touchSample(id, System.currentTimeMillis())
    }

    suspend fun getMissingSamples(set: NoteSet): List<String> {
        val ids = db.sampleDao().getAllSamplesOnce().map { it.id }.toSet()
        return serializer.detectMissingSamples(set, ids)
    }
}
