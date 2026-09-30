package com.ella.music.ui.player

import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.ui.graphics.Color

/**
 * Apple Music 歌词按钮：
 * 正常态为描边图标（ic_nowplaying_lyrics），激活态为填充图标（ic_nowplaying_lyricson）。
 */
@Composable
internal fun AppleLyricsIcon(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Icon(
        painter = painterResource(if (active) R.drawable.ic_nowplaying_lyricson else R.drawable.ic_nowplaying_lyrics),
        contentDescription = null,
        tint = if (active) color.copy(alpha = 1f) else color,
        modifier = modifier
    )
}

/**
 * 歌词翻译按钮图标（Apple Music 风格双气泡 A 文）：
 */
@Composable
internal fun AppleTranslationIcon(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Icon(
        painter = painterResource(if (active) R.drawable.ic_nowplaying_translateon else R.drawable.ic_nowplaying_translate),
        contentDescription = stringResource(R.string.player_show_translation),
        tint = color,
        modifier = modifier
    )
}

/**
 * Apple Music 伴奏按钮图标（麦克风 + 星光）：
 * 正常态为浅底（ic_nowplaying_vocal），激活态为更深底（ic_nowplaying_vocalon）。
 */
@Composable
internal fun AppleVocalIcon(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Icon(
        painter = painterResource(if (active) R.drawable.ic_nowplaying_vocalon else R.drawable.ic_nowplaying_vocal),
        contentDescription = stringResource(R.string.player_accompaniment),
        tint = color,
        modifier = modifier
    )
}

/**
 * Apple Music 播放/暂停图标：
 * 播放时展示实心双竖圆柱（pause.fill），暂停时展示圆角实心右三角（play.fill）。
 */
@Composable
internal fun ApplePlayPauseIcon(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(if (isPlaying) R.drawable.ic_nowplaying_pause else R.drawable.ic_nowplaying_play),
        contentDescription = stringResource(if (isPlaying) R.string.common_pause else R.string.common_play),
        tint = color,
        modifier = modifier
    )
}

/**
 * Apple Music 上一曲图标（backward.fill）：双实心左向圆角箭头。
 */
@Composable
internal fun AppleSkipPreviousIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(R.drawable.ic_nowplaying_rewind),
        contentDescription = stringResource(R.string.common_previous),
        tint = color,
        modifier = modifier
    )
}

/**
 * Apple Music 下一曲图标（forward.fill）：双实心右向圆角箭头。
 */
@Composable
internal fun AppleSkipNextIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(R.drawable.ic_nowplaying_fforward),
        contentDescription = stringResource(R.string.common_next),
        tint = color,
        modifier = modifier
    )
}

/**
 * Apple Music 队列/待播清单图标（list.bullet）：
 * 正常态为三条横线圆点（ic_nowplaying_queue），激活态为反白高亮胶囊（ic_nowplaying_queueon）。
 */
@Composable
internal fun AppleQueueIcon(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Icon(
        painter = painterResource(if (active) R.drawable.ic_nowplaying_queueon else R.drawable.ic_nowplaying_queue),
        contentDescription = stringResource(R.string.player_queue),
        tint = if (active) color.copy(alpha = 1f) else color,
        modifier = modifier
    )
}

/**
 * Apple Music 风格底部中间投放图标（AirPlay / 投放设备）：
 * 替换原来的 Chromecast 图标。
 */
@Composable
internal fun AppleChromecastIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(R.drawable.ic_nowplaying_airplay),
        contentDescription = stringResource(R.string.casting_devices_title),
        tint = color,
        modifier = modifier
    )
}
