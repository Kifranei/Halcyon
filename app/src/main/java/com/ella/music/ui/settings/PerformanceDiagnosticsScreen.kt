package com.ella.music.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Debug
import android.view.Choreographer
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ella.music.R
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class PerformanceSnapshot(
    val running: Boolean = false,
    val elapsedMs: Long = 0L,
    val frames: Int = 0,
    val jankyFrames: Int = 0,
    val missedFrames: Int = 0,
    val fps: Float = 0f,
    val averageFrameMs: Float = 0f,
    val p95FrameMs: Float = 0f,
    val maxFrameMs: Float = 0f,
    val memoryPssMb: Float = 0f,
    val javaHeapMb: Float = 0f,
    val nativeHeapMb: Float = 0f
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
    appendLine("Halcyon gfxinfo-like performance capture")
    appendLine(
        "elapsedMs=${snapshot.elapsedMs} frames=${snapshot.frames} " +
            "janky=${snapshot.jankyFrames} missed=${snapshot.missedFrames} " +
            "fps=${snapshot.fps.oneDecimal()} " +
            "frameMs(avg=${snapshot.averageFrameMs.oneDecimal()},p95=${snapshot.p95FrameMs.oneDecimal()},max=${snapshot.maxFrameMs.oneDecimal()}) " +
            "pssMb=${snapshot.memoryPssMb.oneDecimal()} javaMb=${snapshot.javaHeapMb.oneDecimal()} nativeMb=${snapshot.nativeHeapMb.oneDecimal()}"
    )
    appendLine()
    entries.asReversed().forEach { appendLine(formatPerformanceLogLine(it)) }
}

