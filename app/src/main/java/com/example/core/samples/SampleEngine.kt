package com.example.core.samples

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.database.SampleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

data class DecodedSample(
    val sampleRate: Int,
    val channels: Int,
    val pcmFloats: FloatArray,
    val peaks: List<Float>,
    val durationMs: Long
)

object SamplePcmTools {

    fun computePeaks(pcm: FloatArray, channels: Int, bins: Int = 96): List<Float> {
        if (pcm.isEmpty()) return List(bins) { 0f }
        val frames = pcm.size / channels.coerceAtLeast(1)
        val step = (frames / bins).coerceAtLeast(1)
        val out = ArrayList<Float>(bins)
        for (b in 0 until bins) {
            val start = (b * frames) / bins
            val end = (((b + 1) * frames) / bins).coerceAtMost(frames)
            var pk = 0f
            for (f in start until end) {
                val idx = f * channels
                if (idx < pcm.size) {
                    val v = abs(pcm[idx])
                    if (v > pk) pk = v
                }
            }
            out.add(pk.coerceIn(0f, 1f))
        }
        return out
    }

    fun writeWav16(file: File, sampleRate: Int, channels: Int, pcm: FloatArray) {
        file.parentFile?.mkdirs()
        val dataBytes = pcm.size * 2
        FileOutputStream(file).use { out ->
            val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            hdr.put("RIFF".toByteArray())
            hdr.putInt(36 + dataBytes)
            hdr.put("WAVE".toByteArray())
            hdr.put("fmt ".toByteArray())
            hdr.putInt(16)
            hdr.putShort(1)
            hdr.putShort(channels.toShort())
            hdr.putInt(sampleRate)
            hdr.putInt(sampleRate * channels * 2)
            hdr.putShort((channels * 2).toShort())
            hdr.putShort(16)
            hdr.put("data".toByteArray())
            hdr.putInt(dataBytes)
            out.write(hdr.array())

            val buf = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) {
                val clamped = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                buf.putShort(clamped)
            }
            out.write(buf.array())
        }
    }

    fun readWav(bytes: ByteArray): DecodedSample? {
        if (bytes.size < 44) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4).also { bb.get(it) }.decodeToString()
        if (riff != "RIFF") return null
        bb.int // chunk size
        val wave = ByteArray(4).also { bb.get(it) }.decodeToString()
        if (wave != "WAVE") return null

        var audioFormat = 1
        var channels = 1
        var sampleRate = 48000
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (bb.remaining() >= 8) {
            val id = ByteArray(4).also { bb.get(it) }.decodeToString()
            val size = bb.int
            if (size < 0 || bb.position() + size > bytes.size) break
            when (id) {
                "fmt " -> {
                    val start = bb.position()
                    audioFormat = bb.short.toInt() and 0xFFFF
                    channels = bb.short.toInt() and 0xFFFF
                    sampleRate = bb.int
                    bb.int // byteRate
                    bb.short // blockAlign
                    bitsPerSample = bb.short.toInt() and 0xFFFF
                    bb.position(start + size)
                }
                "data" -> {
                    dataOffset = bb.position()
                    dataSize = size
                    break
                }
                else -> bb.position(bb.position() + size)
            }
        }
        if (dataOffset < 0 || dataSize <= 0) return null

        val dataBuf = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        val floats = when {
            audioFormat == 3 && bitsPerSample == 32 -> {
                val n = dataSize / 4
                FloatArray(n) { dataBuf.float }
            }
            bitsPerSample == 16 -> {
                val n = dataSize / 2
                FloatArray(n) { dataBuf.short / 32768f }
            }
            bitsPerSample == 24 -> {
                val n = dataSize / 3
                FloatArray(n) {
                    val b0 = dataBuf.get().toInt() and 0xFF
                    val b1 = dataBuf.get().toInt() and 0xFF
                    val b2 = dataBuf.get().toInt()
                    val v = (b2 shl 16) or (b1 shl 8) or b0
                    v / 8388608f
                }
            }
            bitsPerSample == 32 -> {
                val n = dataSize / 4
                FloatArray(n) { dataBuf.int / 2147483648f }
            }
            else -> return null
        }
        val frames = floats.size / channels.coerceAtLeast(1)
        val durMs = (frames * 1000L) / sampleRate.coerceAtLeast(1)
        return DecodedSample(sampleRate, channels, floats, computePeaks(floats, channels), durMs)
    }
}

object BuiltInSampleGenerator {
    private const val SR = 48000

