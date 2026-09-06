package com.ella.music.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.ella.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Binds the Shizuku user service once and serializes calls over its Binder interface. */
internal object ShizukuShellCommandExecutor {
    private const val TAG = "HalcyonShizuku"
    private const val SERVICE_PROCESS_SUFFIX = "shell"
    private const val SERVICE_VERSION = 1
    private const val SERVICE_CONNECTION_TIMEOUT_MS = 15_000L

    private val commandMutex = Mutex()
    private val connectionLock = Any()

    @Volatile
    private var service: IShizukuShellService? = null
    private var pendingConnection: kotlinx.coroutines.CancellableContinuation<IShizukuShellService>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val remote = binder?.let { IShizukuShellService.Stub.asInterface(it) }
            if (remote == null) {
                completeConnectionExceptionally(IllegalStateException("Shizuku user service returned no binder"))
                return
            }
            service = remote
            val continuation = synchronized(connectionLock) {
                pendingConnection.also { pendingConnection = null }
            }
            continuation?.resume(remote)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            completeConnectionExceptionally(IllegalStateException("Shizuku user service disconnected"))
        }
    }

    suspend fun execute(context: Context, command: String): String = commandMutex.withLock {
        check(ShizukuPermissionHelper.ensurePermission()) {
            "Shizuku is unavailable or permission was denied"
        }
        val remote = connect(context.applicationContext)
        try {
            withContext(Dispatchers.IO) { remote.exec(command) }
        } catch (error: Throwable) {
            service = null
            Log.w(TAG, "Shizuku shell command failed", error)
            throw error
        }
    }

    private suspend fun connect(context: Context): IShizukuShellService {
        service?.let { return it }
        return withContext(Dispatchers.Main.immediate) {
            service?.let { return@withContext it }
            withTimeout(SERVICE_CONNECTION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    synchronized(connectionLock) {
                        pendingConnection = continuation
                    }
                    continuation.invokeOnCancellation {
                        synchronized(connectionLock) {
                            if (pendingConnection === continuation) pendingConnection = null
                        }
                    }

                    val args = Shizuku.UserServiceArgs(
                        ComponentName(context.packageName, ShizukuShellService::class.java.name)
                    )
                        .daemon(true)
                        .processNameSuffix(SERVICE_PROCESS_SUFFIX)
                        .debuggable(BuildConfig.DEBUG)
                        .version(SERVICE_VERSION)
                    runCatching { Shizuku.bindUserService(args, serviceConnection) }.onFailure {
                        completeConnectionExceptionally(it)
                    }
                }
            }
        }
    }

    private fun completeConnectionExceptionally(error: Throwable) {
        val continuation = synchronized(connectionLock) {
            pendingConnection.also { pendingConnection = null }
        }
        continuation?.resumeWithException(error)
    }
}
