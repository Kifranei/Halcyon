package com.ella.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.ella.music.ui.navigation.Screen
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import java.util.WeakHashMap

internal const val MAIN_PAGES_ROUTE = "main_pages"

/** A composed neighbour is not yet an input destination. */
internal val LocalMainPageActive = compositionLocalOf { true }

/**
 * This page is the committed destination of a programmatic tab jump. Unlike
 * [LocalMainPageActive] it turns true as soon as the spring starts instead of after it
 * settles, so entry gestures like the search IME can fire during the animation (#756).
 */
internal val LocalMainPageEntryTarget = compositionLocalOf { false }

internal fun mainPageIdentity(route: String) = route.substringBefore('?').trimEnd('/')

@Stable
internal class MainPageState(initialRoute: String, initialSearchReturnRoute: String? = null) {
    var selectedRoute by mutableStateOf(initialRoute)
    var searchReturnRoute by mutableStateOf(initialSearchReturnRoute)
        private set
    var requestVersion by mutableIntStateOf(0)
        private set
    var routes: List<String> = emptyList()

    fun select(route: String): Boolean {
        val target = routes.firstOrNull { mainPageIdentity(it) == mainPageIdentity(route) } ?: return false
        settle(if (route.startsWith(Screen.LibrarySearch.baseRoute)) route else target)
        requestVersion++
        return true
    }

    fun settle(route: String) {
        if (route.isSearchRoute() && !selectedRoute.isSearchRoute()) searchReturnRoute = selectedRoute
        selectedRoute = route
    }

    fun exitSearch(): Boolean {
        if (!selectedRoute.isSearchRoute()) return false
        val target = routes.firstOrNull { mainPageIdentity(it) == searchReturnRoute?.let(::mainPageIdentity) }
            ?: Screen.Home.route
        if (!select(target)) {
            settle(target)
            requestVersion++
        }
        searchReturnRoute = null
        return true
    }
}

@Composable
internal fun rememberMainPageState(initialRoute: String): MainPageState = rememberSaveable(
    saver = Saver<MainPageState, Any>(save = { listOf(it.selectedRoute, it.searchReturnRoute.orEmpty()) },
        restore = { saved -> when (saved) {
            is String -> MainPageState(saved) // Saved by the previous app version.
            is List<*> -> (saved.getOrNull(0) as? String)?.let {
                MainPageState(it, (saved.getOrNull(1) as? String)?.ifBlank { null })
            }
            else -> null
        } })
) { MainPageState(initialRoute) }

internal object MainPageNavigation {
    private val controllers = WeakHashMap<NavHostController, MainPageState>()
    fun register(controller: NavHostController, state: MainPageState) { controllers[controller] = state }
    fun unregister(controller: NavHostController) { controllers.remove(controller) }
    fun exitSearch(controller: NavHostController): Boolean {
        if (controller.currentDestination?.route != MAIN_PAGES_ROUTE) return false
        val state = controllers[controller] ?: return false
        if (!state.exitSearch()) return false
        if (state.selectedRoute == Screen.Home.route && state.routes.none { it == Screen.Home.route }) select(controller, Screen.Home.route)
        return true
    }
    fun select(controller: NavHostController, route: String): Boolean {
        val state = controllers[controller] ?: return false
        if (!state.select(route)) {
            if (route != Screen.Home.route) return false
            // Home remains the root Back destination even when its dock button is hidden.
            state.settle(Screen.Home.route)
            controller.navigate(Screen.Home.route) {
                popUpTo(controller.graph.id) { inclusive = false }
                launchSingleTop = true
            }
            return true
        }
        if (controller.currentDestination?.route != MAIN_PAGES_ROUTE) {
            if (!controller.popBackStack(MAIN_PAGES_ROUTE, false)) {
                controller.navigate(MAIN_PAGES_ROUTE) { launchSingleTop = true }
            }
        }
        return true
    }
}

/** Tabs share one navigation entry; only detail screens push destinations onto the stack. */
@Composable
internal fun MainPagePager(
    state: MainPageState,
    routes: List<String>,
    swipeEnabled: Boolean,
    modifier: Modifier = Modifier,
    saveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder = rememberSaveableStateHolder(),
    onBackToHome: () -> Unit = { if (!state.exitSearch()) state.select(Screen.Home.route) },
    content: @Composable (String) -> Unit
) {
    if (routes.isEmpty()) return
    val selected = routes.indexOfFirst { mainPageIdentity(it) == mainPageIdentity(state.selectedRoute) }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = selected) { routes.size }
    // The holder is normally hoisted above the NavHost so a destination stacked by a
    // deeplink does not destroy every tab's saved scroll state when this page leaves
    // composition (#750).
    val holder = saveableStateHolder
    val latestRoutes by rememberUpdatedState(routes)
    var navigating by remember { mutableStateOf(false) }
    var settledRequestVersion by remember { mutableIntStateOf(-1) }
    val requestedVersion = state.requestVersion
    LaunchedEffect(requestedVersion, routes) {
        val destination = latestRoutes.indexOfFirst { mainPageIdentity(it) == mainPageIdentity(state.selectedRoute) }.coerceAtLeast(0)
        navigating = true
        try {
            // A far tab jump would sweep every intermediate page (#720). Pre-position next to
            // the target so the spring only crosses a single page edge.
            val distance = destination - pager.currentPage
            if (kotlin.math.abs(distance) > 1) {
                pager.scrollToPage(destination - distance / kotlin.math.abs(distance))
            }
            pager.springAnimateToPage(destination)
        } finally {
            settledRequestVersion = requestedVersion
            navigating = false
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { if (canSettleMainPage(pager.isScrollInProgress, navigating, state.requestVersion, settledRequestVersion)) pager.settledPage else null }
            .distinctUntilChanged().collect { page ->
                if (page != null) latestRoutes.getOrNull(page)?.let { route ->
                    if (mainPageIdentity(state.selectedRoute) != mainPageIdentity(route)) state.settle(route)
                }
            }
    }
    BackHandler(enabled = state.selectedRoute != Screen.Home.route) {
        onBackToHome()
    }
    HorizontalPager(state = pager, modifier = modifier.fillMaxSize(), key = { mainPageIdentity(routes[it]) },
        userScrollEnabled = swipeEnabled, beyondViewportPageCount = 0) { index ->
        val route = routes[index]
        holder.SaveableStateProvider(mainPageIdentity(route)) {
            val active = mainPageIdentity(route) == mainPageIdentity(state.selectedRoute)
            val readyForInput = active && index == pager.settledPage && !pager.isScrollInProgress && !navigating
            CompositionLocalProvider(
                LocalMainPageActive provides readyForInput,
                LocalMainPageEntryTarget provides (active && navigating && index == pager.targetPage)
            ) {
                MainPageBackScope(active) { content(if (active) state.selectedRoute else route) }
            }
        }
    }
}

internal fun canSettleMainPage(scrolling: Boolean, navigating: Boolean, requestedVersion: Int, settledVersion: Int): Boolean =
    !scrolling && !navigating && requestedVersion == settledVersion

/** Composed neighbouring pages keep their state, but cannot consume the visible page's Back. */
@Composable
internal fun MainPageBackScope(active: Boolean, content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    val owner = remember(parent) {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher(parent)
        }
    }
    SideEffect { owner.navigationEventDispatcher.isEnabled = active }
    DisposableEffect(owner) { onDispose { owner.navigationEventDispatcher.dispose() } }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner, content = content)
}
