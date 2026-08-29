package com.ella.music.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.ella.music.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppLogType(val label: String) {
    APP("应用"),
    CRASH("崩溃"),
    METADATA("元数据"),
    PLAYBACK("播放"),
    LYRICS("歌词"),
    LIBRARY("音乐库"),
    ONLINE("在线"),
    DATABASE("数据"),
    NETWORK("网络")
}

data class AppLogEntry(
    val time: Long,
    val level: String,
    val tag: String,
    val message: String,
    val type: String = AppLogType.APP.name,
    val detail: String? = null,
    val relatedId: String? = null
) {
    val throwable: String? get() = detail
}

object AppLogStore {
    private const val LEGACY_FILE_NAME = "ella_logs.tsv"
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        // Drop the old TSV snapshot. The log screen and export now read process logcat directly.
        runCatching { File(context.applicationContext.filesDir, LEGACY_FILE_NAME).delete() }
    }

    fun info(context: Context, tag: String, message: String, type: AppLogType = tag.detectLogType()) {
        log(context.applicationContext, "INFO", type, tag, message)
    }

    fun debug(context: Context, tag: String, message: String, type: AppLogType = tag.detectLogType()) {
        log(context.applicationContext, "DEBUG", type, tag, message)
    }

    fun warn(
        context: Context,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        type: AppLogType = tag.detectLogType()
    ) {
        log(
            context = context.applicationContext,
            level = "WARNING",
            type = type,
            tag = tag,
            message = message,
            detail = throwable?.stackTraceToString()
        )
    }

    fun error(
        context: Context,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        type: AppLogType = tag.detectLogType()
    ) {
        log(
            context = context.applicationContext,
            level = "ERROR",
            type = type,
            tag = tag,
            message = message,
            detail = throwable?.stackTraceToString()
        )
    }

    fun crash(context: Context, threadName: String, throwable: Throwable) {
        log(
            context = context.applicationContext,
            level = "ERROR",
            type = AppLogType.CRASH,
            tag = "Crash/$threadName",
            message = throwable.message ?: throwable.javaClass.name,
            detail = throwable.stackTraceToString()
        )
    }

    fun network(tag: String, message: String, detail: String? = null, level: String = "WARNING") {
        logGlobal(level = level, type = AppLogType.NETWORK, tag = tag, message = message, detail = detail)
    }

    fun logGlobal(
        level: String,
        type: AppLogType,
        tag: String,
        message: String,
        detail: String? = null,
        relatedId: String? = null,
        echoToLogcat: Boolean = true,
        skipIfRecent: Boolean = false
    ) {
        log(appContext ?: return, level, type, tag, message, detail, relatedId, echoToLogcat, skipIfRecent)
    }

    fun log(
        context: Context,
        level: String,
        type: AppLogType,
        tag: String,
        message: String,
        detail: String? = null,
        relatedId: String? = null,
        echoToLogcat: Boolean = true,
        skipIfRecent: Boolean = false
    ) {
        if (!echoToLogcat) return
        val priority = normalizeLevel(level).logPriority()
        val safeTag = tag.ifBlank { "Halcyon" }.take(23)
        Log.println(priority, safeTag, message)
        if (!detail.isNullOrBlank()) {
            Log.println(priority, safeTag, detail)
        }
        if (!relatedId.isNullOrBlank()) {
            Log.println(priority, safeTag, "related=$relatedId")
        }
    }

    fun read(context: Context): List<AppLogEntry> = AppLogcatCollector.snapshot()

    fun clear(context: Context) {
        AppLogcatCollector.clearSnapshot()
    }

    fun buildDetailedReport(
        context: Context,
        entries: List<AppLogEntry> = read(context),
        scopeDescription: String? = null
    ): String {
        val appContext = context.applicationContext
        return buildString {
            appendLine("Halcyon diagnostic info")
            appendLine("Generated: ${formatTime(System.currentTimeMillis())}")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${appContext.packageName}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            scopeDescription?.takeIf { it.isNotBlank() }?.let { appendLine("Export scope: $it") }
            appendLine("Log count: ${entries.size}")
            appendLine("Error count: ${entries.count { it.level == "ERROR" }}")
            appendLine("Warning count: ${entries.count { it.level == "WARNING" }}")
            appendLine()
            appendLine("== Logcat ==")
            val dump = AppLogcatCollector.dumpRaw()
            if (dump.isBlank()) {
                appendLine("logcat 暂无可读内容")
            } else {
                append(dump)
                if (!dump.endsWith("\n")) appendLine()
            }
        }
    }

    fun exportDetailedReport(
        context: Context,
        entries: List<AppLogEntry> = read(context),
        scopeDescription: String? = null
    ): File {
        val dir = File(context.cacheDir, "shared_logs").apply { mkdirs() }
        val file = File(dir, "halcyon-log-${exportTimeFormat()}.txt")
        file.writeText(buildDetailedReport(context, entries, scopeDescription))
        return file
    }

    fun formatTime(time: Long): String = synchronized(timeFormat) {
        timeFormat.format(Date(time))
    }

    private fun exportTimeFormat(): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        return formatter.format(Date())
    }
}

internal fun normalizeLogLevel(level: String): String = when (level.uppercase(Locale.ROOT)) {
    "V", "VERBOSE" -> "DEBUG"
    "D", "DEBUG" -> "DEBUG"
    "I", "INFO" -> "INFO"
    "W", "WARN", "WARNING" -> "WARNING"
    "E", "ERROR", "CRASH", "F", "FATAL" -> "ERROR"
    else -> level.uppercase(Locale.ROOT).ifBlank { "INFO" }
}

internal fun String.detectLogType(): AppLogType {
    val haystack = lowercase(Locale.ROOT)
    return when {
        listOf("crash", "fatal", "exception", "闪退", "崩溃").any { it in haystack } -> AppLogType.CRASH
        listOf("http", "network", "okhttp", "webdav", "download", "api", "url=", "网络", "下载").any { it in haystack } -> AppLogType.NETWORK
        listOf("player", "playback", "exo", "media3", "decoder", "queue", "audio focus", "播放", "解码", "队列").any { it in haystack } -> AppLogType.PLAYBACK
        listOf("lyric", "ticker", "superlyric", "lyricon", "flyme", "samsung", "歌词", "词幕").any { it in haystack } -> AppLogType.LYRICS
        listOf("scan", "scanner", "library", "folder", "album", "artist", "cover", "音乐库", "扫描", "封面").any { it in haystack } -> AppLogType.LIBRARY
        listOf("tag", "metadata", "taglib", "wav", "alac", "元数据", "标签").any { it in haystack } -> AppLogType.METADATA
        listOf("lx", "online", "quickjs", "在线").any { it in haystack } -> AppLogType.ONLINE
        listOf("database", "db", "cache", "playlist", "stats", "backup", "restore", "数据", "备份").any { it in haystack } -> AppLogType.DATABASE
        else -> AppLogType.APP
    }
}

private fun String.logPriority(): Int = when (this) {
    "DEBUG" -> Log.DEBUG
    "WARNING" -> Log.WARN
    "ERROR" -> Log.ERROR
    else -> Log.INFO
}

private fun normalizeLevel(level: String): String = normalizeLogLevel(level)
