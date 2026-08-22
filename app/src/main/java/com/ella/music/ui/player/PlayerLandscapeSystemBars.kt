package com.ella.music.ui.player

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
internal fun ForceLandscapePlayerBars(
    onDismiss: () -> Unit,
    interceptBack: Boolean = true
) {
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    DisposableEffect(activity) {
        val oldOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setPlayerSystemBars(activity, view)
        onDispose {
            if (oldOrientation != null) {
                activity.requestedOrientation = oldOrientation
            }
            setPlayerSystemBars(activity, view)
            view.post { setPlayerSystemBars(activity, view) }
        }
    }
    BackHandler(enabled = interceptBack, onBack = onDismiss)
}
