package com.ella.music.ui.settings

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.view.Choreographer
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaSmallTopAppBar
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Performance diagnostic snapshot mirroring ConePlayer's comprehensive multi-dimensional monitoring:
 * 1. FpsFrameCollector: Instant/Avg FPS, frame time distribution (P50/P95/Max), jank counts, dropped frame rate
 * 2. MemoryCollector: JVM Heap, Native Heap, PSS, USS, RSS, Graphics PSS, System RAM & low memory alert
 * 3. CpuGcCollector: Process CPU usage %, active thread count, ART GC count, pause time, allocated bytes
 * 4. BatteryPowerCollector: Battery level %, temperature °C, charging state, power save, thermal status
 * 5. NetworkCollector: Realtime Rx/Tx speed, total bytes consumed, connection type, metered status
 * 6. StorageIoCollector: Storage capacity/avail, cache size, process IO bytes, open file descriptors (FD)
 */
internal data class PerformanceSnapshot(
    val running: Boolean = false,
    val elapsedMs: Long = 0L,

    // 1. Frames & FPS
    val frames: Int = 0,
    val jankyFrames: Int = 0,
    val severeJankyFrames: Int = 0,
    val missedFrames: Int = 0,
    val droppedFrameRate: Float = 0f,
    val fps: Float = 0f,
    val averageFrameMs: Float = 0f,
    val p50FrameMs: Float = 0f,
    val p95FrameMs: Float = 0f,
    val maxFrameMs: Float = 0f,

    // 2. Memory
    val javaHeapUsedMb: Float = 0f,
    val javaHeapMaxMb: Float = 0f,
    val javaHeapUsagePercent: Float = 0f,
    val nativeHeapAllocatedMb: Float = 0f,
    val nativeHeapSizeMb: Float = 0f,
    val memoryPssMb: Float = 0f,
    val memoryUssMb: Float = 0f,
    val memoryRssMb: Float = 0f,
    val graphicsPssMb: Float = 0f,
    val systemAvailRamMb: Float = 0f,
    val systemTotalRamMb: Float = 0f,
    val isLowMemory: Boolean = false,

    // 3. CPU & ART GC
    val cpuUsagePercent: Float = 0f,
    val threadCount: Int = 0,
    val gcCount: Long = 0L,
    val gcTimeMs: Long = 0L,
    val gcBytesAllocatedMb: Float = 0f,

    // 4. Battery & Power
    val batteryLevelPercent: Int = 0,
    val batteryTempC: Float = 0f,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val thermalStatus: String = "正常",

    // 5. Network
    val rxSpeedKbps: Float = 0f,
    val txSpeedKbps: Float = 0f,
    val totalRxMb: Float = 0f,
    val totalTxMb: Float = 0f,
    val networkType: String = "Wi-Fi",
    val isMetered: Boolean = false,

    // 6. Storage & I/O
    val storageAvailGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val appCacheMb: Float = 0f,
    val openFdCount: Int = 0,
    val ioReadMb: Float = 0f,
    val ioWriteMb: Float = 0f
)

/** One timestamped line in the in-app gfxinfo-style capture. */
internal data class PerformanceLogEntry(
    val time: Long,
    val level: String,
    val tag: String,
    val message: String
)

private val performanceTimeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

internal fun formatPerformanceLogLine(entry: PerformanceLogEntry): String = synchronized(performanceTimeFormat) {
    "${performanceTimeFormat.format(Date(entry.time))} ${entry.level}/${entry.tag}: ${entry.message}"
}

