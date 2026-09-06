package com.ella.music.ui.components

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

val SideIndexListEndPadding: Dp = 8.dp

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
            .width(28.dp)
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
    val info by remember {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val total = layoutInfo.totalItemsCount
            val visible = layoutInfo.visibleItemsInfo.size
            val first = state.firstVisibleItemIndex
            ScrollIndicatorInfo(
                firstVisibleIndex = first,
                firstVisibleOffset = state.firstVisibleItemScrollOffset,
                visibleCount = visible,
                totalCount = total
            )
        }
    }
    ScrollIndicator(
        scrollInProgress = state.isScrollInProgress,
        firstVisibleIndex = info.firstVisibleIndex,
        firstVisibleOffset = info.firstVisibleOffset,
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
    val info by remember {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val total = layoutInfo.totalItemsCount
            val visible = layoutInfo.visibleItemsInfo.size
            val first = state.firstVisibleItemIndex
            ScrollIndicatorInfo(
                firstVisibleIndex = first,
                firstVisibleOffset = state.firstVisibleItemScrollOffset,
                visibleCount = visible,
                totalCount = total
            )
        }
    }
    ScrollIndicator(
        scrollInProgress = state.isScrollInProgress,
        firstVisibleIndex = info.firstVisibleIndex,
        firstVisibleOffset = info.firstVisibleOffset,
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
    scrollInProgress: Boolean,
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    visibleCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onDragToIndex: (suspend (Int) -> Unit)? = null
) {
    if (totalCount <= 0 || visibleCount <= 0 || totalCount <= visibleCount) return
    val visibleFraction = (visibleCount.toFloat() / totalCount.toFloat()).coerceIn(0.08f, 1f)
    val maxFirst = max(1, totalCount - visibleCount)
    val offsetFraction = (firstVisibleIndex.toFloat() / maxFirst.toFloat()).coerceIn(0f, 1f)
    var trackHeightPx by remember(totalCount, visibleCount) { mutableStateOf(1) }
    var visible by remember(totalCount, visibleCount) { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var hasScrollActivity by remember(totalCount, visibleCount) { mutableStateOf(false) }
    val thumbAlpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, label = "scrollThumbAlpha")
    val currentOnDragToIndex by rememberUpdatedState(onDragToIndex)
    val currentMaxFirst by rememberUpdatedState(maxFirst)
    val currentTrackHeightPx by rememberUpdatedState(trackHeightPx)
    val scrollSignature = remember(firstVisibleIndex, firstVisibleOffset) {
        firstVisibleIndex to firstVisibleOffset
    }

    LaunchedEffect(scrollSignature, scrollInProgress, dragging) {
        if (scrollInProgress || dragging) hasScrollActivity = true
        if (!hasScrollActivity) return@LaunchedEffect
        visible = true
        if (!scrollInProgress && !dragging) {
            delay(SCROLL_THUMB_IDLE_HIDE_MS)
            visible = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .padding(start = 16.dp, end = 2.dp, top = 28.dp, bottom = 28.dp)
            .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
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
                            var lastDispatchTimeMs = 0L

                            fun calculateIndex(y: Float): Int {
                                val safeTrackHeightPx = currentTrackHeightPx.coerceAtLeast(1)
                                val safeMaxFirst = currentMaxFirst.coerceAtLeast(1)
                                return ((y.coerceIn(0f, safeTrackHeightPx.toFloat()) / safeTrackHeightPx.toFloat()) * safeMaxFirst)
                                    .roundToInt()
                                    .coerceIn(0, safeMaxFirst)
                            }

                            fun dispatch(y: Float, force: Boolean = false) {
                                val targetIndex = calculateIndex(y)
                                val now = SystemClock.uptimeMillis()
                                if (!force && targetIndex == lastIndex) return
                                if (!force && now - lastDispatchTimeMs < SCROLL_THUMB_DRAG_THROTTLE_MS) return
                                lastIndex = targetIndex
                                lastDispatchTimeMs = now
                                targetIndices.trySend(targetIndex)
                            }

                            val down = awaitFirstDown(requireUnconsumed = false)
                            dragging = true
                            visible = true
                            down.consume()
                            dispatch(down.position.y, force = true)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.changedToUpIgnoreConsumed()) {
                                    change.consume()
                                    break
                                }
                                if (change.pressed) {
                                    change.consume()
                                    dispatch(change.position.y)
                                }
                            }
                            dragging = false
                        }
                    } finally {
                        dragging = false
                        targetIndices.close()
                        scrollWorker.cancel()
                    }
                }
            }
    ) {
        val thumbHeight = maxHeight * visibleFraction
        val thumbOffset = (maxHeight - thumbHeight) * offsetFraction
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = thumbOffset)
                .height(thumbHeight.coerceAtLeast(24.dp))
                .width(4.dp)
                .alpha(thumbAlpha)
                .clip(RoundedCornerShape(999.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.48f))
        )
    }
}

private const val SCROLL_THUMB_DRAG_THROTTLE_MS = 24L
private const val SCROLL_THUMB_IDLE_HIDE_MS = 1_000L

private data class ScrollIndicatorInfo(
    val firstVisibleIndex: Int,
    val firstVisibleOffset: Int,
    val visibleCount: Int,
    val totalCount: Int
)
