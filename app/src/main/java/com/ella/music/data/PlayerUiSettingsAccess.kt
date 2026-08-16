package com.ella.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.ella.music.data.SettingsManager.Companion.LYRIC_SECONDARY_OFF
import com.ella.music.data.SettingsManager.Companion.LYRIC_SECONDARY_PRONUNCIATION
import com.ella.music.data.SettingsManager.Companion.LYRIC_SECONDARY_TRANSLATION
import com.ella.music.data.SettingsManager.Companion.MINI_PLAYER_RIGHT_NEXT
import com.ella.music.data.SettingsManager.Companion.MINI_PLAYER_RIGHT_QUEUE
import com.ella.music.data.SettingsManager.Companion.PLAYER_BG_THEME_DARK
import com.ella.music.data.SettingsManager.Companion.PLAYER_TITLE_POSITION_ABOVE_COVER
import com.ella.music.data.SettingsManager.Companion.PLAYER_TITLE_POSITION_BELOW_COVER
import com.ella.music.data.SettingsManager.Companion.KEY_AUDIO_VISUALIZER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_AUDIO_VISUALIZER_OPACITY
import com.ella.music.data.SettingsManager.Companion.KEY_DYNAMIC_COVER_CUSTOM_FOLDERS
import com.ella.music.data.SettingsManager.Companion.KEY_DYNAMIC_COVER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_HIDE_SYSTEM_BARS
import com.ella.music.data.SettingsManager.Companion.KEY_SYSTEM_BARS_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_SYSTEM_BARS_RESERVE_SPACE
import com.ella.music.data.SettingsManager.Companion.KEY_MINI_PLAYER_COVER_ROTATION
import com.ella.music.data.SettingsManager.Companion.KEY_MINI_PLAYER_LYRIC_SECONDARY
import com.ella.music.data.SettingsManager.Companion.KEY_MINI_PLAYER_LYRIC_TRANSLATION
import com.ella.music.data.SettingsManager.Companion.KEY_MINI_PLAYER_LYRICS_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_MINI_PLAYER_RIGHT_BUTTON
import com.ella.music.data.SettingsManager.Companion.KEY_MINI_PLAYER_SWIPE_TO_OPEN_PLAYER
import com.ella.music.data.SettingsManager.Companion.KEY_MUSIC_VIDEO_CAPTURE_SUBTITLES
import com.ella.music.data.SettingsManager.Companion.KEY_MUSIC_VIDEO_ORIENTATION
import com.ella.music.data.SettingsManager.Companion.KEY_MUSIC_VIDEO_CUSTOM_FOLDERS
import com.ella.music.data.SettingsManager.Companion.KEY_MUSIC_VIDEO_OFFSETS_JSON
import com.ella.music.data.SettingsManager.Companion.KEY_MUSIC_VIDEO_SYNC_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_BACKGROUND_DIM
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_BACKGROUND_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_BACKGROUND_OPACITY
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_BACKGROUND_THEME
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_BACKGROUND_URI
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_BEAUTIFUL_LYRICS_BACKGROUND
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_BEAUTIFUL_LYRICS_BLUR
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_BEAUTIFUL_LYRICS_BRIGHTNESS
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_BEAUTIFUL_LYRICS_SPEED
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_COVER_CONTENT_COLOR
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_COVER_SWIPE_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_DYNAMIC_FLOW_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_HDR_GLOW
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_IMMERSIVE_COVER
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_KEEP_SCREEN_ON
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_LANDSCAPE_STYLE
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_PAGE_STYLE
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_PROGRESS_INFO_INDEX
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_SHOW_SONG_ANNOTATION
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_SHOW_TOTAL_DURATION
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_TAP_SEEK_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_TITLE_POSITION
import com.ella.music.data.SettingsManager.Companion.KEY_TRANSPORT_BUTTON_OUTLINES
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Player-screen appearance and interaction: mini player, transport controls, cover behaviour,
 * visualizer, dynamic cover / music-video sync, player background and beautiful-lyrics background.
 *
 * Extracted verbatim from [SettingsManager], which implements this interface via class
 * delegation so every call site keeps using settingsManager.<member> unchanged. All flow
 * properties MUST stay eagerly-initialised stored properties (never computed get() =):
 * Compose collectAsState keys on the flow instance, and a fresh instance per access would
 * restart collection on every recomposition.
 */
