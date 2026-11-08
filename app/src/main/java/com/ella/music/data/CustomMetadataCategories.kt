package com.ella.music.data

import com.ella.music.data.model.Song
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Identity follows the tag key, so renaming a category preserves its order and pins.
 * [instance] distinguishes categories that share the same tag key (#751): the first one
 * keeps the plain `tag_<hex>` type and each extra copy appends `_<n>`, so pins, tiles and
 * detail routes still resolve to one stable category.
 */
data class CustomMetadataCategory(
    val name: String,
    val tagKey: String,
    val listStyle: Boolean = true,
    val albumTab: Boolean = false,
    val updatedAt: Long = 0L,
    /** 分类合集 (#744): only the merged items are listed; each owns a set of raw tag values. */
    val collectionMode: Boolean = false,
    val collectionNames: List<String> = emptyList(),
    val collectionMembers: List<String> = emptyList(),
    /** #749: person/genre keys may opt out of the global protect/separator rules. */
    val applySeparators: Boolean = true,
    /** #749: ALBUM keys treat songs as one album when every selected field matches. */
    val albumMergeFields: List<String> = DEFAULT_ALBUM_MERGE_FIELDS,
    val instance: Int = 0
) {
    val type: String get() = "tag_" + tagKey.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) } +
        (if (instance > 0) "_$instance" else "")

    companion object {
        const val MERGE_ALBUM = "ALBUM"
        const val MERGE_ALBUM_ARTIST = "ALBUMARTIST"
        const val MERGE_ARTIST = "ARTIST"
        const val MERGE_COMPOSER = "COMPOSER"
        const val MERGE_DATE = "DATE"
        val ALBUM_MERGE_FIELD_ORDER = listOf(MERGE_ALBUM, MERGE_ALBUM_ARTIST, MERGE_ARTIST, MERGE_COMPOSER, MERGE_DATE)
        val DEFAULT_ALBUM_MERGE_FIELDS = listOf(MERGE_ALBUM, MERGE_ALBUM_ARTIST)
    }
}

private val ARTIST_SPLIT_KEYS = setOf("ARTIST", "ALBUMARTIST", "ALBUM ARTIST", "COMPOSER", "ARRANGER", "LYRICIST",
    "PRODUCER", "SONGWRITER", "SONGWRITERS")

/** Whether the edit sheet should offer the 「应用自定义分隔符」 switch for [tagKey]. */
fun customCategoryUsesSeparators(tagKey: String): Boolean =
    tagKey.trim().uppercase(Locale.ROOT).let { it in ARTIST_SPLIT_KEYS || it == "GENRE" }

fun customCategoryIsAlbum(tagKey: String): Boolean = tagKey.trim().uppercase(Locale.ROOT) == "ALBUM"

private val COLLECTION_MEMBER_SEPARATOR = Regex("[、,，;；]")

/** Merged item names, each with its raw member values (lines pair up by index). */
fun CustomMetadataCategory.collectionGroups(): List<Pair<String, List<String>>> =
    collectionNames.mapIndexedNotNull { index, rawName ->
        val name = rawName.trim().takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
        val members = collectionMembers.getOrNull(index).orEmpty()
            .split(COLLECTION_MEMBER_SEPARATOR).map(String::trim).filter(String::isNotEmpty)
        name to members
    }

/** Runtime lookup so song grouping can follow per-category options without threading them through. */
object CustomCategoryConfigStore {
    private class Runtime(val category: CustomMetadataCategory) {
        val groupsByMember: Map<String, List<String>> = buildMap<String, MutableList<String>> {
            category.collectionGroups().forEach { (name, members) ->
                (members + name).forEach { member ->
                    val list = getOrPut(member.lowercase(Locale.ROOT)) { mutableListOf() }
                    if (name !in list) list += name
                }
            }
        }
    }

    @Volatile
    private var byType: Map<String, Runtime> = emptyMap()

    fun update(items: List<CustomMetadataCategory>) {
        byType = items.associate { it.type to Runtime(it) }
        NameSplitConfigStore.notifyChanged()
    }

    fun category(type: String): CustomMetadataCategory? = byType[type]?.category

    internal fun collectionNamesFor(type: String, value: String): List<String> =
        byType[type]?.groupsByMember?.get(value.trim().lowercase(Locale.ROOT)).orEmpty()
}

