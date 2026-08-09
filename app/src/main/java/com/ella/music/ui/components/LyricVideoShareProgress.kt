package com.ella.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LyricVideoShareProgressOverlay(
    visible: Boolean,
    progress: LyricVideoProgress?,
    onCancel: () -> Unit
) {
    EllaMiuixBottomSheet(
        show = visible,
        title = stringResource(R.string.lyric_video_share_generating),
        onDismissRequest = onCancel,
        enableNestedScroll = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VideoShareProgressBar(fraction = progress?.fraction ?: 0f)
            Spacer(modifier = Modifier.height(12.dp))
            top.yukonga.miuix.kmp.basic.Text(
                text = "${((progress?.fraction ?: 0f) * 100).toInt()}%"
            )
            Spacer(modifier = Modifier.height(18.dp))
            TextButton(
                text = stringResource(R.string.lyric_video_share_cancel),
                onClick = onCancel
            )
        }
    }
}

@Composable
private fun VideoShareProgressBar(fraction: Float) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 150),
        label = "progress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedFraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MiuixTheme.colorScheme.primary)
        )
    }
}
