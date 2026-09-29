package com.ella.music.plugin.i18n

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.icu.util.ULocale
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application configuration honors both system and per-app language preferences. */
object PluginLocales {
    private val current = MutableStateFlow<List<String>>(emptyList())
    val preferences = current.asStateFlow()
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        PluginStrings.inferScript = { ULocale.addLikelySubtags(ULocale.forLocale(it)).script }
        update(context.resources.configuration)
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = update(newConfig)
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
    }

    fun update(configuration: Configuration) {
        // Before Android 13 the Application configuration does not include AppCompat's override.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            if (!appLocales.isEmpty) {
                current.value = appLocales.toLanguageTags().split(',')
                return
            }
        }
        current.value = (0 until configuration.locales.size()).map { configuration.locales[it].toLanguageTag() }
    }
}
