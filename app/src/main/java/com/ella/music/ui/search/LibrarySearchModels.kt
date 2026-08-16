package com.ella.music.ui.search

import android.content.Context
import androidx.compose.runtime.saveable.Saver
import com.ella.music.R
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.matchesFullTagSearch
import com.ella.music.viewmodel.MetadataCategoryItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap

internal enum class SearchFilter {
    All,
    Songs,
    MusicVideos,
    Artists,
    Albums,
    Playlists,
    Folders,
    Composers,
    Arrangers,
    Lyricists,
    Lyrics,
    Genres,
    Years;

    companion object {
        fun fromRouteType(type: String?): SearchFilter {
            return when (type?.trim()?.lowercase()) {
                null, "", "all" -> All
                "song", "songs" -> Songs
                "mv", "musicvideo", "musicvideos" -> MusicVideos
                "artist", "artists" -> Artists
                "album", "albums" -> Albums
                "playlist", "playlists" -> Playlists
                "folder", "folders" -> Folders
                "composer", "composers" -> Composers
                "arranger", "arrangers" -> Arrangers
                "lyricist", "lyricists" -> Lyricists
                "lyric", "lyrics" -> Lyrics
                "genre", "genres" -> Genres
                "year", "years" -> Years
                "duplicate", "duplicates" -> Songs
                else -> All
            }
        }
    }
}

internal val SearchFilter.acceptsSongResults: Boolean
    get() = this in listOf(SearchFilter.All, SearchFilter.Songs, SearchFilter.MusicVideos, SearchFilter.Lyrics)

internal val SearchFilter.supportsDuplicateFilter: Boolean
    get() = this in listOf(SearchFilter.All, SearchFilter.Songs)

/** Extra song-property filters available alongside duplicate search in All and Songs. */
internal data class LibrarySearchContentFilters(
    val noLyrics: Boolean = false,
    val ttmlLyrics: Boolean = false,
    /** The parent MV filter means local MV OR online MV. */
    val musicVideo: Boolean = false,
    val localMusicVideo: Boolean = false,
    val onlineMusicVideo: Boolean = false,
    val dynamicCover: Boolean = false
) {
    val hasActiveFilter: Boolean
        get() = noLyrics || ttmlLyrics || musicVideo || localMusicVideo || onlineMusicVideo || dynamicCover
}

/**
 * Saver for [SearchFilter] so it can survive process death and — more importantly — be
 * retained via `rememberSaveable` across navigation back-stack pops (e.g. opening an
 * album/playlist detail from search results and pressing back returns to the same tab
 * instead of resetting to "All").
 */
internal val SearchFilterSaver: Saver<SearchFilter, String> =
    Saver(save = { it.name }, restore = { runCatching { SearchFilter.valueOf(it) }.getOrDefault(SearchFilter.All) })

internal data class ArtistSearchResult(
    val artist: Artist,
    val representativeSong: Song?,
    val participatedAlbumCount: Int = artist.albumCount
)

internal data class SongSearchResult(
    val song: Song,
    val lyricSnippet: String? = null,
    val matches: List<SongSearchMatch> = song.directSearchMatches("")
) {
    val primaryLabelRes: Int
        get() = when {
            lyricSnippet != null -> R.string.library_search_lyrics
            matches.isNotEmpty() -> matches.first().labelRes
            else -> R.string.library_search_songs
        }
}

internal data class SongSearchMatch(
    val type: SearchSongMatchType,
    val labelRes: Int,
    val value: String,
    /** Metadata fields retain their real tag name instead of the ambiguous generic “Tag”. */
    val displayLabel: String? = null
)

internal enum class SearchSongMatchType(val storageKey: String, val labelRes: Int) {
    Title("title", R.string.library_search_match_title),
    Artist("artist", R.string.library_search_match_artist),
    Album("album", R.string.library_search_match_album),
    FileName("file_name", R.string.library_search_match_file_name),
    TranslatedName("translated_name", R.string.library_search_match_translated_name),
    Alias("alias", R.string.library_search_match_alias),
    Comment("comment", R.string.library_search_match_comment),
    Tag("tag", R.string.library_search_match_tag),
    Lyricist("lyricist", R.string.library_search_match_lyricist),
    Composer("composer", R.string.library_search_match_composer),
    Arranger("arranger", R.string.library_search_match_arranger),
    AlbumArtist("album_artist", R.string.library_search_match_album_artist),
    Genre("genre", R.string.library_search_match_genre),
    Year("year", R.string.library_search_match_year),
    Lyrics("lyrics", R.string.library_search_lyrics)
}

internal data class SongSearchGroupEntry(
    val result: SongSearchResult,
    val match: SongSearchMatch?,
    val keySuffix: String
)

internal data class LibrarySearchFacetResults(
    val albums: List<Album> = emptyList(),
    val artists: List<ArtistSearchResult> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val categoriesByType: Map<String, List<MetadataCategoryItem>> = emptyMap()
)

