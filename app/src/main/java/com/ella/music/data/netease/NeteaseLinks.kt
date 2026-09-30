package com.ella.music.data.netease

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ella.music.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Destinations reachable from a 163 key / NetEase id. */
internal enum class NeteaseLinkKind(val key: String) {
    Song("song"), Comment("comment"), Artist("artist"), ArtistWiki("artistwiki"), Album("album"), MusicVideo("mv"), AlbumComment("album_comment"), MusicVideoComment("mv_comment")
}

/** Where 163 key links open. Presets follow the prefixes documented by NetEase's web and app schemes. */
internal enum class NeteaseLinkTarget(val id: String, val titleRes: Int) {
    Web("web", R.string.netease_link_target_web),
    App("orpheus", R.string.netease_link_target_app),
    HonorApp("honororpheus", R.string.netease_link_target_honor),
    Custom("custom", R.string.netease_link_target_custom);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Web
    }
}

internal data class NeteaseLinkSettings(
    val target: NeteaseLinkTarget = NeteaseLinkTarget.Web,
    val openMusicVideoExternally: Boolean = false,
    val custom: Map<NeteaseLinkKind, String> = emptyMap(),
    val defaultCommentSort: NeteaseCommentSort = NeteaseCommentSort.Recommend
)

internal object NeteaseLinks {
    private const val PREFS = "netease_links"
    private const val KEY_TARGET = "target"
    private const val KEY_MV_EXTERNAL = "mv_external"
    private const val KEY_COMMENT_SORT = "comment_default_sort"
    private const val ID = "{id}"

    private val webPrefixes = mapOf(
        NeteaseLinkKind.Song to "https://y.music.163.com/m/song?id=",
        NeteaseLinkKind.Comment to "https://music.163.com/#/song?id=",
        NeteaseLinkKind.Artist to "https://y.music.163.com/m/artist?id=",
        NeteaseLinkKind.ArtistWiki to "https://music.163.com/st/artistwiki?artistId=",
        NeteaseLinkKind.Album to "https://y.music.163.com/m/album?id=",
        NeteaseLinkKind.MusicVideo to "https://y.music.163.com/m/mv?id=",
        NeteaseLinkKind.AlbumComment to "https://music.163.com/#/album?id=",
        NeteaseLinkKind.MusicVideoComment to "https://music.163.com/#/mv?id="
    )

    private fun appPrefixes(scheme: String) = mapOf(
        NeteaseLinkKind.Song to "$scheme://song/",
        NeteaseLinkKind.Comment to "$scheme://comment?threadId=R_SO_4_",
        NeteaseLinkKind.Artist to "$scheme://artist/",
        NeteaseLinkKind.ArtistWiki to "$scheme://rnpage?component=music-reactnative-artistwiki&artistId=",
        NeteaseLinkKind.Album to "$scheme://album/",
        NeteaseLinkKind.MusicVideo to "$scheme://mv/",
        NeteaseLinkKind.AlbumComment to "$scheme://comment?threadId=R_AL_3_",
        NeteaseLinkKind.MusicVideoComment to "$scheme://comment?threadId=R_MV_5_"
    )

    fun defaultPrefix(target: NeteaseLinkTarget, kind: NeteaseLinkKind): String = when (target) {
        NeteaseLinkTarget.App -> appPrefixes("orpheus").getValue(kind)
        NeteaseLinkTarget.HonorApp -> appPrefixes("honororpheus").getValue(kind)
        NeteaseLinkTarget.Web, NeteaseLinkTarget.Custom -> webPrefixes.getValue(kind)
    }

    /** `{id}` in a template is replaced; otherwise the id is appended to the prefix. */
    fun build(settings: NeteaseLinkSettings, kind: NeteaseLinkKind, id: String): String? {
        val cleanId = id.trim().takeIf { it.isNotEmpty() } ?: return null
        val template = settings.custom[kind]?.trim()?.takeIf { settings.target == NeteaseLinkTarget.Custom && it.isNotEmpty() }
            ?: defaultPrefix(settings.target, kind)
        return if (ID in template) template.replace(ID, cleanId) else template + cleanId
    }

    fun commentsOpenExternally(context: Context, kind: NeteaseLinkKind = NeteaseLinkKind.Comment): Boolean =
        !usesNativeComments(current(context), kind)

    internal fun usesNativeComments(settings: NeteaseLinkSettings, kind: NeteaseLinkKind): Boolean =
        kind.commentResource() != null && (settings.target == NeteaseLinkTarget.Web ||
            (settings.target == NeteaseLinkTarget.Custom && settings.custom[kind].isNullOrBlank()))

    val commentSheetTarget = MutableStateFlow<NeteaseCommentTarget?>(null)

    val webSheetUrl = MutableStateFlow<String?>(null)
    private fun inAppWebKind(kind: NeteaseLinkKind): Boolean = kind in setOf(
        NeteaseLinkKind.MusicVideo
    )

