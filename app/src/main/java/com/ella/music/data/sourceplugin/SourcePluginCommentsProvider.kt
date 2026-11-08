package com.ella.music.data.sourceplugin

import android.content.Context
import com.ella.music.data.model.Song
import com.ella.music.data.netease.NeteaseComment
import com.ella.music.data.netease.NeteaseCommentPage
import com.ella.music.data.netease.NeteaseCommentResource
import com.ella.music.data.netease.NeteaseFloorPage
import com.ella.music.data.netease.OnlineCommentsProvider

/**
 * Comments backend for an account-source plugin song. `songId`/`resource` are NetEase-shaped
 * thread keys the shared comments UI passes through; the plugin itself identifies the item by
 * its stored raw JSON, so those arguments are ignored here.
 */
internal class SourcePluginCommentsProvider(
    context: Context,
    private val config: SourcePluginConfig,
    private val song: Song
) : OnlineCommentsProvider {
    private val appContext = context.applicationContext
    private val client by lazy { SourcePluginRegistry.client(appContext) }

    override val signedInUser: Pair<Long, String>?
        get() = SourcePluginRegistry.account(appContext, config.id)
            ?.let { (it.userId.toLongOrNull() ?: it.userId.hashCode().toLong()) to it.nickname }

    override suspend fun songComments(
        songId: String, sortType: Int, pageNo: Int, cursor: String,
        pageSize: Int, resource: NeteaseCommentResource
    ): NeteaseCommentPage = client.getComments(config, song, sortType, pageNo, cursor)

    override suspend fun commentFloor(
        songId: String, parentCommentId: Long, time: Long,
        limit: Int, resource: NeteaseCommentResource
    ): NeteaseFloorPage = client.getCommentFloor(config, song, parentCommentId, time)

    override suspend fun likeComment(songId: String, commentId: Long, like: Boolean, resource: NeteaseCommentResource) {
        client.likeComment(config, song, commentId, like)
    }

    override suspend fun postComment(
        songId: String, content: String, replyToCommentId: Long,
        parentCommentId: Long, resource: NeteaseCommentResource
    ): NeteaseComment? = client.postComment(config, song, content, replyToCommentId)
}

/** Comments backend for the built-in NetEase account; delegates to the existing client. */
internal class NeteaseCommentsProvider(
    private val client: com.ella.music.data.netease.CatClawNeteaseClient
) : OnlineCommentsProvider {
    override val signedInUser: Pair<Long, String>?
        get() = client.signedInAccount()?.let { it.userId to it.nickname }

    override suspend fun songComments(
        songId: String, sortType: Int, pageNo: Int, cursor: String,
        pageSize: Int, resource: NeteaseCommentResource
    ) = client.songComments(songId, sortType, pageNo, cursor, pageSize, resource)

    override suspend fun commentFloor(
        songId: String, parentCommentId: Long, time: Long,
        limit: Int, resource: NeteaseCommentResource
    ) = client.commentFloor(songId, parentCommentId, time, limit, resource)

    override suspend fun likeComment(songId: String, commentId: Long, like: Boolean, resource: NeteaseCommentResource) =
        client.likeComment(songId, commentId, like, resource)

    override suspend fun postComment(
        songId: String, content: String, replyToCommentId: Long,
        parentCommentId: Long, resource: NeteaseCommentResource
    ) = client.postComment(songId, content, replyToCommentId, parentCommentId, resource)
}

/** Numeric NetEase online id used to key the comment thread (mirrors the sheet's gate). */
internal fun Song.neteaseCommentThreadSongId(): String? =
    onlineId.takeIf { onlineSource == com.ella.music.data.netease.NETEASE_SOURCE && (it.toLongOrNull() ?: 0L) > 0L }

/** Provider for [song]'s comment thread, or null when neither backend can serve it. */
internal fun songCommentProvider(context: Context, song: Song): OnlineCommentsProvider? {
    if (song.neteaseCommentThreadSongId() != null) {
        return NeteaseCommentsProvider(com.ella.music.data.netease.CatClawNeteaseClient(context.applicationContext))
    }
    val pluginId = sourcePluginKeyOf(song.onlineSource).takeIf(String::isNotBlank) ?: return null
    val config = SourcePluginRegistry.config(context, pluginId) ?: return null
    if (!config.hasCapability("comments")) return null
    return SourcePluginCommentsProvider(context, config, song)
}

/** Thread id the comments UI should key on for [song]. */
internal fun songCommentThreadId(song: Song): String? =
    song.neteaseCommentThreadSongId()
        ?: song.onlineId.takeIf { sourcePluginKeyOf(song.onlineSource).isNotBlank() && it.isNotBlank() }
