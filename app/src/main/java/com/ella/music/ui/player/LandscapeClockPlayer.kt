package com.ella.music.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.LyricLine
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.ImmersiveSystemBarsEffect
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun LandscapeClockPlayer(
    song: Song?, embeddedCover: Bitmap?, palette: PlayerPalette, isPlaying: Boolean,
    onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onDismiss: () -> Unit,
    showBackButton: Boolean,
    lyrics: List<LyricLine>, currentLyricIndex: Int, currentPosition: Long,
    fontFamily: FontFamily?, fontWeight: FontWeight, fontScale: Float
) {
    ImmersiveSystemBarsEffect(keepScreenOn = true)
    val context = LocalContext.current
    val clockColor by SettingsManager.getInstance(context).playerClockColor.collectAsState(initial = SettingsManager.DEFAULT_PLAYER_CLOCK_COLOR)
    val tint = landscapeClockTint(palette.accent, clockColor)
    val locale = LocalConfiguration.current.locales[0]
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L)
        }
    }
    val time = remember(now, locale) { DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now, locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMMdEEEE"), locale).format(Date(now))
    }
    val battery by rememberClockBattery(context)
    val position = rememberLyricFramePosition(currentPosition, isPlaying)
    val line = lyrics.getOrNull(currentLyricIndex)?.takeIf { it.hasMiniLyric() }
    LandscapeClockContent(time, date, song?.title.orEmpty(), tint,
        battery, isPlaying, onPrevious, onPlayPause, onNext, onDismiss, showBackButton,
        artwork = { modifier ->
            AlbumArtView(song, embeddedCover, cornerRadius = 18.dp, contentScale = ContentScale.Crop,
                loadOriginal = false, modifier = modifier.playerMorphArtwork())
        }, artist = song?.artist.orEmpty(), lyric = {
            if (line != null) AppleMusicSingleLyricLine(
                line = line, currentPositionMs = currentPosition, currentPositionState = position,
                showTranslation = false, showPronunciation = false,
                fontFamily = fontFamily, fontWeight = fontWeight, fontScale = fontScale,
                secondaryFontScale = 1f, primaryTextSizeSp = 16f, secondaryTextSizeSp = 12f,
                lyricTextAlign = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT,
                contentColor = tint, wordLiftEnabled = true,
                singleLine = true, followWordFocus = true, showBackgroundText = false,
                interactive = false, modifier = Modifier.fillMaxWidth().testTag("clock-lyric"))
        })
}

internal data class ClockBattery(val percent: Int, val charging: Boolean)

internal fun landscapeClockTint(accent: Color, color: Int): Color = when (color) {
    SettingsManager.PLAYER_CLOCK_COLOR_WHITE -> Color.White
    SettingsManager.PLAYER_CLOCK_COLOR_LIGHT_GRAY -> Color(0xFFD3D3D3)
    else -> lerp(accent, Color.White, 0.45f)
}

@androidx.annotation.DrawableRes
internal fun clockBatteryIcon(battery: ClockBattery): Int = when {
    battery.charging -> R.drawable.ic_battery_android_frame_bolt
    battery.percent >= 100 -> R.drawable.ic_battery_android_frame_full
    battery.percent >= 67 -> R.drawable.ic_battery_android_frame_5
    battery.percent >= 51 -> R.drawable.ic_battery_android_frame_4
    battery.percent >= 34 -> R.drawable.ic_battery_android_frame_3
    battery.percent >= 17 -> R.drawable.ic_battery_android_frame_2
    else -> R.drawable.ic_battery_android_frame_1
}

