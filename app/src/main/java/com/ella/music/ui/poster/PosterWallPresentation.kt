package com.ella.music.ui.poster

import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.ui.components.ImmersiveSystemBarsEffect
import com.ella.music.R
import com.ella.music.ui.player.findActivity
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.theme.LocalContentColor

@Composable
internal fun PosterWallSearch(query: String, onQueryChange: (String) -> Unit, onDismiss: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        InputField(query = query, onQueryChange = onQueryChange, onSearch = { keyboard?.hide(); focus.clearFocus() },
            expanded = true, onExpandedChange = { if (!it) onDismiss() },
            label = stringResource(R.string.poster_wall_search), color = PosterPanel,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp))
    }
}

/** Both immersive surfaces restore the caller's orientation and bar visibility on exit. */
@Composable
internal fun PosterImmersiveWindow(landscape: Boolean) {
    ImmersiveSystemBarsEffect(keepScreenOn = true)
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, landscape) {
        val oldOrientation = activity.requestedOrientation
        if (landscape) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity.requestedOrientation = oldOrientation
        }
    }
}
