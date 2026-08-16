package com.ella.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.TagEditorOptionIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * All DataStore-backed settings the player screen needs, collected through a single combined
 * flow instead of ~17 independent [collectAsState] subscriptions.
 *
 * PlayerScreen reads every one of these at the top of the same composable, so any single
 * setting change already recomposes the whole body — bundling them does not widen the
 * recomposition scope, it only collapses the burst of collectors (and their initial
 * emissions) that used to spin up each time the player surface entered composition.
 */
internal data class PlayerScreenSettings(
    val playerTapSeekEnabled: Boolean = true,
    val playerShowTotalDuration: Boolean = SettingsManager.DEFAULT_PLAYER_SHOW_TOTAL_DURATION,
    val lyricSourceMode: Int = SettingsManager.LYRIC_SOURCE_AUTO,
    val audioVisualizerEnabled: Boolean = false,
    val audioVisualizerOpacity: Int = 100,
    val dynamicCoverEnabled: Boolean = false,
    val musicVideoSyncEnabled: Boolean = SettingsManager.DEFAULT_MUSIC_VIDEO_SYNC_ENABLED,
    val dynamicCoverCustomFolders: List<String> = emptyList(),
    val musicVideoCustomFolders: List<String> = emptyList(),
    val immersiveAlbumCover: Boolean = false,
    val coverContentColor: Boolean = false,
    val playerBackgroundEnabled: Boolean = false,
    val playerBackgroundUri: String = "",
    val playerBackgroundOpacity: Int = 100,
    val playerBackgroundDim: Int = 26,
    val beautifulLyricsBackground: Boolean = false,
    val playerDynamicFlowEnabled: Boolean = SettingsManager.DEFAULT_PLAYER_DYNAMIC_FLOW_ENABLED,
    val showSongAnnotation: Boolean = true,
    val coverSwipeEnabled: Boolean = true,
    val lyricParserEngine: Int = SettingsManager.LYRIC_PARSER_ENGINE_ELLA,
    val playerTitlePosition: Int = SettingsManager.PLAYER_TITLE_POSITION_BELOW_COVER,
    val playerPageStyle: Int = SettingsManager.DEFAULT_PLAYER_PAGE_STYLE,
    val playerLandscapeStyle: Int = SettingsManager.DEFAULT_PLAYER_LANDSCAPE_STYLE,
    val playerKeepScreenOn: Boolean = false,
    val hiResLogoEnabled: Boolean = false,
    val hiResLogoUri: String = "",
    val lyricShareCustomInfo: String = "",
    val metadataEditorId: String = TagEditorOptionIds.ASK_EACH_TIME,
    val lyricTimingEditorId: String = TagEditorOptionIds.ASK_EACH_TIME,
    val sleepTimerCustomMinutes: Int = 45,
    val sleepTimerStopAfterCurrent: Boolean = false,
    val lyricPageKeepScreenOn: Boolean = false,
    val appleMusicLyricsWordLift: Boolean = true,
    val lyricPerspectiveEffect: Boolean = false,
    val lyricPerspectiveYAngle: Int = 25,
    val playerLyricTextAlign: Int = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT
)

private data class PlayerSettingsGroupA(
    val playerTapSeekEnabled: Boolean,
    val playerShowTotalDuration: Boolean,
    val lyricSourceMode: Int,
    val audioVisualizerEnabled: Boolean,
    val audioVisualizerOpacity: Int,
    val dynamicCoverEnabled: Boolean,
    val musicVideoSyncEnabled: Boolean,
    val dynamicCoverCustomFolders: List<String>,
    val musicVideoCustomFolders: List<String>
)

private data class PlayerSettingsDynamicCover(
    val dynamicCoverEnabled: Boolean,
    val musicVideoSyncEnabled: Boolean,
    val dynamicCoverCustomFolders: List<String>,
    val musicVideoCustomFolders: List<String>
)

private data class PlayerSettingsVisualizer(
    val enabled: Boolean,
    val opacity: Int
)