interface PlayerUiSettingsAccess {
    val playerBackgroundTheme: Flow<Int>
    val miniPlayerLyricTranslation: Flow<Boolean>
    val miniPlayerLyricSecondary: Flow<Int>
    val miniPlayerCoverRotation: Flow<Boolean>
    val miniPlayerLyricsEnabled: Flow<Boolean>
    val miniPlayerRightButton: Flow<Int>
    val miniPlayerSwipeToOpenPlayer: Flow<Boolean>
    val playerProgressInfoIndex: Flow<Int>
    val transportButtonOutlines: Flow<Boolean>
    val playerTapSeekEnabled: Flow<Boolean>
    val playerShowTotalDuration: Flow<Boolean>
    val playerShowSongAnnotation: Flow<Boolean>
    val playerCoverSwipeEnabled: Flow<Boolean>
    val playerTitlePosition: Flow<Int>
    val playerPageStyle: Flow<Int>
    val playerLandscapeStyle: Flow<Int>
    val playerKeepScreenOn: Flow<Boolean>
    val playerHdrGlow: Flow<Boolean>
    val playerImmersiveCover: Flow<Boolean>
    val playerCoverContentColor: Flow<Boolean>
    val systemBarsMode: Flow<Int>
    val systemBarsReserveSpace: Flow<Boolean>
    val playerDynamicFlowEnabled: Flow<Boolean>
    val audioVisualizerEnabled: Flow<Boolean>
    val audioVisualizerOpacity: Flow<Int>
    val dynamicCoverEnabled: Flow<Boolean>
    val musicVideoSyncEnabled: Flow<Boolean>
    val musicVideoCaptureSubtitles: Flow<Boolean>
    val musicVideoOrientation: Flow<Int>
    val musicVideoOffsetsJson: Flow<String>
    val dynamicCoverCustomFoldersRaw: Flow<String>
    val dynamicCoverCustomFolders: Flow<List<String>>
    val musicVideoCustomFoldersRaw: Flow<String>
    val musicVideoCustomFolders: Flow<List<String>>
    val playerBackgroundEnabled: Flow<Boolean>
    val playerBackgroundUri: Flow<String>
    val playerBackgroundOpacity: Flow<Int>
    val playerBackgroundDim: Flow<Int>
    val playerBeautifulLyricsBackground: Flow<Boolean>
    val playerBeautifulLyricsSpeed: Flow<Int>
    val playerBeautifulLyricsBlur: Flow<Int>
    val playerBeautifulLyricsBrightness: Flow<Int>
    suspend fun setPlayerBackgroundTheme(mode: Int)
    suspend fun setPlayerCoverContentColor(enabled: Boolean)
    suspend fun setMiniPlayerLyricTranslation(enabled: Boolean)
    suspend fun setMiniPlayerLyricSecondary(mode: Int)
    suspend fun setMiniPlayerCoverRotation(enabled: Boolean)
    suspend fun setMiniPlayerLyricsEnabled(enabled: Boolean)
    suspend fun setMiniPlayerRightButton(mode: Int)
    suspend fun setMiniPlayerSwipeToOpenPlayer(enabled: Boolean)
    suspend fun setPlayerProgressInfoIndex(index: Int)
    suspend fun setTransportButtonOutlines(enabled: Boolean)
    suspend fun setPlayerHdrGlow(enabled: Boolean)
    suspend fun setPlayerImmersiveCover(enabled: Boolean)
    suspend fun setSystemBarsMode(mode: Int)
    suspend fun setSystemBarsReserveSpace(enabled: Boolean)
    suspend fun setPlayerDynamicFlowEnabled(enabled: Boolean)
    suspend fun setAudioVisualizerEnabled(enabled: Boolean)
    suspend fun setAudioVisualizerOpacity(opacity: Int)
    suspend fun setDynamicCoverEnabled(enabled: Boolean)
    suspend fun setMusicVideoSyncEnabled(enabled: Boolean)
    suspend fun setMusicVideoCaptureSubtitles(enabled: Boolean)
    suspend fun setMusicVideoOrientation(orientation: Int)
    suspend fun setMusicVideoOffsetsJson(json: String)
    suspend fun setDynamicCoverCustomFolders(folders: String)
    suspend fun setMusicVideoCustomFolders(folders: String)
    suspend fun setPlayerBackgroundEnabled(enabled: Boolean)
    suspend fun setPlayerBackgroundUri(uri: String)
    suspend fun setPlayerBackgroundOpacity(opacity: Int)
    suspend fun setPlayerBackgroundDim(dim: Int)
    suspend fun setPlayerBeautifulLyricsBackground(enabled: Boolean)
    suspend fun setPlayerBeautifulLyricsSpeed(value: Int)
    suspend fun setPlayerBeautifulLyricsBlur(value: Int)
    suspend fun setPlayerBeautifulLyricsBrightness(value: Int)
    suspend fun setPlayerTapSeekEnabled(enabled: Boolean)
    suspend fun setPlayerShowTotalDuration(enabled: Boolean)
    suspend fun setPlayerShowSongAnnotation(enabled: Boolean)
    suspend fun setPlayerCoverSwipeEnabled(enabled: Boolean)
    suspend fun setPlayerTitlePosition(position: Int)
    suspend fun setPlayerPageStyle(style: Int)
    suspend fun setPlayerLandscapeStyle(style: Int)
    suspend fun setPlayerKeepScreenOn(enabled: Boolean)
}

