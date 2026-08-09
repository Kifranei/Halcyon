package com.ella.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.ella.music.data.DesktopLyricSettings
import com.ella.music.data.SettingsManager
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ella.music.R
import com.ella.music.ui.components.ScriptFontPaths
import com.ella.music.ui.player.ensureBundledMiSansBoldPath
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class DesktopLyricService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private var rootView: LinearLayout? = null
    private var lyricView: DesktopComposeLyricView? = null
    private var controlsView: LinearLayout? = null
    private var playPauseButton: ImageButton? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var locked = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var fontScale = 1f
    private var movedDuringTouch = false
    private var lastTapTimeMs = 0L
    private var translationScale = 1.1f
    private var opacityPercent = 100
    private var lyricTextColor = Color.WHITE
    private var statusBarMode = false
    private var hideWhenPaused = true
    private var hideInLandscape = false
    private var hideOnPlayerPage = false
    private var hideOnLyricsPage = false
    private var hostPage = HOST_PAGE_NONE
    private var desktopLyricWidthPercent = 72
    private var statusBarTopOffsetDp = 16
    private var statusBarPosition = SettingsManager.DESKTOP_LYRIC_STATUS_POSITION_CENTER
    private var statusBarWidthPercent = 72
    private var statusBarXOffsetDp = 0
    private var statusBarTextAlign = SettingsManager.DESKTOP_LYRIC_STATUS_ALIGN_LEFT
    private var statusBarVerticalAlign = SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_TOP
    private var statusBarSecondaryMode = SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF
    private var statusBarSecondaryOpacity = 67
    private var statusBarMergeSecondary = false
    private var lyricFontPath = ""
    private var lyricFontWeight = 800
    private var lyricFontItalic = false
    private var appleMusicWordLiftEnabled = true
    private var controllerIsPlaying = false
    private var savedX = Int.MIN_VALUE
    private var savedY = Int.MIN_VALUE
    private val settingsManager by lazy { SettingsManager.getInstance(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settingsLoadJob: Job? = null
    private val startupGate = DesktopLyricStartupGate()
    private var pendingShowIntent: Intent? = null
    private val hideControlsRunnable = Runnable { hideControls() }
    private val panelBackground by lazy {
        GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Color.argb(54, 18, 18, 18))
            setStroke(dp(1), Color.argb(42, 255, 255, 255))
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        loadSettingsFromStoreAsync(applyToExistingView = true)
        controllerFuture = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync().also { future ->
            Futures.addCallback(
                future,
                object : FutureCallback<MediaController> {
                    override fun onSuccess(result: MediaController?) {
                        if (controllerFuture !== future) return
                        controller = result
                        result?.addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                controllerIsPlaying = isPlaying
                                lyricView?.setPlaybackActive(isPlaying)
                                updatePlayPauseIcon()
                                updateStatusBarModeVisibility()
                            }
                        })
                        controllerIsPlaying = result?.isPlaying == true
                        updatePlayPauseIcon()
                        updateStatusBarModeVisibility()
                    }

                    override fun onFailure(t: Throwable) = Unit
                },
                mainExecutor
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENABLE -> {
                userHidden = false
            }
            ACTION_SHOW, ACTION_UPDATE -> showOrUpdate(intent)
            ACTION_SET_HOST_PAGE -> {
                hostPage = intent.getIntExtra(EXTRA_HOST_PAGE, HOST_PAGE_NONE)
                updateStatusBarModeVisibility()
            }
            ACTION_HIDE -> {
                startupGate.clearPendingShow()
                pendingShowIntent = null
                stopSelf()
            }
            ACTION_UNLOCK -> setLocked(false)
            ACTION_APPLY_SETTINGS -> applySettingsFromStore()
            ACTION_RESET_POSITION -> resetPosition()
            ACTION_FONT_SMALLER -> updateFontScale(-0.1f)
            ACTION_FONT_LARGER -> updateFontScale(0.1f)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        settingsLoadJob?.cancel()
        rootView?.removeCallbacks(hideControlsRunnable)
        rootView?.let { runCatching { windowManager.removeView(it) } }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        notificationManager.cancel(NOTIFICATION_ID)
        pendingShowIntent = null
        rootView = null
        lyricView = null
        controlsView = null
        playPauseButton = null
        layoutParams = null
        controller = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOrUpdate(intent: Intent) {
        if (startupGate.shouldDeferShow()) {
            pendingShowIntent = Intent(intent)
            return
        }
        if (!canDrawOverlay()) return
        if (userHidden) {
            stopSelf()
            return
        }
        if (intent.hasExtra(EXTRA_HOST_PAGE)) {
            hostPage = intent.getIntExtra(EXTRA_HOST_PAGE, HOST_PAGE_NONE)
        }
        if (rootView == null) addLyricView()
        updateStatusBarModeVisibility()
        lyricView?.setLyric(
            text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            pronunciation = intent.getStringExtra(EXTRA_PRONUNCIATION).orEmpty(),
            translation = intent.getStringExtra(EXTRA_TRANSLATION).orEmpty(),
            positionMs = intent.getLongExtra(EXTRA_POSITION, 0L),
            lineStartMs = intent.getLongExtra(EXTRA_LINE_START, -1L),
            lineEndMs = intent.getLongExtra(EXTRA_LINE_END, -1L).takeIf { it >= 0L },
            agent = intent.getStringExtra(EXTRA_AGENT).orEmpty(),
            isTtml = intent.getBooleanExtra(EXTRA_IS_TTML, false),
            backgroundText = intent.getStringExtra(EXTRA_BACKGROUND_TEXT).orEmpty(),
            backgroundTranslation = intent.getStringExtra(EXTRA_BACKGROUND_TRANSLATION).orEmpty(),
            backgroundStartMs = intent.getLongExtra(EXTRA_BACKGROUND_START, -1L).takeIf { it >= 0L },
            backgroundEndMs = intent.getLongExtra(EXTRA_BACKGROUND_END, -1L).takeIf { it >= 0L },
            wordTexts = intent.getStringArrayExtra(EXTRA_WORD_TEXTS)?.toList().orEmpty(),
            wordStarts = intent.getLongArrayExtra(EXTRA_WORD_STARTS) ?: LongArray(0),
            wordEnds = intent.getLongArrayExtra(EXTRA_WORD_ENDS) ?: LongArray(0),
            pronunciationWordTexts = intent.getStringArrayExtra(EXTRA_PRONUNCIATION_WORD_TEXTS)?.toList().orEmpty(),
            pronunciationWordStarts = intent.getLongArrayExtra(EXTRA_PRONUNCIATION_WORD_STARTS) ?: LongArray(0),
            pronunciationWordEnds = intent.getLongArrayExtra(EXTRA_PRONUNCIATION_WORD_ENDS) ?: LongArray(0),
            backgroundWordTexts = intent.getStringArrayExtra(EXTRA_BACKGROUND_WORD_TEXTS)?.toList().orEmpty(),
            backgroundWordStarts = intent.getLongArrayExtra(EXTRA_BACKGROUND_WORD_STARTS) ?: LongArray(0),
            backgroundWordEnds = intent.getLongArrayExtra(EXTRA_BACKGROUND_WORD_ENDS) ?: LongArray(0)
        )
        lyricView?.setPlaybackActive(controller?.isPlaying ?: true)
    }

    private fun addLyricView() {
        val statusLyricWidth = statusBarLyricWidth()
        val lyricWidth = if (statusBarMode) {
            statusLyricWidth
        } else {
            desktopLyricWidth()
        }
        val lyricHeight = if (statusBarMode) {
            statusBarLyricHeight()
        } else {
            desktopLyricHeight()
        }
        val lyric = DesktopComposeLyricView(this).apply {
            windowTouchHandler = ::onDrag
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(0))
            addIconControl(R.drawable.ic_skip_previous, getString(R.string.desktop_lyric_previous)) { controller?.seekToPrevious() }
            playPauseButton = addIconControl(R.drawable.ic_player_pause, getString(R.string.desktop_lyric_play_pause)) {
                controller?.let {
                    if (it.isPlaying) it.pause() else it.play()
                    updatePlayPauseIcon()
                }
            }
            addIconControl(R.drawable.ic_skip_next, getString(R.string.desktop_lyric_next)) { controller?.seekToNext() }
            addControl("A-", getString(R.string.desktop_lyric_smaller)) { updateFontScale(-0.08f) }
            addControl("A+", getString(R.string.desktop_lyric_larger)) { updateFontScale(0.08f) }
            if (isTabletDevice()) {
                addControl("↑", getString(R.string.desktop_lyric_pin_top)) { snapToStatusBar() }
            }
            addIconControl(R.drawable.ic_desktop_palette, getString(R.string.desktop_lyric_cycle_color)) { cycleTextColor() }
            addIconControl(R.drawable.ic_desktop_lock, getString(R.string.desktop_lyric_lock)) { setLocked(true) }
            addControl("×", getString(R.string.desktop_lyric_close)) { closeByUser() }
            visibility = View.GONE
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            // 纯文字悬浮窗：不要黑色背景、不要描边、不要阴影卡片
            setPadding(dp(if (statusBarMode) 4 else 8), dp(if (statusBarMode) 0 else 4), dp(if (statusBarMode) 4 else 8), dp(if (statusBarMode) 0 else 4))
            setBackgroundColor(Color.TRANSPARENT)
            elevation = 0f

            // Keep the overlay inside the physical screen; fixed dp widths can overflow on high-density phones.
            addView(lyric, LinearLayout.LayoutParams(lyricWidth, lyricHeight))

            // 控制按钮仍保留，双击歌词时显示
            addView(
                controls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            setOnTouchListener(::onDrag)
        }
        val hadSavedPosition = savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE

        // Must happen before WindowManager attaches the overlay. On some ROMs Compose creates
        // its recomposer while dispatching the root LinearLayout's attach event, before the
        // child view's onAttachedToWindow callback gets a chance to install this owner.
        lyric.installLifecycleOwnerOn(root)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            if (statusBarMode || locked) baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else baseFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = if (statusBarMode) statusBarLyricX(statusLyricWidth) else savedX.takeIf { it != Int.MIN_VALUE } ?: 0
            y = if (statusBarMode) statusBarLyricTopY() else savedY.takeIf { it != Int.MIN_VALUE } ?: desktopLyricDefaultY(root)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        rootView = root
        lyricView = lyric
        controlsView = controls
        layoutParams = params
        applyCurrentSettingsToViews()
        windowManager.addView(root, params)
        clampToScreen(root, params)
        if (!statusBarMode) {
            if (hadSavedPosition) {
                if (params.x != savedX || params.y != savedY) persistPosition(params.x, params.y)
            } else {
                // The display cutout is only available after the overlay is attached. Recenter a
                // first-run/reset window below the camera area once the insets are known, while
                // leaving an explicitly dragged position untouched on later openings.
                root.post {
                    val currentParams = layoutParams ?: return@post
                    if (rootView !== root || hadSavedPosition || savedX != Int.MIN_VALUE || savedY != Int.MIN_VALUE) return@post
                    currentParams.y = desktopLyricDefaultY(root)
                    clampToScreen(root, currentParams)
                    windowManager.updateViewLayout(root, currentParams)
                    persistPosition(currentParams.x, currentParams.y)
                }
            }
        }
        if (statusBarMode) {
            controls.visibility = View.GONE
            setPanelVisible(false)
            notificationManager.cancel(NOTIFICATION_ID)
            updateStatusBarModeVisibility()
        } else if (locked) setLocked(true) else setPanelVisible(false)
    }

    private fun LinearLayout.addControl(label: String, description: String, action: () -> Unit) {
        addView(TextView(context).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(72, 255, 255, 255))
            }
            setPadding(0, 0, 0, dp(1))
            setOnClickListener {
                action()
                if (!locked && rootView != null) scheduleControlsAutoHide()
            }
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        })
    }

    private fun LinearLayout.addIconControl(iconRes: Int, description: String, action: () -> Unit): ImageButton {
        val button = ImageButton(context).apply {
            contentDescription = description
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(72, 255, 255, 255))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                action()
                if (!locked && rootView != null) scheduleControlsAutoHide()
            }
        }
        addView(button, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        })
        return button
    }

    private fun updatePlayPauseIcon() {
        val iconRes = if (controller?.isPlaying == true) {
            R.drawable.ic_player_pause
        } else {
            R.drawable.ic_player_play
        }
        playPauseButton?.setImageResource(iconRes)
    }

    private fun updateFontScale(delta: Float) {
        fontScale = (fontScale + delta).coerceIn(0.8f, 2.2f)
        applyCurrentSettingsToViews()
        serviceScope.launch { settingsManager.setDesktopLyricFontScale((fontScale * 100f).roundToInt()) }
    }

    private fun snapToStatusBar() {
        val view = rootView ?: return
        val params = layoutParams ?: return
        params.x = 0
        params.y = -statusBarHeight() - dp(6)
        clampToScreen(view, params)
        windowManager.updateViewLayout(view, params)
        persistPosition(params.x, params.y)
        if (!locked) scheduleControlsAutoHide()
    }

    private fun resetPosition() {
        val defaultX = 0
        val defaultY = desktopLyricDefaultY()
        savedX = defaultX
        savedY = defaultY
        serviceScope.launch { settingsManager.resetDesktopLyricPosition() }
        val view = rootView ?: return
        val params = layoutParams ?: return
        params.x = defaultX
        params.y = defaultY
        clampToScreen(view, params)
        windowManager.updateViewLayout(view, params)
        if (!locked) scheduleControlsAutoHide()
    }

    private fun cycleTextColor() {
        val currentIndex = desktopLyricQuickColors.indexOf(lyricTextColor).takeIf { it >= 0 } ?: 0
        lyricTextColor = desktopLyricQuickColors[(currentIndex + 1) % desktopLyricQuickColors.size]
        applyCurrentSettingsToViews()
        serviceScope.launch { settingsManager.setDesktopLyricTextColor(lyricTextColor) }
        if (!locked) scheduleControlsAutoHide()
    }

    private fun closeByUser() {
        userHidden = true
        serviceScope.launch { SettingsManager.getInstance(this@DesktopLyricService).setDesktopLyricEnabled(false) }
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
        lyricView = null
        controlsView = null
        layoutParams = null
        stopSelf()
    }

    private fun showControlsWithTimeout() {
        if (statusBarMode) return
        if (locked) return
        controlsView?.visibility = View.VISIBLE
        setPanelVisible(true)
        scheduleControlsAutoHide()
    }

    private fun scheduleControlsAutoHide() {
        rootView?.removeCallbacks(hideControlsRunnable)
        rootView?.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS)
    }

    private fun hideControls() {
        if (!locked) {
            controlsView?.visibility = View.GONE
            setPanelVisible(false)
        }
    }

    private fun setLocked(lock: Boolean, revealControls: Boolean = true) {
        locked = lock
        serviceScope.launch { settingsManager.setDesktopLyricLocked(lock) }
        controlsView?.visibility = when {
            statusBarMode -> View.GONE
            lock -> View.GONE
            revealControls -> View.VISIBLE
            else -> controlsView?.visibility ?: View.GONE
        }
        // Locking only disables touch and hides controls. Preserve the existing lyric surface;
        // changing its background/padding here can make the lyric look dimmer on some overlays.
        if (!lock) setPanelVisible(controlsView?.visibility == View.VISIBLE)
        if (!lock && revealControls) scheduleControlsAutoHide()
        val params = layoutParams ?: return
        // Some overlay implementations inherit a reduced alpha while their touchability/visible
        // state changes. Locking is only a gesture lock; it must not dim the lyric itself.
        params.alpha = 1f
        rootView?.alpha = 1f
        lyricView?.alpha = 1f
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
        params.dimAmount = 0f
        params.flags = if (lock || statusBarMode) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        rootView?.let { windowManager.updateViewLayout(it, params) }
        if (lock && !statusBarMode) postUnlockNotification() else notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun setPanelVisible(visible: Boolean) {
        val root = rootView ?: return
        if (statusBarMode) {
            root.background = null
            root.setPadding(dp(4), 0, dp(4), 0)
            return
        }
        root.background = if (visible) panelBackground else null
        root.setPadding(
            dp(if (visible) 12 else 8),
            dp(if (visible) 8 else 4),
            dp(if (visible) 12 else 8),
            dp(if (visible) 8 else 4)
        )
    }

    private fun onDrag(view: View, event: MotionEvent): Boolean {
        if (statusBarMode) return false
        if (locked) return false
        val windowView = rootView ?: view
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = params.x
                startY = params.y
                movedDuringTouch = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) movedDuringTouch = true
                params.x = startX + dx.toInt()
                params.y = startY + dy.toInt()
                clampToScreen(windowView, params)
                windowManager.updateViewLayout(windowView, params)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!movedDuringTouch) handleTap()
                persistPosition(params.x, params.y)
                return true
            }
        }
        return true
    }

    private fun handleTap() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTapTimeMs <= DOUBLE_TAP_TIMEOUT_MS) {
            showControlsWithTimeout()
            lastTapTimeMs = 0L
        } else {
            lastTapTimeMs = now
        }
    }

    private fun clampToScreen(view: View, params: WindowManager.LayoutParams) {
        if (statusBarMode) {
            params.x = statusBarLyricX(view.width.takeIf { it > 0 } ?: statusBarLyricWidth())
            params.y = statusBarLyricTopY()
            return
        }
        val metrics = resources.displayMetrics
        val halfWidth = ((view.width.takeIf { it > 0 } ?: desktopLyricWidth()) / 2)

        val maxX = (metrics.widthPixels / 2 - halfWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - (view.height.takeIf { it > 0 } ?: desktopLyricHeight())).coerceAtLeast(0)
        params.x = params.x.coerceIn(-maxX, maxX)
        params.y = params.y.coerceIn(-statusBarHeight() - dp(12), maxY)
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun desktopLyricDefaultY(view: View? = rootView): Int {
        val cutoutTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view?.rootWindowInsets?.displayCutout?.safeInsetTop ?: 0
        } else {
            0
        }
        return maxOf(statusBarHeight(), cutoutTop) + dp(8)
    }

    private fun statusBarLyricTopY(): Int =
        (dp(statusBarTopOffsetDp) - statusBarLyricVisualLift()).coerceAtLeast(-dp(8))

    private fun statusBarLyricVisualLift(): Int =
        dp(if (statusBarSecondaryMode == SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF) 6 else 4)

    private fun statusBarLyricWidth(): Int =
        (
            resources.displayMetrics.widthPixels *
                statusBarWidthPercent.coerceIn(
                    DesktopLyricSettings.MIN_WIDTH_PERCENT,
                    DesktopLyricSettings.MAX_WIDTH_PERCENT,
                ) /
                100f
            )
            .roundToInt()
            .coerceIn(dp(160), resources.displayMetrics.widthPixels - dp(16))

    private fun statusBarLyricHeight(): Int =
        if (statusBarSecondaryMode == SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF || statusBarMergeSecondary) {
            dp(104)
        } else {
            dp(150)
        }

    private fun desktopLyricWidth(): Int {
        val metrics = resources.displayMetrics
        val requestedWidth = (
            metrics.widthPixels *
                desktopLyricWidthPercent.coerceIn(
                    DesktopLyricSettings.MIN_WIDTH_PERCENT,
                    DesktopLyricSettings.MAX_WIDTH_PERCENT,
                ) /
                100f
            ).roundToInt()
        val maxWidth = if (isTabletDevice()) {
            metrics.widthPixels - dp(16)
        } else {
            minOf(metrics.widthPixels - dp(72), (metrics.widthPixels * 0.86f).roundToInt())
        }.coerceAtLeast(dp(180))
        val minWidth = minOf(if (isTabletDevice()) dp(280) else dp(180), maxWidth)
        return requestedWidth.coerceIn(minWidth, maxWidth)
    }

    private fun desktopLyricHeight(): Int {
        val metrics = resources.displayMetrics
        if (isTabletDevice()) {
            return (metrics.heightPixels * 0.42f)
                .roundToInt()
                .coerceIn(dp(220), dp(520))
        }
        val maxHeight = minOf(dp(320), (metrics.heightPixels * 0.32f).roundToInt()).coerceAtLeast(dp(128))
        return (metrics.heightPixels * 0.24f)
            .roundToInt()
            .coerceIn(dp(128), maxHeight)
    }

    private fun statusBarLyricX(lyricWidth: Int): Int {
        val sideOffset = ((resources.displayMetrics.widthPixels - lyricWidth) / 2 - dp(12)).coerceAtLeast(0)
        val anchoredX = when (statusBarPosition) {
            SettingsManager.DESKTOP_LYRIC_STATUS_POSITION_LEFT -> -sideOffset
            SettingsManager.DESKTOP_LYRIC_STATUS_POSITION_RIGHT -> sideOffset
            else -> 0
        }
        return anchoredX + dp(statusBarXOffsetDp.coerceIn(-640, 640))
    }

    private fun loadSettingsFromStoreAsync(applyToExistingView: Boolean) {
        settingsLoadJob?.cancel()
        settingsLoadJob = serviceScope.launch {
            val settings = readSettingsFromStore()
            withContext(Dispatchers.Main.immediate) {
                val oldStatusBarMode = statusBarMode
                val oldStatusBarSecondaryMode = statusBarSecondaryMode
                applySettingsSnapshot(settings)
                if (applyToExistingView) {
                    applyCurrentSettingsAfterLoad(oldStatusBarMode, oldStatusBarSecondaryMode)
                }
                if (startupGate.markSettingsLoaded()) {
                    val pendingIntent = pendingShowIntent
                    pendingShowIntent = null
                    pendingIntent?.let(::showOrUpdate)
                }
            }
        }
    }

    private suspend fun readSettingsFromStore(): DesktopLyricSettingsSnapshot {
        val currentStatusBarMode = settingsManager.desktopLyricStatusBarMode.first()
        return DesktopLyricSettingsSnapshot(
        locked = settingsManager.desktopLyricLocked.first(),
        statusBarMode = currentStatusBarMode,
        hideWhenPaused = if (currentStatusBarMode) {
            settingsManager.desktopLyricStatusBarHideWhenPaused.first()
        } else {
            settingsManager.desktopLyricHideWhenPaused.first()
        },
        hideInLandscape = if (currentStatusBarMode) {
            settingsManager.desktopLyricStatusBarHideInLandscape.first()
        } else {
            settingsManager.desktopLyricHideInLandscape.first()
        },
        hideOnPlayerPage = settingsManager.desktopLyricHideOnPlayerPage.first(),
        hideOnLyricsPage = settingsManager.desktopLyricHideOnLyricsPage.first(),
        desktopLyricWidthPercent = settingsManager.desktopLyricWidth.first(),
        statusBarTopOffsetDp = settingsManager.desktopLyricStatusBarTopOffset.first(),
        statusBarPosition = settingsManager.desktopLyricStatusBarPosition.first(),
        statusBarWidthPercent = settingsManager.desktopLyricStatusBarWidth.first(),
        statusBarXOffsetDp = settingsManager.desktopLyricStatusBarXOffset.first(),
        statusBarTextAlign = settingsManager.desktopLyricStatusBarTextAlign.first(),
        statusBarVerticalAlign = settingsManager.desktopLyricStatusBarVerticalAlign.first(),
        statusBarSecondaryMode = settingsManager.desktopLyricStatusBarSecondary.first(),
        statusBarSecondaryOpacity = settingsManager.desktopLyricStatusBarSecondaryOpacity.first(),
        statusBarMergeSecondary = settingsManager.desktopLyricStatusBarMergeSecondary.first(),
        fontScale = (
            if (currentStatusBarMode) {
                settingsManager.desktopLyricStatusBarFontScale.first()
            } else {
                settingsManager.desktopLyricFontScale.first()
            }
            ).coerceIn(80, 220) / 100f,
        translationScale = (
            if (currentStatusBarMode) {
                settingsManager.desktopLyricStatusBarTranslationScale.first()
            } else {
                settingsManager.desktopLyricTranslationScale.first()
            }
            ).coerceIn(80, 220) / 100f,
        opacityPercent = (
            if (currentStatusBarMode) {
                settingsManager.desktopLyricStatusBarOpacity.first()
            } else {
                settingsManager.desktopLyricOpacity.first()
            }
            ).coerceIn(35, 100),
        // The status-bar lyric is another presentation of the desktop lyric. Keep one source of
        // truth for its primary color so switching modes cannot make the text jump to a stale
        // independently stored color.
        lyricTextColor = settingsManager.desktopLyricTextColor.first(),
        // When "apply font to desktop lyric" is off, pass an empty path so the lyric view falls
        // back to the system default typeface instead of the custom lyric font.
        lyricFontPath = if (settingsManager.lyricFontApplyToDesktop.first()) {
            val defaultCjkPath = ensureBundledMiSansBoldPath(this@DesktopLyricService)
            val western = settingsManager.lyricOriginalWesternFontPath.first()
                .ifBlank { settingsManager.lyricWesternFontPath.first() }
                .ifBlank { defaultCjkPath }
            val cjk = settingsManager.lyricOriginalCjkFontPath.first()
                .ifBlank { settingsManager.lyricCjkFontPath.first() }
                .ifBlank { defaultCjkPath }
            ScriptFontPaths(western, cjk).encode()
        } else {
            ""
        },
        lyricFontWeight = settingsManager.lyricFontWeight.first().coerceIn(100, 900),
        lyricFontItalic = settingsManager.lyricFontItalic.first(),
        appleMusicWordLiftEnabled = settingsManager.appleMusicLyricsWordLift.first(),
        savedX = settingsManager.desktopLyricX.first(),
        savedY = settingsManager.desktopLyricY.first()
        )
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateStatusBarModeVisibility()
    }

    private fun applySettingsSnapshot(settings: DesktopLyricSettingsSnapshot) {
        locked = settings.locked
        statusBarMode = settings.statusBarMode
        hideWhenPaused = settings.hideWhenPaused
        hideInLandscape = settings.hideInLandscape
        hideOnPlayerPage = settings.hideOnPlayerPage
        hideOnLyricsPage = settings.hideOnLyricsPage
        desktopLyricWidthPercent = settings.desktopLyricWidthPercent
        statusBarTopOffsetDp = settings.statusBarTopOffsetDp
        statusBarPosition = settings.statusBarPosition
        statusBarWidthPercent = settings.statusBarWidthPercent
        statusBarXOffsetDp = settings.statusBarXOffsetDp
        statusBarTextAlign = settings.statusBarTextAlign
        statusBarVerticalAlign = settings.statusBarVerticalAlign
        statusBarSecondaryMode = settings.statusBarSecondaryMode
        statusBarSecondaryOpacity = settings.statusBarSecondaryOpacity
        statusBarMergeSecondary = settings.statusBarMergeSecondary
        fontScale = settings.fontScale
        translationScale = settings.translationScale
        opacityPercent = settings.opacityPercent
        lyricTextColor = settings.lyricTextColor
        lyricFontPath = settings.lyricFontPath
        lyricFontWeight = settings.lyricFontWeight
        lyricFontItalic = settings.lyricFontItalic
        appleMusicWordLiftEnabled = settings.appleMusicWordLiftEnabled
        savedX = settings.savedX
        savedY = settings.savedY
    }

    private fun applySettingsFromStore() {
        loadSettingsFromStoreAsync(applyToExistingView = true)
    }

    private fun applyCurrentSettingsAfterLoad(
        oldStatusBarMode: Boolean,
        oldStatusBarSecondaryMode: Int
    ) {
        if ((oldStatusBarMode != statusBarMode || (statusBarMode && oldStatusBarSecondaryMode != statusBarSecondaryMode)) && rootView != null) {
            rootView?.let { runCatching { windowManager.removeView(it) } }
            rootView = null
            lyricView = null
            controlsView = null
            layoutParams = null
            addLyricView()
            return
        }
        applyCurrentSettingsToViews()
        setLocked(locked, revealControls = false)
        if (statusBarMode) {
            val view = rootView
            val params = layoutParams
            if (view != null && params != null) {
                lyricView?.layoutParams = lyricView?.layoutParams?.apply {
                    width = statusBarLyricWidth()
                    height = statusBarLyricHeight()
                }
                clampToScreen(view, params)
                windowManager.updateViewLayout(view, params)
            }
        } else {
            val view = rootView
            val params = layoutParams
            if (view != null && params != null) {
                lyricView?.layoutParams = lyricView?.layoutParams?.apply {
                    width = desktopLyricWidth()
                    height = desktopLyricHeight()
                }
                clampToScreen(view, params)
                windowManager.updateViewLayout(view, params)
            }
        }
        updateStatusBarModeVisibility()
    }

    private fun applyCurrentSettingsToViews() {
        lyricView?.setStyle(
            fontScale = fontScale,
            translationScale = translationScale,
            opacityPercent = opacityPercent,
            textColor = lyricTextColor,
            statusBarMode = statusBarMode,
            statusBarSecondaryMode = statusBarSecondaryMode,
            statusBarSecondaryOpacity = statusBarSecondaryOpacity,
            statusBarMergeSecondary = statusBarMergeSecondary,
            statusBarTextAlign = statusBarTextAlign,
            statusBarVerticalAlign = statusBarVerticalAlign,
            lyricFontPath = lyricFontPath,
            lyricFontWeight = lyricFontWeight,
            lyricFontItalic = lyricFontItalic,
            wordLiftEnabled = appleMusicWordLiftEnabled
        )
        rootView?.alpha = 1f
        lyricView?.alpha = 1f
    }

    private fun updateStatusBarModeVisibility() {
        val isPlaying = controller?.isPlaying ?: true
        rootView?.visibility = if (shouldHideOverlay(isPlaying)) View.GONE else View.VISIBLE
    }

    private fun shouldHideOverlay(isPlaying: Boolean): Boolean =
        (hideWhenPaused && !isPlaying) ||
            (hideInLandscape && resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ||
            (hideOnPlayerPage && hostPage == HOST_PAGE_PLAYER) ||
            (hideOnLyricsPage && hostPage == HOST_PAGE_LYRICS)

    private fun persistPosition(x: Int, y: Int) {
        savedX = x
        savedY = y
        serviceScope.launch { settingsManager.setDesktopLyricPosition(x, y) }
    }

    private fun postUnlockNotification() {
        ensureNotificationChannel()
        val intent = PendingIntent.getService(
            this,
            0,
            Intent(this, DesktopLyricService::class.java).setAction(ACTION_UNLOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        notificationManager.notify(
            NOTIFICATION_ID,
            builder
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(getString(R.string.desktop_lyric_locked_title))
                .setContentText(getString(R.string.desktop_lyric_locked_text))
                .setContentIntent(intent)
                .setOngoing(true)
                .setShowWhen(false)
                .build()
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.desktop_lyric_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun canDrawOverlay(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun isTabletDevice(): Boolean = resources.configuration.smallestScreenWidthDp >= 600

    private data class DesktopLyricSettingsSnapshot(
        val locked: Boolean,
        val statusBarMode: Boolean,
        val hideWhenPaused: Boolean,
        val hideInLandscape: Boolean,
        val hideOnPlayerPage: Boolean,
        val hideOnLyricsPage: Boolean,
        val desktopLyricWidthPercent: Int,
        val statusBarTopOffsetDp: Int,
        val statusBarPosition: Int,
        val statusBarWidthPercent: Int,
        val statusBarXOffsetDp: Int,
        val statusBarTextAlign: Int,
        val statusBarVerticalAlign: Int,
        val statusBarSecondaryMode: Int,
        val statusBarSecondaryOpacity: Int,
        val statusBarMergeSecondary: Boolean,
        val fontScale: Float,
        val translationScale: Float,
        val opacityPercent: Int,
        val lyricTextColor: Int,
        val lyricFontPath: String,
        val lyricFontWeight: Int,
        val lyricFontItalic: Boolean,
        val appleMusicWordLiftEnabled: Boolean,
        val savedX: Int,
        val savedY: Int
    )

    companion object {
        const val ACTION_SHOW = "com.ella.music.action.SHOW_DESKTOP_LYRIC"
        const val ACTION_UPDATE = "com.ella.music.action.UPDATE_DESKTOP_LYRIC"
        const val ACTION_ENABLE = "com.ella.music.action.ENABLE_DESKTOP_LYRIC"
        const val ACTION_HIDE = "com.ella.music.action.HIDE_DESKTOP_LYRIC"
        const val ACTION_SET_HOST_PAGE = "com.ella.music.action.SET_DESKTOP_LYRIC_HOST_PAGE"
        const val ACTION_UNLOCK = "com.ella.music.action.UNLOCK_DESKTOP_LYRIC"
        const val ACTION_APPLY_SETTINGS = "com.ella.music.action.APPLY_DESKTOP_LYRIC_SETTINGS"
        const val ACTION_RESET_POSITION = "com.ella.music.action.RESET_DESKTOP_LYRIC_POSITION"
        const val ACTION_FONT_SMALLER = "com.ella.music.action.DESKTOP_LYRIC_FONT_SMALLER"
        const val ACTION_FONT_LARGER = "com.ella.music.action.DESKTOP_LYRIC_FONT_LARGER"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PRONUNCIATION = "pronunciation"
        const val EXTRA_TRANSLATION = "translation"
        const val EXTRA_POSITION = "position"
        const val EXTRA_LINE_START = "line_start"
        const val EXTRA_LINE_END = "line_end"
        const val EXTRA_AGENT = "agent"
        const val EXTRA_IS_TTML = "is_ttml"
        const val EXTRA_BACKGROUND_TEXT = "background_text"
        const val EXTRA_BACKGROUND_TRANSLATION = "background_translation"
        const val EXTRA_BACKGROUND_START = "background_start"
        const val EXTRA_BACKGROUND_END = "background_end"
        const val EXTRA_WORD_TEXTS = "word_texts"
        const val EXTRA_WORD_STARTS = "word_starts"
        const val EXTRA_WORD_ENDS = "word_ends"
        const val EXTRA_PRONUNCIATION_WORD_TEXTS = "pronunciation_word_texts"
        const val EXTRA_PRONUNCIATION_WORD_STARTS = "pronunciation_word_starts"
        const val EXTRA_PRONUNCIATION_WORD_ENDS = "pronunciation_word_ends"
        const val EXTRA_BACKGROUND_WORD_TEXTS = "background_word_texts"
        const val EXTRA_BACKGROUND_WORD_STARTS = "background_word_starts"
        const val EXTRA_BACKGROUND_WORD_ENDS = "background_word_ends"
        const val EXTRA_HOST_PAGE = "host_page"
        const val HOST_PAGE_NONE = 0
        const val HOST_PAGE_PLAYER = 1
        const val HOST_PAGE_LYRICS = 2
        private const val CHANNEL_ID = "ella_desktop_lyric"
        private const val NOTIFICATION_ID = 0x454c4459
        private const val CONTROLS_AUTO_HIDE_MS = 4_000L
        private const val DOUBLE_TAP_TIMEOUT_MS = 360L
        private val desktopLyricQuickColors = intArrayOf(
            Color.WHITE,
            Color.rgb(191, 191, 191),
            Color.rgb(145, 205, 255),
            Color.rgb(3, 169, 244),
            Color.rgb(166, 235, 203),
            Color.rgb(26, 201, 125),
            Color.rgb(179, 136, 255),
            Color.rgb(255, 188, 214),
            Color.rgb(255, 112, 112),
            Color.rgb(255, 224, 150),
            Color.rgb(255, 87, 34)
        )
        private var userHidden = false
    }
}
