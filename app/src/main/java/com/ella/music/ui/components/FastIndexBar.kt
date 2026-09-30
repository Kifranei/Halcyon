package com.ella.music.ui.components

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

val SideIndexListEndPadding: Dp = 8.dp
// Song actions may have a 48 dp minimum touch target even when their visible icon is smaller.
val ScrollIndicatorListEndPadding: Dp = 16.dp

@Composable
fun FastIndexBar(
    letters: List<String>,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    reverse: Boolean = false
) {
    val view = LocalView.current
    // The pointer coroutine can survive a layout transition when the letter list and bar size
    // stay the same. Keep the callback current so a drag after switching to the two-column
    // layout always scrolls the newly active LazyListState rather than the old one.
    val currentOnLetterClick by rememberUpdatedState(onLetterClick)
    val indexLetters = remember(letters, reverse) { letters.toFastIndexLetters(reverse) }
    var heightPx by remember { mutableStateOf(1) }
    var contentHeightPx by remember { mutableStateOf(1) }
    var lastSelectedLetter by remember { mutableStateOf<String?>(null) }
    var lastDispatchTimeMs by remember { mutableStateOf(0L) }
    val barAlpha by animateFloatAsState(
        targetValue = if (lastSelectedLetter != null) 0.18f else 0f,
        label = "fastIndexBarBackgroundAlpha"
    )
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (lastSelectedLetter != null) 1f else 0f,
        label = "fastIndexBubbleAlpha"
    )

    fun selectAt(y: Float, force: Boolean = false) {
        if (indexLetters.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastDispatchTimeMs < 80L) return
        val contentTop = ((heightPx - contentHeightPx) / 2f).coerceAtLeast(0f)
        val localY = (y - contentTop).coerceIn(0f, contentHeightPx.toFloat() - 1f)
        val index = floor((localY / contentHeightPx) * indexLetters.size)
            .toInt()
            .coerceIn(0, indexLetters.lastIndex)
        val letter = indexLetters[index]
        if (letter != lastSelectedLetter) {
            lastSelectedLetter = letter
            lastDispatchTimeMs = now
            currentOnLetterClick(letter)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            // The bar is a sibling of the list. Explicitly place it above the list for hit
            // testing; this changes no dimensions or touch target width.
            .zIndex(1f)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(indexLetters, heightPx, contentHeightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    try {
                        // The whole bar is one gesture target. Child clickables used to win the
                        // hit test in the two-column library and swallowed the vertical drag.
                        down.consume()
                        selectAt(down.position.y, force = true)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                break
                            }
                            if (change.pressed) {
                                selectAt(change.position.y)
                                change.consume()
                            }
                        }
                    } finally {
                        lastSelectedLetter = null
                    }
                }
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        val cellSize = if (indexLetters.isEmpty()) {
            10.dp
        } else {
            (maxHeight / indexLetters.size.toFloat()).coerceAtMost(20.dp).coerceAtLeast(10.dp)
        }
        val barHeight = cellSize * indexLetters.size.toFloat()
        val cellFontSize = when {
            cellSize < 15.dp -> 6.sp
            cellSize < 20.dp -> 9.sp
            else -> 10.sp
        }
        Column(
            modifier = Modifier
                .width(cellSize)
                .height(barHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = barAlpha))
                .onSizeChanged { contentHeightPx = it.height.coerceAtLeast(1) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            indexLetters.forEach { letter ->
                val selected = letter == lastSelectedLetter
                Box(
                    modifier = Modifier
                        .size(cellSize),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        fontSize = cellFontSize,
                        lineHeight = cellFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.72f)
                        }
                    )
                }
            }
        }
        if (bubbleAlpha > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-46).dp)
                    .size(50.dp)
                    .alpha(bubbleAlpha)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lastSelectedLetter.orEmpty(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MiuixTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

fun List<String>.toFastIndexLetters(reverse: Boolean = false): List<String> =
    map { it.trim().ifBlank { "#" } }
        .distinct()
        .sortedWith(
            compareBy<String> { letter ->
                val first = letter.firstOrNull()
                when {
                    first?.isDigit() == true -> 0
                    first?.isLetter() == true -> 1
                    else -> 2
                }
            }.thenBy { if (it == "#") "ZZZ" else it.uppercase() }
        )
        .let { if (reverse) it.asReversed() else it }

fun String.toFastIndexSection(): String {
    val value = trim().removeFastIndexSortPrefix()
    val first = value.firstOrNull()?.uppercaseChar()
    return when {
        first == null -> "#"
        first.isDigit() -> "0"
        first in 'A'..'Z' -> first.toString()
        else -> "#"
    }
}

fun String.toFastIndexSortableKey(): String {
    val value = trim()
    if (value.isBlank()) return "2_"
    val first = value.first()
    return when {
        first.isDigit() -> "0_$value"
        first.isLetter() && first.code < 128 -> "1_${value.uppercase(Locale.ROOT)}"
        else -> "2_$value"
    }
}

private fun String.removeFastIndexSortPrefix(): String =
    if (length >= 2 && this[1] == '_' && this[0] in '0'..'2') drop(2) else this

@Composable
fun LazyListScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    val userDragging by state.interactionSource.collectIsDraggedAsState()
    val info by remember(state) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val total = layoutInfo.totalItemsCount
            val visible = layoutInfo.visibleItemsInfo.size
            val first = state.firstVisibleItemIndex
            ScrollIndicatorInfo(
                firstVisibleIndex = first,
                firstVisibleOffset = state.firstVisibleItemScrollOffset,
                firstItemSize = layoutInfo.visibleItemsInfo.firstOrNull { it.index == first }?.size ?: 1,
                indexStep = 1,
                visibleCount = visible,
                totalCount = total,
                atStart = !state.canScrollBackward,
                atEnd = !state.canScrollForward
            )
        }
    }
    ScrollIndicator(
        userDragging = userDragging,
        scrollInProgress = state.isScrollInProgress,
        firstVisibleIndex = info.firstVisibleIndex,
        firstVisibleOffset = info.firstVisibleOffset,
        firstItemSize = info.firstItemSize,
        indexStep = info.indexStep,
        atStart = info.atStart,
        atEnd = info.atEnd,
        visibleCount = info.visibleCount,
        totalCount = info.totalCount,
        modifier = modifier,
        onDragToIndex = { index ->
            state.scrollToItem(index)
        }
    )
}

