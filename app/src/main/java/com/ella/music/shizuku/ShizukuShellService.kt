package com.ella.music.shizuku

import android.content.Context
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Shizuku user service used for the few privileged shell operations Halcyon supports.
 *
 * A user service is not a normal Android service process, so it must not use app APIs such as
 * ContentResolver. It only runs the command and returns its combined stdout/stderr to the app.
 */
class ShizukuShellService(@Suppress("UNUSED_PARAMETER") context: Context) :
    IShizukuShellService.Stub() {

    private val outputExecutor = Executors.newCachedThreadPool()

    override fun exec(command: String): String {
        require(command.isNotBlank()) { "command must not be blank" }

        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = outputExecutor.submit<String> {
            process.inputStream.bufferedReader().use { it.readText() }
        }

        try {
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IOException("shell command timed out")
            }
            return output.get(OUTPUT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).trim()
        } finally {
            output.cancel(true)
            process.inputStream.close()
            process.errorStream.close()
            process.outputStream.close()
        }
    }

    override fun destroy() {
        outputExecutor.shutdownNow()
        exitProcess(0)
    }

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 10L
        const val OUTPUT_READ_TIMEOUT_SECONDS = 1L
    }
}
