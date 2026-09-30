package com.ella.music.web

import android.app.Application
import com.ella.music.data.SettingsManager
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** Real CIO sockets plus the Android service lifecycle from #526. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WebMusicServiceTest {
    private val services = mutableListOf<ServiceController<WebMusicService>>()
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before fun setUp() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
    }

    @After fun tearDown() {
        try {
            services.toList().forEach(::destroy)
            await("The service must release its listening port") { portIsAvailable() }
            assertTrue("No server exception may escape to the application's crash handler: $uncaught", uncaught.isEmpty())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test fun repeatedRestartsReleaseThePortEvenAfterBrowserRequests() {
        repeat(8) {
            val count = logCount("Web music server started on port")
            val service = create()
            await("The next service must bind after the previous one stops") {
                logCount("Web music server started on port") > count
            }
            assertEquals("[]", requestSongs())
            // Restart immediately, before the old service's asynchronous cleanup completes.
            destroy(service)
        }
    }

    @Test fun stoppingDuringStartupDoesNotLeaveAnOrphanListener() {
        repeat(20) { destroy(create()) }
        val count = logCount("Web music server started on port")
        create()
        await("A surviving service must still start after rapid enable/disable") {
            logCount("Web music server started on port") > count
        }
        assertEquals("[]", requestSongs())
    }

    @Test fun lateCleanupOfAnOldServiceDoesNotStopTheNewService() {
        val firstCount = logCount("Web music server started on port")
        val first = create()
        await("First service starts") { logCount("Web music server started on port") > firstCount }
        val secondCount = logCount("Web music server started on port")
        create()
        await("Replacement service starts") { logCount("Web music server started on port") > secondCount }
        val stops = logCount("Web music server stopped")
        destroy(first)
        await("The old service finishes its delayed cleanup") { logCount("Web music server stopped") > stops }
        assertEquals("[]", requestSongs())
    }

    @Test fun occupiedPortDisablesTheSettingWithoutCrashingTheApplication() {
        ServerSocket().use { blocker ->
            blocker.reuseAddress = true
            blocker.bind(InetSocketAddress("0.0.0.0", WebMusicService.PORT))
            val settings = SettingsManager.getInstance(RuntimeEnvironment.getApplication())
            runBlocking { settings.setWebMusicServerEnabled(true) }
            val service = create()
            await("Startup failure must stop the foreground service") { shadowOf(service.get()).isStoppedBySelf }
            assertFalse(runBlocking { settings.webMusicServerEnabled.first() })
            assertTrue("A bind failure must not reach the application's crash handler", uncaught.isEmpty())
        }
    }

    private fun create(): ServiceController<WebMusicService> =
        Robolectric.buildService(WebMusicService::class.java).create().also { services += it }

    private fun destroy(service: ServiceController<WebMusicService>) {
        if (services.remove(service)) service.destroy()
    }

    private fun logCount(prefix: String) = ShadowLog.getLogsForTag("WebMusicService").count { it.msg.startsWith(prefix) }

    private fun requestSongs(): String {
        val connection = URL("http://127.0.0.1:${WebMusicService.PORT}/api/songs").openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.setRequestProperty("Connection", "close")
        return try {
            assertEquals(200, connection.responseCode)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun portIsAvailable(): Boolean = runCatching {
        ServerSocket().use {
            it.reuseAddress = true
            it.bind(InetSocketAddress("0.0.0.0", WebMusicService.PORT))
        }
    }.isSuccess

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            if (System.nanoTime() >= deadline) {
                val logs = ShadowLog.getLogsForTag("WebMusicService").joinToString("\n") { "${it.msg}: ${it.throwable}" }
                fail("$message; uncaught=$uncaught\n$logs")
            }
            Thread.sleep(10)
        }
    }
}
