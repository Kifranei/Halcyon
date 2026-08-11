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
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
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
import com.ella.music.ui.player.loadPaletteCoverBitmap
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.components.ScriptFontPaths
import com.ella.music.ui.theme.MONET_COVER
import com.ella.music.ui.theme.THEME_DARK
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import com.ella.music.oem.XiaomiHandoffBridge
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    private val startupMainViewModel: MainViewModel by viewModels()
    private val startupPlayerViewModel: PlayerViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var mainViewModel: MainViewModel? = null
    private var appliedLanguageTag: String? = null
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
            // Seed color for cover-based Monet: extract a representative color from the current cover.
            val coverSeed by produceState<ComposeColor?>(null, monetMode, monetSong?.id) {
                val song = monetSong
                value = if (monetMode == MONET_COVER && song != null) {
                    withContext(Dispatchers.IO) {
                        PlayerPalette.seedColor(loadPaletteCoverBitmap(this@MainActivity, song))
                    }
                } else {
                    null
                }
            }

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
                val window = (view.context as ComponentActivity).window
                val controller = WindowCompat.getInsetsController(window, view)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.show(WindowInsetsCompat.Type.systemBars())
                if (systemBarsMode != SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                when (systemBarsMode) {
                    SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS ->
                        controller.hide(WindowInsetsCompat.Type.statusBars())
                    SettingsManager.SYSTEM_BARS_MODE_HIDE_NAVIGATION ->
                        controller.hide(WindowInsetsCompat.Type.navigationBars())
                    SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH ->
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                }
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
                        EllaApp(mainVm, playerVm, isDark)
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentSystemNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
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

    private companion object {
        var startupPlaybackHandled = false
    }

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
