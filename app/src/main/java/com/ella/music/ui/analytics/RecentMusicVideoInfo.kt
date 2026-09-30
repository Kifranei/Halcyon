package com.ella.music.ui.analytics

import android.net.Uri
import androidx.compose.runtime.Composable
import com.ella.music.data.model.Song
import com.ella.music.ui.player.DynamicCoverSource
import com.ella.music.ui.player.MusicVideoInfoDialog

@Composable
internal fun RecentMusicVideoInfo(song: Song, onDismiss: () -> Unit) {
    MusicVideoInfoDialog(DynamicCoverSource(Uri.parse(song.path), song.path), song.title, onDismiss)
}
