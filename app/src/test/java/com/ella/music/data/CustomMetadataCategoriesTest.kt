package com.ella.music.data

import android.app.Application
import com.ella.music.data.model.Song
import com.ella.music.data.repository.readLibraryCacheSongs
import com.ella.music.data.repository.writeLibrarySnapshotJson
import com.ella.music.data.scanner.PersistentMetadataCache
import com.ella.music.ui.components.selectMetadataCategoryCoverSong
import com.ella.music.viewmodel.buildMetadataCategoryItems
import com.ella.music.viewmodel.filterSongsForMetadataCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CustomMetadataCategoriesTest {
    private fun song(id: Long, tags: Map<String, List<String>>) = Song(id, "Song $id", "Artist", "Album", id, 1000, "/$id.flac", "$id.flac", customTags = tags, customTagsLoaded = true)

    @Test fun arbitraryKeysAreNormalizedAndNavigationIdentitiesSurviveRenamingAndCsvNormalization() {
        val categories = normalizeCustomCategories(listOf(CustomMetadataCategory(" 编曲家 ", " arranger "),
            CustomMetadataCategory("Duplicate", "ARRANGER"), CustomMetadataCategory("Mood", "MOOD"),
            CustomMetadataCategory("", "EMPTY"), CustomMetadataCategory("Bad", "LINE\nBREAK")))
        assertEquals(listOf("ARRANGER", "MOOD"), categories.map { it.tagKey })
        assertEquals("ARRANGER", customCategoryTagKey(categories.first().type.lowercase()))
        assertEquals(categories.first().type, categories.first().copy(name = "Arrangers").type)
        val unusual = CustomMetadataCategory("Key", "----:COM.APPLE.ITUNES:编曲")
        assertEquals(unusual.tagKey, customCategoryTagKey(unusual.type))
        assertEquals(categories, decodeCustomCategories(encodeCustomCategories(categories)))
        val styled = CustomMetadataCategory("Mood", "MOOD", listStyle = false, albumTab = true, updatedAt = 9L)
        assertEquals(styled, decodeCustomCategories(encodeCustomCategories(listOf(styled))).single())
        val older = CustomMetadataCategory("Older", "OLD")
        assertEquals(listOf("MOOD", "OLD"), listOf(older, styled).recentEditOrder().map { it.tagKey })
        assertEquals(emptyList<CustomMetadataCategory>(), decodeCustomCategories("malformed"))
        assertNull(customCategoryTagKey("tag_xyz"))
        assertNull(customCategoryTagKey("genre"))
    }

    @Test fun rawMultiValueTagsMatchCountsDetailsAndRepresentativeCover() {
        val category = CustomMetadataCategory("Moods", "MOOD")
        val songs = listOf(song(1, mapOf("mood" to listOf("Quiet", "Energetic; Calm"))),
            song(2, mapOf("MOOD" to listOf("Quiet"))), song(3, emptyMap()))
        val items = buildMetadataCategoryItems(songs, category.type)
        assertEquals(setOf("Quiet", "Energetic", "Calm"), items.map { it.name }.toSet())
        assertEquals(2, items.single { it.name == "Quiet" }.songCount)
        assertEquals(listOf(1L, 2L), filterSongsForMetadataCategory(songs, category.type, "Quiet").map { it.id })
        assertEquals(1L, selectMetadataCategoryCoverSong(songs, category.type, "Energetic")?.id)
        assertTrue(songs.last().customCategoryValues(category.type).isEmpty())
    }

    @Test fun standardTagFallbackSupportsExistingLibrariesWithoutRawTags() {
        val s = song(1, emptyMap()).copy(arranger = "Alice", genre = "Rock", year = "2026")
        assertEquals(listOf("Alice"), s.customCategoryValues(CustomMetadataCategory("Arranger", "ARRANGER").type))
        assertEquals(listOf("2026"), s.customCategoryValues(CustomMetadataCategory("Year", "DATE").type))
        assertEquals(listOf("Rock"), s.customCategoryValues(CustomMetadataCategory("Genre", "GENRE").type))
    }

    @Test fun libraryCachePreservesArbitraryTagsAndLoadsOlderRecords() {
        val file = File.createTempFile("custom-tag-cache", ".json", RuntimeEnvironment.getApplication().cacheDir)
        try {
            val original = song(7, mapOf("MOOD" to listOf("夜晚", "Quiet"), "ARRANGER" to listOf("A", "B")))
            // Exercise the changed streaming format; Android AtomicFile itself is unchanged.
            file.bufferedWriter().use { writeLibrarySnapshotJson(it, listOf(original), emptyList()) }
            assertEquals(original, readLibraryCacheSongs(file).single())
            val cached = PersistentMetadataCache.CachedMetadata.from(original)
            val reloaded = PersistentMetadataCache.CachedMetadata.fromJson(cached.toJson())!!
            assertEquals(original.customTags, reloaded.applyTo(original.copy(customTags = emptyMap(), customTagsLoaded = false)).customTags)
            file.writeText("""{"songs":[{"id":1,"title":"Old","path":"/old.mp3"}]}""")
            val old = readLibraryCacheSongs(file).single()
            assertTrue(old.customTags.isEmpty())
            assertFalse(old.customTagsLoaded)
        } finally { file.delete() }
    }
}