    fun ensureBuiltInSamples(context: Context): List<Pair<SampleEntity, FloatArray>> {
        val dir = File(context.filesDir, "samples/builtin")
        dir.mkdirs()
        val rng = Random(4242)
        val results = mutableListOf<Pair<SampleEntity, FloatArray>>()

        val drumSpecs = listOf(
            "Kick" to "Drums",
            "Snare" to "Drums",
            "Clap" to "Drums",
            "Rim" to "Drums",
            "Closed Hat" to "Drums",
            "Open Hat" to "Drums",
            "Pedal Hat" to "Drums",
            "Tamb" to "Drums",
            "Low Tom" to "Drums",
            "Mid Tom" to "Drums",
            "High Tom" to "Drums",
            "Cowbell" to "Drums",
            "Perc 1" to "Drums",
            "Perc 2" to "Drums",
            "Crash" to "Drums",
            "Ride" to "Drums"
        )

        drumSpecs.forEachIndexed { idx, (name, cat) ->
            val file = File(dir, "builtin_drum_$idx.wav")
            val pcm = if (file.exists()) {
                SamplePcmTools.readWav(file.readBytes())?.pcmFloats ?: synthesizeDrum(idx, rng)
            } else {
                synthesizeDrum(idx, rng).also { SamplePcmTools.writeWav16(file, SR, 1, it) }
            }
            val peaks = SamplePcmTools.computePeaks(pcm, 1)
            val entity = SampleEntity(
                id = "builtin_drum_$idx",
                name = name,
                category = cat,
                folder = "909 Studio Kit",
                filePath = file.absolutePath,
                durationMs = (pcm.size * 1000L) / SR,
                sampleRate = SR,
                channels = 1,
                slotIndex = idx,
                isFavorite = idx in listOf(0, 1, 4),
                lastUsedAt = System.currentTimeMillis(),
                peaksCsv = peaks.joinToString(",")
            )
            results.add(entity to pcm)
        }

        val melodicSpecs = listOf(
            Triple("builtin_rhodes", "Electric Keys", 16),
            Triple("builtin_pad", "Warm String Pad", 17),
            Triple("builtin_pluck", "Acoustic Pluck", 18),
            Triple("builtin_vocal", "Formant Choir", 19)
        )
        melodicSpecs.forEachIndexed { i, (id, name, slot) ->
            val file = File(dir, "$id.wav")
            val pcm = if (file.exists()) {
                SamplePcmTools.readWav(file.readBytes())?.pcmFloats ?: synthesizeMelodic(i)
            } else {
                synthesizeMelodic(i).also { SamplePcmTools.writeWav16(file, SR, 1, it) }
            }
            val peaks = SamplePcmTools.computePeaks(pcm, 1)
            val entity = SampleEntity(
                id = id,
                name = name,
                category = "Keys & Synth",
                folder = "Factory Melodic",
                filePath = file.absolutePath,
                durationMs = (pcm.size * 1000L) / SR,
                sampleRate = SR,
                channels = 1,
                slotIndex = slot,
                isFavorite = i == 0,
                lastUsedAt = System.currentTimeMillis(),
                peaksCsv = peaks.joinToString(",")
            )
            results.add(entity to pcm)
        }

        // Additional 12 studio samples (slots 20..31)
        data class ExtraSampleSpec(val id: String, val name: String, val cat: String, val folder: String, val slot: Int)
        val extraSpecs = listOf(
            ExtraSampleSpec("builtin_perc_808_kick", "808 Sub Kick", "Drums", "808 & Studio Perc", 20),
            ExtraSampleSpec("builtin_perc_808_snare", "808 Crisp Snare", "Drums", "808 & Studio Perc", 21),
            ExtraSampleSpec("builtin_perc_shaker", "Shaker 16th", "Drums", "808 & Studio Perc", 22),
            ExtraSampleSpec("builtin_perc_woodblock", "Woodblock", "Drums", "808 & Studio Perc", 23),
            ExtraSampleSpec("builtin_perc_bongo", "Bongo High", "Drums", "808 & Studio Perc", 24),
            ExtraSampleSpec("builtin_perc_zap", "Laser Zap", "Drums", "808 & Studio Perc", 25),
            ExtraSampleSpec("builtin_bass_reese", "FM Reese Bass", "Bass", "Factory Bass", 26),
            ExtraSampleSpec("builtin_bass_acid", "Acid Pluck Bass", "Bass", "Factory Bass", 27),
            ExtraSampleSpec("builtin_kalimba", "Kalimba Mallet", "Keys & Synth", "Factory Melodic", 28),
            ExtraSampleSpec("builtin_bell", "Brass Bell", "Keys & Synth", "Factory Melodic", 29),
            ExtraSampleSpec("builtin_lofi_piano", "Lo-Fi Tape Piano", "Keys & Synth", "Factory Melodic", 30),
            ExtraSampleSpec("builtin_fx_subdrop", "Sub Drop FX", "FX & Textures", "Studio FX", 31)
        )
        extraSpecs.forEachIndexed { idx, spec ->
            val file = File(dir, "${spec.id}.wav")
            val pcm = if (file.exists()) {
                SamplePcmTools.readWav(file.readBytes())?.pcmFloats ?: synthesizeExtra(idx, rng)
            } else {
                synthesizeExtra(idx, rng).also { SamplePcmTools.writeWav16(file, SR, 1, it) }
            }
            val peaks = SamplePcmTools.computePeaks(pcm, 1)
            val entity = SampleEntity(
                id = spec.id,
                name = spec.name,
                category = spec.cat,
                folder = spec.folder,
                filePath = file.absolutePath,
                durationMs = (pcm.size * 1000L) / SR,
                sampleRate = SR,
                channels = 1,
                slotIndex = spec.slot,
                isFavorite = idx in listOf(0, 6, 8, 10),
                lastUsedAt = System.currentTimeMillis(),
                peaksCsv = peaks.joinToString(",")
            )
            results.add(entity to pcm)
        }

        // Expanded Studio Sample Bank (slots 32..63 -> 64 total built-in assets)
        val expandedSpecs = listOf(
            ExtraSampleSpec("builtin_trap_808_boom", "Trap 808 Boom", "Drums", "Trap Kit", 32),
            ExtraSampleSpec("builtin_trap_clap", "Trap Snap Clap", "Drums", "Trap Kit", 33),
            ExtraSampleSpec("builtin_trap_hat", "Trap Crisp Hat", "Drums", "Trap Kit", 34),
            ExtraSampleSpec("builtin_trap_snare", "Trap Tight Snare", "Drums", "Trap Kit", 35),
            ExtraSampleSpec("builtin_hiphop_kick", "Vinyl Punch Kick", "Drums", "Hip-Hop & Lo-Fi", 36),
            ExtraSampleSpec("builtin_hiphop_snare", "Dusty Crunch Snare", "Drums", "Hip-Hop & Lo-Fi", 37),
            ExtraSampleSpec("builtin_lofi_hat", "Tape Closed Hat", "Drums", "Hip-Hop & Lo-Fi", 38),
            ExtraSampleSpec("builtin_lofi_rim", "Lo-Fi Wood Rim", "Drums", "Hip-Hop & Lo-Fi", 39),
            ExtraSampleSpec("builtin_house_kick", "House 4x4 Kick", "Drums", "House & Techno", 40),
            ExtraSampleSpec("builtin_house_clap", "Classic House Clap", "Drums", "House & Techno", 41),
            ExtraSampleSpec("builtin_techno_kick", "Techno Rumble Kick", "Drums", "House & Techno", 42),
            ExtraSampleSpec("builtin_techno_hat", "Metallic 909 Hat", "Drums", "House & Techno", 43),
            ExtraSampleSpec("builtin_deep_chord_stab", "Deep House Stab", "Keys & Synth", "House & Techno", 44),
            ExtraSampleSpec("builtin_minimal_click", "Minimal Micro Click", "Drums", "Minimal Perc", 45),
            ExtraSampleSpec("builtin_minimal_blip", "Minimal FM Blip", "Drums", "Minimal Perc", 46),
            ExtraSampleSpec("builtin_minimal_sub", "Minimal Sine Tom", "Drums", "Minimal Perc", 47),
            ExtraSampleSpec("builtin_organic_conga", "Warm Conga", "Drums", "Organic Perc", 48),
            ExtraSampleSpec("builtin_organic_clave", "Rosewood Clave", "Drums", "Organic Perc", 49),
            ExtraSampleSpec("builtin_acoustic_kick", "Acoustic Kick", "Drums", "Acoustic Kit", 50),
            ExtraSampleSpec("builtin_acoustic_snare", "Acoustic Brush Snare", "Drums", "Acoustic Kit", 51),
            ExtraSampleSpec("builtin_acoustic_hat", "Acoustic Hi-Hat", "Drums", "Acoustic Kit", 52),
            ExtraSampleSpec("builtin_acoustic_ride", "Acoustic Jazz Ride", "Drums", "Acoustic Kit", 53),
            ExtraSampleSpec("builtin_exp_glitch", "Glitch Granule", "Drums", "Experimental", 54),
            ExtraSampleSpec("builtin_exp_fm_hit", "Metallic Ring Hit", "Drums", "Experimental", 55),
            ExtraSampleSpec("builtin_exp_noise_burst", "White Noise Burst", "FX & Textures", "Experimental", 56),
            ExtraSampleSpec("builtin_bass_sub_clean", "Clean Sub C2", "Bass", "Factory Bass", 57),
            ExtraSampleSpec("builtin_bass_ukg", "UKG Organ Bass", "Bass", "Factory Bass", 58),
            ExtraSampleSpec("builtin_lead_saw_pluck", "Trance Saw Pluck", "Keys & Synth", "Factory Melodic", 59),
            ExtraSampleSpec("builtin_keys_wurli", "Warm Wurli Keys", "Keys & Synth", "Factory Melodic", 60),
            ExtraSampleSpec("builtin_keys_marimba", "Wooden Marimba", "Keys & Synth", "Factory Melodic", 61),
            ExtraSampleSpec("builtin_pad_glass", "Shimmer Glass Pad", "Keys & Synth", "Factory Melodic", 62),
            ExtraSampleSpec("builtin_fx_riser", "Noise Sweep Hit", "FX & Textures", "Studio FX", 63)
        )
        expandedSpecs.forEachIndexed { idx, spec ->
            val file = File(dir, "${spec.id}.wav")
            val pcm = if (file.exists()) {
                SamplePcmTools.readWav(file.readBytes())?.pcmFloats ?: synthesizeExpanded(idx, rng)
            } else {
                synthesizeExpanded(idx, rng).also { SamplePcmTools.writeWav16(file, SR, 1, it) }
            }
            val peaks = SamplePcmTools.computePeaks(pcm, 1)
            val entity = SampleEntity(
                id = spec.id,
                name = spec.name,
                category = spec.cat,
                folder = spec.folder,
                filePath = file.absolutePath,
                durationMs = (pcm.size * 1000L) / SR,
                sampleRate = SR,
                channels = 1,
                slotIndex = spec.slot,
                isFavorite = idx in listOf(0, 4, 8, 25, 28),
                lastUsedAt = System.currentTimeMillis(),
                peaksCsv = peaks.joinToString(",")
            )
            results.add(entity to pcm)
        }

        return results
    }

