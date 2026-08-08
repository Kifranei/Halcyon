package com.ella.music.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class NeteaseKeyInfo(
    val raw: String,
    val decodedJson: String = "",
    val musicId: String = "",
    val musicName: String = "",
    val mvId: String = "",
    val translatedNames: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val albumId: String = "",
    val albumName: String = "",
    val artists: List<NeteaseArtist> = emptyList(),
    val comment: String = ""
) {
    val hasDecodedContent: Boolean
        get() = decodedJson.isNotBlank() ||
            musicId.isNotBlank() ||
            mvId.isNotBlank() ||
            translatedNames.any { it.isNotBlank() } ||
            aliases.any { it.isNotBlank() } ||
            albumId.isNotBlank() ||
            artists.any { it.id.isNotBlank() } ||
            comment.isNotBlank()
}

data class NeteaseArtist(
    val id: String,
    val name: String
)

fun decodeNeteaseKey(value: String): NeteaseKeyInfo? {
    val raw = value.trim().takeIf { it.isNotBlank() } ?: return null
    if (!raw.looksLikeNeteaseKeyValue()) return null
    val normalized = raw.extractNeteaseKeyPayload()

    normalized.extractNeteaseSongIdFromPlainText()?.let { id ->
        return NeteaseKeyInfo(raw = raw, musicId = id)
    }

    val decodedJson = when {
        normalized.trimStart().startsWith("{") -> normalized
        else -> decryptNeteaseKeyPayload(normalized).orEmpty()
    }.trim()

    if (decodedJson.isBlank()) return NeteaseKeyInfo(raw = raw)

    val fallbackMvId = decodedJson.extractJsonId("mvid", "mvId", "mv_id", "mvID")
    val fallbackTranslatedNames = decodedJson.extractJsonStringList(
        "transNames",
        "translatedNames",
        "transName"
    )
    return runCatching {
        val json = JSONObject(decodedJson)
        NeteaseKeyInfo(
            raw = raw,
            decodedJson = decodedJson,
            musicId = json.optId("musicId", "songId", "id"),
            musicName = json.optStringCompat("musicName", "songName", "name"),
            mvId = json.optId("mvid", "mvId", "mv_id", "mvID").ifBlank { fallbackMvId },
            translatedNames = json
                .optStringList("transNames", "translatedNames", "transName")
                .ifEmpty { fallbackTranslatedNames },
            aliases = json.optStringList("alias", "aliases", "alia"),
            albumId = json.optAlbumId(),
            albumName = json.optAlbumName(),
            artists = json.optArtists(),
            comment = json.optStringCompat("comment", "description", "desc", "remark", "note", "subtitle", "subTitle")
        )
    }.getOrElse {
        NeteaseKeyInfo(
            raw = raw,
            decodedJson = decodedJson,
            mvId = fallbackMvId,
            translatedNames = fallbackTranslatedNames
        )
    }
}

fun neteaseSongUrl(id: String): String = "https://y.music.163.com/m/song?id=$id"

/** The short NetEase host is the portable share link accepted by system share surfaces. */
fun neteaseShareSongUrl(id: String): String = "https://y.163.com/m/song?id=$id"

fun neteaseMvUrl(id: String): String = "https://y.music.163.com/m/mv?id=$id"

fun neteaseAlbumUrl(id: String): String = "https://y.music.163.com/m/album?id=$id"

fun neteaseArtistUrl(id: String): String = "https://y.music.163.com/m/artist?id=$id"

private fun decryptNeteaseKeyPayload(payload: String): String? {
    return runCatching {
        val encrypted = Base64.getDecoder().decode(payload)
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(NETEASE_KEY_AES_KEY.toByteArray(Charsets.UTF_8), "AES"))
        cipher.doFinal(encrypted)
            .stripPkcs7Padding()
            .toString(Charsets.UTF_8)
            .trim('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
            .jsonObjectSlice()
    }.getOrNull()
}

