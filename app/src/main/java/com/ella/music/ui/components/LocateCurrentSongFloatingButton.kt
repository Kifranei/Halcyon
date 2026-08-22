package com.ella.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LocateCurrentSongFloatingButton(
    listState: LazyListState,
    currentItemIndex: Int,
    locateRequest: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scope = rememberCoroutineScope()
    var handledLocateRequest by remember {
        mutableIntStateOf(if (currentItemIndex >= 0) locateRequest else 0)
    }
    val visible by remember(currentItemIndex, enabled) {
        derivedStateOf {
            if (!enabled || currentItemIndex < 0) return@derivedStateOf false
            val visibleIndexes = listState.layoutInfo.visibleItemsInfo.map { it.index }
            if (visibleIndexes.isEmpty()) return@derivedStateOf false
            visibleIndexes.none { kotlin.math.abs(it - currentItemIndex) <= 2 }
        }
    }

    LaunchedEffect(locateRequest, currentItemIndex) {
        if (!shouldHonorLocateCurrentSongRequest(locateRequest, handledLocateRequest, currentItemIndex)) {
            return@LaunchedEffect
        }
        handledLocateRequest = locateRequest
        listState.animateScrollToItem(currentItemIndex)
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = {
                if (currentItemIndex >= 0) {
                    scope.launch { listState.animateScrollToItem(currentItemIndex) }
                }
            },
            minWidth = 46.dp,
            minHeight = 46.dp,
            containerColor = MiuixTheme.colorScheme.primary
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_my_location),
                contentDescription = stringResource(R.string.player_locate_current_song),
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

internal fun shouldHonorLocateCurrentSongRequest(
    locateRequest: Int,
    handledRequest: Int,
    currentItemIndex: Int
): Boolean = locateRequest > 0 && locateRequest != handledRequest && currentItemIndex >= 0