private data class PlayerSettingsGroupB(
    val immersiveAlbumCover: Boolean,
    val coverContentColor: Boolean,
    val playerBackgroundEnabled: Boolean,
    val playerBackgroundUri: String,
    val playerBackgroundOpacity: Int,
    val playerBackgroundDim: Int,
    val beautifulLyricsBackground: Boolean,
    val playerDynamicFlowEnabled: Boolean,
    val showSongAnnotation: Boolean,
    val coverSwipeEnabled: Boolean,
    val lyricParserEngine: Int,
    val playerTitlePosition: Int,
    val playerPageStyle: Int,
    val playerLandscapeStyle: Int,
    val playerKeepScreenOn: Boolean,
    val hiResLogoEnabled: Boolean,
    val hiResLogoUri: String
)

private data class PlayerSettingsGroupBBase(
    val immersiveAlbumCover: Boolean,
    val coverContentColor: Boolean,
    val background: PlayerBackgroundSettings
)

private data class PlayerBackgroundSettings(
    val enabled: Boolean,
    val uri: String,
    val opacity: Int,
    val dim: Int
)

private data class PlayerSettingsGroupBExtra(
    val beautifulLyricsBackground: Boolean,
    val playerDynamicFlowEnabled: Boolean,
    val showSongAnnotation: Boolean,
    val coverSwipeEnabled: Boolean,
    val lyricParserEngine: Int,
    val playerTitlePosition: Int,
    val playerPageStyle: Int,
    val playerLandscapeStyle: Int,
    val playerKeepScreenOn: Boolean,
    val hiResLogoEnabled: Boolean,
    val hiResLogoUri: String
)

private data class PlayerSettingsGroupBFlagsPart1(
    val beautifulLyricsBackground: Boolean,
    val playerDynamicFlowEnabled: Boolean,
    val showSongAnnotation: Boolean,
    val coverSwipeEnabled: Boolean,
    val lyricParserEngine: Int
)

private data class PlayerSettingsGroupBFlags(
    val beautifulLyricsBackground: Boolean,
    val playerDynamicFlowEnabled: Boolean,
    val showSongAnnotation: Boolean,
    val coverSwipeEnabled: Boolean,
    val lyricParserEngine: Int,
    val playerTitlePosition: Int,
    val playerPageStyle: Int,
    val playerLandscapeStyle: Int,
    val playerKeepScreenOn: Boolean
)

private data class PlayerSettingsGroupBHiRes(
    val hiResLogoEnabled: Boolean,
    val hiResLogoUri: String
)

private data class PlayerSettingsGroupC(
    val lyricShareCustomInfo: String,
    val metadataEditorId: String,
    val lyricTimingEditorId: String,
    val sleepTimerCustomMinutes: Int,
    val sleepTimerStopAfterCurrent: Boolean
)

private data class PlayerSettingsGroupD(
    val lyricPageKeepScreenOn: Boolean,
    val appleMusicLyricsWordLift: Boolean,
    val lyricPerspectiveEffect: Boolean,
    val lyricPerspectiveYAngle: Int,
    val playerLyricTextAlign: Int
)

