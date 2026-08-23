package com.ella.music.ui.player

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.normalizedAudioFormat
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.ui.components.loadAndroidTypeface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

internal const val PLAYER_POSITION_BACKWARD_DRIFT_TOLERANCE_MS = 600L

internal enum class PlayerLyricLayoutProfile {
    Compact,
    Wide
}

internal fun isUltraWideLandscapePlayerLayout(
    screenWidthDp: Int,
    screenHeightDp: Int
): Boolean {
    if (screenWidthDp <= 0 || screenHeightDp <= 0) return false
    val longSide = max(screenWidthDp, screenHeightDp)
    val shortSide = min(screenWidthDp, screenHeightDp)
    return screenWidthDp > screenHeightDp &&
        longSide.toFloat() / shortSide.toFloat() >= 2.45f
}

internal fun resolvePlayerLyricLayoutProfile(
    screenWidthDp: Int,
    screenHeightDp: Int,
    smallestScreenWidthDp: Int
): PlayerLyricLayoutProfile {
    val wideLandscapeCanvas = screenWidthDp > screenHeightDp && screenWidthDp >= 840
    return if (
        smallestScreenWidthDp >= 600 ||
        isUltraWideLandscapePlayerLayout(screenWidthDp, screenHeightDp) ||
        wideLandscapeCanvas
    ) {
        PlayerLyricLayoutProfile.Wide
    } else {
        PlayerLyricLayoutProfile.Compact
    }
}

internal fun PlayerLyricLayoutProfile.primaryScaleRangePercent(): IntRange =
    primaryScaleRangePercent(ultraWideLandscape = false)

internal fun PlayerLyricLayoutProfile.primaryScaleRangePercent(
    ultraWideLandscape: Boolean
): IntRange =
    when (this) {
        PlayerLyricLayoutProfile.Compact -> SettingsManager.LYRIC_FONT_SCALE_MIN..SettingsManager.LYRIC_FONT_SCALE_PHONE_MAX
        PlayerLyricLayoutProfile.Wide -> {
            val max = if (ultraWideLandscape) {
                SettingsManager.LYRIC_FONT_SCALE_ULTRA_WIDE_MAX
            } else {
                SettingsManager.LYRIC_FONT_SCALE_WIDE_MAX
            }
            SettingsManager.LYRIC_FONT_SCALE_MIN..max
        }
    }

internal fun PlayerLyricLayoutProfile.secondaryScaleRangePercent(): IntRange =
    secondaryScaleRangePercent(ultraWideLandscape = false)

internal fun PlayerLyricLayoutProfile.secondaryScaleRangePercent(
    ultraWideLandscape: Boolean
): IntRange =
    when (this) {
        PlayerLyricLayoutProfile.Compact ->
            SettingsManager.LYRIC_SECONDARY_FONT_SCALE_MIN..SettingsManager.LYRIC_SECONDARY_FONT_SCALE_PHONE_MAX
        PlayerLyricLayoutProfile.Wide -> {
            val max = if (ultraWideLandscape) {
                SettingsManager.LYRIC_SECONDARY_FONT_SCALE_ULTRA_WIDE_MAX
            } else {
                SettingsManager.LYRIC_SECONDARY_FONT_SCALE_WIDE_MAX
            }
            SettingsManager.LYRIC_SECONDARY_FONT_SCALE_MIN..max
        }
    }

internal fun PlayerLyricLayoutProfile.primaryTextSizeRangeSp(): IntRange =
    when (this) {
        PlayerLyricLayoutProfile.Compact ->
            SettingsManager.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MIN_SP..SettingsManager.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MAX_SP
        PlayerLyricLayoutProfile.Wide ->
            SettingsManager.LYRIC_WIDE_PRIMARY_TEXT_SIZE_MIN_SP..SettingsManager.LYRIC_WIDE_PRIMARY_TEXT_SIZE_MAX_SP
    }

