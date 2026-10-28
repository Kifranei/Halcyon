package com.ella.music.shizuku

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlinx.coroutines.CancellationException

/**
 * Shizuku user service used for the few privileged shell operations Halcyon supports.
 *
 * A user service is not a normal Android service process, so it must not use app APIs such as
 * ContentResolver. Privileged integrations use shell commands or the system Binder directly.
 */
@Keep
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

    override fun setOriginOsMediaIslandScene(packageName: String, enabled: Boolean): Boolean {
        if (!PACKAGE_NAME_PATTERN.matches(packageName)) return false

        // The incoming Binder caller is Halcyon. Clear it so the notification service sees
        // the Shizuku user service's shell/root identity, then always restore the caller.
        val identity = Binder.clearCallingIdentity()
        try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val binder = serviceManager.getMethod("getService", String::class.java)
                .invoke(null, "notification") as? IBinder ?: return false
            val stub = Class.forName("android.app.INotificationManager\$Stub")
            val manager = stub.getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder) ?: return false
            val scene = "HALCYON_MEDIA_$packageName"
            val switchValue = enabled.toString()
            val setter = manager.javaClass.getMethod(
                "setSuperXInfosSceneList",
                List::class.java,
                List::class.java,
                List::class.java,
                List::class.java
            ).apply { isAccessible = true }
            // The system updates these named scenes individually. Sending just our scene
            // leaves every other app's scene and the global notification switch intact.
            setter.invoke(
                manager,
                listOf(scene),
                listOf(switchValue),
                listOf(packageName),
                listOf(switchValue)
            )
            val getter = manager.javaClass.getMethod(
                "getSceneStatus", String::class.java, String::class.java, Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
            // The setter can silently reject callers. Verify the effective SuperX state;
            // OriginOS defines switch type 0 as SuperX and returns a boolean.
            return (getter.invoke(manager, packageName, scene, 0) as? Boolean) == enabled
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val cause = (error as? InvocationTargetException)?.targetException
            if (cause is CancellationException) throw cause
            Log.w(TAG, "Unable to update OriginOS media island scene", cause ?: error)
            return false
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private companion object {
        const val TAG = "HalcyonShizuku"
        const val COMMAND_TIMEOUT_SECONDS = 10L
        const val OUTPUT_READ_TIMEOUT_SECONDS = 1L
        val PACKAGE_NAME_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    }
}