private fun String.extractNeteaseKeyPayload(): String {
    val text = trim('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
    val prefixIndex = text.indexOf("163 key", ignoreCase = true)
    if (prefixIndex >= 0) {
        val colonIndex = text.indexOf(':', startIndex = prefixIndex)
        if (colonIndex >= 0 && colonIndex + 1 < text.length) {
            return text.substring(colonIndex + 1).trim()
        }
    }
    return text
}

fun String.looksLikeNeteaseKeyValue(): Boolean {
    val text = trim()
    if (text.isBlank()) return false
    if (text.contains('\n') || text.contains('\r')) return false
    if (Regex("""\[[0-9]{1,2}:[0-9]{2}""").containsMatchIn(text)) return false
    if (text.trimStart().startsWith("{") && text.contains("music", ignoreCase = true)) return true
    if (text.contains("163 key", ignoreCase = true)) {
        val payload = text.extractNeteaseKeyPayload()
        if (payload == text || payload.isBlank()) return false
        return payload.extractNeteaseSongIdFromPlainText() != null ||
            payload.trimStart().startsWith("{") ||
            payload.matches(Regex("""[A-Za-z0-9+/=_-]{24,}"""))
    }
    if (text.extractNeteaseSongIdFromPlainText() != null) return true
    if (text.length !in 24..8192) return false
    if (Regex("""[一-龥ぁ-ゟァ-ヿ가-힣]{8,}""").containsMatchIn(text)) return false
    return text.matches(Regex("""[A-Za-z0-9+/=_-]+"""))
}

private fun String.extractNeteaseSongIdFromPlainText(): String? {
    if (matches(Regex("""\d{4,}"""))) return this
    return Regex("""(?:music\.163\.com/(?:#/)?song\?id=|songid[:=]\s*|song_id[:=]\s*|id=)(\d{4,})""", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
}

private fun String.extractJsonId(vararg names: String): String {
    for (name in names) {
        val match = Regex(
            """"${Regex.escape(name)}"\s*:\s*(?:"([^"]+)"|(-?\d+))""",
            RegexOption.IGNORE_CASE
        ).find(this) ?: continue
        val value = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty().trim()
        if (value.isNotBlank() && value != "0") return value
    }
    return ""
}

private fun String.extractJsonStringList(vararg names: String): List<String> {
    for (name in names) {
        val body = Regex(
            """"${Regex.escape(name)}"\s*:\s*\[(.*?)]""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(this)?.groupValues?.getOrNull(1) ?: continue
        val values = Regex(""""((?:\\.|[^"\\])*)"""")
            .findAll(body)
            .map { match ->
                match.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim()
            }
            .filter(String::isNotBlank)
            .toList()
        if (values.isNotEmpty()) return values
    }
    return emptyList()
}

private fun ByteArray.stripPkcs7Padding(): ByteArray {
    if (isEmpty()) return this
    val padding = last().toInt() and 0xff
    if (padding !in 1..16 || padding > size) return this
    val valid = takeLast(padding).all { (it.toInt() and 0xff) == padding }
    return if (valid) copyOf(size - padding) else this
}

private fun String.jsonObjectSlice(): String {
    val start = indexOf('{')
    val end = lastIndexOf('}')
    return if (start >= 0 && end >= start) substring(start, end + 1) else this
}

private fun JSONObject.optId(vararg names: String): String {
    for (name in names) {
        if (!has(name) || isNull(name)) continue
        val value = opt(name)
        val text = when (value) {
            is Number -> value.toLong().toString()
            else -> value?.toString().orEmpty()
        }.trim()
        if (text.isNotBlank() && text != "0") return text
    }
    return ""
}

private fun JSONObject.optStringCompat(vararg names: String): String {
    for (name in names) {
        val text = optString(name).trim()
        if (text.isNotBlank() && text != "null") return text
    }
    return ""
}

private fun JSONObject.optStringList(vararg names: String): List<String> {
    for (name in names) {
        if (!has(name) || isNull(name)) continue
        val value = opt(name)
        val result = runCatching {
            when (value) {
                is JSONArray -> buildList {
                    for (index in 0 until value.length()) {
                        value.optString(index).trim()
                            .takeIf { it.isNotBlank() && it != "null" }
                            ?.let(::add)
                    }
                }
                else -> value?.toString()
                    ?.split('/', ',', ';', '；')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() && it != "null" }
                    .orEmpty()
            }
        }.getOrDefault(emptyList())
        if (result.isNotEmpty()) return result
    }
    return emptyList()
}

private fun JSONObject.optArtists(): List<NeteaseArtist> {
    val candidates = listOf("artist", "artists", "artistsInfo", "ar")
    val pairedIds = optArtistIds()
    val artists = buildList {
        candidates.forEach { key ->
            if (!has(key) || isNull(key)) return@forEach
            when (val value = opt(key)) {
                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        parseArtist(value, index)?.let(::add)
                    }
                }
                is JSONObject -> parseArtist(value)?.let(::add)
                is String -> value.splitNeteaseArtistNames()
                    .forEachIndexed { index, rawName ->
                        val inline = rawName.extractInlineNeteaseArtist()
                        add(
                            NeteaseArtist(
                                id = inline?.id?.takeIf { it.isNotBlank() } ?: pairedIds.getOrNull(index).orEmpty(),
                                name = inline?.name ?: rawName
                            )
                        )
                    }
            }
        }
    }
    if (artists.isNotEmpty()) return artists.distinctBy { "${it.id}|${it.name}" }

    val singleName = optStringCompat("artistName", "artist")
    val names = singleName.splitNeteaseArtistNames().ifEmpty {
        listOf(singleName).filter { it.isNotBlank() }
    }
    val ids = pairedIds.ifEmpty { listOf(optId("artistId")) }
    return names.mapIndexed { index, name ->
        NeteaseArtist(id = ids.getOrNull(index).orEmpty(), name = name)
    }.ifEmpty {
        ids.filter { it.isNotBlank() }.map { id -> NeteaseArtist(id = id, name = "") }
    }.distinctBy { "${it.id}|${it.name}" }
}

private fun JSONObject.optArtistIds(): List<String> {
    val candidates = listOf("artistIds", "artistId", "artistsId", "artistID")
    for (key in candidates) {
        if (!has(key) || isNull(key)) continue
        val value = opt(key)
        val ids = when (value) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    value.opt(index)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "0" }?.let(::add)
                }
            }
            else -> value?.toString()
                ?.split(Regex("""\s*(?:[/,;；|]|&)\s*"""))
                ?.map { it.trim().trim('(', ')') }
                ?.filter { it.isNotBlank() && it != "0" && it != "null" }
                .orEmpty()
        }
        if (ids.isNotEmpty()) return ids
    }
    return emptyList()
}

private fun String.extractInlineNeteaseArtist(): NeteaseArtist? {
    val match = Regex("""^(.+?)\s*[\(（](\d{4,})[\)）]\s*$""").find(trim()) ?: return null
    val name = match.groupValues.getOrNull(1).orEmpty().trim()
    val id = match.groupValues.getOrNull(2).orEmpty().trim()
    return NeteaseArtist(id = id, name = name).takeIf { it.id.isNotBlank() || it.name.isNotBlank() }
}

private fun parseArtist(array: JSONArray, index: Int): NeteaseArtist? {
    val item = array.opt(index) ?: return null
    return when (item) {
        is JSONArray -> NeteaseArtist(
            name = item.optString(0).trim(),
            id = item.opt(1)?.toString().orEmpty().takeUnless { it == "0" }.orEmpty()
        )
        is JSONObject -> NeteaseArtist(
            name = item.optStringCompat("name", "artistName"),
            id = item.optId("id", "artistId")
        )
        else -> null
    }?.takeIf { it.name.isNotBlank() || it.id.isNotBlank() }
}

private fun parseArtist(item: JSONObject): NeteaseArtist? {
    return NeteaseArtist(
        name = item.optStringCompat("name", "artistName"),
        id = item.optId("id", "artistId")
    ).takeIf { it.name.isNotBlank() || it.id.isNotBlank() }
}

private fun String.splitNeteaseArtistNames(): List<String> {
    return trim()
        .replace(Regex("""\s*&\s*/\s*"""), " / ")
        .split(Regex("""\s+[&/,;；]\s+|[,;；]"""))
        .map { it.trim().trim('/', '&') }
        .filter { it.isNotBlank() && it != "null" }
}

private fun JSONObject.optAlbumId(): String {
    optId("albumId", "alId").takeIf { it.isNotBlank() }?.let { return it }
    val album = opt("album")
    return when (album) {
        is JSONArray -> album.opt(1)?.toString().orEmpty().trim().takeUnless { it == "0" }.orEmpty()
        is JSONObject -> album.optId("id", "albumId")
        else -> ""
    }
}

private fun JSONObject.optAlbumName(): String {
    optStringCompat("albumName").takeIf { it.isNotBlank() }?.let { return it }
    val album = opt("album")
    return when (album) {
        is JSONArray -> album.optString(0).trim()
        is JSONObject -> album.optStringCompat("name", "albumName")
        else -> album?.toString().orEmpty().trim().takeUnless { it == "null" }.orEmpty()
    }
}

private const val NETEASE_KEY_AES_KEY = "#14ljk_!\\]&0U<'("
