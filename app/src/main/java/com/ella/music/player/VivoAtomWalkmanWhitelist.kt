package com.ella.music.player

import android.content.Context
import android.os.Build
import android.util.Log
import com.ella.music.shizuku.ShizukuShellCommandExecutor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maintains this app's entry in vivo/iQOO's Atom Walkman playback-source list.
 *
 * OriginOS stores the list as a JSON array in Settings.System. Reading the value before writing
 * it is important: replacing the complete list would remove other players selected by the user.
 */
internal object VivoAtomWalkmanWhitelist {
    private const val TAG = "HalcyonVivoWhitelist"
    const val SETTINGS_KEY = "musicwidget_list_pkg_type_key"

    fun isVivoOrIqooDevice(): Boolean = listOf(
        Build.MANUFACTURER,
        Build.BRAND,
        Build.PRODUCT,
        Build.DEVICE,
        Build.MODEL
    ).any { identifier ->
        identifier.orEmpty().contains("vivo", ignoreCase = true) ||
            identifier.orEmpty().contains("iqoo", ignoreCase = true)
    }

    suspend fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val packageName = context.packageName
        return try {
            val current = readPackageList(context)
            val updated = updatePackageList(current, packageName, enabled)
            if (updated != current) {
                ShizukuShellCommandExecutor.execute(context, buildWriteCommand(encodePackageList(updated)))
            }
            val verified = readPackageList(context)
            verified.contains(packageName) == enabled
        } catch (error: Throwable) {
            Log.w(TAG, "Unable to ${if (enabled) "add" else "remove"} Atom Walkman whitelist entry", error)
            false
        }
    }

    private suspend fun readPackageList(context: Context): List<String> {
        val raw = ShizukuShellCommandExecutor.execute(context, READ_COMMAND)
        return parsePackageList(raw)
    }

    internal fun updatePackageList(current: List<String>, packageName: String, enabled: Boolean): List<String> {
        if (enabled) {
            return if (current.contains(packageName)) current.toList() else current + packageName
        }
        return current.filterNot { it == packageName }
    }

    internal fun parsePackageList(raw: String): List<String> {
        val normalized = raw.trim()
        if (normalized.isEmpty() || normalized == "null" || normalized == "undefined") return emptyList()

        val array = Json.parseToJsonElement(normalized).jsonArray
        return array.mapNotNull { element ->
            val value = element.jsonPrimitive
                .takeIf(JsonPrimitive::isString)
                ?.contentOrNull
            value
        }
    }

    internal fun encodePackageList(packages: List<String>): String {
        return buildJsonArray {
            packages.forEach { add(JsonPrimitive(it)) }
        }.toString()
    }

    internal fun buildWriteCommand(json: String): String =
        "settings put system $SETTINGS_KEY ${shellQuote(json)}"

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"

    private const val READ_COMMAND = "settings get system $SETTINGS_KEY"
}
