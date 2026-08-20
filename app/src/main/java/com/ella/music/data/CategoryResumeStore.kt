package com.ella.music.data

import android.content.Context
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey

/**
 * Remembers the last song played inside a specific library category.
 * Continue-play rows look up this map instead of the global play-history,
 * so a playlist does not inherit the last song from an album (or vice versa).
 */
class CategoryResumeStore private constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastSongKey(categoryKey: String): String? =
        prefs.getString(categoryKey, null)?.takeIf { it.isNotBlank() }

    fun record(categoryKey: String, songKey: String) {
        if (categoryKey.isBlank() || songKey.isBlank()) return
        prefs.edit().putString(categoryKey, songKey).apply()
    }

    fun record(categoryKey: String, song: Song) {
        record(categoryKey, song.playlistIdentityKey())
    }

    companion object {
        private const val PREFS = "ella_category_resume"

        @Volatile
        private var instance: CategoryResumeStore? = null

        fun getInstance(context: Context): CategoryResumeStore {
            return instance ?: synchronized(this) {
                instance ?: CategoryResumeStore(context.applicationContext).also { instance = it }
            }
        }
    }
}

internal object CategoryResumeKeys {
    fun album(albumId: Long): String = "album:$albumId"
    fun playlist(playlistId: String): String = "playlist:$playlistId"
    fun folder(path: String): String = "folder:${path.trim()}"
    fun folderPlaylist(playlistId: String): String = "folderPlaylist:$playlistId"
    fun artist(name: String): String = "artist:${name.trim()}"
    fun metadata(type: String, name: String): String = "category:$type:${name.trim()}"
    const val HOME = "home:library"
    const val DASHBOARD = "home:dashboard"
}
