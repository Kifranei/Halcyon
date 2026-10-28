package com.ella.music.player

import android.content.Context
import android.os.Build
import android.util.Log
import com.ella.music.shizuku.ShizukuShellCommandExecutor
import kotlinx.coroutines.CancellationException

/** Registers only this player's live-notification scene; other apps' scenes are untouched. */
internal object OriginOsMediaIslandAccess {
    private const val TAG = "HalcyonOriginIsland"

    fun isSupportedDevice(): Boolean =
        Build.VERSION.SDK_INT >= 36 && VivoAtomWalkmanWhitelist.isVivoOrIqooDevice()

    suspend fun setEnabled(
        context: Context,
        enabled: Boolean,
        requestPermission: Boolean = true
    ): Boolean {
        if (!isSupportedDevice()) return !enabled
        return try {
            ShizukuShellCommandExecutor.setOriginOsMediaIslandScene(
                context.applicationContext,
                context.packageName,
                enabled,
                requestPermission
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Unable to update OriginOS playback scene", error)
            false
        }
    }
}
