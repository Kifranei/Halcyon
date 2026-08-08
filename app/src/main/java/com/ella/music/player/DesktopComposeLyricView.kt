package com.ella.music.player

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.ui.components.loadAndroidTypeface
import com.ella.music.ui.player.AppleMusicSingleLyricLine

/** Compose-backed renderer for the system overlay and status-bar lyric surfaces. */
internal class DesktopComposeLyricView(context: Context) : FrameLayout(context) {
    var windowTouchHandler: ((View, MotionEvent) -> Boolean)? = null

    private val composeLifecycleOwner = DesktopComposeLifecycleOwner()
    private var currentLine by mutableStateOf(
        LyricLine(timeMs = 0L, text = "Halcyon", endMs = 4_000L)
    )
    private var currentPositionMs by mutableLongStateOf(0L)
    private var playbackRunning by mutableStateOf(true)
    private var fontScale by mutableFloatStateOf(1f)
    private var translationScale by mutableFloatStateOf(1.1f)
    private var opacityPercent by mutableIntStateOf(100)
    private var textColor by mutableIntStateOf(AndroidColor.WHITE)
    private var statusBarMode by mutableStateOf(false)
    private var statusBarSecondaryMode by mutableIntStateOf(SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF)
    private var statusBarSecondaryOpacity by mutableIntStateOf(67)
    private var statusBarMergeSecondary by mutableStateOf(false)
    // Keep the two possible status-bar secondary sources independently.  The selected source is
    // resolved during composition, so changing the status-bar preference never leaves the prior
    // line's secondary text attached to the overlay until the next playback tick arrives.
    private var statusBarTranslationText by mutableStateOf("")
    private var statusBarPronunciationText by mutableStateOf("")
    private var statusBarPronunciationWords by mutableStateOf(emptyList<LyricWord>())
    private var statusBarTextAlign by mutableIntStateOf(SettingsManager.DESKTOP_LYRIC_STATUS_ALIGN_LEFT)
    private var statusBarVerticalAlign by mutableIntStateOf(SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_TOP)
    private var lyricFontPath by mutableStateOf("")
    private var lyricFontWeight by mutableIntStateOf(800)
    private var lyricFontItalic by mutableStateOf(false)
    private var wordLiftEnabled by mutableStateOf(true)

