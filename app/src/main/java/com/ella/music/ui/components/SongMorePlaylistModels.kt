package com.ella.music.ui.components

import com.ella.music.R
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.playlist.applyPlaylistCustomOrder
import java.util.Locale

internal enum class AddPlaylistSortMode(val labelRes: Int) {
    Custom(R.string.playlist_sort_custom),
    UpdatedAt(R.string.playlist_sort_updated_at),
    CreatedAt(R.string.playlist_sort_created_at),
    Name(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count),
    Duration(R.string.playlist_sort_duration),
    CustomDesc(R.string.playlist_sort_custom_desc),
    UpdatedAtAsc(R.string.playlist_sort_updated_at),
    CreatedAtAsc(R.string.playlist_sort_created_at),
    NameDesc(R.string.playlist_sort_name),
    SongCountAsc(R.string.playlist_sort_song_count),
    DurationAsc(R.string.playlist_sort_duration);

    fun next(): AddPlaylistSortMode = entries[(ordinal + 1) % entries.size]
}

internal fun List<UserPlaylist>.sortedForAddToPlaylist(
    mode: AddPlaylistSortMode,
    customOrderIds: List<String> = emptyList()
): List<UserPlaylist> {
    val favorites = firstOrNull { it.id == FAVORITES_PLAYLIST_ID }
    val others = filterNot { it.id == FAVORITES_PLAYLIST_ID }
    val sortedOthers = when (mode) {
        AddPlaylistSortMode.Custom -> others.applyPlaylistCustomOrder(customOrderIds)
        AddPlaylistSortMode.CustomDesc -> others.applyPlaylistCustomOrder(customOrderIds).asReversed()
        AddPlaylistSortMode.UpdatedAt -> others.sortedWith(
            compareByDescending<UserPlaylist> { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.UpdatedAtAsc -> others.sortedWith(
            compareBy<UserPlaylist> { it.updatedAt }
                .thenBy { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.CreatedAt -> others.sortedWith(
            compareByDescending<UserPlaylist> { it.createdAt }
                .thenByDescending { it.updatedAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.CreatedAtAsc -> others.sortedWith(
            compareBy<UserPlaylist> { it.createdAt }
                .thenBy { it.updatedAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.Name -> others.sortedWith(
            compareBy<UserPlaylist> { it.name.lowercase(Locale.ROOT) }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.NameDesc -> others.sortedWith(
            compareByDescending<UserPlaylist> { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.updatedAt }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.SongCount -> others.sortedWith(
            compareByDescending<UserPlaylist> { it.songs.size }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.SongCountAsc -> others.sortedWith(
            compareBy<UserPlaylist> { it.songs.size }
                .thenBy { it.updatedAt }
                .thenBy { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.Duration -> others.sortedWith(
            compareByDescending<UserPlaylist> { playlist -> playlist.songs.sumOf { it.duration } }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.DurationAsc -> others.sortedWith(
            compareBy<UserPlaylist> { playlist -> playlist.songs.sumOf { it.duration } }
                .thenBy { it.updatedAt }
                .thenBy { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
    }
    return listOfNotNull(favorites) + sortedOthers
}
