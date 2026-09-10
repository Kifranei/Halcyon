package com.ella.music

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import com.ella.music.data.SettingsManager
import java.util.Locale

internal fun Context.withHalcyonLocale(languageTag: String): Context {
    val locale = languageTag.toHalcyonLocale()
    val configuration = Configuration(resources.configuration).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val locales = locale?.let { android.os.LocaleList(it) }
                ?: Resources.getSystem().configuration.locales
            Locale.setDefault(locales.get(0))
            setLocales(locales)
        } else {
            val resolvedLocale = locale ?: Resources.getSystem().configuration.primaryLocale()
            Locale.setDefault(resolvedLocale)
            @Suppress("DEPRECATION")
            setLocale(resolvedLocale)
        }
    }
    return createConfigurationContext(configuration)
}

private fun Configuration.primaryLocale(): Locale {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        locales.get(0)
    } else {
        @Suppress("DEPRECATION")
        locale
    }
}

private fun String.toHalcyonLocale(): Locale? = when (this) {
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
