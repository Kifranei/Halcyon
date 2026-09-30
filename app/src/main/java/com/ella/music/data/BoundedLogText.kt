package com.ella.music.data

import java.io.Reader

internal fun Reader.readBoundedLogText(maxChars: Int): String {
    require(maxChars >= 0)
    val output = StringBuilder(minOf(maxChars, 8192))
    val chunk = CharArray(4096)
    while (output.length < maxChars) {
        val count = read(chunk, 0, minOf(chunk.size, maxChars - output.length))
        if (count < 0) break
        output.append(chunk, 0, count)
    }
    if (output.length == maxChars && read() >= 0) output.append("\n[日志导出达到内存安全上限]\n")
    return output.toString()
}
