package com.ella.music

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.ella.music.data.SettingsManager
import java.util.Locale

internal fun Context.withHalcyonLocale(languageTag: String): Context {
    // Use this app's incoming configuration. On Android 13+ it already contains the locale
    // selected by Settings > Apps > Halcyon > Language; Resources.getSystem() loses that override.
    val incoming = resources.configuration
    val systemLocales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        List(incoming.locales.size()) { incoming.locales[it] }
    } else {
        @Suppress("DEPRECATION")
        listOf(incoming.locale)
    }
    val locale = resolveHalcyonLocale(languageTag, systemLocales)
    val configuration = Configuration(incoming).apply {
        Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            setLocale(locale)
        }
    }
    return createConfigurationContext(configuration)
}

internal fun resolveHalcyonLocale(languageTag: String, systemLocales: List<Locale>): Locale {
    val explicit = explicitHalcyonLocale(languageTag)
    if (explicit != null) return explicit

    // Respect the user's ordered language list, choosing the first translation the app ships.
    // If none is available, show English instead of falling through to the Chinese base strings.
    return systemLocales.firstNotNullOfOrNull(::supportedLocaleFor) ?: Locale.ENGLISH
}

private fun explicitHalcyonLocale(languageTag: String): Locale? = when (languageTag) {
    SettingsManager.APP_LANGUAGE_ZH_CN -> Locale.SIMPLIFIED_CHINESE
    SettingsManager.APP_LANGUAGE_ZH_TW -> Locale.TRADITIONAL_CHINESE
    SettingsManager.APP_LANGUAGE_EN -> Locale.ENGLISH
    SettingsManager.APP_LANGUAGE_JA -> Locale.JAPANESE
    SettingsManager.APP_LANGUAGE_KO -> Locale.KOREAN
    SettingsManager.APP_LANGUAGE_DE -> Locale.GERMAN
    SettingsManager.APP_LANGUAGE_FR -> Locale.FRENCH
    SettingsManager.APP_LANGUAGE_RU -> Locale.forLanguageTag("ru")
    SettingsManager.APP_LANGUAGE_TR -> Locale.forLanguageTag("tr")
    SettingsManager.APP_LANGUAGE_AR -> Locale.forLanguageTag("ar")
    else -> null
}

private fun supportedLocaleFor(deviceLocale: Locale): Locale? = when (deviceLocale.language.lowercase(Locale.ROOT)) {
    "zh" -> if (
        deviceLocale.script.equals("Hant", ignoreCase = true) ||
        deviceLocale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
    ) Locale.TRADITIONAL_CHINESE else Locale.SIMPLIFIED_CHINESE
    "en" -> Locale.ENGLISH
    "ja" -> Locale.JAPANESE
    "ko" -> Locale.KOREAN
    "de" -> Locale.GERMAN
    "fr" -> Locale.FRENCH
    "ru" -> Locale.forLanguageTag("ru")
    "tr" -> Locale.forLanguageTag("tr")
    "ar" -> Locale.forLanguageTag("ar")
    else -> null
}

/** Sync an explicit in-app choice to Android 13's system-managed app-language setting. */
internal fun Context.syncPlatformApplicationLocale(languageTag: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        languageTag == SettingsManager.APP_LANGUAGE_SYSTEM
    ) return false
    val manager = getSystemService(LocaleManager::class.java) ?: return false
    val desired = LocaleList.forLanguageTags(languageTag)
    if (manager.applicationLocales == desired) return false
    manager.applicationLocales = desired
    return true
}

/** Called only when the user explicitly chooses “Follow system” inside Halcyon's settings. */
internal fun Context.resetPlatformApplicationLocale(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val manager = getSystemService(LocaleManager::class.java) ?: return false
    val systemLocales = LocaleList.getEmptyLocaleList()
    if (manager.applicationLocales == systemLocales) return false
    manager.applicationLocales = systemLocales
    return true
}
