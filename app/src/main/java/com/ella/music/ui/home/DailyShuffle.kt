package com.ella.music.ui.home

import com.ella.music.data.model.Song
import kotlin.random.Random

internal const val DAILY_SHUFFLE_MIN_SONGS = 30
internal const val DAILY_SHUFFLE_MAX_SONGS = 50

/**
 * One random playlist for [epochDay]. The same library and day always produce the same list.
 * Libraries smaller than [DAILY_SHUFFLE_MIN_SONGS] play in a day-stable shuffle of everything.
 */
internal fun dailyShufflePlaylist(songs: List<Song>, epochDay: Long): List<Song> {
    if (songs.isEmpty()) return emptyList()
    val random = Random(epochDay)
    val ordered = songs.sortedBy { it.id }
    if (ordered.size <= DAILY_SHUFFLE_MIN_SONGS) return ordered.shuffled(random)
    val upper = minOf(DAILY_SHUFFLE_MAX_SONGS, ordered.size)
    val count = random.nextInt(DAILY_SHUFFLE_MIN_SONGS, upper + 1)
    return ordered.shuffled(random).take(count)
}
