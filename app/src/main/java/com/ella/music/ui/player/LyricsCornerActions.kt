package com.ella.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.launch

/**
 * Flamingo-style overlays on a lyrics surface: translation at the bottom-start, original /
 * accompaniment at the bottom-end.
 */
@Composable
internal fun LyricsCornerActions(
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
    packedEnd: Boolean = false
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val karaokeEnabled by settingsManager.karaokeAccompanimentEnabled.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    Row(
        modifier = if (packedEnd) modifier else modifier.fillMaxWidth(),
        horizontalArrangement = if (packedEnd) {
            Arrangement.spacedBy(10.dp)
        } else {
            Arrangement.SpaceBetween
        },
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleTranslation
                ),
            contentAlignment = Alignment.Center
        ) {
            AppleTranslationIcon(
                color = contentColor,
                active = showTranslation,
                modifier = Modifier.size(36.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        scope.launch { settingsManager.setKaraokeAccompanimentEnabled(!karaokeEnabled) }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AppleVocalIcon(
                color = contentColor,
                active = karaokeEnabled,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
