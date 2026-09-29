package com.ella.music.ui.player

import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.netease.CatClawNeteaseClient
import com.ella.music.data.netease.NETEASE_COMMENT_MAX_LENGTH
import com.ella.music.data.netease.NETEASE_SOURCE
import com.ella.music.data.netease.NeteaseApiException
import com.ella.music.data.netease.NeteaseComment
import com.ella.music.data.netease.NeteaseCommentSort
import com.ella.music.data.netease.clipNeteaseComment
import com.ella.music.data.netease.neteaseCommentLength
import com.ella.music.data.netease.withLiked
import com.ella.music.ui.components.EllaLoadingIndicator
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.SafeCoverImage
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** NetEase song id whose comment thread can be shown, or null outside NetEase mode. */
internal fun Song.neteaseCommentSongId(): String? =
    onlineId.takeIf { onlineSource == NETEASE_SOURCE && (it.toLongOrNull() ?: 0L) > 0L }

/**
 * Title-row comment button, placed between the favorite and more actions. Renders nothing for
 * songs without a NetEase comment thread, and owns its comments sheet so layouts only add one call.
 */
@Composable
internal fun PlayerCommentHeaderAction(
    song: Song?,
    useAppleIcons: Boolean = false
) {
    val current = song ?: return
    val songId = current.neteaseCommentSongId() ?: return
    var showComments by rememberSaveable(songId) { mutableStateOf(false) }
    val contentColor = LocalPlayerContentColor.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .playerNoIndicationClick { showComments = true },
        contentAlignment = Alignment.Center
    ) {
        if (useAppleIcons) {
            // Mirrors the Apple favorite/more glyphs, which carry a faint filled disc.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_comment),
                    contentDescription = stringResource(R.string.netease_comments_open),
                    tint = contentColor.copy(alpha = 0.92f),
                    modifier = Modifier.size(17.dp)
                )
            }
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_comment),
                contentDescription = stringResource(R.string.netease_comments_open),
                tint = contentColor.copy(alpha = 0.92f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
    NeteaseCommentsSheet(
        show = showComments,
        song = current,
        onDismiss = { showComments = false }
    )
}

@Composable
internal fun NeteaseCommentsSheet(
    show: Boolean,
    song: Song,
    onDismiss: () -> Unit,
    songIdOverride: String? = null
) {
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    EllaMiuixBottomSheet(
        show = show,
        enableNestedScroll = false,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
        ) {
            NeteaseCommentsContent(song = song, songIdOverride = songIdOverride)
        }
    }
}

@Stable
private class CommentFloorUi {
    val replies = mutableStateListOf<NeteaseComment>()
    var expanded by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var hasMore by mutableStateOf(true)
    var nextTime = -1L
    var job: Job? = null
}

