package com.ella.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import com.ella.music.data.SettingsManager.Companion.APP_LANGUAGE_DE
import com.ella.music.data.SettingsManager.Companion.APP_LANGUAGE_EN
import com.ella.music.data.SettingsManager.Companion.APP_LANGUAGE_FR
import com.ella.music.data.SettingsManager.Companion.APP_LANGUAGE_JA
import com.ella.music.data.SettingsManager.Companion.APP_LANGUAGE_KO
import com.ella.music.data.SettingsManager.Companion.APP_LANGUAGE_RU
import com.ella.music.data.SettingsManager.Companion.APP_LANGUAGE_SYSTEM
import com.ella.music.data.SettingsManager.Companion.APP_LANGUAGE_ZH_CN
import com.ella.music.data.SettingsManager.Companion.APP_LANGUAGE_ZH_TW
import com.ella.music.data.SettingsManager.Companion.DEFAULT_APP_SHORTCUT_ORDER
import com.ella.music.data.SettingsManager.Companion.DEFAULT_BOTTOM_DOCK_ITEMS
import com.ella.music.data.SettingsManager.Companion.DEFAULT_HOME_LIBRARY_TILE_ORDER
import com.ella.music.data.SettingsManager.Companion.DEFAULT_HOME_ONLINE_TILE_ORDER
import com.ella.music.data.SettingsManager.Companion.DEFAULT_HOME_SECTION_ORDER
import com.ella.music.data.SettingsManager.Companion.HOME_RECENT_SECTION_MODE_ADDED
import com.ella.music.data.SettingsManager.Companion.HOME_RECENT_SECTION_MODE_PLAYED
import com.ella.music.data.SettingsManager.Companion.DEFAULT_SHORTCUT_FOLDER_LABEL
import com.ella.music.data.SettingsManager.Companion.DEFAULT_SHORTCUT_LIBRARY_LABEL
import com.ella.music.data.SettingsManager.Companion.DEFAULT_SHORTCUT_PLAYLISTS_LABEL
import com.ella.music.data.SettingsManager.Companion.DEFAULT_STARTUP_POSTER_DURATION_MS
import com.ella.music.data.SettingsManager.Companion.normalizeAppShortcutOrder
import com.ella.music.data.SettingsManager.Companion.normalizeBottomDockItems
import com.ella.music.data.SettingsManager.Companion.STARTUP_POSTER_DURATION_MAX_MS
import com.ella.music.data.SettingsManager.Companion.STARTUP_POSTER_DURATION_MIN_MS
import com.ella.music.data.SettingsManager.Companion.KEY_APP_ICON_STYLE
import com.ella.music.data.SettingsManager.Companion.KEY_APP_FONT_SCALE_PERCENT
import com.ella.music.data.SettingsManager.Companion.KEY_APP_DISPLAY_SCALE_PERCENT
import com.ella.music.data.SettingsManager.Companion.KEY_APP_LANGUAGE
import com.ella.music.data.SettingsManager.Companion.KEY_APP_SHORTCUT_ORDER
import com.ella.music.data.SettingsManager.Companion.KEY_WIDGET_SAFE_LAYOUT
import com.ella.music.data.SettingsManager.Companion.KEY_APP_WALLPAPER_CONTENT_OVERLAY
import com.ella.music.data.SettingsManager.Companion.KEY_APP_WALLPAPER_DIM
import com.ella.music.data.SettingsManager.Companion.KEY_APP_WALLPAPER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_APP_WALLPAPER_OPACITY
import com.ella.music.data.SettingsManager.Companion.KEY_APP_WALLPAPER_URI
import com.ella.music.data.SettingsManager.Companion.KEY_ARTIST_COVER_CAROUSEL
import com.ella.music.data.SettingsManager.Companion.KEY_ARTIST_COVER_FOLDER_URI
import com.ella.music.data.SettingsManager.Companion.KEY_BOTTOM_BAR_GLASS_EFFECT
import com.ella.music.data.SettingsManager.Companion.KEY_BOTTOM_DOCK_ITEMS
import com.ella.music.data.SettingsManager.Companion.KEY_HI_RES_LOGO_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_HI_RES_LOGO_URI
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_AI_MIX_VISIBLE
import com.ella.music.data.SettingsManager.Companion.KEY_CONTINUE_PLAYBACK_ROW_VISIBLE
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_CARD_COLOR
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_CARD_OPACITY
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_DAILY_MIX_VISIBLE
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_HIDDEN_LIBRARY_TILES
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_HIDDEN_ONLINE_TILES
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_HIDDEN_SECTIONS
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_LIBRARY_TILE_ORDER
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_ONLINE_TILE_ORDER
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_SECTION_ORDER
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_RECENT_SECTION_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_TILE_COLORS
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_TILE_GRADIENT_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_TILE_GRADIENT_START_COLOR
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_TILE_PIN_BUTTONS_VISIBLE
import com.ella.music.data.SettingsManager.Companion.KEY_MONET_COLOR_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_SHORTCUT_FOLDER_LABEL
import com.ella.music.data.SettingsManager.Companion.KEY_SHORTCUT_LIBRARY_LABEL
import com.ella.music.data.SettingsManager.Companion.KEY_SHORTCUT_PLAYLISTS_LABEL
import com.ella.music.data.SettingsManager.Companion.KEY_STARTUP_POSTER_DURATION_MS
import com.ella.music.data.SettingsManager.Companion.KEY_STARTUP_POSTER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_STARTUP_POSTER_URI
import com.ella.music.data.SettingsManager.Companion.KEY_THEME_MODE
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * App-wide appearance and home customisation: theme, language, icon style, bottom dock,
 * startup poster, wallpaper, home cards/tiles/sections, Hi-Res logo, launcher shortcuts and artist covers.
 *
 * Extracted verbatim from [SettingsManager], which implements this interface via class
 * delegation so every call site keeps using settingsManager.<member> unchanged. All flow
 * properties MUST stay eagerly-initialised stored properties (never computed get() =):
 * Compose collectAsState keys on the flow instance, and a fresh instance per access would
 * restart collection on every recomposition.
 */
