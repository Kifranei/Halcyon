package com.ella.music.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.player.BluetoothAutoPlayReceiver
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixDialogActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null,
    onNavigateToEqualizer: () -> Unit = {},
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
    val bluetoothAutoPlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { settingsManager.setBluetoothAutoPlay(granted) }
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.settings_bluetooth_auto_play_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val gaplessPlayback by settingsManager.gaplessPlayback.collectAsState(initial = true)
    val crossfadeDurationMs by settingsManager.crossfadeDurationMs.collectAsState(initial = 0)
    val crossfadeCurve by settingsManager.crossfadeCurve.collectAsState(
        initial = SettingsManager.CROSSFADE_CURVE_EQUAL_POWER
    )
    var showCrossfadeDurationDialog by remember { mutableStateOf(false) }
    var crossfadeDurationInput by remember { mutableStateOf("") }
    val replayGainMode by settingsManager.replayGainMode.collectAsState(initial = SettingsManager.REPLAY_GAIN_OFF)
    val resumePlaybackPosition by settingsManager.resumePlaybackPosition.collectAsState(initial = false)
    val audioFocusDisabled by settingsManager.audioFocusDisabled.collectAsState(initial = false)
    val shuffleMode by settingsManager.shuffleMode.collectAsState(initial = SettingsManager.SHUFFLE_MODE_PSEUDO)
    val playNextMode by settingsManager.playNextMode.collectAsState(initial = SettingsManager.PLAY_NEXT_MODE_REVERSE_STACK)
    val previousButtonAction by settingsManager.previousButtonAction.collectAsState(initial = SettingsManager.PREVIOUS_BUTTON_PREVIOUS)
    val decoderMode by settingsManager.decoderMode.collectAsState(initial = 2)
    val audioOutputBackend by settingsManager.audioOutputBackend.collectAsState(initial = SettingsManager.AUDIO_OUTPUT_BACKEND_AUTO)
    val audioOutputBitDepth by settingsManager.audioOutputBitDepth.collectAsState(initial = SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_AUTO)
    val audioOutputSampleRate by settingsManager.audioOutputSampleRate.collectAsState(initial = SettingsManager.AUDIO_OUTPUT_SAMPLE_RATE_AUTO)
    val usbDacMode by settingsManager.usbDacMode.collectAsState(initial = false)
    val usbAudioController = remember(context) { com.ella.music.player.UsbAudioController.getInstance(context) }
    val connectedUsbDevice by usbAudioController.preferredUsbDevice.collectAsState(initial = null)
    val startupPlayMode by settingsManager.startupPlayMode.collectAsState(initial = SettingsManager.STARTUP_PLAY_OFF)
    val bluetoothAutoPlay by settingsManager.bluetoothAutoPlay.collectAsState(initial = false)
    val decoderLabels = listOf(
        stringResource(R.string.settings_audio_decoder_system),
        stringResource(R.string.settings_audio_decoder_ffmpeg),
        stringResource(R.string.settings_audio_decoder_auto)
    )
    val selectedDecoderMode = decoderMode.coerceIn(decoderLabels.indices)
    val audioOutputBackendValues = listOf(
        SettingsManager.AUDIO_OUTPUT_BACKEND_AUTO,
        SettingsManager.AUDIO_OUTPUT_BACKEND_OPENSLES,
        SettingsManager.AUDIO_OUTPUT_BACKEND_AAUDIO,
        SettingsManager.AUDIO_OUTPUT_BACKEND_HI_RES,
        SettingsManager.AUDIO_OUTPUT_BACKEND_AUDIOTRACK
    )
    val audioOutputBackendLabels = listOf(
        stringResource(R.string.settings_audio_output_backend_auto),
        stringResource(R.string.settings_audio_output_backend_opensles),
        stringResource(R.string.settings_audio_output_backend_aaudio),
        stringResource(R.string.settings_audio_output_backend_hires),
        stringResource(R.string.settings_audio_output_backend_audiotrack)
    )
    val selectedAudioOutputBackendIndex = audioOutputBackendValues.indexOf(audioOutputBackend).let {
        if (it >= 0) it else 0
    }
    val audioOutputBitDepthValues = listOf(
        SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_AUTO,
        SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_16,
        SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_24,
        SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_32,
        SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_FLOAT32
    )
    val audioOutputBitDepthLabels = listOf(
        stringResource(R.string.settings_audio_output_auto),
        stringResource(R.string.settings_audio_output_bit_depth_16),
        stringResource(R.string.settings_audio_output_bit_depth_24),
        stringResource(R.string.settings_audio_output_bit_depth_32),
        stringResource(R.string.settings_audio_output_bit_depth_float32)
    )
    val selectedAudioOutputBitDepthIndex = audioOutputBitDepthValues.indexOf(audioOutputBitDepth).let {
        if (it >= 0) it else 0
    }
    val audioOutputSampleRateValues = listOf(SettingsManager.AUDIO_OUTPUT_SAMPLE_RATE_AUTO) +
        SettingsManager.AUDIO_OUTPUT_SAMPLE_RATES.toList()
    val audioOutputSampleRateLabels = listOf(stringResource(R.string.settings_audio_output_auto)) +
        SettingsManager.AUDIO_OUTPUT_SAMPLE_RATES.map { rate ->
            stringResource(R.string.settings_audio_output_sample_rate_khz, rate / 1000f)
        }
    val selectedAudioOutputSampleRateIndex = audioOutputSampleRateValues.indexOf(audioOutputSampleRate).let {
        if (it >= 0) it else 0
    }
    val shuffleModeLabels = listOf(
        stringResource(R.string.settings_shuffle_mode_pseudo_random),
        stringResource(R.string.settings_shuffle_mode_true_random)
    )
    val selectedShuffleMode = shuffleMode.coerceIn(shuffleModeLabels.indices)
    val playNextModeLabels = listOf(
        stringResource(R.string.settings_play_next_mode_reverse_stack),
        stringResource(R.string.settings_play_next_mode_forward_stack)
    )
    val selectedPlayNextMode = playNextMode.coerceIn(playNextModeLabels.indices)
    val previousButtonLabels = listOf(
        stringResource(R.string.settings_previous_button_previous),
        stringResource(R.string.settings_previous_button_replay_current)
    )
    val selectedPreviousButtonAction = previousButtonAction.coerceIn(previousButtonLabels.indices)
    val replayGainLabels = listOf(
        stringResource(R.string.settings_replay_gain_off),
        stringResource(R.string.settings_replay_gain_track),
        stringResource(R.string.settings_replay_gain_album),
        stringResource(R.string.settings_replay_gain_auto)
    )
    val selectedReplayGainMode = replayGainMode.coerceIn(replayGainLabels.indices)
    val crossfadeCurveLabels = listOf(
        stringResource(R.string.settings_crossfade_curve_equal_power),
        stringResource(R.string.settings_crossfade_curve_linear),
        stringResource(R.string.settings_crossfade_curve_smooth),
        stringResource(R.string.settings_crossfade_curve_flat)
    )
    val selectedCrossfadeCurve = crossfadeCurve.coerceIn(crossfadeCurveLabels.indices)
    val crossfadeCurveEntries = listOf(
        DropdownItem(
            title = crossfadeCurveLabels[SettingsManager.CROSSFADE_CURVE_EQUAL_POWER],
            summary = stringResource(R.string.settings_crossfade_curve_equal_power_summary)
        ),
        DropdownItem(title = crossfadeCurveLabels[SettingsManager.CROSSFADE_CURVE_LINEAR]),
        DropdownItem(title = crossfadeCurveLabels[SettingsManager.CROSSFADE_CURVE_SMOOTH]),
        DropdownItem(
            title = crossfadeCurveLabels[SettingsManager.CROSSFADE_CURVE_FLAT],
            summary = stringResource(R.string.settings_crossfade_curve_flat_summary)
        )
    )
    val startupPlayLabels = listOf(
        stringResource(R.string.settings_startup_play_off),
        stringResource(R.string.settings_startup_play_random),
        stringResource(R.string.settings_startup_play_resume)
    )
    val selectedStartupPlayMode = startupPlayMode.coerceIn(startupPlayLabels.indices)
    val startupPlayEntries = listOf(
        DropdownItem(
            title = startupPlayLabels[SettingsManager.STARTUP_PLAY_OFF],
            summary = stringResource(R.string.settings_startup_play_off_summary)
        ),
        DropdownItem(
            title = startupPlayLabels[SettingsManager.STARTUP_PLAY_RANDOM],
            summary = stringResource(R.string.settings_startup_play_random_summary)
        ),
        DropdownItem(
            title = startupPlayLabels[SettingsManager.STARTUP_PLAY_RESUME],
            summary = stringResource(R.string.settings_startup_play_resume_summary)
        )
    )
    val decoderEntries = listOf(
        DropdownItem(
            title = decoderLabels[0],
            summary = stringResource(R.string.settings_audio_decoder_system_summary)
        ),
        DropdownItem(
            title = decoderLabels[1],
            summary = stringResource(R.string.settings_audio_decoder_ffmpeg_summary)
        ),
        DropdownItem(
            title = decoderLabels[2],
            summary = stringResource(R.string.settings_audio_decoder_auto_summary)
        )
    )
    val audioOutputBackendEntries = listOf(
        DropdownItem(
            title = audioOutputBackendLabels[0],
            summary = stringResource(R.string.settings_audio_output_backend_auto_summary)
        ),
        DropdownItem(
            title = audioOutputBackendLabels[1],
            summary = stringResource(R.string.settings_audio_output_backend_compat_summary)
        ),
        DropdownItem(
            title = audioOutputBackendLabels[2],
            summary = stringResource(R.string.settings_audio_output_backend_compat_summary)
        ),
        DropdownItem(
            title = audioOutputBackendLabels[3],
            summary = stringResource(R.string.settings_audio_output_backend_hires_summary)
        ),
        DropdownItem(
            title = audioOutputBackendLabels[4],
            summary = stringResource(R.string.settings_audio_output_backend_audiotrack_summary)
        )
    )
    val audioOutputBitDepthEntries = audioOutputBitDepthLabels.map { DropdownItem(title = it) }
    val audioOutputSampleRateEntries = audioOutputSampleRateLabels.map { DropdownItem(title = it) }
    val shuffleModeEntries = listOf(
        DropdownItem(
            title = shuffleModeLabels[0],
            summary = stringResource(R.string.settings_shuffle_mode_pseudo_random_summary)
        ),
        DropdownItem(
            title = shuffleModeLabels[SettingsManager.SHUFFLE_MODE_TRUE_RANDOM],
            summary = stringResource(R.string.settings_shuffle_mode_true_random_summary)
        )
    )
    val previousButtonEntries = listOf(
        DropdownItem(
            title = previousButtonLabels[SettingsManager.PREVIOUS_BUTTON_PREVIOUS],
            summary = stringResource(R.string.settings_previous_button_previous_summary)
        ),
        DropdownItem(
            title = previousButtonLabels[SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT],
            summary = stringResource(R.string.settings_previous_button_replay_current_summary)
        )
    )
    val playNextModeEntries = listOf(
        DropdownItem(
            title = playNextModeLabels[SettingsManager.PLAY_NEXT_MODE_REVERSE_STACK],
            summary = stringResource(R.string.settings_play_next_mode_reverse_stack_summary)
        ),
        DropdownItem(
            title = playNextModeLabels[SettingsManager.PLAY_NEXT_MODE_FORWARD_STACK],
            summary = stringResource(R.string.settings_play_next_mode_forward_stack_summary)
        )
    )
    val replayGainEntries = listOf(
        DropdownItem(
            title = replayGainLabels[SettingsManager.REPLAY_GAIN_OFF],
            summary = stringResource(R.string.settings_replay_gain_off_summary)
        ),
        DropdownItem(
            title = replayGainLabels[SettingsManager.REPLAY_GAIN_TRACK],
            summary = stringResource(R.string.settings_replay_gain_track_summary)
        ),
        DropdownItem(
            title = replayGainLabels[SettingsManager.REPLAY_GAIN_ALBUM],
            summary = stringResource(R.string.settings_replay_gain_album_summary)
        ),
        DropdownItem(
            title = replayGainLabels[SettingsManager.REPLAY_GAIN_AUTO],
            summary = stringResource(R.string.settings_replay_gain_auto_summary)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_audio_screen_title),
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SmallTitle(text = stringResource(R.string.equalizer_section_effects))

            SettingsCardGroup(highlight = highlightKey == "audio_effects") {
                SettingsFocusAnchor(active = highlightKey == "audio_effects") {
                    ArrowPreference(
                        title = stringResource(R.string.equalizer_screen_title),
                        summary = stringResource(R.string.settings_audio_equalizer_summary),
                        onClick = onNavigateToEqualizer
                    )
                }
            }

            SmallTitle(text = stringResource(R.string.settings_audio_output_section))

            SettingsCardGroup(highlight = highlightKey == "audio_output") {
                Column {
                    SettingsFocusAnchor(active = highlightKey == "audio_output") {
                        WindowSpinnerPreference(
                            title = stringResource(R.string.settings_audio_output_backend),
                            summary = stringResource(
                                R.string.settings_current_value,
                                audioOutputBackendLabels[selectedAudioOutputBackendIndex]
                            ),
                            items = audioOutputBackendEntries,
                            selectedIndex = selectedAudioOutputBackendIndex,
                            onSelectedIndexChange = { index ->
                                scope.launch {
                                    settingsManager.setAudioOutputBackend(audioOutputBackendValues[index])
                                }
                            }
                        )
                    }
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_audio_output_bit_depth),
                        summary = stringResource(
                            R.string.settings_current_value,
                            audioOutputBitDepthLabels[selectedAudioOutputBitDepthIndex]
                        ),
                        items = audioOutputBitDepthEntries,
                        selectedIndex = selectedAudioOutputBitDepthIndex,
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                settingsManager.setAudioOutputBitDepth(audioOutputBitDepthValues[index])
                            }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_audio_output_sample_rate),
                        summary = stringResource(
                            R.string.settings_current_value,
                            audioOutputSampleRateLabels[selectedAudioOutputSampleRateIndex]
                        ),
                        items = audioOutputSampleRateEntries,
                        selectedIndex = selectedAudioOutputSampleRateIndex,
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                settingsManager.setAudioOutputSampleRate(audioOutputSampleRateValues[index])
                            }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_usb_dac_mode),
                        summary = connectedUsbDevice?.let {
                            stringResource(R.string.settings_usb_dac_connected, it.productName ?: "USB DAC")
                        } ?: stringResource(R.string.settings_usb_dac_mode_summary),
                        checked = usbDacMode,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setUsbDacMode(enabled) }
                            if (enabled) usbAudioController.requestUsbAudioPermission()
                        }
                    )
                }
            }

            SmallTitle(text = stringResource(R.string.settings_playback_section))

            SettingsCardGroup(highlight = highlightKey == "audio_playback") {
                Column {
                    SettingsFocusAnchor(active = highlightKey == "audio_playback") {
                        SwitchPreference(
                            title = stringResource(R.string.settings_gapless_playback),
                            summary = stringResource(R.string.settings_gapless_playback_summary),
                            checked = gaplessPlayback,
                            onCheckedChange = {
                                scope.launch { settingsManager.setGaplessPlayback(it) }
                            }
                        )
                    }
                    ArrowPreference(
                        title = stringResource(R.string.settings_crossfade),
                        summary = stringResource(
                            R.string.settings_crossfade_summary_with_value,
                            stringResource(
                                R.string.settings_crossfade_value,
                                crossfadeDurationMs / 1_000f
                            )
                        ),
                        onClick = {
                            crossfadeDurationInput = String.format(
                                java.util.Locale.ROOT,
                                "%.2f",
                                crossfadeDurationMs / 1_000.0
                            )
                            showCrossfadeDurationDialog = true
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_crossfade_curve),
                        summary = stringResource(
                            R.string.settings_current_value,
                            crossfadeCurveLabels[selectedCrossfadeCurve]
                        ),
                        items = crossfadeCurveEntries,
                        selectedIndex = selectedCrossfadeCurve,
                        enabled = crossfadeDurationMs > 0,
                        onSelectedIndexChange = { curve ->
                            scope.launch { settingsManager.setCrossfadeCurve(curve) }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_replay_gain),
                        summary = stringResource(R.string.settings_current_value, replayGainLabels[selectedReplayGainMode]),
                        items = replayGainEntries,
                        selectedIndex = selectedReplayGainMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setReplayGainMode(index) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_resume_playback_position),
                        summary = stringResource(R.string.settings_resume_playback_position_summary),
                        checked = resumePlaybackPosition,
                        onCheckedChange = {
                            scope.launch { settingsManager.setResumePlaybackPosition(it) }
                            playerViewModel?.setResumePlaybackPositionEnabled(it)
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_startup_play),
                        summary = stringResource(R.string.settings_current_value, startupPlayLabels[selectedStartupPlayMode]),
                        items = startupPlayEntries,
                        selectedIndex = selectedStartupPlayMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setStartupPlayMode(index) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_bluetooth_auto_play),
                        summary = stringResource(R.string.settings_bluetooth_auto_play_summary),
                        checked = bluetoothAutoPlay,
                        onCheckedChange = {
                            if (!it) {
                                scope.launch { settingsManager.setBluetoothAutoPlay(false) }
                            } else if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                !BluetoothAutoPlayReceiver.hasBluetoothConnectPermission(context)
                            ) {
                                bluetoothAutoPlayPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            } else {
                                scope.launch { settingsManager.setBluetoothAutoPlay(true) }
                            }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_shuffle_mode),
                        summary = stringResource(R.string.settings_current_value, shuffleModeLabels[selectedShuffleMode]),
                        items = shuffleModeEntries,
                        selectedIndex = selectedShuffleMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setShuffleMode(index) }
                            playerViewModel?.setShuffleMode(index)
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_play_next_mode),
                        summary = stringResource(R.string.settings_current_value, playNextModeLabels[selectedPlayNextMode]),
                        items = playNextModeEntries,
                        selectedIndex = selectedPlayNextMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setPlayNextMode(index) }
                            playerViewModel?.setPlayNextMode(index)
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_previous_button),
                        summary = stringResource(R.string.settings_current_value, previousButtonLabels[selectedPreviousButtonAction]),
                        items = previousButtonEntries,
                        selectedIndex = selectedPreviousButtonAction,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setPreviousButtonAction(index) }
                            playerViewModel?.setPreviousButtonAction(index)
                        }
                    )
                }
            }

            SmallTitle(text = stringResource(R.string.settings_system_section))

            SettingsCardGroup(highlight = highlightKey == "audio_system") {
                Column {
                    SettingsFocusAnchor(active = highlightKey == "audio_system") {
                        SwitchPreference(
                            title = stringResource(R.string.settings_disable_audio_focus),
                            summary = stringResource(R.string.settings_disable_audio_focus_summary),
                            checked = audioFocusDisabled,
                            onCheckedChange = {
                                scope.launch { settingsManager.setAudioFocusDisabled(it) }
                            }
                        )
                    }
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_decoder),
                        summary = stringResource(R.string.settings_current_value, decoderLabels[selectedDecoderMode]),
                        items = decoderEntries,
                        selectedIndex = selectedDecoderMode,
                        onSelectedIndexChange = { index ->
                            playerViewModel?.setDecoderMode(index)
                                ?: scope.launch { settingsManager.setDecoderMode(index) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }

    EllaMiuixDialog(
        show = showCrossfadeDurationDialog,
        title = stringResource(R.string.settings_crossfade_duration),
        summary = stringResource(R.string.settings_crossfade_duration_summary),
        onDismissRequest = { showCrossfadeDurationDialog = false }
    ) {
        Column {
            EllaMiuixTextField(
                value = crossfadeDurationInput,
                onValueChange = { value ->
                    crossfadeDurationInput = value.filter { character ->
                        character.isDigit() || character == '.' || character == ','
                    }
                },
                label = stringResource(R.string.settings_crossfade_duration_seconds),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            EllaMiuixDialogActions(
                cancelText = stringResource(R.string.common_cancel),
                confirmText = stringResource(R.string.common_save),
                onCancel = { showCrossfadeDurationDialog = false },
                onConfirm = {
                    val normalizedInput = crossfadeDurationInput.trim().replace(',', '.')
                    val seconds = normalizedInput.toDoubleOrNull()
                    val hasValidPrecision = normalizedInput.matches(
                        Regex("""\d{1,2}(?:\.\d{0,2})?""")
                    )
                    if (!hasValidPrecision || seconds == null || seconds !in 0.0..12.0) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_crossfade_duration_invalid),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val durationMs = ((seconds * 100.0).roundToInt() * 10)
                            .coerceIn(0, 12_000)
                        scope.launch { settingsManager.setCrossfadeDurationMs(durationMs) }
                        showCrossfadeDurationDialog = false
                    }
                },
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
