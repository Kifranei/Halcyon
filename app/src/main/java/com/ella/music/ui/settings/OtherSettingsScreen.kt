package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.GlassChevronBackwardIcon
import com.ella.music.ui.components.ellaPageBackground
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun OtherSettingsScreen(onBack: () -> Unit, onVideo: () -> Unit) {
    val pageBackground = ellaPageBackground()
    val settingsBackdrop = rememberLayerBackdrop()
    Scaffold(
        containerColor = pageBackground,
        topBar = {
            EllaSmallTopAppBar(
                backdrop = settingsBackdrop,
                title = stringResource(R.string.settings_other),
                color = pageBackground,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(GlassChevronBackwardIcon, stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().layerBackdrop(settingsBackdrop).padding(padding).padding(16.dp)) {
            SettingsCardGroup {
                ArrowPreference(
                    title = stringResource(R.string.video_tools_title),
                    summary = stringResource(R.string.video_tools_summary),
                    onClick = onVideo
                )
            }
        }
    }
}
