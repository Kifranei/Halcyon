package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaSmallTopAppBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.ella.music.ui.components.EllaSpinnerPreference

@Composable
internal fun ExperimentalSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    val crossfadeEnabled by settingsManager.crossfadeEnabled.collectAsState(initial = false)
    val crossfadeDurationMs by settingsManager.crossfadeDurationMs.collectAsState(initial = 0)
    val crossfadeCurve by settingsManager.crossfadeCurve.collectAsState(
        initial = SettingsManager.CROSSFADE_CURVE_EQUAL_POWER
    )
    var showCrossfadeDurationDialog by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            EllaSmallTopAppBar(
                title = stringResource(R.string.settings_experimental),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsCardGroup {
                SettingsSearchAnchor(R.string.settings_crossfade) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_crossfade),
                        summary = stringResource(R.string.settings_crossfade_summary),
                        checked = crossfadeEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setCrossfadeEnabled(enabled) }
                        }
                    )
                }

                if (crossfadeEnabled || SettingsSearchFocus.reveals(
                        R.string.settings_crossfade_duration,
                        R.string.settings_crossfade_curve
                    )
                ) {
                    Column {
                        SettingsSearchAnchor(R.string.settings_crossfade_duration) {
                            SettingsIntSliderPreference(
                                title = stringResource(R.string.settings_crossfade_duration),
                                summary = "",
                                value = (crossfadeDurationMs / 10).coerceIn(1, 1_200),
                                valueRange = 1..1_200,
                                valueText = stringResource(
                                    R.string.settings_crossfade_value,
                                    crossfadeDurationMs / 1_000f
                                ),
                                steps = 0,
                                showKeyPoints = false,
                                onClick = { showCrossfadeDurationDialog = true },
                                holdDownState = showCrossfadeDurationDialog,
                                onValueChange = { centiseconds ->
                                    scope.launch {
                                        settingsManager.setCrossfadeDurationMs(
                                            (centiseconds * 10).coerceIn(10, 12_000)
                                        )
                                    }
                                }
                            )
                        }

                        SettingsSearchAnchor(R.string.settings_crossfade_curve) {
                            EllaSpinnerPreference(
                                title = stringResource(R.string.settings_crossfade_curve),
                                summary = stringResource(R.string.settings_crossfade_curve_summary),
                                items = crossfadeCurveEntries,
                                selectedIndex = selectedCrossfadeCurve,
                                onSelectedIndexChange = { curve ->
                                    scope.launch { settingsManager.setCrossfadeCurve(curve) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    SettingsSecondsInputDialog(
        show = showCrossfadeDurationDialog,
        title = stringResource(R.string.settings_crossfade_duration),
        summary = stringResource(R.string.settings_duration_input_range, 0.01f, 12f),
        valueMs = crossfadeDurationMs.coerceIn(10, 12_000),
        minMs = 10,
        maxMs = 12_000,
        onDismissRequest = { showCrossfadeDurationDialog = false },
        onSave = { durationMs ->
            scope.launch { settingsManager.setCrossfadeDurationMs(durationMs) }
        }
    )
}
