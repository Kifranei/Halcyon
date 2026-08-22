package com.ella.music.player

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.ella.music.data.model.Song

/** Sends ColorOS Live Lyrics Bridge direct-v4 Provider broadcasts from Halcyon itself. */
internal class OPlusExternalLyricSender(context: Context) {
    private val appContext = context.applicationContext
    private val session = OPlusExternalLyricSession()

    fun notifyTrackChanged(song: Song) {
        if (!session.onSong(song.playbackStackKey())) return
        dispatch(song, lyricInfoJson = null, eventType = OPlusExternalLyricProtocol.EVENT_TRACK_CHANGED)
    }

    fun publishLyric(
        song: Song?,
        lyricInfoJson: String?,
        force: Boolean = false
    ) {
        if (song == null) {
            session.clear()
            return
        }
        val songKey = song.playbackStackKey()
        if (session.onSong(songKey)) {
            dispatch(song, lyricInfoJson, OPlusExternalLyricProtocol.EVENT_TRACK_CHANGED)
        }
        val json = lyricInfoJson?.takeIf { it.isNotBlank() } ?: return
        if (!session.shouldSendLyricReady(songKey, json, force)) return
        dispatch(song, json, OPlusExternalLyricProtocol.EVENT_LYRIC_READY)
    }

    fun clear() {
        session.clear()
    }

    private fun dispatch(song: Song, lyricInfoJson: String?, eventType: String) {
        val lyric = lyricInfoJson?.let { OPlusLyricPayload.stringField(it, "lyric") }.orEmpty()
        val rawLyric = lyricInfoJson?.let(OPlusLyricPayload::rawLyric).orEmpty()
        val translationLyric = lyricInfoJson
            ?.let { OPlusLyricPayload.stringField(it, OPlusLyricPayload.TRANSLATION_LYRIC_INFO_KEY) }
            .orEmpty()
        val intent = Intent(OPlusExternalLyricProtocol.ACTION_DIRECT_V4).apply {
            setPackage(OPlusExternalLyricProtocol.SYSTEM_UI_PACKAGE)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            putExtra(OPlusExternalLyricProtocol.EXTRA_PROTOCOL_VERSION, OPlusExternalLyricProtocol.PROTOCOL_VERSION)
            putExtra(OPlusExternalLyricProtocol.EXTRA_SOURCE, OPlusExternalLyricProtocol.SOURCE)
            putExtra(OPlusExternalLyricProtocol.EXTRA_PLAYER_PACKAGE, appContext.packageName)
            putExtra(OPlusExternalLyricProtocol.EXTRA_SENDER_PACKAGE, appContext.packageName)
            putExtra(OPlusExternalLyricProtocol.EXTRA_SENDER_KIND, OPlusExternalLyricProtocol.SENDER_KIND_PROVIDER)
            putExtra(OPlusExternalLyricProtocol.EXTRA_CAPABILITIES, OPlusExternalLyricProtocol.CAPABILITIES)
            putExtra(
                OPlusExternalLyricProtocol.EXTRA_IDENTITY_CONFIDENCE,
                OPlusExternalLyricProtocol.IDENTITY_CONFIDENCE_CURRENT_TRACK
            )
            OPlusExternalLyricProtocol.matchPolicy(song).takeIf { it.isNotBlank() }?.let { policy ->
                putExtra(OPlusExternalLyricProtocol.EXTRA_MATCH_POLICY, policy)
            }
            putExtra(OPlusExternalLyricProtocol.EXTRA_EVENT_TYPE, eventType)
            putExtra(OPlusExternalLyricProtocol.EXTRA_TRACK_GENERATION, session.generation)
            putExtra(
                OPlusExternalLyricProtocol.EXTRA_REQUEST_ID,
                "${session.generation}:$eventType:${SystemClock.elapsedRealtime()}"
            )
            putExtra(OPlusExternalLyricProtocol.EXTRA_MEDIA_ID, OPlusExternalLyricProtocol.mediaId(song))
            putExtra(OPlusExternalLyricProtocol.EXTRA_MEDIA_URI, OPlusExternalLyricProtocol.mediaUri(song))
            putExtra(OPlusExternalLyricProtocol.EXTRA_TRACK_KEY, OPlusExternalLyricProtocol.trackKey(song))
            putExtra(OPlusExternalLyricProtocol.EXTRA_SONG_NAME, song.title)
            putExtra(OPlusExternalLyricProtocol.EXTRA_ARTIST, song.artist)
            putExtra(OPlusExternalLyricProtocol.EXTRA_DURATION, song.duration)
            putExtra(OPlusExternalLyricProtocol.EXTRA_CAPTURED_AT, System.currentTimeMillis())
            if (lyric.isNotBlank()) putExtra(OPlusExternalLyricProtocol.EXTRA_LYRIC, lyric)
            if (rawLyric.isNotBlank()) putExtra(OPlusExternalLyricProtocol.EXTRA_RAW_LYRIC, rawLyric)
            if (translationLyric.isNotBlank()) {
                putExtra(OPlusExternalLyricProtocol.EXTRA_TRANSLATION_LYRIC, translationLyric)
            }
            if (!lyricInfoJson.isNullOrBlank()) {
                putExtra(OPlusExternalLyricProtocol.EXTRA_LYRIC_INFO, lyricInfoJson)
            }
        }
        runCatching {
            appContext.sendBroadcast(intent)
            Log.d(
                TAG,
                "OPlus provider $eventType gen=${session.generation} mediaId=${OPlusExternalLyricProtocol.mediaId(song)}"
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to send ColorOS lyric provider broadcast", error)
        }
    }

    private companion object {
        const val TAG = "PlaybackService"
    }
}
