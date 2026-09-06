package com.ella.music.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
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

// Window-scoped override survives Activity focus/theme callbacks while the player is visible.
private val playerImmersiveWindows = java.util.WeakHashMap<Window, Boolean>()

internal fun Window.setPlayerImmersiveOverride(enabled: Boolean) {
    if (enabled) playerImmersiveWindows[this] = true else playerImmersiveWindows.remove(this)
}

internal fun Window.applyHalcyonSystemBars(mode: Int) {
    val effectiveMode = if (playerImmersiveWindows[this] == true) SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH else mode
    WindowCompat.setDecorFitsSystemWindows(this, false)
    navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        setNavigationBarDividerColor(Color.TRANSPARENT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isNavigationBarContrastEnforced = false
    }
    val controller = WindowInsetsControllerCompat(this, decorView)
    if (effectiveMode != SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH) controller.show(WindowInsetsCompat.Type.systemBars())
    if (effectiveMode != SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    when (effectiveMode) {
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
        onDispose {
            // WindowBottomSheet owns a separate window. Restore the activity window immediately
            // when it disappears so gesture navigation never inherits the sheet's white bar.
            context.findActivity()?.window?.applyHalcyonSystemBars(mode)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun View.findHostWindow(): Window? {
    var current: ViewParent? = parent
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent
    }
    return (context as? Activity)?.window
}
