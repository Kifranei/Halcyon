package com.ella.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.player.AudioEffectSettings
import com.ella.music.player.AudioEffectState
import com.ella.music.ui.components.EllaSmallTopAppBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.VerticalSlider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)

    val capabilities by AudioEffectState.capabilities.collectAsState()
    val eqEnabled by settingsManager.eqEnabled.collectAsState(initial = false)
    val eqPreset by settingsManager.eqPreset.collectAsState(initial = AudioEffectSettings.PRESET_CUSTOM)
    val bandLevels by settingsManager.eqBandLevelsMb.collectAsState(initial = emptyList())
    val eqQ by settingsManager.eqQ.collectAsState(initial = AudioEffectSettings.EQ_Q_DEFAULT)
    val toneBassDb by settingsManager.toneBassDb.collectAsState(initial = 0)
    val toneTrebleDb by settingsManager.toneTrebleDb.collectAsState(initial = 0)
    val compressorEnabled by settingsManager.compressorEnabled.collectAsState(initial = false)
    val compressorThresholdDb by settingsManager.compressorThresholdDb.collectAsState(initial = -18)
    val compressorRatio by settingsManager.compressorRatio.collectAsState(initial = 2)
    val compressorMakeupDb by settingsManager.compressorMakeupDb.collectAsState(initial = 0)
    val stereoWidth by settingsManager.stereoWidth.collectAsState(initial = 100)
    val surround360Enabled by settingsManager.surround360Enabled.collectAsState(initial = false)
    val surround360Intensity by settingsManager.surround360Intensity.collectAsState(initial = 50)
    val surround360RotationSpeed by settingsManager.surround360RotationSpeed.collectAsState(initial = 30)
    val panoramic360Enabled by settingsManager.panoramic360Enabled.collectAsState(initial = false)
    val panoramic360Intensity by settingsManager.panoramic360Intensity.collectAsState(initial = 50)
    val panoramic360AzimuthDegrees by settingsManager.panoramic360AzimuthDegrees.collectAsState(initial = 0)
    val panoramic360ElevationDegrees by settingsManager.panoramic360ElevationDegrees.collectAsState(initial = 0)
    val loudnessBalanceEnabled by settingsManager.loudnessBalanceEnabled.collectAsState(initial = false)
    val loudnessPercent by settingsManager.loudnessPercent.collectAsState(initial = 35)
    val channelBalance by settingsManager.channelBalance.collectAsState(initial = 0)
    val crossfeedEnabled by settingsManager.crossfeedEnabled.collectAsState(initial = false)
    val crossfeedLowCutHz by settingsManager.crossfeedLowCutHz.collectAsState(initial = 300)
    val crossfeedHighCutHz by settingsManager.crossfeedHighCutHz.collectAsState(initial = 2_000)
    val crossfeedAttenuationTenthsDb by settingsManager.crossfeedAttenuationTenthsDb.collectAsState(initial = 60)
    val monoBassEnabled by settingsManager.monoBassEnabled.collectAsState(initial = false)
    val monoBassCrossoverHz by settingsManager.monoBassCrossoverHz.collectAsState(initial = 120)
    val monoBassAmount by settingsManager.monoBassAmount.collectAsState(initial = 100)
    val speakerOutputEnabled by settingsManager.speakerOutputEnabled.collectAsState(initial = false)
    val speakerOutputMode by settingsManager.speakerOutputMode.collectAsState(initial = AudioEffectSettings.SPEAKER_OUTPUT_MODE_ELASTICITY)
    val speakerOutputStrength by settingsManager.speakerOutputStrength.collectAsState(initial = 82)
    val dynamicEqEnabled by settingsManager.dynamicEqEnabled.collectAsState(initial = false)
    val dynamicEqIntensity by settingsManager.dynamicEqIntensity.collectAsState(initial = 50)
    val deEsserAmount by settingsManager.deEsserAmount.collectAsState(initial = 45)
    val deEsserFrequencyHz by settingsManager.deEsserFrequencyHz.collectAsState(initial = 6_500)
    val moogLadderEnabled by settingsManager.moogLadderEnabled.collectAsState(initial = false)
    val moogLadderMode by settingsManager.moogLadderMode.collectAsState(initial = AudioEffectSettings.MOOG_LADDER_MODE_LOW_PASS_24)
    val moogLadderCutoffHz by settingsManager.moogLadderCutoffHz.collectAsState(initial = 12_000)
    val moogLadderResonance by settingsManager.moogLadderResonance.collectAsState(initial = 20)
    val moogLadderDriveDb by settingsManager.moogLadderDriveDb.collectAsState(initial = 0)
    val moogLadderMix by settingsManager.moogLadderMix.collectAsState(initial = 100)
    val peakLimiterEnabled by settingsManager.peakLimiterEnabled.collectAsState(initial = true)
    val platformSpatialRequested by settingsManager.platformSpatialAudioEnabled.collectAsState(initial = false)
    val platformSpatialSnapshot = remember(platformSpatialRequested, surround360Enabled, panoramic360Enabled) {
        com.ella.music.player.AndroidSpatialAudio.snapshot(context)
    }
    val reverbPreset by settingsManager.reverbPreset.collectAsState(initial = AudioEffectSettings.REVERB_PRESET_OFF)

    val accent = MiuixTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.equalizer_screen_title),
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

            val caps = capabilities
            if (caps == null) {
                SettingsCardGroup(highlight = highlightKey == "equalizer_unavailable") {
                    Text(
                        text = stringResource(R.string.equalizer_unavailable),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(160.dp))
                return@Column
            }

            if (!caps.supported) {
                SettingsCardGroup(highlight = highlightKey == "equalizer_unavailable") {
                    Text(
                        text = stringResource(R.string.equalizer_unavailable),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(160.dp))
                return@Column
            } else {
                SmallTitle(text = stringResource(R.string.equalizer_section_eq))
                SettingsCardGroup(highlight = highlightKey == "equalizer") {
                    Column {
                        SettingsFocusAnchor(active = highlightKey == "equalizer") {
                            SwitchPreference(
                                title = stringResource(R.string.equalizer_master),
                                summary = stringResource(R.string.equalizer_band_count, caps.displayBandCount),
                                checked = eqEnabled,
                                onCheckedChange = { scope.launch { settingsManager.setEqEnabled(it) } }
                            )
                        }

                        val presetNames = eqPresetDisplayNames()
                        val presetItems = buildList {
                            add(DropdownItem(title = stringResource(R.string.equalizer_preset_custom)))
                            presetNames.forEachIndexed { index, name ->
                                if (index in caps.presetBandLevelsMb.indices) add(DropdownItem(title = name))
                            }
                        }
                        val selectedPresetIndex = if (eqPreset in caps.presetBandLevelsMb.indices) eqPreset + 1 else 0
                        WindowSpinnerPreference(
                            title = stringResource(R.string.equalizer_preset),
                            items = presetItems,
                            selectedIndex = selectedPresetIndex,
                            onSelectedIndexChange = { index ->
                                scope.launch {
                                    if (index <= 0) {
                                        settingsManager.setEqPreset(AudioEffectSettings.PRESET_CUSTOM)
                                    } else {
                                        val presetIndex = index - 1
                                        val levels = caps.presetBandLevelsMb.getOrNull(presetIndex)
                                            ?: List(caps.displayBandCount) { 0 }
                                        settingsManager.setEqPresetWithBands(presetIndex, levels.toDisplayBandLevels(caps))
                                    }
                                }
                            }
                        )
                    }
                }

                SettingsCardGroup(highlight = highlightKey == "equalizer_bands") {
                    SettingsFocusAnchor(active = highlightKey == "equalizer_bands") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (band in 0 until caps.displayBandCount) {
                                val levelMb = bandLevels.getOrElse(band) { 0 }
                                val freqHz = caps.displayCenterFreqsHz.getOrElse(band) { 0 }
                                EqBandColumn(
                                    freqLabel = formatFreq(freqHz),
                                    gainLabel = formatGainDb(levelMb),
                                    levelMb = levelMb,
                                    minMb = caps.minLevelMb,
                                    maxMb = caps.maxLevelMb,
                                    onLevelChange = { newLevel ->
                                        val updated = MutableList(caps.displayBandCount) { idx -> bandLevels.getOrElse(idx) { 0 } }
                                        updated[band] = newLevel.coerceIn(caps.minLevelMb, caps.maxLevelMb)
                                        scope.launch { settingsManager.setEqBandLevelsMb(updated) }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.equalizer_reset),
                    color = accent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 8.dp, top = 2.dp, bottom = 6.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                scope.launch { settingsManager.setEqBandLevelsMb(List(caps.displayBandCount) { 0 }) }
                            }
                        }
                )

                SmallTitle(text = stringResource(R.string.equalizer_section_parametric))
                SettingsCardGroup {
                    EqControlSlider(
                        title = stringResource(R.string.equalizer_eq_q),
                        valueText = String.format(Locale.ROOT, "%.1f", eqQ / 100f),
                        value = eqQ,
                        range = AudioEffectSettings.EQ_Q_MIN..AudioEffectSettings.EQ_Q_MAX,
                        onChange = { scope.launch { settingsManager.setEqQ(it) } }
                    )
                }
                SectionResetLink(accent) {
                    scope.launch { settingsManager.setEqQ(AudioEffectSettings.EQ_Q_DEFAULT) }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_tone))
                SettingsCardGroup {
                    Column {
                        EqControlSlider(
                            title = stringResource(R.string.equalizer_tone_bass),
                            valueText = formatGainDbInt(toneBassDb),
                            value = toneBassDb,
                            range = AudioEffectSettings.TONE_GAIN_MIN_DB..AudioEffectSettings.TONE_GAIN_MAX_DB,
                            onChange = { scope.launch { settingsManager.setToneBassDb(it) } }
                        )
                        EqControlSlider(
                            title = stringResource(R.string.equalizer_tone_treble),
                            valueText = formatGainDbInt(toneTrebleDb),
                            value = toneTrebleDb,
                            range = AudioEffectSettings.TONE_GAIN_MIN_DB..AudioEffectSettings.TONE_GAIN_MAX_DB,
                            onChange = { scope.launch { settingsManager.setToneTrebleDb(it) } }
                        )
                    }
                }
                SectionResetLink(accent) {
                    scope.launch {
                        settingsManager.setToneBassDb(0)
                        settingsManager.setToneTrebleDb(0)
                    }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_compressor))
                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.equalizer_compressor_enable),
                            checked = compressorEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setCompressorEnabled(it) } }
                        )
                        if (compressorEnabled) {
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_compressor_threshold),
                                valueText = "$compressorThresholdDb dB",
                                value = compressorThresholdDb,
                                range = AudioEffectSettings.COMP_THRESHOLD_MIN_DB..AudioEffectSettings.COMP_THRESHOLD_MAX_DB,
                                onChange = { scope.launch { settingsManager.setCompressorThresholdDb(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_compressor_ratio),
                                valueText = "$compressorRatio:1",
                                value = compressorRatio,
                                range = AudioEffectSettings.COMP_RATIO_MIN..AudioEffectSettings.COMP_RATIO_MAX,
                                onChange = { scope.launch { settingsManager.setCompressorRatio(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_compressor_makeup),
                                valueText = "+$compressorMakeupDb dB",
                                value = compressorMakeupDb,
                                range = AudioEffectSettings.COMP_MAKEUP_MIN_DB..AudioEffectSettings.COMP_MAKEUP_MAX_DB,
                                onChange = { scope.launch { settingsManager.setCompressorMakeupDb(it) } }
                            )
                        }
                    }
                }
                SectionResetLink(accent) {
                    scope.launch {
                        settingsManager.setCompressorEnabled(false)
                        settingsManager.setCompressorThresholdDb(-18)
                        settingsManager.setCompressorRatio(2)
                        settingsManager.setCompressorMakeupDb(0)
                    }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_dynamic_eq))
                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.equalizer_dynamic_eq_enable),
                            summary = stringResource(R.string.equalizer_dynamic_eq_summary),
                            checked = dynamicEqEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setDynamicEqEnabled(it) } }
                        )
                        if (dynamicEqEnabled) {
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_dynamic_eq_intensity),
                                valueText = "$dynamicEqIntensity%",
                                value = dynamicEqIntensity,
                                range = AudioEffectSettings.DYNAMIC_EQ_PERCENT_MIN..AudioEffectSettings.DYNAMIC_EQ_PERCENT_MAX,
                                onChange = { scope.launch { settingsManager.setDynamicEqIntensity(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_deesser_amount),
                                valueText = "$deEsserAmount%",
                                value = deEsserAmount,
                                range = AudioEffectSettings.DYNAMIC_EQ_PERCENT_MIN..AudioEffectSettings.DYNAMIC_EQ_PERCENT_MAX,
                                onChange = { scope.launch { settingsManager.setDeEsserAmount(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_deesser_frequency),
                                valueText = "$deEsserFrequencyHz Hz",
                                value = deEsserFrequencyHz,
                                range = AudioEffectSettings.DE_ESSER_FREQUENCY_MIN_HZ..AudioEffectSettings.DE_ESSER_FREQUENCY_MAX_HZ,
                                onChange = { scope.launch { settingsManager.setDeEsserFrequencyHz(it) } }
                            )
                        }
                    }
                }
                SectionResetLink(accent) {
                    scope.launch {
                        settingsManager.setDynamicEqEnabled(false)
                        settingsManager.setDynamicEqIntensity(50)
                        settingsManager.setDeEsserAmount(45)
                        settingsManager.setDeEsserFrequencyHz(6_500)
                    }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_moog_ladder))
                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.equalizer_moog_ladder_enable),
                            summary = stringResource(R.string.equalizer_moog_ladder_summary),
                            checked = moogLadderEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setMoogLadderEnabled(it) } }
                        )
                        if (moogLadderEnabled) {
                            WindowSpinnerPreference(
                                title = stringResource(R.string.equalizer_moog_ladder_mode),
                                items = moogLadderModeEntries(),
                                selectedIndex = moogLadderMode,
                                onSelectedIndexChange = { scope.launch { settingsManager.setMoogLadderMode(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_moog_ladder_cutoff),
                                valueText = "$moogLadderCutoffHz Hz",
                                value = moogLadderCutoffHz,
                                range = AudioEffectSettings.MOOG_LADDER_CUTOFF_MIN_HZ..AudioEffectSettings.MOOG_LADDER_CUTOFF_MAX_HZ,
                                onChange = { scope.launch { settingsManager.setMoogLadderCutoffHz(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_moog_ladder_resonance),
                                valueText = "$moogLadderResonance%",
                                value = moogLadderResonance,
                                range = AudioEffectSettings.MOOG_LADDER_RESONANCE_MIN..AudioEffectSettings.MOOG_LADDER_RESONANCE_MAX,
                                onChange = { scope.launch { settingsManager.setMoogLadderResonance(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_moog_ladder_drive),
                                valueText = "$moogLadderDriveDb dB",
                                value = moogLadderDriveDb,
                                range = AudioEffectSettings.MOOG_LADDER_DRIVE_MIN_DB..AudioEffectSettings.MOOG_LADDER_DRIVE_MAX_DB,
                                onChange = { scope.launch { settingsManager.setMoogLadderDriveDb(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_moog_ladder_mix),
                                valueText = "$moogLadderMix%",
                                value = moogLadderMix,
                                range = AudioEffectSettings.MOOG_LADDER_MIX_MIN..AudioEffectSettings.MOOG_LADDER_MIX_MAX,
                                onChange = { scope.launch { settingsManager.setMoogLadderMix(it) } }
                            )
                        }
                    }
                }
                SectionResetLink(accent) {
                    scope.launch {
                        settingsManager.setMoogLadderEnabled(false)
                        settingsManager.setMoogLadderMode(AudioEffectSettings.MOOG_LADDER_MODE_LOW_PASS_24)
                        settingsManager.setMoogLadderCutoffHz(12_000)
                        settingsManager.setMoogLadderResonance(20)
                        settingsManager.setMoogLadderDriveDb(0)
                        settingsManager.setMoogLadderMix(100)
                    }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_peak_limiter))
                SettingsCardGroup {
                    SwitchPreference(
                        title = stringResource(R.string.equalizer_peak_limiter_enable),
                        summary = stringResource(R.string.equalizer_peak_limiter_summary),
                        checked = peakLimiterEnabled,
                        onCheckedChange = { scope.launch { settingsManager.setPeakLimiterEnabled(it) } }
                    )
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_stereo))
                SettingsCardGroup {
                    EqControlSlider(
                        title = stringResource(R.string.equalizer_stereo_width),
                        valueText = "$stereoWidth%",
                        value = stereoWidth,
                        range = AudioEffectSettings.STEREO_WIDTH_MIN..AudioEffectSettings.STEREO_WIDTH_MAX,
                        onChange = { scope.launch { settingsManager.setStereoWidth(it) } }
                    )
                }
                SectionResetLink(accent) {
                    scope.launch { settingsManager.setStereoWidth(100) }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_surround_360))
                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.equalizer_surround_360_enable),
                            summary = stringResource(R.string.equalizer_surround_360_summary),
                            checked = surround360Enabled,
                            onCheckedChange = { scope.launch { settingsManager.setSurround360Enabled(it) } }
                        )
                        if (surround360Enabled) {
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_surround_360_intensity),
                                valueText = "$surround360Intensity%",
                                value = surround360Intensity,
                                range = AudioEffectSettings.SURROUND_360_INTENSITY_MIN..AudioEffectSettings.SURROUND_360_INTENSITY_MAX,
                                onChange = { scope.launch { settingsManager.setSurround360Intensity(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_surround_360_rotation),
                                valueText = "$surround360RotationSpeed deg/s",
                                value = surround360RotationSpeed,
                                range = AudioEffectSettings.SURROUND_360_ROTATION_MIN..AudioEffectSettings.SURROUND_360_ROTATION_MAX,
                                onChange = { scope.launch { settingsManager.setSurround360RotationSpeed(it) } }
                            )
                        }
                    }
                }
                SectionResetLink(accent) {
                    scope.launch {
                        settingsManager.setSurround360Enabled(false)
                        settingsManager.setSurround360Intensity(50)
                        settingsManager.setSurround360RotationSpeed(30)
                    }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_panoramic_360))
                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.equalizer_panoramic_360_enable),
                            summary = stringResource(R.string.equalizer_panoramic_360_summary),
                            checked = panoramic360Enabled,
                            onCheckedChange = { scope.launch { settingsManager.setPanoramic360Enabled(it) } }
                        )
                        if (panoramic360Enabled) {
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_panoramic_360_intensity),
                                valueText = "$panoramic360Intensity%",
                                value = panoramic360Intensity,
                                range = AudioEffectSettings.PANORAMIC_360_INTENSITY_MIN..AudioEffectSettings.PANORAMIC_360_INTENSITY_MAX,
                                onChange = { scope.launch { settingsManager.setPanoramic360Intensity(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_panoramic_360_azimuth),
                                valueText = "$panoramic360AzimuthDegrees deg",
                                value = panoramic360AzimuthDegrees,
                                range = AudioEffectSettings.PANORAMIC_360_AZIMUTH_MIN..AudioEffectSettings.PANORAMIC_360_AZIMUTH_MAX,
                                onChange = { scope.launch { settingsManager.setPanoramic360AzimuthDegrees(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_panoramic_360_elevation),
                                valueText = "$panoramic360ElevationDegrees deg",
                                value = panoramic360ElevationDegrees,
                                range = AudioEffectSettings.PANORAMIC_360_ELEVATION_MIN..AudioEffectSettings.PANORAMIC_360_ELEVATION_MAX,
                                onChange = { scope.launch { settingsManager.setPanoramic360ElevationDegrees(it) } }
                            )
                        }
                    }
                }
                SectionResetLink(accent) {
                    scope.launch {
                        settingsManager.setPanoramic360Enabled(false)
                        settingsManager.setPanoramic360Intensity(50)
                        settingsManager.setPanoramic360AzimuthDegrees(0)
                        settingsManager.setPanoramic360ElevationDegrees(0)
                    }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_platform_spatial))
                SettingsCardGroup {
                    SwitchPreference(
                        title = stringResource(R.string.equalizer_platform_spatial_enable),
                        summary = when {
                            surround360Enabled || panoramic360Enabled -> stringResource(R.string.equalizer_platform_spatial_custom_active)
                            platformSpatialSnapshot.usable -> stringResource(R.string.equalizer_platform_spatial_available)
                            else -> stringResource(R.string.equalizer_platform_spatial_unavailable)
                        },
                        checked = platformSpatialRequested,
                        enabled = !surround360Enabled && !panoramic360Enabled && platformSpatialSnapshot.apiSupported,
                        onCheckedChange = { scope.launch { settingsManager.setPlatformSpatialAudioEnabled(it) } }
                    )
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_loudness))
                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.equalizer_loudness_enable),
                            summary = stringResource(R.string.equalizer_loudness_summary),
                            checked = loudnessBalanceEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setLoudnessBalanceEnabled(it) } }
                        )
                        if (loudnessBalanceEnabled) {
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_loudness_amount),
                                valueText = "$loudnessPercent%",
                                value = loudnessPercent,
                                range = AudioEffectSettings.LOUDNESS_PERCENT_MIN..AudioEffectSettings.LOUDNESS_PERCENT_MAX,
                                onChange = { scope.launch { settingsManager.setLoudnessPercent(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_channel_balance),
                                valueText = channelBalanceLabel(channelBalance),
                                value = channelBalance,
                                range = AudioEffectSettings.CHANNEL_BALANCE_MIN..AudioEffectSettings.CHANNEL_BALANCE_MAX,
                                onChange = { scope.launch { settingsManager.setChannelBalance(it) } }
                            )
                        }
                    }
                }
                SectionResetLink(accent) {
                    scope.launch {
                        settingsManager.setLoudnessBalanceEnabled(false)
                        settingsManager.setLoudnessPercent(35)
                        settingsManager.setChannelBalance(0)
                    }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_crossfeed))
                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.equalizer_crossfeed_enable),
                            summary = stringResource(R.string.equalizer_crossfeed_summary),
                            checked = crossfeedEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setCrossfeedEnabled(it) } }
                        )
                        if (crossfeedEnabled) {
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_crossfeed_low_cut),
                                valueText = "$crossfeedLowCutHz Hz",
                                value = crossfeedLowCutHz,
                                range = AudioEffectSettings.CROSSFEED_LOW_CUT_MIN_HZ..AudioEffectSettings.CROSSFEED_LOW_CUT_MAX_HZ,
                                onChange = { scope.launch { settingsManager.setCrossfeedLowCutHz(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_crossfeed_high_cut),
                                valueText = "$crossfeedHighCutHz Hz",
                                value = crossfeedHighCutHz,
                                range = AudioEffectSettings.CROSSFEED_HIGH_CUT_MIN_HZ..AudioEffectSettings.CROSSFEED_HIGH_CUT_MAX_HZ,
                                onChange = { scope.launch { settingsManager.setCrossfeedHighCutHz(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_crossfeed_attenuation),
                                valueText = String.format(Locale.ROOT, "%.1f dB", crossfeedAttenuationTenthsDb / 10f),
                                value = crossfeedAttenuationTenthsDb,
                                range = AudioEffectSettings.CROSSFEED_ATTENUATION_MIN_TENTHS_DB..AudioEffectSettings.CROSSFEED_ATTENUATION_MAX_TENTHS_DB,
                                onChange = { scope.launch { settingsManager.setCrossfeedAttenuationTenthsDb(it) } }
                            )
                        }
                    }
                }
                SectionResetLink(accent) {
                    scope.launch {
                        settingsManager.setCrossfeedEnabled(false)
                        settingsManager.setCrossfeedLowCutHz(300)
                        settingsManager.setCrossfeedHighCutHz(2_000)
                        settingsManager.setCrossfeedAttenuationTenthsDb(60)
                    }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_mono_bass))
                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.equalizer_mono_bass_enable),
                            summary = stringResource(R.string.equalizer_mono_bass_summary),
                            checked = monoBassEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setMonoBassEnabled(it) } }
                        )
                        if (monoBassEnabled) {
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_mono_bass_crossover),
                                valueText = "$monoBassCrossoverHz Hz",
                                value = monoBassCrossoverHz,
                                range = AudioEffectSettings.MONO_BASS_CROSSOVER_MIN_HZ..AudioEffectSettings.MONO_BASS_CROSSOVER_MAX_HZ,
                                onChange = { scope.launch { settingsManager.setMonoBassCrossoverHz(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_mono_bass_amount),
                                valueText = "$monoBassAmount%",
                                value = monoBassAmount,
                                range = AudioEffectSettings.MONO_BASS_AMOUNT_MIN..AudioEffectSettings.MONO_BASS_AMOUNT_MAX,
                                onChange = { scope.launch { settingsManager.setMonoBassAmount(it) } }
                            )
                        }
                    }
                }
                SectionResetLink(accent) {
                    scope.launch {
                        settingsManager.setMonoBassEnabled(false)
                        settingsManager.setMonoBassCrossoverHz(120)
                        settingsManager.setMonoBassAmount(100)
                    }
                }

                SmallTitle(text = stringResource(R.string.equalizer_section_speaker))
                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.equalizer_speaker_enable),
                            summary = stringResource(R.string.equalizer_speaker_summary),
                            checked = speakerOutputEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setSpeakerOutputEnabled(it) } }
                        )
                        if (speakerOutputEnabled) {
                            WindowSpinnerPreference(
                                title = stringResource(R.string.equalizer_speaker_mode),
                                items = speakerOutputModeEntries(),
                                selectedIndex = speakerOutputMode,
                                onSelectedIndexChange = { scope.launch { settingsManager.setSpeakerOutputMode(it) } }
                            )
                            EqControlSlider(
                                title = stringResource(R.string.equalizer_speaker_strength),
                                valueText = "$speakerOutputStrength%",
                                value = speakerOutputStrength,
                                range = AudioEffectSettings.SPEAKER_OUTPUT_STRENGTH_MIN..AudioEffectSettings.SPEAKER_OUTPUT_STRENGTH_MAX,
                                onChange = { scope.launch { settingsManager.setSpeakerOutputStrength(it) } }
                            )
                        }
                    }
                }
                SectionResetLink(accent) {
                    scope.launch {
                        settingsManager.setSpeakerOutputEnabled(false)
                        settingsManager.setSpeakerOutputMode(AudioEffectSettings.SPEAKER_OUTPUT_MODE_ELASTICITY)
                        settingsManager.setSpeakerOutputStrength(82)
                    }
                }

                SmallTitle(text = stringResource(R.string.equalizer_reverb))
                SettingsCardGroup {
                    val reverbEntries = reverbPresetEntries()
                    val selectedReverbIndex = reverbEntries
                        .indexOfFirst { it.first == reverbPreset }
                        .coerceAtLeast(0)
                    WindowSpinnerPreference(
                        title = stringResource(R.string.equalizer_reverb),
                        items = reverbEntries.map { DropdownItem(title = it.second) },
                        selectedIndex = selectedReverbIndex,
                        onSelectedIndexChange = { index ->
                            reverbEntries.getOrNull(index)?.let { entry ->
                                scope.launch { settingsManager.setReverbPreset(entry.first) }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
private fun EqBandColumn(
    freqLabel: String,
    gainLabel: String,
    levelMb: Int,
    minMb: Int,
    maxMb: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = freqLabel,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        VerticalSlider(
            value = levelMb.toFloat().coerceIn(minMb.toFloat(), maxMb.toFloat()),
            onValueChange = { onLevelChange(it.roundToInt().coerceIn(minMb, maxMb)) },
            valueRange = minMb.toFloat()..maxMb.toFloat(),
            width = 18.dp,
            modifier = Modifier.height(180.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = gainLabel,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EqControlSlider(
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
            Text(
                text = valueText,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onChange(it.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

@Composable
private fun ColumnScope.SectionResetLink(accent: Color, onReset: () -> Unit) {
    Text(
        text = stringResource(R.string.equalizer_reset_section),
        color = accent,
        fontSize = 13.sp,
        modifier = Modifier
            .align(Alignment.End)
            .padding(end = 8.dp, top = 2.dp, bottom = 6.dp)
            .pointerInput(Unit) {
                detectTapGestures { onReset() }
            }
    )
}

/** Localized graphic-EQ preset names, aligned with FIXED_EQ_PRESET_BAND_LEVELS_MB order. */
@Composable
private fun eqPresetDisplayNames(): List<String> = listOf(
    stringResource(R.string.equalizer_preset_rock),
    stringResource(R.string.equalizer_preset_pop),
    stringResource(R.string.equalizer_preset_jazz),
    stringResource(R.string.equalizer_preset_classical),
    stringResource(R.string.equalizer_preset_dance),
    stringResource(R.string.equalizer_preset_electronic),
    stringResource(R.string.equalizer_preset_hiphop),
    stringResource(R.string.equalizer_preset_vocal),
    stringResource(R.string.equalizer_preset_acoustic),
    stringResource(R.string.equalizer_preset_bass_boost),
    stringResource(R.string.equalizer_preset_treble_boost)
)

/** Reverb presets in display order, paired with their AudioEffectSettings.REVERB_PRESET_* id. */
@Composable
private fun reverbPresetEntries(): List<Pair<Int, String>> = listOf(
    AudioEffectSettings.REVERB_PRESET_OFF to stringResource(R.string.equalizer_reverb_off),
    AudioEffectSettings.REVERB_PRESET_STUDIO to stringResource(R.string.equalizer_reverb_studio),
    AudioEffectSettings.REVERB_PRESET_SMALL_ROOM to stringResource(R.string.equalizer_reverb_small_room),
    AudioEffectSettings.REVERB_PRESET_MEDIUM_ROOM to stringResource(R.string.equalizer_reverb_medium_room),
    AudioEffectSettings.REVERB_PRESET_LARGE_ROOM to stringResource(R.string.equalizer_reverb_large_room),
    AudioEffectSettings.REVERB_PRESET_HALL to stringResource(R.string.equalizer_reverb_hall),
    AudioEffectSettings.REVERB_PRESET_CHURCH to stringResource(R.string.equalizer_reverb_church),
    AudioEffectSettings.REVERB_PRESET_PLATE to stringResource(R.string.equalizer_reverb_plate)
)

private fun formatGainDbInt(db: Int): String = if (db > 0) "+$db dB" else "$db dB"

@Composable
private fun channelBalanceLabel(balance: Int): String = when {
    balance < 0 -> stringResource(R.string.equalizer_channel_balance_left, -balance)
    balance > 0 -> stringResource(R.string.equalizer_channel_balance_right, balance)
    else -> stringResource(R.string.equalizer_channel_balance_center)
}

@Composable
private fun speakerOutputModeEntries(): List<DropdownItem> = listOf(
    DropdownItem(title = stringResource(R.string.equalizer_speaker_mode_elasticity)),
    DropdownItem(title = stringResource(R.string.equalizer_speaker_mode_powerful)),
    DropdownItem(title = stringResource(R.string.equalizer_speaker_mode_wide))
)

@Composable
private fun moogLadderModeEntries(): List<DropdownItem> = listOf(
    DropdownItem(title = stringResource(R.string.equalizer_moog_ladder_mode_low_pass_24)),
    DropdownItem(title = stringResource(R.string.equalizer_moog_ladder_mode_low_pass_12)),
    DropdownItem(title = stringResource(R.string.equalizer_moog_ladder_mode_high_pass_24)),
    DropdownItem(title = stringResource(R.string.equalizer_moog_ladder_mode_band_pass_12)),
    DropdownItem(title = stringResource(R.string.equalizer_moog_ladder_mode_notch))
)

private fun List<Int>.toDisplayBandLevels(caps: com.ella.music.player.EqualizerCapabilities): List<Int> {
    if (size == caps.displayBandCount) return this
    if (isEmpty()) return List(caps.displayBandCount) { 0 }
    return caps.displayCenterFreqsHz.map { displayFreq ->
        val sourceIndex = caps.centerFreqsHz.nearestBandIndex(displayFreq).takeIf { it >= 0 } ?: 0
        getOrElse(sourceIndex) { 0 }
    }
}

private fun List<Int>.nearestBandIndex(freqHz: Int): Int {
    if (isEmpty()) return -1
    var bestIndex = 0
    var bestDistance = Float.MAX_VALUE
    forEachIndexed { index, center ->
        val safeCenter = center.coerceAtLeast(1)
        val safeFreq = freqHz.coerceAtLeast(1)
        val distance = kotlin.math.abs(kotlin.math.ln(safeFreq.toFloat() / safeCenter.toFloat()))
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) "%.1fk".format(hz / 1000f) else hz.toString()

private fun formatGainDb(levelMb: Int): String {
    val db = levelMb / 100f
    return "%.1f".format(db)
}
