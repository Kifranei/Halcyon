package com.ella.music.data.netease

import android.content.Context
import com.ella.music.R
import com.ella.music.data.LxSourceConfig
import com.ella.music.data.MusicFreePluginConfig
import com.ella.music.data.SettingsManager
import com.ella.music.data.lx.LxOnlineService
import com.ella.music.data.lx.LxOnlineSong
import com.ella.music.data.model.Song
import com.ella.music.data.musicfree.MusicFreeOnlineSong
import com.ella.music.data.musicfree.MusicFreePluginService
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.Locale

enum class NeteasePlaybackProvider(val id: String, val titleRes: Int) {
    Official("official", R.string.netease_playback_official),
    Lx("lx", R.string.netease_playback_lx),
    MusicFree("musicfree", R.string.netease_playback_musicfree);

    companion object {
        fun fromId(id: String?): NeteasePlaybackProvider = entries.firstOrNull { it.id == id } ?: Official
    }
}

/** Resolves transport without replacing account metadata, identity, artwork, or lyrics. */
internal class NeteasePlaybackResolver(
    private val provider: suspend () -> NeteasePlaybackProvider,
    private val lxSource: suspend () -> LxSourceConfig?,
    private val musicFreeSource: suspend () -> MusicFreePluginConfig?,
    private val resolveLx: suspend (Song, LxSourceConfig) -> Song,
    private val resolveMusicFree: suspend (Song, MusicFreePluginConfig) -> Song,
    private val message: (Int) -> String,
    private val onResolved: (Song) -> Unit = {}
) {
    constructor(context: Context) : this(
        provider = {
            NeteasePlaybackProvider.fromId(SettingsManager.getInstance(context).neteasePlaybackProvider.first()).also {
                NeteaseLibraryStore.getInstance(context).setPlaybackProvider(it)
            }
        },
        lxSource = { SettingsManager.getInstance(context).selectedLxSource.first() },
        musicFreeSource = {
            val settings = SettingsManager.getInstance(context)
            val plugins = settings.musicFreePlugins.first()
            val selected = settings.selectedMusicFreePluginId.first()
            plugins.firstOrNull { it.id == selected } ?: plugins.firstOrNull()
        },
        resolveLx = { song, source ->
            LxOnlineService(context).resolvePlayableSong(song.toNeteaseLxItem(), source.script)
        },
        resolveMusicFree = { song, source -> MusicFreePluginService(context).resolveNeteaseSong(song, source) },
        message = { context.getString(it) },
        onResolved = { NeteaseLibraryStore.getInstance(context).rememberPluginPlayback(it.onlineId, it.mimeType) }
    )

    /** null means the existing official stable-URI/cache path; plugin errors are never caught. */
    suspend fun resolve(song: Song): Song? {
        val resolved = when (provider()) {
            NeteasePlaybackProvider.Official -> return null
            NeteasePlaybackProvider.Lx -> {
                val source = lxSource()?.takeIf { it.script.isNotBlank() }
                    ?: throw IOException(message(R.string.netease_playback_lx_missing))
                resolveLx(song, source)
            }
            NeteasePlaybackProvider.MusicFree -> {
                val source = musicFreeSource()?.takeIf { it.script.isNotBlank() }
                    ?: throw IOException(message(R.string.netease_playback_musicfree_missing))
                resolveMusicFree(song, source)
            }
        }
        if (!isNeteasePluginStreamUrl(resolved.path)) {
            throw IOException(message(R.string.netease_playback_invalid_url))
        }
        return song.copy(path = resolved.path, fileName = resolved.fileName, mimeType = resolved.mimeType).also(onResolved)
    }
}

internal fun isNeteasePluginStreamUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme?.lowercase(Locale.ROOT) in setOf("https", "http") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

internal fun Song.toNeteaseLxItem(): LxOnlineSong = LxOnlineSong(
    song = copy(path = "", onlineLyrics = "", onlineLyricTranslation = "", onlineLyricPronunciation = ""),
    source = "wy", songmid = onlineId, quality = "128k", coverUrl = coverUrl,
    sourceMetadata = mapOf("albumId" to albumId.toString(), "albumName" to album,
        "singer" to artist, "duration" to (duration / 1000).toString())
)

internal fun Song.toNeteaseMusicFreeItem(platform: String): MusicFreeOnlineSong = MusicFreeOnlineSong(
    song = copy(path = "", onlineLyrics = "", onlineLyricTranslation = "", onlineLyricPronunciation = ""),
    pluginName = platform,
    rawJson = JSONObject().put("id", onlineId).put("platform", platform)
        .put("title", title).put("artist", artist).put("album", album).put("artwork", coverUrl)
        .put("duration", duration / 1000.0).toString(),
    coverUrl = coverUrl
)

/** Only matched metadata is used for other platforms; an arbitrary same-title cover is not enough. */
internal fun Song.matchesNeteasePluginTrack(candidate: Song): Boolean {
    fun normalize(value: String) = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    fun artists(value: String) = value.split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
        .map(::normalize).filter(String::isNotBlank).toSet()
    if (normalize(title).isBlank() || normalize(title) != normalize(candidate.title)) return false
    val owners = artists(artist)
    if (owners.isEmpty() || owners.intersect(artists(candidate.artist)).isEmpty()) return false
    if (duration > 0 && candidate.duration > 0 && kotlin.math.abs(duration - candidate.duration) > 10_000L) return false
    return true
}
