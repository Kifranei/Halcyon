package com.ella.music.data

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads this process's logcat buffer the same way `adb logcat --pid` does.
 *
 * There is no parallel TSV snapshot and no 3000-line cap. The system logcat ring buffer is the
 * source of truth; this collector only keeps an in-memory view for the in-app log screen.
 */
object AppLogcatCollector {
    private const val TAG = "AppLogcatCollector"
    private val started = AtomicBoolean(false)
    private val lock = Any()
    private val entries = ArrayDeque<AppLogEntry>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EllaLogcatCollector").apply { isDaemon = true }
    }

    fun start(context: Context) {
        AppLogStore.install(context)
        if (!started.compareAndSet(false, true)) return
        executor.execute {
            runCatching { collectLive() }
                .onFailure { error ->
                    Log.w(TAG, "logcat collector unavailable", error)
                }
        }
    }

    fun snapshot(): List<AppLogEntry> = synchronized(lock) {
        entries.toList().asReversed()
    }

    fun clearSnapshot() = synchronized(lock) {
        entries.clear()
    }

    fun dumpRaw(): String {
        return runCatching {
            val process = startLogcat(dumpOnly = true)
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroy()
                return@runCatching output.ifBlank { "读取 logcat 超时\n" }
            }
            output.ifBlank { "logcat 暂无可读内容\n" }
        }.getOrElse { error ->
            "读取 logcat 失败: ${error.message ?: error.javaClass.name}\n"
        }
    }

    private fun collectLive() {
        val process = startLogcat(dumpOnly = false)
        var pending: AppLogEntry? = null
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val (completed, nextPending) = AppLogcatParser.consumeLine(line, pending)
                pending = nextPending
                completed?.let(::append)
            }
        }
        pending?.let(::append)
    }

    private fun startLogcat(dumpOnly: Boolean): java.lang.Process {
        val uid = Process.myUid()
        val pid = Process.myPid()
        // UID survives process restarts, so closing and reopening the app can still show
        // the previous session while it remains in the system logcat ring buffer.
        val uidCommand = buildList {
            add("logcat")
            add("-v")
            add("threadtime")
            add("--uid=$uid")
            if (dumpOnly) add("-d")
        }
        val pidCommand = buildList {
            add("logcat")
            add("-v")
            add("threadtime")
            add("--pid=$pid")
            if (dumpOnly) add("-d")
        }
        return runCatching {
            ProcessBuilder(uidCommand).redirectErrorStream(true).start()
        }.getOrElse {
            ProcessBuilder(pidCommand).redirectErrorStream(true).start()
        }
    }

    private fun append(entry: AppLogEntry) = synchronized(lock) {
        val last = entries.lastOrNull()
        if (last != null &&
            last.time == entry.time &&
            last.level == entry.level &&
            last.tag == entry.tag &&
            last.message == entry.message &&
            last.detail == entry.detail
        ) {
            return
        }
        entries.addLast(entry)
    }
}

internal object AppLogcatParser {
    private val threadTimeRegex =
        Regex("""(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+([^:]+):\s?(.*)""")

    fun parseDump(text: String, pid: String? = null): List<AppLogEntry> {
        var pending: AppLogEntry? = null
        val result = ArrayList<AppLogEntry>()
        text.lineSequence().forEach { line ->
            val (completed, nextPending) = consumeLine(line, pending, pid)
            pending = nextPending
            completed?.let(result::add)
        }
        pending?.let(result::add)
        return result
    }

    fun consumeLine(
        line: String,
        pending: AppLogEntry?,
        pidFilter: String? = null
    ): Pair<AppLogEntry?, AppLogEntry?> {
        val match = threadTimeRegex.matchEntire(line.trim())
        if (match == null) {
            if (pending == null || line.isBlank()) return null to pending
            val extra = line.trimEnd()
            val mergedDetail = listOfNotNull(pending.detail, extra)
                .filter { it.isNotBlank() }
                .joinToString("\n")
            return null to pending.copy(
                message = if (pending.message.isBlank()) extra else pending.message,
                detail = mergedDetail.takeIf { it.isNotBlank() && it != pending.message }
            )
        }
        val pid = match.groupValues[3]
        if (pidFilter != null && pid != pidFilter) return null to pending
        val parsed = AppLogEntry(
            time = parseThreadTime(match.groupValues[1], match.groupValues[2]),
            level = normalizeLogLevel(match.groupValues[5]),
            tag = match.groupValues[6].trim(),
            message = match.groupValues[7],
            type = "${match.groupValues[6]} ${match.groupValues[7]}".detectLogType().name
        )
        return pending to parsed
    }

    private fun parseThreadTime(date: String, time: String): Long {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        return runCatching {
            parser.parse("$year-$date $time")?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }
}
