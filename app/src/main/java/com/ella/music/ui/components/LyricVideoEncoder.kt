package com.ella.music.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.ella.music.R
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.min

internal data class LyricVideoProgress(
    val current: Int,
    val total: Int
) {
    val fraction: Float get() = if (total > 0) current.toFloat() / total else 0f
}

private const val LYRIC_VIDEO_TAG = "LyricVideoEncoder"

internal suspend fun generateLyricVideo(
    context: Context,
    song: Song?,
    lines: List<LyricLine>,
    cover: Bitmap?,
    includeTranslation: Boolean,
    typeface: android.graphics.Typeface? = null,
    onProgress: (LyricVideoProgress) -> Unit
): Uri? = withContext(Dispatchers.Default) {
    val renderer = LyricVideoRenderer(
        cover = cover,
        lines = lines,
        includeTranslation = includeTranslation,
        typeface = typeface
    )
    val totalFrames = renderer.totalFrames()
    if (totalFrames <= 0) {
        renderer.recycle()
        return@withContext null
    }

    val dir = File(context.cacheDir, "lyric_video_share").apply {
        deleteRecursively()
        mkdirs()
    }
    val videoOnlyFile = File(dir, "video_only_${System.currentTimeMillis()}.mp4")
    val outputFile = File(dir, "halcyon_lyric_${System.currentTimeMillis()}.mp4")

    try {
        encodeVideoTrack(renderer, totalFrames, videoOnlyFile, onProgress)
        renderer.recycle()

        val audioMuxed = muxVideoAndAudio(
            context = context,
            song = song,
            lines = lines,
            videoFile = videoOnlyFile,
            outputFile = outputFile
        )

        val shareFile = if (audioMuxed) {
            videoOnlyFile.delete()
            outputFile
        } else {
            outputFile.delete()
            videoOnlyFile
        }
        if (!shareFile.exists()) return@withContext null

        onProgress(LyricVideoProgress(totalFrames, totalFrames))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
    } catch (e: CancellationException) {
        renderer.recycle()
        videoOnlyFile.delete()
        outputFile.delete()
        throw e
    } catch (e: Exception) {
        renderer.recycle()
        videoOnlyFile.delete()
        outputFile.delete()
        null
    }
}

private suspend fun encodeVideoTrack(
    renderer: LyricVideoRenderer,
    totalFrames: Int,
    outputFile: File,
    onProgress: (LyricVideoProgress) -> Unit
) {
    val size = LyricVideoRenderer.VIDEO_SIZE
    val fps = LyricVideoRenderer.FPS
    val frameDurationUs = 1_000_000L / fps

    val videoFormat = MediaFormat.createVideoFormat(
        MediaFormat.MIMETYPE_VIDEO_AVC, size, size
    ).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val inputSurface = encoder.createInputSurface()
    encoder.start()

    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var videoTrackIndex = -1
    var muxerStarted = false
    val bufferInfo = MediaCodec.BufferInfo()
    var outputFrameCount = 0L

    val frameBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val frameCanvas = android.graphics.Canvas(frameBitmap)
    val bitmapPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

    try {
        for (frameIndex in 0 until totalFrames) {
            coroutineContext.ensureActive()
            onProgress(LyricVideoProgress(frameIndex, totalFrames))

            frameCanvas.drawColor(android.graphics.Color.BLACK)
            renderer.drawFrame(frameCanvas, frameIndex)

            val surfaceCanvas = inputSurface.lockCanvas(null)
            try {
                surfaceCanvas.drawBitmap(frameBitmap, 0f, 0f, bitmapPaint)
            } finally {
                inputSurface.unlockCanvasAndPost(surfaceCanvas)
            }

            drainEncoder(
                encoder = encoder,
                muxer = muxer,
                bufferInfo = bufferInfo,
                endOfStream = false,
                frameDurationUs = frameDurationUs,
                trackIndex = videoTrackIndex,
                onFrameWritten = { outputFrameCount++ }
            ) { format ->
                videoTrackIndex = muxer.addTrack(format)
                muxer.start()
                muxerStarted = true
                videoTrackIndex
            }.let { result -> if (result.first >= 0) videoTrackIndex = result.first }
        }

        encoder.signalEndOfInputStream()

        var eos = false
        while (!eos) {
            val result = drainEncoder(
                encoder = encoder,
                muxer = muxer,
                bufferInfo = bufferInfo,
                endOfStream = true,
                frameDurationUs = frameDurationUs,
                trackIndex = videoTrackIndex,
                onFrameWritten = { outputFrameCount++ }
            ) { format ->
                if (videoTrackIndex < 0) {
                    videoTrackIndex = muxer.addTrack(format)
                    muxer.start()
                    muxerStarted = true
                }
                videoTrackIndex
            }
            if (result.first >= 0) videoTrackIndex = result.first
            eos = result.second
        }
    } finally {
        frameBitmap.recycle()
        try { encoder.stop() } catch (_: Exception) {}
        try { encoder.release() } catch (_: Exception) {}
        try { inputSurface.release() } catch (_: Exception) {}
        if (muxerStarted) {
            try { muxer.stop() } catch (_: Exception) {}
        }
        try { muxer.release() } catch (_: Exception) {}
    }
}