internal fun buildPerformanceReport(
    snapshot: PerformanceSnapshot,
    entries: List<PerformanceLogEntry>
): String = buildString {
    appendLine("=== Halcyon 性能与系统状态监控报告 ===")
    appendLine("采样耗时: ${snapshot.elapsedMs} ms | 总渲染帧数: ${snapshot.frames}")
    appendLine("[帧率与渲染] FPS=${snapshot.fps.oneDecimal()} 掉帧=${snapshot.jankyFrames} 严重掉帧=${snapshot.severeJankyFrames} 丢帧=${snapshot.missedFrames} 丢帧率=${snapshot.droppedFrameRate.oneDecimal()}% 帧耗时(均值=${snapshot.averageFrameMs.oneDecimal()}ms, P50=${snapshot.p50FrameMs.oneDecimal()}ms, P95=${snapshot.p95FrameMs.oneDecimal()}ms, 峰值=${snapshot.maxFrameMs.oneDecimal()}ms)")
    appendLine("[内存详细] JVM堆=${snapshot.javaHeapUsedMb.oneDecimal()}MB/${snapshot.javaHeapMaxMb.oneDecimal()}MB (${snapshot.javaHeapUsagePercent.oneDecimal()}%) | Native堆=${snapshot.nativeHeapAllocatedMb.oneDecimal()}MB/${snapshot.nativeHeapSizeMb.oneDecimal()}MB | PSS=${snapshot.memoryPssMb.oneDecimal()}MB USS=${snapshot.memoryUssMb.oneDecimal()}MB RSS=${snapshot.memoryRssMb.oneDecimal()}MB Graphics=${snapshot.graphicsPssMb.oneDecimal()}MB | 系统RAM可用=${snapshot.systemAvailRamMb.oneDecimal()}MB/${snapshot.systemTotalRamMb.oneDecimal()}MB (低内存=${snapshot.isLowMemory})")
    appendLine("[CPU与ART GC] CPU使用率=${snapshot.cpuUsagePercent.oneDecimal()}% | 活跃线程数=${snapshot.threadCount} | ART GC次数=${snapshot.gcCount} 累计耗时=${snapshot.gcTimeMs}ms 累计分配=${snapshot.gcBytesAllocatedMb.oneDecimal()}MB")
    appendLine("[电池与温控] 电量=${snapshot.batteryLevelPercent}% 温度=${snapshot.batteryTempC.oneDecimal()}°C 充电=${snapshot.isCharging} 省电模式=${snapshot.isPowerSaveMode} 温控状态=${snapshot.thermalStatus}")
    appendLine("[网络流量] 下行速率=${snapshot.rxSpeedKbps.oneDecimal()}KB/s 上行速率=${snapshot.txSpeedKbps.oneDecimal()}KB/s 累计消耗(Rx=${snapshot.totalRxMb.oneDecimal()}MB, Tx=${snapshot.totalTxMb.oneDecimal()}MB) 网络=${snapshot.networkType} (计费网络=${snapshot.isMetered})")
    appendLine("[存储与IO] 内部存储可用=${snapshot.storageAvailGb.oneDecimal()}GB/${snapshot.storageTotalGb.oneDecimal()}GB 应用缓存=${snapshot.appCacheMb.oneDecimal()}MB 文件句柄(FD)=${snapshot.openFdCount} 进程IO(读=${snapshot.ioReadMb.oneDecimal()}MB, 写=${snapshot.ioWriteMb.oneDecimal()}MB)")
    appendLine()
    appendLine("=== 帧渲染日志 ===")
    entries.asReversed().forEach { appendLine(formatPerformanceLogLine(it)) }
}

