package com.ella.music.video

import java.net.URI

internal data class VideoPlaylistEntry(val source: String, val title: String)
internal data class ParsedVideoPlaylist(val hls: Boolean, val entries: List<VideoPlaylistEntry>,
    val durationMs: Long, val ended: Boolean, val master: Boolean)

internal fun parseVideoPlaylist(text: String, source: String): ParsedVideoPlaylist {
    val lines=text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val hls=lines.any { it.startsWith("#EXT-X-",true) }
    val base=URI(source)
    var title="";var duration=0.0
    val entries=mutableListOf<VideoPlaylistEntry>()
    lines.forEach { line ->
        if(line.startsWith("#EXTINF:",true)) {
            val info=line.substringAfter(':');title=info.substringAfter(',',"").trim()
            duration+=info.substringBefore(',').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        } else if(!line.startsWith('#')) {
            val candidate=runCatching { base.resolve(line).toString() }.getOrNull()
            if(candidate!=null && isVideoSource(candidate) &&
                (base.scheme !in setOf("http","https") || URI(candidate).scheme in setOf("http","https"))) {
                entries+=VideoPlaylistEntry(candidate,title.ifBlank { URI(candidate).path?.substringAfterLast('/').orEmpty().ifBlank { "Video" } })
            }
            title=""
        }
    }
    return ParsedVideoPlaylist(hls,entries.distinctBy { it.source },(duration*1000).toLong(),
        lines.any { it.equals("#EXT-X-ENDLIST",true) },lines.any { it.startsWith("#EXT-X-STREAM-INF:",true) })
}

internal fun isVideoSource(source: String): Boolean = runCatching {
    val uri=URI(source.trim().replace(" ", "%20"))
    uri.scheme?.lowercase() in setOf("http","https","content","file") &&
        (uri.scheme !in setOf("http","https") || !uri.host.isNullOrBlank())
}.getOrDefault(false)

internal fun isPlaylistContentType(type: String?): Boolean = type?.substringBefore(';')?.trim()?.lowercase() in
    setOf("application/vnd.apple.mpegurl","application/x-mpegurl","audio/mpegurl","audio/x-mpegurl")

internal fun safeVideoName(title: String, extension: String): String = title.substringBeforeLast('.',title)
    .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"),"_").trim().take(100).ifBlank { "Video" } + "." + extension
