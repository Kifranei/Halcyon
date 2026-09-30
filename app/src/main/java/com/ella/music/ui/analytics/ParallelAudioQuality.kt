package com.ella.music.ui.analytics

import com.ella.music.data.model.Song
import com.ella.music.data.model.AudioInfo
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

internal suspend fun loadLibraryQualityRows(songs: List<Song>, load: (Song) -> AudioInfo): List<SongWithInfo> = coroutineScope {
    val next = AtomicInteger()
    val rows = arrayOfNulls<SongWithInfo>(songs.size)
    List(minOf(4, songs.size)) {
        async(Dispatchers.IO) {
            while (true) {
                ensureActive()
                val index = next.getAndIncrement()
                if (index >= songs.size) break
                rows[index] = SongWithInfo(songs[index], load(songs[index]))
            }
        }
    }.awaitAll()
    rows.map { requireNotNull(it) }
}
