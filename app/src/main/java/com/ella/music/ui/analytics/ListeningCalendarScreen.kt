package com.ella.music.ui.analytics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.settings.SettingsIntSliderPreference
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import com.ella.music.ui.components.GlassChevronBackwardIcon
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ListeningCalendarHistoryScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val songs by mainViewModel.songs.collectAsState()
    val playbackHistory by mainViewModel.playbackHistory.collectAsState()
    val dailyListenMs by mainViewModel.dailyListenMs.collectAsState()
    val libraryById = remember(songs) { songs.associateBy { it.id } }
    val libraryByStatsKey = remember(songs) { songs.associateBy { it.calendarStatsKey() } }
    val dayAggregates = remember(playbackHistory, dailyListenMs, libraryById, libraryByStatsKey) {
        buildListeningDayAggregates(playbackHistory, dailyListenMs, libraryById, libraryByStatsKey)
    }
    val monthSections = remember(dayAggregates) { buildListeningMonths(dayAggregates) }
    val firstDayWithHistory = remember(dayAggregates) { dayAggregates.values.firstOrNull { it.entries.isNotEmpty() }?.dateKey }
    var selectedDateKey by remember(firstDayWithHistory) { mutableStateOf(firstDayWithHistory) }
    var selectedMonthIndex by remember(monthSections) { mutableIntStateOf(0) }
    val safeMonthIndex = selectedMonthIndex.coerceIn(0, (monthSections.size - 1).coerceAtLeast(0))
    val selectedMonth = monthSections.getOrNull(safeMonthIndex)

    fun selectMonth(index: Int) {
        val target = monthSections.getOrNull(index) ?: return
        selectedMonthIndex = index
        selectedDateKey = target.weeks
            .asSequence()
            .flatMap { it.asSequence() }
            .filterNotNull()
            .firstOrNull { it.entries.isNotEmpty() }
            ?.dateKey
            ?: target.weeks.asSequence().flatMap { it.asSequence() }.filterNotNull().firstOrNull()?.dateKey
    }
    val selectedDay = remember(selectedDateKey, dayAggregates) {
        selectedDateKey?.let(dayAggregates::get)
    }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var pendingRemoveEntry by remember { mutableStateOf<PlaybackHistoryEntry?>(null) }
    val scope = rememberCoroutineScope()
    // #698: gear opens the "音乐回忆-听歌历史" card's display-count setting.
    val settings = com.ella.music.data.SettingsManager.getInstance(LocalContext.current)
    val historyCardCount by settings.analyticsHistoryCardCount.collectAsState(initial = 20)
    var showCardSettings by remember { mutableStateOf(false) }
    // #781: one shared ordering pref drives this day's list (default earliest-first) and
    // the memories 听歌历史 card (default latest-first) once the user picks a direction.
    val historySortMode by settings.analyticsHistorySortMode.collectAsState(
        initial = com.ella.music.data.SettingsManager.ANALYTICS_HISTORY_SORT_DEFAULT
    )
    val dayEntriesAscending = historySortMode != com.ella.music.data.SettingsManager.ANALYTICS_HISTORY_SORT_DESC
    val orderedEntries = remember(selectedDay, dayEntriesAscending) {
        val entries = selectedDay?.entries.orEmpty()
        if (dayEntriesAscending) entries else entries.asReversed()
    }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = GlassChevronBackwardIcon,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                Text(
                    text = stringResource(R.string.listening_calendar_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground
                )
                Text(
                    text = if (playbackHistory.isEmpty()) {
                        stringResource(R.string.listening_calendar_empty_day)
                    } else {
                        stringResource(R.string.listening_calendar_records_total, playbackHistory.size)
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
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

        if (dayAggregates.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.listening_calendar_empty_day),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item("month-calendar:${selectedMonth?.label.orEmpty()}") {
                    selectedMonth?.let { month ->
                        ListeningMonthCard(
                            month = month,
                            selectedDateKey = selectedDateKey,
                            onDayClick = { dateKey ->
                                if (dayAggregates[dateKey]?.entries?.isNotEmpty() == true) {
                                    selectedDateKey = dateKey
                                }
                            },
                            onPreviousMonth = { selectMonth(safeMonthIndex + 1) },
                            onNextMonth = { selectMonth(safeMonthIndex - 1) },
                            canGoPrevious = safeMonthIndex < monthSections.lastIndex,
                            canGoNext = safeMonthIndex > 0
                        )
                    }
                }
                // The calendar is the primary surface of this page (#637). The selected day's
                // summary and timeline follow it so tapping a heat cell immediately reveals the
                // records for that date underneath, instead of making the calendar feel like a
                // footer.
                item("selected-day:${selectedDay?.dateKey.orEmpty()}") {
                    ListeningDayDetailSection(
                        day = selectedDay,
                        mainViewModel = mainViewModel,
                        playerViewModel = playerViewModel,
                        sortAscending = dayEntriesAscending,
                        onToggleSort = {
                            scope.launch {
                                settings.setAnalyticsHistorySortMode(
                                    if (dayEntriesAscending) {
                                        com.ella.music.data.SettingsManager.ANALYTICS_HISTORY_SORT_DESC
                                    } else {
                                        com.ella.music.data.SettingsManager.ANALYTICS_HISTORY_SORT_ASC
                                    }
                                )
                            }
                        }
                    )
                }
                itemsIndexed(
                    items = orderedEntries,
                    key = { _, timelineEntry -> "history:${timelineEntry.entry.entryId}" }
                ) { index, timelineEntry ->
                    ListeningTimelineRow(
                        entry = timelineEntry,
                        isLast = index == orderedEntries.lastIndex,
                        mainViewModel = mainViewModel,
                        playerViewModel = playerViewModel,
                        onSongMore = { song -> actionSong = song },
                        onRemoveHistoryEntry = { entry -> pendingRemoveEntry = entry },
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
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
    }

    SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = { actionSong = null },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist
    )

    pendingRemoveEntry?.let { entry ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.listening_calendar_remove_history_title),
            message = stringResource(R.string.listening_calendar_remove_history_message),
            onDismiss = { pendingRemoveEntry = null },
            onConfirm = {
                val toRemove = entry
                pendingRemoveEntry = null
                scope.launch { mainViewModel.removePlaybackHistoryEntry(toRemove) }
            }
        )
    }

    EllaMiuixBottomSheet(
        show = showCardSettings,
        title = stringResource(R.string.analytics_card_settings),
        onDismissRequest = { showCardSettings = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            SettingsIntSliderPreference(
                title = stringResource(R.string.analytics_history_card_count),
                summary = "",
                value = historyCardCount,
                valueRange = 3..30,
                valueText = historyCardCount.toString(),
                steps = 26,
                showKeyPoints = false,
                onValueChange = { value ->
                    scope.launch { settings.setAnalyticsHistoryCardCount(value) }
                }
            )
        }
    }
}