internal fun PlayerLyricLayoutProfile.secondaryTextSizeRangeSp(): IntRange =
    when (this) {
        PlayerLyricLayoutProfile.Compact ->
            SettingsManager.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MIN_SP..SettingsManager.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MAX_SP
        PlayerLyricLayoutProfile.Wide ->
            SettingsManager.LYRIC_WIDE_SECONDARY_TEXT_SIZE_MIN_SP..SettingsManager.LYRIC_WIDE_SECONDARY_TEXT_SIZE_MAX_SP
    }

internal fun shouldIgnoreMinorPlaybackRegression(
    currentUiPositionMs: Long,
    nextPositionMs: Long,
    isPlaying: Boolean,
    toleranceMs: Long = PLAYER_POSITION_BACKWARD_DRIFT_TOLERANCE_MS
): Boolean =
    isPlaying &&
        nextPositionMs < currentUiPositionMs &&
        currentUiPositionMs - nextPositionMs in 1..toleranceMs

@Composable
internal fun rememberThrottledPlayerPosition(
    positionFlow: StateFlow<Long>,
    isPlaying: Boolean,
    anchorKey: Any?,
    livePositionProvider: () -> Long = { positionFlow.value },
    intervalMs: Long = 250L
): Long {
    val latestPlaying by rememberUpdatedState(isPlaying)
    val latestLivePositionProvider by rememberUpdatedState(livePositionProvider)
    return produceState(initialValue = positionFlow.value, positionFlow, anchorKey) {
        var lastUiTickMs = 0L
        fun applyPosition(positionMs: Long) {
            val now = SystemClock.elapsedRealtime()
            if (shouldIgnoreMinorPlaybackRegression(value, positionMs, latestPlaying)) return
            val reset = positionMs < value || kotlin.math.abs(positionMs - value) > 1_500L
            val shouldUpdate = reset || !latestPlaying || now - lastUiTickMs >= intervalMs
            if (!shouldUpdate) return

            value = positionMs
            lastUiTickMs = now
        }
        launch {
            positionFlow.collect { positionMs ->
                applyPosition(positionMs)
            }
        }
        while (true) {
            if (latestPlaying) {
                applyPosition(latestLivePositionProvider())
            }
            delay(intervalMs)
        }
    }.value
}

internal fun adaptiveTitleFontSize(text: String, maxSize: TextUnit): TextUnit {
    val scale = when {
        text.length > 72 -> 0.54f
        text.length > 58 -> 0.62f
        text.length > 44 -> 0.70f
        text.length > 32 -> 0.80f
        text.length > 24 -> 0.90f
        else -> 1f
    }
    return (maxSize.value * scale).sp
}

internal fun String.toPlayerLyricFontFamily(weight: Int, italic: Boolean): FontFamily? {
    if (isBlank()) return null
    return runCatching {
        FontFamily(loadAndroidTypeface(this, weight, italic, boldFallback = false))
    }.getOrNull()
}

internal fun String.toPlayerLyricTypeface(weight: Int): Typeface? {
    if (isBlank()) return null
    return runCatching {
        loadAndroidTypeface(this, weight, italic = false, boldFallback = false)
    }.getOrNull()
}