    private fun synthesizeDrum(idx: Int, rng: Random): FloatArray {
        val len = when (idx) {
            0 -> (SR * 0.45).toInt()      // Kick
            5, 14, 15 -> (SR * 0.75).toInt() // Open hat, crash, ride
            else -> (SR * 0.28).toInt()
        }
        val out = FloatArray(len)
        var phase = 0.0
        var phase2 = 0.0
        var hpPrev = 0f
        var hpOut = 0f
        for (i in 0 until len) {
            val t = i.toDouble() / SR
            val s = when (idx) {
                0 -> { // Punchy 909 Kick
                    val f = 48.0 + 135.0 * exp(-t * 36.0)
                    phase += f / SR
                    val body = sin(2.0 * PI * phase) * exp(-t * 7.5)
                    val click = sin(2.0 * PI * 900.0 * t) * exp(-t * 140.0) * 0.45
                    tanh((body + click) * 1.6)
                }
                1 -> { // Snare
                    phase += 185.0 / SR
                    val tone = sin(2.0 * PI * phase) * exp(-t * 22.0) * 0.55
                    val noise = (rng.nextFloat() * 2f - 1f) * exp(-t * 14.0) * 0.65
                    (tone + noise).coerceIn(-1.0, 1.0)
                }
                2 -> { // Clap
                    val burst = if (t < 0.035) abs(sin(t * 240.0)) else 1.0
                    val noise = (rng.nextFloat() * 2f - 1f) * exp(-t * 11.0) * burst * 0.85
                    noise.coerceIn(-1.0, 1.0)
                }
                3 -> { // Rim
                    val f1 = sin(2.0 * PI * 470.0 * t)
                    val f2 = sin(2.0 * PI * 1650.0 * t)
                    (f1 * 0.6 + f2 * 0.5) * exp(-t * 55.0)
                }
                4, 5, 6 -> { // Hats (Closed, Open, Pedal)
                    val decay = if (idx == 5) 5.5 else if (idx == 6) 22.0 else 42.0
                    val raw = (rng.nextFloat() * 2f - 1f)
                    hpOut = 0.82f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    val metallic = sin(2.0 * PI * 5400.0 * t) * 0.25 + sin(2.0 * PI * 8100.0 * t) * 0.2
                    (hpOut * 0.75 + metallic) * exp(-t * decay)
                }
                7 -> { // Tambourine
                    val raw = (rng.nextFloat() * 2f - 1f)
                    hpOut = 0.85f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    hpOut * exp(-t * 16.0) * (0.6 + 0.4 * sin(2.0 * PI * 42.0 * t))
                }
                8, 9, 10 -> { // Toms
                    val base = when (idx) { 8 -> 78.0; 9 -> 115.0; else -> 165.0 }
                    val f = base + base * 0.9 * exp(-t * 25.0)
                    phase += f / SR
                    sin(2.0 * PI * phase) * exp(-t * 9.5) * 0.85
                }
                11 -> { // Cowbell
                    phase += 540.0 / SR
                    phase2 += 800.0 / SR
                    val sq1 = if (sin(2.0 * PI * phase) > 0) 0.5 else -0.5
                    val sq2 = if (sin(2.0 * PI * phase2) > 0) 0.5 else -0.5
                    (sq1 + sq2) * 0.55 * exp(-t * 14.0)
                }
                12, 13 -> { // Percs
                    val base = if (idx == 12) 260.0 else 420.0
                    phase += (base + 200.0 * exp(-t * 40.0)) / SR
                    sin(2.0 * PI * phase) * exp(-t * 18.0) * 0.75
                }
                else -> { // Crash / Ride
                    val decay = if (idx == 14) 2.8 else 4.2
                    val raw = (rng.nextFloat() * 2f - 1f)
                    hpOut = 0.78f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    (hpOut * 0.65 + sin(2.0 * PI * 3800.0 * t) * 0.2) * exp(-t * decay)
                }
            }
            out[i] = s.toFloat().coerceIn(-0.98f, 0.98f)
        }
        return out
    }

