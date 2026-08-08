package com.ella.music.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.SystemClock
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.ella.music.MainActivity
import com.ella.music.R
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
import com.ella.music.data.repository.CoverUsage
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.player.PlayerPalette
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 4x1 compact playback widget. */
class PlaybackCompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PlaybackWidgetUpdater.updateCompact(context, ids)
    }
}

/** 4x2 playback widget with artwork, progress and a larger identity block. */
class PlaybackExpandedWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PlaybackWidgetUpdater.updateExpanded(context, ids)
    }
}

internal object PlaybackWidgetUpdater {
    private const val PREFS_NAME = "playback_widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ALBUM = "album"
    private const val KEY_PLAYING = "playing"
    private const val KEY_POSITION = "position"
    private const val KEY_DURATION = "duration"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_MEDIA_KEY = "media_key"
    private const val KEY_ARTWORK_KEY = "artwork_key"
    private const val KEY_BACKGROUND_STYLE_VERSION = "background_style_version"
    private const val KEY_SAFE_LAYOUT = "safe_layout"
    private const val KEY_LYRIC_TEXT = "lyric_text"
    private const val KEY_LYRIC_TRANSLATION = "lyric_translation"
    private const val KEY_LYRIC_SONG_KEY = "lyric_song_key"
    private const val PROGRESS_MAX = 1_000
    private const val PROGRESS_UPDATE_INTERVAL_MS = 5_000L
    private const val LYRIC_UPDATE_INTERVAL_MS = 200L
    private const val ARTWORK_SIZE = 256
    private const val BACKGROUND_WIDTH = 360
    private const val BACKGROUND_HEIGHT = 144
    private const val BACKGROUND_STYLE_VERSION = 4
    private const val ARTWORK_FILE = "playback_cover.png"
    private const val BACKGROUND_FILE = "playback_background.png"

