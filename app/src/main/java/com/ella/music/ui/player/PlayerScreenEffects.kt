package com.ella.music.ui.player

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.applyHalcyonSystemBars
import com.ella.music.ui.components.setPlayerImmersiveOverride

@Composable
internal fun PlayerSystemBarsEffect(
    context: Context,
    view: View,
    trigger: Any?,
    landscape: Boolean = false
) {
    val settings = SettingsManager.getInstance(context)
    val hideLandscapeBars by settings.playerLandscapeHideSystemBars.collectAsState(initial = false)
    val globalMode by settings.systemBarsMode.collectAsState(initial = SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH)
    DisposableEffect(view, trigger, landscape, hideLandscapeBars, globalMode) {
        val activity = context.findActivity()
        fun applyBars() {
            val window = activity?.window ?: return
            window.setPlayerImmersiveOverride(landscape && hideLandscapeBars)
            window.applyHalcyonSystemBars(globalMode)
            setPlayerSystemBars(activity, view)
        }
        applyBars()
        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) applyBars()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            activity?.window?.let {
                it.setPlayerImmersiveOverride(false)
                it.applyHalcyonSystemBars(globalMode)
            }
        }
    }
}

@Composable
internal fun PlayerLyricKeepScreenOnEffect(
    view: View,
    showLyrics: Boolean,
    keepScreenOn: Boolean
) {
    DisposableEffect(view, showLyrics, keepScreenOn) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = previousKeepScreenOn || (showLyrics && keepScreenOn)
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }
}

@Composable
internal fun PlayerSurfaceKeepScreenOnEffect(
    view: View,
    keepScreenOn: Boolean
) {
    DisposableEffect(view, keepScreenOn) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = previousKeepScreenOn || keepScreenOn
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }
}