/** In-process performance sampler combining Choreographer frame timing and system diagnostic metrics. */
internal class FramePerformanceSampler(
    private val context: Context,
    private val view: View
) {
    private val choreographer = Choreographer.getInstance()
    private val frameDurationsNs = ArrayDeque<Long>()
    private val logEntries = ArrayDeque<PerformanceLogEntry>()
    private val displayRefreshNs = displayRefreshPeriodNs(context)
    private val displayRefreshHz = 1_000_000_000f / displayRefreshNs
    private var running = false
    private var firstFrameNs = 0L
    private var lastFrameNs = 0L
    private var lastPublishNs = 0L
    private var frameCount = 0
    private var jankyFrameCount = 0
    private var severeJankyFrameCount = 0
    private var missedFrameCount = 0
    private var lastLoggedJankyCount = 0
    private var callback: Choreographer.FrameCallback? = null
    private val snapshotState = mutableStateOf(PerformanceSnapshot())
    private val logState = mutableStateOf<List<PerformanceLogEntry>>(emptyList())

    // CPU sampling state
    private var lastProcessCpuTime = 0L
    private var lastCpuSampleTimeMs = 0L

    // Network sampling state
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var lastNetSampleTimeMs = 0L

    val snapshot: State<PerformanceSnapshot> get() = snapshotState
    val logs: State<List<PerformanceLogEntry>> get() = logState

    fun start() {
        if (running) return
        resetCounters()
        logEntries.clear()
        logState.value = emptyList()
        running = true
        appendLog("I", "gfxinfo", "capture started refreshHz=${displayRefreshHz.oneDecimal()}")
        publishSnapshot(force = true)
        postNextFrame()
    }

    fun stop() {
        if (!running) return
        running = false
        callback?.let(choreographer::removeFrameCallback)
        callback = null
        publishSnapshot(force = true)
        appendLog("I", "gfxinfo", "capture stopped elapsedMs=${snapshotState.value.elapsedMs}")
    }

    fun reset() {
        stop()
        resetCounters()
        logEntries.clear()
        logState.value = emptyList()
        snapshotState.value = PerformanceSnapshot()
    }

    private fun resetCounters() {
        firstFrameNs = 0L
        lastFrameNs = 0L
        lastPublishNs = 0L
        frameCount = 0
        jankyFrameCount = 0
        severeJankyFrameCount = 0
        missedFrameCount = 0
        lastLoggedJankyCount = 0
        lastProcessCpuTime = 0L
        lastCpuSampleTimeMs = 0L
        lastRxBytes = -1L
        lastTxBytes = -1L
        lastNetSampleTimeMs = 0L
        frameDurationsNs.clear()
    }

    private fun postNextFrame() {
        if (!running) return
        val next = Choreographer.FrameCallback { frameTimeNs ->
            if (!running) return@FrameCallback
            if (firstFrameNs == 0L) firstFrameNs = frameTimeNs
            if (lastFrameNs != 0L) {
                val frameNs = (frameTimeNs - lastFrameNs).coerceAtLeast(0L)
                frameDurationsNs.addLast(frameNs)
                if (frameDurationsNs.size > MAX_FRAME_SAMPLES) frameDurationsNs.removeFirst()
                frameCount++
                if (frameNs > displayRefreshNs * JANK_MULTIPLIER) jankyFrameCount++
                if (frameNs > displayRefreshNs * SEVERE_JANK_MULTIPLIER) severeJankyFrameCount++
                missedFrameCount += ((frameNs / displayRefreshNs).toInt() - 1).coerceAtLeast(0)
            }
            lastFrameNs = frameTimeNs
            publishSnapshot()
            postNextFrame()
        }
        callback = next
        choreographer.postFrameCallback(next)
    }

    private fun publishSnapshot(force: Boolean = false) {
        val nowNs = System.nanoTime()
        if (!force && nowNs - lastPublishNs < PUBLISH_INTERVAL_NS) return
        lastPublishNs = nowNs
        val elapsedMs = if (firstFrameNs == 0L) 0L else ((nowNs - firstFrameNs) / 1_000_000L).coerceAtLeast(0L)

        // 1. Frame / FPS
        val sorted = frameDurationsNs.sorted()
        val average = if (sorted.isNotEmpty()) sorted.average().toFloat() / 1_000_000f else 0f
        val p50 = if (sorted.isNotEmpty()) sorted[((sorted.size - 1) * 0.50f).toInt()].toFloat() / 1_000_000f else 0f
        val p95 = if (sorted.isNotEmpty()) sorted[((sorted.size - 1) * 0.95f).toInt()].toFloat() / 1_000_000f else 0f
        val max = sorted.lastOrNull()?.toFloat()?.div(1_000_000f) ?: 0f
        val fps = if (elapsedMs > 0L) frameCount * 1_000f / elapsedMs else 0f
        val droppedRate = if (frameCount + missedFrameCount > 0) {
            missedFrameCount.toFloat() / (frameCount + missedFrameCount) * 100f
        } else 0f

        // 2. Memory
        val runtime = Runtime.getRuntime()
        val javaHeapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB.toFloat()
        val javaHeapMaxMb = runtime.maxMemory() / BYTES_PER_MB.toFloat()
        val javaHeapUsagePercent = if (javaHeapMaxMb > 0f) (javaHeapUsedMb / javaHeapMaxMb * 100f) else 0f
        val nativeHeapAllocatedMb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB.toFloat()
        val nativeHeapSizeMb = Debug.getNativeHeapSize() / BYTES_PER_MB.toFloat()
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        val pssMb = memoryInfo.totalPss / 1024f
        val ussMb = memoryInfo.totalPrivateDirty / 1024f
        val rssMb = (memoryInfo.totalPrivateDirty + memoryInfo.totalSharedDirty) / 1024f
        val graphicsPssMb = (memoryInfo.getMemoryStat("summary.graphics")?.toFloatOrNull() ?: 0f) / 1024f
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val sysMemInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(sysMemInfo)
        val sysAvailRamMb = sysMemInfo.availMem / BYTES_PER_MB.toFloat()
        val sysTotalRamMb = sysMemInfo.totalMem / BYTES_PER_MB.toFloat()

        // 3. CPU & ART GC
        val cpuUsage = sampleCpuUsage()
        val threadCount = Thread.activeCount()
        val gcCount = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0L
        val gcTimeMs = Debug.getRuntimeStat("art.gc.gc-time-ms")?.toLongOrNull() ?: 0L
        val gcBytesAllocatedMb = (Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L) / BYTES_PER_MB.toFloat()

        // 4. Battery & Power
        val batteryMetrics = sampleBatteryPower()

        // 5. Network
        val netMetrics = sampleNetwork()

        // 6. Storage & I/O
        val storageMetrics = sampleStorageIo()

        val snapshot = PerformanceSnapshot(
            running = running,
            elapsedMs = elapsedMs,
            frames = frameCount,
            jankyFrames = jankyFrameCount,
            severeJankyFrames = severeJankyFrameCount,
            missedFrames = missedFrameCount,
            droppedFrameRate = droppedRate,
            fps = fps,
            averageFrameMs = average,
            p50FrameMs = p50,
            p95FrameMs = p95,
            maxFrameMs = max,
            javaHeapUsedMb = javaHeapUsedMb,
            javaHeapMaxMb = javaHeapMaxMb,
            javaHeapUsagePercent = javaHeapUsagePercent,
            nativeHeapAllocatedMb = nativeHeapAllocatedMb,
            nativeHeapSizeMb = nativeHeapSizeMb,
            memoryPssMb = pssMb,
            memoryUssMb = ussMb,
            memoryRssMb = rssMb,
            graphicsPssMb = graphicsPssMb,
            systemAvailRamMb = sysAvailRamMb,
            systemTotalRamMb = sysTotalRamMb,
            isLowMemory = sysMemInfo.lowMemory,
            cpuUsagePercent = cpuUsage,
            threadCount = threadCount,
            gcCount = gcCount,
            gcTimeMs = gcTimeMs,
            gcBytesAllocatedMb = gcBytesAllocatedMb,
            batteryLevelPercent = batteryMetrics.levelPercent,
            batteryTempC = batteryMetrics.tempC,
            isCharging = batteryMetrics.isCharging,
            isPowerSaveMode = batteryMetrics.isPowerSave,
            thermalStatus = batteryMetrics.thermalStatus,
            rxSpeedKbps = netMetrics.rxSpeedKbps,
            txSpeedKbps = netMetrics.txSpeedKbps,
            totalRxMb = netMetrics.totalRxMb,
            totalTxMb = netMetrics.totalTxMb,
            networkType = netMetrics.networkType,
            isMetered = netMetrics.isMetered,
            storageAvailGb = storageMetrics.availGb,
            storageTotalGb = storageMetrics.totalGb,
            appCacheMb = storageMetrics.cacheMb,
            openFdCount = storageMetrics.openFdCount,
            ioReadMb = storageMetrics.ioReadMb,
            ioWriteMb = storageMetrics.ioWriteMb
        )
        snapshotState.value = snapshot

        val level = if (snapshot.jankyFrames > lastLoggedJankyCount) "W" else "D"
        lastLoggedJankyCount = snapshot.jankyFrames
        appendLog(
            level,
            "gfxinfo",
            "fps=${snapshot.fps.oneDecimal()} janky=${snapshot.jankyFrames} p95=${snapshot.p95FrameMs.oneDecimal()}ms " +
                "pss=${snapshot.memoryPssMb.oneDecimal()}MB cpu=${snapshot.cpuUsagePercent.oneDecimal()}% " +
                "gcCount=${snapshot.gcCount} netRx=${snapshot.rxSpeedKbps.oneDecimal()}KB/s"
        )
    }

    private fun sampleCpuUsage(): Float {
        val now = SystemClock.elapsedRealtime()
        val deltaMs = now - lastCpuSampleTimeMs
        if (deltaMs < 200L) return snapshotState.value.cpuUsagePercent
        return runCatching {
            val stat = File("/proc/self/stat").readText()
            val afterComm = stat.substringAfterLast(')').trim().split(' ')
            val utime = afterComm[11].toLong()
            val stime = afterComm[12].toLong()
            val currentProcessCpuTime = utime + stime
            val cpuDelta = currentProcessCpuTime - lastProcessCpuTime
            lastProcessCpuTime = currentProcessCpuTime
            lastCpuSampleTimeMs = now
            val cpus = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val usage = (cpuDelta.toFloat() * 1000f / (deltaMs * 100f * cpus)) * 100f
            usage.coerceIn(0f, 100f)
        }.getOrDefault(0f)
    }

    private fun sampleBatteryPower(): BatteryMetrics {
        val bIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (scale > 0) (level * 100 / scale) else 0
        val temp = (bIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = bIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val powerSave = pm?.isPowerSaveMode == true
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            when (pm?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "正常"
                PowerManager.THERMAL_STATUS_LIGHT -> "轻微发热"
                PowerManager.THERMAL_STATUS_MODERATE -> "中度发热"
                PowerManager.THERMAL_STATUS_SEVERE -> "严重发热"
                PowerManager.THERMAL_STATUS_CRITICAL -> "极高温"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "紧急状态"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "关机预警"
                else -> "正常"
            }
        } else "正常"
        return BatteryMetrics(pct, temp, charging, powerSave, thermal)
    }

    private fun sampleNetwork(): NetworkMetrics {
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
        val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        val now = SystemClock.elapsedRealtime()
        val deltaSec = (now - lastNetSampleTimeMs) / 1000f
        var rxSpeed = 0f
        var txSpeed = 0f
        if (lastNetSampleTimeMs > 0L && deltaSec > 0f) {
            if (lastRxBytes >= 0L) rxSpeed = ((rx - lastRxBytes) / 1024f) / deltaSec
            if (lastTxBytes >= 0L) txSpeed = ((tx - lastTxBytes) / 1024f) / deltaSec
        }
        lastRxBytes = rx
        lastTxBytes = tx
        lastNetSampleTimeMs = now
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(network)
        val type = when {
            caps == null -> "离线"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝网络"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "蓝牙共享"
            else -> "已连接"
        }
        val isMetered = cm?.isActiveNetworkMetered == true
        return NetworkMetrics(
            rxSpeedKbps = rxSpeed.coerceAtLeast(0f),
            txSpeedKbps = txSpeed.coerceAtLeast(0f),
            totalRxMb = rx / BYTES_PER_MB.toFloat(),
            totalTxMb = tx / BYTES_PER_MB.toFloat(),
            networkType = type,
            isMetered = isMetered
        )
    }

    private fun sampleStorageIo(): StorageIoMetrics {
        val stat = runCatching { StatFs(context.filesDir.path) }.getOrNull()
        val availGb = (stat?.availableBytes ?: 0L) / (1024f * 1024f * 1024f)
        val totalGb = (stat?.totalBytes ?: 0L) / (1024f * 1024f * 1024f)
        val fdCount = runCatching { File("/proc/self/fd").list()?.size ?: 0 }.getOrDefault(0)
        val cacheMb = runCatching {
            val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
            var sum = 0L
            for (f in cacheFiles) {
                sum += if (f.isFile) f.length() else 0L
            }
            sum / BYTES_PER_MB.toFloat()
        }.getOrDefault(0f)
        var ioRead = 0f
        var ioWrite = 0f
        runCatching {
            File("/proc/self/io").forEachLine { line ->
                if (line.startsWith("read_bytes:")) {
                    ioRead = (line.substringAfter(':').trim().toLongOrNull() ?: 0L) / BYTES_PER_MB.toFloat()
                } else if (line.startsWith("write_bytes:")) {
                    ioWrite = (line.substringAfter(':').trim().toLongOrNull() ?: 0L) / BYTES_PER_MB.toFloat()
                }
            }
        }
        return StorageIoMetrics(availGb, totalGb, cacheMb, fdCount, ioRead, ioWrite)
    }

    private fun appendLog(level: String, tag: String, message: String) {
        logEntries.addLast(PerformanceLogEntry(System.currentTimeMillis(), level, tag, message))
        while (logEntries.size > MAX_LOG_ENTRIES) logEntries.removeFirst()
        logState.value = logEntries.toList().asReversed()
    }

    private data class BatteryMetrics(
        val levelPercent: Int,
        val tempC: Float,
        val isCharging: Boolean,
        val isPowerSave: Boolean,
        val thermalStatus: String
    )

    private data class NetworkMetrics(
        val rxSpeedKbps: Float,
        val txSpeedKbps: Float,
        val totalRxMb: Float,
        val totalTxMb: Float,
        val networkType: String,
        val isMetered: Boolean
    )

    private data class StorageIoMetrics(
        val availGb: Float,
        val totalGb: Float,
        val cacheMb: Float,
        val openFdCount: Int,
        val ioReadMb: Float,
        val ioWriteMb: Float
    )

    private companion object {
        const val MAX_FRAME_SAMPLES = 3_000
        const val MAX_LOG_ENTRIES = 1_200
        const val PUBLISH_INTERVAL_NS = 250_000_000L
        const val JANK_MULTIPLIER = 1.5
        const val SEVERE_JANK_MULTIPLIER = 2.5
        const val BYTES_PER_MB = 1024L * 1024L

        fun displayRefreshPeriodNs(context: Context): Long {
            val display: Display? = if (Build.VERSION.SDK_INT >= 30) {
                context.getSystemService(WindowManager::class.java)?.defaultDisplay
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            }
            val refreshRate = display?.refreshRate?.takeIf { it.isFinite() && it >= 30f } ?: 60f
            return (1_000_000_000f / refreshRate).toLong().coerceAtLeast(1L)
        }
    }
}

