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
    Song("song"), Comment("comment"), Artist("artist"), ArtistWiki("artistwiki"), Album("album"), MusicVideo("mv")
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
    val custom: Map<NeteaseLinkKind, String> = emptyMap()
)

internal object NeteaseLinks {
    private const val PREFS = "netease_links"
    private const val KEY_TARGET = "target"
    private const val KEY_MV_EXTERNAL = "mv_external"
    private const val ID = "{id}"

    private val webPrefixes = mapOf(
        NeteaseLinkKind.Song to "https://y.music.163.com/m/song?id=",
        NeteaseLinkKind.Comment to "https://music.163.com/#/song?id=",
        NeteaseLinkKind.Artist to "https://y.music.163.com/m/artist?id=",
        NeteaseLinkKind.ArtistWiki to "https://music.163.com/st/artistwiki?artistId=",
        NeteaseLinkKind.Album to "https://y.music.163.com/m/album?id=",
        NeteaseLinkKind.MusicVideo to "https://y.music.163.com/m/mv?id="
    )

    private fun appPrefixes(scheme: String) = mapOf(
        NeteaseLinkKind.Song to "$scheme://song/",
        NeteaseLinkKind.Comment to "$scheme://comment?threadId=R_SO_4_",
        NeteaseLinkKind.Artist to "$scheme://artist/",
        // The app scheme has no wiki page; the web wiki is the documented destination.
        NeteaseLinkKind.ArtistWiki to webPrefixes.getValue(NeteaseLinkKind.ArtistWiki),
        NeteaseLinkKind.Album to "$scheme://album/",
        NeteaseLinkKind.MusicVideo to "$scheme://mv/"
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

    /**
     * Comments open in the NetEase app (or a custom template) only when the user chose an app
     * target or set a custom comment link; otherwise Halcyon's own comment sheet is used, since
     * the NetEase website has no standalone comment page.
     */
    fun commentsOpenExternally(context: Context): Boolean {
        val settings = current(context)
        return when (settings.target) {
            NeteaseLinkTarget.Web -> false
            NeteaseLinkTarget.Custom -> !settings.custom[NeteaseLinkKind.Comment].isNullOrBlank()
            else -> true
        }
    }

    /** Web fallback used when the chosen app scheme has no handler on this device. */
    fun webUrl(kind: NeteaseLinkKind, id: String): String? = build(NeteaseLinkSettings(), kind, id)

    private val mutableSettings = MutableStateFlow<NeteaseLinkSettings?>(null)

    fun settings(context: Context): StateFlow<NeteaseLinkSettings?> {
        if (mutableSettings.value == null) mutableSettings.value = read(context)
        return mutableSettings.asStateFlow()
    }

    fun current(context: Context): NeteaseLinkSettings = mutableSettings.value ?: read(context).also { mutableSettings.value = it }

    private fun read(context: Context): NeteaseLinkSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return NeteaseLinkSettings(
            target = NeteaseLinkTarget.fromId(prefs.getString(KEY_TARGET, null)),
            openMusicVideoExternally = prefs.getBoolean(KEY_MV_EXTERNAL, false),
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
            NeteaseLinkKind.entries.forEach { kind ->
                val value = next.custom[kind]?.trim().orEmpty()
                if (value.isEmpty()) remove("custom_${kind.key}") else putString("custom_${kind.key}", value)
            }
        }.apply()
        mutableSettings.value = next
    }

    /** Opens [kind]/[id] with the user's link settings, falling back to the web page if no app handles it. */
    fun open(context: Context, kind: NeteaseLinkKind, id: String) {
        val url = build(current(context), kind, id) ?: return
        if (launch(context, url)) return
        val fallback = webUrl(kind, id)
        if (fallback != null && fallback != url && launch(context, fallback)) return
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
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