private fun drainEncoder(
    encoder: MediaCodec,
    muxer: MediaMuxer,
    bufferInfo: MediaCodec.BufferInfo,
    endOfStream: Boolean,
    frameDurationUs: Long,
    trackIndex: Int,
    onFrameWritten: () -> Long,
    onFormatAvailable: (MediaFormat) -> Int
): Pair<Int, Boolean> {
    val timeoutUs = if (endOfStream) 10_000L else 0L
    var resolvedTrackIndex = trackIndex
    var hitEos = false

    while (true) {
        val index = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
        when {
            index == MediaCodec.INFO_TRY_AGAIN_LATER -> break
            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                resolvedTrackIndex = onFormatAvailable(encoder.outputFormat)
            }
            index >= 0 -> {
                val buf = encoder.getOutputBuffer(index)
                if (buf != null &&
                    bufferInfo.size > 0 &&
                    bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                    resolvedTrackIndex >= 0
                ) {
                    val frameNum = onFrameWritten()
                    bufferInfo.presentationTimeUs = frameNum * frameDurationUs
                    muxer.writeSampleData(resolvedTrackIndex, buf, bufferInfo)
                }
                encoder.releaseOutputBuffer(index, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    hitEos = true
                    break
                }
            }
        }
    }
    return Pair(resolvedTrackIndex, hitEos)
}

private fun muxVideoAndAudio(
    context: Context,
    song: Song?,
    lines: List<LyricLine>,
    videoFile: File,
    outputFile: File
): Boolean {
    if (song == null || lines.isEmpty()) return false
    val path = song.path
    if (path.isBlank()) return false

    val globalStartMs = lines.first().timeMs
    val globalEndMs = lines.last().let { line ->
        val wordEnd = line.words.maxOfOrNull { it.endMs }
        wordEnd ?: line.endMs ?: (line.timeMs + 4000L)
    } + LyricVideoRenderer.DISSOLVE_MS + 300L

    val audioClipFile = File(
        outputFile.parentFile ?: context.cacheDir,
        "audio_clip_${System.currentTimeMillis()}.m4a"
    )
    val clipStartUs = globalStartMs * 1000L
    val clipEndUs = globalEndMs * 1000L

    return try {
        val sourceAudioMime = findPrimaryAudioTrackMime(context, path)
        if (shouldTryDirectSourceAudioMux(sourceAudioMime)) {
            val directMuxed = muxVideoWithSourceAudioSegment(
                context = context,
                videoFile = videoFile,
                songPath = path,
                outputFile = outputFile,
                clipStartUs = clipStartUs,
                clipEndUs = clipEndUs
            )
            if (directMuxed) {
                Log.d(
                    LYRIC_VIDEO_TAG,
                    "Muxed lyric share source audio track directly for ${song.title} mime=${sourceAudioMime.orEmpty()}"
                )
                return true
            }
            Log.w(
                LYRIC_VIDEO_TAG,
                "Direct source audio mux failed for ${song.title} mime=${sourceAudioMime.orEmpty()}, falling back to transcode"
            )
        }
        if (!transcodeAudioSegmentToAac(context, path, globalStartMs, globalEndMs, audioClipFile)) {
            outputFile.delete()
            return false
        }
        muxVideoWithAudioTrack(videoFile, audioClipFile, outputFile)
    } catch (error: Exception) {
        Log.w(LYRIC_VIDEO_TAG, "Failed to mux lyric share audio for ${song.title}", error)
        outputFile.delete()
        false
    } finally {
        audioClipFile.delete()
    }
}