    /** Web fallback used when the chosen app scheme has no handler on this device. */
    fun webUrl(kind: NeteaseLinkKind, id: String): String? = build(NeteaseLinkSettings(), kind, id)

    private val mutableSettings = MutableStateFlow<NeteaseLinkSettings?>(null)

    fun settings(context: Context): StateFlow<NeteaseLinkSettings?> {
        if (mutableSettings.value == null) mutableSettings.value = read(context)
        return mutableSettings.asStateFlow()
    }

    fun current(context: Context): NeteaseLinkSettings = mutableSettings.value ?: read(context).also { mutableSettings.value = it }

    internal fun read(context: Context): NeteaseLinkSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return NeteaseLinkSettings(
            target = NeteaseLinkTarget.fromId(prefs.getString(KEY_TARGET, null)),
            openMusicVideoExternally = prefs.getBoolean(KEY_MV_EXTERNAL, false),
            defaultCommentSort = NeteaseCommentSort.fromApiValue(prefs.getInt(KEY_COMMENT_SORT, NeteaseCommentSort.Recommend.apiValue))
                ?: NeteaseCommentSort.Recommend,
            custom = NeteaseLinkKind.entries.mapNotNull { kind ->
                prefs.getString("custom_${kind.key}", null)?.takeIf { it.isNotBlank() }?.let { kind to it }
            }.toMap()
        )
    }

    fun update(context: Context, transform: (NeteaseLinkSettings) -> NeteaseLinkSettings) {
        val next = transform(current(context))
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_TARGET, next.target.id)
            putBoolean(KEY_MV_EXTERNAL, next.openMusicVideoExternally)
            putInt(KEY_COMMENT_SORT, next.defaultCommentSort.apiValue)
            NeteaseLinkKind.entries.forEach { kind ->
                val value = next.custom[kind]?.trim().orEmpty()
                if (value.isEmpty()) remove("custom_${kind.key}") else putString("custom_${kind.key}", value)
            }
        }.apply()
        mutableSettings.value = next
    }

    /** Opens [kind]/[id] with the user's link settings, falling back to the web page if no app handles it. */
    fun open(context: Context, kind: NeteaseLinkKind, id: String) {
        val resource = kind.commentResource()
        if (resource != null && (id.toLongOrNull() ?: 0L) <= 0L) return
        if (resource != null && !commentsOpenExternally(context, kind)) {
            commentSheetTarget.value = NeteaseCommentTarget(id, resource)
            return
        }
        val url = build(current(context), kind, id) ?: return
        val openWebInApp = inAppWebKind(kind) &&
            !(kind == NeteaseLinkKind.MusicVideo && current(context).openMusicVideoExternally)
        if (openWebInApp && (url.startsWith("https://") || url.startsWith("http://"))) {
            webSheetUrl.value = url
            return
        }
        if (launch(context, url)) return
        if (resource != null) {
            commentSheetTarget.value = NeteaseCommentTarget(id, resource)
            return
        }
        val fallback = webUrl(kind, id)
        if (fallback != null && fallback != url) {
            if (openWebInApp) {
                webSheetUrl.value = fallback
                return
            }
            if (launch(context, fallback)) return
        }
        Toast.makeText(context, R.string.netease_link_open_failed, Toast.LENGTH_SHORT).show()
    }

    /** Re-targets a y.music.163.com web link produced elsewhere (artist/album resolvers). */
    fun openWebUrl(context: Context, webUrl: String) {
        val parsed = parseWebUrl(webUrl)
        if (parsed == null) {
            if (!launch(context, webUrl)) Toast.makeText(context, R.string.netease_link_open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        open(context, parsed.first, parsed.second)
    }

    fun parseWebUrl(url: String): Pair<NeteaseLinkKind, String>? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.host != "163.com" && !parsed.host.endsWith(".163.com")) return null
        val id = parsed.queryParameter("id")?.takeIf { it.isNotBlank() } ?: return null
        val kind = when (parsed.pathSegments.lastOrNull { it.isNotEmpty() }) {
            "song" -> NeteaseLinkKind.Song
            "artist" -> NeteaseLinkKind.Artist
            "album" -> NeteaseLinkKind.Album
            "mv" -> NeteaseLinkKind.MusicVideo
            else -> return null
        }
        return kind to id
    }

    private fun launch(context: Context, url: String): Boolean = try {
        val intent = intentForUrl(url)
        context.startActivity(intent.apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: java.net.URISyntaxException) {
        false
    } catch (_: SecurityException) {
        false
    }

    internal fun intentForUrl(url: String): Intent = when {
        url.startsWith("intent:", ignoreCase = true) -> Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        url.startsWith("android-app:", ignoreCase = true) -> Intent.parseUri(url, Intent.URI_ANDROID_APP_SCHEME)
        // URI_INTENT_SCHEME would treat orpheus://...#Intent;... as an opaque VIEW URI.
        // Zero accepts an Intent fragment on any scheme, preserving package, action and extras.
        "#Intent;" in url -> Intent.parseUri(url, 0)
        else -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
}