@Stable
private class NeteaseCommentsUi(
    private val client: CatClawNeteaseClient,
    private val songId: String,
    private val scope: CoroutineScope,
    /** Application context, only for toasts and their strings. */
    private val appContext: Context
) {
    var sort by mutableStateOf(NeteaseCommentSort.Recommend)
        private set
    val comments = mutableStateListOf<NeteaseComment>()
    val floors = mutableStateMapOf<Long, CommentFloorUi>()
    var totalCount by mutableLongStateOf(0L)
        private set
    /** First page of the current sort: loading, failed, or settled. */
    var initialLoading by mutableStateOf(true)
        private set
    var initialFailed by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var pageFailed by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set

    private var nextPageNo = 1
    private var nextCursor = ""
    private var nextSortType = NeteaseCommentSort.Recommend.apiValue
    private var job: Job? = null
    /** Bumped on every reload so a cancelled request's cleanup cannot touch the new sort's state. */
    private var generation = 0

    /** Composer: reply target (null = new top-level comment), draft text, and in-flight send. */
    var replyTarget by mutableStateOf<NeteaseComment?>(null)
        private set
    var draft by mutableStateOf("")
        private set
    var sending by mutableStateOf(false)
        private set
    /** Bumped whenever the composer should take focus and raise the keyboard. */
    var focusRequests by mutableStateOf(0)
        private set
    private val likesInFlight = HashSet<Long>()

    private fun showToast(message: String) = Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()

    private fun toast(@StringRes id: Int) = showToast(appContext.getString(id))

    fun updateDraft(value: String) {
        draft = clipNeteaseComment(value)
    }

    fun replyTo(comment: NeteaseComment) {
        replyTarget = comment
        focusRequests++
    }

    fun cancelReply() {
        replyTarget = null
    }

    /** Every loaded copy of a comment: top-level list and all floors. */
    private fun updateComment(id: Long, transform: (NeteaseComment) -> NeteaseComment) {
        val index = comments.indexOfFirst { it.id == id }
        if (index >= 0) comments[index] = transform(comments[index])
        floors.values.forEach { floor ->
            val replyIndex = floor.replies.indexOfFirst { it.id == id }
            if (replyIndex >= 0) floor.replies[replyIndex] = transform(floor.replies[replyIndex])
        }
        replyTarget?.takeIf { it.id == id }?.let { replyTarget = transform(it) }
    }

    private fun findComment(id: Long): NeteaseComment? =
        comments.firstOrNull { it.id == id } ?: floors.values.firstNotNullOfOrNull { floor ->
            floor.replies.firstOrNull { it.id == id }
        }

    private fun failureText(error: Exception, @StringRes fallback: Int): String {
        val code = (error as? NeteaseApiException)?.code
        return when {
            code == 301 -> appContext.getString(R.string.netease_comments_login_required)
            code != null && fallback == R.string.netease_comments_send_failed ->
                appContext.getString(R.string.netease_comments_send_failed_code, code)
            else -> appContext.getString(fallback)
        }
    }

    /** Optimistic like toggle; rolls back and toasts when the server refuses. */
    fun toggleLike(comment: NeteaseComment) {
        if (client.signedInAccount() == null) {
            toast(R.string.netease_comments_login_required)
            return
        }
        val id = comment.id
        if (!likesInFlight.add(id)) return
        val liked = !(findComment(id) ?: comment).liked
        updateComment(id) { it.withLiked(liked) }
        scope.launch {
            try {
                client.likeComment(songId = songId, commentId = id, like = liked)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateComment(id) { it.withLiked(!liked) }
                showToast(failureText(error, R.string.netease_comments_like_failed))
            } finally {
                likesInFlight.remove(id)
            }
        }
    }

    /** Posts [draft] as a reply to [replyTarget] or as a new comment; [onPosted] gets whether it was top-level. */
    fun send(onPosted: (topLevel: Boolean) -> Unit) {
        val content = draft.trim()
        if (sending || content.isEmpty() || neteaseCommentLength(content) > NETEASE_COMMENT_MAX_LENGTH) return
        val account = client.signedInAccount()
        if (account == null) {
            toast(R.string.netease_comments_login_required)
            return
        }
        val target = replyTarget
        // Replies to a floor reply stay in that floor, as in the NetEase app.
        val floorId = target?.let { if (it.parentCommentId > 0L) it.parentCommentId else it.id } ?: 0L
        val token = generation
        sending = true
        scope.launch {
            try {
                val created = client.postComment(
                    songId = songId,
                    content = content,
                    replyToCommentId = target?.id ?: 0L,
                    parentCommentId = floorId
                )
                if (draft.trim() == content) draft = ""
                if (replyTarget?.id == target?.id) replyTarget = null
                if (created == null) {
                    toast(R.string.netease_comments_sent)
                } else if (token == generation) {
                    val local = created.copy(
                        timeMs = created.timeMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        user = created.user.takeIf { it.nickname.isNotBlank() }
                            ?: created.user.copy(userId = account.userId, nickname = account.nickname)
                    )
                    insertCreated(local, floorId)
                }
                onPosted(floorId == 0L)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showToast(failureText(error, R.string.netease_comments_send_failed))
            } finally {
                sending = false
            }
        }
    }

    private fun insertCreated(created: NeteaseComment, floorId: Long) {
        if (floorId == 0L) {
            if (comments.none { it.id == created.id }) {
                comments.add(0, created)
                totalCount += 1
            }
            return
        }
        val floor = floors.getOrPut(floorId) {
            // A floor that had no replies has nothing else to page in.
            CommentFloorUi().also { fresh -> fresh.hasMore = (findComment(floorId)?.replyCount ?: 0) > 0 }
        }
        floor.expanded = true
        if (floor.replies.none { it.id == created.id }) {
            floor.replies.add(0, created)
            updateComment(floorId) { it.copy(replyCount = it.replyCount + 1) }
        }
    }

    fun select(newSort: NeteaseCommentSort) {
        if (newSort == sort && !initialFailed) return
        sort = newSort
        reload()
    }

    fun reload() {
        job?.cancel()
        floors.values.forEach { it.job?.cancel() }
        floors.clear()
        comments.clear()
        hasMore = false
        pageFailed = false
        loadingMore = false
        initialFailed = false
        initialLoading = true
        nextPageNo = 1
        nextCursor = ""
        nextSortType = sort.apiValue
        val token = ++generation
        job = scope.launch { fetch(first = true, token = token) }
    }

    fun loadMore() {
        if (initialLoading || loadingMore || !hasMore || job?.isActive == true) return
        pageFailed = false
        loadingMore = true
        val token = generation
        job = scope.launch { fetch(first = false, token = token) }
    }

    private suspend fun fetch(first: Boolean, token: Int) {
        try {
            val page = client.songComments(
                songId = songId,
                sortType = nextSortType,
                pageNo = nextPageNo,
                cursor = nextCursor
            )
            if (token != generation) return
            val known = comments.mapTo(HashSet<Long>()) { it.id }
            comments.addAll(page.comments.filter { known.add(it.id) })
            if (page.totalCount > 0 || first) totalCount = page.totalCount
            // The server may serve another sort than requested (anonymous Recommend -> Hot);
            // continue paging with what it reports, or page 2 is rejected.
            nextSortType = page.sortType
            nextCursor = page.cursor
            nextPageNo = page.pageNo + 1
            hasMore = page.hasMore
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (token == generation) {
                if (first) initialFailed = true else pageFailed = true
            }
        } finally {
            if (token == generation) {
                if (first) initialLoading = false else loadingMore = false
            }
        }
    }

    fun floor(commentId: Long): CommentFloorUi? = floors[commentId]

    fun toggleFloor(comment: NeteaseComment) {
        val floor = floors.getOrPut(comment.id) { CommentFloorUi() }
        if (floor.expanded) {
            floor.expanded = false
            return
        }
        floor.expanded = true
        if (floor.replies.isEmpty()) loadFloor(comment.id)
    }

    fun loadFloor(commentId: Long) {
        val floor = floors[commentId] ?: return
        if (floor.loading || (!floor.hasMore && floor.replies.isNotEmpty())) return
        floor.loading = true
        floor.failed = false
        floor.job = scope.launch {
            try {
                val page = client.commentFloor(songId = songId, parentCommentId = commentId, time = floor.nextTime)
                val known = floor.replies.mapTo(HashSet<Long>()) { it.id }
                floor.replies.addAll(page.replies.filter { known.add(it.id) })
                floor.nextTime = page.nextTime
                floor.hasMore = page.hasMore
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                floor.failed = true
            } finally {
                floor.loading = false
            }
        }
    }
}