internal class PlayerUiSettingsAccessImpl(private val context: Context) : PlayerUiSettingsAccess {

    override val playerBackgroundTheme: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BACKGROUND_THEME] ?: PLAYER_BG_THEME_DARK }

    override val miniPlayerLyricTranslation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_LYRIC_TRANSLATION] ?: true }
    override val miniPlayerLyricSecondary: Flow<Int> = context.dataStore.data.map {
        (it[KEY_MINI_PLAYER_LYRIC_SECONDARY]
            ?: if (it[KEY_MINI_PLAYER_LYRIC_TRANSLATION] == false) {
                LYRIC_SECONDARY_OFF
            } else {
                LYRIC_SECONDARY_TRANSLATION
            }).coerceIn(LYRIC_SECONDARY_OFF, LYRIC_SECONDARY_PRONUNCIATION)
    }
    override val miniPlayerCoverRotation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_COVER_ROTATION] ?: true }

    override val miniPlayerLyricsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_LYRICS_ENABLED] ?: true }
    override val miniPlayerRightButton: Flow<Int> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_RIGHT_BUTTON] ?: MINI_PLAYER_RIGHT_NEXT }
    override val miniPlayerSwipeToOpenPlayer: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_SWIPE_TO_OPEN_PLAYER] ?: true }
    override val playerProgressInfoIndex: Flow<Int> =
        context.dataStore.data.map { (it[KEY_PLAYER_PROGRESS_INFO_INDEX] ?: 0).coerceAtLeast(0) }
    override val transportButtonOutlines: Flow<Boolean> =
        context.dataStore.data.map {
            it[KEY_TRANSPORT_BUTTON_OUTLINES]
                ?: SettingsManager.DEFAULT_TRANSPORT_BUTTON_OUTLINES
        }
    override val playerTapSeekEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_TAP_SEEK_ENABLED] ?: true }
    override val playerShowTotalDuration: Flow<Boolean> =
        context.dataStore.data.map {
            it[KEY_PLAYER_SHOW_TOTAL_DURATION]
                ?: SettingsManager.DEFAULT_PLAYER_SHOW_TOTAL_DURATION
        }
    override val playerShowSongAnnotation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_SHOW_SONG_ANNOTATION] ?: true }
    override val playerCoverSwipeEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_COVER_SWIPE_ENABLED] ?: false }

    override val playerTitlePosition: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_PLAYER_TITLE_POSITION] ?: PLAYER_TITLE_POSITION_BELOW_COVER)
                .coerceIn(PLAYER_TITLE_POSITION_BELOW_COVER, PLAYER_TITLE_POSITION_ABOVE_COVER)
        }
    override val playerPageStyle: Flow<Int> =
        context.dataStore.data.map { SettingsManager.normalizePlayerPageStyle(it[KEY_PLAYER_PAGE_STYLE]) }
    override val playerLandscapeStyle: Flow<Int> =
        context.dataStore.data.map { SettingsManager.normalizePlayerLandscapeStyle(it[KEY_PLAYER_LANDSCAPE_STYLE]) }
    override val playerKeepScreenOn: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_KEEP_SCREEN_ON] ?: false }
    override val playerHdrGlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PLAYER_HDR_GLOW] ?: false }
    override val playerImmersiveCover: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_IMMERSIVE_COVER] ?: false }
    override val playerCoverContentColor: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_COVER_CONTENT_COLOR] ?: false }

    override val systemBarsMode: Flow<Int> =
        context.dataStore.data.map {
            SettingsManager.resolveSystemBarsMode(
                storedMode = it[KEY_SYSTEM_BARS_MODE],
                legacyHideSystemBars = it[KEY_HIDE_SYSTEM_BARS] ?: false
            )
        }
    override val systemBarsReserveSpace: Flow<Boolean> =
        context.dataStore.data.map {
            it[KEY_SYSTEM_BARS_RESERVE_SPACE]
                ?: SettingsManager.DEFAULT_SYSTEM_BARS_RESERVE_SPACE
        }
    override val playerDynamicFlowEnabled: Flow<Boolean> =
        context.dataStore.data.map {
            it[KEY_PLAYER_DYNAMIC_FLOW_ENABLED]
                ?: SettingsManager.DEFAULT_PLAYER_DYNAMIC_FLOW_ENABLED
        }
    override val audioVisualizerEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUDIO_VISUALIZER_ENABLED] ?: false }
    override val audioVisualizerOpacity: Flow<Int> =
        context.dataStore.data.map { it[KEY_AUDIO_VISUALIZER_OPACITY]?.coerceIn(20, 100) ?: 100 }

    override val dynamicCoverEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DYNAMIC_COVER_ENABLED] ?: false }
    override val musicVideoSyncEnabled: Flow<Boolean> =
        context.dataStore.data.map {
            it[KEY_MUSIC_VIDEO_SYNC_ENABLED]
                ?: SettingsManager.DEFAULT_MUSIC_VIDEO_SYNC_ENABLED
        }
    override val musicVideoCaptureSubtitles: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MUSIC_VIDEO_CAPTURE_SUBTITLES] ?: false }
    override val musicVideoOrientation: Flow<Int> =
        context.dataStore.data.map {
            it[KEY_MUSIC_VIDEO_ORIENTATION]
                ?.coerceIn(
                    SettingsManager.MUSIC_VIDEO_ORIENTATION_SYSTEM,
                    SettingsManager.MUSIC_VIDEO_ORIENTATION_PORTRAIT
                )
                ?: SettingsManager.DEFAULT_MUSIC_VIDEO_ORIENTATION
        }
    override val musicVideoOffsetsJson: Flow<String> =
        context.dataStore.data.map { it[KEY_MUSIC_VIDEO_OFFSETS_JSON].orEmpty() }
    override val dynamicCoverCustomFoldersRaw: Flow<String> =
        context.dataStore.data.map { normalizeDynamicCoverCustomFolders(it[KEY_DYNAMIC_COVER_CUSTOM_FOLDERS]) }
    override val dynamicCoverCustomFolders: Flow<List<String>> =
        dynamicCoverCustomFoldersRaw.map(::parseDynamicCoverCustomFolders)
    override val musicVideoCustomFoldersRaw: Flow<String> =
        context.dataStore.data.map { normalizeDynamicCoverCustomFolders(it[KEY_MUSIC_VIDEO_CUSTOM_FOLDERS]) }
    override val musicVideoCustomFolders: Flow<List<String>> =
        musicVideoCustomFoldersRaw.map(::parseDynamicCoverCustomFolders)

    override val playerBackgroundEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_BACKGROUND_ENABLED] ?: false }
    override val playerBackgroundUri: Flow<String> =
        context.dataStore.data.map { it[KEY_PLAYER_BACKGROUND_URI] ?: "" }
    override val playerBackgroundOpacity: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BACKGROUND_OPACITY]?.coerceIn(20, 100) ?: 100 }
    override val playerBackgroundDim: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BACKGROUND_DIM]?.coerceIn(0, 80) ?: 26 }
    override val playerBeautifulLyricsBackground: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BACKGROUND] ?: false }
    override val playerBeautifulLyricsSpeed: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BEAUTIFUL_LYRICS_SPEED]?.coerceIn(5, 60) ?: 25 }
    override val playerBeautifulLyricsBlur: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BLUR]?.coerceIn(0, 80) ?: 32 }
    override val playerBeautifulLyricsBrightness: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BRIGHTNESS]?.coerceIn(30, 120) ?: 70 }

    override suspend fun setPlayerBackgroundTheme(mode: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BACKGROUND_THEME] = mode }
    }

    override suspend fun setPlayerCoverContentColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_COVER_CONTENT_COLOR] = enabled }
    }

    override suspend fun setMiniPlayerLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_LYRIC_TRANSLATION] = enabled }
    }

    override suspend fun setMiniPlayerLyricSecondary(mode: Int) {
        context.dataStore.edit {
            val safeMode = mode.coerceIn(LYRIC_SECONDARY_OFF, LYRIC_SECONDARY_PRONUNCIATION)
            it[KEY_MINI_PLAYER_LYRIC_SECONDARY] = safeMode
            it[KEY_MINI_PLAYER_LYRIC_TRANSLATION] = safeMode == LYRIC_SECONDARY_TRANSLATION
        }
    }

    override suspend fun setMiniPlayerCoverRotation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_COVER_ROTATION] = enabled }
    }

    override suspend fun setMiniPlayerLyricsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_LYRICS_ENABLED] = enabled }
    }

    override suspend fun setMiniPlayerRightButton(mode: Int) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_RIGHT_BUTTON] = mode.coerceIn(MINI_PLAYER_RIGHT_NEXT, MINI_PLAYER_RIGHT_QUEUE) }
    }

    override suspend fun setMiniPlayerSwipeToOpenPlayer(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_SWIPE_TO_OPEN_PLAYER] = enabled }
    }

    override suspend fun setPlayerProgressInfoIndex(index: Int) {
        context.dataStore.edit { it[KEY_PLAYER_PROGRESS_INFO_INDEX] = index.coerceAtLeast(0) }
    }

    override suspend fun setTransportButtonOutlines(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TRANSPORT_BUTTON_OUTLINES] = enabled }
    }

    override suspend fun setPlayerHdrGlow(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_HDR_GLOW] = enabled }
    }

    override suspend fun setPlayerImmersiveCover(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_IMMERSIVE_COVER] = enabled }
    }


    override suspend fun setSystemBarsMode(mode: Int) {
        val normalized = SettingsManager.resolveSystemBarsMode(mode, legacyHideSystemBars = false)
        context.dataStore.edit {
            it[KEY_SYSTEM_BARS_MODE] = normalized
            it[KEY_HIDE_SYSTEM_BARS] =
                normalized == SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH
        }
    }

    override suspend fun setSystemBarsReserveSpace(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SYSTEM_BARS_RESERVE_SPACE] = enabled }
    }

    override suspend fun setPlayerDynamicFlowEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_DYNAMIC_FLOW_ENABLED] = enabled }
    }

    override suspend fun setAudioVisualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUDIO_VISUALIZER_ENABLED] = enabled }
    }

    override suspend fun setAudioVisualizerOpacity(opacity: Int) {
        context.dataStore.edit { it[KEY_AUDIO_VISUALIZER_OPACITY] = opacity.coerceIn(20, 100) }
    }

    override suspend fun setDynamicCoverEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COVER_ENABLED] = enabled }
    }

    override suspend fun setMusicVideoSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MUSIC_VIDEO_SYNC_ENABLED] = enabled }
    }

    override suspend fun setMusicVideoCaptureSubtitles(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MUSIC_VIDEO_CAPTURE_SUBTITLES] = enabled }
    }

    override suspend fun setMusicVideoOrientation(orientation: Int) {
        context.dataStore.edit {
            it[KEY_MUSIC_VIDEO_ORIENTATION] = orientation.coerceIn(
                SettingsManager.MUSIC_VIDEO_ORIENTATION_SYSTEM,
                SettingsManager.MUSIC_VIDEO_ORIENTATION_PORTRAIT
            )
        }
    }

    override suspend fun setMusicVideoOffsetsJson(json: String) {
        context.dataStore.edit {
            val value = json.trim()
            if (value.isBlank()) it.remove(KEY_MUSIC_VIDEO_OFFSETS_JSON) else it[KEY_MUSIC_VIDEO_OFFSETS_JSON] = value
        }
    }

    override suspend fun setDynamicCoverCustomFolders(folders: String) {
        context.dataStore.edit { prefs ->
            val normalized = normalizeDynamicCoverCustomFolders(folders)
            if (normalized.isBlank()) {
                prefs.remove(KEY_DYNAMIC_COVER_CUSTOM_FOLDERS)
            } else {
                prefs[KEY_DYNAMIC_COVER_CUSTOM_FOLDERS] = normalized
            }
        }
    }

    override suspend fun setMusicVideoCustomFolders(folders: String) {
        context.dataStore.edit { prefs ->
            val normalized = normalizeDynamicCoverCustomFolders(folders)
            if (normalized.isBlank()) {
                prefs.remove(KEY_MUSIC_VIDEO_CUSTOM_FOLDERS)
            } else {
                prefs[KEY_MUSIC_VIDEO_CUSTOM_FOLDERS] = normalized
            }
        }
    }

    override suspend fun setPlayerBackgroundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_BACKGROUND_ENABLED] = enabled }
    }

    override suspend fun setPlayerBackgroundUri(uri: String) {
        context.dataStore.edit {
            val safeUri = uri.trim()
            if (safeUri.isBlank()) it.remove(KEY_PLAYER_BACKGROUND_URI) else it[KEY_PLAYER_BACKGROUND_URI] = safeUri
        }
    }

    override suspend fun setPlayerBackgroundOpacity(opacity: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BACKGROUND_OPACITY] = opacity.coerceIn(20, 100) }
    }

    override suspend fun setPlayerBackgroundDim(dim: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BACKGROUND_DIM] = dim.coerceIn(0, 80) }
    }

    override suspend fun setPlayerBeautifulLyricsBackground(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BACKGROUND] = enabled }
    }

    override suspend fun setPlayerBeautifulLyricsSpeed(value: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BEAUTIFUL_LYRICS_SPEED] = value.coerceIn(5, 60) }
    }

    override suspend fun setPlayerBeautifulLyricsBlur(value: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BLUR] = value.coerceIn(0, 80) }
    }

    override suspend fun setPlayerBeautifulLyricsBrightness(value: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BRIGHTNESS] = value.coerceIn(30, 120) }
    }

    override suspend fun setPlayerTapSeekEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_TAP_SEEK_ENABLED] = enabled }
    }

    override suspend fun setPlayerShowTotalDuration(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_SHOW_TOTAL_DURATION] = enabled }
    }

    override suspend fun setPlayerShowSongAnnotation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_SHOW_SONG_ANNOTATION] = enabled }
    }

    override suspend fun setPlayerCoverSwipeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_COVER_SWIPE_ENABLED] = enabled }
    }

    override suspend fun setPlayerTitlePosition(position: Int) {
        context.dataStore.edit {
            it[KEY_PLAYER_TITLE_POSITION] = position.coerceIn(
                PLAYER_TITLE_POSITION_BELOW_COVER,
                PLAYER_TITLE_POSITION_ABOVE_COVER
            )
        }
    }

    override suspend fun setPlayerPageStyle(style: Int) {
        context.dataStore.edit {
            it[KEY_PLAYER_PAGE_STYLE] = SettingsManager.normalizePlayerPageStyle(style)
        }
    }

    override suspend fun setPlayerLandscapeStyle(style: Int) {
        context.dataStore.edit {
            it[KEY_PLAYER_LANDSCAPE_STYLE] = SettingsManager.normalizePlayerLandscapeStyle(style)
        }
    }

    override suspend fun setPlayerKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_KEEP_SCREEN_ON] = enabled }
    }

    private fun parseDynamicCoverCustomFolders(raw: String): List<String> =
        normalizeDynamicCoverCustomFolders(raw)
            .split('\n')
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun normalizeDynamicCoverCustomFolders(raw: String?): String =
        raw.orEmpty()
            .split(Regex("""[;\r\n]+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
}