/** In-process frame sampler exposing the same headline evidence normally inspected with gfxinfo. */
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
    private var missedFrameCount = 0
    private var lastLoggedJankyCount = 0
    private var callback: Choreographer.FrameCallback? = null
    private val snapshotState = mutableStateOf(PerformanceSnapshot())
    private val logState = mutableStateOf<List<PerformanceLogEntry>>(emptyList())

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
        missedFrameCount = 0
        lastLoggedJankyCount = 0
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
        // Choreographer and System.nanoTime both use a monotonic clock on Android. Keeping the
        // elapsed-time calculation monotonic avoids wall-clock changes skewing the FPS value.
        val nowNs = System.nanoTime()
        if (!force && nowNs - lastPublishNs < PUBLISH_INTERVAL_NS) return
        lastPublishNs = nowNs
        val elapsedMs = if (firstFrameNs == 0L) 0L else ((nowNs - firstFrameNs) / 1_000_000L).coerceAtLeast(0L)
        val sorted = frameDurationsNs.sorted()
        val average = if (sorted.isNotEmpty()) sorted.average().toFloat() / 1_000_000f else 0f
        val p95 = if (sorted.isNotEmpty()) {
            sorted[((sorted.size - 1) * 0.95f).toInt()].toFloat() / 1_000_000f
        } else {
            0f
        }
        val max = sorted.lastOrNull()?.toFloat()?.div(1_000_000f) ?: 0f
        val fps = if (elapsedMs > 0L) frameCount * 1_000f / elapsedMs else 0f
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        val runtime = Runtime.getRuntime()
        val javaHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB.toFloat()
        val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB.toFloat()
        val snapshot = PerformanceSnapshot(
            running = running,
            elapsedMs = elapsedMs,
            frames = frameCount,
            jankyFrames = jankyFrameCount,
            missedFrames = missedFrameCount,
            fps = fps,
            averageFrameMs = average,
            p95FrameMs = p95,
            maxFrameMs = max,
            memoryPssMb = memoryInfo.totalPss / 1024f,
            javaHeapMb = javaHeapMb,
            nativeHeapMb = nativeHeapMb
        )
        snapshotState.value = snapshot
        val level = if (snapshot.jankyFrames > lastLoggedJankyCount) "W" else "D"
        lastLoggedJankyCount = snapshot.jankyFrames
        appendLog(
            level,
            "gfxinfo",
            "elapsedMs=${snapshot.elapsedMs} frames=${snapshot.frames} fps=${snapshot.fps.oneDecimal()} " +
                "janky=${snapshot.jankyFrames} missed=${snapshot.missedFrames} " +
                "frameMs(avg=${snapshot.averageFrameMs.oneDecimal()},p95=${snapshot.p95FrameMs.oneDecimal()},max=${snapshot.maxFrameMs.oneDecimal()}) " +
                "pssMb=${snapshot.memoryPssMb.oneDecimal()} javaMb=${snapshot.javaHeapMb.oneDecimal()} nativeMb=${snapshot.nativeHeapMb.oneDecimal()}"
        )
    }

    private fun appendLog(level: String, tag: String, message: String) {
        logEntries.addLast(PerformanceLogEntry(System.currentTimeMillis(), level, tag, message))
        while (logEntries.size > MAX_LOG_ENTRIES) logEntries.removeFirst()
        logState.value = logEntries.toList().asReversed()
    }

    private companion object {
        const val MAX_FRAME_SAMPLES = 3_000
        const val MAX_LOG_ENTRIES = 1_200
        const val PUBLISH_INTERVAL_NS = 250_000_000L
        const val JANK_MULTIPLIER = 1.5
        const val BYTES_PER_MB = 1024L * 1024L

        fun displayRefreshPeriodNs(context: Context): Long {
            val display: Display? = if (android.os.Build.VERSION.SDK_INT >= 30) {
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
    val pageBackground = ellaPageBackground()
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
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                context.getString(R.string.settings_performance_diagnostics),
                buildPerformanceReport(snapshot, entries)
            )
        )
        Toast.makeText(context, R.string.logs_copied, Toast.LENGTH_SHORT).show()
    }

    fun shareLogs(entries: List<PerformanceLogEntry>) {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "shared_logs").apply { mkdirs() }
                    .resolve("halcyon-gfxinfo-${System.currentTimeMillis()}.txt")
                    .also { it.writeText(buildPerformanceReport(snapshot, entries)) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.logs_share_subject))
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            }
            runCatching {
                context.startActivity(
                    android.content.Intent.createChooser(
                        intent,
                        context.getString(R.string.logs_share_chooser_title)
                    ).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }.onFailure {
                Toast.makeText(context, R.string.share_no_available_app, Toast.LENGTH_SHORT).show()
            }
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
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    BasicComponent(
                        title = stringResource(
                            if (snapshot.running) R.string.settings_performance_running
                            else R.string.settings_performance_ready
                        ),
                        summary = stringResource(R.string.settings_performance_explanation) +
                            " · gfxinfo/logcat 风格记录 ${logs.size} 条",
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
                EllaMiuixTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.logs_search_label),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item("summary") {
                PerformanceMetricCard(
                    title = stringResource(R.string.settings_performance_frame_title),
                    values = listOf(
                        stringResource(R.string.settings_performance_fps, snapshot.fps),
                        stringResource(R.string.settings_performance_frames, snapshot.frames),
                        stringResource(R.string.settings_performance_janky_frames, snapshot.jankyFrames),
                        stringResource(R.string.settings_performance_missed_frames, snapshot.missedFrames),
                        stringResource(
                            R.string.settings_performance_frame_time,
                            snapshot.averageFrameMs,
                            snapshot.p95FrameMs,
                            snapshot.maxFrameMs
                        ),
                        stringResource(R.string.settings_performance_pss, snapshot.memoryPssMb),
                        stringResource(R.string.settings_performance_java_heap, snapshot.javaHeapMb),
                        stringResource(R.string.settings_performance_native_heap, snapshot.nativeHeapMb)
                    )
                )
            }
            item("log-header") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    BasicComponent(
                        title = "gfxinfo",
                        summary = "${filteredLogs.size}/${logs.size} 条 · 点击记录查看完整行",
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            if (filteredLogs.isEmpty()) {
                item("empty") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        BasicComponent(
                            title = stringResource(
                                if (logs.isEmpty()) R.string.logs_empty else R.string.logs_empty_filtered
                            )
                        )
                    }
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
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
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
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
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