@Composable
private fun ColumnScope.NeteaseCommentsContent(song: Song, songIdOverride: String? = null) {
    val context = LocalContext.current
    val songId = songIdOverride?.takeIf { (it.toLongOrNull() ?: 0L) > 0L } ?: song.neteaseCommentSongId() ?: return
    val scope = rememberCoroutineScope()
    val client = remember(context) { CatClawNeteaseClient(context.applicationContext) }
    val ui = remember(songId) { NeteaseCommentsUi(client, songId, scope, context.applicationContext) }
    LaunchedEffect(ui) { ui.reload() }
    // A fresh list position per sort, so switching tabs starts at the top.
    val listState = remember(ui, ui.sort) { LazyListState() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(ui, ui.focusRequests) {
        if (ui.focusRequests > 0) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(ui, listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            // Include the item count so a short appended page re-arms the trigger.
            (info.totalItemsCount > 0 && last >= info.totalItemsCount - 4) to info.totalItemsCount
        }.distinctUntilChanged().collect { (nearEnd, _) -> if (nearEnd) ui.loadMore() }
    }

    NeteaseCommentsHeader(song = song)
    Spacer(modifier = Modifier.height(14.dp))
    NeteaseCommentsSortRow(
        totalCount = ui.totalCount,
        sort = ui.sort,
        onSort = ui::select
    )
    Spacer(modifier = Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        when {
            ui.initialLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EllaLoadingIndicator()
            }
            ui.initialFailed -> NeteaseCommentsMessage(
                text = stringResource(R.string.netease_comments_error),
                actionText = stringResource(R.string.netease_comments_retry),
                onAction = ui::reload
            )
            ui.comments.isEmpty() -> NeteaseCommentsMessage(text = stringResource(R.string.netease_comments_empty))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(ui.comments, key = { it.id }) { comment ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        NeteaseCommentRow(
                            comment = comment,
                            floor = ui.floor(comment.id),
                            onToggleFloor = { ui.toggleFloor(comment) },
                            onLoadMoreFloor = { ui.loadFloor(comment.id) },
                            onLike = ui::toggleLike,
                            onReply = ui::replyTo
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            thickness = 0.5.dp
                        )
                    }
                }
                item(key = "footer") {
                    NeteaseCommentsFooter(
                        loading = ui.loadingMore,
                        failed = ui.pageFailed,
                        hasMore = ui.hasMore,
                        onRetry = ui::loadMore
                    )
                }
            }
        }
    }
    // The sheet itself applies imePadding and caps its height, so this bottom bar rides on top
    // of the keyboard while the weighted list above shrinks.
    NeteaseCommentComposer(
        draft = ui.draft,
        replyTarget = ui.replyTarget,
        sending = ui.sending,
        focusRequester = focusRequester,
        onDraftChange = ui::updateDraft,
        onCancelReply = ui::cancelReply,
        onSend = {
            ui.send { topLevel ->
                keyboard?.hide()
                focusManager.clearFocus()
                if (topLevel) scope.launch { runCatching { listState.animateScrollToItem(0) } }
            }
        }
    )
}

