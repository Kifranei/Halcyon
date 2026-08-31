package com.ella.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.content.pm.PackageManager
import com.ella.music.ui.listmodel.MusicSortKeyCache
import java.io.File
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.activity.viewModels
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ella.music.ui.player.PlayerPalette
import com.ella.music.ui.player.coverContentColor
import com.ella.music.ui.player.loadPaletteCoverBitmap
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.components.ScriptFontPaths
import com.ella.music.ui.components.applyHalcyonSystemBars
import com.ella.music.ui.theme.MONET_COVER
import com.ella.music.ui.theme.THEME_DARK
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import com.ella.music.oem.XiaomiHandoffBridge
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    private val startupMainViewModel: MainViewModel by viewModels()
    private val startupPlayerViewModel: PlayerViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestNotificationPermissionIfNeeded()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        lifecycleScope.launch {
            SettingsManager.getInstance(applicationContext).setNotificationPermissionPromptHandled(true)
            if (!granted) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.notification_permission_denied_hint),
                    Toast.LENGTH_LONG
                ).show()
            }
            notificationPermissionRequestInFlight = false
        }
    }

    private var notificationPermissionRequestInFlight = false

    private var mainViewModel: MainViewModel? = null
    private var appliedLanguageTag: String? = null
    private var appliedSystemBarsMode = SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH
    private var currentSystemNightMode by mutableIntStateOf(Configuration.UI_MODE_NIGHT_UNDEFINED)
    var latestIntent: Intent? = null
        private set
    var onNewIntentCallback: ((Intent) -> Unit)? = null

    private var xiaomiHandoffBridge: XiaomiHandoffBridge? = null

    override fun attachBaseContext(newBase: Context) {
        val language = runBlocking(Dispatchers.IO) {
            SettingsManager.getInstance(newBase).appLanguage.first()
        }
        appliedLanguageTag = language
        super.attachBaseContext(newBase.withHalcyonLocale(language))
    }

    override fun onStop() {
        super.onStop()
        // Flush any newly computed A-Z sort keys so the next cold launch reuses them.
        lifecycleScope.launch(Dispatchers.IO) { MusicSortKeyCache.persist() }
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        latestIntent = intent
        xiaomiHandoffBridge = XiaomiHandoffBridge(this) {
            val song = startupPlayerViewModel.currentSong.value
            Uri.Builder()
                .scheme("halcyon")
                .authority(if (song == null) "home" else "player")
                .appendPath("main")
                .apply {
                    if (song != null) {
                        appendQueryParameter("id", song.id.toString())
                        appendQueryParameter("path", song.path)
                        appendQueryParameter("position", startupPlayerViewModel.currentPosition.value.toString())
                    }
                }
                .build()
        }.also { it.publish() }
        MusicSortKeyCache.configure(File(filesDir, "music_sort_keys.json"))
        currentSystemNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // DataStore is read before Compose creates its first frame.  Rendering hard-coded defaults
        // and replacing them a frame later made every cold launch visibly jump between themes and
        // home layouts, while delaying the ViewModels behind a spinner made the app feel slower
        // than the actual restore work.  Keep Android's system splash until this small snapshot is
        // ready, then compose the real configured UI directly.
        val settingsManager = SettingsManager.getInstance(this)
        val startupAppearance = runBlocking(Dispatchers.IO) {
            StartupAppearance(
                themeMode = settingsManager.themeMode.first(),
                appLanguage = settingsManager.appLanguage.first(),
                legacyAppFontPath = settingsManager.lyricFontPath.first(),
                globalWesternFontPath = settingsManager.globalWesternFontPath.first(),
                globalCjkFontPath = settingsManager.globalCjkFontPath.first(),
                appFontWeight = settingsManager.lyricFontWeight.first(),
                monetMode = settingsManager.monetColorMode.first(),
                systemBarsMode = settingsManager.systemBarsMode.first(),
                systemBarsReserveSpace = settingsManager.systemBarsReserveSpace.first()
            )
        }
        appliedSystemBarsMode = startupAppearance.systemBarsMode
        window.applyHalcyonSystemBars(appliedSystemBarsMode)
        val mainVm = startupMainViewModel
        val playerVm = startupPlayerViewModel
        runBlocking { mainVm.awaitInitialLibraryRestore() }
        mainViewModel = mainVm

        setContent {
            val themeMode by settingsManager.themeMode.collectAsState(initial = startupAppearance.themeMode)
            val appLanguage by settingsManager.appLanguage.collectAsState(
                initial = startupAppearance.appLanguage
            )
            val legacyAppFontPath by settingsManager.lyricFontPath.collectAsState(initial = startupAppearance.legacyAppFontPath)
            val globalWesternFontPath by settingsManager.globalWesternFontPath.collectAsState(initial = startupAppearance.globalWesternFontPath)
            val globalCjkFontPath by settingsManager.globalCjkFontPath.collectAsState(initial = startupAppearance.globalCjkFontPath)
            val appFontWeight by settingsManager.lyricFontWeight.collectAsState(initial = startupAppearance.appFontWeight)
            val appFontPath = remember(legacyAppFontPath, globalWesternFontPath, globalCjkFontPath) {
                val western = globalWesternFontPath.ifBlank { legacyAppFontPath }
                if (western.isBlank() && globalCjkFontPath.isBlank()) {
                    ""
                } else {
                    ScriptFontPaths(western, globalCjkFontPath).encode()
                }
            }
            val monetMode by settingsManager.monetColorMode.collectAsState(initial = startupAppearance.monetMode)
            val systemBarsMode by settingsManager.systemBarsMode.collectAsState(
                initial = startupAppearance.systemBarsMode
            )
            val systemBarsReserveSpace by settingsManager.systemBarsReserveSpace.collectAsState(
                initial = startupAppearance.systemBarsReserveSpace
            )
            val monetSong by produceState<Song?>(null, playerVm) {
                playerVm.currentSong.collect { value = it }
            }
            val playerCoverContentColor by settingsManager.playerCoverContentColor.collectAsState(initial = false)
            val systemDark = when (currentSystemNightMode) {
                Configuration.UI_MODE_NIGHT_YES -> true
                Configuration.UI_MODE_NIGHT_NO -> false
                else -> isSystemInDarkTheme()
            }
            val isDark = when (themeMode) {
                THEME_DARK -> true
                THEME_FOLLOW_SYSTEM -> systemDark
                else -> false
            }
            // Seed color for cover-based Monet: extract a representative color from the current cover.
            val coverSeed by produceState<ComposeColor?>(
                null,
                monetMode,
                monetSong?.id,
                playerCoverContentColor,
                isDark
            ) {
                val song = monetSong
                value = if (monetMode == MONET_COVER && song != null) {
                    withContext(Dispatchers.IO) {
                        // Global cover-based Monet must use the same song-specific artwork as the
                        // player. MediaStore's album URI can point at a sibling track when one
                        // album contains several different embedded covers.
                        val bitmap = playerVm.getCoverArtBitmap(song)
                            ?: loadPaletteCoverBitmap(this@MainActivity, song)
                        if (playerCoverContentColor) {
                            PlayerPalette.from(bitmap, light = !isDark).coverContentColor()
                        } else {
                            PlayerPalette.seedColor(bitmap)
                        }
                    }
                } else {
                    null
                }
            }

            LaunchedEffect(appLanguage) {
                if (applyAppLanguage(appLanguage)) {
                    delay(260L)
                    if (!isFinishing && !isDestroyed) recreate()
                }
            }

            val view = LocalView.current
            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { isDark },
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }

                onDispose {}
            }

            LaunchedEffect(isDark) {
                val window = (view.context as ComponentActivity).window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }

            LaunchedEffect(systemBarsMode, isDark) {
                appliedSystemBarsMode = systemBarsMode
                (view.context as ComponentActivity).window.applyHalcyonSystemBars(systemBarsMode)
            }

            LaunchedEffect(Unit) {
                checkAndRequestPermissions()
            }

            LaunchedEffect(mainVm, playerVm) {
                if (!startupPlaybackHandled) {
                    startupPlaybackHandled = true
                    when (settingsManager.startupPlayMode.first()) {
                        SettingsManager.STARTUP_PLAY_RANDOM -> {
                            val songs = mainVm.songs.first { it.isNotEmpty() }
                            if (playerVm.currentSong.value == null && !playerVm.hasSavedPlaybackQueue()) {
                                val startIndex = songs.indices.random()
                                playerVm.setPlaylist(songs, startIndex)
                            }
                        }
                        SettingsManager.STARTUP_PLAY_RESUME -> {
                            if (playerVm.currentSong.value == null && playerVm.hasSavedPlaybackQueue()) {
                                playerVm.playRestoredQueue()
                            }
                        }
                    }
                }
            }

            EllaTheme(
                themeMode = themeMode,
                appFontPath = appFontPath,
                appFontWeight = appFontWeight,
                monetMode = monetMode,
                keyColor = coverSeed,
                systemDarkOverride = systemDark
            ) {
                val televisionDevice = remember { isTelevisionDevice() }
                val televisionFocusRequester = remember { FocusRequester() }
                val televisionFocusManager = LocalFocusManager.current
                LaunchedEffect(televisionDevice) {
                    if (televisionDevice) {
                        televisionFocusRequester.requestFocus()
                        // The outer focus target receives the first key event so media keys work
                        // before a screen-specific control has focus.  Move into the nearest
                        // content control after the first frame so a TV remote can immediately
                        // use DPAD/OK without a touch screen gesture.
                        delay(80L)
                        if (!televisionFocusManager.moveFocus(FocusDirection.Enter)) {
                            televisionFocusManager.moveFocus(FocusDirection.Next)
                        }
                    }
                }
                val reservedHiddenBarInsets = if (systemBarsReserveSpace) {
                    when (systemBarsMode) {
                        SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS ->
                            WindowInsets.statusBarsIgnoringVisibility
                        SettingsManager.SYSTEM_BARS_MODE_HIDE_NAVIGATION ->
                            WindowInsets.navigationBarsIgnoringVisibility
                        SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH ->
                            WindowInsets.statusBarsIgnoringVisibility.union(
                                WindowInsets.navigationBarsIgnoringVisibility
                            )
                        else -> null
                    }
                } else {
                    null
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (televisionDevice) {
                                Modifier
                                    .focusRequester(televisionFocusRequester)
                                    .focusable()
                                    .onPreviewKeyEvent { keyEvent ->
                                        val nativeEvent = keyEvent.nativeKeyEvent
                                        val handled = when (nativeEvent.keyCode) {
                                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                                            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                                            android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                                            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> true
                                            else -> false
                                        }
                                        if (handled && nativeEvent.action == android.view.KeyEvent.ACTION_UP) {
                                            when (nativeEvent.keyCode) {
                                                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> playerVm.skipToNext()
                                                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> playerVm.skipToPrevious()
                                                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> playerVm.pauseForMusicVideo()
                                                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> playerVm.resumeAfterMusicVideo()
                                                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> playerVm.togglePlayPause()
                                            }
                                        }
                                        handled
                                    }
                            } else {
                                Modifier
                            }
                        )
                        .background(MiuixTheme.colorScheme.background)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (reservedHiddenBarInsets != null) {
                                    Modifier.windowInsetsPadding(reservedHiddenBarInsets)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = ComposeColor.Transparent,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            EllaApp(mainVm, playerVm, isDark)
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.applyHalcyonSystemBars(appliedSystemBarsMode)
            // Some OEM permission controllers ignore a launcher call made while the first
            // Activity window is still losing focus. Retry from the first focused frame.
            requestNotificationPermissionIfNeeded()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentSystemNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        window.applyHalcyonSystemBars(appliedSystemBarsMode)
    }

    override fun onPostResume() {
        super.onPostResume()
        requestNotificationPermissionIfNeeded()
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
            false
        } else true
    }

    /**
     * Runtime notification permission requests are Activity operations. Waiting until the
     * Activity is resumed avoids launching from the first Compose frame, which is ignored by
     * several Android/OEM permission controllers. The audio permission callback retries this
     * after a competing permission dialog has finished.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isFinishing ||
            isDestroyed ||
            !hasWindowFocus() ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            notificationPermissionRequestInFlight
        ) return

        notificationPermissionRequestInFlight = true
        lifecycleScope.launch {
            val settings = SettingsManager.getInstance(applicationContext)
            val handled = withContext(Dispatchers.IO) {
                settings.notificationPermissionPromptHandled.first()
            }
            if (handled || ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (!handled) settings.setNotificationPermissionPromptHandled(true)
                notificationPermissionRequestInFlight = false
                return@launch
            }

            delay(250L)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || isFinishing || isDestroyed) {
                notificationPermissionRequestInFlight = false
                return@launch
            }
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }.onFailure {
                notificationPermissionRequestInFlight = false
            }
        }
    }

    private fun applyAppLanguage(languageTag: String): Boolean {
        if (appliedLanguageTag == languageTag) return false
        appliedLanguageTag = languageTag
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
        xiaomiHandoffBridge?.onNewIntent(intent)
        onNewIntentCallback?.invoke(intent)
    }

    override fun onDestroy() {
        xiaomiHandoffBridge?.cancel()
        xiaomiHandoffBridge = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_MEDIA_NOTIFICATION_CLICK = "com.ella.music.action.MEDIA_NOTIFICATION_CLICK"
        const val EXTRA_OPEN_PLAYER_FROM_NOTIFICATION = "open_player_from_notification"
        private var startupPlaybackHandled = false
    }

    private fun isTelevisionDevice(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION

    private data class StartupAppearance(
        val themeMode: Int,
        val appLanguage: String,
        val legacyAppFontPath: String,
        val globalWesternFontPath: String,
        val globalCjkFontPath: String,
        val appFontWeight: Int,
        val monetMode: Int,
        val systemBarsMode: Int,
        val systemBarsReserveSpace: Boolean
    )
}
