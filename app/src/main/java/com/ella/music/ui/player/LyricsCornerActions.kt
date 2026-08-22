package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

/**
 * Flamingo-style overlays on a lyrics surface: translation at the bottom-start, original /
 * accompaniment at the bottom-end.
 */
@Composable
internal fun LyricsCornerActions(
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val karaokeEnabled by settingsManager.karaokeAccompanimentEnabled.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val chipColor = contentColor.copy(alpha = 0.16f)
    val iconTint = contentColor.copy(alpha = 0.92f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        LyricsRoundChip(
            selected = showTranslation,
            background = chipColor,
            onClick = onToggleTranslation
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_translation),
                contentDescription = stringResource(R.string.player_show_translation),
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(chipColor.copy(alpha = if (karaokeEnabled) 0.36f else 0.16f))
                .clickable {
                    scope.launch { settingsManager.setKaraokeAccompanimentEnabled(!karaokeEnabled) }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_karaoke_mic),
                contentDescription = stringResource(R.string.player_accompaniment),
                tint = iconTint.copy(alpha = if (karaokeEnabled) 1f else 0.55f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun LyricsRoundChip(
    selected: Boolean,
    background: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background.copy(alpha = if (selected) 0.28f else 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
