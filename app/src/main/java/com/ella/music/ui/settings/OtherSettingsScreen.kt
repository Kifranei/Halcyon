package com.ella.music.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun OtherSettingsScreen(onBack: () -> Unit, onVideo: () -> Unit) {
    Scaffold(topBar={ EllaSmallTopAppBar(title=stringResource(R.string.settings_other),navigationIcon={
        IconButton(onClick=onBack) { Icon(MiuixIcons.Regular.Back,stringResource(R.string.common_back)) }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            SettingsCardGroup {
                ArrowPreference(title=stringResource(R.string.video_tools_title),summary=stringResource(R.string.video_tools_summary),onClick=onVideo)
            }
        }
    }
}
