package com.ella.music.data

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class ArtistBiographyMatchRule(val search: String, val alias: String,
    val enabled: Boolean = true, val sources: Set<String> = emptySet(), val updatedAt: Long = 0L)

val artistBiographyProviderNames = linkedMapOf("qq" to "QQ音乐", "kugou" to "酷狗音乐",
    "kuwo" to "酷我音乐", "netease" to "网易云音乐", "lastfm" to "Last.fm", "wikipedia" to "Wikipedia")

fun normalizeArtistBiographyMatchRules(rules: List<ArtistBiographyMatchRule>): List<ArtistBiographyMatchRule> =
    rules.mapNotNull { rule ->
        val search = rule.search.trim(); val alias = rule.alias.trim()
        if (search.isBlank() || alias.isBlank()) null else rule.copy(search = search, alias = alias,
            sources = rule.sources.filter { it in artistBiographyProviderNames }.toSet())
    }.distinctBy { Triple(it.search.lowercase(Locale.ROOT), it.alias, it.sources) }

/** Edited and newly added rules stay first. Untouched entries keep reverse stored order. */
fun List<ArtistBiographyMatchRule>.recentEditOrder(): List<ArtistBiographyMatchRule> =
    withIndex().sortedWith(
        compareByDescending<IndexedValue<ArtistBiographyMatchRule>> { it.value.updatedAt }.thenByDescending { it.index }
    ).map { it.value }

fun encodeArtistBiographyMatchRules(rules: List<ArtistBiographyMatchRule>): String = JSONArray().apply {
    normalizeArtistBiographyMatchRules(rules).forEach { rule ->
        put(JSONObject().put("search", rule.search).put("alias", rule.alias).put("enabled", rule.enabled)
            .put("sources", JSONArray(rule.sources.sorted())).put("updatedAt", rule.updatedAt))
    }
}.toString()

fun decodeArtistBiographyMatchRules(json: String): List<ArtistBiographyMatchRule> = runCatching {
    val array = JSONArray(json)
    normalizeArtistBiographyMatchRules((0 until array.length()).mapNotNull { index ->
        val entry = array.optJSONObject(index) ?: return@mapNotNull null
        val sources = entry.optJSONArray("sources")
        ArtistBiographyMatchRule(entry.optString("search"), entry.optString("alias"), entry.optBoolean("enabled", true),
            if (sources == null) emptySet() else (0 until sources.length()).map { sources.optString(it) }.toSet(),
            entry.optLong("updatedAt", 0L))
    })
}.getOrDefault(emptyList())

/** Exact name rules; aliases never change library tags or apply to another selected provider. */
internal fun artistBiographyMatchCandidates(name: String, source: String,
    rules: List<ArtistBiographyMatchRule>): List<String> = buildList {
    val original = name.trim()
    rules.filter { it.enabled && it.search.equals(original, ignoreCase = true) &&
        (it.sources.isEmpty() || source in it.sources) }.forEach { add(it.alias.trim()) }
    addAll(artistBiographyScriptCandidates(original))
}.filter(String::isNotBlank).distinct()