@Composable
internal fun PerformanceDiagnosticsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pageBackground = diagnosticsPageBackground()
    val sampler = remember(context, view) { FramePerformanceSampler(context, view) }
    val snapshot by sampler.snapshot
    val logs by sampler.logs
    var query by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<PerformanceLogEntry?>(null) }

    DisposableEffect(sampler) {
        onDispose { sampler.stop() }
    }

    val filteredLogs = remember(logs, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) logs else logs.filter {
            formatPerformanceLogLine(it).contains(keyword, ignoreCase = true)
        }
    }

    fun copyLogs(entries: List<PerformanceLogEntry>) {
        copyDiagnosticsText(
            context = context,
            label = context.getString(R.string.settings_performance_diagnostics),
            text = buildPerformanceReport(snapshot, entries)
        )
    }

    fun shareLogs(entries: List<PerformanceLogEntry>) {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "shared_logs").apply { mkdirs() }
                    .resolve("halcyon-perf-${System.currentTimeMillis()}.txt")
                    .also { it.writeText(buildPerformanceReport(snapshot, entries)) }
            }
            shareDiagnosticsTextFile(context = context, file = file)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_performance_diagnostics),
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(enabled = logs.isNotEmpty(), onClick = { copyLogs(filteredLogs) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Copy,
                        contentDescription = stringResource(R.string.logs_copy_action)
                    )
                }
                IconButton(enabled = logs.isNotEmpty(), onClick = { shareLogs(filteredLogs) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Share,
                        contentDescription = stringResource(R.string.logs_share_action)
                    )
                }
                IconButton(enabled = logs.isNotEmpty(), onClick = sampler::reset) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Delete,
                        contentDescription = stringResource(R.string.logs_clear_action),
                        tint = MiuixTheme.colorScheme.error
                    )
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("status") {
                DiagnosticsCard {
                    BasicComponent(
                        title = stringResource(
                            if (snapshot.running) R.string.settings_performance_running
                            else R.string.settings_performance_ready
                        ),
                        summary = stringResource(R.string.settings_performance_explanation) +
                            " · 采集记录 ${logs.size} 条",
                        insideMargin = PaddingValues(16.dp)
                    )
                }
            }
            item("buttons") {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { if (snapshot.running) sampler.stop() else sampler.start() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(
                                if (snapshot.running) R.string.settings_performance_stop
                                else R.string.settings_performance_start
                            )
                        )
                    }
                    Button(onClick = sampler::reset, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_performance_reset))
                    }
                }
            }
            item("search") {
                DiagnosticsSearchBar(
                    query = query,
                    onQueryChange = { query = it }
                )
            }

            // 1. FPS & Jank metrics
            item("metric-fps") {
                PerformanceMetricCard(
                    title = "渲染与帧率 (FPS & Jank)",
                    values = listOf(
                        "实时帧率: ${snapshot.fps.oneDecimal()} FPS",
                        "总帧数: ${snapshot.frames} 帧",
                        "掉帧统计: 轻微掉帧 ${snapshot.jankyFrames} 帧 · 严重掉帧 ${snapshot.severeJankyFrames} 帧 · 丢失 ${snapshot.missedFrames} 帧",
                        "掉帧率: ${snapshot.droppedFrameRate.oneDecimal()}%",
                        "帧耗时: 均值 ${snapshot.averageFrameMs.oneDecimal()} ms · P50 ${snapshot.p50FrameMs.oneDecimal()} ms · P95 ${snapshot.p95FrameMs.oneDecimal()} ms · 峰值 ${snapshot.maxFrameMs.oneDecimal()} ms"
                    )
                )
            }

            // 2. Memory metrics
            item("metric-memory") {
                PerformanceMetricCard(
                    title = "内存占用 (Memory)",
                    values = listOf(
                        "JVM 堆内存: ${snapshot.javaHeapUsedMb.oneDecimal()} MB / ${snapshot.javaHeapMaxMb.oneDecimal()} MB (${snapshot.javaHeapUsagePercent.oneDecimal()}%)",
                        "Native 堆: 已分配 ${snapshot.nativeHeapAllocatedMb.oneDecimal()} MB (总大小 ${snapshot.nativeHeapSizeMb.oneDecimal()} MB)",
                        "实际物理占用 PSS: ${snapshot.memoryPssMb.oneDecimal()} MB",
                        "私有常驻 USS: ${snapshot.memoryUssMb.oneDecimal()} MB · 常驻 RSS: ${snapshot.memoryRssMb.oneDecimal()} MB",
                        "图形显存 Graphics PSS: ${snapshot.graphicsPssMb.oneDecimal()} MB",
                        "设备系统 RAM: 可用 ${snapshot.systemAvailRamMb.oneDecimal()} MB / 总量 ${snapshot.systemTotalRamMb.oneDecimal()} MB (低内存状态: ${if (snapshot.isLowMemory) "低内存告警" else "正常"})"
                    )
                )
            }

            // 3. CPU & ART GC metrics
            item("metric-cpu-gc") {
                PerformanceMetricCard(
                    title = "CPU 与 ART 垃圾回收",
                    values = listOf(
                        "应用 CPU 使用率: ${snapshot.cpuUsagePercent.oneDecimal()}%",
                        "活跃线程数: ${snapshot.threadCount} 个",
                        "ART GC 次数: ${snapshot.gcCount} 次",
                        "ART GC 累计停顿耗时: ${snapshot.gcTimeMs} ms",
                        "ART 累计分配内存: ${snapshot.gcBytesAllocatedMb.oneDecimal()} MB"
                    )
                )
            }

            // 4. Battery & Power metrics
            item("metric-battery") {
                PerformanceMetricCard(
                    title = "电池状态与温控 (Battery & Power)",
                    values = listOf(
                        "电池电量: ${snapshot.batteryLevelPercent}% · 电池温度: ${snapshot.batteryTempC.oneDecimal()} °C",
                        "充电状态: ${if (snapshot.isCharging) "充电中" else "未充电"} · 省电模式: ${if (snapshot.isPowerSaveMode) "已开启" else "关闭"}",
                        "系统温控状态: ${snapshot.thermalStatus}"
                    )
                )
            }

            // 5. Network metrics
            item("metric-network") {
                PerformanceMetricCard(
                    title = "网络流量与状态 (Network Traffic)",
                    values = listOf(
                        "实时速率: 下行 ${snapshot.rxSpeedKbps.oneDecimal()} KB/s · 上行 ${snapshot.txSpeedKbps.oneDecimal()} KB/s",
                        "进程累计流量: 接收 ${snapshot.totalRxMb.oneDecimal()} MB · 发送 ${snapshot.totalTxMb.oneDecimal()} MB",
                        "连接类型: ${snapshot.networkType} (计费网络: ${if (snapshot.isMetered) "是" else "否"})"
                    )
                )
            }

            // 6. Storage & I/O metrics
            item("metric-storage") {
                PerformanceMetricCard(
                    title = "存储空间与进程 I/O (Storage & I/O)",
                    values = listOf(
                        "内部存储可用: ${snapshot.storageAvailGb.oneDecimal()} GB / 总量 ${snapshot.storageTotalGb.oneDecimal()} GB",
                        "应用缓存占用: ${snapshot.appCacheMb.oneDecimal()} MB",
                        "打开文件句柄 (FD): ${snapshot.openFdCount} 个",
                        "进程 I/O 读写: 读取 ${snapshot.ioReadMb.oneDecimal()} MB · 写入 ${snapshot.ioWriteMb.oneDecimal()} MB"
                    )
                )
            }

            item("log-header") {
                DiagnosticsCard {
                    BasicComponent(
                        title = "gfxinfo",
                        summary = "${filteredLogs.size}/${logs.size} 条 · 点击记录查看完整行",
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            if (filteredLogs.isEmpty()) {
                item("empty") {
                    DiagnosticsEmptyCard(
                        text = stringResource(
                            if (logs.isEmpty()) R.string.logs_empty else R.string.logs_empty_filtered
                        )
                    )
                }
            } else {
                itemsIndexed(
                    items = filteredLogs,
                    key = { index, entry -> "${index}-${entry.time}-${entry.message.hashCode()}" }
                ) { _, entry ->
                    PerformanceLogItem(entry = entry, onClick = { selectedEntry = entry })
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        EllaMiuixBottomSheet(
            show = true,
            title = stringResource(R.string.settings_performance_diagnostics),
            endAction = {
                IconButton(onClick = { copyLogs(listOf(entry)) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Copy,
                        contentDescription = stringResource(R.string.logs_copy_action)
                    )
                }
            },
            onDismissRequest = { selectedEntry = null }
        ) {
            SelectionContainer {
                Text(
                    text = formatPerformanceLogLine(entry),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun PerformanceLogItem(
    entry: PerformanceLogEntry,
    onClick: () -> Unit
) {
    DiagnosticsCard {
        BasicComponent(
            onClick = onClick,
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Text(
                text = formatPerformanceLogLine(entry),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = if (entry.level == "W") MiuixTheme.colorScheme.error
                else MiuixTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PerformanceMetricCard(
    title: String,
    values: List<String>
) {
    DiagnosticsCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            values.forEach { value ->
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
