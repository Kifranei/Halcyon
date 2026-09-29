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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
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
        requestedSampleRate.takeIf { it > 0 }?.let(::formatAudioRate)
            ?: stringResource(R.string.player_audio_output_auto_sample_rate),
        requestedBitDepth.takeIf { it > 0 }?.let {
            if (it == SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_FLOAT32) "Float32" else "$it-bit PCM"
        } ?: stringResource(R.string.player_audio_output_auto_bit_depth)
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
        if (eqEnabled) add(stringResource(R.string.equalizer_master))
        if (bassBoostEnabled) add(stringResource(R.string.equalizer_bass_boost))
        if (virtualizerEnabled) add(stringResource(R.string.equalizer_virtualizer))
        if (reverbPreset > 0) add(stringResource(R.string.equalizer_reverb))
        if (replayGainMode != SettingsManager.REPLAY_GAIN_OFF) add("ReplayGain")
    }.joinToString(" · ").ifBlank { stringResource(R.string.player_audio_output_dsp_bypass) }
    val decoder = when (decoderMode) {
        0 -> stringResource(R.string.settings_audio_decoder_system)
        1 -> stringResource(R.string.settings_audio_decoder_ffmpeg)
        else -> stringResource(R.string.settings_audio_decoder_auto)
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
        AudioOutputInfoSection(stringResource(R.string.player_audio_output_section_source)) {
            AudioOutputInfoRow(
                stringResource(R.string.player_audio_output_file),
                song?.fileName?.ifBlank { song.path }.orEmpty().ifBlank { "—" },
                forceLtrValue = true
            )
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_codec_container), codec, forceLtrValue = true)
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_source_format), source, forceLtrValue = true)
            AudioOutputInfoRow(
                stringResource(R.string.player_audio_output_replaygain_tag),
                resolvedAudioInfo?.replayGainDb?.let { "%+.2f dB".format(it) } ?: "—",
                forceLtrValue = true
            )
        }
        AudioOutputInfoSection(stringResource(R.string.player_audio_output_section_decoder)) {
            AudioOutputInfoRow(stringResource(R.string.settings_decoder), decoder, forceLtrValue = false)
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_decoded_pcm), decodedPcm, forceLtrValue = true)
        }
        AudioOutputInfoSection(stringResource(R.string.player_audio_output_resampling)) {
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_sample_rate), sourceToOutputRate, forceLtrValue = true)
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_bit_depth), sourceToOutputDepth, forceLtrValue = true)
            AudioOutputInfoRow(
                stringResource(R.string.player_audio_output_resampling),
                stringResource(
                    if (formatRequiresConversion) R.string.player_audio_output_resampling_active
                    else R.string.player_audio_output_resampling_none
                ),
                forceLtrValue = false
            )
        }
        AudioOutputInfoSection(stringResource(R.string.player_audio_output_section_dsp)) {
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_dsp_chain), dsp, forceLtrValue = false)
        }
        AudioOutputInfoSection(stringResource(R.string.player_audio_output_section_output)) {
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_requested_format), requestedOutput, forceLtrValue = true)
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_path), output, forceLtrValue = true)
            AudioOutputInfoRow(stringResource(R.string.player_audio_output_device), outputDevice, forceLtrValue = false)
            AudioOutputInfoRow(
                stringResource(R.string.player_audio_output_audio_session_id),
                audioSessionId.takeIf { it > 0 }?.toString() ?: "—",
                forceLtrValue = true
            )
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
private fun AudioOutputInfoRow(
    label: String,
    value: String,
    forceLtrValue: Boolean = false
) {
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
                style = TextStyle(
                    textDirection = if (forceLtrValue) TextDirection.Ltr else TextDirection.Content
                ),
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

private fun formatAudioRate(sampleRate: Int): String =
    if (sampleRate % 1_000 == 0) "${sampleRate / 1_000} kHz" else "%.1f kHz".format(sampleRate / 1_000f)
