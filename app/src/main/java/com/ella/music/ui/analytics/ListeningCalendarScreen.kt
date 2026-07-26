package com.ella.music.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
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
    val selectedDay = remember(selectedDateKey, dayAggregates) {
        selectedDateKey?.let(dayAggregates::get)
    }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var pendingRemoveEntry by remember { mutableStateOf<PlaybackHistoryEntry?>(null) }
    val scope = rememberCoroutineScope()

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
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item("selected-day") {
                    ListeningDayDetailSection(
                        day = selectedDay,
                        mainViewModel = mainViewModel,
                        playerViewModel = playerViewModel,
                        onSongMore = { song -> actionSong = song },
                        onRemoveHistoryEntry = { entry -> pendingRemoveEntry = entry }
                    )
                }
                items(monthSections, key = { it.label }) { month ->
                    ListeningMonthCard(
                        month = month,
                        selectedDateKey = selectedDateKey,
                        onDayClick = { dateKey ->
                            if (dayAggregates[dateKey]?.entries?.isNotEmpty() == true) {
                                selectedDateKey = dateKey
                            }
                        }
                    )
                }
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
}