    init {
        installLifecycleOwnerOn(this)
        composeLifecycleOwner.start()
        addView(
            ComposeView(context).apply {
                installLifecycleOwnerOn(this)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    DesktopLyricContent()
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    /**
     * WindowManager overlays are not attached to an Activity decor tree. Install the owner on
     * the service-created root before it is attached so Compose never tries to resolve state from
     * a bare [android.widget.LinearLayout]. A WindowManager overlay has no Activity decor tree,
     * so it needs the complete set of owners that Compose normally inherits from an Activity.
     */
    fun installLifecycleOwnerOn(root: View) {
        root.setViewTreeLifecycleOwner(composeLifecycleOwner)
        root.setViewTreeViewModelStoreOwner(composeLifecycleOwner)
        root.setViewTreeSavedStateRegistryOwner(composeLifecycleOwner)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A WindowManager overlay is rooted in the service's LinearLayout rather than an
        // Activity decor view. Compose resolves its recomposer from that root, so setting the
        // owner only on this FrameLayout is not sufficient on every ROM.
        installLifecycleOwnerOn(rootView)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean =
        windowTouchHandler?.invoke(this, event) ?: true

    override fun onDetachedFromWindow() {
        composeLifecycleOwner.destroy()
        super.onDetachedFromWindow()
    }

    fun setPlaybackActive(isPlaying: Boolean) {
        playbackRunning = isPlaying
    }

    fun setStyle(
        fontScale: Float,
        translationScale: Float,
        opacityPercent: Int,
        textColor: Int,
        statusBarMode: Boolean = false,
        statusBarSecondaryMode: Int = SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF,
        statusBarSecondaryOpacity: Int = 67,
        statusBarMergeSecondary: Boolean = false,
        statusBarTextAlign: Int = SettingsManager.DESKTOP_LYRIC_STATUS_ALIGN_LEFT,
        statusBarVerticalAlign: Int = SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_TOP,
        lyricFontPath: String = "",
        lyricFontWeight: Int = 800,
        lyricFontItalic: Boolean = false,
        wordLiftEnabled: Boolean = true
    ) {
        this.fontScale = fontScale.coerceIn(0.8f, 2.2f)
        this.translationScale = translationScale.coerceIn(0.8f, 2.2f)
        this.opacityPercent = opacityPercent.coerceIn(35, 100)
        this.textColor = textColor
        this.statusBarMode = statusBarMode
        this.statusBarSecondaryMode = statusBarSecondaryMode.coerceIn(0, 2)
        this.statusBarSecondaryOpacity = statusBarSecondaryOpacity.coerceIn(20, 100)
        this.statusBarMergeSecondary = statusBarMergeSecondary
        this.statusBarTextAlign = statusBarTextAlign.coerceIn(0, 2)
        this.statusBarVerticalAlign = statusBarVerticalAlign.coerceIn(0, 2)
        this.lyricFontPath = lyricFontPath
        this.lyricFontWeight = lyricFontWeight.coerceIn(100, 900)
        this.lyricFontItalic = lyricFontItalic
        this.wordLiftEnabled = wordLiftEnabled

    }

    fun setLyric(
        text: String,
        pronunciation: String,
        translation: String,
        positionMs: Long,
        lineStartMs: Long,
        lineEndMs: Long?,
        agent: String,
        isTtml: Boolean,
        backgroundText: String,
        backgroundTranslation: String,
        backgroundStartMs: Long?,
        backgroundEndMs: Long?,
        wordTexts: List<String>,
        wordStarts: LongArray,
        wordEnds: LongArray,
        pronunciationWordTexts: List<String>,
        pronunciationWordStarts: LongArray,
        pronunciationWordEnds: LongArray,
        backgroundWordTexts: List<String>,
        backgroundWordStarts: LongArray,
        backgroundWordEnds: LongArray
    ) {
        currentPositionMs = positionMs
        val words = buildLyricWords(wordTexts, wordStarts, wordEnds)
        val pronunciationWords = buildLyricWords(
            pronunciationWordTexts,
            pronunciationWordStarts,
            pronunciationWordEnds
        )
        val backgroundWords = buildLyricWords(
            backgroundWordTexts,
            backgroundWordStarts,
            backgroundWordEnds
        )
        val inferredStart = sequenceOf(
            lineStartMs.takeIf { it >= 0L },
            words.minOfOrNull { it.startMs },
            pronunciationWords.minOfOrNull { it.startMs },
            backgroundStartMs,
            backgroundWords.minOfOrNull { it.startMs },
            positionMs
        ).filterNotNull().first()
        val inferredEnd = sequenceOf(
            lineEndMs,
            words.maxOfOrNull { it.endMs },
            pronunciationWords.maxOfOrNull { it.endMs },
            backgroundEndMs,
            backgroundWords.maxOfOrNull { it.endMs },
            inferredStart + 4_000L
        ).filterNotNull().first().coerceAtLeast(inferredStart + 1L)

        val inferredPronunciation = pronunciation.ifBlank {
            when {
                isLikelyRomanizationSecondary(text, translation) -> translation
                isLikelyRomanizationSecondary(backgroundText.ifBlank { text }, backgroundTranslation) -> {
                    backgroundTranslation
                }
                else -> ""
            }
        }
        val displayTranslation = if (
            pronunciation.isBlank() && isLikelyRomanizationSecondary(text, translation)
        ) "" else translation
        val displayBackgroundTranslation = if (
            pronunciation.isBlank() &&
            isLikelyRomanizationSecondary(backgroundText.ifBlank { text }, backgroundTranslation)
        ) "" else backgroundTranslation

        statusBarTranslationText = displayTranslation.normalizeDesktopStatusBarSecondaryText()
        statusBarPronunciationText = inferredPronunciation.normalizeDesktopStatusBarSecondaryText()
        statusBarPronunciationWords = pronunciationWords

        val mainText = text.ifBlank { backgroundText }.ifBlank { "♪" }
        currentLine = if (statusBarMode) {
            LyricLine(
                timeMs = inferredStart,
                text = mainText,
                words = if (text.isBlank() && backgroundText.isNotBlank()) backgroundWords else words,
                // The status-bar renderer owns its selected secondary line.  Keeping it out of
                // LyricLine prevents Compose from rendering it twice when the merge preference
                // changes while this lyric is still active.
                translation = null,
                pronunciation = null,
                pronunciationWords = emptyList(),
                isTtml = isTtml,
                endMs = inferredEnd
            )
        } else {
            LyricLine(
                timeMs = inferredStart,
                text = text,
                words = words,
                translation = displayTranslation,
                pronunciation = inferredPronunciation,
                pronunciationWords = pronunciationWords,
                agent = agent,
                backgroundText = backgroundText,
                backgroundWords = backgroundWords,
                backgroundTranslation = displayBackgroundTranslation,
                backgroundStartMs = backgroundStartMs,
                backgroundEndMs = backgroundEndMs,
                isTtml = isTtml,
                endMs = inferredEnd
            )
        }
    }

    @Composable
    private fun DesktopLyricContent() {
        val line = currentLine
        val statusBarSecondaryText = when (statusBarSecondaryMode) {
            SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_TRANSLATION -> statusBarTranslationText
            SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_PRONUNCIATION -> statusBarPronunciationText
            else -> ""
        }
        val statusBarSecondaryWords = if (
            statusBarSecondaryMode == SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_PRONUNCIATION
        ) {
            statusBarPronunciationWords
        } else {
            emptyList()
        }
        var smoothPositionMs by remember { mutableLongStateOf(currentPositionMs) }
        LaunchedEffect(currentPositionMs, playbackRunning) {
            val anchorPositionMs = currentPositionMs
            val anchorFrameNs = withFrameNanos { it }
            smoothPositionMs = anchorPositionMs
            while (playbackRunning) {
                val frameNs = withFrameNanos { it }
                smoothPositionMs = anchorPositionMs + ((frameNs - anchorFrameNs) / 1_000_000L)
            }
        }
        val fontFamily = remember(lyricFontPath, lyricFontWeight, lyricFontItalic) {
            FontFamily(
                loadAndroidTypeface(
                    fontPath = lyricFontPath,
                    weight = lyricFontWeight,
                    italic = lyricFontItalic,
                    boldFallback = true
                )
            )
        }
        val effectiveAlign = if (statusBarMode) {
            statusBarTextAlign
        } else {
            SettingsManager.PLAYER_LYRIC_ALIGN_CENTER
        }
        val desktopMaxLineWidth = with(LocalDensity.current) {
            (24f * fontScale).sp.toDp() * DESKTOP_LYRIC_MAX_GLYPHS
        }
        val verticalAlignment = when {
            !statusBarMode -> Alignment.Center
            statusBarVerticalAlign == SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_CENTER -> Alignment.Center
            statusBarVerticalAlign == SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_BOTTOM -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (statusBarMode) 2.dp else 6.dp),
            contentAlignment = verticalAlignment
        ) {
            AppleMusicSingleLyricLine(
                line = line,
                currentPositionMs = smoothPositionMs,
                // In status-bar mode the selected secondary source is rendered by the dedicated
                // status-bar path below.  The normal lyric-line secondary slots stay reserved
                // for the desktop floating-window renderer.
                showTranslation = !statusBarMode,
                showPronunciation = !statusBarMode,
                fontFamily = fontFamily,
                fontWeight = FontWeight(lyricFontWeight),
                fontScale = fontScale,
                secondaryFontScale = translationScale,
                primaryTextSizeSp = if (statusBarMode) 12.5f else 24f,
                secondaryTextSizeSp = if (statusBarMode) 9.5f else 14f,
                lyricTextAlign = effectiveAlign,
                contentColor = Color(textColor).copy(alpha = opacityPercent / 100f),
                wordLiftEnabled = wordLiftEnabled,
                singleLine = statusBarMode,
                inlineStaticSecondaryText = if (statusBarMode) statusBarSecondaryText else "",
                inlineStaticSecondaryWords = if (statusBarMode) statusBarSecondaryWords else emptyList(),
                mergeInlineSecondary = statusBarMode && statusBarMergeSecondary,
                statusBarMarquee = statusBarMode,
                secondaryAlpha = if (statusBarMode) statusBarSecondaryOpacity / 100f else 0.74f,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (statusBarMode) Modifier else Modifier.widthIn(max = desktopMaxLineWidth)
                    )
            )
        }
    }

    private fun buildLyricWords(
        texts: List<String>,
        starts: LongArray,
        ends: LongArray
    ): List<LyricWord> = texts.mapIndexedNotNull { index, text ->
        val start = starts.getOrNull(index) ?: return@mapIndexedNotNull null
        val end = ends.getOrNull(index) ?: return@mapIndexedNotNull null
        if (text.isBlank() || end <= start) return@mapIndexedNotNull null
        LyricWord(text = text, startMs = start, endMs = end)
    }

    private fun isLikelyRomanizationSecondary(primary: String, candidate: String): Boolean {
        val primaryText = primary.takeIf { it.isNotBlank() } ?: return false
        val secondary = candidate.trim().takeIf { it.isNotBlank() } ?: return false
        if (!primaryText.hasCjkKanaOrHangul()) return false
        if (!secondary.any { it.isLatinLetter() }) return false
        if (secondary.hasCjkKanaOrHangul()) return false
        val useful = secondary.filterNot { it.isWhitespace() }
        if (useful.isEmpty()) return false
        val romanChars = useful.count { it.isLatinLetter() || it in "-'.`·・" }
        return romanChars.toFloat() / useful.length >= 0.82f
    }

    private fun String.hasCjkKanaOrHangul(): Boolean = any { char ->
        when (Character.UnicodeBlock.of(char)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES,
            Character.UnicodeBlock.HANGUL_JAMO,
            Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO -> true
            else -> false
        }
    }

    private fun Char.isLatinLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'
    private fun String?.isDuetAgent(): Boolean =
        equals("v1", ignoreCase = true) || equals("v2", ignoreCase = true)
}

private class DesktopComposeLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val stateController = SavedStateRegistryController.create(this)
    private var destroyed = false

    override val lifecycle: Lifecycle = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = stateController.savedStateRegistry

    init {
        stateController.performAttach()
        stateController.performRestore(null)
    }

    fun start() {
        if (destroyed) return
        if (registry.currentState == Lifecycle.State.INITIALIZED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

internal fun mergeDesktopStatusBarLyric(
    mainText: String,
    secondaryText: String,
    mergeSecondary: Boolean
): String = if (mergeSecondary && secondaryText.isNotBlank()) {
    "${mainText.trimEnd()} ${secondaryText.normalizeDesktopStatusBarSecondaryText()}"
} else {
    mainText
}

/**
 * The lyric parsers normally collapse whitespace, but third-party lyrics can still carry line
 * separators or several secondary fragments.  A status-bar overlay has exactly one visual row,
 * so normalize every fragment before it reaches the inline marquee rather than letting BasicText
 * create an accidental second row.
 */
internal fun String.normalizeDesktopStatusBarSecondaryText(): String =
    trim().replace(Regex("\\s+"), " ")

private const val DESKTOP_LYRIC_MAX_GLYPHS = 8f
