package com.ella.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun PlayerPagerSyncEffects(
    immersiveAlbumCover: Boolean,
    showLyrics: Boolean,
    pagerState: PagerState,
    onShowLyricsChange: (Boolean) -> Unit
) {
    LaunchedEffect(showLyrics) {
        if (immersiveAlbumCover) return@LaunchedEffect
        val target = if (showLyrics) PLAYER_PAGE_LYRICS else PLAYER_PAGE_COVER
        if (showLyrics && pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        } else if (!showLyrics && pagerState.currentPage == PLAYER_PAGE_LYRICS && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (immersiveAlbumCover) return@LaunchedEffect
        val lyricPageVisible = pagerState.currentPage == PLAYER_PAGE_LYRICS
        if (showLyrics != lyricPageVisible) {
            onShowLyricsChange(lyricPageVisible)
        }
    }
    LaunchedEffect(immersiveAlbumCover) {
        if (immersiveAlbumCover && pagerState.currentPage != PLAYER_PAGE_COVER) {
            onShowLyricsChange(false)
            pagerState.scrollToPage(PLAYER_PAGE_COVER)
        }
    }
}

@Composable
internal fun PlayerScreenPageHost(
    immersiveAlbumCover: Boolean,
    showLyrics: Boolean,
    pagerState: PagerState,
    userScrollEnabled: Boolean,
    onShowImmersiveLyrics: () -> Unit,
    onDismissImmersiveLyrics: () -> Unit,
    onShowPagedLyrics: () -> Unit,
    onDismissPagedLyrics: () -> Unit,
    coverPage: @Composable (onShowLyrics: () -> Unit, Modifier) -> Unit,
    lyricsPage: @Composable (onDismissLyrics: () -> Unit, enableSwipeDismiss: Boolean, backEnabled: Boolean, Modifier) -> Unit,
    detailPage: @Composable (Modifier) -> Unit,
    playerVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (immersiveAlbumCover) {
        // Keep the cover resident so its image/video state is not recreated after lyrics close,
        // but hide it immediately before composing the lyric page. This prevents the cover's mini
        // lyric layer from showing underneath the full lyric layer.
        Box(modifier = modifier.fillMaxSize()) {
            coverPage(
                onShowImmersiveLyrics,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (showLyrics) 0f else 1f }
            )
            if (showLyrics) {
            lyricsPage(
                onDismissImmersiveLyrics,
                true,
                true,
                Modifier.fillMaxSize()
            )
            }
        }
    } else {
        // Only intercept back to return the pager to the cover page while the player surface is
        // actually visible. The player stays resident (composed, slid off-screen) after it's closed;
        // if this handler stayed enabled while the pager was parked on a side page (e.g. after
        // tapping an artist/album on the info page navigated away), it would swallow the first back
        // press on the destination screen, requiring a second press to actually go back.
        BackHandler(enabled = playerVisible && pagerState.currentPage != PLAYER_PAGE_COVER) {
            onDismissPagedLyrics()
        }
        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize(),
            userScrollEnabled = userScrollEnabled,
            // Keep adjacent pages alive while swiping so the cover never briefly tears down
            // before returning from lyrics or details.
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                PLAYER_PAGE_COVER -> coverPage(
                    onShowPagedLyrics,
                    Modifier.fillMaxSize()
                )
                PLAYER_PAGE_LYRICS -> lyricsPage(
                    onDismissPagedLyrics,
                    false,
                    false,
                    Modifier.fillMaxSize()
                )
                PLAYER_PAGE_DETAILS -> detailPage(Modifier.fillMaxSize())
            }
        }
    }
}

internal const val PLAYER_PAGE_DETAILS = 0
internal const val PLAYER_PAGE_COVER = 1
internal const val PLAYER_PAGE_LYRICS = 2
internal const val PLAYER_PAGE_COUNT = 3
