package com.ella.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ella.music.data.BottomBarStyle
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Adapted from KnSQL's App.kt SectionRail and ui/Adaptive.kt wide-screen layout. */
internal fun useNavigationRail(
    style: BottomBarStyle,
    windowWidthDp: Float,
    windowHeightDp: Float,
    tablet: Boolean
): Boolean {
    if (windowWidthDp <= 0f || windowHeightDp <= 0f) return false
    // Landscape navigation follows the window, regardless of the portrait dock preference.
    if (windowWidthDp > windowHeightDp) return true
    return style == BottomBarStyle.Normal && windowWidthDp >= 600f &&
        (tablet || windowWidthDp >= 840f || windowHeightDp / windowWidthDp < 1.2f)
}

@Composable
internal fun MainNavigationRail(
    state: NavigationRailState,
    tabs: List<BottomDockTab>,
    currentTabRoute: String?,
    currentRoute: String?,
    mergeSearch: Boolean,
    onNavigate: (String) -> Unit,
    onNavigateSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        state = state,
        modifier = modifier,
        defaultWindowInsetsPadding = true,
        color = MiuixTheme.colorScheme.surface,
        expandContentDescription = stringResource(R.string.navigation_rail_expand),
        collapseContentDescription = stringResource(R.string.navigation_rail_collapse)
    ) {
        tabs.forEach { tab ->
            val selected = if (tab.route.isSearchRoute()) currentRoute.isSearchRoute()
                else !currentRoute.isSearchRoute() && currentTabRoute == tab.route
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        if (tab.route.isSearchRoute()) onNavigateSearch() else onNavigate(tab.route)
                    }
                },
                icon = tab.icon,
                label = tab.label
            )
        }
        if (!mergeSearch && tabs.none { it.route.isSearchRoute() }) {
            NavigationRailItem(
                selected = currentRoute.isSearchRoute(),
                onClick = { if (!currentRoute.isSearchRoute()) onNavigateSearch() },
                icon = MiuixIcons.Basic.Search,
                label = stringResource(R.string.common_search)
            )
        }
    }
}