    private data class Snapshot(
        val title: String,
        val artist: String,
        val album: String,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val updatedAtElapsedMs: Long,
        val mediaKey: String
    ) {
        fun effectivePositionMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
            val projected = if (isPlaying) {
                positionMs + (nowElapsedMs - updatedAtElapsedMs).coerceAtLeast(0L)
            } else {
                positionMs
            }
            return projected.coerceIn(0L, durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val artworkRequest = AtomicLong(0L)
    private val bitmapFileLock = Any()
    private var progressJob: Job? = null
    private var lastLyricSignature: String? = null
    private var lastLyricUpdateElapsedMs = 0L

    fun updateFromPlayer(context: Context, player: Player) {
        val appContext = context.applicationContext
        val metadata = player.mediaMetadata
        val mediaItem = player.currentMediaItem
        val song = mediaItem?.toSongFromMediaItemExtras()
        val mediaKey = song?.let {
            listOf(it.id, it.path, it.dateModified, it.fileSize, it.coverUrl).joinToString("|")
        } ?: mediaItem?.mediaId.orEmpty()
        if (loadSnapshot(appContext).mediaKey != mediaKey) {
            clearStoredLyric(appContext)
        }
        val snapshot = Snapshot(
            title = metadata.title?.toString()?.takeIf(String::isNotBlank)
                ?: appContext.getString(R.string.app_name),
            artist = metadata.artist?.toString()?.takeIf(String::isNotBlank)
                ?: metadata.albumArtist?.toString()?.takeIf(String::isNotBlank)
                ?: appContext.getString(R.string.widget_tap_to_play),
            album = metadata.albumTitle?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            updatedAtElapsedMs = SystemClock.elapsedRealtime(),
            mediaKey = mediaKey
        )
        persistSnapshot(appContext, snapshot)
        updateAll(appContext)
        resolveArtwork(
            context = appContext,
            mediaKey = mediaKey,
            artworkData = metadata.artworkData,
            artworkUri = metadata.artworkUri,
            song = song
        )
        updateProgressLoop(appContext, player)
    }

    /**
     * Updates the line shown by the widget.  Word-timed lyrics are rendered with the currently
     * sung word highlighted; line-only lyrics remain readable as a normal single line.
     */
    @Synchronized
    fun updateLyrics(
        context: Context,
        song: Song?,
        line: LyricLine?,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        val appContext = context.applicationContext
        val lyricLine = line
        val primaryText = lyricLine?.text?.takeIf(String::isNotBlank)
            ?: lyricLine?.backgroundText?.takeIf(String::isNotBlank)
        if (song == null || lyricLine == null || primaryText == null) {
            if (prefs(appContext).getString(KEY_LYRIC_TEXT, null).isNullOrBlank()) {
                lastLyricSignature = null
                lastLyricUpdateElapsedMs = 0L
            } else {
                clearLyrics(appContext)
            }
            return
        }

        val songKey = widgetSongKey(song)
        val wordIndex = activeLyricWordIndex(lyricLine, positionMs)
        val translation = lyricLine.translation?.takeIf(String::isNotBlank).orEmpty()
        val preferences = prefs(appContext)
        val signature = listOf(
            songKey,
            lyricLine.timeMs,
            primaryText,
            translation,
            wordIndex,
            isPlaying
        ).joinToString("|")
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val sameLine = signature == lastLyricSignature
        if (sameLine && isPlaying &&
            nowElapsedMs - lastLyricUpdateElapsedMs < LYRIC_UPDATE_INTERVAL_MS
        ) {
            return
        }

        if (preferences.getString(KEY_LYRIC_TEXT, null) != primaryText ||
            preferences.getString(KEY_LYRIC_TRANSLATION, null).orEmpty() != translation ||
            preferences.getString(KEY_LYRIC_SONG_KEY, null) != songKey
        ) {
            preferences.edit()
                .putString(KEY_LYRIC_TEXT, primaryText)
                .putString(KEY_LYRIC_TRANSLATION, translation)
                .putString(KEY_LYRIC_SONG_KEY, songKey)
                .apply()
        }
        lastLyricSignature = signature
        lastLyricUpdateElapsedMs = nowElapsedMs

        if (!hasWidgets(appContext)) return
        val rendered = renderLyric(primaryText, translation, lyricLine.words, wordIndex)
        updateLyricViews(appContext, rendered, visible = true)
    }

    @Synchronized
    fun clearLyrics(context: Context) {
        val appContext = context.applicationContext
        val hadLyric = !prefs(appContext).getString(KEY_LYRIC_TEXT, null).isNullOrBlank()
        clearStoredLyric(appContext)
        lastLyricSignature = null
        lastLyricUpdateElapsedMs = 0L
        if (hadLyric && hasWidgets(appContext)) {
            updateLyricViews(appContext, "", visible = false)
        }
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun setSafeLayout(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        val preferences = prefs(appContext)
        if (preferences.getBoolean(KEY_SAFE_LAYOUT, false) == enabled) return
        preferences.edit().putBoolean(KEY_SAFE_LAYOUT, enabled).apply()
        updateAll(appContext)
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val compactIds = manager.getAppWidgetIds(
            ComponentName(context, PlaybackCompactWidgetProvider::class.java)
        )
        val expandedIds = manager.getAppWidgetIds(
            ComponentName(context, PlaybackExpandedWidgetProvider::class.java)
        )
        val snapshot = loadSnapshot(context)
        val artwork = loadArtwork(context, snapshot.mediaKey)
        val background = loadBackground(context, snapshot.mediaKey)
        updateCompact(context, compactIds, snapshot, artwork, background)
        updateExpanded(context, expandedIds, snapshot, artwork, background)
    }

    fun updateCompact(context: Context, ids: IntArray) {
        val snapshot = loadSnapshot(context)
        updateCompact(
            context,
            ids,
            snapshot,
            loadArtwork(context, snapshot.mediaKey),
            loadBackground(context, snapshot.mediaKey)
        )
    }

    fun updateExpanded(context: Context, ids: IntArray) {
        val snapshot = loadSnapshot(context)
        updateExpanded(
            context,
            ids,
            snapshot,
            loadArtwork(context, snapshot.mediaKey),
            loadBackground(context, snapshot.mediaKey)
        )
    }

    private fun updateCompact(
        context: Context,
        ids: IntArray,
        snapshot: Snapshot,
        artwork: Bitmap?,
        background: Bitmap?
    ) {
        if (ids.isEmpty()) return
        val views = createRemoteViews(
            context = context,
            layoutId = R.layout.widget_playback_compact,
            snapshot = snapshot,
            artwork = artwork,
            background = background,
            expanded = false
        )
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    private fun updateExpanded(
        context: Context,
        ids: IntArray,
        snapshot: Snapshot,
        artwork: Bitmap?,
        background: Bitmap?
    ) {
        if (ids.isEmpty()) return
        val views = createRemoteViews(
            context = context,
            layoutId = expandedLayoutId(context),
            snapshot = snapshot,
            artwork = artwork,
            background = background,
            expanded = true
        )
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    private fun createRemoteViews(
        context: Context,
        layoutId: Int,
        snapshot: Snapshot,
        artwork: Bitmap?,
        background: Bitmap?,
        expanded: Boolean
    ): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
        setTextViewText(R.id.widget_title, snapshot.title)
        setTextViewText(R.id.widget_artist, snapshot.artist)
        setTextViewText(R.id.widget_album, snapshot.album)
        val storedLyric = loadStoredLyric(context, snapshot.mediaKey)
        setTextViewText(
            R.id.widget_lyric,
            storedLyric.displayText
        )
        setViewVisibility(
            R.id.widget_lyric,
            if (storedLyric.displayText.isNotBlank()) View.VISIBLE else View.GONE
        )
        setViewVisibility(
            R.id.widget_album,
            if (expanded && snapshot.album.isNotBlank()) View.VISIBLE else View.GONE
        )
        artwork?.let { setImageViewBitmap(R.id.widget_cover, it) }
        background?.let { setImageViewBitmap(R.id.widget_background_art, it) }
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val position = snapshot.effectivePositionMs(nowElapsedMs)
        val progress = if (snapshot.durationMs > 0L) {
            ((position.toDouble() / snapshot.durationMs) * PROGRESS_MAX)
                .toInt()
                .coerceIn(0, PROGRESS_MAX)
        } else {
            0
        }
        setProgressBar(R.id.widget_progress, PROGRESS_MAX, progress, snapshot.durationMs <= 0L)
        setChronometer(
            R.id.widget_position,
            nowElapsedMs - position,
            null,
            snapshot.isPlaying
        )
        setTextViewText(R.id.widget_duration, snapshot.durationMs.formatWidgetTime())
        setImageViewResource(
            R.id.widget_play_pause,
            if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        setContentDescription(
            R.id.widget_play_pause,
            context.getString(if (snapshot.isPlaying) R.string.common_pause else R.string.common_play)
        )
        setOnClickPendingIntent(R.id.widget_root, mainActivityIntent(context))
        setOnClickPendingIntent(
            R.id.widget_previous,
            serviceIntent(context, PlaybackService.ACTION_WIDGET_PREVIOUS, 1)
        )
        setOnClickPendingIntent(
            R.id.widget_play_pause,
            serviceIntent(context, PlaybackService.ACTION_WIDGET_PLAY_PAUSE, 2)
        )
        setOnClickPendingIntent(
            R.id.widget_next,
            serviceIntent(context, PlaybackService.ACTION_WIDGET_NEXT, 3)
        )
    }

    private fun updateLyricViews(context: Context, text: CharSequence, visible: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        fun partial(layoutId: Int): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.widget_lyric, text)
            setViewVisibility(R.id.widget_lyric, if (visible) View.VISIBLE else View.GONE)
        }
        val compactIds = manager.getAppWidgetIds(
            ComponentName(context, PlaybackCompactWidgetProvider::class.java)
        )
        if (compactIds.isNotEmpty()) {
            manager.partiallyUpdateAppWidget(compactIds, partial(R.layout.widget_playback_compact))
        }
        val expandedIds = manager.getAppWidgetIds(
            ComponentName(context, PlaybackExpandedWidgetProvider::class.java)
        )
        if (expandedIds.isNotEmpty()) {
            manager.partiallyUpdateAppWidget(expandedIds, partial(expandedLayoutId(context)))
        }
    }

    private fun updateProgressLoop(context: Context, player: Player) {
        if (!player.isPlaying || !hasWidgets(context)) {
            stopProgressUpdates()
            return
        }
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive && player.isPlaying && hasWidgets(context)) {
                delay(PROGRESS_UPDATE_INTERVAL_MS)
                if (!player.isPlaying) break
                val existing = loadSnapshot(context)
                persistSnapshot(
                    context,
                    existing.copy(
                        isPlaying = true,
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.coerceAtLeast(0L),
                        updatedAtElapsedMs = SystemClock.elapsedRealtime()
                    )
                )
                updateProgress(context, existing)
            }
            progressJob = null
        }
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.getAppWidgetIds(
            ComponentName(context, PlaybackCompactWidgetProvider::class.java)
        ).isNotEmpty() || manager.getAppWidgetIds(
            ComponentName(context, PlaybackExpandedWidgetProvider::class.java)
        ).isNotEmpty()
    }

    private fun updateProgress(context: Context, snapshot: Snapshot) {
        val manager = AppWidgetManager.getInstance(context)
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val position = snapshot.effectivePositionMs(nowElapsedMs)
        val progress = if (snapshot.durationMs > 0L) {
            ((position.toDouble() / snapshot.durationMs) * PROGRESS_MAX)
                .toInt()
                .coerceIn(0, PROGRESS_MAX)
        } else {
            0
        }
        fun partial(layoutId: Int): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
            setProgressBar(R.id.widget_progress, PROGRESS_MAX, progress, false)
            setChronometer(
                R.id.widget_position,
                nowElapsedMs - position,
                null,
                snapshot.isPlaying
            )
            setTextViewText(R.id.widget_duration, snapshot.durationMs.formatWidgetTime())
        }
        val compactIds = manager.getAppWidgetIds(
            ComponentName(context, PlaybackCompactWidgetProvider::class.java)
        )
        if (compactIds.isNotEmpty()) {
            manager.partiallyUpdateAppWidget(compactIds, partial(R.layout.widget_playback_compact))
        }
        val expandedIds = manager.getAppWidgetIds(
            ComponentName(context, PlaybackExpandedWidgetProvider::class.java)
        )
        if (expandedIds.isNotEmpty()) {
            manager.partiallyUpdateAppWidget(expandedIds, partial(expandedLayoutId(context)))
        }
    }