@Composable
fun LazyGridScrollIndicator(
    state: LazyGridState,
    modifier: Modifier = Modifier
) {
    val userDragging by state.interactionSource.collectIsDraggedAsState()
    val info by remember(state) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val total = layoutInfo.totalItemsCount
            val visible = layoutInfo.visibleItemsInfo.size
            val first = state.firstVisibleItemIndex
            ScrollIndicatorInfo(
                firstVisibleIndex = first,
                firstVisibleOffset = state.firstVisibleItemScrollOffset,
                firstItemSize = layoutInfo.visibleItemsInfo.firstOrNull { it.index == first }?.size?.height ?: 1,
                indexStep = layoutInfo.visibleItemsInfo.count { it.row == layoutInfo.visibleItemsInfo.firstOrNull()?.row }
                    .coerceAtLeast(1),
                visibleCount = visible,
                totalCount = total,
                atStart = !state.canScrollBackward,
                atEnd = !state.canScrollForward
            )
        }
    }
    ScrollIndicator(
        userDragging = userDragging,
        scrollInProgress = state.isScrollInProgress,
        firstVisibleIndex = info.firstVisibleIndex,
        firstVisibleOffset = info.firstVisibleOffset,
        firstItemSize = info.firstItemSize,
        indexStep = info.indexStep,
        atStart = info.atStart,
        atEnd = info.atEnd,
        visibleCount = info.visibleCount,
        totalCount = info.totalCount,
        modifier = modifier,
        onDragToIndex = { index ->
            state.scrollToItem(index)
        }
    )
}

