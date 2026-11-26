package com.example.core.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WavHeaderInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
    val audioFormat: Int, // 1=PCM, 3=IEEE float
    val dataSize: Long,
    val totalFileSize: Long
) {
    val isValid: Boolean
        get() = sampleRate in 8000..192000 &&
            channels in 1..8 &&
            (bitDepth == 16 || bitDepth == 24 || bitDepth == 32) &&
            (audioFormat == 1 || audioFormat == 3) &&
            dataSize > 0L &&
            totalFileSize >= 44L + dataSize
}

data class ExportedWavResult(
    val uri: Uri,
    val displayName: String,
    val folderLabel: String
)

object WavExportHelper {

    fun sanitizeFileBaseName(projectName: String): String {
        val cleaned = projectName
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_', '.')
        val withoutWav = if (cleaned.endsWith(".wav", ignoreCase = true)) {
            cleaned.dropLast(4).trim('_', '.')
        } else {
            cleaned
        }
        return withoutWav.ifBlank { "ZYNTRA_Export" }.take(64)
    }

    /**
     * Resolves duplicate export names strictly keeping .wav as the extension:
     * First export: ProjectName.wav
     * Second export: ProjectName(1).wav
     * Third export: ProjectName(2).wav
     */
    fun nextUniqueWavFilename(projectName: String, existingFilenames: Set<String>): String {
        val base = sanitizeFileBaseName(projectName)
        val existingLower = existingFilenames.map { it.lowercase() }.toHashSet()
        val firstCandidate = "$base.wav"
        if (firstCandidate.lowercase() !in existingLower) {
            return firstCandidate
        }
        var index = 1
        while (true) {
            val candidate = "$base($index).wav"
            if (candidate.lowercase() !in existingLower) {
                return candidate
            }
            index++
        }
    }

    fun parseWavHeader(file: File): WavHeaderInfo? {
        if (!file.exists() || file.length() < 44L) return null
        val bytes = file.inputStream().use { input ->
            val header = ByteArray(128.coerceAtMost(file.length().toInt()))
            val read = input.read(header)
            if (read < 44) return null
            header.copyOf(read)
        }
        return parseWavHeader(bytes, totalActualFileSize = file.length())
    }

    fun parseWavHeader(bytes: ByteArray, totalActualFileSize: Long = bytes.size.toLong()): WavHeaderInfo? {
        if (bytes.size < 44) return null
        if (bytes.copyOfRange(0, 4).decodeToString() != "RIFF") return null
        if (bytes.copyOfRange(8, 12).decodeToString() != "WAVE") return null

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riffChunkSize = bb.getInt(4).toLong() and 0xFFFFFFFFL
        if (riffChunkSize < 36L) return null

        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bitDepth = 0
        var audioFormat = 0
        var dataSize = -1L

        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.copyOfRange(offset, offset + 4).decodeToString()
            val chunkSize = bb.getInt(offset + 4).toLong() and 0xFFFFFFFFL
            val bodyOffset = offset + 8
            when (chunkId) {
                "fmt " -> {
                    if (bodyOffset + 16 > bytes.size) return null
                    audioFormat = bb.getShort(bodyOffset).toInt() and 0xFFFF
                    channels = bb.getShort(bodyOffset + 2).toInt() and 0xFFFF
                    sampleRate = bb.getInt(bodyOffset + 4)
                    bitDepth = bb.getShort(bodyOffset + 14).toInt() and 0xFFFF
                }
                "data" -> {
                    dataSize = chunkSize
                    break
                }
            }
            val next = bodyOffset + chunkSize + (chunkSize and 1L)
            if (next <= offset || next > Int.MAX_VALUE) break
            offset = next.toInt()
        }

        if (dataSize <= 0L || sampleRate <= 0 || channels <= 0 || bitDepth <= 0) return null
        val info = WavHeaderInfo(
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
            audioFormat = audioFormat,
            dataSize = dataSize,
            totalFileSize = totalActualFileSize
        )
        return if (info.isValid) info else null
    }

    fun queryDownloadsNoteCraftNames(context: Context): Set<String> {
        val names = mutableSetOf<String>()
        val relPath = "${Environment.DIRECTORY_DOWNLOADS}/ZYNTRA/"
        val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("$relPath%")
        runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val col = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                while (col >= 0 && cursor.moveToNext()) {
                    cursor.getString(col)?.let { names.add(it) }
                }
            }
        }
        return names
    }

    fun saveWavFileToDownloads(
        context: Context,
        sourceWav: File,
        projectName: String
    ): ExportedWavResult? {
        if (parseWavHeader(sourceWav) == null) return null
        val existing = queryDownloadsNoteCraftNames(context)
        val filename = nextUniqueWavFilename(projectName, existing)
        val resolver = context.contentResolver
        val relPath = "${Environment.DIRECTORY_DOWNLOADS}/ZYNTRA"

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
            put(MediaStore.Downloads.RELATIVE_PATH, relPath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openFileDescriptor(itemUri, "w")?.use { pfd ->
                java.io.FileOutputStream(pfd.fileDescriptor).use { out ->
                    FileInputStream(sourceWav).use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                pfd.fileDescriptor.sync()
            } ?: return null

            val publish = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(itemUri, publish, null, null)
            ExportedWavResult(
                uri = itemUri,
                displayName = filename,
                folderLabel = "Downloads/ZYNTRA"
            )
        } catch (_: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            null
        }
    }

    fun saveWavFileToTreeUri(
        context: Context,
        sourceWav: File,
        treeUri: Uri,
        projectName: String
    ): ExportedWavResult? {
        if (parseWavHeader(sourceWav) == null) return null
        val resolver = context.contentResolver
        val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

        val existingNames = mutableSetOf<String>()
        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (idx >= 0 && cursor.moveToNext()) {
                    cursor.getString(idx)?.let { existingNames.add(it) }
                }
            }
        }

        val uniqueName = nextUniqueWavFilename(projectName, existingNames)
        val docUri = DocumentsContract.createDocument(resolver, parentDocUri, "audio/wav", uniqueName)
            ?: return null

        return try {
            resolver.openFileDescriptor(docUri, "w")?.use { pfd ->
                java.io.FileOutputStream(pfd.fileDescriptor).use { out ->
                    FileInputStream(sourceWav).use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                pfd.fileDescriptor.sync()
            } ?: return null
            ExportedWavResult(
                uri = docUri,
                displayName = queryDisplayName(context, docUri) ?: uniqueName,
                folderLabel = "Selected Folder"
            )
        } catch (_: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, docUri) }
            null
        }
    }

    fun copyAndFlushWavToUri(context: Context, sourceWav: File, destinationUri: Uri): Boolean {
        if (parseWavHeader(sourceWav) == null) return false
        val resolver = context.contentResolver
        return try {
            val pfd = resolver.openFileDescriptor(destinationUri, "wt")
                ?: resolver.openFileDescriptor(destinationUri, "w")
            if (pfd != null) {
                pfd.use {
                    java.io.FileOutputStream(it.fileDescriptor).use { out ->
                        FileInputStream(sourceWav).use { input -> input.copyTo(out) }
                        out.flush()
                    }
                    it.fileDescriptor.sync()
                }
                true
            } else {
                resolver.openOutputStream(destinationUri)?.use { out ->
                    FileInputStream(sourceWav).use { input -> input.copyTo(out) }
                    out.flush()
                } ?: return false
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()
    }
}