@Composable
private fun NeteaseCommentComposer(
    draft: String,
    replyTarget: NeteaseComment?,
    sending: Boolean,
    focusRequester: FocusRequester,
    onDraftChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit
) {
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val length = neteaseCommentLength(draft)
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 0.5.dp)
        if (replyTarget != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.netease_comments_reply_hint, replyTarget.user.nickname),
                    fontSize = 12.sp,
                    color = summary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.netease_comments_cancel_reply),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onCancelReply)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                label = if (replyTarget != null) {
                    stringResource(R.string.netease_comments_reply_hint, replyTarget.user.nickname)
                } else {
                    stringResource(R.string.netease_comments_input_hint)
                },
                useLabelAsPlaceholder = true,
                singleLine = false,
                maxLines = 4,
                readOnly = sending,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(
                    text = stringResource(R.string.netease_comments_send),
                    onClick = onSend,
                    enabled = !sending && draft.isNotBlank() && length <= NETEASE_COMMENT_MAX_LENGTH,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
                if (draft.isNotEmpty()) {
                    Text(
                        text = "$length/$NETEASE_COMMENT_MAX_LENGTH",
                        fontSize = 11.sp,
                        color = if (length >= NETEASE_COMMENT_MAX_LENGTH) MiuixTheme.colorScheme.primary
                        else summary.copy(alpha = 0.72f),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

private fun neteaseImageUrl(url: String, sizePx: Int): String? {
    if (url.isBlank()) return null
    val https = url.replace("http://", "https://")
    // NetEase's image CDN resizes on request; avoids pulling full-size avatars for 36 dp circles.
    return if (https.contains(".music.126.net/") && !https.contains('?')) "$https?param=${sizePx}y$sizePx" else https
}

@Composable
private fun NeteaseCommentsHeader(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SafeCoverImage(
            model = neteaseImageUrl(song.coverUrl, 160),
            contentDescription = null,
            sizePx = 160,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title.ifBlank { song.fileName },
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.artist.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NeteaseCommentsSortRow(
    totalCount: Long,
    sort: NeteaseCommentSort,
    onSort: (NeteaseCommentSort) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (totalCount > 0) {
                stringResource(R.string.netease_comments_title_count, totalCount.toString())
            } else {
                stringResource(R.string.netease_comments_title)
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val labels = listOf(
            NeteaseCommentSort.Recommend to R.string.netease_comments_sort_recommend,
            NeteaseCommentSort.Hot to R.string.netease_comments_sort_hot,
            NeteaseCommentSort.Latest to R.string.netease_comments_sort_latest
        )
        labels.forEachIndexed { index, (option, label) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .size(width = 1.dp, height = 11.dp)
                        .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f))
                )
            }
            val selected = option == sort
            Text(
                text = stringResource(label),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MiuixTheme.colorScheme.onSurface
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSort(option) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun NeteaseCommentsMessage(
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.widthIn(min = 120.dp),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun NeteaseCommentsFooter(
    loading: Boolean,
    failed: Boolean,
    hasMore: Boolean,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> EllaLoadingIndicator(modifier = Modifier.size(22.dp))
            failed -> Text(
                text = stringResource(R.string.netease_comments_load_more_failed),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            !hasMore -> Text(
                text = stringResource(R.string.netease_comments_no_more),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun NeteaseCommentAvatar(url: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.secondaryContainer)
    ) {
        val model = neteaseImageUrl(url, 120)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun NeteaseCommentRow(
    comment: NeteaseComment,
    floor: CommentFloorUi?,
    onToggleFloor: () -> Unit,
    onLoadMoreFloor: () -> Unit,
    onLike: (NeteaseComment) -> Unit,
    onReply: (NeteaseComment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReply(comment) }
            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 10.dp)
    ) {
        NeteaseCommentAvatar(url = comment.user.avatarUrl, size = 36.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            NeteaseCommentMeta(comment = comment, compact = false, onLike = { onLike(comment) })
            Spacer(modifier = Modifier.height(6.dp))
            NeteaseCommentBody(comment = comment, fontSizeSp = 15, showReplyPrefix = false)
            comment.replyTo?.let { target ->
                // Legacy top-level replies quote their target inline, as the NetEase app does.
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary)) { append("@${target.nickname}") }
                        append("：")
                        append(target.content)
                    },
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            if (comment.replyCount > 0) {
                NeteaseCommentFloor(
                    replyCount = comment.replyCount,
                    floor = floor,
                    onToggle = onToggleFloor,
                    onLoadMore = onLoadMoreFloor,
                    onLike = onLike,
                    onReply = onReply
                )
            }
        }
    }
}

@Composable
private fun NeteaseCommentMeta(comment: NeteaseComment, compact: Boolean, onLike: () -> Unit) {
    val context = LocalContext.current
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user.nickname,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = summary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                val vipIcon = comment.user.vipIconUrl
                if (!compact && vipIcon.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AsyncImage(
                        model = vipIcon,
                        contentDescription = stringResource(R.string.netease_comments_vip_badge),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(13.dp)
                            .widthIn(max = 44.dp)
                    )
                }
            }
            val timeText = remember(comment.id, comment.timeMs) { formatCommentTime(context, comment) }
            val metaLine = listOf(timeText, comment.ipLocation).filter(String::isNotBlank).joinToString(" · ")
            if (metaLine.isNotBlank()) {
                Text(
                    text = metaLine,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = summary.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        val likeColor = if (comment.liked) MiuixTheme.colorScheme.primary else summary
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onLike)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val likes = comment.likedCount
            if (likes > 0) {
                Text(
                    text = formatLikeCount(context, likes),
                    fontSize = 12.sp,
                    color = likeColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                painter = painterResource(R.drawable.ic_comment_like),
                contentDescription = stringResource(R.string.netease_comments_like),
                tint = likeColor,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
        }
    }
}

@Composable
private fun NeteaseCommentBody(comment: NeteaseComment, fontSizeSp: Int, showReplyPrefix: Boolean) {
    val target = comment.replyTo?.takeIf { showReplyPrefix }
    val prefix = target?.let { stringResource(R.string.netease_comments_reply_to, it.nickname) }
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Text(
        text = buildAnnotatedString {
            if (prefix != null) withStyle(SpanStyle(color = summary)) { append(prefix) }
            append(comment.content)
        },
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp + 7).sp,
        color = MiuixTheme.colorScheme.onSurface
    )
}

@Composable
private fun NeteaseCommentFloor(
    replyCount: Int,
    floor: CommentFloorUi?,
    onToggle: () -> Unit,
    onLoadMore: () -> Unit,
    onLike: (NeteaseComment) -> Unit,
    onReply: (NeteaseComment) -> Unit
) {
    val expanded = floor?.expanded == true
    if (expanded && floor != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            floor.replies.forEach { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onReply(reply) }
                        .padding(vertical = 8.dp)
                ) {
                    NeteaseCommentAvatar(url = reply.user.avatarUrl, size = 26.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        NeteaseCommentMeta(comment = reply, compact = true, onLike = { onLike(reply) })
                        Spacer(modifier = Modifier.height(4.dp))
                        NeteaseCommentBody(comment = reply, fontSizeSp = 14, showReplyPrefix = true)
                    }
                }
            }
            when {
                floor.loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EllaLoadingIndicator(modifier = Modifier.size(20.dp))
                }
                floor.failed -> NeteaseFloorLink(
                    text = stringResource(R.string.netease_comments_load_more_failed),
                    onClick = onLoadMore
                )
                floor.hasMore -> NeteaseFloorLink(
                    text = stringResource(R.string.netease_comments_more_replies),
                    onClick = onLoadMore
                )
            }
        }
        NeteaseFloorLink(text = stringResource(R.string.netease_comments_collapse_replies), onClick = onToggle)
    } else {
        Spacer(modifier = Modifier.height(2.dp))
        NeteaseFloorLink(
            text = stringResource(R.string.netease_comments_expand_replies, replyCount) + " ›",
            onClick = onToggle
        )
    }
}

