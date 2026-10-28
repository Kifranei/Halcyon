package com.ella.music.player

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.ella.music.MainActivity
import com.ella.music.R
import com.ella.music.data.model.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OriginOsMediaIslandControllerTest {
    @Test fun customCardPreservesWaveColorsAndServiceControls() = withIsland { f ->
        val extras = requireNotNull(f.notification()).extras
        val right = extras.getBundle("notification.superx.island")!!
            .getBundle("island.superx.rightInfo")!!
        // The device's island process crashes when the second color is absent.
        assertEquals(listOf(Color.WHITE, Color.WHITE), right.getIntegerArrayList("island.superx.rightInfo.waveColor"))
        assertEquals(1, right.getInt("island.superx.rightInfo.waveState"))
        assertEquals(7, extras.getInt("notification.superx.template"))
        assertTrue(extras.getBoolean("notification.superx.disableInvertColor"))
        val compact = requireNotNull(extras.getBundle("notification.superx.shortInfos"))
        assertEquals("Song title", compact.getString("notification.superx.shortInfos.describeShort"))
        assertEquals("Artist", compact.getString("notification.superx.shortInfos.coreInfoShort"))
        assertNotNull(compact.getParcelable("notification.superx.shortInfos.image", android.graphics.drawable.Icon::class.java))
        for (island in listOf(false, true)) {
            val card = f.card(island = island)
            assertEquals("Song title", card.findViewById<TextView>(R.id.island_title).text.toString())
            assertEquals("Artist", card.findViewById<TextView>(R.id.island_artist).text.toString())
            val actions = listOf(
                R.id.island_previous to PlaybackService.ACTION_SKIP_PREVIOUS,
                R.id.island_play_pause to PlaybackService.ACTION_PLAY_PAUSE,
                R.id.island_next to PlaybackService.ACTION_SKIP_NEXT
            )
            for ((id, action) in actions) {
                assertTrue(card.findViewById<View>(id).performClick())
                val intent = shadowOf(f.context).nextStartedService
                assertEquals(PlaybackService::class.java.name, intent.component!!.className)
                assertEquals(action, intent.action)
                assertNull(shadowOf(f.context).nextStartedActivity)
            }
        }
    }

    @Test fun headerOpensHomeWithItsOwnImmutableActivityIntent() = withIsland { f ->
        val notification = f.notification()!!
        assertTrue(notification.contentIntent.isImmutable)
        assertEquals(notification.contentIntent, notification.extras.getParcelable("notification.superx.clickResp", PendingIntent::class.java))
        for (island in listOf(false, true)) {
            assertTrue(f.card(island).findViewById<View>(R.id.island_header).performClick())
            val intent = shadowOf(f.context).nextStartedActivity
            assertEquals(MainActivity::class.java.name, intent.component!!.className)
            assertEquals(MainActivity.ACTION_OPEN_HOME_FROM_ISLAND, intent.action)
            assertFalse(intent.hasExtra(MainActivity.EXTRA_OPEN_PLAYER_FROM_NOTIFICATION))
            assertNotEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
            assertNotEquals(0, intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP)
            assertNull(shadowOf(f.context).nextStartedService)
        }
    }

    @Test fun themeSwitchUpdatesCardIconsWithoutChangingNodeOrDarkIslandIcons() = withIsland { f ->
        f.systemTheme(dark = false)
        drain()
        val light = f.notification()!!
        assertEquals(0xFF3E444D.toInt(), iconColor(f.card(false)))
        assertEquals(0xFFDADFE7.toInt(), iconColor(f.card(true)))

        f.systemTheme(dark = true)
        drain()
        val dark = f.notification()!!
        assertNotSame(light, dark)
        assertEquals(light.extras.getInt("notification.superx.newNode"), dark.extras.getInt("notification.superx.newNode"))
        assertEquals(0xFFDADFE7.toInt(), iconColor(f.card(false)))
        assertEquals(0xFFDADFE7.toInt(), iconColor(f.card(true)))

        f.systemTheme(dark = true)
        drain()
        assertSame(dark, f.notification())
        f.systemTheme(dark = false)
        drain()
        assertEquals(0xFF3E444D.toInt(), iconColor(f.card(false)))
    }

    @Test fun themeSwitchDoesNotExtendPauseTimeoutOrResurrectExpiredCard() = withIsland { f ->
        f.systemTheme(dark = false)
        drain()
        f.player.transport(false, Player.STATE_READY)
        drain()
        val node = f.notification()!!.extras.getInt("notification.superx.newNode")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        f.systemTheme(dark = true)
        drain()
        assertEquals(node, f.notification()!!.extras.getInt("notification.superx.newNode"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
        assertNull(f.notification())
        f.systemTheme(dark = false)
        drain()
        assertNull(f.notification())
        f.player.transport(true, Player.STATE_READY)
        drain()
        assertEquals(0xFF3E444D.toInt(), iconColor(f.card(false)))
    }

    @Test fun islandOnlySwitchUpdatesSameNodeAndPreservesForegroundNotification() = withIsland { f ->
        f.manager.createNotificationChannel(NotificationChannel("foreground", "Playback", NotificationManager.IMPORTANCE_LOW))
        val foreground = Notification.Builder(f.context, "foreground")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setGroup(OriginOsMediaIslandController.NOTIFICATION_GROUP).build()
        f.manager.notify(1001, foreground)
        val visible = f.notification()!!
        val node = visible.extras.getInt("notification.superx.newNode")
        assertTrue(visible.extras.getBoolean("notification.superx.showNotify"))
        assertNull(visible.group)
        assertEquals(0, visible.flags and Notification.FLAG_GROUP_SUMMARY)

        f.islandOnly.value = true
        drain()
        val hidden = f.notification()!!
        assertNotSame(visible, hidden)
        assertFalse(hidden.extras.getBoolean("notification.superx.showNotify"))
        assertEquals(foreground.group, hidden.group)
        assertNotEquals(0, hidden.flags and Notification.FLAG_GROUP_SUMMARY)
        assertEquals(0, foreground.flags and Notification.FLAG_GROUP_SUMMARY)
        assertEquals(node, hidden.extras.getInt("notification.superx.newNode"))
        val island = hidden.extras.getBundle("notification.superx.island")!!
        assertEquals(1, island.getBundle("island.superx.rightInfo")!!.getInt("island.superx.rightInfo.waveState"))
        assertNotNull(island.getParcelable("island.superx.customTemplate", RemoteViews::class.java))
        assertSame(foreground, shadowOf(f.manager).getNotification(1001))

        f.islandOnly.value = false
        drain()
        val restored = f.notification()!!
        assertTrue(restored.extras.getBoolean("notification.superx.showNotify"))
        assertNull(restored.group)
        assertEquals(0, restored.flags and Notification.FLAG_GROUP_SUMMARY)
        assertEquals(node, restored.extras.getInt("notification.superx.newNode"))
        assertSame(foreground, shadowOf(f.manager).getNotification(1001))
    }

    @Test fun islandOnlySwitchDoesNotExtendPauseTimeoutOrResurrectExpiredIsland() = withIsland { f ->
        f.player.transport(false, Player.STATE_READY)
        drain()
        val node = f.notification()!!.extras.getInt("notification.superx.newNode")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        f.islandOnly.value = true
        drain()
        assertFalse(f.notification()!!.extras.getBoolean("notification.superx.showNotify"))
        assertEquals(node, f.notification()!!.extras.getInt("notification.superx.newNode"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
        assertNull(f.notification())

        f.islandOnly.value = false
        drain()
        assertNull(f.notification())
        f.islandOnly.value = true
        drain()
        assertNull(f.notification())
        f.player.transport(true, Player.STATE_READY)
        drain()
        assertFalse(f.notification()!!.extras.getBoolean("notification.superx.showNotify"))
    }

    @Test fun restoredIslandOnlySettingAppliesToFirstPublication() = withIsland(initiallyIslandOnly = true) { f ->
        assertFalse(f.notification()!!.extras.getBoolean("notification.superx.showNotify"))
        assertEquals(OriginOsMediaIslandController.NOTIFICATION_GROUP, f.notification()!!.group)
        assertNotEquals(0, f.notification()!!.flags and Notification.FLAG_GROUP_SUMMARY)
        assertNotNull(f.notification()!!.extras.getBundle("notification.superx.island"))
    }

    @Test fun lyricUpdatesDoNotReplaceSongIdentityOrRestartExpiredPause() = withIsland { f ->
        val original = f.notification()
        f.player.patchLyric("First lyric line")
        drain()
        assertSame(original, f.notification())
        assertEquals("Song title", f.notification()!!.extras.getBundle("notification.superx.baseInfos")!!
            .getCharSequence("notification.superx.baseInfos.title"))

        f.player.transport(false, Player.STATE_READY)
        drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        f.player.patchLyric("A new lyric during pause")
        drain()
        assertNotNull(f.notification())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
        assertNull(f.notification())
        f.player.patchLyric("Later metadata must not resurrect the island")
        f.controller.updateArtwork("track", Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
        drain()
        assertNull(f.notification())
        f.player.transport(true, Player.STATE_READY)
        drain()
        assertNotNull(f.notification())
    }

    @Test fun currentLineChangesOnlyExpandedIslandAndKeepsTheSameNode() = withIsland { f ->
        f.manager.createNotificationChannel(NotificationChannel("foreground", "Playback", NotificationManager.IMPORTANCE_LOW))
        val foreground = Notification.Builder(f.context, "foreground")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Song title").build()
        f.manager.notify(1001, foreground)
        val node = f.notification()!!.extras.getInt("notification.superx.newNode")

        f.lyric("纯音乐，请欣赏")
        drain()
        val firstLine = f.notification()!!
        assertEquals("纯音乐，请欣赏", f.card(true).findViewById<TextView>(R.id.island_title).text.toString())
        assertEquals("Song title - Artist", f.card(true).findViewById<TextView>(R.id.island_artist).text.toString())
        assertEquals("Song title", f.card(false).findViewById<TextView>(R.id.island_title).text.toString())
        assertEquals("Artist", f.card(false).findViewById<TextView>(R.id.island_artist).text.toString())
        assertEquals("Song title", firstLine.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Artist", firstLine.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals("Song title", f.player.mediaMetadata.title.toString())
        assertSame(foreground, shadowOf(f.manager).getNotification(1001))

        f.lyric("纯音乐，请欣赏")
        drain()
        assertSame(firstLine, f.notification())
        f.lyric("下一行歌词")
        drain()
        assertNotSame(firstLine, f.notification())
        assertEquals("下一行歌词", f.card(true).findViewById<TextView>(R.id.island_title).text.toString())
        assertEquals(node, f.notification()!!.extras.getInt("notification.superx.newNode"))
        assertSame(foreground, shadowOf(f.manager).getNotification(1001))
    }

    @Test fun missingLyricsRestoreOriginalTwoLines() = withIsland { f ->
        f.lyric("Current line")
        drain()
        f.lyrics.value = null
        drain()
        assertEquals("Song title", f.card(true).findViewById<TextView>(R.id.island_title).text.toString())
        assertEquals("Artist", f.card(true).findViewById<TextView>(R.id.island_artist).text.toString())
    }

    @Test fun switchingTrackRejectsOldSongLyricsWhileWaitingForNewLyrics() = withIsland { f ->
        f.lyric("Old song lyric")
        drain()
        val oldSongKey = f.lyrics.value!!.songKey
        f.player.nextTrack()
        drain()
        val awaitingLyrics = f.notification()
        assertEquals("Next song", f.card(true).findViewById<TextView>(R.id.island_title).text.toString())
        assertEquals("Next artist", f.card(true).findViewById<TextView>(R.id.island_artist).text.toString())

        f.lyrics.value = OriginOsMediaIslandLyric(oldSongKey, "Delayed lyric from old song")
        drain()
        assertSame(awaitingLyrics, f.notification())
        f.lyric("New song lyric")
        drain()
        assertEquals("New song lyric", f.card(true).findViewById<TextView>(R.id.island_title).text.toString())
        assertEquals("Next song - Next artist", f.card(true).findViewById<TextView>(R.id.island_artist).text.toString())
    }

    @Test fun islandLyricsDoNotExtendPauseOrResurrectExpiredCard() = withIsland { f ->
        f.lyric("Playing line")
        drain()
        f.player.transport(false, Player.STATE_READY)
        drain()
        val node = f.notification()!!.extras.getInt("notification.superx.newNode")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        f.lyric("Line after a seek during pause")
        drain()
        assertEquals(node, f.notification()!!.extras.getInt("notification.superx.newNode"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
        assertNull(f.notification())
        f.lyric("Expired line")
        drain()
        assertNull(f.notification())
        f.player.transport(true, Player.STATE_READY)
        drain()
        assertEquals("Expired line", f.card(true).findViewById<TextView>(R.id.island_title).text.toString())
    }

    @Test fun bufferingKeepsCardAndStopOrDisableWithdrawsOnlyIsland() = withIsland { f ->
        f.manager.createNotificationChannel(NotificationChannel("foreground", "Playback", NotificationManager.IMPORTANCE_LOW))
        f.manager.notify(1001, Notification.Builder(f.context, "foreground").setSmallIcon(android.R.drawable.ic_media_play).build())
        f.player.transport(true, Player.STATE_BUFFERING)
        drain()
        assertEquals(0, f.notification()!!.extras.getBundle("notification.superx.island")!!
            .getBundle("island.superx.rightInfo")!!.getInt("island.superx.rightInfo.waveState"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertNotNull(f.notification())
        f.player.transport(true, Player.STATE_ENDED)
        drain()
        assertNull(f.notification())
        f.player.transport(true, Player.STATE_READY)
        drain()
        assertNotNull(f.notification())
        f.enabled.value = false
        f.lyric("Disabled island lyric")
        drain()
        assertNull(f.notification())
        assertNotNull(shadowOf(f.manager).getNotification(1001))
        f.controller.close()
        f.lyric("After controller destruction")
        f.enabled.value = true
        f.player.patchLyric("After service destruction")
        f.systemTheme(dark = true)
        drain()
        assertNull(f.notification())
    }

    @Test fun restoringPausedQueueDoesNotCreateIsland() = withIsland(initiallyPlaying = false) { f ->
        assertNull(f.notification())
        f.player.patchLyric("Restored metadata")
        f.lyric("Paused queue lyric")
        drain()
        assertNull(f.notification())
    }

    @Test fun onlyResumeAndTrackChangeCanReappearAfterUserSwipesIsland() = withIsland { f ->
        fun node() = f.notification()!!.extras.getInt("notification.superx.newNode")
        val first = node()
        f.controller.updateArtwork("track", Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
        f.lyric("Lyric updates must not bypass a swipe dismissal")
        drain()
        assertEquals(first, node())
        f.player.transport(false, Player.STATE_READY)
        drain()
        assertEquals(first, node())
        f.player.transport(true, Player.STATE_READY)
        drain()
        assertTrue(node() > first)
        val resumed = node()
        f.player.nextTrack()
        drain()
        assertTrue(node() > resumed)
    }

    private class Fixture(initiallyPlaying: Boolean, initiallyIslandOnly: Boolean) {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        val enabled = MutableStateFlow(true)
        val islandOnly = MutableStateFlow(initiallyIslandOnly)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val player = TestPlayer(initiallyPlaying)
        val lyrics = MutableStateFlow<OriginOsMediaIslandLyric?>(null)
        val controller = OriginOsMediaIslandController(context, player, enabled, islandOnly, scope, lyrics)
        fun lyric(text: String) {
            lyrics.value = OriginOsMediaIslandLyric(player.currentMediaItem!!.toSongFromMediaItemExtras()!!.playbackStackKey(), text)
        }
        fun notification(): Notification? = shadowOf(manager).getNotification(OriginOsMediaIslandController.NOTIFICATION_ID)
        fun card(island: Boolean): View {
            val extras = notification()!!.extras
            val remote = if (island) extras.getBundle("notification.superx.island")!!
                .getParcelable("island.superx.customTemplate", RemoteViews::class.java)!!
            else extras.getParcelable("notification.superx.customTemplate", RemoteViews::class.java)!!
            return remote.apply(context, FrameLayout(context))
        }
        fun systemTheme(dark: Boolean) {
            val configuration = Configuration(context.resources.configuration)
            configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            context.onConfigurationChanged(configuration)
        }
        fun close() { controller.close(); scope.cancel(); player.release() }
    }

    private fun withIsland(initiallyPlaying: Boolean = true, initiallyIslandOnly: Boolean = false, block: (Fixture) -> Unit) {
        val fixture = Fixture(initiallyPlaying, initiallyIslandOnly)
        try { drain(); block(fixture) } finally { fixture.close() }
    }

    private class TestPlayer(initiallyPlaying: Boolean) : SimpleBasePlayer(Looper.getMainLooper()) {
        private var item = MediaItem.Builder().setMediaId("track").setMediaMetadata(
            MediaMetadata.Builder().setTitle("Song title").setArtist("Artist")
                .setExtras(Song(1, "Song title", "Artist", "Album", 1, 180_000, "/music/song.flac", "song.flac").toMediaItemExtras())
                .build()
        ).build()
        private var snapshot = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(listOf(data())).setCurrentMediaItemIndex(0).setContentPositionMs(0)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(initiallyPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()

        override fun getState(): State = snapshot
        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
        fun transport(playWhenReady: Boolean, state: Int) {
            snapshot = snapshot.buildUpon().setPlaybackState(state)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            invalidateState()
        }
        fun patchLyric(lyric: String) {
            item = item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setTitle(lyric).build()).build()
            snapshot = snapshot.buildUpon().setPlaylist(listOf(data())).build()
            invalidateState()
        }
        fun nextTrack() {
            item = item.buildUpon().setMediaId("next-track").setMediaMetadata(
                MediaMetadata.Builder().setTitle("Next song").setArtist("Next artist")
                    .setExtras(Song(2, "Next song", "Next artist", "Album", 1, 180_000, "/music/next.flac", "next.flac").toMediaItemExtras())
                    .build()
            ).build()
            snapshot = snapshot.buildUpon().setPlaylist(listOf(data())).build()
            invalidateState()
        }
        private fun data(): MediaItemData = MediaItemData.Builder(item.mediaId).setMediaItem(item)
            .setDurationUs(180_000_000).build()
    }

    private companion object {
        fun drain() = shadowOf(Looper.getMainLooper()).idle()
        fun iconColor(card: View): Int {
            val bitmap = (card.findViewById<ImageView>(R.id.island_previous).drawable as BitmapDrawable).bitmap
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return pixels.filter { Color.alpha(it) == 255 }.groupingBy { it }.eachCount().maxBy { it.value }.key
        }
    }
}
