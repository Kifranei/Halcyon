package com.ella.music.data

import androidx.datastore.preferences.core.Preferences

internal fun resolveContinuePlaybackVisibility(preferences: Preferences, library: Boolean): Boolean {
    val key = if (library) SettingsManager.KEY_LIBRARY_CONTINUE_PLAYBACK_ROW_VISIBLE
        else SettingsManager.KEY_CATEGORY_CONTINUE_PLAYBACK_ROW_VISIBLE
    // Keep the old single-switch choice until each independent choice is explicitly saved.
    return preferences[key] ?: preferences[SettingsManager.KEY_CONTINUE_PLAYBACK_ROW_VISIBLE] ?: true
}