@Composable
private fun rememberClockBattery(context: Context): State<ClockBattery?> {
    val battery = remember { mutableStateOf<ClockBattery?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                battery.value = if (level >= 0 && scale > 0) ClockBattery(
                    (level * 100 / scale).coerceIn(0, 100), status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL) else null
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return battery
}

/** A quiet cover/clock surface. A tap exposes the existing transport controls for five seconds. */
@Composable
internal fun LandscapeClockContent(
    time: String, date: String, title: String, tint: Color, battery: ClockBattery?, isPlaying: Boolean,
    onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onDismiss: () -> Unit,
    showBackButton: Boolean, artwork: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier, artist: String = "", lyric: @Composable () -> Unit = {}
) {
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, interaction, isPlaying) {
        if (controlsVisible && isPlaying) { delay(5000); controlsVisible = false }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black).testTag("landscape-cover-clock")
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            controlsVisible = !controlsVisible
        }) {
        val horizontalPadding = minOf(maxWidth * 0.065f, 64.dp)
        val coverSize = minOf(maxHeight * 0.58f, (maxWidth - horizontalPadding * 2) * 0.38f)
        val informationHeight = minOf(coverSize + 40.dp, maxHeight - 72.dp)
        val columnGap = maxWidth * 0.06f
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = horizontalPadding, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically) {
            artwork(Modifier.size(coverSize).clip(RoundedCornerShape(18.dp)).testTag("clock-artwork"))
            Spacer(Modifier.width(columnGap))
            BoxWithConstraints(Modifier.weight(1f).height(informationHeight)) {
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                val requestedSize = (coverSize.value * 0.46f).sp
                val timeStyle = MiuixTheme.textStyles.main.copy(
                    fontSize = requestedSize, fontWeight = FontWeight.Light,
                    platformStyle = PlatformTextStyle(includeFontPadding = false))
                val measured = measurer.measure(time, timeStyle).size
                val fit = minOf(1f, with(density) { maxWidth.toPx() } / measured.width.coerceAtLeast(1))
                Column(Modifier.fillMaxSize()) {
                    Text(time, color = tint, maxLines = 1, softWrap = false,
                        style = timeStyle.copy(fontSize = requestedSize * fit, lineHeight = requestedSize * fit),
                        modifier = Modifier.testTag("clock-time"))
                    Text(date, color = tint.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).testTag("clock-date"))
                    Spacer(Modifier.weight(1f))
                    Text(title, color = tint, fontSize = (coverSize.value * 0.086f).coerceIn(20f, 34f).sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("clock-title"))
                    Text(artist, color = tint.copy(alpha = 0.65f), fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp).testTag("clock-artist"))
                    Box(Modifier.fillMaxWidth().height(32.dp).padding(top = 6.dp)) { lyric() }
                }
            }
        }
        battery?.let {
            val description = stringResource(R.string.player_clock_battery, it.percent)
            Row(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp).semantics(mergeDescendants = true) { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(clockBatteryIcon(it)), contentDescription = null,
                    tint = tint.copy(alpha = 0.60f), modifier = Modifier.size(24.dp).testTag("clock-battery-icon"))
                Text("${it.percent}%", color = tint.copy(alpha = 0.60f), fontSize = 11.sp,
                    modifier = Modifier.testTag("clock-battery"))
            }
        }
        CompositionLocalProvider(LocalPlayerContentColor provides tint) {
            AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)) {
                Row(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 12.dp)
                    .testTag("clock-transport"), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PlayerTransportIconButton(onClick = { interaction++; onPrevious() }, buttonSize = 48.dp) {
                        Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.common_previous), tint = tint)
                    }
                    PlayerTransportIconButton(onClick = { interaction++; onPlayPause() }, buttonSize = 56.dp) {
                        val description = stringResource(if (isPlaying) R.string.common_pause else R.string.common_play)
                        Box(Modifier.semantics { contentDescription = description }) {
                            CenteredPlayPauseGlyph(isPlaying, tint, Modifier.size(32.dp))
                        }
                    }
                    PlayerTransportIconButton(onClick = { interaction++; onNext() }, buttonSize = 48.dp) {
                        Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.common_next), tint = tint)
                    }
                }
            }
            if (controlsVisible && showBackButton) {
                Box(Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    PlayerTransportIconButton(onClick = onDismiss, buttonSize = 48.dp) {
                        Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back), tint = tint)
                    }
                }
            }
        }
    }
}