interface AppearanceSettingsAccess {
    val themeMode: Flow<Int>
    val monetColorMode: Flow<Int>
    val appLanguage: Flow<String>
    val appFontScalePercent: Flow<Int>
    val appDisplayScalePercent: Flow<Int>
    val appIconStyle: Flow<String>
    val widgetSafeLayout: Flow<Boolean>
    val bottomBarGlassEffect: Flow<BottomBarGlassEffect>
    val bottomDockItems: Flow<List<String>>
    val artistCoverFolderUri: Flow<String>
    val artistCoverCarousel: Flow<Boolean>
    val startupPosterEnabled: Flow<Boolean>
    val startupPosterUri: Flow<String>
    val startupPosterDurationMs: Flow<Int>
    val appWallpaperEnabled: Flow<Boolean>
    val appWallpaperUri: Flow<String>
    val appWallpaperOpacity: Flow<Int>
    val appWallpaperDim: Flow<Int>
    val appWallpaperContentOverlay: Flow<Int>
    val homeCardColor: Flow<String>
    val homeCardOpacity: Flow<Int>
    val homeTileColors: Flow<String>
    val homeTileGradientEnabled: Flow<Boolean>
    val homeTileGradientStartColor: Flow<String>
    val hiResLogoEnabled: Flow<Boolean>
    val hiResLogoUri: Flow<String>
    val shortcutLibraryLabel: Flow<String>
    val shortcutPlaylistsLabel: Flow<String>
    val shortcutFolderLabel: Flow<String>
    val appShortcutOrder: Flow<List<String>>
    val homeDailyMixVisible: Flow<Boolean>
    val homeAiMixVisible: Flow<Boolean>
    val continuePlaybackRowVisible: Flow<Boolean>
    val homeRecentSectionMode: Flow<Int>
    val homeSectionOrder: Flow<String>
    val homeHiddenSections: Flow<String>
    val homeLibraryTileOrder: Flow<String>
    val homeHiddenLibraryTiles: Flow<String>
    val homeOnlineTileOrder: Flow<String>
    val homeHiddenOnlineTiles: Flow<String>
    val homeTilePinButtonsVisible: Flow<Boolean>
    suspend fun setThemeMode(mode: Int)
    suspend fun setMonetColorMode(mode: Int)
    suspend fun setAppLanguage(languageTag: String)
    suspend fun setAppFontScalePercent(percent: Int)
    suspend fun setAppDisplayScalePercent(percent: Int)
    suspend fun setAppIconStyle(style: String)
    suspend fun setWidgetSafeLayout(enabled: Boolean)
    suspend fun setBottomBarGlassEffect(effect: BottomBarGlassEffect)
    suspend fun setBottomDockItems(items: List<String>)
    suspend fun setArtistCoverCarousel(carousel: Boolean)
    suspend fun setArtistCoverFolderUri(uri: String)
    suspend fun setStartupPosterEnabled(enabled: Boolean)
    suspend fun setStartupPosterUri(uri: String)
    suspend fun setStartupPosterDurationMs(durationMs: Int)
    suspend fun setAppWallpaperEnabled(enabled: Boolean)
    suspend fun setAppWallpaperUri(uri: String)
    suspend fun setAppWallpaperOpacity(opacity: Int)
    suspend fun setAppWallpaperDim(dim: Int)
    suspend fun setAppWallpaperContentOverlay(strength: Int)
    suspend fun setHomeCardColor(color: String)
    suspend fun setHomeCardOpacity(opacity: Int)
    suspend fun setHomeTileColor(tileId: String, color: String)
    suspend fun setHomeTileGradientEnabled(enabled: Boolean)
    suspend fun setHomeTileGradientStartColor(color: String)
    suspend fun setHiResLogoEnabled(enabled: Boolean)
    suspend fun setHiResLogoUri(uri: String)
    suspend fun setShortcutLibraryLabel(label: String)
    suspend fun setShortcutPlaylistsLabel(label: String)
    suspend fun setShortcutFolderLabel(label: String)
    suspend fun setAppShortcutOrder(shortcutIds: List<String>)
    suspend fun setHomeDailyMixVisible(visible: Boolean)
    suspend fun setHomeAiMixVisible(visible: Boolean)
    suspend fun setContinuePlaybackRowVisible(visible: Boolean)
    suspend fun setHomeRecentSectionMode(mode: Int)
    suspend fun setHomeSectionOrder(order: String)
    suspend fun setHomeHiddenSections(hidden: String)
    suspend fun setHomeLibraryTileOrder(order: String)
    suspend fun setHomeHiddenLibraryTiles(hidden: String)
    suspend fun setHomeOnlineTileOrder(order: String)
    suspend fun setHomeHiddenOnlineTiles(hidden: String)
    suspend fun setHomeTilePinButtonsVisible(visible: Boolean)
}

