package com.ella.music.data.ai

import android.content.Context
import com.ella.music.R
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class OpenAiLibraryChatInput(
    val songs: List<Song>,
    val playbackStats: List<SongPlaybackStats>,
    val playbackHistory: List<PlaybackHistoryEntry>,
    val userMessage: String,
    val maxPlayableItems: Int = 30,
    val conversationHistory: List<Pair<String, String>> = emptyList()
)

data class OpenAiLibraryChatResponse(
    val answer: String,
    val songKeys: List<String>,
    val playlistName: String
)

class OpenAiLibraryChatAssistant(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("OpenAILibraryChat"))
        .build()
) {
    suspend fun chat(
        config: OpenAiSongInterpretationConfig,
        input: OpenAiLibraryChatInput
    ): OpenAiLibraryChatResponse {
        if (input.userMessage.isBlank()) error(context.getString(R.string.error_ai_chat_empty_message))
        if (input.songs.isEmpty()) error(context.getString(R.string.error_library_empty))
        val text = AiProviderClient(context, client).completeChat(
            config = config,
            systemPrompt = buildSystemPrompt(),
            userPrompt = buildPrompt(input),
            conversationHistory = input.conversationHistory.takeLast(20),
            temperature = 0.68,
            topP = 0.9,
            maxTokens = 1600
        )
        return parseChatResponse(text, input.songs)
    }

    private fun buildSystemPrompt(): String =
        "你叫 Ella，是 Halcyon 本地音乐播放器里的 AI 听歌助手。你只能基于用户提供的本地曲库、最近播放和播放统计回答，" +
            "可以推荐或解释本地歌曲，但不能请求删除、移动、重命名、修改文件，也不能编造曲库之外的歌曲。"

    private fun buildPrompt(input: OpenAiLibraryChatInput): String {
        val songIds = input.songs.mapIndexed { index, song -> song.id to song.toCandidateId(index) }.toMap()
        val candidates = JSONArray()
        input.songs.forEachIndexed { index, song ->
            candidates.put(
                JSONObject()
                    .put("id", song.toCandidateId(index))
                    .put("title", song.title.toPromptText(80))
                    .put("artist", song.artist.toPromptText(80))
                    .put("album", song.album.toPromptText(80))
                    .put("genre", song.genre.toPromptText(60))
                    .put("year", song.year.toPromptText(20))
                    .put("duration", song.durationText)
            )
        }

        val recent = JSONArray()
        input.playbackHistory
            .mapNotNull { entry -> songIds[entry.songId] }
            .take(50)
            .forEachIndexed { index, id -> recent.put(JSONObject().put("id", id).put("rank", index + 1)) }

        val stats = JSONArray()
        input.playbackStats
            .mapNotNull { stat -> songIds[stat.songId]?.let { it to stat } }
            .sortedWith(
                compareByDescending<Pair<String, SongPlaybackStats>> { it.second.listenedMs }
                    .thenByDescending { it.second.playCount }
                    .thenByDescending { it.second.lastPlayedAt }
            )
            .take(60)
            .forEach { (id, stat) ->
                stats.put(
                    JSONObject()
                        .put("id", id)
                        .put("playCount", stat.playCount)
                        .put("listenedMinutes", stat.listenedMs / 60_000L)
                )
            }

        return buildString {
            appendLine("用户问题：${input.userMessage.trim()}")
            appendLine()
            appendLine("请只返回一个 JSON 对象，不要输出 Markdown。")
            appendLine("JSON 格式：{\"answer\":\"中文回答\",\"playlistName\":\"歌单名称\",\"songIds\":[\"song_1\",\"song_2\"]}")
            appendLine("answer 字段可以使用轻量 Markdown，例如 ## 标题、- 列表、**重点**、`标签`；不要在 JSON 外层使用 Markdown 代码块。")
            appendLine("playlistName 是根据用户问题和推荐内容生成的简短歌单名称（2-8个字），例如'日语流行'、'深夜放松'、'跑步动感'等。如果不需要推荐歌曲则返回空字符串。")
            appendLine("如果推荐了歌曲，请在 answer 里简短说明推荐理由，真正可播放的歌曲必须放进 songIds。")
            appendLine("如果不需要播放或推荐歌曲，songIds 返回空数组。")
            appendLine("songIds 最多 ${input.maxPlayableItems.coerceIn(1, 50)} 首，且必须来自候选歌曲 id。")
            appendLine()
            appendLine("候选歌曲：")
            appendLine(candidates.toString())
            appendLine()
            appendLine("最近播放：")
            appendLine(recent.toString())
            appendLine()
            appendLine("播放统计：")
            appendLine(stats.toString())
        }
    }

    private fun parseChatResponse(text: String, songs: List<Song>): OpenAiLibraryChatResponse {
        if (text.isBlank()) error(context.getString(R.string.error_openai_empty_response))
        val root = JSONObject(text.toJsonObjectText())
        val ids = root.optJSONArray("songIds") ?: root.optJSONArray("songs") ?: JSONArray()
        val songsByCandidateId = songs.mapIndexed { index, song -> song.toCandidateId(index) to song }.toMap()
        val songKeys = mutableListOf<String>()
        for (i in 0 until ids.length()) {
            val id = when (val item = ids.opt(i)) {
                is JSONObject -> item.optString("id")
                else -> item?.toString().orEmpty()
            }
            val song = songsByCandidateId[id] ?: continue
            songKeys += song.playlistIdentityKey()
        }
        return OpenAiLibraryChatResponse(
            answer = root.optString("answer").ifBlank { text.trim() },
            songKeys = songKeys.distinct(),
            playlistName = root.optString("playlistName").trim()
        )
    }

    private fun String.toJsonObjectText(): String {
        val trimmed = trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return trimmed
    }

    private fun Song.toCandidateId(index: Int): String = "song_${index + 1}"

    private fun String.toPromptText(maxLength: Int): String =
        trim().replace(Regex("\\s+"), " ").let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
}