private fun findPrimaryAudioTrackMime(context: Context, songPath: String): String? {
    val extractor = MediaExtractor()
    return try {
        extractor.setSongDataSource(context, songPath)
        (0 until extractor.trackCount)
            .asSequence()
            .map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
            .firstOrNull { it.startsWith("audio/") }
    } catch (error: Exception) {
        Log.w(LYRIC_VIDEO_TAG, "Failed to inspect lyric share source track mime for $songPath", error)
        null
    } finally {
        try { extractor.release() } catch (_: Exception) {}
    }
}

internal fun shouldTryDirectSourceAudioMux(sourceAudioMime: String?): Boolean {
    val normalizedMime = sourceAudioMime.orEmpty().lowercase()
    // Only direct-copy codecs that stay broadly playable inside the exported MP4.
    // Lossless M4A/ALAC must be transcoded to AAC, otherwise the lyric video can
    // look correct but play back silently in other apps.
    return normalizedMime == MediaFormat.MIMETYPE_AUDIO_AAC ||
        normalizedMime == "audio/mpeg"
}

private fun transcodeAudioSegmentToAac(
    context: Context,
    songPath: String,
    clipStartMs: Long,
    clipEndMs: Long,
    outputFile: File
): Boolean {
    val clipStartUs = clipStartMs * 1000L
    val clipEndUs = clipEndMs * 1000L
    if (clipEndUs <= clipStartUs) return false

    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    var encoder: MediaCodec? = null
    var muxer: MediaMuxer? = null

    return try {
        extractor.setSongDataSource(context, songPath)

        val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()
                .startsWith("audio/")
        } ?: return false

        val sourceFormat = extractor.getTrackFormat(audioTrackIndex)
        val sourceMime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: return false
        val sampleRate = sourceFormat.optionalInteger(MediaFormat.KEY_SAMPLE_RATE) ?: return false
        val channelCount = sourceFormat.optionalInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: return false
        val targetBitRate = when {
            channelCount >= 2 && sampleRate >= 48_000 -> 256_000
            channelCount >= 2 -> 192_000
            else -> 128_000
        }

        extractor.selectTrack(audioTrackIndex)
        extractor.seekTo(clipStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        // Force 16-bit PCM output from the decoder. Without this, high-res sources
        // (24-bit FLAC, 32-bit float, etc.) may output PCM in a different bit depth,
        // and if KEY_PCM_ENCODING isn't reported in the output format, pcmBytesPerFrame
        // defaults to channels*2 (16-bit assumption) — causing PTS drift and pitch shift.
        try {
            sourceFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        } catch (_: Exception) {}

        decoder = MediaCodec.createDecoderByType(sourceMime).apply {
            configure(sourceFormat, null, null, 0)
            start()
        }

        val encoderFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channelCount
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, targetBitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 256 * 1024)
        }
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        val activeMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = activeMuxer

        val decoderInfo = MediaCodec.BufferInfo()
        val encoderInfo = MediaCodec.BufferInfo()
        var muxerTrackIndex = -1
        var muxerStarted = false
        var decoderInputEnded = false
        var decoderOutputEnded = false
        var encoderInputEnded = false
        var encoderOutputEnded = false
        var pcmBytesPerFrame = channelCount * 2
        var effectiveSampleRate = sampleRate
        var lastQueuedAudioPtsUs = 0L

        while (!encoderOutputEnded) {
            if (!decoderInputEnded) {
                val decoderInputIndex = decoder.dequeueInputBuffer(10_000)
                if (decoderInputIndex >= 0) {
                    val decoderInputBuffer = decoder.getInputBuffer(decoderInputIndex)
                    if (decoderInputBuffer == null) {
                        decoder.queueInputBuffer(decoderInputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        decoderInputEnded = true
                    } else {
                        decoderInputBuffer.clear()
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleTimeUs < 0 || sampleTimeUs > clipEndUs) {
                            decoder.queueInputBuffer(
                                decoderInputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            decoderInputEnded = true
                        } else {
                            val sampleSize = extractor.readSampleData(decoderInputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    decoderInputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                decoderInputEnded = true
                            } else {
                                decoder.queueInputBuffer(
                                    decoderInputIndex,
                                    0,
                                    sampleSize,
                                    sampleTimeUs,
                                    extractor.sampleFlags
                                )
                                extractor.advance()
                            }
                        }
                    }
                }
            }

            var decoderProgress = true
            while (decoderProgress && !decoderOutputEnded) {
                val decoderOutputIndex = decoder.dequeueOutputBuffer(decoderInfo, 10_000)
                when {
                    decoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        decoderProgress = false
                    }
                    decoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        effectiveSampleRate =
                            outputFormat.optionalInteger(MediaFormat.KEY_SAMPLE_RATE) ?: effectiveSampleRate
                        val outputChannels =
                            outputFormat.optionalInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: channelCount
                        val bytesPerSample = outputFormat.bytesPerSample()
                        pcmBytesPerFrame = (outputChannels * bytesPerSample).coerceAtLeast(1)
                    }
                    decoderOutputIndex >= 0 -> {
                        val isEos =
                            decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val decodeTimeUs = decoderInfo.presentationTimeUs
                        val shouldEncode =
                            decoderInfo.size > 0 && decodeTimeUs in clipStartUs..clipEndUs
                        if (shouldEncode) {
                            val decodedBuffer = decoder.getOutputBuffer(decoderOutputIndex)
                            if (decodedBuffer != null) {
                                decodedBuffer.position(decoderInfo.offset)
                                decodedBuffer.limit(decoderInfo.offset + decoderInfo.size)

                                var consumedBytes = 0
                                while (consumedBytes < decoderInfo.size) {
                                    val encoderInputIndex = waitForEncoderInputBuffer(
                                        encoder = encoder,
                                        bufferInfo = encoderInfo,
                                        onFormatChanged = {
                                            if (!muxerStarted) {
                                                muxerTrackIndex = activeMuxer.addTrack(encoder.outputFormat)
                                                activeMuxer.start()
                                                muxerStarted = true
                                            }
                                        },
                                        onSampleData = { buffer, info ->
                                            if (muxerStarted && muxerTrackIndex >= 0) {
                                                activeMuxer.writeSampleData(muxerTrackIndex, buffer, info)
                                            }
                                        }
                                    ).takeIf { it >= 0 } ?: break

                                    val encoderInputBuffer = encoder.getInputBuffer(encoderInputIndex)
                                    if (encoderInputBuffer == null) {
                                        encoder.queueInputBuffer(
                                            encoderInputIndex,
                                            0,
                                            0,
                                            lastQueuedAudioPtsUs,
                                            0
                                        )
                                        continue
                                    }

                                    encoderInputBuffer.clear()
                                    val copySize = min(
                                        decoderInfo.size - consumedBytes,
                                        encoderInputBuffer.remaining()
                                    )
                                    val chunk = decodedBuffer.duplicate().apply {
                                        position(decodedBuffer.position())
                                        limit(decodedBuffer.position() + copySize)
                                    }
                                    encoderInputBuffer.put(chunk)
                                    decodedBuffer.position(decodedBuffer.position() + copySize)

                                    val ptsUs = (decodeTimeUs - clipStartUs).coerceAtLeast(0L) +
                                        pcmBytesToDurationUs(
                                            consumedBytes,
                                            pcmBytesPerFrame,
                                            effectiveSampleRate
                                        )
                                    lastQueuedAudioPtsUs = ptsUs
                                    encoder.queueInputBuffer(
                                        encoderInputIndex,
                                        0,
                                        copySize,
                                        ptsUs,
                                        0
                                    )
                                    consumedBytes += copySize
                                }
                            }
                        }

                        decoder.releaseOutputBuffer(decoderOutputIndex, false)

                        if (isEos || decodeTimeUs > clipEndUs) {
                            decoderOutputEnded = true
                        }
                    }
                }
            }

            if (decoderOutputEnded && !encoderInputEnded) {
                val encoderInputIndex = waitForEncoderInputBuffer(
                    encoder = encoder,
                    bufferInfo = encoderInfo,
                    onFormatChanged = {
                        if (!muxerStarted) {
                            muxerTrackIndex = activeMuxer.addTrack(encoder.outputFormat)
                            activeMuxer.start()
                            muxerStarted = true
                        }
                    },
                    onSampleData = { buffer, info ->
                        if (muxerStarted && muxerTrackIndex >= 0) {
                            activeMuxer.writeSampleData(muxerTrackIndex, buffer, info)
                        }
                    }
                )
                if (encoderInputIndex >= 0) {
                    encoder.queueInputBuffer(
                        encoderInputIndex,
                        0,
                        0,
                        lastQueuedAudioPtsUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    encoderInputEnded = true
                }
            }

            encoderOutputEnded = drainAudioEncoder(
                encoder = encoder,
                bufferInfo = encoderInfo,
                onFormatChanged = {
                    if (!muxerStarted) {
                        muxerTrackIndex = activeMuxer.addTrack(encoder.outputFormat)
                        activeMuxer.start()
                        muxerStarted = true
                    }
                },
                onSampleData = { buffer, info ->
                    if (muxerStarted && muxerTrackIndex >= 0) {
                        activeMuxer.writeSampleData(muxerTrackIndex, buffer, info)
                    }
                }
            )
        }

        if (muxerStarted) {
            activeMuxer.stop()
        }
        activeMuxer.release()
        muxer = null
        outputFile.exists() && outputFile.length() > 0L
    } catch (error: Exception) {
        Log.w(LYRIC_VIDEO_TAG, "Failed to transcode lyric share audio from $songPath", error)
        outputFile.delete()
        false
    } finally {
        try { extractor.release() } catch (_: Exception) {}
        try { decoder?.stop() } catch (_: Exception) {}
        try { decoder?.release() } catch (_: Exception) {}
        try { encoder?.stop() } catch (_: Exception) {}
        try { encoder?.release() } catch (_: Exception) {}
        try { muxer?.stop() } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
    }
}