    private fun synthesizeMelodic(kind: Int): FloatArray {
        val len = (SR * 1.4).toInt()
        val out = FloatArray(len)
        val f0 = 261.6256 // C4
        for (i in 0 until len) {
            val t = i.toDouble() / SR
            val s = when (kind) {
                0 -> { // Rhodes Electric Keys
                    val env = exp(-t * 2.1)
                    val tine = sin(2.0 * PI * f0 * 7.0 * t) * exp(-t * 18.0) * 0.25
                    val body = sin(2.0 * PI * f0 * t) + 0.35 * sin(2.0 * PI * f0 * 2.0 * t) * exp(-t * 4.0)
                    (body * 0.65 + tine) * env
                }
                1 -> { // Warm String Pad
                    val env = (1.0 - exp(-t * 5.0)) * exp(-t * 0.7)
                    val s1 = sin(2.0 * PI * f0 * t)
                    val s2 = sin(2.0 * PI * f0 * 1.004 * t) * 0.6
                    val s3 = sin(2.0 * PI * f0 * 2.0 * t) * 0.35
                    (s1 + s2 + s3) * 0.45 * env
                }
                2 -> { // Acoustic Pluck
                    val env = exp(-t * 4.8)
                    val h1 = sin(2.0 * PI * f0 * t)
                    val h2 = sin(2.0 * PI * f0 * 2.0 * t) * exp(-t * 8.0) * 0.5
                    val h3 = sin(2.0 * PI * f0 * 3.0 * t) * exp(-t * 14.0) * 0.35
                    (h1 + h2 + h3) * 0.6 * env
                }
                else -> { // Formant Choir
                    val env = (1.0 - exp(-t * 7.0)) * exp(-t * 0.9)
                    val vib = 1.0 + 0.005 * sin(2.0 * PI * 5.5 * t)
                    val a = sin(2.0 * PI * f0 * vib * t) * 0.5
                    val b = sin(2.0 * PI * f0 * 3.0 * vib * t) * 0.35
                    val c = sin(2.0 * PI * f0 * 5.0 * vib * t) * 0.2
                    (a + b + c) * 0.65 * env
                }
            }
            out[i] = s.toFloat().coerceIn(-0.98f, 0.98f)
        }
        return out
    }

