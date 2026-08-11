package com.ella.music.oem

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.lang.reflect.Proxy
import java.util.function.Supplier

/**
 * Optional bridge for the HyperOS application-handoff SDK.
 *
 * The SDK is supplied by HyperOS and is not available in the public Gradle repositories. Keeping
 * this adapter reflective lets the normal APK build and run on other Android distributions while
 * still using the official Handoff API when the vendor SDK is present on a HyperOS device.
 */
internal class XiaomiHandoffBridge(
    private val activity: Activity,
    private val deepLinkProvider: () -> Uri
) {
    private var session: Any? = null
    private var published = false

    fun publish() {
        if (published) return
        runCatching {
            val handoffClass = Class.forName(HANDOFF_CLASS)
            val builder = invokeStatic(handoffClass, "from", activity)
            val supplier = Supplier { deepLinkProvider() }
            val configuredBuilder = findMethod(builder, "setDeepLink", 1)
                .invoke(builder, supplier)
                ?: builder
            val builtSession = findMethod(configuredBuilder, "build", 0)
                .invoke(configuredBuilder)
                ?: return
            val publishMethod = findMethod(builtSession, "publish", 1)
            val callbackType = publishMethod.parameterTypes[0]
            val callback = Proxy.newProxyInstance(
                callbackType.classLoader ?: XiaomiHandoffBridge::class.java.classLoader,
                arrayOf(callbackType)
            ) { _, method, args ->
                if (method.name == "onError") {
                    Log.w(TAG, "HyperOS handoff error: ${args?.joinToString()}")
                }
                null
            }
            publishMethod.invoke(builtSession, callback)
            session = builtSession
            published = true
        }.onFailure { error ->
            // ClassNotFoundException is expected on non-HyperOS builds. Other failures are logged
            // so a vendor SDK/API mismatch can be diagnosed from a user's device log.
            if (error !is ClassNotFoundException) {
                Log.w(TAG, "HyperOS handoff SDK unavailable or incompatible", error)
            }
        }
    }

    fun onNewIntent(intent: Intent) {
        val activeSession = session ?: return
        runCatching { findMethod(activeSession, "onNewIntent", 1).invoke(activeSession, intent) }
            .onFailure { Log.w(TAG, "Failed to forward HyperOS handoff intent", it) }
    }

    fun cancel() {
        val activeSession = session ?: return
        runCatching { findMethod(activeSession, "cancel", 0).invoke(activeSession) }
            .onFailure { Log.w(TAG, "Failed to cancel HyperOS handoff", it) }
        session = null
        published = false
    }

    private fun invokeStatic(type: Class<*>, name: String, vararg args: Any?): Any {
        val method = type.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == args.size
        } ?: error("$HANDOFF_CLASS.$name is missing")
        return method.invoke(null, *args) ?: error("$HANDOFF_CLASS.$name returned null")
    }

    private fun findMethod(target: Any, name: String, parameterCount: Int) =
        target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == parameterCount
        } ?: error("${target.javaClass.name}.$name is missing")

    private companion object {
        const val HANDOFF_CLASS = "com.xiaomi.dist.handoff.sdk.Handoff"
        const val TAG = "XiaomiHandoff"
    }
}
