package com.ella.music.ui.player

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.ScriptFontPaths

internal data class PlayerLyricFontState(
    val originalFontFamily: FontFamily?,
    val translationFontFamily: FontFamily?,
    val originalFontPath: String,
    val translationFontPath: String,
    val fontWeight: FontWeight,
    val fontScale: Float,
    val secondaryFontScale: Float,
    val compactPrimaryTextSizeSp: Float,
    val compactSecondaryTextSizeSp: Float,
    val widePrimaryTextSizeSp: Float,
    val wideSecondaryTextSizeSp: Float,
    val shareTypeface: Typeface?
)

@Composable
internal fun rememberPlayerLyricFontState(
    context: Context,
    settingsManager: SettingsManager
): PlayerLyricFontState {
    // The former Western/CJK pair is the migration source.  New installs use the bundled
    // defaults, while existing users keep their lyric choice for both original and translation.
    val legacyFontPath by settingsManager.lyricFontPath.collectAsState(initial = "")
    val legacyWesternPath by settingsManager.lyricWesternFontPath.collectAsState(initial = "")
    val legacyCjkPath by settingsManager.lyricCjkFontPath.collectAsState(initial = "")
    val originalWesternPath by settingsManager.lyricOriginalWesternFontPath.collectAsState(initial = "")
    val originalCjkPath by settingsManager.lyricOriginalCjkFontPath.collectAsState(initial = "")
    val translationWesternPath by settingsManager.lyricTranslationWesternFontPath.collectAsState(initial = "")
    val translationCjkPath by settingsManager.lyricTranslationCjkFontPath.collectAsState(initial = "")
    val lyricFontWeightValue by settingsManager.lyricFontWeight.collectAsState(initial = 800)
    val lyricFontScaleValue by settingsManager.lyricFontScale.collectAsState(initial = 100)
    val lyricSecondaryFontScaleValue by settingsManager.lyricSecondaryFontScale.collectAsState(initial = 100)
    val lyricCompactPrimaryTextSizeValue by settingsManager.lyricCompactPrimaryTextSize.collectAsState(initial = SettingsManager.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_DEFAULT_SP)
    val lyricCompactSecondaryTextSizeValue by settingsManager.lyricCompactSecondaryTextSize.collectAsState(initial = SettingsManager.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_DEFAULT_SP)
    val lyricWidePrimaryTextSizeValue by settingsManager.lyricWidePrimaryTextSize.collectAsState(initial = SettingsManager.LYRIC_WIDE_PRIMARY_TEXT_SIZE_DEFAULT_SP)
    val lyricWideSecondaryTextSizeValue by settingsManager.lyricWideSecondaryTextSize.collectAsState(initial = SettingsManager.LYRIC_WIDE_SECONDARY_TEXT_SIZE_DEFAULT_SP)
    val lyricShareUseLyricFont by settingsManager.lyricShareUseLyricFont.collectAsState(initial = true)
    val lyricFontApplyToPage by settingsManager.lyricFontApplyToPage.collectAsState(initial = true)
    val bundledInterPath = remember(context) { ensureBundledInterPath(context) }
    val bundledCjkPath = remember(context) { ensureBundledMiSansBoldPath(context) }
    val migratedLegacyCjkPath = remember(legacyFontPath) {
        legacyFontPath.takeUnless { it.contains("Inter", ignoreCase = true) }.orEmpty()
    }
    val legacyWestern = legacyWesternPath.ifBlank { bundledInterPath }
    val legacyCjk = legacyCjkPath.ifBlank { migratedLegacyCjkPath.ifBlank { bundledCjkPath } }
    val effectiveOriginalPath = remember(originalWesternPath, originalCjkPath, legacyWestern, legacyCjk) {
        ScriptFontPaths(originalWesternPath.ifBlank { legacyWestern }, originalCjkPath.ifBlank { legacyCjk }).encode()
    }
    val effectiveTranslationPath = remember(translationWesternPath, translationCjkPath, legacyWestern, legacyCjk) {
        ScriptFontPaths(translationWesternPath.ifBlank { legacyWestern }, translationCjkPath.ifBlank { legacyCjk }).encode()
    }
    val originalFamily = remember(effectiveOriginalPath, lyricFontWeightValue) {
        effectiveOriginalPath.toPlayerLyricFontFamily(lyricFontWeightValue, italic = false)
    }
    val translationFamily = remember(effectiveTranslationPath, lyricFontWeightValue) {
        effectiveTranslationPath.toPlayerLyricFontFamily(lyricFontWeightValue, italic = false)
    }
    val fontWeight = remember(lyricFontWeightValue) { FontWeight(lyricFontWeightValue.coerceIn(100, 900)) }
    val fontScale = remember(lyricFontScaleValue) {
        lyricFontScaleValue.coerceIn(SettingsManager.LYRIC_FONT_SCALE_MIN, SettingsManager.LYRIC_FONT_SCALE_ULTRA_WIDE_MAX) / 100f
    }
    val secondaryFontScale = remember(lyricSecondaryFontScaleValue) {
        lyricSecondaryFontScaleValue.coerceIn(SettingsManager.LYRIC_SECONDARY_FONT_SCALE_MIN, SettingsManager.LYRIC_SECONDARY_FONT_SCALE_ULTRA_WIDE_MAX) / 100f
    }
    val shareTypeface = remember(lyricShareUseLyricFont, effectiveOriginalPath, lyricFontWeightValue) {
        if (lyricShareUseLyricFont) effectiveOriginalPath.toPlayerLyricTypeface(lyricFontWeightValue) else null
    }
    return PlayerLyricFontState(
        originalFontFamily = if (lyricFontApplyToPage) originalFamily else null,
        translationFontFamily = if (lyricFontApplyToPage) translationFamily else null,
        originalFontPath = if (lyricFontApplyToPage) effectiveOriginalPath else "",
        translationFontPath = if (lyricFontApplyToPage) effectiveTranslationPath else "",
        fontWeight = fontWeight,
        fontScale = fontScale,
        secondaryFontScale = secondaryFontScale,
        compactPrimaryTextSizeSp = lyricCompactPrimaryTextSizeValue.coerceIn(SettingsManager.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MIN_SP, SettingsManager.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MAX_SP).toFloat(),
        compactSecondaryTextSizeSp = lyricCompactSecondaryTextSizeValue.coerceIn(SettingsManager.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MIN_SP, SettingsManager.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MAX_SP).toFloat(),
        widePrimaryTextSizeSp = lyricWidePrimaryTextSizeValue.coerceIn(SettingsManager.LYRIC_WIDE_PRIMARY_TEXT_SIZE_MIN_SP, SettingsManager.LYRIC_WIDE_PRIMARY_TEXT_SIZE_MAX_SP).toFloat(),
        wideSecondaryTextSizeSp = lyricWideSecondaryTextSizeValue.coerceIn(SettingsManager.LYRIC_WIDE_SECONDARY_TEXT_SIZE_MIN_SP, SettingsManager.LYRIC_WIDE_SECONDARY_TEXT_SIZE_MAX_SP).toFloat(),
        shareTypeface = shareTypeface
    )
}

internal fun PlayerLyricFontState.primaryTextSizeSp(profile: PlayerLyricLayoutProfile): Float =
    if (profile == PlayerLyricLayoutProfile.Wide) widePrimaryTextSizeSp else compactPrimaryTextSizeSp

internal fun PlayerLyricFontState.secondaryTextSizeSp(profile: PlayerLyricLayoutProfile): Float =
    if (profile == PlayerLyricLayoutProfile.Wide) wideSecondaryTextSizeSp else compactSecondaryTextSizeSp
