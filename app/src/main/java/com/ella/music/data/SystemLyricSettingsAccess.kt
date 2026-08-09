package com.ella.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.ella.music.data.SettingsManager.Companion.OPLUS_LYRIC_MODE_MODULE
import com.ella.music.data.SettingsManager.Companion.OPLUS_LYRIC_MODE_SYSTEM
import com.ella.music.data.SettingsManager.Companion.KEY_BLUETOOTH_LYRIC_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_BLUETOOTH_LYRIC_PRONUNCIATION
import com.ella.music.data.SettingsManager.Companion.KEY_BLUETOOTH_LYRIC_TRANSLATION
import com.ella.music.data.SettingsManager.Companion.KEY_COLOROS_LOCK_SCREEN_LYRIC_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_COLOROS_LOCK_SCREEN_LYRIC_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_GETTER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_LIVE_UPDATE_LYRIC_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_LIVE_UPDATE_LYRIC_DISPLAY_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_LIVE_UPDATE_LYRIC_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_LIVE_UPDATE_LYRIC_SECONDARY_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_XIAOMI_SUPER_ISLAND_LYRIC_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_XIAOMI_SUPER_ISLAND_SETTINGS
import com.ella.music.data.SettingsManager.Companion.KEY_LYRICON_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_LYRICON_PRONUNCIATION
import com.ella.music.data.SettingsManager.Companion.KEY_LYRICON_TRANSLATION
import com.ella.music.data.SettingsManager.Companion.KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION
import com.ella.music.data.SettingsManager.Companion.KEY_STATUS_BAR_ALLOW_PHONETIC
import com.ella.music.data.SettingsManager.Companion.KEY_SUPER_LYRIC_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_SUPER_LYRIC_PRONUNCIATION
import com.ella.music.data.SettingsManager.Companion.KEY_SUPER_LYRIC_TRANSLATION
import com.ella.music.data.SettingsManager.Companion.KEY_TICKER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_TICKER_HEADS_UP_LYRICS
import com.ella.music.data.SettingsManager.Companion.KEY_TICKER_HIDE_NOTIFICATION
import com.ella.music.data.SettingsManager.Companion.KEY_MEDIA_NOTIFICATION_BUTTONS
import com.ella.music.data.SettingsManager.Companion.normalizeMediaNotificationButtonIds
import com.ella.music.data.SettingsManager.Companion.LIVE_UPDATE_LYRIC_MODE_ORIGINAL
import com.ella.music.data.SettingsManager.Companion.LIVE_UPDATE_LYRIC_MODE_PRONUNCIATION
import com.ella.music.data.SettingsManager.Companion.LIVE_UPDATE_LYRIC_DISPLAY_MODE_COMPACT
import com.ella.music.data.SettingsManager.Companion.LIVE_UPDATE_LYRIC_DISPLAY_MODE_FULL
import com.ella.music.data.SettingsManager.Companion.LIVE_UPDATE_LYRIC_SECONDARY_MODE_SONG
import com.ella.music.data.SettingsManager.Companion.LIVE_UPDATE_LYRIC_SECONDARY_MODE_PRONUNCIATION
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Status-bar / system-surface lyric bridges: Lyricon, ticker, Samsung floating lyrics,
 * SuperLyric, Lyric Getter, Bluetooth (AVRCP) lyrics and ColorOS lock-screen lyrics.
 *
 * Extracted verbatim from [SettingsManager], which implements this interface via class
 * delegation so every call site keeps using settingsManager.<member> unchanged. All flow
 * properties MUST stay eagerly-initialised stored properties (never computed get() =):
 * Compose collectAsState keys on the flow instance, and a fresh instance per access would
 * restart collection on every recomposition.
 */