private fun muxVideoWithAudioTrack(
    videoFile: File,
    audioFile: File,
    outputFile: File
): Boolean {
    val videoExtractor = MediaExtractor()
    val audioExtractor = MediaExtractor()
    var muxer: MediaMuxer? = null

    return try {
        videoExtractor.setDataSource(videoFile.absolutePath)
        audioExtractor.setDataSource(audioFile.absolutePath)

        val videoTrackSrc = (0 until videoExtractor.trackCount).firstOrNull { index ->
            videoExtractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()
                .startsWith("video/")
        } ?: return false
        val audioTrackSrc = (0 until audioExtractor.trackCount).firstOrNull { index ->
            audioExtractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()
                .startsWith("audio/")
        } ?: return false

        val videoFormat = videoExtractor.getTrackFormat(videoTrackSrc)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackSrc)

        val activeMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = activeMuxer
        val muxerVideoTrack = activeMuxer.addTrack(videoFormat)
        val muxerAudioTrack = activeMuxer.addTrack(audioFormat)
        activeMuxer.start()

        copySelectedTrack(videoExtractor, videoTrackSrc, activeMuxer, muxerVideoTrack)
        copySelectedTrack(
            extractor = audioExtractor,
            sourceTrackIndex = audioTrackSrc,
            muxer = activeMuxer,
            targetTrackIndex = muxerAudioTrack,
            normalizeToZero = true
        )

        activeMuxer.stop()
        activeMuxer.release()
        muxer = null
        outputFile.exists() && outputFile.length() > 0L
    } catch (_: Exception) {
        outputFile.delete()
        false
    } finally {
        try { videoExtractor.release() } catch (_: Exception) {}
        try { audioExtractor.release() } catch (_: Exception) {}
        try { muxer?.stop() } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
    }
}