internal fun ensureBundledMiSansBoldPath(context: Context): String {
    val bundledDir = File(context.filesDir, "lyric_builtin_fonts").apply { mkdirs() }
    val target = File(bundledDir, "MiSans-Bold.ttf")
    if (!target.exists() || target.length() <= 0L) {
        runCatching {
            context.assets.open("fonts/MiSans-Bold.ttf").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.onFailure {
            if (target.exists() && target.length() <= 0L) target.delete()
        }
    }
    return target.takeIf { it.exists() && it.canRead() && it.length() > 0L }?.absolutePath.orEmpty()
}

internal fun isXiaomiFamilyPlayerDevice(): Boolean {
    val brand = Build.BRAND.orEmpty()
    val manufacturer = Build.MANUFACTURER.orEmpty()
    return listOf(brand, manufacturer).any { value ->
        value.contains("xiaomi", ignoreCase = true) ||
            value.contains("redmi", ignoreCase = true) ||
            value.contains("poco", ignoreCase = true)
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
internal fun setPlayerSystemBars(activity: Activity?, view: View) {
    val window = activity?.window ?: return
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
}

@Composable
internal fun rememberBluetoothOutputName(): String? {
    val context = LocalContext.current
    var outputName by remember(context) { mutableStateOf(context.currentOutputDisplayName()) }
    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            outputName = null
            return@DisposableEffect onDispose {}
        }
        outputName = context.currentOutputDisplayName(audioManager)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                outputName = context.currentOutputDisplayName(audioManager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                outputName = context.currentOutputDisplayName(audioManager)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }
    return outputName
}

internal fun ensureBundledInterPath(context: Context): String {
    val bundledDir = File(context.filesDir, "lyric_builtin_fonts").apply { mkdirs() }
    val target = File(bundledDir, "Inter-Bold.ttf")
    if (!target.exists() || target.length() <= 0L) {
        runCatching {
            context.assets.open("fonts/Inter-Bold.ttf").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.onFailure {
            if (target.exists() && target.length() <= 0L) target.delete()
        }
    }
    return target.takeIf { it.exists() && it.canRead() && it.length() > 0L }?.absolutePath.orEmpty()
}

private fun Context.currentOutputDisplayName(): String? {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    return currentOutputDisplayName(audioManager)
}

private fun Context.currentOutputDisplayName(audioManager: AudioManager?): String? {
    val devices = runCatching {
        audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
    }.getOrDefault(emptyArray())
    val bluetooth = devices.firstOrNull(::isBluetoothOutputDevice)
    val headphones = devices.firstOrNull { device ->
        device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
    }
    val usb = devices.firstOrNull { device ->
        device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }
    val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    return when {
        bluetooth != null -> bluetooth.outputDisplayName(this, R.string.player_output_bluetooth)
        headphones != null -> headphones.outputDisplayName(this, R.string.player_output_headphones)
        usb != null -> usb.outputDisplayName(this, R.string.player_output_usb_audio)
        speaker != null -> getString(R.string.player_output_speaker)
        else -> null
    }
}

private fun isBluetoothOutputDevice(device: AudioDeviceInfo): Boolean {
    val type = device.type
    if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
        return true
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
    ) {
        return true
    }
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        type == AudioDeviceInfo.TYPE_BLE_BROADCAST
}

private fun AudioDeviceInfo.outputDisplayName(context: Context, fallbackRes: Int): String =
    productName
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { it.isLikelyLocalDeviceModelName() }
        ?: context.getString(fallbackRes)

private fun String.isLikelyLocalDeviceModelName(): Boolean {
    val normalized = trim()
    if (normalized.isBlank()) return false
    val candidates = listOf(
        Build.MODEL,
        Build.DEVICE,
        Build.PRODUCT,
        Build.BOARD,
        Build.HARDWARE
    ).map { it.orEmpty().trim() }.filter { it.isNotBlank() }
    return candidates.any { normalized.equals(it, ignoreCase = true) }
}

internal fun AudioInfo.isHiResLogoTrack(): Boolean {
    val summary = audioQualitySummary(this)
    if (summary.listTag in setOf("HR", "MQ") ||
        summary.compactLabel.equals("Hi-Res", ignoreCase = true)) {
        return true
    }
    val fmt = normalizedAudioFormat(format)
    return fmt in setOf("FLAC", "ALAC", "WAV", "APE", "DSD") && sampleRate >= 48_000
}

internal fun Float.nextPlaybackStep(): Float {
    val next = ((this * 4).toInt() + 1) / 4f
    return if (next > 2f) 0.5f else next.coerceIn(0.5f, 2f)
}

internal fun enqueuePlayerDownload(context: Context, song: Song) {
    val fileName = song.fileName.ifBlank { "${song.title}-${song.artist}.mp3" }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { "Halcyon.mp3" }
    val request = DownloadManager.Request(Uri.parse(song.path))
        .setTitle(fileName)
        .setDescription("${song.title} - ${song.artist}")
        .setMimeType(song.mimeType.ifBlank { "audio/*" })
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Halcyon/$fileName")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}