    private fun expandedLayoutId(context: Context): Int =
        if (prefs(context).getBoolean(KEY_SAFE_LAYOUT, false)) {
            R.layout.widget_playback_expanded_safe
        } else {
            R.layout.widget_playback_expanded
        }

    private fun resolveArtwork(
        context: Context,
        mediaKey: String,
        artworkData: ByteArray?,
        artworkUri: Uri?,
        song: com.ella.music.data.model.Song?
    ) {
        if (mediaKey.isBlank()) return
        val prefs = prefs(context)
        if (prefs.getString(KEY_ARTWORK_KEY, null) == mediaKey &&
            prefs.getInt(KEY_BACKGROUND_STYLE_VERSION, 0) == BACKGROUND_STYLE_VERSION &&
            artworkFile(context).isFile && backgroundFile(context).isFile
        ) {
            return
        }
        val requestId = artworkRequest.incrementAndGet()
        scope.launch {
            val source = withContext(Dispatchers.IO) {
                artworkData
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::decodeArtworkData)
                    ?: song?.let {
                        MusicRepository.getInstance(context).getCoverArtBitmap(
                            it,
                            ARTWORK_SIZE,
                            CoverUsage.Notification
                        )
                    }
                    ?: artworkUri?.let { decodeArtworkUri(context, it) }
            } ?: return@launch
            if (requestId != artworkRequest.get() || loadSnapshot(context).mediaKey != mediaKey) {
                return@launch
            }
            val cover = source.centerCropRounded(ARTWORK_SIZE, 42f)
            val background = source.createWidgetBackground()
            val persisted = withContext(Dispatchers.IO) {
                persistBitmap(artworkFile(context), cover) &&
                    persistBitmap(backgroundFile(context), background)
            }
            if (!persisted) return@launch
            if (requestId == artworkRequest.get() && loadSnapshot(context).mediaKey == mediaKey) {
                prefs.edit()
                    .putString(KEY_ARTWORK_KEY, mediaKey)
                    .putInt(KEY_BACKGROUND_STYLE_VERSION, BACKGROUND_STYLE_VERSION)
                    .apply()
                updateAll(context)
            }
        }
    }

    private fun persistSnapshot(context: Context, snapshot: Snapshot) {
        prefs(context).edit()
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putString(KEY_ALBUM, snapshot.album)
            .putBoolean(KEY_PLAYING, snapshot.isPlaying)
            .putLong(KEY_POSITION, snapshot.positionMs)
            .putLong(KEY_DURATION, snapshot.durationMs)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAtElapsedMs)
            .putString(KEY_MEDIA_KEY, snapshot.mediaKey)
            .apply()
    }

    private data class StoredLyric(
        val text: String,
        val translation: String
    ) {
        val displayText: String
            get() = if (translation.isBlank()) text else "$text\n$translation"
    }

    private fun clearStoredLyric(context: Context) {
        prefs(context).edit()
            .remove(KEY_LYRIC_TEXT)
            .remove(KEY_LYRIC_TRANSLATION)
            .remove(KEY_LYRIC_SONG_KEY)
            .apply()
    }

    private fun loadStoredLyric(context: Context, mediaKey: String): StoredLyric {
        val preferences = prefs(context)
        val lyricSongKey = preferences.getString(KEY_LYRIC_SONG_KEY, null).orEmpty()
        if (mediaKey.isBlank() || lyricSongKey != mediaKey) return StoredLyric("", "")
        return StoredLyric(
            text = preferences.getString(KEY_LYRIC_TEXT, null).orEmpty(),
            translation = preferences.getString(KEY_LYRIC_TRANSLATION, null).orEmpty()
        )
    }

    private fun widgetSongKey(song: Song): String = listOf(
        song.id,
        song.path,
        song.dateModified,
        song.fileSize,
        song.coverUrl
    ).joinToString("|")

    private fun loadSnapshot(context: Context): Snapshot {
        val prefs = prefs(context)
        return Snapshot(
            title = prefs.getString(KEY_TITLE, null)?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.app_name),
            artist = prefs.getString(KEY_ARTIST, null)?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.widget_tap_to_play),
            album = prefs.getString(KEY_ALBUM, null).orEmpty(),
            isPlaying = prefs.getBoolean(KEY_PLAYING, false),
            positionMs = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L),
            durationMs = prefs.getLong(KEY_DURATION, 0L).coerceAtLeast(0L),
            updatedAtElapsedMs = prefs.getLong(KEY_UPDATED_AT, SystemClock.elapsedRealtime()),
            mediaKey = prefs.getString(KEY_MEDIA_KEY, null).orEmpty()
        )
    }

    /** Returns the word being sung at [positionMs], or -1 while between timed words. */
    internal fun activeLyricWordIndex(line: LyricLine, positionMs: Long): Int {
        val index = line.words.indexOfLast { positionMs >= it.startMs }
        if (index < 0) return -1
        return index.takeIf { positionMs < line.words[it].endMs } ?: -1
    }

    private fun renderLyric(
        text: String,
        translation: String,
        words: List<LyricWord>,
        activeWordIndex: Int
    ): CharSequence {
        val displayText = if (translation.isBlank()) text else "$text\n$translation"
        if (words.isEmpty() || activeWordIndex !in words.indices) return displayText

        val rendered = SpannableString(displayText)
        var searchOffset = 0
        words.withIndex().forEach { (index, word) ->
            val range = wordRange(text, word, searchOffset)
                ?: return@forEach
            searchOffset = range.last + 1
            val color = if (index == activeWordIndex) {
                Color.WHITE
            } else {
                Color.argb(185, 255, 255, 255)
            }
            rendered.setSpan(
                ForegroundColorSpan(color),
                range.first,
                range.last + 1,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (index == activeWordIndex) {
                rendered.setSpan(
                    StyleSpan(Typeface.BOLD),
                    range.first,
                    range.last + 1,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return rendered
    }

    /** Returns the inclusive character range for a timed word in the source line. */
    internal fun wordRange(text: String, word: LyricWord, fallbackOffset: Int = 0): IntRange? {
        val raw = word.text.takeIf(String::isNotEmpty) ?: return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val start = text.indexOf(trimmed, fallbackOffset.coerceIn(0, text.length))
            .takeIf { it >= 0 }
            ?: text.indexOf(trimmed)
        if (start < 0) return null
        return start..(start + trimmed.length - 1)
    }

    private fun loadArtwork(context: Context, mediaKey: String): Bitmap? =
        loadPersistedBitmap(context, mediaKey, artworkFile(context))

    private fun loadBackground(context: Context, mediaKey: String): Bitmap? =
        loadPersistedBitmap(context, mediaKey, backgroundFile(context))

    private fun loadPersistedBitmap(context: Context, mediaKey: String, file: File): Bitmap? {
        if (mediaKey.isBlank() || prefs(context).getString(KEY_ARTWORK_KEY, null) != mediaKey) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun persistBitmap(target: File, bitmap: Bitmap): Boolean = synchronized(bitmapFileLock) {
        val directory = target.parentFile ?: return@synchronized false
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "Cannot create widget artwork directory: ${directory.absolutePath}")
            return@synchronized false
        }
        var temporary: File? = null
        try {
            temporary = File.createTempFile("${target.name}.", ".tmp", directory)
            temporary.outputStream().buffered().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Bitmap compression failed for ${target.name}")
                }
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            true
        } catch (error: Exception) {
            Log.w(TAG, "Cannot persist widget artwork: ${target.name}", error)
            false
        } finally {
            temporary?.takeIf(File::exists)?.delete()
        }
    }

    private fun decodeArtworkData(data: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = bounds.widgetArtworkSampleSize()
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }.getOrNull()

    private fun decodeArtworkUri(context: Context, uri: Uri): Bitmap? = runCatching {
        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            // Remote song artwork is resolved through MusicRepository above, which uses the
            // app's shared HTTP client and caches instead of opening a second raw connection.
            null
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = bounds.widgetArtworkSampleSize()
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                )
            }
        }
    }.getOrNull()

    private fun BitmapFactory.Options.widgetArtworkSampleSize(): Int {
        var sample = 1
        while (outWidth / sample > ARTWORK_SIZE * 2 || outHeight / sample > ARTWORK_SIZE * 2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun Bitmap.centerCropRounded(size: Int, radius: Float): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val shader = BitmapShader(this, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = maxOf(size / width.toFloat(), size / height.toFloat())
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (size - width * scale) / 2f,
                (size - height * scale) / 2f
            )
        }
        shader.setLocalMatrix(matrix)
        canvas.drawRoundRect(
            RectF(0f, 0f, size.toFloat(), size.toFloat()),
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        )
        return output
    }

    private fun Bitmap.createWidgetBackground(): Bitmap {
        val palette = PlayerPalette.fromCoverBackground(this)
        val output = Bitmap.createBitmap(BACKGROUND_WIDTH, BACKGROUND_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawRect(
            0f,
            0f,
            BACKGROUND_WIDTH.toFloat(),
            BACKGROUND_HEIGHT.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    BACKGROUND_WIDTH.toFloat(),
                    BACKGROUND_HEIGHT.toFloat(),
                    intArrayOf(
                        palette.top.toArgb(),
                        palette.middle.toArgb(),
                        palette.bottom.toArgb()
                    ),
                    floatArrayOf(0f, 0.56f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
        return output
    }

    private fun Long.formatWidgetTime(): String {
        val seconds = (coerceAtLeast(0L) / 1_000L)
        val minutes = seconds / 60L
        return "%d:%02d".format(minutes, seconds % 60L)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun artworkFile(context: Context) = File(widgetFilesDir(context), ARTWORK_FILE)

    private fun backgroundFile(context: Context) = File(widgetFilesDir(context), BACKGROUND_FILE)

    private fun widgetFilesDir(context: Context) = File(context.filesDir, "playback_widget")

    private fun mainActivityIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private const val TAG = "PlaybackWidget"
}
