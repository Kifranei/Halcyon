package com.ella.music.data

import android.content.Context
import android.os.Build
import android.util.AtomicFile
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
    private const val FILE_NAME = "ella_logs.tsv"
    private const val PREF_NAME = "ella_log_prefs"
    private const val KEY_RETENTION_DAYS = "retention_days"
    private const val MAX_LINES = 3_000
    private const val MAX_MESSAGE_LENGTH = 2_000
    private const val MAX_DETAIL_LENGTH = 24_000
    private const val RECENT_WINDOW_MS = 4_000L
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val recentSignatures = LinkedHashMap<String, Long>()
    private var lastReadableEntries: List<AppLogEntry> = emptyList()

    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun info(context: Context, tag: String, message: String, type: AppLogType = tag.detectType()) {
        log(context.applicationContext, "INFO", type, tag, message)
    }

    fun debug(context: Context, tag: String, message: String, type: AppLogType = tag.detectType()) {
        log(context.applicationContext, "DEBUG", type, tag, message)
    }

    fun warn(
        context: Context,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        type: AppLogType = tag.detectType()
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
        type: AppLogType = tag.detectType()
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

    fun logcat(level: String, tag: String, message: String, detail: String? = null) {
        val type = "$tag $message $detail".detectType()
        logGlobal(
            level = normalizeLevel(level),
            type = type,
            tag = tag,
            message = message,
            detail = detail,
            echoToLogcat = false,
            skipIfRecent = true
        )
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
        val context = appContext ?: return
        log(context, level, type, tag, message, detail, relatedId, echoToLogcat, skipIfRecent)
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
        val entry = AppLogEntry(
            time = System.currentTimeMillis(),
            level = normalizeLevel(level),
            type = type.name,
            tag = tag.take(MAX_MESSAGE_LENGTH),
            message = message.take(MAX_MESSAGE_LENGTH),
            detail = detail?.take(MAX_DETAIL_LENGTH),
            relatedId = relatedId?.take(MAX_MESSAGE_LENGTH)
        )
        append(context.applicationContext, entry, echoToLogcat, skipIfRecent)
    }

    fun read(context: Context): List<AppLogEntry> = synchronized(lock) {
        pruneByRetentionLocked(context)
        readEntriesLocked(context)
            .asReversed()
    }

    fun retentionDays(context: Context): Int = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_RETENTION_DAYS, 7)

    fun setRetentionDays(context: Context, days: Int): Int = synchronized(lock) {
        val safeDays = days.coerceIn(1, 30)
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RETENTION_DAYS, safeDays)
            .apply()
        clearOlderThanLocked(context.applicationContext, safeDays)
    }

    fun clear(context: Context) = synchronized(lock) {
        val file = logFile(context)
        file.delete()
        recentSignatures.clear()
        lastReadableEntries = emptyList()
    }

    fun clearOlderThan(context: Context, days: Int): Int = synchronized(lock) {
        clearOlderThanLocked(context, days)
    }

    private fun clearOlderThanLocked(context: Context, days: Int): Int {
        val file = logFile(context)
        if (!file.baseFile.exists()) return 0
        val cutoff = System.currentTimeMillis() - days.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val entries = readEntriesLocked(context)
        val kept = entries.filter { it.time >= cutoff }
        val removed = entries.size - kept.size
        if (removed > 0) {
            writeEntriesLocked(context, kept)
        }
        return removed
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
            appendLine("== App logs ==")
            if (entries.isEmpty()) {
                appendLine("No persisted logs")
            } else {
                entries.asReversed().forEach { entry ->
                    appendLine(entry.toReportLine())
                    entry.relatedId?.takeIf { it.isNotBlank() }?.let { appendLine("Related: $it") }
                    entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                        appendLine(detail.trimEnd())
                    }
                    appendLine("----")
                }
            }
            appendLine()
            appendLine("== Logcat tail ==")
            append(readLogcatTail())
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

    private fun append(
        context: Context,
        entry: AppLogEntry,
        echoToLogcat: Boolean = true,
        skipIfRecent: Boolean = false
    ) = synchronized(lock) {
        val signature = entry.signature()
        cleanupRecentLocked(entry.time)
        if (skipIfRecent && recentSignatures.containsKey(signature)) return@synchronized
        recentSignatures[signature] = entry.time

        if (echoToLogcat) {
            Log.println(entry.logPriority(), entry.tag, entry.message)
        }
        val file = logFile(context)
        val cutoff = System.currentTimeMillis() - retentionDays(context) * 24L * 60L * 60L * 1000L
        val retained = readEntriesLocked(context)
            .filter { it.time >= cutoff }
            .takeLast(MAX_LINES - 1)
        writeEntriesLocked(context, retained + entry)
    }

    private fun cleanupRecentLocked(now: Long) {
        val iterator = recentSignatures.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > RECENT_WINDOW_MS) iterator.remove()
        }
    }

    private fun AppLogEntry.signature(): String =
        "${level.uppercase(Locale.ROOT)}|${type.uppercase(Locale.ROOT)}|$tag|$message"

    private fun AppLogEntry.logPriority(): Int = when (level) {
        "DEBUG" -> Log.DEBUG
        "WARNING" -> Log.WARN
        "ERROR" -> Log.ERROR
        else -> Log.INFO
    }

    private fun logFile(context: Context): AtomicFile =
        AtomicFile(File(context.filesDir, FILE_NAME))

    private fun readEntriesLocked(context: Context): List<AppLogEntry> {
        val file = logFile(context)
        if (!file.baseFile.exists()) return emptyList()
        return runCatching {
            file.openRead().bufferedReader().use { reader ->
                reader.readLines().mapNotNull(::decode)
            }.also { entries -> lastReadableEntries = entries }
        }.getOrElse { error ->
            // Retain the previous on-disk snapshot instead of presenting a false empty log list
            // while a low-memory storage read is interrupted.
            Log.w("AppLogStore", "Failed to read persisted logs", error)
            lastReadableEntries
        }
    }

    private fun writeEntriesLocked(context: Context, entries: List<AppLogEntry>) {
        val file = logFile(context)
        runCatching {
            file.baseFile.parentFile?.mkdirs()
            val stream = file.startWrite()
            try {
                stream.write(entries.joinToString(separator = "\n", transform = ::encode).toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
                lastReadableEntries = entries
            } catch (error: Throwable) {
                file.failWrite(stream)
                throw error
            }
        }.onFailure { error -> Log.w("AppLogStore", "Failed to persist logs", error) }
    }

    private fun pruneByRetentionLocked(context: Context) {
        clearOlderThanLocked(context.applicationContext, retentionDays(context))
    }

    private fun AppLogEntry.toReportLine(): String {
        return "[${formatTime(time)}] $level/${typeLabel()} $tag: $message"
    }

    private fun exportTimeFormat(): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        return formatter.format(Date())
    }

    private fun readLogcatTail(): String {
        return runCatching {
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "900")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy()
                return@runCatching "读取 logcat 超时\n"
            }
            process.inputStream.bufferedReader().use { it.readText() }.ifBlank { "logcat 暂无可读内容\n" }
        }.getOrElse { error ->
            "读取 logcat 失败: ${error.message ?: error.javaClass.name}\n"
        }
    }

    private fun encode(entry: AppLogEntry): String = listOf(
        entry.time.toString(),
        entry.level,
        entry.type,
        entry.tag,
        entry.message,
        entry.detail.orEmpty(),
        entry.relatedId.orEmpty()
    ).joinToString("\t") { it.escape() }

    private fun decode(line: String): AppLogEntry? {
        val parts = line.split('\t').map { it.unescape() }
        return when {
            parts.size >= 7 -> AppLogEntry(
                time = parts[0].toLongOrNull() ?: return null,
                level = normalizeLevel(parts[1]),
                type = parts[2].ifBlank { AppLogType.APP.name },
                tag = parts[3],
                message = parts[4],
                detail = parts[5].takeIf { it.isNotBlank() },
                relatedId = parts[6].takeIf { it.isNotBlank() }
            )
            parts.size >= 5 -> AppLogEntry(
                time = parts[0].toLongOrNull() ?: return null,
                level = normalizeLevel(parts[1]),
                tag = parts[2],
                message = parts[3],
                type = "${parts[2]} ${parts[3]} ${parts[4]}".detectType().name,
                detail = parts[4].takeIf { it.isNotBlank() }
            )
            else -> null
        }
    }

    private fun normalizeLevel(level: String): String = when (level.uppercase(Locale.ROOT)) {
        "D", "DEBUG" -> "DEBUG"
        "I", "INFO" -> "INFO"
        "W", "WARN", "WARNING" -> "WARNING"
        "E", "ERROR", "CRASH", "F", "FATAL" -> "ERROR"
        else -> level.uppercase(Locale.ROOT).ifBlank { "INFO" }
    }

    private fun AppLogEntry.typeLabel(): String =
        runCatching { AppLogType.valueOf(type).label }.getOrDefault(type)

    private fun String.detectType(): AppLogType {
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

    private fun String.escape(): String = replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun String.unescape(): String {
        val result = StringBuilder(length)
        var escaping = false
        for (char in this) {
            if (escaping) {
                result.append(
                    when (char) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        else -> char
                    }
                )
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else {
                result.append(char)
            }
        }
        if (escaping) result.append('\\')
        return result.toString()
    }
}
