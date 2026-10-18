package org.jhaeussler.practicetracker.drummachine.sound

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavAudioDecoder {
    /**
     * Decodes a standard 16-bit signed PCM RIFF/WAV stream into a mono [ShortArray].
     *
     * - If the source is stereo (2 channels), interleaved L/R samples are downmixed: (L + R) / 2.
     * - If [expectedSampleRate] is provided and does not match the header, [IllegalArgumentException] is thrown.
     * - Non-PCM (e.g. compressed, float) or bit depths other than 16-bit throw [IllegalArgumentException].
     * - Chunks such as 'JUNK' or 'LIST' preceding the 'data' chunk are cleanly skipped.
     */
    fun decodeToMonoPcm(
        inputStream: InputStream,
        expectedSampleRate: Int? = null
    ): ShortArray
    {
        // 1. Validate 12-byte RIFF/WAVE Container Header
        val header = ByteArray(12)

        if (!readFully(inputStream, header, 0, 12)) {
            throw IllegalArgumentException("Stream too short or invalid WAV header")
        }

        val riffTag = String(header, 0, 4, Charsets.US_ASCII)
        val waveTag = String(header, 8, 4, Charsets.US_ASCII)

        if (riffTag != "RIFF" || waveTag != "WAVE") {
            throw IllegalArgumentException("Invalid WAV: Missing RIFF/WAVE header")
        }

        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var fmtParsed = false

        val chunkHeader = ByteArray(8)

        // 2. Iterate through Subchunks
        while (readFully(inputStream, chunkHeader, 0, 8))
        {
            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int

            val chunkSizeLong = chunkSize.toLong() and 0xFFFFFFFFL

            when (chunkId)
            {
                "fmt " -> {
                    if (chunkSize < 16) {
                        throw IllegalArgumentException("Malformed 'fmt ' chunk size: $chunkSize")
                    }

                    val fmtData = ByteArray(chunkSize)

                    if (!readFully(inputStream, fmtData, 0, chunkSize)) {
                        throw IllegalArgumentException("Truncated 'fmt ' chunk")
                    }

                    val bb = ByteBuffer.wrap(fmtData).order(ByteOrder.LITTLE_ENDIAN)
                    val audioFormat = bb.short.toInt() and 0xFFFF

                    channels = bb.short.toInt() and 0xFFFF
                    sampleRate = bb.int
                    bb.int   // byteRate (ignored)
                    bb.short // blockAlign (ignored)
                    bitsPerSample = bb.short.toInt() and 0xFFFF

                    if (audioFormat != 1) {
                        throw IllegalArgumentException(
                            "Unsupported WAV format: $audioFormat (only uncompressed PCM (1) supported)"
                        )
                    }
                    if (channels !in 1..2) {
                        throw IllegalArgumentException(
                            "Unsupported channel count: $channels (only mono (1) and stereo (2) supported)"
                        )
                    }
                    if (bitsPerSample != 16) {
                        throw IllegalArgumentException(
                            "Unsupported bit depth: $bitsPerSample (only 16-bit PCM supported)"
                        )
                    }
                    if (expectedSampleRate != null && sampleRate != expectedSampleRate) {
                        throw IllegalArgumentException(
                            "Sample rate mismatch: file is $sampleRate Hz, expected $expectedSampleRate Hz"
                        )
                    }

                    fmtParsed = true
                }

                "data" -> {
                    if (!fmtParsed) {
                        throw IllegalArgumentException("'data' chunk encountered before 'fmt ' chunk")
                    }

                    val dataBytes = ByteArray(chunkSize)

                    if (!readFully(inputStream, dataBytes, 0, chunkSize)) {
                        throw IllegalArgumentException("Truncated 'data' chunk")
                    }

                    val bb = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN)

                    return if (channels == 1)
                    {
                        // Mono decoding
                        val numSamples = chunkSize / 2
                        val samples = ShortArray(numSamples)

                        for (i in 0 until numSamples) {
                            samples[i] = bb.short
                        }

                        samples
                    }
                    else {
                        // Stereo downmixing to mono: (L + R) / 2
                        val numFrames = chunkSize / 4
                        val samples = ShortArray(numFrames)

                        for (i in 0 until numFrames)
                        {
                            val left = bb.short.toInt()
                            val right = bb.short.toInt()
                            samples[i] = ((left + right) / 2).toShort()
                        }

                        samples
                    }
                }

                else -> {
                    // Skip unsupported or metadata chunks (e.g. 'LIST', 'JUNK', 'PEAK')
                    val pad = if (chunkSizeLong % 2L != 0L) 1L else 0L
                    skipFully(inputStream, chunkSizeLong + pad)
                }
            }
        }

        throw IllegalArgumentException("Missing 'data' chunk in WAV stream")
    }

    private fun readFully(inputStream: InputStream, buffer: ByteArray, offset: Int, length: Int): Boolean
    {
        var bytesRead = 0

        while (bytesRead < length)
        {
            val count = inputStream.read(buffer, offset + bytesRead, length - bytesRead)

            if (count < 0) return false

            bytesRead += count
        }

        return true
    }

    private fun skipFully(inputStream: InputStream, bytesToSkip: Long)
    {
        var remaining = bytesToSkip

        while (remaining > 0)
        {
            val skipped = inputStream.skip(remaining)

            if (skipped <= 0)
            {
                // InputStream.skip may return 0 on some stream implementations; fallback to single-byte read
                if (inputStream.read() == -1) break
                remaining -= 1
            }
            else {
                remaining -= skipped
            }
        }
    }
}