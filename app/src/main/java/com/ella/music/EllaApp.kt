package com.ella.music

import android.app.Application
import android.os.Build
import com.ella.music.data.AppLogcatCollector
import com.ella.music.data.AppLogStore
import com.ella.music.data.AppIconManager
import com.ella.music.data.SettingsManager
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.repository.RemoteAudioCache
import com.ella.music.mcp.McpServerService
import com.ella.music.web.WebMusicService
import com.ella.music.oem.AppMemoryTrimAdapter
import com.ella.music.player.PlaybackWidgetUpdater
import com.ella.music.oem.HyperOsFairMemoryAdapter
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.settings.WebDavAutoBackupScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

class EllaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
                .onFailure { AppLogStore.warn(this, "EllaApp", "Unable to exempt hidden APIs", it) }
        }
        WebDavClient.initContext(this)
        RemoteAudioCache.init(this)
        AppLogStore.install(this)
        AppLogcatCollector.start(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogStore.crash(this, thread.name, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        AppLogStore.info(this, "EllaApp", "Application started")
        HyperOsFairMemoryAdapter.initialize(this)
        AppMemoryTrimAdapter.initialize(this)
        com.ella.music.plugin.i18n.PluginLocales.initialize(this)

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settingsManager = SettingsManager.getInstance(this)
        WebDavAutoBackupScheduler.start(this, appScope)

        // Warm the process-wide sort singleton off the main thread. runBlocking here previously
        // stalled Application.onCreate (and cold start) whenever DataStore was slow — especially
        // painful after a huge Navidrome sync left a large prefs/cache footprint.
        // Lists may briefly use defaults then recompose once warmUp finishes (#210/#126/#133).
        appScope.launch {
            runCatching { LibrarySortUiState.warmUp(settingsManager) }
        }

        // Auto-start MCP server if previously enabled
        appScope.launch {
            if (settingsManager.mcpServerEnabled.first()) {
                McpServerService.start(this@EllaApp)
            }
        }
        appScope.launch {
            if (settingsManager.webMusicServerEnabled.first()) {
                if (!WebMusicService.start(this@EllaApp)) {
                    settingsManager.setWebMusicServerEnabled(false)
                }
            }
        }
        appScope.launch {
            settingsManager.appIconStyle
                .distinctUntilChanged()
                .collect { style ->
                    AppIconManager.apply(this@EllaApp, style)
                }
        }
        appScope.launch {
            settingsManager.widgetSafeLayout
                .distinctUntilChanged()
                .collect { enabled ->
                    PlaybackWidgetUpdater.setSafeLayout(this@EllaApp, enabled)
                }
        }
    }
}
