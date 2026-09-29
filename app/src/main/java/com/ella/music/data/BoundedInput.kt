package com.ella.music.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

internal class InputTooLargeException(maxBytes: Long) : IOException("Input exceeds $maxBytes bytes")

/** Copies at most [maxBytes], probing one extra byte so oversized streams are never silently truncated. */
internal fun InputStream.copyToBoundedOrThrow(output: OutputStream, maxBytes: Long): Long {
    require(maxBytes >= 0L)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val remaining = maxBytes - total
        val requested = minOf(buffer.size.toLong(), remaining + 1L).toInt()
        val count = read(buffer, 0, requested)
        if (count < 0) break
        if (count == 0) continue
        if (count.toLong() > remaining) throw InputTooLargeException(maxBytes)
        output.write(buffer, 0, count)
        total += count
    }
    output.flush()
    return total
}

internal fun InputStream.readUtf8Bounded(maxBytes: Long): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, 64L * 1024L).toInt())
    copyToBoundedOrThrow(output, maxBytes)
    return String(output.toByteArray(), StandardCharsets.UTF_8)
}