    private fun synthesizeExtra(kind: Int, rng: Random): FloatArray {
        val len = when (kind) {
            0 -> (SR * 0.85).toInt()       // 808 Sub Kick
            1, 2, 3, 4, 5 -> (SR * 0.3).toInt() // Snare / Perc
            11 -> (SR * 1.6).toInt()       // Sub Drop FX
            else -> (SR * 1.25).toInt()    // Bass & Melodic
        }
        val out = FloatArray(len)
        var phase = 0.0
        var phase2 = 0.0
        var hpPrev = 0f
        var hpOut = 0f
        var lpState = 0.0
        for (i in 0 until len) {
            val t = i.toDouble() / SR
            val s = when (kind) {
                0 -> { // 808 Sub Kick
                    val f = 42.0 + 115.0 * exp(-t * 42.0)
                    phase += f / SR
                    val sub = sin(2.0 * PI * phase) * exp(-t * 3.1)
                    val punch = sin(2.0 * PI * 750.0 * t) * exp(-t * 160.0) * 0.28
                    tanh((sub + punch) * 1.35)
                }
                1 -> { // 808 Crisp Snare
                    phase += 175.0 / SR
                    val tone = sin(2.0 * PI * phase) * exp(-t * 28.0) * 0.5
                    val raw = rng.nextFloat() * 2f - 1f
                    hpOut = 0.72f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    tanh((tone + hpOut * exp(-t * 18.0) * 0.85) * 1.2)
                }
                2 -> { // Shaker 16th
                    val raw = rng.nextFloat() * 2f - 1f
                    hpOut = 0.88f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    val env = (1.0 - exp(-t * 95.0)) * exp(-t * 24.0)
                    hpOut * env * 0.85
                }
                3 -> { // Woodblock
                    val f = 820.0 + 60.0 * exp(-t * 90.0)
                    phase += f / SR
                    val m = sin(2.0 * PI * 1980.0 * t) * 0.25
                    (sin(2.0 * PI * phase) + m) * exp(-t * 32.0) * 0.8
                }
                4 -> { // Bongo High
                    val f = 310.0 + 180.0 * exp(-t * 48.0)
                    phase += f / SR
                    sin(2.0 * PI * phase) * exp(-t * 15.0) * 0.88
                }
                5 -> { // Laser Zap
                    val f = 90.0 + 1400.0 * exp(-t * 26.0)
                    phase += f / SR
                    tanh(sin(2.0 * PI * phase) * 1.5) * exp(-t * 14.0)
                }
                6 -> { // FM Reese Bass (C2)
                    val f0 = 65.406
                    val mod = sin(2.0 * PI * f0 * 2.0 * t) * 1.6 * exp(-t * 1.4)
                    val s1 = sin(2.0 * PI * f0 * t + mod)
                    val s2 = sin(2.0 * PI * f0 * 1.007 * t - mod * 0.6)
                    tanh((s1 + s2) * 0.6) * (1.0 - exp(-t * 60.0)) * exp(-t * 0.8)
                }
                7 -> { // Acid Pluck Bass (C2)
                    val f0 = 65.406
                    phase += f0 / SR
                    val saw = 2.0 * (phase - kotlin.math.floor(phase + 0.5))
                    val cut = 0.06 + 0.45 * exp(-t * 9.0)
                    lpState += cut * (saw - lpState)
                    tanh(lpState * 2.1) * exp(-t * 2.4)
                }
                8 -> { // Kalimba Mallet (C4)
                    val f0 = 261.6256
                    val body = sin(2.0 * PI * f0 * t)
                    val overtone = sin(2.0 * PI * f0 * 2.756 * t) * exp(-t * 28.0) * 0.35
                    val click = sin(2.0 * PI * 1800.0 * t) * exp(-t * 120.0) * 0.25
                    (body * 0.72 + overtone + click) * exp(-t * 3.8)
                }
                9 -> { // Brass Bell (C4)
                    val f0 = 261.6256
                    val mod = sin(2.0 * PI * f0 * 1.414 * t) * (2.5 * exp(-t * 3.2))
                    val carrier = sin(2.0 * PI * f0 * t + mod)
                    val sub = sin(2.0 * PI * f0 * 0.5 * t) * 0.3
                    (carrier * 0.65 + sub) * exp(-t * 1.6)
                }
                10 -> { // Lo-Fi Tape Piano (C4)
                    val f0 = 261.6256
                    val wobble = 1.0 + 0.004 * sin(2.0 * PI * 3.2 * t)
                    val h1 = sin(2.0 * PI * f0 * wobble * t)
                    val h2 = sin(2.0 * PI * f0 * 2.0 * wobble * t) * 0.42 * exp(-t * 2.5)
                    val h3 = sin(2.0 * PI * f0 * 3.0 * wobble * t) * 0.18 * exp(-t * 5.0)
                    tanh((h1 + h2 + h3) * 0.62) * (1.0 - exp(-t * 90.0)) * exp(-t * 1.8)
                }
                else -> { // Sub Drop FX
                    val f = 28.0 + 130.0 * exp(-t * 3.2)
                    phase += f / SR
                    phase2 += (f * 2.0) / SR
                    val wave = sin(2.0 * PI * phase) + 0.25 * sin(2.0 * PI * phase2) * exp(-t * 2.5)
                    tanh(wave * 1.2) * (1.0 - exp(-t * 55.0)) * exp(-t * 1.4)
                }
            }
            out[i] = s.toFloat().coerceIn(-0.98f, 0.98f)
        }
        return out
    }

