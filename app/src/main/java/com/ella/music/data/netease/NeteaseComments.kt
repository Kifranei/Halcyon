package com.ella.music.data.netease

import org.json.JSONArray
import org.json.JSONObject

/** Sort modes of `/eapi/v2/resource/comments`; [apiValue] is the request `sortType`. */
internal enum class NeteaseCommentSort(val apiValue: Int) {
    Recommend(1),
    Hot(2),
    Latest(3);

    companion object {
        fun fromApiValue(value: Int): NeteaseCommentSort? = entries.firstOrNull { it.apiValue == value }
    }
}

internal data class NeteaseCommentUser(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String,
    /** Black-vip / music-package badge image, empty when the user has none. */
    val vipIconUrl: String = "",
    val vipLevel: Int = 0
)

/** The comment a reply answers ("回复 @nickname:"). */
internal data class NeteaseRepliedComment(
    val commentId: Long,
    val nickname: String,
    val content: String
)

internal data class NeteaseComment(
    val id: Long,
    val user: NeteaseCommentUser,
    val content: String,
    val timeMs: Long,
    /** Server-formatted relative time, e.g. "12分钟前"; may be empty. */
    val timeStr: String,
    val ipLocation: String,
    val likedCount: Long,
    val replyCount: Int,
    val parentCommentId: Long,
    /**
     * Target shown as "回复 @x:". Null when the comment answers nothing, or when it answers its
     * own floor owner (NetEase omits the prefix for direct floor replies).
     */
    val replyTo: NeteaseRepliedComment?,
    /** Whether the signed-in account has liked this comment (always false when anonymous). */
    val liked: Boolean = false
)

/** NetEase rejects comments longer than this (counted in code points, as the app does). */
internal const val NETEASE_COMMENT_MAX_LENGTH = 140

internal fun neteaseCommentLength(text: String): Int = text.codePointCount(0, text.length)

/** Clips [text] to [NETEASE_COMMENT_MAX_LENGTH] code points without splitting a surrogate pair. */
internal fun clipNeteaseComment(text: String): String =
    if (neteaseCommentLength(text) <= NETEASE_COMMENT_MAX_LENGTH) text
    else text.substring(0, text.offsetByCodePoints(0, NETEASE_COMMENT_MAX_LENGTH))

/** Optimistic like state: flips [NeteaseComment.liked] and moves the count by one. */
internal fun NeteaseComment.withLiked(liked: Boolean): NeteaseComment =
    if (liked == this.liked) this
    else copy(liked = liked, likedCount = (likedCount + if (liked) 1 else -1).coerceAtLeast(0))

/**
 * One page of thread comments. [sortType] and [cursor] are the values the server reports it
 * actually used: without login a Recommend request is served as Hot, and the next page must
 * then be requested with the Hot sort type or the API answers 400.
 */
internal data class NeteaseCommentPage(
    val comments: List<NeteaseComment>,
    val totalCount: Long,
    val hasMore: Boolean,
    val cursor: String,
    val sortType: Int,
    val pageNo: Int
)

internal data class NeteaseFloorPage(
    val replies: List<NeteaseComment>,
    val totalCount: Int,
    val hasMore: Boolean,
    /** Pass back as `time` to fetch the next floor page. */
    val nextTime: Long
)

internal enum class NeteaseCommentResource(val prefix: String) {
    Song("R_SO_4_"), Album("R_AL_3_"), MusicVideo("R_MV_5_");
    fun threadId(id: String): String {
        require((id.toLongOrNull() ?: 0L) > 0L)
        return prefix + id
    }
}
internal data class NeteaseCommentTarget(val id: String, val resource: NeteaseCommentResource)
internal fun NeteaseLinkKind.commentResource(): NeteaseCommentResource? = when (this) {
    NeteaseLinkKind.Comment -> NeteaseCommentResource.Song
    NeteaseLinkKind.AlbumComment -> NeteaseCommentResource.Album
    NeteaseLinkKind.MusicVideoComment -> NeteaseCommentResource.MusicVideo
    else -> null
}
internal fun neteaseSongThreadId(songId: String): String = NeteaseCommentResource.Song.threadId(songId)

/** Android and JVM org.json disagree on optString for JSON null; normalise both to "". */
private fun JSONObject.text(key: String): String =
    if (!has(key) || isNull(key)) "" else optString(key).takeUnless { it == "null" }.orEmpty()

private fun httpsUrl(url: String): String =
    if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