internal class AppearanceSettingsAccessImpl(private val context: Context) : AppearanceSettingsAccess {

    override val themeMode: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: 0 }
    override val monetColorMode: Flow<Int> = context.dataStore.data.map { it[KEY_MONET_COLOR_MODE] ?: 0 }

    override val appLanguage: Flow<String> =
        context.dataStore.data.map { it[KEY_APP_LANGUAGE] ?: APP_LANGUAGE_SYSTEM }
    override val appFontScalePercent: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_APP_FONT_SCALE_PERCENT]
                ?: SettingsManager.DEFAULT_APP_FONT_SCALE_PERCENT).coerceIn(
                SettingsManager.APP_FONT_SCALE_MIN_PERCENT,
                SettingsManager.APP_FONT_SCALE_MAX_PERCENT
            )
        }
    override val appDisplayScalePercent: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_APP_DISPLAY_SCALE_PERCENT]
                ?: SettingsManager.DEFAULT_APP_DISPLAY_SCALE_PERCENT).coerceIn(
                SettingsManager.APP_DISPLAY_SCALE_MIN_PERCENT,
                SettingsManager.APP_DISPLAY_SCALE_MAX_PERCENT
            )
        }
    override val appIconStyle: Flow<String> =
        context.dataStore.data.map { AppIconManager.normalize(it[KEY_APP_ICON_STYLE]) }
    override val widgetSafeLayout: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_WIDGET_SAFE_LAYOUT] ?: false }
    override val bottomBarGlassEffect: Flow<BottomBarGlassEffect> = context.dataStore.data.map { preferences ->
        runCatching {
            BottomBarGlassEffect.valueOf(
                preferences[KEY_BOTTOM_BAR_GLASS_EFFECT] ?: BottomBarGlassEffect.LiquidGlass.name
            )
        }.getOrDefault(BottomBarGlassEffect.LiquidGlass)
    }
    override val bottomDockItems: Flow<List<String>> =
        context.dataStore.data.map {
            normalizeBottomDockItems(it[KEY_BOTTOM_DOCK_ITEMS] ?: DEFAULT_BOTTOM_DOCK_ITEMS)
                .split(',')
                .filter(String::isNotBlank)
        }

    override val artistCoverFolderUri: Flow<String> =
        context.dataStore.data.map { it[KEY_ARTIST_COVER_FOLDER_URI].orEmpty() }
    // 当某位艺术家有多张封面图时：true=多图轮播，false=随机取一张。
    override val artistCoverCarousel: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ARTIST_COVER_CAROUSEL] ?: true }

    override val startupPosterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_STARTUP_POSTER_ENABLED] ?: false }
    override val startupPosterUri: Flow<String> =
        context.dataStore.data.map { it[KEY_STARTUP_POSTER_URI] ?: "" }
    override val startupPosterDurationMs: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_STARTUP_POSTER_DURATION_MS] ?: DEFAULT_STARTUP_POSTER_DURATION_MS)
                .coerceIn(STARTUP_POSTER_DURATION_MIN_MS, STARTUP_POSTER_DURATION_MAX_MS)
        }
    override val appWallpaperEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_APP_WALLPAPER_ENABLED] ?: false }
    override val appWallpaperUri: Flow<String> =
        context.dataStore.data.map { it[KEY_APP_WALLPAPER_URI] ?: "" }
    override val appWallpaperOpacity: Flow<Int> =
        context.dataStore.data.map { it[KEY_APP_WALLPAPER_OPACITY]?.coerceIn(20, 100) ?: 100 }
    override val appWallpaperDim: Flow<Int> =
        context.dataStore.data.map { it[KEY_APP_WALLPAPER_DIM]?.coerceIn(0, 80) ?: 30 }
    override val appWallpaperContentOverlay: Flow<Int> =
        context.dataStore.data.map { it[KEY_APP_WALLPAPER_CONTENT_OVERLAY]?.coerceIn(0, 80) ?: 24 }

    override val homeCardColor: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_CARD_COLOR] ?: "" }
    override val homeCardOpacity: Flow<Int> =
        context.dataStore.data.map { it[KEY_HOME_CARD_OPACITY]?.coerceIn(20, 100) ?: 58 }
    override val homeTileColors: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_TILE_COLORS] ?: "" }
    override val homeTileGradientEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HOME_TILE_GRADIENT_ENABLED] ?: false }
    override val homeTileGradientStartColor: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_TILE_GRADIENT_START_COLOR] ?: "" }
    override val hiResLogoEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HI_RES_LOGO_ENABLED] ?: false }
    override val hiResLogoUri: Flow<String> =
        context.dataStore.data.map { it[KEY_HI_RES_LOGO_URI] ?: "" }

    override val shortcutLibraryLabel: Flow<String> =
        context.dataStore.data.map { it[KEY_SHORTCUT_LIBRARY_LABEL] ?: DEFAULT_SHORTCUT_LIBRARY_LABEL }
    override val shortcutPlaylistsLabel: Flow<String> =
        context.dataStore.data.map { it[KEY_SHORTCUT_PLAYLISTS_LABEL] ?: DEFAULT_SHORTCUT_PLAYLISTS_LABEL }
    override val shortcutFolderLabel: Flow<String> =
        context.dataStore.data.map { it[KEY_SHORTCUT_FOLDER_LABEL] ?: DEFAULT_SHORTCUT_FOLDER_LABEL }
    override val appShortcutOrder: Flow<List<String>> =
        context.dataStore.data.map { preferences ->
            preferences[KEY_APP_SHORTCUT_ORDER]
                ?.let(::normalizeAppShortcutOrder)
                ?: DEFAULT_APP_SHORTCUT_ORDER
        }

    override val homeDailyMixVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HOME_DAILY_MIX_VISIBLE] ?: true }
    override val homeAiMixVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HOME_AI_MIX_VISIBLE] ?: true }
    override val continuePlaybackRowVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_CONTINUE_PLAYBACK_ROW_VISIBLE] ?: true }
    override val homeRecentSectionMode: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_HOME_RECENT_SECTION_MODE] ?: HOME_RECENT_SECTION_MODE_ADDED)
                .coerceIn(HOME_RECENT_SECTION_MODE_PLAYED, HOME_RECENT_SECTION_MODE_ADDED)
        }
    override val homeSectionOrder: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_SECTION_ORDER] ?: DEFAULT_HOME_SECTION_ORDER }
    override val homeHiddenSections: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_HIDDEN_SECTIONS] ?: "" }
    override val homeLibraryTileOrder: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_LIBRARY_TILE_ORDER] ?: DEFAULT_HOME_LIBRARY_TILE_ORDER }
    override val homeHiddenLibraryTiles: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_HIDDEN_LIBRARY_TILES] ?: "" }
    override val homeOnlineTileOrder: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_ONLINE_TILE_ORDER] ?: DEFAULT_HOME_ONLINE_TILE_ORDER }
    override val homeHiddenOnlineTiles: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_HIDDEN_ONLINE_TILES] ?: "" }
    override val homeTilePinButtonsVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HOME_TILE_PIN_BUTTONS_VISIBLE] ?: false }

    override suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    override suspend fun setMonetColorMode(mode: Int) {
        context.dataStore.edit { it[KEY_MONET_COLOR_MODE] = mode }
    }

    override suspend fun setAppLanguage(languageTag: String) {
        val normalized = when (languageTag) {
            APP_LANGUAGE_ZH_CN -> APP_LANGUAGE_ZH_CN
            APP_LANGUAGE_ZH_TW -> APP_LANGUAGE_ZH_TW
            APP_LANGUAGE_EN -> APP_LANGUAGE_EN
            APP_LANGUAGE_JA -> APP_LANGUAGE_JA
            APP_LANGUAGE_KO -> APP_LANGUAGE_KO
            APP_LANGUAGE_DE -> APP_LANGUAGE_DE
            APP_LANGUAGE_FR -> APP_LANGUAGE_FR
            APP_LANGUAGE_RU -> APP_LANGUAGE_RU
            else -> APP_LANGUAGE_SYSTEM
        }
        context.dataStore.edit { it[KEY_APP_LANGUAGE] = normalized }
    }

    override suspend fun setAppFontScalePercent(percent: Int) {
        context.dataStore.edit {
            it[KEY_APP_FONT_SCALE_PERCENT] = percent.coerceIn(
                SettingsManager.APP_FONT_SCALE_MIN_PERCENT,
                SettingsManager.APP_FONT_SCALE_MAX_PERCENT
            )
        }
    }

    override suspend fun setAppDisplayScalePercent(percent: Int) {
        context.dataStore.edit {
            it[KEY_APP_DISPLAY_SCALE_PERCENT] = percent.coerceIn(
                SettingsManager.APP_DISPLAY_SCALE_MIN_PERCENT,
                SettingsManager.APP_DISPLAY_SCALE_MAX_PERCENT
            )
        }
    }

    override suspend fun setAppIconStyle(style: String) {
        context.dataStore.edit { it[KEY_APP_ICON_STYLE] = AppIconManager.normalize(style) }
    }

    override suspend fun setWidgetSafeLayout(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WIDGET_SAFE_LAYOUT] = enabled }
    }

    override suspend fun setBottomBarGlassEffect(effect: BottomBarGlassEffect) {
        context.dataStore.edit { it[KEY_BOTTOM_BAR_GLASS_EFFECT] = effect.name }
    }

    override suspend fun setBottomDockItems(items: List<String>) {
        context.dataStore.edit {
            it[KEY_BOTTOM_DOCK_ITEMS] = normalizeBottomDockItems(items.joinToString(","))
        }
    }

    override suspend fun setArtistCoverCarousel(carousel: Boolean) {
        context.dataStore.edit { it[KEY_ARTIST_COVER_CAROUSEL] = carousel }
    }

    override suspend fun setArtistCoverFolderUri(uri: String) {
        context.dataStore.edit { prefs ->
            val safeUri = uri.trim()
            if (safeUri.isBlank()) {
                prefs.remove(KEY_ARTIST_COVER_FOLDER_URI)
            } else {
                prefs[KEY_ARTIST_COVER_FOLDER_URI] = safeUri
            }
        }
    }

    override suspend fun setStartupPosterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STARTUP_POSTER_ENABLED] = enabled }
    }

    override suspend fun setStartupPosterUri(uri: String) {
        context.dataStore.edit {
            val safeUri = uri.trim()
            if (safeUri.isBlank()) it.remove(KEY_STARTUP_POSTER_URI) else it[KEY_STARTUP_POSTER_URI] = safeUri
        }
    }

    override suspend fun setStartupPosterDurationMs(durationMs: Int) {
        context.dataStore.edit {
            it[KEY_STARTUP_POSTER_DURATION_MS] = durationMs.coerceIn(
                STARTUP_POSTER_DURATION_MIN_MS,
                STARTUP_POSTER_DURATION_MAX_MS
            )
        }
    }

    override suspend fun setAppWallpaperEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APP_WALLPAPER_ENABLED] = enabled }
    }

    override suspend fun setAppWallpaperUri(uri: String) {
        context.dataStore.edit {
            val safeUri = uri.trim()
            if (safeUri.isBlank()) it.remove(KEY_APP_WALLPAPER_URI) else it[KEY_APP_WALLPAPER_URI] = safeUri
        }
    }

    override suspend fun setAppWallpaperOpacity(opacity: Int) {
        context.dataStore.edit { it[KEY_APP_WALLPAPER_OPACITY] = opacity.coerceIn(20, 100) }
    }

    override suspend fun setAppWallpaperDim(dim: Int) {
        context.dataStore.edit { it[KEY_APP_WALLPAPER_DIM] = dim.coerceIn(0, 80) }
    }

    override suspend fun setAppWallpaperContentOverlay(strength: Int) {
        context.dataStore.edit { it[KEY_APP_WALLPAPER_CONTENT_OVERLAY] = strength.coerceIn(0, 80) }
    }

    override suspend fun setHomeCardColor(color: String) {
        context.dataStore.edit {
            val safeColor = color.trim()
            if (safeColor.isBlank()) it.remove(KEY_HOME_CARD_COLOR) else it[KEY_HOME_CARD_COLOR] = safeColor
        }
    }

    override suspend fun setHomeCardOpacity(opacity: Int) {
        context.dataStore.edit { it[KEY_HOME_CARD_OPACITY] = opacity.coerceIn(20, 100) }
    }

    override suspend fun setHomeTileColor(tileId: String, color: String) {
        val safeId = tileId.trim().lowercase(Locale.ROOT).takeIf { it.matches(Regex("""[a-z0-9_]+""")) } ?: return
        val safeColor = color.trim().takeIf { it.isBlank() || it.matches(Regex("""#[0-9A-Fa-f]{8}""")) } ?: return
        context.dataStore.edit { prefs ->
            val json = runCatching { JSONObject(prefs[KEY_HOME_TILE_COLORS].orEmpty()) }.getOrElse { JSONObject() }
            if (safeColor.isBlank()) json.remove(safeId) else json.put(safeId, safeColor.uppercase(Locale.ROOT))
            if (json.length() == 0) prefs.remove(KEY_HOME_TILE_COLORS) else prefs[KEY_HOME_TILE_COLORS] = json.toString()
        }
    }

    override suspend fun setHomeTileGradientEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HOME_TILE_GRADIENT_ENABLED] = enabled }
    }

    override suspend fun setHomeTileGradientStartColor(color: String) {
        context.dataStore.edit {
            val safeColor = color.trim()
            if (safeColor.isBlank()) it.remove(KEY_HOME_TILE_GRADIENT_START_COLOR) else it[KEY_HOME_TILE_GRADIENT_START_COLOR] = safeColor
        }
    }

    override suspend fun setHiResLogoEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HI_RES_LOGO_ENABLED] = enabled }
    }

    override suspend fun setHiResLogoUri(uri: String) {
        context.dataStore.edit {
            val safeUri = uri.trim()
            if (safeUri.isBlank()) it.remove(KEY_HI_RES_LOGO_URI) else it[KEY_HI_RES_LOGO_URI] = safeUri
        }
    }

    override suspend fun setShortcutLibraryLabel(label: String) {
        setShortcutLabel(KEY_SHORTCUT_LIBRARY_LABEL, label, DEFAULT_SHORTCUT_LIBRARY_LABEL)
    }

    override suspend fun setShortcutPlaylistsLabel(label: String) {
        setShortcutLabel(KEY_SHORTCUT_PLAYLISTS_LABEL, label, DEFAULT_SHORTCUT_PLAYLISTS_LABEL)
    }

    override suspend fun setShortcutFolderLabel(label: String) {
        setShortcutLabel(KEY_SHORTCUT_FOLDER_LABEL, label, DEFAULT_SHORTCUT_FOLDER_LABEL)
    }

    override suspend fun setAppShortcutOrder(shortcutIds: List<String>) {
        context.dataStore.edit {
            // Persist an explicit empty value too: users may deliberately choose no dynamic
            // shortcuts, while a missing preference means "keep the four legacy defaults".
            it[KEY_APP_SHORTCUT_ORDER] = normalizeAppShortcutOrder(shortcutIds.joinToString(","))
                .joinToString(",")
        }
    }

    private suspend fun setShortcutLabel(
        key: Preferences.Key<String>,
        label: String,
        defaultLabel: String
    ) {
        context.dataStore.edit {
            val safeLabel = label.trim().take(24)
            if (safeLabel.isBlank() || safeLabel == defaultLabel) it.remove(key) else it[key] = safeLabel
        }
    }

    override suspend fun setHomeDailyMixVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_HOME_DAILY_MIX_VISIBLE] = visible }
    }

    override suspend fun setHomeAiMixVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_HOME_AI_MIX_VISIBLE] = visible }
    }

    override suspend fun setContinuePlaybackRowVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_CONTINUE_PLAYBACK_ROW_VISIBLE] = visible }
    }

    override suspend fun setHomeRecentSectionMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_HOME_RECENT_SECTION_MODE] = mode.coerceIn(
                HOME_RECENT_SECTION_MODE_PLAYED,
                HOME_RECENT_SECTION_MODE_ADDED
            )
        }
    }

    override suspend fun setHomeSectionOrder(order: String) {
        context.dataStore.edit { it[KEY_HOME_SECTION_ORDER] = order.trim() }
    }

    override suspend fun setHomeHiddenSections(hidden: String) {
        context.dataStore.edit { it[KEY_HOME_HIDDEN_SECTIONS] = hidden.trim() }
    }

    override suspend fun setHomeLibraryTileOrder(order: String) {
        context.dataStore.edit { it[KEY_HOME_LIBRARY_TILE_ORDER] = order.trim() }
    }

    override suspend fun setHomeHiddenLibraryTiles(hidden: String) {
        context.dataStore.edit { it[KEY_HOME_HIDDEN_LIBRARY_TILES] = hidden.trim() }
    }

    override suspend fun setHomeOnlineTileOrder(order: String) {
        context.dataStore.edit { it[KEY_HOME_ONLINE_TILE_ORDER] = order.trim() }
    }

    override suspend fun setHomeHiddenOnlineTiles(hidden: String) {
        context.dataStore.edit { it[KEY_HOME_HIDDEN_ONLINE_TILES] = hidden.trim() }
    }

    override suspend fun setHomeTilePinButtonsVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_HOME_TILE_PIN_BUTTONS_VISIBLE] = visible }
    }
}