private fun muxVideoWithSourceAudioSegment(
    context: Context,
    videoFile: File,
    songPath: String,
    outputFile: File,
    clipStartUs: Long,
    clipEndUs: Long
): Boolean {
    if (clipEndUs <= clipStartUs) return false

    val videoExtractor = MediaExtractor()
    val audioExtractor = MediaExtractor()
    var muxer: MediaMuxer? = null

    return try {
        videoExtractor.setDataSource(videoFile.absolutePath)
        audioExtractor.setSongDataSource(context, songPath)

        val videoTrackSrc = (0 until videoExtractor.trackCount).firstOrNull { index ->
            videoExtractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()
                .startsWith("video/")
        } ?: return false
        val audioTrackSrc = (0 until audioExtractor.trackCount).firstOrNull { index ->
            audioExtractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()
                .startsWith("audio/")
        } ?: return false

        val videoFormat = videoExtractor.getTrackFormat(videoTrackSrc)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackSrc)

        val activeMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = activeMuxer
        val muxerVideoTrack = activeMuxer.addTrack(videoFormat)
        val muxerAudioTrack = activeMuxer.addTrack(audioFormat)
        activeMuxer.start()

        copySelectedTrack(videoExtractor, videoTrackSrc, activeMuxer, muxerVideoTrack)
        copySelectedTrackRange(
            extractor = audioExtractor,
            sourceTrackIndex = audioTrackSrc,
            muxer = activeMuxer,
            targetTrackIndex = muxerAudioTrack,
            startUs = clipStartUs,
            endUs = clipEndUs,
            presentationOffsetUs = clipStartUs
        )

        activeMuxer.stop()
        activeMuxer.release()
        muxer = null
        outputFile.exists() && outputFile.length() > 0L
    } catch (error: Exception) {
        Log.w(LYRIC_VIDEO_TAG, "Failed to mux lyric share source audio segment from $songPath", error)
        outputFile.delete()
        false
    } finally {
        try { videoExtractor.release() } catch (_: Exception) {}
        try { audioExtractor.release() } catch (_: Exception) {}
        try { muxer?.stop() } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
    }
}

