package com.ella.music.ui.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.LyricLine
import com.ella.music.data.SettingsManager
import com.ella.music.R
import sh.calvin.reorderable.ReorderableItem
import com.ella.music.data.repository.MusicRepository
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.ella.music.ui.poster.PosterLyricScene
import com.ella.music.ui.poster.PosterLyricStyle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.robolectric.RuntimeEnvironment
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w840dp-h400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KaraokeGlowRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lyricViewportTransitionSoftensOnlyTheConfiguredTopBoundary() {
        val feather = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.size(240.dp, 240.dp).background(Color.Black).testTag("viewport-feather")) {
                androidx.compose.foundation.Canvas(Modifier.size(240.dp, 240.dp).then(
                    if (feather.value) Modifier.miniLyricsEdgeFeather(topEdgeHeight = 16.dp, bottomEdgeHeight = 0.dp)
                    else Modifier
                )) {
                    for (x in 0..20) drawRect(Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset(x * 12.dp.toPx(), 0f),
                        size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
                }
            }
        }
        val plain = compose.onNodeWithTag("viewport-feather").captureToImage().asAndroidBitmap()
        compose.runOnIdle { feather.value = true }
        val softened = compose.onNodeWithTag("viewport-feather").captureToImage().asAndroidBitmap()
        val edgePixels = plain.height * 16 / 240
        var edgeChanges = 0
        for (y in 0 until plain.height) for (x in 0 until plain.width) {
            if (y < edgePixels) {
                if (plain.getPixel(x, y) != softened.getPixel(x, y)) edgeChanges++
            } else {
                assertEquals("The centre and bottom must remain unchanged at $x,$y", plain.getPixel(x, y), softened.getPixel(x, y))
            }
        }
        assertTrue("The top must fade and soften into its background", edgeChanges > 100)
    }

    @Test fun miniLyricRowsRemainSharpRegardlessOfTheirTimelineDistance() {
        val blur = mutableStateOf(true)
        val lines = (0 until 6).map { LyricLine(it * 10000L, "VISIBLE ROW $it", endMs = (it + 1) * 10000L) }
        compose.setContent {
            MiuixTheme {
                Box(Modifier.size(360.dp, 300.dp).background(Color.Black).testTag("mini-sharp-rows")) {
                    // Freeze the frame clock while retaining the playing-row styling.
                    AppleMusicLyricsView(lines, 0, 1000, isPlaying = true, pageVisible = false,
                        showTranslation = false, showPronunciation = false,
                        fontFamily = null, fontWeight = FontWeight.Bold, fontScale = 1f, secondaryFontScale = 1f,
                        primaryTextSizeSp = 24f, secondaryTextSizeSp = 18f,
                        lyricTextAlign = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT,
                        contentColor = Color.White, wordLiftEnabled = false,
                        onLineClick = {}, onLineLongClick = {},
                        topContentPadding = 0.dp, bottomContentPadding = 0.dp, focusOffsetDp = 0.dp,
                        useFocusLeadingPadding = false, lineSpacing = 16.dp,
                        nonCurrentLineBlurEnabled = blur.value, edgeFeatherEnabled = false,
                        userScrollEnabled = false)
                }
            }
        }
        compose.onNodeWithText("VISIBLE ROW 2").assertIsDisplayed()
        val normal = compose.onNodeWithTag("mini-sharp-rows").captureToImage().asAndroidBitmap()
        compose.runOnIdle { blur.value = false }
        val sharp = compose.onNodeWithTag("mini-sharp-rows").captureToImage().asAndroidBitmap()
        assertTrue("Visible mini rows must not acquire distance-based blur", normal.sameAs(sharp))
    }

    @Test fun reorderEdgesScrollBothWaysAtABoundedSpeedAndStopWhenPulledBack() {
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState
        compose.setContent {
            var rows by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf((0 until 60).toList()) }
            listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = 10)
            val reorder = com.ella.music.ui.components.rememberEllaReorderableLazyListState(listState) { from, to ->
                rows = rows.toMutableList().apply { add(to.index, removeAt(from.index)) }
            }
            MiuixTheme {
                androidx.compose.foundation.lazy.LazyColumn(state = listState,
                    modifier = Modifier.size(840.dp, 400.dp).testTag("reorder-area")) {
                    items(count = rows.size, key = { rows[it] }) { index ->
                        ReorderableItem(state = reorder, key = rows[index]) {
                            Box(Modifier.size(840.dp, 60.dp).draggableHandle(
                                dragGestureDetector = com.ella.music.ui.playlist.ImmediateOrLongPressDragGestureDetector)) {
                                top.yukonga.miuix.kmp.basic.Text("Row ${rows[index]}")
                            }
                        }
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("reorder-area").fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithText("Row 12").fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().performTouchInput { moveTo(androidx.compose.ui.geometry.Offset(start.x, bounds.bottom - 6f)) }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        val downIndex = listState.firstVisibleItemIndex
        assertTrue("The lower edge must scroll forward", downIndex > 10)
        assertTrue("Holding an edge must not fling through the list", downIndex < 20)
        compose.onRoot().performTouchInput { moveTo(androidx.compose.ui.geometry.Offset(start.x, bounds.center.y)) }
        compose.mainClock.advanceTimeBy(100)
        val stopped = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        compose.mainClock.advanceTimeBy(400)
        assertEquals("Pulling back into the list must stop edge scrolling", stopped,
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
        compose.onRoot().performTouchInput { moveTo(androidx.compose.ui.geometry.Offset(start.x, bounds.top + 6f)) }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        assertTrue("The upper edge must scroll backward", listState.firstVisibleItemIndex < downIndex)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test fun draggingThePlayingQueueEntryDoesNotRecenterTheListAfterEveryMove() {
        val songs = (0 until 40).map { com.ella.music.data.model.Song(it.toLong(), "Queue $it", "Artist", "Album", 0, 1000, "/$it.flac", "$it.flac") }
        var committedTo = -1
        compose.setContent {
            MiuixTheme {
                PlayerQueueMenu(playlist = songs, currentSongKey = songs.first().playlistIdentityKey(),
                    currentQueueIndexHint = 0, shuffleEnabled = false, repeatMode = 0, queueLocked = false,
                    onCyclePlaybackMode = {}, onToggleQueueLock = {}, onSongClick = {}, onRemoveSong = {},
                    onMoveSong = { _, to -> committedTo = to }, onRandomizeQueue = {}, onAddQueueToPlaylist = {},
                    onClearQueue = {}, modifier = Modifier.size(840.dp, 400.dp))
            }
        }
        val handleCenter = compose.onAllNodesWithText("☰", useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(handleCenter) }
        compose.mainClock.advanceTimeBy(650)
        compose.waitForIdle()
        compose.onRoot().performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, 1f)) }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, 420f), delayMillis = 400) }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertTrue("The current song must have crossed at least one row: $committedTo", committedTo > 0)
        compose.onNodeWithText("Queue 1").assertIsDisplayed()
    }

    @Test fun clockColorDefaultsToCoverAndPersistsAllThreeOptions() {
        val settings = SettingsManager.getInstance(RuntimeEnvironment.getApplication())
        runBlocking {
            settings.setPlayerClockColor(-1)
            assertEquals(SettingsManager.PLAYER_CLOCK_COLOR_COVER, settings.playerClockColor.first())
            for (color in 0..2) {
                settings.setPlayerClockColor(color)
                assertEquals(color, settings.playerClockColor.first())
                val exported = settings.exportSettingsJson()
                settings.setPlayerClockColor(-1)
                settings.restoreSettingsJson(exported)
                assertEquals(color, settings.playerClockColor.first())
            }
        }
        val accent = Color(0xFFFF7733)
        assertEquals(androidx.compose.ui.graphics.lerp(accent, Color.White, 0.45f), landscapeClockTint(accent, 0))
        assertEquals(Color.White, landscapeClockTint(accent, 1))
        assertEquals(Color(0xFFD3D3D3), landscapeClockTint(accent, 2))
    }

    @Test fun reorderMenuKeepsSaveAndResetAndSupportsLongLists() {
        val items = (0 until 40).map { com.ella.music.ui.components.ReorderableSelectionItem("$it", "Action $it") }
        var saved = emptyList<com.ella.music.ui.components.ReorderableSelectionItem>()
        compose.setContent {
            MiuixTheme {
                com.ella.music.ui.components.ReorderableSelectionSheet(show = true, title = "Reorder",
                    items = items, defaultItems = items, onDismissRequest = {}, onSave = { saved = it })
            }
        }
        compose.onNodeWithText("Action 0").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Action 39"))
        compose.onNodeWithText("Action 39").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(R.string.common_save)).performClick()
        assertEquals(40, saved.size)
        assertFalse(saved.last().enabled)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.common_restore)).performClick()
        compose.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(R.string.common_save)).performClick()
        assertEquals(items, saved)
    }

    @Test fun lyricMenuSavesExactThresholdAndSharesGlobalEffectSwitches() {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsManager.getInstance(context)
        runBlocking {
            settings.setAppleMusicLyricsSustainThresholdMs(1053)
            settings.setLyricRainbowEnabled(false)
            settings.setLyricHdrHighlightEnabled(false)
            settings.setLyricSustainMotion(false)
        }
        compose.setContent {
            MiuixTheme {
                LyricActionMenu(showPronunciation = false, showTranslation = false, keepScreenOn = false,
                    lyricFormatAvailability = MusicRepository.LyricFormatAvailability(), preferTtmlLyrics = null,
                    lyricSourceMode = SettingsManager.LYRIC_SOURCE_AUTO, layoutProfile = PlayerLyricLayoutProfile.Compact,
                    fontScale = 1f, secondaryFontScale = 1f, primaryTextSizeSp = 32f, secondaryTextSizeSp = 20f,
                    perspectiveEffect = false, perspectiveYAngle = 0, onTogglePronunciation = {},
                    onToggleTranslation = {}, onToggleKeepScreenOn = {}, onTogglePerspectiveEffect = {},
                    onPerspectiveYAngle = {}, onLyricSourceMode = {}, onLyricFormatPreference = {},
                    onFontScale = {}, onSecondaryFontScale = {}, onPrimaryTextSize = {}, onSecondaryTextSize = {})
            }
        }
        for ((title, flow) in listOf(
            R.string.settings_lyric_rainbow to settings.lyricRainbowEnabled,
            R.string.settings_lyric_hdr_highlight to settings.lyricHdrHighlightEnabled,
            R.string.settings_lyric_sustain_motion to settings.lyricSustainMotion
        )) {
            compose.onNodeWithText(context.getString(title)).performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { flow.first() } }
        }
        compose.onNodeWithText(context.getString(R.string.player_lyrics_sustain_threshold)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("1000")
        compose.onNodeWithText(context.getString(R.string.common_confirm)).performClick()
        compose.waitUntil(5000) { runBlocking { settings.appleMusicLyricsSustainThresholdMs.first() } == 1000 }
        for (minutes in listOf(1, 60, 180, 419)) {
            runBlocking {
                settings.setSleepTimerCustomMinutes(minutes)
                assertEquals(minutes, settings.sleepTimerCustomMinutes.first())
            }
        }
    }

    @Test fun latinWordsWithPunctuationRenderSeparateKanaInTheSharedLyricLine() {
        val lines = punctuatedLatinRubyLines()
        val line = mutableStateOf(lines.first())
        compose.setContent {
            Box(Modifier.size(640.dp, 180.dp).background(Color.Black).testTag("punctuation-ruby")) {
                AppleMusicSingleLyricLine(line = line.value, currentPositionMs = line.value.timeMs,
                    showTranslation = false, showPronunciation = true,
                    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
                    fontScale = 1f, secondaryFontScale = 1f,
                    primaryTextSizeSp = 32f, secondaryTextSizeSp = 20f,
                    lyricTextAlign = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT, contentColor = Color.White,
                    wordLiftEnabled = false, singleLine = false, interactive = false)
            }
        }
        for ((index, kana) in listOf("ライ", "ワイ").withIndex()) {
            compose.runOnIdle { line.value = lines[index] }
            compose.onAllNodesWithText(kana).assertCountEquals(4)
            compose.onAllNodesWithText(kana.repeat(4)).assertCountEquals(0)
            val bounds = compose.onAllNodesWithText(kana).fetchSemanticsNodes().map { it.boundsInRoot }
            assertTrue("Each reading must be positioned separately above its own word",
                bounds.zipWithNext().all { (first, next) -> first.right < next.left })
            val image = compose.onNodeWithTag("punctuation-ruby").captureToImage().asAndroidBitmap()
            val output = File("build/outputs/latin-ruby-punctuation/${if (index == 0) "lie" else "why"}-ruby.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        // A timed Latin reading of a Latin lyric still uses a normal, separate secondary row.
        val latinReading = lines.first().copy(pronunciation = "lie lie lie lie",
            pronunciationWords = lines.first().pronunciationWords.map { it.copy(text = "lie") })
        compose.runOnIdle { line.value = latinReading }
        compose.onNodeWithText("lie lie lie lie").assertExists()
        compose.onAllNodesWithText("lie").assertCountEquals(0)
    }

    @Test fun kanaCentersOnTheWordExcludingItsCommaOrQuestionMark() {
        val below = mutableStateOf(false)
        val punctuation = mutableStateOf(",")
        var expectedHalfWidth = 0f
        val base = TextStyle(fontSize = 32.sp, fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Start)
        compose.setContent {
            expectedHalfWidth = rememberTextMeasurer().measure("Lie", base).size.width / 2f
            Box(Modifier.size(640.dp, 180.dp).background(Color.Black)) {
                TimedLyricText("Lie${punctuation.value} ",
                    listOf(LyricWord("Lie${punctuation.value} ", 16147, 16340)),
                    positionMs = 16147, active = false, style = base, contentColor = Color.White,
                    wordLiftEnabled = false, singleLine = true, pronunciation = "ライ",
                    pronunciationWords = listOf(LyricWord("ライ", 16147, 16291)),
                    rubyStyle = TextStyle(fontSize = 12.sp, color = Color.White), rubyBelow = below.value,
                    modifier = Modifier.testTag("punctuated-word"))
            }
        }
        for (mark in listOf(",", "?", "，", "？")) for (rubyBelow in listOf(false, true)) {
            compose.runOnIdle { punctuation.value = mark; below.value = rubyBelow }
            val origin = compose.onNodeWithTag("punctuated-word").fetchSemanticsNode().boundsInRoot.left
            val reading = compose.onNodeWithText("ライ").fetchSemanticsNode().boundsInRoot
            assertEquals("Edge punctuation must not move the kana away from Lie", origin + expectedHalfWidth,
                reading.center.x, 1.5f)
        }
    }

    @Test fun kanaCentersOnVisibleSoRatherThanItsTrailingSeparator() {
        var expectedHalfWidth = 0f
        val below = mutableStateOf(false)
        val base = TextStyle(fontSize = 32.sp, fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Start)
        compose.setContent {
            expectedHalfWidth = rememberTextMeasurer().measure("So", base).size.width / 2f
            Box(Modifier.size(640.dp, 180.dp).background(Color.Black)) {
                TimedLyricText("So 桜", listOf(LyricWord("So", 77130, 77320), LyricWord(" 桜", 78970, 79880)),
                    positionMs = 78000, active = false, style = base, contentColor = Color.White,
                    wordLiftEnabled = false, singleLine = true, pronunciation = "ソー さくら",
                    pronunciationWords = listOf(LyricWord("ソー", 77130, 77320), LyricWord("さくら", 78970, 79880)),
                    rubyStyle = TextStyle(fontSize = 12.sp, color = Color.White), rubyBelow = below.value,
                    modifier = Modifier.testTag("ruby-line"))
            }
        }
        fun checkAlignment() {
            compose.waitForIdle()
            val origin = compose.onNodeWithTag("ruby-line").fetchSemanticsNode().boundsInRoot.left
            val reading = compose.onNodeWithText("ソー").fetchSemanticsNode().boundsInRoot
            assertEquals("Ruby belongs over So, excluding the trailing space", origin + expectedHalfWidth,
                reading.center.x, 1.5f)
        }
        checkAlignment()
        val image = compose.onNodeWithTag("ruby-line").captureToImage().asAndroidBitmap()
        val output = File("build/outputs/ruby-alignment/so-ruby.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnIdle { below.value = true }
        checkAlignment()
    }

    @Test fun theaterPalettesIgnoreTheNormalPlayersRainbowSetting() {
        val settings = SettingsManager.getInstance(RuntimeEnvironment.getApplication())
        val style = mutableStateOf(PosterLyricStyle.Glow)
        val position = mutableLongStateOf(1000L)
        val time = mutableFloatStateOf(0f)
        val line = LyricLine(0, "MMMM", words = listOf(LyricWord("MMMM", 0, 1200)), endMs = 1200)
        fun rainbow(enabled: Boolean) {
            runBlocking {
                settings.setLyricRainbowEnabled(enabled)
                settings.lyricRainbowEnabled.first { it == enabled }
            }
            compose.waitForIdle()
        }
        compose.setContent {
            Box(Modifier.size(640.dp, 360.dp).testTag("theater-palette")) {
                PosterLyricScene(style.value, listOf(line), 0, position, time, "", false, false)
            }
        }
        for (scene in PosterLyricStyle.entries) {
            compose.runOnIdle { style.value = scene }
            rainbow(false)
            val original = compose.onNodeWithTag("theater-palette").captureToImage().asAndroidBitmap()
            rainbow(true)
            val configured = compose.onNodeWithTag("theater-palette").captureToImage().asAndroidBitmap()
            assertTrue("$scene must retain its own lyric colors", original.sameAs(configured))
            val output = File("build/outputs/poster-wall-preview/theater-${scene.name.lowercase()}.png")
            output.parentFile.mkdirs()
            output.outputStream().use { configured.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        rainbow(false)
    }

    @Test fun goldenGlowWaitsUntilTheRevealActuallyReachesAGlyphStroke() {
        val glow = mutableStateOf(false)
        val position = mutableLongStateOf(40L)
        compose.setContent {
            Box(Modifier.size(240.dp, 100.dp).background(Color.Black).testTag("glow-onset")) {
                TimedLyricText("M", listOf(LyricWord("M", 0, 4000)), 40, true,
                    TextStyle(color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold),
                    Color.White, false, positionClock = position,
                    glowColor = if (glow.value) Color(0xFFFFDF92) else null, glowRadius = 36f)
            }
        }
        compose.waitForIdle()
        val plain = compose.onNodeWithTag("glow-onset").captureToImage().asAndroidBitmap()
        compose.runOnIdle { glow.value = true }
        compose.waitForIdle()
        val onset = compose.onNodeWithTag("glow-onset").captureToImage().asAndroidBitmap()
        assertTrue("A reveal still in the glyph's left bearing must not light its golden outline", plain.sameAs(onset))
        compose.runOnIdle { position.longValue = 2500 }
        compose.waitForIdle()
        val sung = compose.onNodeWithTag("glow-onset").captureToImage().asAndroidBitmap()
        compose.runOnIdle { glow.value = false }
        compose.waitForIdle()
        val sungWithoutGlow = compose.onNodeWithTag("glow-onset").captureToImage().asAndroidBitmap()
        assertFalse("Once the stroke is revealed it must still produce a golden bloom", sung.sameAs(sungWithoutGlow))
    }

    @Test fun aCompletedWordsGoldenHaloDoesNotTintTheNextUnsungGlyph() {
        val glow = mutableStateOf(false)
        val position = mutableLongStateOf(1000)
        var wordWidth = 0
        compose.setContent {
            val style = TextStyle(color = Color.White, fontSize = 42.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
            wordWidth = rememberTextMeasurer().measure("错", style).size.width
            Box(Modifier.size(240.dp, 100.dp).background(Color.Black).testTag("glow-render")) {
                TimedLyricText("错错", listOf(LyricWord("错", 0, 100), LyricWord("错", 2000, 3500)),
                    1000, true, style, Color.White, false, positionClock = position,
                    glowColor = if (glow.value) Color(0xFFFFDF92) else null, glowRadius = 36f)
            }
        }
        compose.waitForIdle()
        val plain = compose.onNodeWithTag("glow-render").captureToImage().asAndroidBitmap()
        compose.runOnIdle { glow.value = true }
        compose.waitForIdle()
        val golden = compose.onNodeWithTag("glow-render").captureToImage().asAndroidBitmap()
        var sungChanges = 0
        var futureChanges = 0
        for (y in 0 until plain.height) for (x in 0 until plain.width) {
            if (plain.getPixel(x, y) != golden.getPixel(x, y)) {
                if (x < wordWidth) sungChanges++ else if (x > wordWidth + 1) futureChanges++
            }
        }
        assertTrue("The sung glyph should retain its golden halo", sungChanges > 50)
        assertEquals("The unsung glyph must keep exactly its neutral dim rendering", 0, futureChanges)
        compose.runOnIdle { position.longValue = 2700 }
        compose.waitForIdle()
        val singing = compose.onNodeWithTag("glow-render").captureToImage().asAndroidBitmap()
        var newlyLit = 0
        for (y in 0 until plain.height) for (x in wordWidth + 2 until plain.width) {
            if (singing.getPixel(x, y) != golden.getPixel(x, y)) newlyLit++
        }
        assertTrue("The next glyph should light up only after its onset", newlyLit > 50)
    }
}