@Composable
private fun NeteaseFloorLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    )
}

private fun Context.usesSimplifiedChinese(): Boolean {
    val locale = resources.configuration.locales[0] ?: Locale.getDefault()
    if (locale.language != "zh") return false
    if (locale.script.equals("Hant", ignoreCase = true)) return false
    return locale.country.uppercase(Locale.ROOT) !in setOf("TW", "HK", "MO")
}

/** Server strings ("12分钟前", "昨天22:12") are Simplified Chinese; other locales format locally. */
private fun formatCommentTime(context: Context, comment: NeteaseComment): String {
    if (comment.timeStr.isNotBlank() && context.usesSimplifiedChinese()) return comment.timeStr
    val time = comment.timeMs
    if (time <= 0L) return comment.timeStr
    val now = System.currentTimeMillis()
    val diff = now - time
    if (diff in 0 until DateUtils.DAY_IN_MILLIS) {
        return DateUtils.getRelativeTimeSpanString(
            time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
    val sameYear = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR) ==
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NUMERIC_DATE or
        (if (sameYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR)
    return DateUtils.formatDateTime(context, time, flags)
}

private fun formatLikeCount(context: Context, count: Long): String {
    if (count < 10_000L) return count.toString()
    val chinese = context.resources.configuration.locales[0]?.language == "zh"
    fun compact(value: Double): String =
        if (value >= 100) value.toLong().toString()
        else String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
    return when {
        chinese && count >= 100_000_000L -> compact(count / 100_000_000.0) + "亿"
        chinese -> compact(count / 10_000.0) + if (context.usesSimplifiedChinese()) "万" else "萬"
        count >= 1_000_000L -> compact(count / 1_000_000.0) + "M"
        else -> compact(count / 1_000.0) + "K"
    }
}
