package com.ella.music.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppearanceSubpageScreen(
    page: String,
    onBack: () -> Unit,
    highlightKey: String? = null,
    onNavigateToBottomNavigationSettings: () -> Unit = {}
) {
    val pageBackground = ellaPageBackground()
    val title = when (page) {
        APPEARANCE_PAGE_SYSTEM_BARS -> stringResource(R.string.settings_appearance_system_bars_page)
        APPEARANCE_PAGE_WALLPAPER -> stringResource(R.string.settings_appearance_wallpaper_page)
        APPEARANCE_PAGE_PLAYER -> stringResource(R.string.settings_appearance_player_page)
        APPEARANCE_PAGE_LIST -> stringResource(R.string.settings_appearance_list_page)
        else -> stringResource(R.string.settings_appearance_theme_page)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = title,
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
                .verticalScroll(rememberSettingsScrollState("appearance_$page"))
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsAppearanceSection(
                highlightKey = highlightKey,
                page = page,
                onNavigateToBottomNavigationSettings = onNavigateToBottomNavigationSettings
            )
            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}
