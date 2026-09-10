package com.ella.music.ui.home

import com.ella.music.ui.components.EllaMiuixBottomSheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ExplicitSongTitle
import com.ella.music.viewmodel.MainViewModel
import android.widget.Toast
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import com.ella.music.ui.components.EllaMiuixActionMenuGroup
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SongAiInterpretationMenu(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val openAiApiKey by settingsManager.openAiApiKey.collectAsState(initial = "")
    val aiFailedText = stringResource(R.string.song_more_ai_failed)
    var requestKey by remember(song.id) { mutableStateOf(0) }
    var isLoading by remember(song.id) { mutableStateOf(false) }
    var resultText by remember(song.id) { mutableStateOf("") }
    var errorText by remember(song.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(song.id, requestKey, openAiApiKey) {
        if (openAiApiKey.isBlank()) {
            Toast.makeText(context, R.string.library_ai_missing_api_key, Toast.LENGTH_SHORT).show()
            onDismiss()
            return@LaunchedEffect
        }
        isLoading = true
        errorText = null
        resultText = ""
        runCatching {
            mainViewModel.interpretSongWithOpenAi(song)
        }.onSuccess {
            resultText = it
        }.onFailure {
            errorText = it.message ?: aiFailedText
        }
        isLoading = false
    }

    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.song_more_ai_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExplicitSongTitle(
                title = song.title,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            when {
                isLoading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                    ) {
                        SongInfoRow(
                            stringResource(R.string.library_status_label),
                            stringResource(R.string.library_ai_loading)
                        )
                    }
                }
                errorText != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                    ) {
                        SongInfoRow(stringResource(R.string.library_status_label), errorText.orEmpty())
                    }
                    EllaMiuixActionMenuGroup {
                        LibraryMenuItem(stringResource(R.string.library_retry), onClick = { requestKey++ })
                    }
                }
                resultText.isNotBlank() -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                    ) {
                        Text(
                            text = resultText,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                    EllaMiuixActionMenuGroup {
                        LibraryMenuItem(stringResource(R.string.library_reinterpret), onClick = { requestKey++ })
                    }
                }
            }

            EllaMiuixActionMenuGroup {
                LibraryMenuItem(stringResource(R.string.common_close), onDismiss)
            }
        }
    }
}
