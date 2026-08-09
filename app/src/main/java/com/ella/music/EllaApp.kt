package com.ella.music

import android.app.Application
import android.os.Build
import com.ella.music.data.AppLogcatCollector
import com.ella.music.data.AppLogStore
import com.ella.music.data.AppIconManager
import com.ella.music.data.SettingsManager
import com.ella.music.data.webdav.WebDavClient
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
import kotlinx.coroutines.runBlocking
import org.lsposed.hiddenapibypass.HiddenApiBypass

class EllaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
                .onFailure { AppLogStore.warn(this, "EllaApp", "Unable to exempt hidden APIs", it) }
        }
        WebDavClient.initContext(this)
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

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settingsManager = SettingsManager.getInstance(this)
        WebDavAutoBackupScheduler.start(this, appScope)

        // 预热进程级排序单例：进程重启后单例会回到默认值，导致各列表页 collectAsState(initial=...)
        // 先用默认值渲染再被 DataStore 异步值覆盖，表现为"排序乱跳/不记忆"（#210/#126）。
        // #133 的"设置恢复默认"同因——OOM 触发进程重启后单例全回默认。
        runBlocking {
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
                WebMusicService.start(this@EllaApp)
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
