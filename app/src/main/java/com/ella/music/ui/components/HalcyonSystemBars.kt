package com.ella.music.ui.components

import android.app.Activity
import android.view.View
import android.view.ViewParent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ella.music.data.SettingsManager

internal fun Window.applyHalcyonSystemBars(mode: Int) {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    val controller = WindowInsetsControllerCompat(this, decorView)
    controller.show(WindowInsetsCompat.Type.systemBars())
    if (mode != SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    when (mode) {
        SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
        SettingsManager.SYSTEM_BARS_MODE_HIDE_NAVIGATION ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
internal fun ApplyHalcyonSystemBarsToCurrentWindow() {
    val view = LocalView.current
    val context = LocalContext.current
    val mode by SettingsManager.getInstance(context).systemBarsMode.collectAsState(
        initial = SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH
    )
    DisposableEffect(view, mode) {
        view.findHostWindow()?.applyHalcyonSystemBars(mode)
        onDispose { }
    }
}

private fun View.findHostWindow(): Window? {
    var current: ViewParent? = parent
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent
    }
    return (context as? Activity)?.window
}
