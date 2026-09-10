package com.ella.music.data.ai

import android.content.Context
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class OpenAiSongInterpretationConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val protocol: AiApiProtocol = AiApiProtocol.Compatible
)

data class OpenAiSongInterpretationInput(
    val song: Song,
    val tagInfo: SongTagInfo,
    val audioInfo: AudioInfo?,
    val audioInfoText: String,
    val lyrics: List<LyricLine>
)

class OpenAiSongInterpreter(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("OpenAISongInterpretation"))
        .build()
) {
    suspend fun interpret(
        config: OpenAiSongInterpretationConfig,
        input: OpenAiSongInterpretationInput
    ): String {
        return AiProviderClient(context, client).completeChat(
            config = config,
            systemPrompt = buildSystemPrompt(),
            userPrompt = buildPrompt(input),
            temperature = 0.72,
            topP = 0.92,
            maxTokens = 1400
        )
    }

    private fun buildSystemPrompt(): String =
        "你是一个认真但不装腔的音乐评论助手。请用中文解读歌曲，尊重用户提供的歌词和标签信息，" +
            "不要编造确定的创作背景；如果只是推测，请明确说是推测。"

    private fun buildPrompt(input: OpenAiSongInterpretationInput): String {
        val song = input.song
        val tag = input.tagInfo
        val metadata = JSONObject()
            .put("标题", tag.title.ifBlank { song.title })
            .put("艺术家", tag.artist.ifBlank { song.artist })
            .put("专辑", tag.album.ifBlank { song.album })
            .put("专辑艺术家", tag.albumArtist.ifBlank { song.albumArtist })
            .put("流派", tag.genre.ifBlank { song.genre })
            .put("年份", tag.year.ifBlank { song.year })
            .put("作曲", tag.composer.ifBlank { song.composer })
            .put("编曲", tag.arranger.ifBlank { song.arranger })
            .put("作词", tag.lyricist.ifBlank { song.lyricist })
            .put("注释", tag.displayComment)
            .put("时长", song.durationText)
            .put("音频信息", input.audioInfoText)
            .put("文件名", song.fileName.ifBlank { song.path.substringAfterLast('/') })

        val lyricsText = input.lyrics.toPromptLyrics()
        return buildString {
            appendLine("请根据下面的歌曲信息和歌词做一次 AI 解读。")
            appendLine()
            appendLine("要求：")
            appendLine("1. 分成「整体印象」「歌词与意象」「情绪/声音」「可能的聆听角度」「一句话总结」。")
            appendLine("2. 如果歌词为空，请主要基于歌曲标签做谨慎解读。")
            appendLine("3. 如果歌词有翻译、和声或背景人声，也一起考虑。")
            appendLine("4. 不要把文件名、码率等技术信息硬解释成创作意图。")
            appendLine()
            appendLine("歌曲信息：")
            appendLine(metadata.toString(2))
            appendLine()
            appendLine("歌词：")
            appendLine(lyricsText.ifBlank { "（未找到歌词）" })
        }
    }

    private fun List<LyricLine>.toPromptLyrics(): String {
        val raw = take(180).joinToString("\n") { line ->
            val parts = buildList {
                add(line.timeMs.toMinuteSecond())
                if (line.text.isNotBlank()) add(line.text)
                if (!line.translation.isNullOrBlank()) add("译：${line.translation}")
                if (!line.pronunciation.isNullOrBlank()) add("音：${line.pronunciation}")
                if (!line.backgroundText.isNullOrBlank()) add("和声：${line.backgroundText}")
                if (!line.backgroundTranslation.isNullOrBlank()) add("和声译：${line.backgroundTranslation}")
            }
            parts.joinToString(" | ")
        }
        return if (raw.length <= 12_000) raw else raw.take(12_000) + "\n（歌词过长，已截断）"
    }

    private fun Long.toMinuteSecond(): String {
        val totalSeconds = (this / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
