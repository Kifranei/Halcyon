package com.ella.music.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ella.music.MainActivity
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE
import com.ella.music.ui.navigation.Screen
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Process-wide performance tracking singleton that maintains the [FramePerformanceSampler]
 * across screen navigation and overlay sessions.
 */
internal object PerformanceTracker {
    private var samplerInstance: FramePerformanceSampler? = null

    @Synchronized
    fun get(context: Context): FramePerformanceSampler {
        return samplerInstance ?: FramePerformanceSampler(context.applicationContext).also {
            samplerInstance = it
        }
    }
}

/**
 * Manages the floating performance overlay toolbar with live FPS/jank indicators,
 * recording controls, and navigation shortcuts back to [PerformanceDiagnosticsScreen].
 */
internal object PerformanceFloatingToolbarManager {
    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var lifecycleOwner: PerformanceOverlayLifecycleOwner? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    val isShowingState = mutableStateOf(false)

    fun isShowing(): Boolean = isShowingState.value

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun show(context: Context) {
        if (isShowingState.value) return
        if (!canDrawOverlays(context)) {
            requestOverlayPermission(context)
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val owner = PerformanceOverlayLifecycleOwner()
        lifecycleOwner = owner

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 240
        }
        overlayLayoutParams = params

        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        owner.installOn(root)
        owner.start()

        val composeView = ComposeView(context).apply {
            owner.installOn(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MiuixTheme {
                    val sampler = PerformanceTracker.get(context)
                    val snapshot by sampler.snapshot
                    PerformanceFloatingToolbarContent(
                        snapshot = snapshot,
                        onStartRecording = { sampler.start() },
                        onStopRecording = { sampler.stop() },
                        onJumpBack = {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(EXTRA_SHORTCUT_ROUTE, Screen.PerformanceDiagnostics.route)
                            }
                            context.startActivity(intent)
                        },
                        onClose = { hide() },
                        onDrag = { dx, dy ->
                            overlayLayoutParams?.let { lp ->
                                val dm = context.resources.displayMetrics
                                lp.x = (lp.x + dx.roundToInt()).coerceIn(0, (dm.widthPixels - 100).coerceAtLeast(0))
                                lp.y = (lp.y + dy.roundToInt()).coerceIn(0, (dm.heightPixels - 100).coerceAtLeast(0))
                                runCatching { wm.updateViewLayout(root, lp) }
                            }
                        }
                    )
                }
            }
        }

        root.addView(composeView)
        rootView = root

        runCatching {
            wm.addView(root, params)
            isShowingState.value = true
        }
    }

    fun hide() {
        if (!isShowingState.value) return
        val root = rootView
        val wm = windowManager
        if (root != null && wm != null) {
            runCatching { wm.removeView(root) }
        }
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        rootView = null
        windowManager = null
        isShowingState.value = false
    }

    fun toggle(context: Context) {
        if (isShowingState.value) {
            hide()
        } else {
            show(context)
        }
    }
}

@Composable
internal fun PerformanceFloatingToolbarContent(
    snapshot: PerformanceSnapshot,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onJumpBack: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Row(
        modifier = Modifier
            .shadow(10.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xF0202124))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Drag handle indicator
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(horizontal = 3.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Box(modifier = Modifier.size(3.dp).background(Color(0x88FFFFFF), CircleShape))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Box(modifier = Modifier.size(3.dp).background(Color(0x88FFFFFF), CircleShape))
                    }
                }
            }
        }

        // Live FPS & Jank counter (also drag-responsive)
        Column(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(horizontal = 2.dp)
        ) {
            val fps = snapshot.fps
            val fpsColor = when {
                fps >= 55f -> Color(0xFF52C41A)
                fps >= 40f -> Color(0xFFFAAD14)
                else -> Color(0xFFFF4D4F)
            }
            Text(
                text = "${fps.toInt()} FPS",
                color = fpsColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (snapshot.jankyFrames > 0) "掉帧:${snapshot.jankyFrames}" else "顺畅",
                color = if (snapshot.jankyFrames > 0) Color(0xFFFFB070) else Color(0x99FFFFFF),
                fontSize = 10.sp
            )
        }

        // Start / Stop Recording Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (snapshot.running) Color(0x33FF4D4F) else Color(0x3352C41A))
                .clickable {
                    if (snapshot.running) onStopRecording() else onStartRecording()
                }
                .padding(horizontal = 9.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (snapshot.running) "停止" else "录制",
                color = if (snapshot.running) Color(0xFFFF4D4F) else Color(0xFF52C41A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Jump back to Performance Diagnostics page
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x28FFFFFF))
                .clickable { onJumpBack() }
                .padding(horizontal = 9.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "检测页",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Close Floating Toolbar Button
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Close,
                contentDescription = "关闭",
                tint = Color(0xCCFFFFFF),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

internal class PerformanceOverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val stateController = SavedStateRegistryController.create(this)
    private var destroyed = false

    override val lifecycle: Lifecycle = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = stateController.savedStateRegistry

    init {
        stateController.performAttach()
        stateController.performRestore(null)
    }

    fun installOn(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    fun start() {
        if (destroyed) return
        if (registry.currentState == Lifecycle.State.INITIALIZED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