fun customCategoryTagKey(type: String): String? {
    if (!type.startsWith("tag_")) return null
    return runCatching {
        // Duplicate-key categories carry an `_<instance>` suffix; hex never contains '_'.
        val hex = type.removePrefix("tag_").substringBefore('_')
        require(hex.isNotEmpty() && hex.length % 2 == 0)
        String(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), Charsets.UTF_8)
            .takeIf { it.isNotBlank() && it.none(Char::isISOControl) }
    }.getOrNull()
}

internal fun normalizeCustomCategories(items: List<CustomMetadataCategory>): List<CustomMetadataCategory> =
    items.mapNotNull { item ->
        val name = item.name.trim()
        val key = item.tagKey.trim().uppercase(Locale.ROOT)
        if (name.isEmpty() || key.isEmpty() || name.any(Char::isISOControl) || key.any(Char::isISOControl)) null
        else item.copy(name = name, tagKey = key,
            albumMergeFields = CustomMetadataCategory.ALBUM_MERGE_FIELD_ORDER.filter {
                it == CustomMetadataCategory.MERGE_ALBUM || it in item.albumMergeFields
            })
    }.distinctBy { it.type }

/** Edited and newly added categories stay first. Untouched entries keep reverse stored order. */
fun List<CustomMetadataCategory>.recentEditOrder(): List<CustomMetadataCategory> =
    withIndex().sortedWith(
        compareByDescending<IndexedValue<CustomMetadataCategory>> { it.value.updatedAt }.thenByDescending { it.index }
    ).map { it.value }

internal fun decodeCustomCategories(value: String): List<CustomMetadataCategory> = runCatching {
    val json = JSONArray(value)
    normalizeCustomCategories((0 until json.length()).mapNotNull { index ->
        val item = json.optJSONObject(index) ?: return@mapNotNull null
        CustomMetadataCategory(
            item.optString("name"),
            item.optString("tagKey"),
            listStyle = item.optBoolean("listStyle", true),
            albumTab = item.optBoolean("albumTab", false),
            updatedAt = item.optLong("updatedAt", 0L),
            collectionMode = item.optBoolean("collectionMode", false),
            collectionNames = item.optJSONArray("collectionNames").toStringList(),
            collectionMembers = item.optJSONArray("collectionMembers").toStringList(),
            applySeparators = item.optBoolean("applySeparators", true),
            albumMergeFields = item.optJSONArray("albumMergeFields")?.toStringList()
                ?: CustomMetadataCategory.DEFAULT_ALBUM_MERGE_FIELDS,
            instance = item.optInt("instance", 0)
        )
    })
}.getOrDefault(emptyList())

private fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

internal fun encodeCustomCategories(items: List<CustomMetadataCategory>): String = JSONArray().apply {
    normalizeCustomCategories(items).forEach { item ->
        put(JSONObject().put("name", item.name).put("tagKey", item.tagKey)
            .put("listStyle", item.listStyle).put("albumTab", item.albumTab).put("updatedAt", item.updatedAt)
            .put("collectionMode", item.collectionMode)
            .put("collectionNames", JSONArray(item.collectionNames))
            .put("collectionMembers", JSONArray(item.collectionMembers))
            .put("applySeparators", item.applySeparators)
            .put("albumMergeFields", JSONArray(item.albumMergeFields))
            .put("instance", item.instance))
    }
}.toString()

/** Use the same matching in counts, cards, detail pages, and batch editing. */
fun Song.customCategoryValues(type: String): List<String> {
    val key = customCategoryTagKey(type) ?: return emptyList()
    val config = CustomCategoryConfigStore.category(type)
    // An untouched category keeps the default field list; treat that as "inherit the global
    // album merge strategy" so the 专辑 block, its detail page and the library albums all group
    // identically (#760). A customized field list still wins over the global one.
    val albumFields = config?.albumMergeFields
        ?.takeIf { it != CustomMetadataCategory.DEFAULT_ALBUM_MERGE_FIELDS }
        ?: NameSplitConfigStore.albumMergeFields
    val values = if (key == "ALBUM") albumMergeValues(albumFields)
        else rawCustomCategoryValues(key, config?.applySeparators ?: true)
    if (config?.collectionMode != true) return values
    return values.flatMap { CustomCategoryConfigStore.collectionNamesFor(type, it) }.distinct()
}