    private fun synthesizeExpanded(kind: Int, rng: Random): FloatArray {
        val len = when (kind) {
            0 -> (SR * 1.1).toInt()             // Trap 808 Boom
            8, 10, 18 -> (SR * 0.45).toInt()    // House/Techno/Acoustic Kick
            12, 25, 26, 27, 28, 29, 30, 31 -> (SR * 1.3).toInt() // Stabs, Bass, Keys, FX
            21, 23, 24 -> (SR * 0.55).toInt()   // Ride, Metallic, Noise
            else -> (SR * 0.28).toInt()
        }
        val out = FloatArray(len)
        var phase = 0.0
        var phase2 = 0.0
        var hpPrev = 0f
        var hpOut = 0f
        var lpState = 0.0
        for (i in 0 until len) {
            val t = i.toDouble() / SR
            val s = when (kind) {
                0 -> { // Trap 808 Boom
                    val f = 38.0 + 145.0 * exp(-t * 48.0)
                    phase += f / SR
                    val body = sin(2.0 * PI * phase) * exp(-t * 2.4)
                    val punch = sin(2.0 * PI * 680.0 * t) * exp(-t * 170.0) * 0.3
                    tanh((body + punch) * 1.55)
                }
                1 -> { // Trap Snap Clap
                    val burst = if (t < 0.025) abs(sin(t * 320.0)) else 1.0
                    val raw = rng.nextFloat() * 2f - 1f
                    hpOut = 0.78f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    tanh(hpOut * exp(-t * 16.0) * burst * 1.4)
                }
                2 -> { // Trap Crisp Hat
                    val raw = rng.nextFloat() * 2f - 1f
                    hpOut = 0.90f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    (hpOut * 0.7 + sin(2.0 * PI * 7400.0 * t) * 0.25) * exp(-t * 48.0)
                }
                3 -> { // Trap Tight Snare
                    phase += 210.0 / SR
                    val tone = sin(2.0 * PI * phase) * exp(-t * 34.0) * 0.52
                    val noise = (rng.nextFloat() * 2f - 1f) * exp(-t * 22.0) * 0.65
                    tanh((tone + noise) * 1.2)
                }
                4 -> { // Vinyl Punch Kick
                    val f = 54.0 + 115.0 * exp(-t * 32.0)
                    phase += f / SR
                    val thump = sin(2.0 * PI * phase) * exp(-t * 8.5)
                    val vinyl = (rng.nextFloat() * 2f - 1f) * exp(-t * 80.0) * 0.18
                    tanh((thump + vinyl) * 1.45)
                }
                5 -> { // Dusty Crunch Snare
                    phase += 170.0 / SR
                    val raw = rng.nextFloat() * 2f - 1f
                    val body = sin(2.0 * PI * phase) * exp(-t * 20.0) * 0.55
                    val snare = raw * exp(-t * 15.0) * 0.6
                    tanh((body + snare) * 1.35)
                }
                6 -> { // Tape Closed Hat
                    val raw = rng.nextFloat() * 2f - 1f
                    hpOut = 0.80f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    lpState += 0.35 * (hpOut - lpState)
                    lpState * exp(-t * 36.0) * 0.9
                }
                7 -> { // Lo-Fi Wood Rim
                    phase += (420.0 + 120.0 * exp(-t * 80.0)) / SR
                    (sin(2.0 * PI * phase) * 0.7 + sin(2.0 * PI * 1120.0 * t) * 0.3) * exp(-t * 42.0)
                }
                8 -> { // House 4x4 Kick
                    val f = 50.0 + 140.0 * exp(-t * 38.0)
                    phase += f / SR
                    val body = sin(2.0 * PI * phase) * exp(-t * 7.8)
                    val click = sin(2.0 * PI * 1100.0 * t) * exp(-t * 150.0) * 0.4
                    tanh((body + click) * 1.55)
                }
                9 -> { // Classic House Clap
                    val env = if (t < 0.03) 0.7 + 0.3 * sin(t * 280.0) else 1.0
                    val raw = rng.nextFloat() * 2f - 1f
                    hpOut = 0.75f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    hpOut * env * exp(-t * 12.5) * 0.9
                }
                10 -> { // Techno Rumble Kick
                    val f = 46.0 + 160.0 * exp(-t * 44.0)
                    phase += f / SR
                    val sub = sin(2.0 * PI * phase) * exp(-t * 5.2)
                    val rumble = (rng.nextFloat() * 2f - 1f) * 0.18 * exp(-t * 4.5)
                    lpState += 0.04 * (rumble - lpState)
                    tanh((sub + lpState * 2.5) * 1.7)
                }
                11 -> { // Metallic 909 Hat
                    val raw = rng.nextFloat() * 2f - 1f
                    hpOut = 0.87f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    val metal = sin(2.0 * PI * 4900.0 * t) * 0.3 + sin(2.0 * PI * 7600.0 * t) * 0.25
                    (hpOut * 0.65 + metal) * exp(-t * 28.0)
                }
                12 -> { // Deep House Stab (C3 minor triad)
                    val freqs = doubleArrayOf(130.81, 155.56, 196.00)
                    var sum = 0.0
                    for (fq in freqs) {
                        val ph = (fq * t) % 1.0
                        sum += (2.0 * ph - 1.0)
                    }
                    val cut = 0.08 + 0.42 * exp(-t * 7.5)
                    lpState += cut * (sum * 0.33 - lpState)
                    tanh(lpState * 1.5) * (1.0 - exp(-t * 90.0)) * exp(-t * 3.0)
                }
                13 -> { // Minimal Micro Click
                    phase += (1200.0 * exp(-t * 120.0) + 180.0) / SR
                    sin(2.0 * PI * phase) * exp(-t * 95.0) * 0.85
                }
                14 -> { // Minimal FM Blip
                    val mod = sin(2.0 * PI * 980.0 * t) * 2.2 * exp(-t * 35.0)
                    sin(2.0 * PI * 440.0 * t + mod) * exp(-t * 24.0) * 0.78
                }
                15 -> { // Minimal Sine Tom
                    val f = 95.0 + 75.0 * exp(-t * 22.0)
                    phase += f / SR
                    sin(2.0 * PI * phase) * exp(-t * 11.0) * 0.85
                }
                16 -> { // Warm Conga
                    val f = 220.0 + 130.0 * exp(-t * 35.0)
                    phase += f / SR
                    (sin(2.0 * PI * phase) + 0.18 * sin(2.0 * PI * phase * 2.0)) * exp(-t * 11.5) * 0.78
                }
                17 -> { // Rosewood Clave
                    (sin(2.0 * PI * 2480.0 * t) * 0.65 + sin(2.0 * PI * 1240.0 * t) * 0.35) * exp(-t * 44.0)
                }
                18 -> { // Acoustic Kick
                    val f = 56.0 + 95.0 * exp(-t * 28.0)
                    phase += f / SR
                    val head = sin(2.0 * PI * phase) * exp(-t * 9.0)
                    val beater = (rng.nextFloat() * 2f - 1f) * exp(-t * 95.0) * 0.35
                    tanh((head + beater) * 1.3)
                }
                19 -> { // Acoustic Brush Snare
                    phase += 192.0 / SR
                    val shell = sin(2.0 * PI * phase) * exp(-t * 24.0) * 0.42
                    val brush = (rng.nextFloat() * 2f - 1f) * exp(-t * 10.5) * 0.68
                    shell + brush
                }
                20 -> { // Acoustic Hi-Hat
                    val raw = rng.nextFloat() * 2f - 1f
                    hpOut = 0.84f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    (hpOut * 0.72 + sin(2.0 * PI * 6100.0 * t) * 0.2) * exp(-t * 32.0)
                }
                21 -> { // Acoustic Jazz Ride
                    val raw = rng.nextFloat() * 2f - 1f
                    hpOut = 0.82f * (hpOut + raw - hpPrev)
                    hpPrev = raw
                    val ping = sin(2.0 * PI * 3250.0 * t) * exp(-t * 14.0) * 0.38
                    (hpOut * 0.45 * exp(-t * 4.5) + ping)
                }
                22 -> { // Glitch Granule
                    val stepF = if ((i / 180) % 2 == 0) 640.0 else 1420.0
                    phase += stepF / SR
                    val sq = if (sin(2.0 * PI * phase) > 0) 0.7 else -0.7
                    sq * exp(-t * 22.0) * (if ((i / 90) % 3 == 0) 1.0 else 0.45)
                }
                23 -> { // Metallic Ring Hit
                    val c1 = sin(2.0 * PI * 520.0 * t)
                    val c2 = sin(2.0 * PI * 1190.0 * t)
                    (c1 * c2) * exp(-t * 7.5) * 0.85
                }
                24 -> { // White Noise Burst
                    (rng.nextFloat() * 2f - 1f) * (1.0 - exp(-t * 50.0)) * exp(-t * 4.5) * 0.75
                }
                25 -> { // Clean Sub C2 (65.406 Hz)
                    val f0 = 65.406
                    val sub = sin(2.0 * PI * f0 * t) * 0.85 + sin(2.0 * PI * f0 * 2.0 * t) * 0.18
                    tanh(sub * 1.15) * (1.0 - exp(-t * 80.0)) * exp(-t * 1.2)
                }
                26 -> { // UKG Organ Bass (C2)
                    val f0 = 65.406
                    val h1 = sin(2.0 * PI * f0 * t)
                    val h2 = sin(2.0 * PI * f0 * 2.0 * t) * 0.65
                    val h3 = sin(2.0 * PI * f0 * 3.0 * t) * 0.42
                    val env = (1.0 - exp(-t * 120.0)) * exp(-t * 4.2)
                    tanh((h1 + h2 + h3) * 0.55) * env
                }
                27 -> { // Trance Saw Pluck (C4)
                    val f0 = 261.6256
                    val s1 = 2.0 * ((f0 * t) % 1.0) - 1.0
                    val s2 = 2.0 * ((f0 * 1.006 * t) % 1.0) - 1.0
                    val cut = 0.06 + 0.55 * exp(-t * 10.0)
                    lpState += cut * ((s1 + s2) * 0.45 - lpState)
                    lpState * (1.0 - exp(-t * 100.0)) * exp(-t * 2.4)
                }
                28 -> { // Warm Wurli Keys (C4)
                    val f0 = 261.6256
                    val bark = sin(2.0 * PI * f0 * 5.0 * t) * exp(-t * 14.0) * 0.28
                    val body = sin(2.0 * PI * f0 * t) + 0.3 * sin(2.0 * PI * f0 * 2.0 * t) * exp(-t * 3.5)
                    tanh((body * 0.65 + bark) * 1.2) * exp(-t * 1.9)
                }
                29 -> { // Wooden Marimba (C4)
                    val f0 = 261.6256
                    val body = sin(2.0 * PI * f0 * t) * 0.78
                    val h4 = sin(2.0 * PI * f0 * 4.0 * t) * exp(-t * 26.0) * 0.35
                    (body + h4) * exp(-t * 4.5)
                }
                30 -> { // Shimmer Glass Pad (C4)
                    val f0 = 261.6256
                    val env = (1.0 - exp(-t * 4.5)) * exp(-t * 0.65)
                    val shimmer = sin(2.0 * PI * f0 * t) * 0.45 +
                        sin(2.0 * PI * f0 * 2.002 * t) * 0.32 +
                        sin(2.0 * PI * f0 * 3.0 * t) * 0.18
                    shimmer * env
                }
                else -> { // Noise Sweep Hit
                    val f = 140.0 + 1800.0 * (t / 1.3)
                    phase += f / SR
                    val raw = rng.nextFloat() * 2f - 1f
                    (raw * 0.45 + sin(2.0 * PI * phase) * 0.35) * (1.0 - exp(-t * 12.0)) * exp(-t * 1.6)
                }
            }
            out[i] = s.toFloat().coerceIn(-0.98f, 0.98f)
        }
        return out
    }
}

