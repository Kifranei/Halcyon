package com.ella.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.ella.music.R
import com.ella.music.data.SettingsManager.Companion.DEFAULT_LYRIC_SOURCE_PRIORITY
import com.ella.music.data.SettingsManager.Companion.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_DEFAULT_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MAX_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MIN_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_DEFAULT_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MAX_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MIN_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_FONT_SCALE_MIN
import com.ella.music.data.SettingsManager.Companion.LYRIC_FONT_SCALE_ULTRA_WIDE_MAX
import com.ella.music.data.SettingsManager.Companion.LYRIC_PARSER_ENGINE_AUTO
import com.ella.music.data.SettingsManager.Companion.LYRIC_PARSER_ENGINE_ELLA
import com.ella.music.data.SettingsManager.Companion.LYRIC_SECONDARY_FONT_SCALE_MIN
import com.ella.music.data.SettingsManager.Companion.LYRIC_SECONDARY_FONT_SCALE_ULTRA_WIDE_MAX
import com.ella.music.data.SettingsManager.Companion.LYRIC_SOURCE_AUTO
import com.ella.music.data.SettingsManager.Companion.LYRIC_SOURCE_EMBEDDED
import com.ella.music.data.SettingsManager.Companion.LYRIC_WIDE_PRIMARY_TEXT_SIZE_DEFAULT_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_WIDE_PRIMARY_TEXT_SIZE_MAX_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_WIDE_PRIMARY_TEXT_SIZE_MIN_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_WIDE_SECONDARY_TEXT_SIZE_DEFAULT_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_WIDE_SECONDARY_TEXT_SIZE_MAX_SP
import com.ella.music.data.SettingsManager.Companion.LYRIC_WIDE_SECONDARY_TEXT_SIZE_MIN_SP
import com.ella.music.data.SettingsManager.Companion.normalizeLyricSourcePriority
import com.ella.music.data.SettingsManager.Companion.PLAYER_LYRIC_ALIGN_LEFT
import com.ella.music.data.SettingsManager.Companion.KEY_APPLE_MUSIC_LYRICS_WORD_LIFT
import com.ella.music.data.SettingsManager.Companion.KEY_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS
import com.ella.music.data.SettingsManager.Companion.KEY_GLOBAL_CJK_FONT_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_GLOBAL_CJK_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_GLOBAL_WESTERN_FONT_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_GLOBAL_WESTERN_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_IGNORE_LYRIC_HEADER_TAGS
import com.ella.music.data.SettingsManager.Companion.KEY_HIDE_LYRIC_EXTRA_INFO
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_CJK_FONT_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_CJK_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_COMPACT_PRIMARY_TEXT_SIZE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_COMPACT_SECONDARY_TEXT_SIZE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_FONT_APPLY_TO_DESKTOP
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_FONT_APPLY_TO_PAGE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_FONT_ITALIC
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_FONT_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_FONT_SCALE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_FONT_WEIGHT
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_LINE_BLACKLIST
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_OFFSET_OVERRIDES
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_ORIGINAL_CJK_FONT_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_ORIGINAL_CJK_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_ORIGINAL_WESTERN_FONT_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_ORIGINAL_WESTERN_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_PAGE_KEEP_SCREEN_ON
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_PAGE_TRANSLATION
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_PARSER_ENGINE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_PERSPECTIVE_EFFECT
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_PERSPECTIVE_Y_ANGLE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_PRONUNCIATION_BELOW
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_SECONDARY_FONT_SCALE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_SHARE_CUSTOM_INFO
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_SHARE_USE_LYRIC_FONT
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_SOURCE_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_SOURCE_PRIORITY
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_TRANSLATION_CJK_FONT_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_TRANSLATION_CJK_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_TRANSLATION_WESTERN_FONT_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_TRANSLATION_WESTERN_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_WESTERN_FONT_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_WESTERN_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_WIDE_PRIMARY_TEXT_SIZE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_WIDE_SECONDARY_TEXT_SIZE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRICO_PLUGIN_ENABLED_IDS
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_LYRIC_TEXT_ALIGN
import com.ella.music.plugin.source.LyricoPluginManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Lyric sourcing, parsing and typography: source mode/priority, Lyrico plugins, blacklist and
 * per-song offsets, page behaviour, fonts (lyric/global/original/translation), sizes and perspective.
 *
 * Extracted verbatim from [SettingsManager], which implements this interface via class
 * delegation so every call site keeps using settingsManager.<member> unchanged. All flow
 * properties MUST stay eagerly-initialised stored properties (never computed get() =):
 * Compose collectAsState keys on the flow instance, and a fresh instance per access would
 * restart collection on every recomposition.
 */
