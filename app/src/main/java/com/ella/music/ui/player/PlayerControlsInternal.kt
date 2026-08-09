package com.ella.music.ui.player

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import com.ella.music.R
import com.ella.music.data.AudioQualitySummary
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.oem.MiPlayAudioSupport
import top.yukonga.miuix.kmp.basic.Icon

@Composable
internal fun PlayerTransportIconButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .playerNoIndicationClick(onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
internal fun CenteredPlayPauseGlyph(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play_legacy),
        contentDescription = if (isPlaying) stringResource(R.string.common_pause) else stringResource(R.string.common_play),
        tint = tint,
        modifier = modifier
    )
}

@Composable
internal fun PlaybackModeIcon(
    shuffleEnabled: Boolean,
    repeatMode: Int,
    color: Color,
    modifier: Modifier = Modifier.size(34.dp)
) {
    val label = when {
        shuffleEnabled -> stringResource(R.string.player_playback_mode_shuffle)
        repeatMode == Player.REPEAT_MODE_ONE -> stringResource(R.string.player_playback_mode_repeat_one)
        repeatMode == Player.REPEAT_MODE_ALL -> stringResource(R.string.player_playback_mode_repeat_all)
        else -> stringResource(R.string.player_playback_mode_in_order)
    }
    Icon(
        painter = painterResource(
            id = when {
                shuffleEnabled -> R.drawable.ic_shuffle
                repeatMode == Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                repeatMode == Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                else -> R.drawable.ic_playback_order
            }
        ),
        contentDescription = label,
        tint = color,
        modifier = modifier
    )
}

@Composable
internal fun GlowSeekBar(
    value: Float,
    onSeek: (Float) -> Unit,
    accent: Color,
    allowTapSeek: Boolean,
    onPreviewProgressChange: (Float?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val safeProgress = value.coerceIn(0f, 1f)
    var draggingProgress by remember { mutableStateOf<Float?>(null) }
    val displayProgress = draggingProgress ?: safeProgress
    val glowArgb = LocalPlayerContentColor.current.toArgb()
    val trackArgb = LocalPlayerContentColor.current.copy(alpha = 0.19f).toArgb()

    fun progressAt(width: Float, x: Float): Float {
        return (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier.height(30.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                GlowGlowProgressBar(ctx).apply {
                    shaderMode = GlowGlowProgressBar.ShaderMode.HIGH_END
                    trackHeightPx = resources.displayMetrics.density * 4.5f
                    trackHorizontalPaddingPx = 0f
                    headGlowAlpha = 1f
                }
            },
            update = { view ->
                view.progressFraction = displayProgress
                view.glowColor = glowArgb
                view.trackColor = trackArgb
                view.fallbackProgressColor = accent.copy(alpha = 0.82f).toArgb()
            },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(allowTapSeek) {
                    if (!allowTapSeek) return@pointerInput
                    detectTapGestures { offset ->
                        onSeek(progressAt(size.width.toFloat(), offset.x))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggingProgress = progressAt(size.width.toFloat(), offset.x).also(onPreviewProgressChange)
                        },
                        onDragEnd = {
                            draggingProgress?.let(onSeek)
                            draggingProgress = null
                            onPreviewProgressChange(null)
                        },
                        onDragCancel = {
                            draggingProgress = null
                            onPreviewProgressChange(null)
                        }
                    ) { change, _ ->
                        draggingProgress = progressAt(size.width.toFloat(), change.position.x).also(onPreviewProgressChange)
                    }
                }
        )
    }
}

internal fun openSystemOutputSwitcher(context: Context) {
    if (MiPlayAudioSupport.openMiPlayDetailIfSupported(context)) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val shown = runCatching {
            MediaRouter2.getInstance(context).showSystemOutputSwitcher()
        }.getOrDefault(false)
        if (shown) return
    }

    Toast.makeText(context, context.getString(R.string.player_media_output_unsupported), Toast.LENGTH_SHORT).show()
    runCatching {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal fun formatTime(ms: Long): String {
    return ms.formatPlaybackDuration()
}

internal fun AudioQualitySummary.playerCompactText(): String {
    return when {
        compactLabel == "MQ" -> "∞ Master"
        showMobius -> "∞ $compactLabel"
        else -> compactLabel
    }
}