object AudioDecoder {
    suspend fun decodeFromUri(context: Context, uri: Uri): Pair<String, DecodedSample>? = withContext(Dispatchers.IO) {
        val displayName = resolveName(context, uri)
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        // Fast path for WAV
        SamplePcmTools.readWav(rawBytes)?.let { return@withContext displayName to it }

        // MediaExtractor + MediaCodec fallback for MP3, M4A, FLAC, AIFF, OGG
        val tmpFile = File.createTempFile("import_", ".audio", context.cacheDir)
        try {
            tmpFile.writeBytes(rawBytes)
            val decoded = decodeViaMediaCodec(tmpFile.absolutePath) ?: return@withContext null
            displayName to decoded
        } finally {
            tmpFile.delete()
        }
    }

    private fun resolveName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Sample"
    }

    private fun decodeViaMediaCodec(path: String): DecodedSample? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(path)
            var trackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIdx = i
                    format = f
                    break
                }
            }
            if (trackIdx < 0 || format == null) return null
            extractor.selectTrack(trackIdx)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmBytes = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var sr = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48000
            var ch = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            var sawInputEos = false
            var sawOutputEos = false
            var iter = 0

            while (!sawOutputEos && iter++ < 50000) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)
                        if (buf != null) {
                            val sampleSize = extractor.readSampleData(buf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.get(chunk)
                        pcmBytes.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEos = true
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sr = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            val raw = pcmBytes.toByteArray()
            if (raw.size < 2) return null
            val shorts = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val n = raw.size / 2
            val floats = FloatArray(n) { shorts.short / 32768f }
            val safeCh = ch.coerceAtLeast(1)
            val safeSr = sr.coerceAtLeast(8000)
            val frames = floats.size / safeCh
            val durMs = (frames * 1000L) / safeSr
            DecodedSample(safeSr, safeCh, floats, SamplePcmTools.computePeaks(floats, safeCh), durMs)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}

enum class MicRecordingState {
    READY,
    RECORDING,
    PROCESSING,
    SAVED,
    ERROR
}

class MicRecorder {
    private val _state = MutableStateFlow(MicRecordingState.READY)
    val state: StateFlow<MicRecordingState> = _state.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _actualSampleRate = MutableStateFlow(48000)
    val actualSampleRate: StateFlow<Int> = _actualSampleRate.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _livePeaks = MutableStateFlow<List<Float>>(emptyList())
    val livePeaks: StateFlow<List<Float>> = _livePeaks.asStateFlow()

    private var recordThread: Thread? = null
    private val collected = mutableListOf<Float>()

    fun clearError() {
        _errorMessage.value = null
        if (_state.value == MicRecordingState.ERROR) {
            _state.value = MicRecordingState.READY
        }
    }

    fun markSaved() {
        _state.value = MicRecordingState.SAVED
    }

    fun markError(msg: String) {
        _errorMessage.value = msg
        _state.value = MicRecordingState.ERROR
    }

    @SuppressLint("MissingPermission")
    fun start(preferredSampleRate: Int = 48000) {
        if (_isRecording.value) return
        synchronized(collected) { collected.clear() }
        _durationMs.value = 0L
        _livePeaks.value = emptyList()
        _errorMessage.value = null

        val candidateRates = listOf(preferredSampleRate.coerceIn(16000, 48000), 48000, 44100, 16000).distinct()
        var recorder: AudioRecord? = null
        var chosenSr = 48000
        var chosenMinBuf = 4096

        for (sr in candidateRates) {
            val minBuf = AudioRecord.getMinBufferSize(
                sr,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) continue
            val bufSize = (minBuf * 2).coerceAtLeast(4096)
            var candidate: AudioRecord? = null
            try {
                candidate = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sr,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    candidate.startRecording()
                    if (candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder = candidate
                        chosenSr = sr
                        chosenMinBuf = bufSize
                        break
                    }
                }
                runCatching { candidate.release() }
            } catch (_: Throwable) {
                runCatching { candidate?.release() }
            }
        }

        if (recorder == null) {
            _isRecording.value = false
            _state.value = MicRecordingState.ERROR
            _errorMessage.value = "Microphone unavailable or unsupported audio format"
            return
        }

        _actualSampleRate.value = chosenSr
        _isRecording.value = true
        _state.value = MicRecordingState.RECORDING

        val activeRecorder = recorder
        recordThread = Thread {
            val buf = ShortArray((chosenMinBuf / 4).coerceIn(512, 4096))
            val peaks = mutableListOf<Float>()
            val startTime = System.currentTimeMillis()
            try {
                while (_isRecording.value) {
                    val read = activeRecorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        var pk = 0f
                        synchronized(collected) {
                            for (i in 0 until read) {
                                val f = buf[i] / 32768f
                                collected.add(f)
                                val a = abs(f)
                                if (a > pk) pk = a
                            }
                        }
                        peaks.add(pk)
                        if (peaks.size > 120) peaks.removeAt(0)
                        _livePeaks.value = peaks.toList()
                        _durationMs.value = System.currentTimeMillis() - startTime
                    } else if (read < 0) {
                        _errorMessage.value = "Microphone read interrupted ($read)"
                        _state.value = MicRecordingState.ERROR
                        _isRecording.value = false
                        break
                    }
                }
            } catch (t: Throwable) {
                _errorMessage.value = t.message ?: "Microphone capture failed"
                _state.value = MicRecordingState.ERROR
                _isRecording.value = false
            } finally {
                runCatching {
                    activeRecorder.stop()
                    activeRecorder.release()
                }
            }
        }.apply { start() }
    }

    fun stopAndGetPcm(fallbackSampleRate: Int = 48000): DecodedSample? {
        if (!_isRecording.value) return null
        _state.value = MicRecordingState.PROCESSING
        _isRecording.value = false
        recordThread?.join(800)
        recordThread = null

        val sr = _actualSampleRate.value.coerceAtLeast(16000)
        val floats = synchronized(collected) { collected.toFloatArray() }
        val minFrames = (sr * 0.12f).toInt() // >= 120ms minimum valid sample
        if (floats.size < minFrames) {
            _state.value = MicRecordingState.ERROR
            _errorMessage.value = "Recording too short — hold mic longer"
            return null
        }

        // Safely normalize quiet mic input without amplifying pure noise floor
        var peak = 0f
        for (f in floats) {
            val a = abs(f)
            if (a > peak) peak = a
        }
        if (peak in 0.004f..0.55f) {
            val gain = (0.85f / peak).coerceAtMost(8f)
            for (i in floats.indices) {
                floats[i] = (floats[i] * gain).coerceIn(-0.98f, 0.98f)
            }
        }

        val durMs = (floats.size * 1000L) / sr
        _state.value = MicRecordingState.SAVED
        return DecodedSample(sr, 1, floats, SamplePcmTools.computePeaks(floats, 1), durMs)
    }
}
