package com.ella.music.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/** Shared, serialized Shizuku permission request used by all privileged integrations. */
internal object ShizukuPermissionHelper {
    private val permissionMutex = Mutex()
    private var nextRequestCode = 2000

    suspend fun ensurePermission(): Boolean = permissionMutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return@withContext false
            if (runCatching { Shizuku.checkSelfPermission() }
                    .getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext true
            }

            suspendCancellableCoroutine { continuation ->
                val requestCode = synchronized(this@ShizukuPermissionHelper) {
                    nextRequestCode = (nextRequestCode + 1).coerceAtLeast(2001)
                    nextRequestCode
                }
                lateinit var listener: Shizuku.OnRequestPermissionResultListener
                listener = Shizuku.OnRequestPermissionResultListener { returnedCode, result ->
                    if (returnedCode != requestCode || !continuation.isActive) return@OnRequestPermissionResultListener
                    Shizuku.removeRequestPermissionResultListener(listener)
                    continuation.resume(result == PackageManager.PERMISSION_GRANTED)
                }
                Shizuku.addRequestPermissionResultListener(listener)
                continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
                runCatching { Shizuku.requestPermission(requestCode) }.onFailure {
                    Shizuku.removeRequestPermissionResultListener(listener)
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        }
    }
}
