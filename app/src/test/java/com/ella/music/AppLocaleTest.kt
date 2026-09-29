package com.ella.music

import com.ella.music.data.SettingsManager
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleTest {
    @Test
    fun systemLanguageUsesFirstSupportedLocaleFromOrderedPreferences() {
        val locale = resolveHalcyonLocale(
            SettingsManager.APP_LANGUAGE_SYSTEM,
            listOf(Locale.forLanguageTag("es-MX"), Locale.forLanguageTag("en-GB"))
        )

        assertEquals("en", locale.language)
    }

    @Test
    fun unsupportedSystemLanguageFallsBackToEnglishInsteadOfBaseChinese() {
        val locale = resolveHalcyonLocale(
            SettingsManager.APP_LANGUAGE_SYSTEM,
            listOf(Locale.forLanguageTag("es-MX"), Locale.forLanguageTag("th-TH"))
        )

        assertEquals(Locale.ENGLISH, locale)
    }

    @Test
    fun systemChineseScriptAndRegionSelectTheMatchingVariant() {
        val locale = resolveHalcyonLocale(
            SettingsManager.APP_LANGUAGE_SYSTEM,
            listOf(Locale.forLanguageTag("zh-Hant-HK"))
        )

        assertEquals("TW", locale.country)
    }

    @Test
    fun explicitInAppChoiceOverridesDeviceLanguages() {
        val locale = resolveHalcyonLocale(
            SettingsManager.APP_LANGUAGE_DE,
            listOf(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH)
        )

        assertEquals(Locale.GERMAN, locale)
    }
}