interface LyricSettingsAccess {
    val lyricSourceMode: Flow<Int>
    val lyricSourcePriority: Flow<String>
    val lyricoPluginEnabledIds: Flow<Set<String>>
    val ignoreLyricHeaderTags: Flow<Boolean>
    val hideLyricExtraInfo: Flow<Boolean>
    val lyricLineBlacklist: Flow<List<String>>
    val lyricOffsetOverrides: Flow<Map<String, Long>>
    val playerLyricTextAlign: Flow<Int>
    val lyricPronunciationBelow: Flow<Boolean>
    val lyricPageTranslation: Flow<Boolean>
    val lyricPageKeepScreenOn: Flow<Boolean>
    val appleMusicLyricsWordLift: Flow<Boolean>
    val appleMusicLyricsSustainThresholdMs: Flow<Int>
    val lyricParserEngine: Flow<Int>
    val lyricShareCustomInfo: Flow<String>
    val lyricFontName: Flow<String>
    val lyricFontPath: Flow<String>
    val lyricWesternFontName: Flow<String>
    val lyricWesternFontPath: Flow<String>
    val lyricCjkFontName: Flow<String>
    val lyricCjkFontPath: Flow<String>
    val globalWesternFontName: Flow<String>
    val globalWesternFontPath: Flow<String>
    val globalCjkFontName: Flow<String>
    val globalCjkFontPath: Flow<String>
    val lyricOriginalWesternFontName: Flow<String>
    val lyricOriginalWesternFontPath: Flow<String>
    val lyricOriginalCjkFontName: Flow<String>
    val lyricOriginalCjkFontPath: Flow<String>
    val lyricTranslationWesternFontName: Flow<String>
    val lyricTranslationWesternFontPath: Flow<String>
    val lyricTranslationCjkFontName: Flow<String>
    val lyricTranslationCjkFontPath: Flow<String>
    val lyricFontWeight: Flow<Int>
    val lyricFontScale: Flow<Int>
    val lyricSecondaryFontScale: Flow<Int>
    val lyricCompactPrimaryTextSize: Flow<Int>
    val lyricCompactSecondaryTextSize: Flow<Int>
    val lyricWidePrimaryTextSize: Flow<Int>
    val lyricWideSecondaryTextSize: Flow<Int>
    val lyricFontItalic: Flow<Boolean>
    val lyricFontApplyToPage: Flow<Boolean>
    val lyricFontApplyToDesktop: Flow<Boolean>
    val lyricShareUseLyricFont: Flow<Boolean>
    val lyricPerspectiveEffect: Flow<Boolean>
    val lyricPerspectiveYAngle: Flow<Int>
    suspend fun setPlayerLyricTextAlign(align: Int)
    suspend fun setLyricPronunciationBelow(below: Boolean)
    suspend fun setLyricLineBlacklist(lines: List<String>)
    suspend fun setIgnoreLyricHeaderTags(enabled: Boolean)
    suspend fun setHideLyricExtraInfo(enabled: Boolean)
    suspend fun setLyricSourceMode(mode: Int)
    suspend fun setLyricSourcePriority(priority: String)
    suspend fun setLyricoPluginEnabled(id: String, enabled: Boolean)
    suspend fun setLyricOffsetOverride(songKey: String, offsetMs: Long)
    suspend fun setLyricPageTranslation(enabled: Boolean)
    suspend fun setLyricPageKeepScreenOn(enabled: Boolean)
    suspend fun setAppleMusicLyricsWordLift(enabled: Boolean)
    suspend fun setAppleMusicLyricsSustainThresholdMs(thresholdMs: Int)
    suspend fun setLyricPerspectiveEffect(enabled: Boolean)
    suspend fun setLyricPerspectiveYAngle(angle: Int)
    suspend fun setLyricShareCustomInfo(info: String)
    suspend fun setLyricShareUseLyricFont(enabled: Boolean)
    suspend fun setLyricFont(name: String, path: String)
    suspend fun clearLyricFont()
    suspend fun setLyricWesternFont(name: String, path: String)
    suspend fun clearLyricWesternFont()
    suspend fun setLyricCjkFont(name: String, path: String)
    suspend fun clearLyricCjkFont()
    suspend fun setGlobalFont(westernName: String, westernPath: String, cjkName: String, cjkPath: String)
    suspend fun setLyricOriginalFont(westernName: String, westernPath: String, cjkName: String, cjkPath: String)
    suspend fun setLyricTranslationFont(westernName: String, westernPath: String, cjkName: String, cjkPath: String)
    suspend fun setLyricFontWeight(weight: Int)
    suspend fun setLyricFontScale(scale: Int)
    suspend fun setLyricSecondaryFontScale(scale: Int)
    suspend fun setLyricCompactPrimaryTextSize(sizeSp: Int)
    suspend fun setLyricCompactSecondaryTextSize(sizeSp: Int)
    suspend fun setLyricWidePrimaryTextSize(sizeSp: Int)
    suspend fun setLyricWideSecondaryTextSize(sizeSp: Int)
    suspend fun setLyricFontItalic(enabled: Boolean)
    suspend fun setLyricFontApplyToPage(enabled: Boolean)
    suspend fun setLyricFontApplyToDesktop(enabled: Boolean)
    suspend fun setLyricParserEngine(engine: Int)
}

