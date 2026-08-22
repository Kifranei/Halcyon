package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.components.EllaMiuixBottomSheet

@Composable
internal fun LyricsPlayerHeader(
    song: Song?,
    embeddedCover: Bitmap?,
    annotation: String,
    activeSinger: String?,
    isFavorite: Boolean,
    onDismissLyrics: () -> Unit,
    onArtist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowMenu: () -> Unit,
    fontFamily: FontFamily? = null,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtView(
            song = song,
            embeddedCover = embeddedCover,
            cornerRadius = 12.dp,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(56.dp)
                .then(coverModifier)
                .clickable(onClick = onDismissLyrics)
        )
        Spacer(modifier = Modifier.width(12.dp))
        PlayerSongMetaText(
            song = song,
            annotation = annotation,
            titleFontSize = 22.sp,
            artistFontSize = 14.sp,
            artistAlpha = 0.72f,
            showArtistWithAnnotation = true,
            artistOverride = activeSinger,
            artistIconRes = activeSinger?.takeIf { it.isNotBlank() }?.let { R.drawable.ic_ttml_singer },
            contentColor = LocalPlayerContentColor.current,
            fontFamily = fontFamily,
            onArtistClick = onArtist,
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 230.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        PlayerHeaderAction(
            kind = PlayerHeaderActionKind.Favorite,
            selected = isFavorite,
            onClick = onToggleFavorite
        )
        PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onShowMenu)
    }
}

@Composable
internal fun LyricsPlayerMenuSheet(
    show: Boolean,
    showPronunciation: Boolean,
    showTranslation: Boolean,
    keepScreenOn: Boolean,
    perspectiveEffect: Boolean,
    perspectiveYAngle: Int,
    lyricFormatAvailability: MusicRepository.LyricFormatAvailability,
    preferTtmlLyrics: Boolean?,
    lyricSourceMode: Int,
    lyricParserEngine: Int,
    layoutProfile: PlayerLyricLayoutProfile,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    onDismiss: () -> Unit,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onTogglePerspectiveEffect: () -> Unit,
    onPerspectiveYAngle: (Int) -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    onLyricFormatPreference: (Boolean) -> Unit,
    onLyricParserEngine: (Int) -> Unit,
    onFontScale: (Float) -> Unit,
    onSecondaryFontScale: (Float) -> Unit,
    onPrimaryTextSize: (Float) -> Unit,
    onSecondaryTextSize: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!show) return
    var page by remember(show) { mutableStateOf(LyricsPlayerMenuPage.Main) }
    LaunchedEffect(show) {
        if (show) page = LyricsPlayerMenuPage.Main
    }
    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = stringResource(
            if (page == LyricsPlayerMenuPage.Style) {
                R.string.player_lyric_style_settings
            } else {
                R.string.player_lyrics_display
            }
        ),
        onDismissRequest = onDismiss
    ) {
        when (page) {
            LyricsPlayerMenuPage.Main -> {
                LyricActionMenu(
                    showPronunciation = showPronunciation,
                    showTranslation = showTranslation,
                    keepScreenOn = keepScreenOn,
                    lyricFormatAvailability = lyricFormatAvailability,
                    preferTtmlLyrics = preferTtmlLyrics,
                    lyricSourceMode = lyricSourceMode,
                    lyricParserEngine = lyricParserEngine,
                    layoutProfile = layoutProfile,
                    fontScale = fontScale,
                    secondaryFontScale = secondaryFontScale,
                    primaryTextSizeSp = primaryTextSizeSp,
                    secondaryTextSizeSp = secondaryTextSizeSp,
                    perspectiveEffect = perspectiveEffect,
                    perspectiveYAngle = perspectiveYAngle,
                    onTogglePronunciation = onTogglePronunciation,
                    onToggleTranslation = onToggleTranslation,
                    onToggleKeepScreenOn = onToggleKeepScreenOn,
                    onTogglePerspectiveEffect = onTogglePerspectiveEffect,
                    onPerspectiveYAngle = onPerspectiveYAngle,
                    onLyricSourceMode = onLyricSourceMode,
                    onLyricFormatPreference = onLyricFormatPreference,
                    onLyricParserEngine = onLyricParserEngine,
                    onFontScale = onFontScale,
                    onSecondaryFontScale = onSecondaryFontScale,
                    onPrimaryTextSize = onPrimaryTextSize,
                    onSecondaryTextSize = onSecondaryTextSize,
                    onStyleSettings = { page = LyricsPlayerMenuPage.Style },
                    modifier = modifier
                )
            }
            LyricsPlayerMenuPage.Style -> {
                LyricStyleSettingsContent(
                    layoutProfile = layoutProfile,
                    fontScale = fontScale,
                    secondaryFontScale = secondaryFontScale,
                    primaryTextSizeSp = primaryTextSizeSp,
                    secondaryTextSizeSp = secondaryTextSizeSp,
                    perspectiveEffect = perspectiveEffect,
                    perspectiveYAngle = perspectiveYAngle,
                    onPerspectiveYAngle = onPerspectiveYAngle,
                    onFontScale = onFontScale,
                    onSecondaryFontScale = onSecondaryFontScale,
                    onPrimaryTextSize = onPrimaryTextSize,
                    onSecondaryTextSize = onSecondaryTextSize,
                    onBack = { page = LyricsPlayerMenuPage.Main },
                    applyScrollableContainer = true,
                    modifier = modifier
                )
            }
        }
    }
}

private enum class LyricsPlayerMenuPage {
    Main,
    Style
}
