package com.ella.music.data

import android.os.Build

object HyperOsDetector {
    const val BG_EFFECT_OS2 = 0
    const val BG_EFFECT_OS3 = 1
    const val BG_EFFECT_OS1 = 2

    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        return manufacturer in setOf("xiaomi", "redmi", "poco") ||
            brand in setOf("xiaomi", "redmi", "poco")
    }

    fun getSystemProperty(key: String): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, key, "") as? String).orEmpty()
        }.getOrDefault("")
    }

    /**
     * Extracts HyperOS major version from system properties, e.g. "OS2.0..." -> 2.
     * Returns null if not a Xiaomi device or no HyperOS version property is present.
     */
    fun getHyperOsMajorVersion(): Int? {
        if (!isXiaomiDevice()) return null
        val osName = getSystemProperty("ro.mi.os.version.name")
        val incremental = getSystemProperty("ro.build.version.incremental")
        val raw = osName.ifBlank { incremental }
        if (raw.isBlank()) return null

        val regex = Regex("""OS(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(raw)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /**
     * HyperOS 1 (OS1) defaults to OS1 (2).
     * HyperOS 2 (OS2) defaults to OS2 (0).
     * HyperOS 3 and higher (or other devices) default to OS3 (1).
     */
    fun defaultBgEffectVersion(): Int {
        val major = getHyperOsMajorVersion() ?: return BG_EFFECT_OS3
        return when (major) {
            1 -> BG_EFFECT_OS1
            2 -> BG_EFFECT_OS2
            else -> BG_EFFECT_OS3
        }
    }
}