internal fun SongSearchResult.toSearchGroupEntries(
    filter: SearchFilter,
    enabledAllSongMatchTypes: Set<String>
): List<Pair<Int, SongSearchGroupEntry>> {
    if (lyricSnippet != null) {
        if (filter == SearchFilter.All && SearchSongMatchType.Lyrics.storageKey !in enabledAllSongMatchTypes) {
            return emptyList()
        }
        return listOf(
            R.string.library_search_lyrics to SongSearchGroupEntry(
                result = this,
                match = null,
                keySuffix = "lyrics:${lyricSnippet.hashCode()}"
            )
        )
    }
    if (filter == SearchFilter.Lyrics) return emptyList()
    if (matches.isEmpty()) {
        return listOf(
            R.string.library_search_songs to SongSearchGroupEntry(
                result = this,
                match = null,
                keySuffix = "song"
            )
        )
    }
    val visibleMatches = if (filter == SearchFilter.All) {
        matches.filter { it.type.storageKey in enabledAllSongMatchTypes }
    } else {
        matches
    }
    return visibleMatches.mapIndexed { index, match ->
        match.labelRes to SongSearchGroupEntry(
            result = this,
            match = match,
            keySuffix = "$index:${match.labelRes}:${match.value.hashCode()}"
        )
    }
}

internal fun Song.directSearchMatches(
    query: String,
    tagInfo: SongTagInfo? = null,
    includeSnapshotTag: Boolean = false
): List<SongSearchMatch> {
    val target = query.trim()
    if (target.isBlank()) return emptyList()
    return buildList {
        addMatch(SearchSongMatchType.Title, title, target)
        addMatch(SearchSongMatchType.Artist, artist, target)
        addMatch(SearchSongMatchType.Album, album, target)
        addMatch(SearchSongMatchType.FileName, fileName, target)
        decodeNeteaseKey(tagInfo?.neteaseKey.orEmpty())?.let { key ->
            key.translatedNames.forEach { translatedName ->
                addMatch(SearchSongMatchType.TranslatedName, translatedName, target)
            }
            key.aliases.forEach { alias -> addMatch(SearchSongMatchType.Alias, alias, target) }
        }
        tagInfo?.displayComment?.let { addMatch(SearchSongMatchType.Comment, it, target) }
        tagInfo?.customTags
            .orEmpty()
            .forEach { (rawKey, values) ->
                val key = rawKey.trim()
                if (key.normalizedSearchTagKey() in SEARCH_LYRIC_TAG_KEYS) return@forEach
                values.forEach { rawValue ->
                    val value = rawValue.trim()
                    if (key.isNotBlank() && value.isNotBlank() && value.contains(target, ignoreCase = true)) {
                        add(SongSearchMatch(SearchSongMatchType.Tag, SearchSongMatchType.Tag.labelRes, value, key))
                    }
                }
            }
        addMatch(SearchSongMatchType.Lyricist, lyricist, target)
        addMatch(SearchSongMatchType.Composer, composer, target)
        addMatch(SearchSongMatchType.Arranger, arranger, target)
        addMatch(SearchSongMatchType.AlbumArtist, albumArtist, target)
        addMatch(SearchSongMatchType.Genre, genre, target)
        addMatch(SearchSongMatchType.Year, year, target)
        if (includeSnapshotTag && isEmpty()) {
            add(SongSearchMatch(SearchSongMatchType.Tag, SearchSongMatchType.Tag.labelRes, target))
        }
    }
}

private val SEARCH_LYRIC_TAG_KEYS = setOf(
    "LYRIC",
    "LYRICS",
    "SYNCEDLYRIC",
    "SYNCEDLYRICS"
)

private fun String.normalizedSearchTagKey(): String =
    uppercase().filter(Char::isLetterOrDigit)

private fun MutableList<SongSearchMatch>.addMatch(type: SearchSongMatchType, value: String, query: String) {
    val trimmed = value.trim()
    if (trimmed.isNotBlank() && trimmed.contains(query, ignoreCase = true)) {
        add(SongSearchMatch(type, type.labelRes, trimmed))
    }
}

internal fun List<LyricLine>.firstMatchingLyricSnippet(query: String): String? {
    return asSequence()
        .flatMap { line ->
            sequenceOf(
                line.text,
                line.translation.orEmpty(),
                line.pronunciation.orEmpty(),
                line.backgroundText.orEmpty(),
                line.backgroundTranslation.orEmpty()
            )
        }
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains(query, ignoreCase = true) }
        .firstOrNull()
        ?.compactSearchSnippet(query)
}

private fun String.compactSearchSnippet(query: String): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    if (normalized.length <= 52) return normalized
    val index = normalized.indexOf(query, ignoreCase = true).coerceAtLeast(0)
    val start = (index - 18).coerceAtLeast(0)
    val end = (index + query.length + 28).coerceAtMost(normalized.length)
    return buildString {
        if (start > 0) append("...")
        append(normalized.substring(start, end))
        if (end < normalized.length) append("...")
    }
}

internal fun Song.searchIdentityKey(): String = "$id|$path"