interface SystemLyricSettingsAccess {
    val lyriconEnabled: Flow<Boolean>
    val lyriconTranslation: Flow<Boolean>
    val lyriconPronunciation: Flow<Boolean>
    val tickerEnabled: Flow<Boolean>
    val tickerHideNotification: Flow<Boolean>
    val tickerHeadsUpLyrics: Flow<Boolean>
    val mediaNotificationButtonIds: Flow<List<String>>
    val liveUpdateLyricEnabled: Flow<Boolean>
    val liveUpdateLyricMode: Flow<Int>
    val liveUpdateLyricDisplayMode: Flow<Int>
    val liveUpdateLyricSecondaryMode: Flow<Int>
    val xiaomiSuperIslandLyricEnabled: Flow<Boolean>
    val xiaomiSuperIslandSettings: Flow<XiaomiSuperIslandSettings>
    val samsungFloatingLyricTranslation: Flow<Boolean>
    val statusBarAllowPhonetic: Flow<Boolean>
    val superLyricEnabled: Flow<Boolean>
    val superLyricTranslation: Flow<Boolean>
    val superLyricPronunciation: Flow<Boolean>
    val lyricGetterEnabled: Flow<Boolean>
    val bluetoothLyricEnabled: Flow<Boolean>
    val bluetoothLyricTranslation: Flow<Boolean>
    val bluetoothLyricPronunciation: Flow<Boolean>
    val colorOsLockScreenLyricEnabled: Flow<Boolean>
    val colorOsLockScreenLyricMode: Flow<Int>
    suspend fun setLyriconEnabled(enabled: Boolean)
    suspend fun setLyriconTranslation(enabled: Boolean)
    suspend fun setLyriconPronunciation(enabled: Boolean)
    suspend fun setTickerEnabled(enabled: Boolean)
    suspend fun setTickerHideNotification(enabled: Boolean)
    suspend fun setTickerHeadsUpLyrics(enabled: Boolean)
    suspend fun setMediaNotificationButtonIds(ids: List<String>)
    suspend fun setLiveUpdateLyricEnabled(enabled: Boolean)
    suspend fun setLiveUpdateLyricMode(mode: Int)
    suspend fun setLiveUpdateLyricDisplayMode(mode: Int)
    suspend fun setLiveUpdateLyricSecondaryMode(mode: Int)
    suspend fun setXiaomiSuperIslandLyricEnabled(enabled: Boolean)
    suspend fun setXiaomiSuperIslandSettings(settings: XiaomiSuperIslandSettings)
    suspend fun setSamsungFloatingLyricTranslation(enabled: Boolean)
    suspend fun setStatusBarAllowPhonetic(enabled: Boolean)
    suspend fun setSuperLyricEnabled(enabled: Boolean)
    suspend fun setSuperLyricTranslation(enabled: Boolean)
    suspend fun setSuperLyricPronunciation(enabled: Boolean)
    suspend fun setLyricGetterEnabled(enabled: Boolean)
    suspend fun setBluetoothLyricEnabled(enabled: Boolean)
    suspend fun setBluetoothLyricTranslation(enabled: Boolean)
    suspend fun setBluetoothLyricPronunciation(enabled: Boolean)
    suspend fun setColorOsLockScreenLyricEnabled(enabled: Boolean)
    suspend fun setColorOsLockScreenLyricMode(mode: Int)
}

internal class SystemLyricSettingsAccessImpl(private val context: Context) : SystemLyricSettingsAccess {

    private fun Int.coerceInOplusLyricMode(): Int =
        if (this == OPLUS_LYRIC_MODE_MODULE) {
            OPLUS_LYRIC_MODE_MODULE
        } else {
            OPLUS_LYRIC_MODE_SYSTEM
        }