private fun copySelectedTrack(
    extractor: MediaExtractor,
    sourceTrackIndex: Int,
    muxer: MediaMuxer,
    targetTrackIndex: Int,
    normalizeToZero: Boolean = false
) {
    extractor.selectTrack(sourceTrackIndex)
    val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
    val bufferInfo = MediaCodec.BufferInfo()
    var firstSampleTimeUs = Long.MIN_VALUE
    while (true) {
        buffer.clear()
        val sampleSize = extractor.readSampleData(buffer, 0)
        if (sampleSize < 0) break
        val sampleTimeUs = extractor.sampleTime
        if (firstSampleTimeUs == Long.MIN_VALUE) {
            firstSampleTimeUs = sampleTimeUs.coerceAtLeast(0L)
        }
        buffer.position(0)
        buffer.limit(sampleSize)
        bufferInfo.set(
            0,
            sampleSize,
            if (normalizeToZero) {
                (sampleTimeUs - firstSampleTimeUs).coerceAtLeast(0L)
            } else {
                sampleTimeUs
            },
            extractor.sampleFlags
        )
        muxer.writeSampleData(targetTrackIndex, buffer, bufferInfo)
        extractor.advance()
    }
    extractor.unselectTrack(sourceTrackIndex)
}

private fun copySelectedTrackRange(
    extractor: MediaExtractor,
    sourceTrackIndex: Int,
    muxer: MediaMuxer,
    targetTrackIndex: Int,
    startUs: Long,
    endUs: Long,
    presentationOffsetUs: Long
) {
    extractor.selectTrack(sourceTrackIndex)
    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
    val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
    val bufferInfo = MediaCodec.BufferInfo()
    while (true) {
        val sampleTimeUs = extractor.sampleTime
        if (sampleTimeUs < 0 || sampleTimeUs > endUs) break
        if (sampleTimeUs < startUs) {
            extractor.advance()
            continue
        }
        buffer.clear()
        val sampleSize = extractor.readSampleData(buffer, 0)
        if (sampleSize < 0) break
        buffer.position(0)
        buffer.limit(sampleSize)
        bufferInfo.set(
            0,
            sampleSize,
            (sampleTimeUs - presentationOffsetUs).coerceAtLeast(0L),
            extractor.sampleFlags
        )
        muxer.writeSampleData(targetTrackIndex, buffer, bufferInfo)
        extractor.advance()
    }
    extractor.unselectTrack(sourceTrackIndex)
}

