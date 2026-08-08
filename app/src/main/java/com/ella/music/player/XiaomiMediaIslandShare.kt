package com.ella.music.player

import com.ella.music.data.model.Song
import org.json.JSONObject

/**
 * Payload consumed by Xiaomi/HyperOS media-notification-island drag sharing.
 *
 * The cover is deliberately not duplicated in shareData: HyperOS takes it from the
 * media notification's large icon, as required by the platform integration contract.
 */
internal fun Song.toXiaomiMediaIslandShareParams(neteaseShareUrl: String): String {
    val shareData = JSONObject()
        .put("title", title)
        .put(
            "content",
            listOf(artist, album)
                .map(String::trim)
                .filter(String::isNotBlank)
                .joinToString(" · ")
        )
        .put("shareContent", neteaseShareUrl.trim())

    return JSONObject()
        .put(
            "param_v2",
            JSONObject().put(
                "param_island",
                JSONObject().put("shareData", shareData)
            )
        )
        .toString()
}
