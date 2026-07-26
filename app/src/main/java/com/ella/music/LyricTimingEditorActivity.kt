package com.ella.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.LyricTimingEditorLauncher
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.MONET_COVER
import com.ella.music.ui.theme.THEME_DARK
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel

class LyricTimingEditorActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val song = LyricTimingEditorLauncher.songFrom(intent)
        if (song == null) {
            finish()
            return
        }
        setContent {
            val settings = remember { SettingsManager.getInstance(this) }
            val themeMode by settings.themeMode.collectAsState(initial = THEME_FOLLOW_SYSTEM)
            val monetMode by settings.monetColorMode.collectAsState(initial = 0)
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDark = if (themeMode == THEME_DARK) true else if (themeMode == THEME_FOLLOW_SYSTEM) systemDark else false
            LaunchedEffect(isDark) {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !isDark
            }
            EllaTheme(
                themeMode = themeMode,
                monetMode = if (monetMode == MONET_COVER) 0 else monetMode,
                systemDarkOverride = systemDark
            ) {
                LyricTimingEditorScreen(
                    song = song,
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel,
                    onBack = ::finish
                )
            }
        }
    }
}