internal fun parseNeteaseCommentUser(json: JSONObject?): NeteaseCommentUser {
    if (json == null) return NeteaseCommentUser(0, "", "")
    val vip = json.optJSONObject("vipRights")
    val vipIcon = vip?.let { rights ->
        listOf("associator", "musicPackage").firstNotNullOfOrNull { key ->
            rights.optJSONObject(key)?.takeIf { it.optBoolean("rights", true) }?.text("iconUrl")?.takeIf(String::isNotBlank)
        }
    }.orEmpty()
    return NeteaseCommentUser(
        userId = json.optLong("userId"),
        nickname = json.text("nickname"),
        avatarUrl = httpsUrl(json.text("avatarUrl")),
        vipIconUrl = httpsUrl(vipIcon),
        vipLevel = vip?.optInt("redVipLevel", 0)?.coerceAtLeast(0) ?: 0
    )
}

internal fun parseNeteaseComment(json: JSONObject): NeteaseComment? {
    val id = json.optLong("commentId").takeIf { it > 0 } ?: return null
    val parentId = json.optLong("parentCommentId")
    val replied = (json.optJSONArray("beReplied") ?: JSONArray()).optJSONObject(0)?.let { target ->
        val targetId = target.optLong("beRepliedCommentId")
        if (parentId > 0 && targetId == parentId) null
        else NeteaseRepliedComment(
            commentId = targetId,
            nickname = target.optJSONObject("user")?.text("nickname").orEmpty(),
            content = target.text("content")
        )
    }
    val floorReplies = json.optJSONObject("showFloorComment")?.optInt("replyCount", 0) ?: 0
    return NeteaseComment(
        id = id,
        user = parseNeteaseCommentUser(json.optJSONObject("user")),
        content = json.text("content"),
        timeMs = json.optLong("time"),
        timeStr = json.text("timeStr"),
        ipLocation = json.optJSONObject("ipLocation")?.text("location").orEmpty(),
        likedCount = json.optLong("likedCount").coerceAtLeast(0),
        replyCount = maxOf(json.optInt("replyCount", 0), floorReplies).coerceAtLeast(0),
        parentCommentId = parentId,
        replyTo = replied,
        liked = json.optBoolean("liked", false)
    )
}

/**
 * Parses the comment created by `/eapi/resource/comments/add` or `/reply` (under `comment`).
 * [parentCommentId] is the floor a reply lands in; the response may omit it, and it decides
 * whether the "回复 @x:" prefix is shown (direct floor replies hide it).
 */
internal fun parseNeteaseCreatedComment(root: JSONObject, parentCommentId: Long = 0L): NeteaseComment? {
    val created = root.optJSONObject("comment") ?: root.optJSONObject("data")?.optJSONObject("comment") ?: return null
    val json = JSONObject(created.toString())
    if (parentCommentId > 0L && json.optLong("parentCommentId") <= 0L) json.put("parentCommentId", parentCommentId)
    return parseNeteaseComment(json)
}

private fun parseCommentArray(array: JSONArray?): List<NeteaseComment> {
    if (array == null) return emptyList()
    val seen = HashSet<Long>()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let(::parseNeteaseComment)?.takeIf { seen.add(it.id) }
    }
}

/** Parses a `/eapi/v2/resource/comments` response (root object, including `code`). */
internal fun parseNeteaseCommentPage(root: JSONObject, requestedSort: Int, pageNo: Int): NeteaseCommentPage {
    val data = root.optJSONObject("data") ?: JSONObject()
    val comments = parseCommentArray(data.optJSONArray("comments"))
    return NeteaseCommentPage(
        comments = comments,
        totalCount = data.optLong("totalCount").coerceAtLeast(0),
        hasMore = data.optBoolean("hasMore", false) && comments.isNotEmpty(),
        cursor = data.text("cursor"),
        sortType = data.optInt("sortType", requestedSort).takeIf { it > 0 } ?: requestedSort,
        pageNo = pageNo
    )
}

/** Parses a `/eapi/resource/comment/floor/get` response. */
internal fun parseNeteaseFloorPage(root: JSONObject): NeteaseFloorPage {
    val data = root.optJSONObject("data") ?: JSONObject()
    val replies = parseCommentArray(data.optJSONArray("comments"))
    val nextTime = data.optLong("time", replies.lastOrNull()?.timeMs ?: -1L)
    return NeteaseFloorPage(
        replies = replies,
        totalCount = data.optInt("totalCount", replies.size).coerceAtLeast(0),
        hasMore = data.optBoolean("hasMore", false) && replies.isNotEmpty() && nextTime > 0,
        nextTime = nextTime
    )
}
