package com.ella.music.oem

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import coil3.imageLoader
import com.ella.music.data.AppLogStore
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.components.clearArtworkModelMemoryCache
import com.ella.music.ui.player.clearPlayerPaletteMemoryCache

/** HyperOS TRIM/KILL integration that replies to the system callback within its deadline. */
internal object HyperOsFairMemoryAdapter : IBinder.DeathRecipient {
    private const val TAG = "FairMemory"
    private const val ACTION_TRIM = "itgsa.intent.action.TRIM"
    private const val ACTION_KILL = "itgsa.intent.action.KILL"
    private const val KEY_COMMON = "common"
    private const val KEY_EXTRA = "extra"
    private const val KEY_NOTIFY_TYPE = "notifyType"
    private const val KEY_NOTIFY_ID = "notifyId"
    private const val KEY_REASON = "reason"
    private const val KEY_ACTION = "action"
    private const val KEY_CALLBACK = "callback"
    private const val TRANSACTION_EXCEPTION_REPLY = IBinder.FIRST_CALL_TRANSACTION
    private const val RESULT_SUCCESS = 0

    @Volatile
    private var initialized = false
    private var remoteCallback: IBinder? = null

    fun initialize(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val worker = HandlerThread(TAG).apply { start() }
            val registered = runCatching {
                val filter = IntentFilter().apply {
                    addAction(ACTION_TRIM)
                    addAction(ACTION_KILL)
                }
                val handler = Handler(worker.looper)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    application.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    application.registerReceiver(receiver, filter, null, handler)
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to register HyperOS memory receiver", error)
                AppLogStore.error(application, TAG, "Memory receiver registration failed", error)
                worker.quitSafely()
            }.isSuccess
            if (registered) {
                initialized = true
                AppLogStore.info(application, TAG, "Registered $ACTION_TRIM and $ACTION_KILL")
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_TRIM && intent.action != ACTION_KILL) return
            val common = intent.extras?.getBundle(KEY_COMMON) ?: return
            val callback = common.getBinder(KEY_CALLBACK) ?: return
            val notifyType = common.getInt(KEY_NOTIFY_TYPE)
            val notifyId = common.getInt(KEY_NOTIFY_ID)
            val reason = common.getString(KEY_REASON).orEmpty()
            val isKill = intent.action == ACTION_KILL || common.getString(KEY_ACTION).orEmpty()
                .contains("KILL", ignoreCase = true)
            val result = runCatching {
                releaseRecreatableCaches(context.applicationContext)
                if (isKill) checkpoint(context.applicationContext, notifyType, notifyId, reason)
                RESULT_SUCCESS
            }.onFailure { error ->
                AppLogStore.error(context, TAG, "Memory callback handling failed", error)
            }.getOrDefault(1)
            val extra = intent.extras?.getBundle(KEY_EXTRA)
            Log.i(TAG, "Handled ${if (isKill) "KILL" else "TRIM"} type=$notifyType id=$notifyId pss=${extra?.getInt("pss", -1)}")
            if (rememberCallback(callback)) reply(notifyType, notifyId, result, isKill)
        }
    }

    override fun binderDied() {
        synchronized(this) { remoteCallback = null }
    }

    private fun rememberCallback(callback: IBinder): Boolean = synchronized(this) {
        val current = remoteCallback
        if (current === callback) return@synchronized true
        current?.let { runCatching { it.unlinkToDeath(this, 0) } }
        try {
            callback.linkToDeath(this, 0)
            remoteCallback = callback
            true
        } catch (_: RemoteException) {
            remoteCallback = null
            false
        }
    }

    private fun releaseRecreatableCaches(context: Context) {
        MusicRepository.clearMemoryCachesIfInitialized()
        clearArtworkModelMemoryCache()
        clearPlayerPaletteMemoryCache()
        runCatching { context.imageLoader.memoryCache?.clear() }
    }

    private fun checkpoint(context: Context, notifyType: Int, notifyId: Int, reason: String) {
        context.getSharedPreferences("hyperos_fair_memory", Context.MODE_PRIVATE).edit()
            .putLong("last_kill_request_at", System.currentTimeMillis())
            .putInt("last_notify_type", notifyType)
            .putInt("last_notify_id", notifyId)
            .putString("last_reason", reason)
            .commit()
    }

    private fun reply(notifyType: Int, notifyId: Int, result: Int, isKill: Boolean) {
        val data = Parcel.obtain()
        val response = Parcel.obtain()
        try {
            val callback = synchronized(this) { remoteCallback } ?: return
            data.writeInt(notifyType)
            data.writeInt(notifyId)
            data.writeInt(result)
            data.writeBundle(Bundle().apply {
                putString("reply", if (isKill) "state_checkpointed" else "memory_released")
            })
            callback.transact(TRANSACTION_EXCEPTION_REPLY, data, response, 0)
            response.readException()
        } catch (error: Exception) {
            Log.e(TAG, "Memory callback reply failed", error)
        } finally {
            response.recycle()
            data.recycle()
        }
    }
}
