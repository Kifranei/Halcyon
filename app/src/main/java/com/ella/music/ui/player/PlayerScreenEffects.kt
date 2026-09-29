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
    landscape: Boolean = false,
    isMusicVideoLandscape: Boolean = false
) {
    val settings = SettingsManager.getInstance(context)
    val hideLandscapeBars by settings.playerLandscapeHideSystemBars.collectAsState(initial = false)
    val hideMusicVideoBars by settings.musicVideoImmersiveLyricsHideSystemBars.collectAsState(
        initial = SettingsManager.DEFAULT_MUSIC_VIDEO_IMMERSIVE_LYRICS_HIDE_SYSTEM_BARS
    )
    val globalMode by settings.systemBarsMode.collectAsState(initial = SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH)
    val reserveSystemBarSpace by settings.systemBarsReserveSpace.collectAsState(
        initial = SettingsManager.DEFAULT_SYSTEM_BARS_RESERVE_SPACE
    )
    val playerMode by settings.playerSystemBarsMode.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_SYSTEM_BARS_MODE
    )
    DisposableEffect(
        view,
        trigger,
        landscape,
        isMusicVideoLandscape,
        hideLandscapeBars,
        hideMusicVideoBars,
        globalMode,
        playerMode,
        reserveSystemBarSpace
    ) {
        val activity = context.findActivity()
        fun applyBars() {
            val window = activity?.window ?: return
            val effectiveMode = when {
                isMusicVideoLandscape && hideMusicVideoBars -> SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH
                landscape && hideLandscapeBars -> SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH
                else -> SettingsManager.playerSystemBarsEffectiveMode(playerMode, globalMode)
            }
            window.setPlayerImmersiveOverride(effectiveMode == SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH)
            window.applyHalcyonSystemBars(effectiveMode, reserveSystemBarSpace)
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
                it.applyHalcyonSystemBars(globalMode, reserveSystemBarSpace)
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
