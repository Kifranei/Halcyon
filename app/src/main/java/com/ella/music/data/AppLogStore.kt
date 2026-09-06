package com.ella.music.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.ella.music.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    private const val DEVICE_PROPERTY_READ_LIMIT_BYTES = 512 * 1024
    private val BUILD_PROP_PATHS = listOf(
        "/system/build.prop",
        "/system/system/build.prop",
        "/system/etc/prop.default",
        "/system/etc/build.prop",
        "/system_ext/build.prop",
        "/system_ext/etc/build.prop",
        "/product/build.prop",
        "/product/etc/build.prop",
        "/vendor/build.prop",
        "/vendor/default.prop",
        "/vendor/etc/build.prop",
        "/odm/build.prop",
        "/odm/etc/build.prop",
        "/system_dlkm/build.prop"
    )
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
            appendLine()
            appendLine("== Device/build properties ==")
            appendDeviceProperties()
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

    /**
     * Adds the same low-level context that is normally requested alongside a bug report.  The
     * values are collected at export time so a report copied from a different device/process is
     * still self describing.  Some Android releases make individual build.prop files unreadable;
     * failures are intentionally represented as a short marker while the public Build values and
     * getprop output remain available.
     */
    private fun StringBuilder.appendDeviceProperties() {
        fun appendValue(name: String, value: String?) {
            appendLine("$name: ${value?.takeIf { it.isNotBlank() } ?: "<unavailable>"}")
        }

        appendValue("Build.BRAND", Build.BRAND)
        appendValue("Build.MANUFACTURER", Build.MANUFACTURER)
        appendValue("Build.MODEL", Build.MODEL)
        appendValue("Build.DEVICE", Build.DEVICE)
        appendValue("Build.PRODUCT", Build.PRODUCT)
        appendValue("Build.BOARD", Build.BOARD)
        appendValue("Build.HARDWARE", Build.HARDWARE)
        appendValue("Build.FINGERPRINT", Build.FINGERPRINT)
        appendValue("Build.ID", Build.ID)
        appendValue("Build.DISPLAY", Build.DISPLAY)
        appendValue("Build.TYPE", Build.TYPE)
        appendValue("Build.TAGS", Build.TAGS)
        appendValue("Build.HOST", Build.HOST)
        appendValue("Build.USER", Build.USER)
        appendValue("Build.BOOTLOADER", Build.BOOTLOADER)
        appendValue("Build.SUPPORTED_ABIS", Build.SUPPORTED_ABIS.joinToString(","))
        appendValue("Build.VERSION.SECURITY_PATCH", Build.VERSION.SECURITY_PATCH)
        appendValue("Build.VERSION.INCREMENTAL", Build.VERSION.INCREMENTAL)
        appendValue("Build.VERSION.CODENAME", Build.VERSION.CODENAME)
        appendValue("Build.VERSION.BASE_OS", Build.VERSION.BASE_OS)
        appendValue(
            "Build.VERSION.PREVIEW_SDK_INT",
            Build.VERSION.PREVIEW_SDK_INT.toString()
        )
        runCatching { Build.getRadioVersion() }
            .onSuccess { appendValue("Build.RADIO", it) }

        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("getprop"))
            val output = process.inputStream.use { it.readLimitedText(DEVICE_PROPERTY_READ_LIMIT_BYTES) }
            process.waitFor(2L, TimeUnit.SECONDS)
            process.destroy()
            output.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("-- getprop --")
                appendLine(it.trimEnd())
            }
        }.onFailure { appendLine("getprop: <unavailable: ${it.javaClass.simpleName}>") }

        BUILD_PROP_PATHS.forEach { path ->
            val file = File(path)
            if (!file.isFile) return@forEach
            appendLine()
            appendLine("-- $path --")
            runCatching {
                appendLine(file.inputStream().use { it.readLimitedText(DEVICE_PROPERTY_READ_LIMIT_BYTES) }.trimEnd())
            }.onFailure {
                appendLine("<unreadable: ${it.javaClass.simpleName}>")
            }
        }
    }

    private fun java.io.InputStream.readLimitedText(limitBytes: Int): String {
        val bytes = ByteArray(16 * 1024)
        var total = 0
        val output = java.io.ByteArrayOutputStream()
        while (total < limitBytes) {
            val read = read(bytes, 0, minOf(bytes.size, limitBytes - total))
            if (read <= 0) break
            output.write(bytes, 0, read)
            total += read
        }
        return output.toString(Charsets.UTF_8.name())
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
