package com.ella.music.ui.ai

import android.content.Context
import com.ella.music.R
import com.ella.music.data.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal const val MAX_SESSIONS = 20
private const val MAX_MESSAGES_PER_SESSION = 100

internal data class AiChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val messages: List<AiChatMessage>
)

internal data class AiChatSessionMeta(val id: String, val title: String, val createdAt: Long)

private fun sessionsDir(context: Context): File =
    File(context.filesDir, "ai_chat_sessions").also { it.mkdirs() }

internal fun defaultSessionTitle(context: Context): String =
    context.getString(R.string.ai_chat_new_session)

internal fun String.isDefaultAiChatSessionTitle(): Boolean =
    trim() in setOf("New chat", "New Chat", "新对话", "新對話", "新しい会話")

internal fun loadSessionIndex(context: Context): List<AiChatSessionMeta> {
    return runCatching {
        val file = File(sessionsDir(context), "index.json")
        if (!file.exists()) return@runCatching emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val rawTitle = obj.getString("title")
            AiChatSessionMeta(
                id = obj.getString("id"),
                title = if (rawTitle.isDefaultAiChatSessionTitle()) defaultSessionTitle(context) else rawTitle,
                createdAt = obj.getLong("createdAt")
            )
        }
    }.getOrDefault(emptyList())
}

internal fun saveSessionIndex(context: Context, index: List<AiChatSessionMeta>) {
    runCatching {
        val array = JSONArray()
        index.forEach { meta ->
            array.put(JSONObject().put("id", meta.id).put("title", meta.title).put("createdAt", meta.createdAt))
        }
        File(sessionsDir(context), "index.json").writeText(array.toString())
    }
}

internal fun loadSessionMessages(context: Context, sessionId: String): List<AiChatMessage> {
    return runCatching {
        val file = File(sessionsDir(context), "$sessionId.json")
        if (!file.exists()) return@runCatching emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            AiChatMessage(
                role = if (obj.getString("role") == "user") AiChatRole.User else AiChatRole.Assistant,
                text = obj.getString("text"),
                songs = obj.optJSONArray("songs")?.toSongList().orEmpty(),
                loading = false,
                playlistName = obj.optString("playlistName")
            )
        }
    }.getOrDefault(emptyList())
}

internal fun saveSessionMessages(context: Context, sessionId: String, messages: List<AiChatMessage>) {
    runCatching {
        val array = JSONArray()
        messages.filter { !it.loading }.takeLast(MAX_MESSAGES_PER_SESSION).forEach { msg ->
            array.put(
                JSONObject()
                    .put("role", if (msg.role == AiChatRole.User) "user" else "assistant")
                    .put("text", msg.text)
                    .put("playlistName", msg.playlistName)
                    .put("songs", JSONArray().apply {
                        msg.songs.forEach { put(it.toJson()) }
                    })
            )
        }
        File(sessionsDir(context), "$sessionId.json").writeText(array.toString())
    }
}

private fun JSONArray.toSongList(): List<Song> =
    (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.toSong()
    }

private fun Song.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("albumId", albumId)
        .put("duration", duration)
        .put("path", path)
        .put("fileName", fileName)
        .put("fileSize", fileSize)
        .put("mimeType", mimeType)
        .put("dateAdded", dateAdded)
        .put("dateModified", dateModified)
        .put("trackNumber", trackNumber)
        .put("discNumber", discNumber)
        .put("albumArtist", albumArtist)
        .put("genre", genre)
        .put("year", year)
        .put("composer", composer)
        .put("arranger", arranger)
        .put("lyricist", lyricist)
        .put("coverUrl", coverUrl)
        .put("onlineSource", onlineSource)
        .put("onlineId", onlineId)
    .put("onlineMvId", onlineMvId)
        .put("onlineLyrics", onlineLyrics)
        .put("onlineLyricTranslation", onlineLyricTranslation)
        .put("onlineLyricPronunciation", onlineLyricPronunciation)

private fun JSONObject.toSong(): Song =
    Song(
        id = optLong("id"),
        title = optString("title"),
        artist = optString("artist"),
        album = optString("album"),
        albumId = optLong("albumId"),
        duration = optLong("duration"),
        path = optString("path"),
        fileName = optString("fileName"),
        fileSize = optLong("fileSize"),
        mimeType = optString("mimeType"),
        dateAdded = optLong("dateAdded"),
        dateModified = optLong("dateModified"),
        trackNumber = optInt("trackNumber"),
        discNumber = optInt("discNumber"),
        albumArtist = optString("albumArtist"),
        genre = optString("genre"),
        year = optString("year"),
        composer = optString("composer"),
        arranger = optString("arranger"),
        lyricist = optString("lyricist"),
        coverUrl = optString("coverUrl"),
        onlineSource = optString("onlineSource"),
        onlineId = optString("onlineId"),
        onlineMvId = optString("onlineMvId"),
        onlineLyrics = optString("onlineLyrics"),
        onlineLyricTranslation = optString("onlineLyricTranslation"),
        onlineLyricPronunciation = optString("onlineLyricPronunciation")
    )

internal fun deleteSession(context: Context, sessionId: String) {
    runCatching {
        File(sessionsDir(context), "$sessionId.json").delete()
    }
}

internal enum class AiChatRole { User, Assistant }

internal data class AiChatMessage(
    val role: AiChatRole,
    val text: String,
    val songs: List<Song>,
    val loading: Boolean = false,
    val playlistName: String = ""
)