internal fun Album.matchesLibrarySearch(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        albumArtist.contains(query, ignoreCase = true)

internal fun List<Song>.duplicateTitleAlbumSongs(): List<Song> =
    buildList {
        val firstByKey = HashMap<String, Song>()
        val duplicatesByKey = LinkedHashMap<String, MutableList<Song>>()
        this@duplicateTitleAlbumSongs.forEach { song ->
            val key = "${song.title.trim().lowercase()}|${song.album.trim().lowercase()}"
            val first = firstByKey.putIfAbsent(key, song)
            if (first != null) {
                val duplicates = duplicatesByKey.getOrPut(key) { mutableListOf(first) }
                duplicates += song
            }
        }
        duplicatesByKey.values.forEach(::addAll)
    }
        .sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { it.title.lowercase() }.thenBy { it.artist.lowercase() })

internal fun buildDirectSongSearchResults(
    songs: List<Song>,
    query: String,
    filter: SearchFilter
): List<SongSearchResult> =
    when {
        !filter.acceptsSongResults || filter == SearchFilter.Lyrics -> emptyList()
        query.isBlank() -> songs.map { song ->
            SongSearchResult(song = song, matches = song.directSearchMatches(query))
        }
        else -> songs.asSequence()
            .filter { it.matchesFullTagSearch(query) }
            .map { song ->
                SongSearchResult(song = song, matches = song.directSearchMatches(query))
            }
            .toList()
    }

internal fun loadSearchHistory(context: Context): List<String> =
    context.getSharedPreferences(SEARCH_PREFS, Context.MODE_PRIVATE)
        .getString(SEARCH_HISTORY_KEY, "")
        .orEmpty()
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

internal fun saveSearchHistory(context: Context, query: String): List<String> {
    val next = (listOf(query.trim()) + loadSearchHistory(context))
        .filter { it.isNotBlank() }
        .distinct()
        .take(20)
    saveSearchHistory(context, next)
    return next
}

internal fun saveSearchHistory(context: Context, history: List<String>) {
    context.getSharedPreferences(SEARCH_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(SEARCH_HISTORY_KEY, history.joinToString("\n"))
        .apply()
}

internal fun loadCachedSongSearchResults(
    context: Context,
    songs: List<Song>,
    query: String,
    filter: SearchFilter
): List<SongSearchResult> {
    if (query.isBlank() || filter !in listOf(SearchFilter.All, SearchFilter.Songs)) return emptyList()
    val raw = context.getSharedPreferences(SEARCH_PREFS, Context.MODE_PRIVATE)
        .getString(searchResultCacheKey(query, filter), null)
        ?: return emptyList()
    val byKey = songs.associateBy { it.searchIdentityKey() }
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val song = byKey[item.optString("key")] ?: continue
                val snippet = item.optString("lyricSnippet").takeIf { it.isNotBlank() }
                add(SongSearchResult(song, snippet, song.directSearchMatches(query)))
                if (size >= 80) break
            }
        }
    }.getOrDefault(emptyList())
}

internal fun saveCachedSongSearchResults(
    context: Context,
    query: String,
    filter: SearchFilter,
    results: List<SongSearchResult>
) {
    if (query.isBlank() || filter !in listOf(SearchFilter.All, SearchFilter.Songs) || results.isEmpty()) return
    val array = JSONArray()
    results.take(80).forEach { result ->
        array.put(
            JSONObject()
                .put("key", result.song.searchIdentityKey())
                .put("lyricSnippet", result.lyricSnippet.orEmpty())
        )
    }
    context.getSharedPreferences(SEARCH_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(searchResultCacheKey(query, filter), array.toString())
        .apply()
}

private fun searchResultCacheKey(query: String, filter: SearchFilter): String =
    "results:${filter.name.lowercase()}:${query.trim().lowercase()}"

internal fun librarySearchFacetCacheKey(
    query: String,
    filter: SearchFilter,
    duplicatesOnlyActive: Boolean,
    showAlbumArtists: Boolean,
    categoryTypes: List<String>,
    songs: List<Song>,
    albums: List<Album>,
    playlists: List<UserPlaylist>
): String = buildString {
    append(query.trim().lowercase())
    append('|').append(filter.name)
    append('|').append(duplicatesOnlyActive)
    append('|').append(showAlbumArtists)
    append('|').append(categoryTypes.joinToString(","))
    append('|').append(System.identityHashCode(songs))
    append('|').append(System.identityHashCode(albums))
    append('|').append(System.identityHashCode(playlists))
}

internal fun loadCachedLibrarySearchFacetResults(cacheKey: String): LibrarySearchFacetResults? =
    SearchFacetResultCache.get(cacheKey)

internal fun saveCachedLibrarySearchFacetResults(
    cacheKey: String,
    results: LibrarySearchFacetResults
) {
    SearchFacetResultCache.put(cacheKey, results)
}

private object SearchFacetResultCache {
    private const val MAX_ENTRIES = 12
    private val cache = object : LinkedHashMap<String, LibrarySearchFacetResults>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LibrarySearchFacetResults>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): LibrarySearchFacetResults? = cache[key]

    @Synchronized
    fun put(key: String, results: LibrarySearchFacetResults) {
        cache[key] = results
    }
}

private const val SEARCH_PREFS = "library_search"
private const val SEARCH_HISTORY_KEY = "history"
