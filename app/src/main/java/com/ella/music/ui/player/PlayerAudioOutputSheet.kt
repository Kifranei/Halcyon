package com.ella.music.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.ella.music.player.PlaybackAudioOutputState
import com.ella.music.player.PlaybackAudioSession
import com.ella.music.player.playbackFormatRequiresConversion
import com.ella.music.player.playbackPcmEncodingLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AudioOutputInfoSheetContent(
    onBack: () -> Unit,
    song: Song? = null,
    audioInfo: AudioInfo? = null,
    showHeader: Boolean = true
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val info by PlaybackAudioOutputState.info.collectAsState()
    val audioSessionId by PlaybackAudioSession.audioSessionId.collectAsState()
    val decoderMode by settingsManager.decoderMode.collectAsState(initial = 2)
    val requestedBitDepth by settingsManager.audioOutputBitDepth.collectAsState(initial = 0)
    val requestedSampleRate by settingsManager.audioOutputSampleRate.collectAsState(initial = 0)
    val replayGainMode by settingsManager.replayGainMode.collectAsState(initial = SettingsManager.REPLAY_GAIN_OFF)
    val eqEnabled by settingsManager.eqEnabled.collectAsState(initial = false)
    val bassBoostEnabled by settingsManager.bassBoostEnabled.collectAsState(initial = false)
    val virtualizerEnabled by settingsManager.virtualizerEnabled.collectAsState(initial = false)
    val reverbPreset by settingsManager.reverbPreset.collectAsState(initial = 0)
    val resolvedAudioInfo by produceState(initialValue = audioInfo, song, audioInfo) {
        value = audioInfo ?: song?.let { current ->
            withContext(Dispatchers.IO) {
                MusicRepository.getInstance(context).getAudioInfo(current)
            }
        }
    }
    val outputDevice = rememberBluetoothOutputName().orEmpty().ifBlank { "—" }
    val sourceFormat = resolvedAudioInfo?.format?.takeIf(String::isNotBlank)
        ?: info.sourceMimeType.substringAfter('/').uppercase().takeIf(String::isNotBlank)
        ?: "—"
    val source = listOfNotNull(
        sourceFormat,
        info.sourceSampleRate.takeIf { it > 0 }?.let(::formatAudioRate),
        resolvedAudioInfo?.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" },
        info.sourceChannelCount.takeIf { it > 0 }?.let { "$it ch" },
        info.sourceBitRate.takeIf { it > 0 }?.let { "${it / 1_000} kbps" }
    ).joinToString(" · ").ifBlank { "—" }
    val output = listOfNotNull(
        info.outputBackend,
        info.outputSampleRate.takeIf { it > 0 }?.let(::formatAudioRate),
        playbackPcmEncodingLabel(info.outputEncoding).takeUnless { it == "—" },
        info.outputChannelCount.takeIf { it > 0 }?.let { "$it ch" }
    ).joinToString(" · ")
    val codec = listOfNotNull(
        info.sourceCodecs.takeIf(String::isNotBlank),
        info.sourceMimeType.takeIf(String::isNotBlank),
        info.sourceContainerMimeType.takeIf(String::isNotBlank)
    ).distinct().joinToString(" · ").ifBlank { sourceFormat }
    val decodedPcm = listOfNotNull(
        info.outputSampleRate.takeIf { it > 0 }?.let(::formatAudioRate),
        playbackPcmEncodingLabel(info.outputEncoding).takeUnless { it == "—" },
        info.outputChannelCount.takeIf { it > 0 }?.let { "$it ch" }
    ).joinToString(" · ").ifBlank { "—" }
    val requestedOutput = listOf(
        requestedSampleRate.takeIf { it > 0 }?.let(::formatAudioRate) ?: "自动采样率",
        requestedBitDepth.takeIf { it > 0 }?.let {
            if (it == SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_FLOAT32) "Float32" else "$it-bit PCM"
        } ?: "自动位深"
    ).joinToString(" · ")
    val sourceToOutputRate = listOf(
        info.sourceSampleRate.takeIf { it > 0 }?.let(::formatAudioRate) ?: "—",
        info.outputSampleRate.takeIf { it > 0 }?.let(::formatAudioRate) ?: "—"
    ).joinToString(" → ")
    val sourceToOutputDepth = listOf(
        resolvedAudioInfo?.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" } ?: "—",
        playbackPcmEncodingLabel(info.outputEncoding)
    ).joinToString(" → ")
    val dsp = buildList {
        if (eqEnabled) add("均衡器")
        if (bassBoostEnabled) add("低音增强")
        if (virtualizerEnabled) add("虚拟器")
        if (reverbPreset > 0) add("混响")
        if (replayGainMode != SettingsManager.REPLAY_GAIN_OFF) add("ReplayGain")
    }.joinToString(" · ").ifBlank { "旁路（无效果）" }
    val decoder = when (decoderMode) {
        0 -> "Android 系统解码"
        1 -> "FFmpeg 解码优先"
        else -> "自动（系统 / FFmpeg 回退）"
    }
    val formatRequiresConversion = playbackFormatRequiresConversion(
        sourceSampleRate = info.sourceSampleRate,
        outputSampleRate = info.outputSampleRate,
        sourceBitDepth = resolvedAudioInfo?.bitDepth ?: 0,
        outputEncoding = info.outputEncoding
    )

    val maxStandaloneHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    val contentModifier = if (showHeader) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxStandaloneHeight)
            .verticalScroll(rememberScrollState())
    }
    Column(modifier = contentModifier) {
        if (showHeader) {
            HalfSheetTitle(title = stringResource(R.string.player_audio_output_info), onBack = onBack)
            Spacer(modifier = Modifier.height(18.dp))
        }
        AudioOutputInfoSection("媒体源") {
            AudioOutputInfoRow("文件", song?.fileName?.ifBlank { song.path }.orEmpty().ifBlank { "—" })
            AudioOutputInfoRow("编码 / 容器", codec)
            AudioOutputInfoRow("原始音频格式", source)
            AudioOutputInfoRow("ReplayGain 标签", resolvedAudioInfo?.replayGainDb?.let { "%+.2f dB".format(it) } ?: "—")
        }
        AudioOutputInfoSection("解码") {
            AudioOutputInfoRow(stringResource(R.string.settings_decoder), decoder)
            AudioOutputInfoRow("解码后 PCM", decodedPcm)
        }
        AudioOutputInfoSection("重采样") {
            AudioOutputInfoRow("采样率", sourceToOutputRate)
            AudioOutputInfoRow("位深", sourceToOutputDepth)
            AudioOutputInfoRow(
                stringResource(R.string.player_audio_output_resampling),
                stringResource(
                    if (formatRequiresConversion) R.string.player_audio_output_resampling_active
                    else R.string.player_audio_output_resampling_none
                )
            )
        }
        AudioOutputInfoSection("DSP") { AudioOutputInfoRow("处理链", dsp) }
        AudioOutputInfoSection("输出") {
            AudioOutputInfoRow("请求格式", requestedOutput)
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_path), output)
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_device), outputDevice)
            AudioOutputInfoRow("音频会话 ID", audioSessionId.takeIf { it > 0 }?.toString() ?: "—")
        }
    }
}

@Composable
private fun AudioOutputInfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 5.dp)
    )
    PlayerActionMenuGroup(content = content)
}

@Composable
private fun AudioOutputInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Text(
                text = value,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

private fun formatAudioRate(sampleRate: Int): String =
    if (sampleRate % 1_000 == 0) "${sampleRate / 1_000} kHz" else "%.1f kHz".format(sampleRate / 1_000f)