@Composable
private fun ScrollIndicator(
    userDragging: Boolean,
    scrollInProgress: Boolean,
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    firstItemSize: Int,
    indexStep: Int,
    atStart: Boolean,
    atEnd: Boolean,
    visibleCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onDragToIndex: (suspend (Int) -> Unit)? = null
) {
    if (totalCount <= 0 || visibleCount <= 0 || totalCount <= visibleCount) return
    val density = LocalDensity.current
    val visibleFraction = visibleCount.toFloat() / totalCount.toFloat()
    val maxFirst = (totalCount - visibleCount + indexStep).coerceAtLeast(1)
    val offsetFraction = when {
        atStart -> 0f
        atEnd -> 1f
        else -> ((firstVisibleIndex + firstVisibleOffset.toFloat() / firstItemSize.coerceAtLeast(1) * indexStep) / maxFirst)
            .coerceIn(0f, 1f)
    }
    var trackHeightPx by remember { mutableStateOf(1) }
    var visible by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var userScrollActive by remember { mutableStateOf(false) }
    val thumbAlpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, label = "scrollThumbAlpha")
    val currentOnDragToIndex by rememberUpdatedState(onDragToIndex)
    val currentMaxFirst by rememberUpdatedState(maxFirst)
    val currentTotalCount by rememberUpdatedState(totalCount)
    val geometry = scrollThumbGeometry(
        trackHeightPx.toFloat(), visibleFraction, with(density) { 64.dp.toPx() }
    )
    val currentGeometry by rememberUpdatedState(geometry)
    val thumbOffsetPx = if (dragging) dragOffsetPx.coerceIn(0f, geometry.travel) else geometry.offset(offsetFraction)
    val currentThumbOffsetPx by rememberUpdatedState(thumbOffsetPx)
    val scrollSignature = remember(firstVisibleIndex, firstVisibleOffset) {
        firstVisibleIndex to firstVisibleOffset
    }

    LaunchedEffect(scrollSignature, scrollInProgress, userDragging, dragging) {
        when {
            userDragging || dragging -> {
                userScrollActive = true
                visible = true
            }
            scrollInProgress -> {
                // Continue displaying through a user's fling. Programmatic location/source
                // jumps must not summon an overlay over the row's more button.
                visible = userScrollActive
            }
            else -> {
                val endedUserScroll = userScrollActive
                userScrollActive = false
                if (!endedUserScroll) visible = false
                delay(SCROLL_THUMB_IDLE_HIDE_MS)
                visible = false
            }
        }
    }

    Box(
        modifier = modifier
            // Keep the grab area close enough to the edge to avoid covering the last row's
            // trailing actions, while leaving a small inset for the system back gesture.
            .width(28.dp)
            .fillMaxHeight()
            .zIndex(1f)
            .padding(end = 2.dp, top = 28.dp, bottom = 28.dp)
            .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1) }
    ) {
        if (thumbAlpha > 0f) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxHeight().width(8.dp)
                    .alpha(thumbAlpha * if (dragging) 0.20f else 0.08f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MiuixTheme.colorScheme.primary)
            )
        }
        // No pointer modifier on the track or on a hidden/fading-out thumb. Invisible
        // indicators cannot intercept edge taps or turn a list swipe into a jump (#144).
        val thumbGesture = if (visible && onDragToIndex != null) Modifier.pointerInput(Unit) {
                if (currentOnDragToIndex == null) return@pointerInput
                coroutineScope {
                    val targetIndices = Channel<Int>(Channel.CONFLATED)
                    val scrollWorker = launch {
                        for (targetIndex in targetIndices) {
                            currentOnDragToIndex?.invoke(targetIndex)
                        }
                    }
                    try {
                        awaitEachGesture {
                            var lastIndex = -1
                            var moved = false
                            fun dispatch() {
                                val targetIndex = scrollThumbTargetIndex(
                                    currentGeometry.progress(dragOffsetPx), currentMaxFirst, currentTotalCount
                                )
                                if (targetIndex == lastIndex) return
                                lastIndex = targetIndex
                                targetIndices.trySend(targetIndex)
                            }

                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            dragOffsetPx = currentThumbOffsetPx
                            dragging = true
                            down.consume()
                            // Start from the grabbed thumb position: touching its middle must
                            // not jump the list. Conflation keeps only the latest drag request.
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val delta = change.position.y - change.previousPosition.y
                                    if (delta != 0f) moved = true
                                    dragOffsetPx = (dragOffsetPx + delta)
                                        .coerceIn(0f, currentGeometry.travel)
                                    if (change.changedToUpIgnoreConsumed()) {
                                        if (moved) dispatch()
                                        change.consume()
                                        break
                                    }
                                    if (!change.pressed) break
                                    if (moved) dispatch()
                                    change.consume()
                                }
                            } finally {
                                dragging = false
                            }
                        }
                    } finally {
                        dragging = false
                        targetIndices.close()
                        scrollWorker.cancel()
                    }
                }
            } else Modifier
        val thumbHeight = with(density) { geometry.height.toDp() }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .height(thumbHeight)
                .width(22.dp)
                .then(if (visible && onDragToIndex != null) Modifier.systemGestureExclusion() else Modifier)
                .then(if (visible) Modifier.testTag("scroll-indicator-thumb") else Modifier)
                .then(thumbGesture),
            contentAlignment = Alignment.Center
        ) {
          Box(
            Modifier.fillMaxHeight()
                .width(if (dragging) 10.dp else 8.dp)
                .alpha(thumbAlpha)
                .clip(RoundedCornerShape(999.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = if (dragging) 0.92f else 0.65f))
          )
        }
    }
}

private const val SCROLL_THUMB_IDLE_HIDE_MS = 1_000L

private data class ScrollIndicatorInfo(
    val firstVisibleIndex: Int,
    val firstVisibleOffset: Int,
    val firstItemSize: Int,
    val indexStep: Int,
    val visibleCount: Int,
    val totalCount: Int,
    val atStart: Boolean,
    val atEnd: Boolean
)
