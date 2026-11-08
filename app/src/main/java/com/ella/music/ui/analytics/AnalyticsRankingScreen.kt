package com.ella.music.ui.analytics

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaGlassListPopup
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaCheckOptionGroup
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.settings.SettingsIntSliderPreference
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import com.ella.music.ui.components.GlassChevronBackwardIcon
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Rows appended per scroll step on the standalone ranking page (#781). */
private const val RANKING_PAGE_SIZE = 50

/** 最短播放时长 filter choices, in seconds; 0 disables the filter (#781). */
private val RANKING_MIN_DURATION_OPTIONS_SEC = listOf(0, 10, 30, 60, 180, 300)

/** Standalone rankings page reached from the memories ranking cards' header arrows (#698). */
@Composable
internal fun AnalyticsRankingScreen(
    kind: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settings = SettingsManager.getInstance(context)
    val scope = rememberCoroutineScope()
    val songs by mainViewModel.songs.collectAsState()
    val playbackHistory by mainViewModel.playbackHistory.collectAsState()
    // The page-local range defaults to the card's configured range (#698).
    val cardRange by settings.analyticsRankingRange(kind).collectAsState(initial = "all")
    val cardCount by settings.analyticsRankingCount(kind).collectAsState(initial = 10)
    var pageRange by rememberSaveable { mutableStateOf<String?>(null) }
    val range = AnalyticsRankingRange.fromPref(pageRange ?: cardRange)
    // #781: drop entries whose played duration is shorter than the chosen minimum.
    var minDurationSec by rememberSaveable { mutableIntStateOf(0) }
    var minDurationMenuVisible by remember { mutableStateOf(false) }
    val filteredHistory = remember(playbackHistory, minDurationSec) {
        if (minDurationSec <= 0) playbackHistory
        else playbackHistory.filter { it.listenedMs >= minDurationSec * 1_000L }
    }
    val stats = remember(filteredHistory, range, kind) {
        buildRangeRankingStats(filteredHistory, range, kind)
    }
    // #781: history entries may reference songs that were retagged, rescanned or deleted;
    // resolve them back to a library song so the rows stay playable/identified.
    val historySongResolver = remember(songs) { HistorySongResolver(songs) }
    val statsSongMap = remember(filteredHistory, historySongResolver) {
        buildStatsSongMap(filteredHistory, historySongResolver)
    }
    // #781: compose a first page only; appending all rows at once janked "全部时间".
    var visibleCount by remember(stats) { mutableIntStateOf(RANKING_PAGE_SIZE) }
    val visibleStats = remember(stats, visibleCount) { stats.take(visibleCount) }
    val listState = rememberLazyListState()
    var showCardSettings by remember { mutableStateOf(false) }
    val titleRes = if (kind == "count") {
        R.string.analytics_play_count_ranking
    } else {
        R.string.analytics_listen_duration_ranking
    }
    fun minDurationLabel(seconds: Int): String = when {
        seconds <= 0 -> context.getString(R.string.analytics_duration_filter_all)
        seconds < 60 -> context.getString(R.string.settings_seconds_value, seconds)
        else -> context.getString(R.string.analytics_duration_minutes, seconds / 60)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = GlassChevronBackwardIcon,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = stringResource(titleRes),
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f)
            )
            Box {
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    minDurationMenuVisible = true
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Filter,
                        contentDescription = stringResource(R.string.analytics_min_play_duration),
                        tint = if (minDurationSec > 0) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                EllaGlassListPopup(
                    show = minDurationMenuVisible,
                    onDismissRequest = { minDurationMenuVisible = false }
                ) {
                    RANKING_MIN_DURATION_OPTIONS_SEC.forEach { optionSec ->
                        GlassPopupItem(
                            text = minDurationLabel(optionSec),
                            selected = minDurationSec == optionSec,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                minDurationSec = optionSec
                                minDurationMenuVisible = false
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { showCardSettings = true }) {
                Icon(
                    imageVector = MiuixIcons.Regular.Settings,
                    contentDescription = stringResource(R.string.analytics_card_settings),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(AnalyticsRankingRange.entries) { _, option ->
                val selected = option == range
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surface
                        )
                        .clickable { pageRange = option.prefValue }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        fontSize = 13.sp,
                        color = if (selected) MiuixTheme.colorScheme.onPrimary
                        else MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 40.dp)
            ) {
                item("ranking-rows") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = analyticsWallpaperCardColors()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (stats.isEmpty()) {
                                Text(
                                    text = stringResource(
                                        if (kind == "count") R.string.analytics_play_count_empty
                                        else R.string.analytics_listen_duration_empty
                                    ),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            } else {
                                visibleStats.forEachIndexed { index, stat ->
                                    RankingRow(
                                        index = index + 1,
                                        stat = stat,
                                        value = if (kind == "count") {
                                            context.getString(R.string.analytics_times_count, stat.playCount)
                                        } else {
                                            formatListenDuration(context, stat.listenedMs)
                                        },
                                        song = statsSongMap[stat.analyticsStatsKey()]
                                            ?: historySongResolver.resolve(stat),
                                        mainViewModel = mainViewModel,
                                        onClick = { song: Song -> playerViewModel.playSong(song) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (stats.size > visibleStats.size) {
                    item("load-more") {
                        // Auto-append when the tail scrolls into view; the label doubles as
                        // a manual "load more" for accessibility.
                        LaunchedEffect(Unit) {
                            visibleCount = (visibleCount + RANKING_PAGE_SIZE).coerceAtMost(stats.size)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    visibleCount = (visibleCount + RANKING_PAGE_SIZE).coerceAtMost(stats.size)
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.analytics_ranking_load_more,
                                    stats.size - visibleStats.size
                                ),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
            LazyListScrollIndicator(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }

    EllaMiuixBottomSheet(
        show = showCardSettings,
        title = stringResource(R.string.analytics_card_settings),
        onDismissRequest = { showCardSettings = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.analytics_card_range),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            EllaCheckOptionGroup(
                options = AnalyticsRankingRange.entries.map { it.prefValue to stringResource(it.labelRes) },
                selected = AnalyticsRankingRange.fromPref(cardRange).prefValue,
                onSelect = { value ->
                    scope.launch { settings.setAnalyticsRankingRange(kind, value) }
                }
            )
            SettingsIntSliderPreference(
                title = stringResource(R.string.analytics_card_count),
                summary = "",
                value = cardCount,
                valueRange = 3..30,
                valueText = cardCount.toString(),
                steps = 26,
                showKeyPoints = false,
                onValueChange = { value ->
                    scope.launch { settings.setAnalyticsRankingCount(kind, value) }
                }
            )
        }
    }
}
