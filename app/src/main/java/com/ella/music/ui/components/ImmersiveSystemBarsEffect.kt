package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.ella.music.data.SettingsManager

/** Keep the window immersive through rotation, sheet dismissal and Activity focus callbacks. */
@Composable
internal fun ImmersiveSystemBarsEffect(keepScreenOn: Boolean = false) {
    val activity = LocalContext.current.findActivity() ?: return
    val orientation = LocalConfiguration.current.orientation
    DisposableEffect(activity, keepScreenOn) {
        val window = activity.window
        val view = window.decorView
        val owner = Any()
        window.acquireImmersiveSystemBars(owner, keepScreenOn)
        fun applyBars() = window.applyHalcyonSystemBars(SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH, false)
        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) applyBars()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        applyBars()
        onDispose {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            window.releaseImmersiveSystemBars(owner)?.let { restore ->
                window.applyHalcyonSystemBars(restore.mode, restore.reserveSpace)
            }
        }
    }
    LaunchedEffect(activity, orientation) {
        activity.window.applyHalcyonSystemBars(SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH, false)
    }
}
