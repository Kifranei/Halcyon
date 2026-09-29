package com.ella.music.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
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

internal var currentAppSystemBarsMode: Int = SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH
    private set
internal var currentAppSystemBarsReserveSpace: Boolean = SettingsManager.DEFAULT_SYSTEM_BARS_RESERVE_SPACE
    private set

internal fun Window.setPlayerImmersiveOverride(enabled: Boolean) {
    if (enabled) playerImmersiveWindows[this] = true else playerImmersiveWindows.remove(this)
}

@Suppress("DEPRECATION")
internal fun Window.applyHalcyonSystemBars(
    mode: Int,
    reserveSpace: Boolean = SettingsManager.DEFAULT_SYSTEM_BARS_RESERVE_SPACE
) {
    val effectiveMode = if (playerImmersiveWindows[this] == true) SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH else mode
    currentAppSystemBarsMode = effectiveMode
    currentAppSystemBarsReserveSpace = reserveSpace

    // Always let the Compose root paint the full window. The reserveSpace choice is applied by
    // the normal content layers using stable insets, while full-window surfaces such as the
    // player background remain behind the hidden bars instead of exposing the platform window
    // background around the app.
    WindowCompat.setDecorFitsSystemWindows(this, false)

    // WindowCompat maps this to the platform layout flags on older Android releases, but a few
    // HyperOS builds restore decorView.systemUiVisibility when the status-bar disable flag is
    // changed. Re-assert the layout bits so a hidden bar never turns into a black/white strip.
    val managedVisibilityFlags =
        View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    var flags = (decorView.systemUiVisibility and managedVisibilityFlags.inv()) or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    if (effectiveMode == SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH ||
        effectiveMode == SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS) {
        flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
    if (effectiveMode == SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH ||
        effectiveMode == SettingsManager.SYSTEM_BARS_MODE_HIDE_NAVIGATION) {
        flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
    decorView.systemUiVisibility = flags

    statusBarColor = Color.TRANSPARENT
    navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        setNavigationBarDividerColor(Color.TRANSPARENT)
        attributes = attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isNavigationBarContrastEnforced = false
    }
    val controller = WindowInsetsControllerCompat(this, decorView)
    // System bars are controlled only through the platform Insets API. In particular, do not
    // mutate HyperOS global settings here: hiding this app's bars must not require Shizuku and
    // must not change the status/navigation bar state of other applications.
    // Crucially, NEVER call controller.show(systemBars()) prior to hide() — doing so causes an
    // unavoidable flicker when transitioning between screens, popping up dropdowns, or closing sheets.
    if (effectiveMode != SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    when (effectiveMode) {
        SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH ->
            controller.show(WindowInsetsCompat.Type.systemBars())
        SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS -> {
            controller.show(WindowInsetsCompat.Type.navigationBars())
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        SettingsManager.SYSTEM_BARS_MODE_HIDE_NAVIGATION -> {
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
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
    val reserveSpace by SettingsManager.getInstance(context).systemBarsReserveSpace.collectAsState(
        initial = SettingsManager.DEFAULT_SYSTEM_BARS_RESERVE_SPACE
    )
    DisposableEffect(view, mode, reserveSpace) {
        val targetMode = if (currentAppSystemBarsMode != SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH) {
            currentAppSystemBarsMode
        } else {
            mode
        }
        view.findHostWindow()?.applyHalcyonSystemBars(targetMode, reserveSpace)
        onDispose {
            // Restore the activity window immediately using currentAppSystemBarsMode when child window disappears
            context.findActivity()?.window?.applyHalcyonSystemBars(currentAppSystemBarsMode, reserveSpace)
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun findWindowForView(view: View): Window? {
    if (view is DialogWindowProvider) return view.window
    var current: ViewParent? = view.parent
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent
    }
    try {
        var cls: Class<*>? = view.javaClass
        while (cls != null && cls != View::class.java && cls != Any::class.java) {
            val field = cls.declaredFields.firstOrNull { it.name == "mWindow" }
            if (field != null) {
                field.isAccessible = true
                val win = field.get(view) as? Window
                if (win != null) return win
            }
            cls = cls.superclass
        }
    } catch (_: Throwable) {}

    var parent = view.parent
    while (parent is View) {
        try {
            var cls: Class<*>? = parent.javaClass
            while (cls != null && cls != View::class.java && cls != Any::class.java) {
                val field = cls.declaredFields.firstOrNull { it.name == "mWindow" }
                if (field != null) {
                    field.isAccessible = true
                    val win = field.get(parent) as? Window
                    if (win != null) return win
                }
                cls = cls.superclass
            }
        } catch (_: Throwable) {}
        parent = parent.parent
    }

    var ctx = view.context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx.window
        ctx = ctx.baseContext
    }
    return (view.context as? Activity)?.window
}

internal fun View.findHostWindow(): Window? = findWindowForView(this)
