package com.ella.music.data.scanner

import com.ella.music.data.model.Song
import com.ella.music.data.scanner.TwoStageScanCoordinator.needsMetadataPlaceholderRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** #675: a tag-confirmed folder-name album must not be re-enriched on every scan. */
class FolderAlbumRefreshTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val folderAlbumSong = Song(
        id = 1L,
        title = "01",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 180_000L,
        path = "/music/Artist/Album/01.flac",
        fileName = "01.flac",
        fileSize = 1024L,
        dateModified = 1000L
    )

    @Test
    fun unconfirmedFolderAlbumNeedsRefreshOnlyWhenFallbackIsOff() {
        assertTrue(folderAlbumSong.needsMetadataPlaceholderRefresh(folderNameAsAlbumWhenMissing = false))
        assertFalse(folderAlbumSong.needsMetadataPlaceholderRefresh(folderNameAsAlbumWhenMissing = true))
    }

    @Test
    fun tagConfirmedFolderAlbumDoesNotNeedRefresh() {
        assertFalse(
            folderAlbumSong.needsMetadataPlaceholderRefresh(
                folderNameAsAlbumWhenMissing = false,
                folderAlbumConfirmed = true
            )
        )
    }

    @Test
    fun cacheRemembersFolderAlbumConfirmation() {
        val cache = PersistentMetadataCache(File(tempFolder.root, "cache.json"), ConcurrentHashMap())
        cache.put(folderAlbumSong, folderAlbumConfirmed = true)
        assertTrue(cache.isFolderAlbumConfirmed(folderAlbumSong))

        val record = PersistentMetadataCache.CachedMetadata.from(folderAlbumSong, folderAlbumConfirmed = true)
        val reloaded = PersistentMetadataCache.CachedMetadata.fromJson(record.toJson())
        assertTrue(reloaded?.folderAlbumConfirmed == true)

        // Legacy entries (no flag) stay unconfirmed and get one refresh.
        val legacy = PersistentMetadataCache.CachedMetadata.from(folderAlbumSong).toJson()
        assertFalse(PersistentMetadataCache.CachedMetadata.fromJson(legacy)?.folderAlbumConfirmed == true)
    }
}
