package com.ella.music.ui.settings

import android.app.Application
import androidx.datastore.preferences.core.edit
import com.ella.music.data.dataStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.ActionMenuLayout
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.netease.NeteaseLinks
import com.ella.music.data.netease.NeteaseLinkTarget
import com.ella.music.data.netease.NeteaseLinkKind
import com.ella.music.ui.folder.FolderDisplaySettings
import com.ella.music.ui.folder.FolderDisplayStore
import com.ella.music.ui.about.UpdateChannelPreferences
import com.ella.music.MusicVideoCaptionStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsBackupRoundTripTest {
    @Before @After
    fun clearSettingsSingletonBetweenApplications() {
        SettingsManager::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        FolderDisplayStore.state = null
    }
    @Test fun changingLastFmRegionPreservesMigratedSpotifyMarket() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsManager(context)
        context.dataStore.edit {
            it.remove(SettingsManager.KEY_ARTIST_SPOTIFY_REGION)
            it[SettingsManager.KEY_ARTIST_IMAGE_REGION] = "zh-hk"
        }
        assertEquals("HK", settings.artistSpotifyRegion.first())
        settings.setArtistImageRegion("ja")
        assertEquals("ja", settings.artistImageRegion.first())
        assertEquals("HK", settings.artistSpotifyRegion.first())
        settings.restoreSettingsJson(org.json.JSONObject().put("artist_image_region", "zh-tw"))
        assertEquals("zh", settings.artistImageRegion.first())
        assertEquals("TW", settings.artistSpotifyRegion.first())
    }
    @Test fun incompatibleValuesDoNotEraseCompatiblePreferences() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsManager.getInstance(context)
        val captions = context.getSharedPreferences("music_video_caption_preferences", 0)
        captions.edit().putFloat("font_size_sp", 28f).putLong("sync_bad", 1000L).apply()
        settings.setPlayerMiniLyricScale(137)
        settings.setPlayerActionMenuLayout("song_info,poster_wall;speed")
        settings.restoreSettingsJson(org.json.JSONObject()
            .put("player_mini_lyric_scale", "invalid")
            .put("player_action_menu_layout", org.json.JSONArray())
            .put("player_mini_lyric_primary_size", 28)
            .put("music_video_caption_settings_json", """{"font_size_sp":"invalid","bold":false,"sync_bad":1.5}"""))
        assertEquals(137, settings.playerMiniLyricScale.first())
        assertEquals("song_info,poster_wall;speed", settings.playerActionMenuLayout.first())
        assertEquals(28, settings.playerMiniLyricPrimarySize.first())
        assertEquals(28f, captions.getFloat("font_size_sp", 21f), 0f)
        assertEquals(false, captions.getBoolean("bold", true))
        assertEquals(1000L, captions.getLong("sync_bad", 0L))
    }
    @Test fun jsonRestoresActionOrderAndAllMiniLyricValues() = roundTrip(false)
    @Test fun zipRestoresActionOrderAndAllMiniLyricValues() = roundTrip(true)

    private fun roundTrip(zip: Boolean) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsManager.getInstance(context)
        val layout = ActionMenuLayout(ActionMenuIds.playerActionMenuDefaults.reversed(), setOf(ActionMenuIds.SPEED)).serialize()
        settings.setPlayerActionMenuLayout(layout)
        settings.setPlayerMiniLyricScale(137)
        settings.setPlayerMiniLyricPrimarySize(28)
        settings.setPlayerMiniLyricSecondarySize(21)
        settings.setPlayerMiniLyricLineSpacing(24)
        settings.setPlayerMiniLyricTextAlign(2)
        settings.setReplayGainPreampDb(2.35f)
        settings.setAppleMusicLyricsWordLift(false)
        settings.setDesktopLyricWordLift(true)
        settings.setSpotifyCanvasEnabled(true)
        settings.setArtistImageRegion("zh")
        settings.setArtistSpotifyRegion("HK")
        FolderDisplayStore.update(context, FolderDisplaySettings(85, 35))
        UpdateChannelPreferences.setIncludesPrereleases(context, true)
        val captionPreferences = context.getSharedPreferences("music_video_caption_preferences", 0)
        val captionStyle = MusicVideoCaptionStyle(fontSizeSp = 28f, positionY = 0.68f, textColorArgb = 0xFFAABBCC.toInt())
        captionStyle.save(captionPreferences)
        captionPreferences.edit().putBoolean("captions_enabled", true).putLong("sync_abcdef", 2350L).apply()
        context.getSharedPreferences("playback_widget", 0).edit().putBoolean("safe_layout", true).apply()
        val custom = "intent://song/{id}#Intent;scheme=test;end"
        NeteaseLinks.update(context) { it.copy(target = NeteaseLinkTarget.Custom, custom = mapOf(NeteaseLinkKind.SongWiki to custom)) }
        val types = setOf(BackupType.Personalization, BackupType.OnlineSources, BackupType.LibraryAndScan)
        val file = if (zip) buildApplicationBackupZipFile(context, types) else {
            File.createTempFile("settings-backup-", ".json", context.cacheDir).apply {
                writeText(buildApplicationBackupJson(context, types).toString(), Charsets.UTF_8)
            }
        }
        try {
            settings.setPlayerActionMenuLayout("")
            settings.setPlayerMiniLyricScale(100)
            settings.setPlayerMiniLyricPrimarySize(19)
            settings.setPlayerMiniLyricSecondarySize(14)
            settings.setPlayerMiniLyricLineSpacing(7)
            settings.setPlayerMiniLyricTextAlign(0)
            settings.setReplayGainPreampDb(0f)
            settings.setDesktopLyricWordLift(false)
            settings.setSpotifyCanvasEnabled(false)
            settings.setArtistImageRegion("en")
            settings.setArtistSpotifyRegion("US")
            FolderDisplayStore.update(context, FolderDisplaySettings())
            UpdateChannelPreferences.setIncludesPrereleases(context, false)
            captionPreferences.edit().clear().apply()
            context.getSharedPreferences("playback_widget", 0).edit().putBoolean("safe_layout", false).apply()
            NeteaseLinks.update(context) { it.copy(target = NeteaseLinkTarget.Web, custom = emptyMap()) }
            val backup = readApplicationBackupFile(context, file)
            assertEquals("custom", backup.getJSONObject("settings").getString("netease_link_target"))
            restoreApplicationBackup(context, backup, types)
            assertEquals(layout, settings.playerActionMenuLayout.first())
            assertEquals(137, settings.playerMiniLyricScale.first())
            assertEquals(28, settings.playerMiniLyricPrimarySize.first())
            assertEquals(21, settings.playerMiniLyricSecondarySize.first())
            assertEquals(24, settings.playerMiniLyricLineSpacing.first())
            assertEquals(2, settings.playerMiniLyricTextAlign.first())
            assertEquals(2.35f, settings.replayGainPreampDb.first(), 0.001f)
            assertEquals(false, settings.appleMusicLyricsWordLift.first())
            assertEquals(true, settings.desktopLyricWordLift.first())
            assertEquals(true, settings.spotifyCanvasEnabled.first())
            assertEquals("zh", settings.artistImageRegion.first())
            assertEquals("HK", settings.artistSpotifyRegion.first())
            assertEquals(FolderDisplaySettings(85, 35), FolderDisplayStore.get(context).value)
            assertEquals(true, UpdateChannelPreferences.includesPrereleases(context))
            assertEquals(captionStyle, MusicVideoCaptionStyle.load(captionPreferences))
            assertEquals(true, captionPreferences.getBoolean("captions_enabled", false))
            assertEquals(2350L, captionPreferences.getLong("sync_abcdef", 0L))
            assertEquals(true, context.getSharedPreferences("playback_widget", 0).getBoolean("safe_layout", false))
            assertEquals(NeteaseLinkTarget.Custom, NeteaseLinks.current(context).target)
            assertEquals(custom, NeteaseLinks.current(context).custom[NeteaseLinkKind.SongWiki])
        } finally { file.delete() }
    }
}