internal class LyricSettingsAccessImpl(private val context: Context) : LyricSettingsAccess {

    override val lyricSourceMode: Flow<Int> =
        context.dataStore.data.map { it[KEY_LYRIC_SOURCE_MODE] ?: LYRIC_SOURCE_AUTO }
    override val lyricSourcePriority: Flow<String> =
        context.dataStore.data.map {
            normalizeLyricSourcePriority(it[KEY_LYRIC_SOURCE_PRIORITY] ?: DEFAULT_LYRIC_SOURCE_PRIORITY)
        }
    override val lyricoPluginEnabledIds: Flow<Set<String>> =
        context.dataStore.data.map { LyricoPluginManager.normalizeEnabledIds(it[KEY_LYRICO_PLUGIN_ENABLED_IDS]) }
    override val ignoreLyricHeaderTags: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_IGNORE_LYRIC_HEADER_TAGS] ?: true }
    override val hideLyricExtraInfo: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HIDE_LYRIC_EXTRA_INFO] ?: true }
    override val lyricLineBlacklist: Flow<List<String>> =
        context.dataStore.data.map { parseLyricLineBlacklist(it[KEY_LYRIC_LINE_BLACKLIST]) }
    override val lyricOffsetOverrides: Flow<Map<String, Long>> =
        context.dataStore.data.map { parseLyricOffsetOverrides(it[KEY_LYRIC_OFFSET_OVERRIDES]) }
    override val playerLyricTextAlign: Flow<Int> =
        context.dataStore.data.map { (it[KEY_PLAYER_LYRIC_TEXT_ALIGN] ?: PLAYER_LYRIC_ALIGN_LEFT).coerceIn(0, 2) }
    // Whether romaji / phonetic guides render BELOW the main lyric line (main → romaji → translation)
    // instead of above it. Default false = above.
    override val lyricPronunciationBelow: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LYRIC_PRONUNCIATION_BELOW] ?: false }
    override val lyricPageTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_PAGE_TRANSLATION] ?: true }
    override val lyricPageKeepScreenOn: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LYRIC_PAGE_KEEP_SCREEN_ON] ?: false }
    override val appleMusicLyricsWordLift: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_APPLE_MUSIC_LYRICS_WORD_LIFT] ?: true }
    override val appleMusicLyricsSustainThresholdMs: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS]
                ?: SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS)
                .coerceIn(
                    SettingsManager.MIN_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS,
                    SettingsManager.MAX_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS
                )
        }

    override val lyricParserEngine: Flow<Int> =
        context.dataStore.data.map { it[KEY_LYRIC_PARSER_ENGINE] ?: LYRIC_PARSER_ENGINE_ELLA }

    override val lyricShareCustomInfo: Flow<String> =
        context.dataStore.data.map { it[KEY_LYRIC_SHARE_CUSTOM_INFO] ?: "" }

    override val lyricFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_FONT_NAME] ?: "" }
    override val lyricFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_FONT_PATH] ?: "" }
    override val lyricWesternFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_WESTERN_FONT_NAME] ?: "" }
    override val lyricWesternFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_WESTERN_FONT_PATH] ?: "" }
    override val lyricCjkFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_CJK_FONT_NAME] ?: "" }
    override val lyricCjkFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_CJK_FONT_PATH] ?: "" }
    override val globalWesternFontName: Flow<String> = context.dataStore.data.map { it[KEY_GLOBAL_WESTERN_FONT_NAME] ?: "" }
    override val globalWesternFontPath: Flow<String> = context.dataStore.data.map { it[KEY_GLOBAL_WESTERN_FONT_PATH] ?: "" }
    override val globalCjkFontName: Flow<String> = context.dataStore.data.map { it[KEY_GLOBAL_CJK_FONT_NAME] ?: "" }
    override val globalCjkFontPath: Flow<String> = context.dataStore.data.map { it[KEY_GLOBAL_CJK_FONT_PATH] ?: "" }
    override val lyricOriginalWesternFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_ORIGINAL_WESTERN_FONT_NAME] ?: "" }
    override val lyricOriginalWesternFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_ORIGINAL_WESTERN_FONT_PATH] ?: "" }
    override val lyricOriginalCjkFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_ORIGINAL_CJK_FONT_NAME] ?: "" }
    override val lyricOriginalCjkFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_ORIGINAL_CJK_FONT_PATH] ?: "" }
    override val lyricTranslationWesternFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_TRANSLATION_WESTERN_FONT_NAME] ?: "" }
    override val lyricTranslationWesternFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_TRANSLATION_WESTERN_FONT_PATH] ?: "" }
    override val lyricTranslationCjkFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_TRANSLATION_CJK_FONT_NAME] ?: "" }
    override val lyricTranslationCjkFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_TRANSLATION_CJK_FONT_PATH] ?: "" }
    override val lyricFontWeight: Flow<Int> = context.dataStore.data.map { it[KEY_LYRIC_FONT_WEIGHT] ?: 800 }
    override val lyricFontScale: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_FONT_SCALE] ?: 100).coerceIn(LYRIC_FONT_SCALE_MIN, LYRIC_FONT_SCALE_ULTRA_WIDE_MAX)
    }
    override val lyricSecondaryFontScale: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_SECONDARY_FONT_SCALE] ?: 100).coerceIn(
            LYRIC_SECONDARY_FONT_SCALE_MIN,
            LYRIC_SECONDARY_FONT_SCALE_ULTRA_WIDE_MAX
        )
    }
    override val lyricCompactPrimaryTextSize: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_COMPACT_PRIMARY_TEXT_SIZE] ?: LYRIC_COMPACT_PRIMARY_TEXT_SIZE_DEFAULT_SP)
            .coerceIn(LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MIN_SP, LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MAX_SP)
    }
    override val lyricCompactSecondaryTextSize: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_COMPACT_SECONDARY_TEXT_SIZE] ?: LYRIC_COMPACT_SECONDARY_TEXT_SIZE_DEFAULT_SP)
            .coerceIn(LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MIN_SP, LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MAX_SP)
    }
    override val lyricWidePrimaryTextSize: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_WIDE_PRIMARY_TEXT_SIZE] ?: LYRIC_WIDE_PRIMARY_TEXT_SIZE_DEFAULT_SP)
            .coerceIn(LYRIC_WIDE_PRIMARY_TEXT_SIZE_MIN_SP, LYRIC_WIDE_PRIMARY_TEXT_SIZE_MAX_SP)
    }
    override val lyricWideSecondaryTextSize: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_WIDE_SECONDARY_TEXT_SIZE] ?: LYRIC_WIDE_SECONDARY_TEXT_SIZE_DEFAULT_SP)
            .coerceIn(LYRIC_WIDE_SECONDARY_TEXT_SIZE_MIN_SP, LYRIC_WIDE_SECONDARY_TEXT_SIZE_MAX_SP)
    }
    override val lyricFontItalic: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_FONT_ITALIC] ?: false }
    override val lyricFontApplyToPage: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_FONT_APPLY_TO_PAGE] ?: true }
    override val lyricFontApplyToDesktop: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_FONT_APPLY_TO_DESKTOP] ?: true }
    override val lyricShareUseLyricFont: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_SHARE_USE_LYRIC_FONT] ?: false }
    override val lyricPerspectiveEffect: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_PERSPECTIVE_EFFECT] ?: false }
    override val lyricPerspectiveYAngle: Flow<Int> = context.dataStore.data.map { it[KEY_LYRIC_PERSPECTIVE_Y_ANGLE] ?: 25 }

    override suspend fun setPlayerLyricTextAlign(align: Int) {
        context.dataStore.edit { it[KEY_PLAYER_LYRIC_TEXT_ALIGN] = align.coerceIn(0, 2) }
    }

    override suspend fun setLyricPronunciationBelow(below: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PRONUNCIATION_BELOW] = below }
    }

    override suspend fun setLyricLineBlacklist(lines: List<String>) {
        val normalized = normalizeLyricLineBlacklist(lines.asSequence())
        context.dataStore.edit { prefs ->
            if (normalized.isEmpty()) {
                prefs.remove(KEY_LYRIC_LINE_BLACKLIST)
            } else {
                prefs[KEY_LYRIC_LINE_BLACKLIST] = normalized.joinToString("\n")
            }
        }
    }

    override suspend fun setIgnoreLyricHeaderTags(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IGNORE_LYRIC_HEADER_TAGS] = enabled }
    }

    override suspend fun setHideLyricExtraInfo(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HIDE_LYRIC_EXTRA_INFO] = enabled }
    }

    override suspend fun setLyricSourceMode(mode: Int) {
        context.dataStore.edit { it[KEY_LYRIC_SOURCE_MODE] = mode.coerceIn(LYRIC_SOURCE_AUTO, LYRIC_SOURCE_EMBEDDED) }
    }

    override suspend fun setLyricSourcePriority(priority: String) {
        context.dataStore.edit { it[KEY_LYRIC_SOURCE_PRIORITY] = normalizeLyricSourcePriority(priority) }
    }

    override suspend fun setLyricoPluginEnabled(id: String, enabled: Boolean) {
        val pluginId = id.trim()
        if (pluginId.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = LyricoPluginManager.normalizeEnabledIds(prefs[KEY_LYRICO_PLUGIN_ENABLED_IDS]).toMutableSet()
            if (enabled) current += pluginId else current -= pluginId
            prefs[KEY_LYRICO_PLUGIN_ENABLED_IDS] = current.joinToString(",")
        }
    }

    override suspend fun setLyricOffsetOverride(songKey: String, offsetMs: Long) {
        val key = songKey.trim()
        if (key.isBlank()) return
        context.dataStore.edit { prefs ->
            val offsets = parseLyricOffsetOverrides(prefs[KEY_LYRIC_OFFSET_OVERRIDES]).toMutableMap()
            if (offsetMs == 0L) offsets.remove(key) else offsets[key] = offsetMs.coerceIn(-5000L, 5000L)
            prefs[KEY_LYRIC_OFFSET_OVERRIDES] = JSONObject(offsets.mapValues { it.value }).toString()
        }
    }

    override suspend fun setLyricPageTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PAGE_TRANSLATION] = enabled }
    }

    override suspend fun setLyricPageKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PAGE_KEEP_SCREEN_ON] = enabled }
    }

    override suspend fun setAppleMusicLyricsWordLift(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APPLE_MUSIC_LYRICS_WORD_LIFT] = enabled }
    }

    override suspend fun setAppleMusicLyricsSustainThresholdMs(thresholdMs: Int) {
        context.dataStore.edit {
            it[KEY_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS] = thresholdMs.coerceIn(
                SettingsManager.MIN_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS,
                SettingsManager.MAX_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS
            )
        }
    }

    override suspend fun setLyricPerspectiveEffect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PERSPECTIVE_EFFECT] = enabled }
    }

    override suspend fun setLyricPerspectiveYAngle(angle: Int) {
        context.dataStore.edit { it[KEY_LYRIC_PERSPECTIVE_Y_ANGLE] = angle.coerceIn(0, 45) }
    }

    override suspend fun setLyricShareCustomInfo(info: String) {
        context.dataStore.edit {
            val trimmed = info.trim().removePrefix("@").trim()
            if (trimmed.isBlank()) {
                it.remove(KEY_LYRIC_SHARE_CUSTOM_INFO)
            } else {
                it[KEY_LYRIC_SHARE_CUSTOM_INFO] = trimmed
            }
        }
    }

    override suspend fun setLyricShareUseLyricFont(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_SHARE_USE_LYRIC_FONT] = enabled }
    }

    override suspend fun setLyricFont(name: String, path: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_FONT_NAME] = name.ifBlank { context.getString(R.string.settings_default_custom_font_name) }
            it[KEY_LYRIC_FONT_PATH] = path
        }
    }

    override suspend fun clearLyricFont() {
        context.dataStore.edit {
            it.remove(KEY_LYRIC_FONT_NAME)
            it.remove(KEY_LYRIC_FONT_PATH)
        }
    }

    override suspend fun setLyricWesternFont(name: String, path: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_WESTERN_FONT_NAME] = name.ifBlank { "Inter" }
            it[KEY_LYRIC_WESTERN_FONT_PATH] = path
        }
    }

    override suspend fun clearLyricWesternFont() {
        context.dataStore.edit {
            it.remove(KEY_LYRIC_WESTERN_FONT_NAME)
            it.remove(KEY_LYRIC_WESTERN_FONT_PATH)
        }
    }

    override suspend fun setLyricCjkFont(name: String, path: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_CJK_FONT_NAME] = name.ifBlank { context.getString(R.string.settings_default_custom_font_name) }
            it[KEY_LYRIC_CJK_FONT_PATH] = path
        }
    }

    override suspend fun clearLyricCjkFont() {
        context.dataStore.edit {
            it.remove(KEY_LYRIC_CJK_FONT_NAME)
            it.remove(KEY_LYRIC_CJK_FONT_PATH)
        }
    }

    override suspend fun setGlobalFont(westernName: String, westernPath: String, cjkName: String, cjkPath: String) {
        context.dataStore.edit {
            it[KEY_GLOBAL_WESTERN_FONT_NAME] = westernName
            it[KEY_GLOBAL_WESTERN_FONT_PATH] = westernPath
            it[KEY_GLOBAL_CJK_FONT_NAME] = cjkName
            it[KEY_GLOBAL_CJK_FONT_PATH] = cjkPath
        }
    }

    override suspend fun setLyricOriginalFont(westernName: String, westernPath: String, cjkName: String, cjkPath: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_ORIGINAL_WESTERN_FONT_NAME] = westernName
            it[KEY_LYRIC_ORIGINAL_WESTERN_FONT_PATH] = westernPath
            it[KEY_LYRIC_ORIGINAL_CJK_FONT_NAME] = cjkName
            it[KEY_LYRIC_ORIGINAL_CJK_FONT_PATH] = cjkPath
        }
    }

    override suspend fun setLyricTranslationFont(westernName: String, westernPath: String, cjkName: String, cjkPath: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_TRANSLATION_WESTERN_FONT_NAME] = westernName
            it[KEY_LYRIC_TRANSLATION_WESTERN_FONT_PATH] = westernPath
            it[KEY_LYRIC_TRANSLATION_CJK_FONT_NAME] = cjkName
            it[KEY_LYRIC_TRANSLATION_CJK_FONT_PATH] = cjkPath
        }
    }

    override suspend fun setLyricFontWeight(weight: Int) {
        context.dataStore.edit { it[KEY_LYRIC_FONT_WEIGHT] = weight.coerceIn(100, 900) }
    }

    override suspend fun setLyricFontScale(scale: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_FONT_SCALE] = scale.coerceIn(LYRIC_FONT_SCALE_MIN, LYRIC_FONT_SCALE_ULTRA_WIDE_MAX)
        }
    }

    override suspend fun setLyricSecondaryFontScale(scale: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_SECONDARY_FONT_SCALE] =
                scale.coerceIn(LYRIC_SECONDARY_FONT_SCALE_MIN, LYRIC_SECONDARY_FONT_SCALE_ULTRA_WIDE_MAX)
        }
    }

    override suspend fun setLyricCompactPrimaryTextSize(sizeSp: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_COMPACT_PRIMARY_TEXT_SIZE] =
                sizeSp.coerceIn(LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MIN_SP, LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MAX_SP)
        }
    }

    override suspend fun setLyricCompactSecondaryTextSize(sizeSp: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_COMPACT_SECONDARY_TEXT_SIZE] =
                sizeSp.coerceIn(LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MIN_SP, LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MAX_SP)
        }
    }

    override suspend fun setLyricWidePrimaryTextSize(sizeSp: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_WIDE_PRIMARY_TEXT_SIZE] =
                sizeSp.coerceIn(LYRIC_WIDE_PRIMARY_TEXT_SIZE_MIN_SP, LYRIC_WIDE_PRIMARY_TEXT_SIZE_MAX_SP)
        }
    }

    override suspend fun setLyricWideSecondaryTextSize(sizeSp: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_WIDE_SECONDARY_TEXT_SIZE] =
                sizeSp.coerceIn(LYRIC_WIDE_SECONDARY_TEXT_SIZE_MIN_SP, LYRIC_WIDE_SECONDARY_TEXT_SIZE_MAX_SP)
        }
    }

    override suspend fun setLyricFontItalic(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_FONT_ITALIC] = enabled }
    }

    override suspend fun setLyricFontApplyToPage(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_FONT_APPLY_TO_PAGE] = enabled }
    }

    override suspend fun setLyricFontApplyToDesktop(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_FONT_APPLY_TO_DESKTOP] = enabled }
    }

    override suspend fun setLyricParserEngine(engine: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_PARSER_ENGINE] = engine.coerceIn(LYRIC_PARSER_ENGINE_AUTO, LYRIC_PARSER_ENGINE_ELLA)
        }
    }

    private fun parseLyricOffsetOverrides(json: String?): Map<String, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            root.keys().asSequence()
                .mapNotNull { key ->
                    val value = root.optLong(key, Long.MIN_VALUE)
                        .takeIf { it != Long.MIN_VALUE && it != 0L }
                        ?.coerceIn(-5000L, 5000L)
                    value?.let { key to it }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun parseLyricLineBlacklist(raw: String?): List<String> =
        normalizeLyricLineBlacklist(raw.orEmpty().lineSequence())

    private fun normalizeLyricLineBlacklist(lines: Sequence<String>): List<String> =
        lines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
}