private fun waitForEncoderInputBuffer(
    encoder: MediaCodec,
    bufferInfo: MediaCodec.BufferInfo,
    onFormatChanged: () -> Unit,
    onSampleData: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
): Int {
    while (true) {
        val inputIndex = encoder.dequeueInputBuffer(10_000)
        if (inputIndex >= 0) return inputIndex
        if (drainAudioEncoder(
                encoder = encoder,
                bufferInfo = bufferInfo,
                onFormatChanged = onFormatChanged,
                onSampleData = onSampleData
            )
        ) {
            return -1
        }
    }
}

private fun drainAudioEncoder(
    encoder: MediaCodec,
    bufferInfo: MediaCodec.BufferInfo,
    onFormatChanged: () -> Unit,
    onSampleData: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
): Boolean {
    while (true) {
        when (val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> return false
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormatChanged()
            else -> if (outputIndex >= 0) {
                val encodedBuffer = encoder.getOutputBuffer(outputIndex)
                if (encodedBuffer != null &&
                    bufferInfo.size > 0 &&
                    bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                ) {
                    encodedBuffer.position(bufferInfo.offset)
                    encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    onSampleData(encodedBuffer, bufferInfo)
                }
                val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                encoder.releaseOutputBuffer(outputIndex, false)
                if (isEos) return true
            }
        }
    }
}

private fun MediaExtractor.setSongDataSource(context: Context, songPath: String) {
    when {
        songPath.startsWith("content://", ignoreCase = true) -> {
            setDataSource(context, Uri.parse(songPath), null)
        }
        songPath.startsWith("file://", ignoreCase = true) -> {
            setDataSource(Uri.parse(songPath).path.orEmpty())
        }
        else -> {
            setDataSource(songPath)
        }
    }
}

private fun MediaFormat.optionalInteger(key: String): Int? =
    if (containsKey(key)) getInteger(key) else null

private fun MediaFormat.bytesPerSample(): Int =
    when (optionalInteger(MediaFormat.KEY_PCM_ENCODING)) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_FLOAT,
        AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        else -> 2
    }

private fun pcmBytesToDurationUs(
    bytes: Int,
    bytesPerFrame: Int,
    sampleRate: Int
): Long {
    if (bytes <= 0 || bytesPerFrame <= 0 || sampleRate <= 0) return 0L
    val frameCount = bytes.toLong() / bytesPerFrame.toLong()
    return frameCount * 1_000_000L / sampleRate.toLong()
}

internal fun shareLyricVideoFile(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(
            context.contentResolver,
            "${context.getString(R.string.app_name)} Lyric Video",
            uri
        )
    }
    context.startActivity(
        Intent.createChooser(shareIntent, context.getString(R.string.lyric_video_share_chooser_title))
    )
}