@Composable
internal fun rememberPlayerScreenSettings(settingsManager: SettingsManager): PlayerScreenSettings {
    val flow: Flow<PlayerScreenSettings> = remember(settingsManager) {
        val visualizer = combine(
            settingsManager.audioVisualizerEnabled,
            settingsManager.audioVisualizerOpacity
        ) { enabled, opacity ->
            PlayerSettingsVisualizer(enabled, opacity)
        }
        val dynamicCoverSettings = combine(
            settingsManager.dynamicCoverEnabled,
            settingsManager.musicVideoSyncEnabled,
            settingsManager.dynamicCoverCustomFolders,
            settingsManager.musicVideoCustomFolders
        ) { enabled, musicVideoEnabled, customFolders, musicVideoFolders ->
            PlayerSettingsDynamicCover(enabled, musicVideoEnabled, customFolders, musicVideoFolders)
        }
        val groupA = combine(
            settingsManager.playerTapSeekEnabled,
            settingsManager.playerShowTotalDuration,
            settingsManager.lyricSourceMode,
            visualizer,
            dynamicCoverSettings
        ) { tapSeek, showTotal, lyricSource, visualizerState, dynamicCover ->
            PlayerSettingsGroupA(
                playerTapSeekEnabled = tapSeek,
                playerShowTotalDuration = showTotal,
                lyricSourceMode = lyricSource,
                audioVisualizerEnabled = visualizerState.enabled,
                audioVisualizerOpacity = visualizerState.opacity,
                dynamicCoverEnabled = dynamicCover.dynamicCoverEnabled,
                musicVideoSyncEnabled = dynamicCover.musicVideoSyncEnabled,
                dynamicCoverCustomFolders = dynamicCover.dynamicCoverCustomFolders,
                musicVideoCustomFolders = dynamicCover.musicVideoCustomFolders
            )
        }
        val playerBackground = combine(
            settingsManager.playerBackgroundEnabled,
            settingsManager.playerBackgroundUri,
            settingsManager.playerBackgroundOpacity,
            settingsManager.playerBackgroundDim
        ) { enabled, uri, opacity, dim ->
            PlayerBackgroundSettings(enabled, uri, opacity, dim)
        }
        val groupBBase = combine(
            settingsManager.playerImmersiveCover,
            settingsManager.playerCoverContentColor,
            playerBackground
        ) { immersive, coverContentColor, background ->
            PlayerSettingsGroupBBase(immersive, coverContentColor, background)
        }
        val groupBFlagsPart1 = combine(
            settingsManager.playerBeautifulLyricsBackground,
            settingsManager.playerDynamicFlowEnabled,
            settingsManager.playerShowSongAnnotation,
            settingsManager.playerCoverSwipeEnabled,
            settingsManager.lyricParserEngine
        ) { beautifulLyrics, dynamicFlowEnabled, showAnnotation, coverSwipe, parserEngine ->
            PlayerSettingsGroupBFlagsPart1(
                beautifulLyrics,
                dynamicFlowEnabled,
                showAnnotation,
                coverSwipe,
                parserEngine
            )
        }
        val groupBFlags = combine(
            groupBFlagsPart1,
            settingsManager.playerTitlePosition,
            settingsManager.playerPageStyle,
            settingsManager.playerLandscapeStyle,
            settingsManager.playerKeepScreenOn
        ) { part1, titlePosition, pageStyle, landscapeStyle, keepScreenOn ->
            PlayerSettingsGroupBFlags(
                beautifulLyricsBackground = part1.beautifulLyricsBackground,
                playerDynamicFlowEnabled = part1.playerDynamicFlowEnabled,
                showSongAnnotation = part1.showSongAnnotation,
                coverSwipeEnabled = part1.coverSwipeEnabled,
                lyricParserEngine = part1.lyricParserEngine,
                playerTitlePosition = titlePosition,
                playerPageStyle = pageStyle,
                playerLandscapeStyle = landscapeStyle,
                playerKeepScreenOn = keepScreenOn
            )
        }
        val groupBHiRes = combine(
            settingsManager.hiResLogoEnabled,
            settingsManager.hiResLogoUri
        ) { hiResEnabled, hiResUri ->
            PlayerSettingsGroupBHiRes(hiResEnabled, hiResUri)
        }
        val groupBExtra = combine(groupBFlags, groupBHiRes) { flags, hiRes ->
            PlayerSettingsGroupBExtra(
                beautifulLyricsBackground = flags.beautifulLyricsBackground,
                playerDynamicFlowEnabled = flags.playerDynamicFlowEnabled,
                showSongAnnotation = flags.showSongAnnotation,
                coverSwipeEnabled = flags.coverSwipeEnabled,
                lyricParserEngine = flags.lyricParserEngine,
                playerTitlePosition = flags.playerTitlePosition,
                playerPageStyle = flags.playerPageStyle,
                playerLandscapeStyle = flags.playerLandscapeStyle,
                playerKeepScreenOn = flags.playerKeepScreenOn,
                hiResLogoEnabled = hiRes.hiResLogoEnabled,
                hiResLogoUri = hiRes.hiResLogoUri
            )
        }
        val groupB = combine(groupBBase, groupBExtra) { base, extra ->
            PlayerSettingsGroupB(
                immersiveAlbumCover = base.immersiveAlbumCover,
                coverContentColor = base.coverContentColor,
                playerBackgroundEnabled = base.background.enabled,
                playerBackgroundUri = base.background.uri,
                playerBackgroundOpacity = base.background.opacity,
                playerBackgroundDim = base.background.dim,
                beautifulLyricsBackground = extra.beautifulLyricsBackground,
                playerDynamicFlowEnabled = extra.playerDynamicFlowEnabled,
                showSongAnnotation = extra.showSongAnnotation,
                coverSwipeEnabled = extra.coverSwipeEnabled,
                lyricParserEngine = extra.lyricParserEngine,
                playerTitlePosition = extra.playerTitlePosition,
                playerPageStyle = extra.playerPageStyle,
                playerLandscapeStyle = extra.playerLandscapeStyle,
                playerKeepScreenOn = extra.playerKeepScreenOn,
                hiResLogoEnabled = extra.hiResLogoEnabled,
                hiResLogoUri = extra.hiResLogoUri
            )
        }
        val groupC = combine(
            settingsManager.lyricShareCustomInfo,
            settingsManager.metadataEditorId,
            settingsManager.lyricTimingEditorId,
            settingsManager.sleepTimerCustomMinutes,
            settingsManager.sleepTimerStopAfterCurrent
        ) { shareInfo, metadataId, timingId, customMinutes, stopAfterCurrent ->
            PlayerSettingsGroupC(shareInfo, metadataId, timingId, customMinutes, stopAfterCurrent)
        }
        val groupD = combine(
            settingsManager.lyricPageKeepScreenOn,
            settingsManager.appleMusicLyricsWordLift,
            settingsManager.lyricPerspectiveEffect,
            settingsManager.lyricPerspectiveYAngle,
            settingsManager.playerLyricTextAlign
        ) { keepScreenOn, wordLiftEnabled, perspective, perspectiveYAngle, lyricTextAlign ->
            PlayerSettingsGroupD(
                keepScreenOn,
                wordLiftEnabled,
                perspective,
                perspectiveYAngle,
                lyricTextAlign
            )
        }
        combine(groupA, groupB, groupC, groupD) { a, b, c, d ->
            PlayerScreenSettings(
                playerTapSeekEnabled = a.playerTapSeekEnabled,
                playerShowTotalDuration = a.playerShowTotalDuration,
                lyricSourceMode = a.lyricSourceMode,
                audioVisualizerEnabled = a.audioVisualizerEnabled,
                audioVisualizerOpacity = a.audioVisualizerOpacity,
                dynamicCoverEnabled = a.dynamicCoverEnabled,
                musicVideoSyncEnabled = a.musicVideoSyncEnabled,
                dynamicCoverCustomFolders = a.dynamicCoverCustomFolders,
                musicVideoCustomFolders = a.musicVideoCustomFolders,
                immersiveAlbumCover = b.immersiveAlbumCover,
                coverContentColor = b.coverContentColor,
                playerBackgroundEnabled = b.playerBackgroundEnabled,
                playerBackgroundUri = b.playerBackgroundUri,
                playerBackgroundOpacity = b.playerBackgroundOpacity,
                playerBackgroundDim = b.playerBackgroundDim,
                beautifulLyricsBackground = b.beautifulLyricsBackground,
                playerDynamicFlowEnabled = b.playerDynamicFlowEnabled,
                showSongAnnotation = b.showSongAnnotation,
                coverSwipeEnabled = b.coverSwipeEnabled,
                lyricParserEngine = b.lyricParserEngine,
                playerTitlePosition = b.playerTitlePosition,
                playerPageStyle = b.playerPageStyle,
                playerLandscapeStyle = b.playerLandscapeStyle,
                playerKeepScreenOn = b.playerKeepScreenOn,
                hiResLogoEnabled = b.hiResLogoEnabled,
                hiResLogoUri = b.hiResLogoUri,
                lyricShareCustomInfo = c.lyricShareCustomInfo,
                metadataEditorId = c.metadataEditorId,
                lyricTimingEditorId = c.lyricTimingEditorId,
                sleepTimerCustomMinutes = c.sleepTimerCustomMinutes,
                sleepTimerStopAfterCurrent = c.sleepTimerStopAfterCurrent,
                lyricPageKeepScreenOn = d.lyricPageKeepScreenOn,
                appleMusicLyricsWordLift = d.appleMusicLyricsWordLift,
                lyricPerspectiveEffect = d.lyricPerspectiveEffect,
                lyricPerspectiveYAngle = d.lyricPerspectiveYAngle,
                playerLyricTextAlign = d.playerLyricTextAlign
            )
        }
    }
    val settings by flow.collectAsState(initial = PlayerScreenSettings())
    return settings
}