/** Songs are one album when every selected field matches; the item label lists those fields. */
private fun Song.albumMergeValues(fields: List<String>): List<String> {
    val albumName = customTags.entries.firstOrNull { it.key.equals("ALBUM", ignoreCase = true) }
        ?.value?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: album.trim()
    if (albumName.isEmpty()) return emptyList()
    val parts = CustomMetadataCategory.ALBUM_MERGE_FIELD_ORDER.filter { it in fields }.mapNotNull { field ->
        when (field) {
            CustomMetadataCategory.MERGE_ALBUM -> albumName
            CustomMetadataCategory.MERGE_ALBUM_ARTIST -> albumArtist
            CustomMetadataCategory.MERGE_ARTIST -> artist
            CustomMetadataCategory.MERGE_COMPOSER -> composer
            CustomMetadataCategory.MERGE_DATE -> year
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
    }
    return listOf(parts.joinToString(" · "))
}

private fun Song.rawCustomCategoryValues(key: String, applySeparators: Boolean): List<String> {
    val raw = customTags.entries.filter { it.key.equals(key, ignoreCase = true) }.flatMap { it.value }
    val fallback = when (key) {
        "TITLE" -> title
        "ARTIST" -> artist
        "ALBUM" -> album
        "ALBUMARTIST", "ALBUM ARTIST" -> albumArtist
        "COMPOSER" -> composer
        "ARRANGER" -> arranger
        "LYRICIST" -> lyricist
        "PRODUCER" -> producer
        "GENRE" -> genre
        "DATE", "YEAR" -> year
        else -> ""
    }
    val values = raw.ifEmpty { listOf(fallback) }
    return values.flatMap { value ->
        when {
            !applySeparators && customCategoryUsesSeparators(key) -> listOf(value.trim()).filter(String::isNotEmpty)
            key in ARTIST_SPLIT_KEYS -> splitArtistNames(value)
            key == "GENRE" -> splitGenreNames(value)
            else -> value.split(';', '；').map(String::trim).filter(String::isNotEmpty)
        }
    }.distinctBy { it.tagIdentityKey() }
}

/** Lyrics remain in their existing cache rather than being duplicated in every library record. */
internal fun Map<String, List<String>>.libraryCustomTags(): Map<String, List<String>> = filterKeys { key ->
    val normalized = key.uppercase(Locale.ROOT)
    normalized.replace(" ", "") !in setOf("LYRICS", "LYRIC", "SYNCEDLYRICS", "UNSYNCEDLYRICS",
        "TTMLLYRICS", "TTMLLYRIC", "TTML", "USLT", "SYLT", "©LYR", "163KEY", "NETEASEKEY",
        "----:COM.APPLE.ITUNES:LYRICS")
}

fun metadataCategoryEditKey(type: String): String? = when (type) {
    "genre" -> "GENRE"
    "year" -> "DATE"
    "composer" -> "COMPOSER"
    "arranger" -> "ARRANGER"
    "lyricist" -> "LYRICIST"
    "producer" -> "PRODUCER"
    else -> customCategoryTagKey(type)
}

private val BATCH_TAG_PLACEHOLDER = Regex("<([A-Za-z0-9_]+(?: [A-Za-z0-9_]+)?)>")

/** Batch edits accept <TAG> placeholders that copy another tag's value per song. */
fun Song.resolveBatchTagValue(template: String): String =
    BATCH_TAG_PLACEHOLDER.replace(template) { match ->
        batchTagFieldValue(match.groupValues[1].uppercase(Locale.ROOT))
    }

/** Read the editable value a batch [tag] targets (built-ins then custom tags, #725). */
fun Song.batchTagFieldValue(key: String): String = when (key) {
    "TITLE" -> title
    "ARTIST" -> artist
    "ALBUM" -> album
    "ALBUMARTIST", "ALBUM ARTIST" -> albumArtist
    "COMPOSER" -> composer
    "ARRANGER" -> arranger
    "LYRICIST" -> lyricist
    "PRODUCER" -> producer
    "GENRE" -> genre
    "YEAR", "DATE" -> year
    "TRACK", "TRACKNUMBER", "TRACK NUMBER" -> trackNumber.takeIf { it > 0 }?.toString().orEmpty()
    "DISC", "DISCNUMBER", "DISC NUMBER" -> discNumber.takeIf { it > 0 }?.toString().orEmpty()
    "FILENAME", "FILE NAME" -> fileName
    else -> customTags.entries
        .firstOrNull { it.key.equals(key, ignoreCase = true) }
        ?.value?.joinToString("; ").orEmpty()
}
