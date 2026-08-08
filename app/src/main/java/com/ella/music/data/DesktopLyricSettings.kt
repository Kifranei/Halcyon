package com.ella.music.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DesktopLyricSettings(private val dataStore: DataStore<Preferences>) {
    companion object {
        const val MIN_WIDTH_PERCENT = 30
        const val MAX_WIDTH_PERCENT = 100

        val KEY_DESKTOP_LYRIC_ENABLED = booleanPreferencesKey("desktop_lyric_enabled")
        val KEY_DESKTOP_LYRIC_HIDE_WHEN_PAUSED = booleanPreferencesKey("desktop_lyric_hide_when_paused")
        val KEY_DESKTOP_LYRIC_HIDE_IN_LANDSCAPE = booleanPreferencesKey("desktop_lyric_hide_in_landscape")
        val KEY_DESKTOP_LYRIC_HIDE_ON_PLAYER_PAGE = booleanPreferencesKey("desktop_lyric_hide_on_player_page")
        val KEY_DESKTOP_LYRIC_HIDE_ON_LYRICS_PAGE = booleanPreferencesKey("desktop_lyric_hide_on_lyrics_page")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_MODE = booleanPreferencesKey("desktop_lyric_status_bar_mode")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_HIDE_WHEN_PAUSED = booleanPreferencesKey("desktop_lyric_status_bar_hide_when_paused")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_HIDE_IN_LANDSCAPE = booleanPreferencesKey("desktop_lyric_status_bar_hide_in_landscape")
        val KEY_DESKTOP_LYRIC_WIDTH = intPreferencesKey("desktop_lyric_width")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_TOP_OFFSET = intPreferencesKey("desktop_lyric_status_bar_top_offset")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_POSITION = intPreferencesKey("desktop_lyric_status_bar_position")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_WIDTH = intPreferencesKey("desktop_lyric_status_bar_width")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_X_OFFSET = intPreferencesKey("desktop_lyric_status_bar_x_offset")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_ALIGN = intPreferencesKey("desktop_lyric_status_bar_text_align")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_VERTICAL_ALIGN = intPreferencesKey("desktop_lyric_status_bar_vertical_align")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY = intPreferencesKey("desktop_lyric_status_bar_secondary")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY_OPACITY = intPreferencesKey("desktop_lyric_status_bar_secondary_opacity")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_MERGE_SECONDARY = booleanPreferencesKey("desktop_lyric_status_bar_merge_secondary")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_FONT_SCALE = intPreferencesKey("desktop_lyric_status_bar_font_scale")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_TRANSLATION_SCALE = intPreferencesKey("desktop_lyric_status_bar_translation_scale")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_OPACITY = intPreferencesKey("desktop_lyric_status_bar_opacity")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_COLOR = intPreferencesKey("desktop_lyric_status_bar_text_color")
        val KEY_DESKTOP_LYRIC_LOCKED = booleanPreferencesKey("desktop_lyric_locked")
        val KEY_DESKTOP_LYRIC_FONT_SCALE = intPreferencesKey("desktop_lyric_font_scale")
        val KEY_DESKTOP_LYRIC_TRANSLATION_SCALE = intPreferencesKey("desktop_lyric_translation_scale")
        val KEY_DESKTOP_LYRIC_OPACITY = intPreferencesKey("desktop_lyric_opacity")
        val KEY_DESKTOP_LYRIC_TEXT_COLOR = intPreferencesKey("desktop_lyric_text_color")
        val KEY_DESKTOP_LYRIC_X = intPreferencesKey("desktop_lyric_x")
        val KEY_DESKTOP_LYRIC_Y = intPreferencesKey("desktop_lyric_y")
    }

    val desktopLyricEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_ENABLED] ?: false }
    val desktopLyricHideWhenPaused: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_HIDE_WHEN_PAUSED] ?: true }
    val desktopLyricHideInLandscape: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_HIDE_IN_LANDSCAPE] ?: false }
    val desktopLyricHideOnPlayerPage: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_HIDE_ON_PLAYER_PAGE] ?: false }
    val desktopLyricHideOnLyricsPage: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_HIDE_ON_LYRICS_PAGE] ?: false }
    val desktopLyricStatusBarMode: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_STATUS_BAR_MODE] ?: false }
    val desktopLyricStatusBarHideWhenPaused: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_STATUS_BAR_HIDE_WHEN_PAUSED] ?: true }
    val desktopLyricStatusBarHideInLandscape: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_STATUS_BAR_HIDE_IN_LANDSCAPE] ?: false }
    val desktopLyricWidth: Flow<Int> = dataStore.data.map {
        (it[KEY_DESKTOP_LYRIC_WIDTH] ?: 72).coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)
    }
    val desktopLyricStatusBarTopOffset: Flow<Int> = dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_TOP_OFFSET] ?: 16).coerceIn(0, 120) }
    val desktopLyricStatusBarPosition: Flow<Int> = dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_POSITION] ?: SettingsManager.DESKTOP_LYRIC_STATUS_POSITION_CENTER).coerceIn(0, 2) }
    val desktopLyricStatusBarWidth: Flow<Int> = dataStore.data.map {
        (it[KEY_DESKTOP_LYRIC_STATUS_BAR_WIDTH] ?: 72).coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)
    }
    val desktopLyricStatusBarXOffset: Flow<Int> = dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_X_OFFSET] ?: 0).coerceIn(-640, 640) }
    val desktopLyricStatusBarTextAlign: Flow<Int> = dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_ALIGN] ?: SettingsManager.DESKTOP_LYRIC_STATUS_ALIGN_LEFT).coerceIn(0, 2) }
    val desktopLyricStatusBarVerticalAlign: Flow<Int> = dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_VERTICAL_ALIGN] ?: SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_TOP).coerceIn(0, 2) }
    val desktopLyricStatusBarSecondary: Flow<Int> = dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY] ?: SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF).coerceIn(0, 2) }
    val desktopLyricStatusBarSecondaryOpacity: Flow<Int> = dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY_OPACITY] ?: 67).coerceIn(20, 100) }
    val desktopLyricStatusBarMergeSecondary: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_STATUS_BAR_MERGE_SECONDARY] ?: false }
    val desktopLyricStatusBarFontScale: Flow<Int> = dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_FONT_SCALE] ?: 100).coerceIn(80, 220) }
    val desktopLyricStatusBarTranslationScale: Flow<Int> = dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_TRANSLATION_SCALE] ?: 90).coerceIn(80, 220) }
    val desktopLyricStatusBarOpacity: Flow<Int> = dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_OPACITY] ?: 100).coerceIn(35, 100) }
    val desktopLyricStatusBarTextColor: Flow<Int> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_COLOR] ?: -1 }
    val desktopLyricLocked: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_LOCKED] ?: false }
    val desktopLyricFontScale: Flow<Int> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_FONT_SCALE] ?: 100 }
    val desktopLyricTranslationScale: Flow<Int> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_TRANSLATION_SCALE] ?: 90 }
    val desktopLyricOpacity: Flow<Int> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_OPACITY] ?: 100 }
    val desktopLyricTextColor: Flow<Int> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_TEXT_COLOR] ?: -1 }
    val desktopLyricX: Flow<Int> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_X] ?: Int.MIN_VALUE }
    val desktopLyricY: Flow<Int> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_Y] ?: Int.MIN_VALUE }

    suspend fun setDesktopLyricEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_ENABLED] = enabled }
    }

    suspend fun setDesktopLyricHideWhenPaused(enabled: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_HIDE_WHEN_PAUSED] = enabled }
    }

    suspend fun setDesktopLyricHideInLandscape(enabled: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_HIDE_IN_LANDSCAPE] = enabled }
    }

    suspend fun setDesktopLyricHideOnPlayerPage(enabled: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_HIDE_ON_PLAYER_PAGE] = enabled }
    }

    suspend fun setDesktopLyricHideOnLyricsPage(enabled: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_HIDE_ON_LYRICS_PAGE] = enabled }
    }

    suspend fun setDesktopLyricStatusBarMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_MODE] = enabled }
    }

    suspend fun setDesktopLyricStatusBarHideWhenPaused(enabled: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_HIDE_WHEN_PAUSED] = enabled }
    }

    suspend fun setDesktopLyricStatusBarHideInLandscape(enabled: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_HIDE_IN_LANDSCAPE] = enabled }
    }

    suspend fun setDesktopLyricWidth(widthPercent: Int) {
        dataStore.edit {
            it[KEY_DESKTOP_LYRIC_WIDTH] = widthPercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)
        }
    }

    suspend fun setDesktopLyricStatusBarTopOffset(offsetDp: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_TOP_OFFSET] = offsetDp.coerceIn(0, 120) }
    }

    suspend fun setDesktopLyricStatusBarPosition(position: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_POSITION] = position.coerceIn(0, 2) }
    }

    suspend fun setDesktopLyricStatusBarWidth(widthPercent: Int) {
        dataStore.edit {
            it[KEY_DESKTOP_LYRIC_STATUS_BAR_WIDTH] = widthPercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)
        }
    }

    suspend fun setDesktopLyricStatusBarXOffset(offsetDp: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_X_OFFSET] = offsetDp.coerceIn(-640, 640) }
    }

    suspend fun setDesktopLyricStatusBarTextAlign(align: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_ALIGN] = align.coerceIn(0, 2) }
    }

    suspend fun setDesktopLyricStatusBarVerticalAlign(align: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_VERTICAL_ALIGN] = align.coerceIn(0, 2) }
    }

    suspend fun setDesktopLyricStatusBarSecondary(mode: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY] = mode.coerceIn(0, 2) }
    }

    suspend fun setDesktopLyricStatusBarSecondaryOpacity(opacity: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY_OPACITY] = opacity.coerceIn(20, 100) }
    }

    suspend fun setDesktopLyricStatusBarMergeSecondary(enabled: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_MERGE_SECONDARY] = enabled }
    }

    suspend fun setDesktopLyricStatusBarFontScale(scale: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_FONT_SCALE] = scale.coerceIn(80, 220) }
    }

    suspend fun setDesktopLyricStatusBarTranslationScale(scale: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_TRANSLATION_SCALE] = scale.coerceIn(80, 220) }
    }

    suspend fun setDesktopLyricStatusBarOpacity(opacity: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_OPACITY] = opacity.coerceIn(35, 100) }
    }

    suspend fun setDesktopLyricStatusBarTextColor(color: Int) {
        dataStore.edit {
            it[KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_COLOR] = color
            it[KEY_DESKTOP_LYRIC_TEXT_COLOR] = color
        }
    }

    suspend fun setDesktopLyricLocked(locked: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_LOCKED] = locked }
    }

    suspend fun setDesktopLyricFontScale(scale: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_FONT_SCALE] = scale.coerceIn(80, 220) }
    }

    suspend fun setDesktopLyricTranslationScale(scale: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_TRANSLATION_SCALE] = scale.coerceIn(80, 220) }
    }

    suspend fun setDesktopLyricOpacity(opacity: Int) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_OPACITY] = opacity.coerceIn(35, 100) }
    }

    suspend fun setDesktopLyricTextColor(color: Int) {
        dataStore.edit {
            it[KEY_DESKTOP_LYRIC_TEXT_COLOR] = color
            it[KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_COLOR] = color
        }
    }

    suspend fun setDesktopLyricPosition(x: Int, y: Int) {
        dataStore.edit {
            it[KEY_DESKTOP_LYRIC_X] = x
            it[KEY_DESKTOP_LYRIC_Y] = y
        }
    }

    suspend fun resetDesktopLyricPosition() {
        dataStore.edit {
            it.remove(KEY_DESKTOP_LYRIC_X)
            it.remove(KEY_DESKTOP_LYRIC_Y)
        }
    }
}