    override val lyriconEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_ENABLED] ?: false }
    override val lyriconTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_TRANSLATION] ?: true }
    override val lyriconPronunciation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_PRONUNCIATION] ?: false }

    override val tickerEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_ENABLED] ?: false }
    override val tickerHideNotification: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_HIDE_NOTIFICATION] ?: true }
    override val tickerHeadsUpLyrics: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_HEADS_UP_LYRICS] ?: false }
    override val mediaNotificationButtonIds: Flow<List<String>> = context.dataStore.data.map {
        normalizeMediaNotificationButtonIds(it[KEY_MEDIA_NOTIFICATION_BUTTONS].orEmpty())
    }
    override val liveUpdateLyricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LIVE_UPDATE_LYRIC_ENABLED] ?: false }
    override val liveUpdateLyricMode: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LIVE_UPDATE_LYRIC_MODE] ?: LIVE_UPDATE_LYRIC_MODE_ORIGINAL)
            .coerceIn(LIVE_UPDATE_LYRIC_MODE_ORIGINAL, LIVE_UPDATE_LYRIC_MODE_PRONUNCIATION)
    }
    override val liveUpdateLyricDisplayMode: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LIVE_UPDATE_LYRIC_DISPLAY_MODE] ?: LIVE_UPDATE_LYRIC_DISPLAY_MODE_COMPACT)
            .coerceIn(LIVE_UPDATE_LYRIC_DISPLAY_MODE_COMPACT, LIVE_UPDATE_LYRIC_DISPLAY_MODE_FULL)
    }
    override val liveUpdateLyricSecondaryMode: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LIVE_UPDATE_LYRIC_SECONDARY_MODE] ?: LIVE_UPDATE_LYRIC_SECONDARY_MODE_SONG)
            .coerceIn(LIVE_UPDATE_LYRIC_SECONDARY_MODE_SONG, LIVE_UPDATE_LYRIC_SECONDARY_MODE_PRONUNCIATION)
    }
    override val xiaomiSuperIslandLyricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_XIAOMI_SUPER_ISLAND_LYRIC_ENABLED] ?: false }
    override val xiaomiSuperIslandSettings: Flow<XiaomiSuperIslandSettings> =
        context.dataStore.data.map { XiaomiSuperIslandSettings.decode(it[KEY_XIAOMI_SUPER_ISLAND_SETTINGS]) }
    override val samsungFloatingLyricTranslation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION] ?: false }
    override val statusBarAllowPhonetic: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_STATUS_BAR_ALLOW_PHONETIC] ?: false }
    override val superLyricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUPER_LYRIC_ENABLED] ?: false }
    override val superLyricTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUPER_LYRIC_TRANSLATION] ?: true }
    override val superLyricPronunciation: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUPER_LYRIC_PRONUNCIATION] ?: false }
    override val lyricGetterEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_GETTER_ENABLED] ?: false }

    override val bluetoothLyricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BLUETOOTH_LYRIC_ENABLED] ?: false }
    override val bluetoothLyricTranslation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BLUETOOTH_LYRIC_TRANSLATION] ?: true }
    override val bluetoothLyricPronunciation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BLUETOOTH_LYRIC_PRONUNCIATION] ?: false }
    override val colorOsLockScreenLyricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_COLOROS_LOCK_SCREEN_LYRIC_ENABLED] ?: false }
    override val colorOsLockScreenLyricMode: Flow<Int> =
        context.dataStore.data.map { (it[KEY_COLOROS_LOCK_SCREEN_LYRIC_MODE] ?: OPLUS_LYRIC_MODE_SYSTEM).coerceInOplusLyricMode() }
    override suspend fun setLyriconEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICON_ENABLED] = enabled }
    }

    override suspend fun setLyriconTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICON_TRANSLATION] = enabled }
    }

    override suspend fun setLyriconPronunciation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICON_PRONUNCIATION] = enabled }
    }

    override suspend fun setTickerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_ENABLED] = enabled }
    }

    override suspend fun setTickerHideNotification(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_HIDE_NOTIFICATION] = enabled }
    }

    override suspend fun setTickerHeadsUpLyrics(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_HEADS_UP_LYRICS] = enabled }
    }

    override suspend fun setMediaNotificationButtonIds(ids: List<String>) {
        context.dataStore.edit {
            it[KEY_MEDIA_NOTIFICATION_BUTTONS] = normalizeMediaNotificationButtonIds(
                ids.joinToString(",")
            ).joinToString(",")
        }
    }

    override suspend fun setLiveUpdateLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LIVE_UPDATE_LYRIC_ENABLED] = enabled }
    }

    override suspend fun setLiveUpdateLyricMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_LIVE_UPDATE_LYRIC_MODE] = mode.coerceIn(
                LIVE_UPDATE_LYRIC_MODE_ORIGINAL,
                LIVE_UPDATE_LYRIC_MODE_PRONUNCIATION
            )
        }
    }

    override suspend fun setLiveUpdateLyricDisplayMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_LIVE_UPDATE_LYRIC_DISPLAY_MODE] = mode.coerceIn(
                LIVE_UPDATE_LYRIC_DISPLAY_MODE_COMPACT,
                LIVE_UPDATE_LYRIC_DISPLAY_MODE_FULL
            )
        }
    }

    override suspend fun setLiveUpdateLyricSecondaryMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_LIVE_UPDATE_LYRIC_SECONDARY_MODE] = mode.coerceIn(
                LIVE_UPDATE_LYRIC_SECONDARY_MODE_SONG,
                LIVE_UPDATE_LYRIC_SECONDARY_MODE_PRONUNCIATION
            )
        }
    }

    override suspend fun setXiaomiSuperIslandLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_XIAOMI_SUPER_ISLAND_LYRIC_ENABLED] = enabled }
    }

    override suspend fun setXiaomiSuperIslandSettings(settings: XiaomiSuperIslandSettings) {
        context.dataStore.edit { it[KEY_XIAOMI_SUPER_ISLAND_SETTINGS] = settings.sanitized().encode() }
    }

    override suspend fun setSamsungFloatingLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION] = enabled }
    }

    override suspend fun setStatusBarAllowPhonetic(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STATUS_BAR_ALLOW_PHONETIC] = enabled }
    }

    override suspend fun setSuperLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPER_LYRIC_ENABLED] = enabled }
    }

    override suspend fun setSuperLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPER_LYRIC_TRANSLATION] = enabled }
    }

    override suspend fun setSuperLyricPronunciation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPER_LYRIC_PRONUNCIATION] = enabled }
    }

    override suspend fun setLyricGetterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_GETTER_ENABLED] = enabled }
    }

    override suspend fun setBluetoothLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_LYRIC_ENABLED] = enabled }
    }

    override suspend fun setBluetoothLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_LYRIC_TRANSLATION] = enabled }
    }

    override suspend fun setBluetoothLyricPronunciation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_LYRIC_PRONUNCIATION] = enabled }
    }

    override suspend fun setColorOsLockScreenLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_COLOROS_LOCK_SCREEN_LYRIC_ENABLED] = enabled }
    }

    override suspend fun setColorOsLockScreenLyricMode(mode: Int) {
        context.dataStore.edit { it[KEY_COLOROS_LOCK_SCREEN_LYRIC_MODE] = mode.coerceInOplusLyricMode() }
    }
}
