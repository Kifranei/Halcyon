package com.ella.music.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.flow.first

object LibrarySortUiState {
    var librarySongSortIndex by mutableIntStateOf(0)
    var albumListSortIndex by mutableIntStateOf(0)
    var albumListFirstVisibleItemIndex by mutableIntStateOf(0)
    var albumListFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    val albumListScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    var artistListSortIndex by mutableIntStateOf(0)
    var artistListFirstVisibleItemIndex by mutableIntStateOf(0)
    var artistListFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    val artistListScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    var albumDetailSongSortIndex by mutableIntStateOf(0)
    var artistDetailSongSortIndex by mutableIntStateOf(0)
    var artistDetailAlbumSortIndex by mutableIntStateOf(0)
    var folderListSortIndex by mutableIntStateOf(0)
    var folderListFirstVisibleItemIndex by mutableIntStateOf(0)
    var folderListFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    val folderListScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    var folderDetailSongSortIndex by mutableIntStateOf(0)
    var pendingFolderDetailSongSortIndex by mutableStateOf<Int?>(null)
    var folderPlaylistListSortIndex by mutableIntStateOf(2)
    var pendingFolderPlaylistListSortIndex by mutableStateOf<Int?>(null)
    var playlistListSortIndex by mutableIntStateOf(2)
    var playlistCustomOrderIds by mutableStateOf<List<String>>(emptyList())
    var pendingPlaylistListSortIndex by mutableStateOf<Int?>(null)

    val metadataCategoryScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    val metadataCategoryDetailScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    private val metadataCategorySortIndices = mutableStateMapOf<String, Int>()
    private val metadataCategoryDetailSongSortIndices = mutableStateMapOf<String, Int>()
    private val metadataCategoryDetailAlbumSortIndices = mutableStateMapOf<String, Int>()

    /**
     * 从 DataStore 预热所有排序索引到进程级单例。
     *
     * 进程被系统杀掉重启后，单例会重置为默认值（如 folderListSortIndex=0、
     * playlistListSortIndex=2）。各页面的 `collectAsState(initial = LibrarySortUiState.xxx)`
     * 会先用这个默认值渲染，DataStore 异步 emit 存储值后再重排，表现为"排序乱跳"
     * （#126）或"不记忆上次排序"（#210）。#133 的"设置恢复默认"同理——OOM 触发进程
     * 重启后，所有设置单例回到默认值。
     *
     * 在 EllaApp.onCreate 异步调用本函数后，单例在首个 Composable 重组前就已是
     * 存储值，`collectAsState(initial = ...)` 的 initial 正确，不再闪默认值。
     */
    suspend fun warmUp(settingsManager: SettingsManager) {
        librarySongSortIndex = settingsManager.librarySongSortIndex.first()
        albumListSortIndex = settingsManager.albumListSortIndex.first()
        artistListSortIndex = settingsManager.artistListSortIndex.first()
        albumDetailSongSortIndex = settingsManager.albumDetailSongSortIndex.first()
        artistDetailSongSortIndex = settingsManager.artistDetailSongSortIndex.first()
        artistDetailAlbumSortIndex = settingsManager.artistDetailAlbumSortIndex.first()
        folderListSortIndex = settingsManager.folderListSortIndex.first()
        folderDetailSongSortIndex = settingsManager.folderDetailSongSortIndex.first()
        pendingFolderDetailSongSortIndex = null
        folderPlaylistListSortIndex = settingsManager.folderPlaylistListSortIndex.first()
        pendingFolderPlaylistListSortIndex = null
        playlistListSortIndex = settingsManager.playlistListSortIndex.first()
        playlistCustomOrderIds = settingsManager.playlistCustomOrder.first()
        pendingPlaylistListSortIndex = null
        metadataCategoryTypes.forEach { type ->
            metadataCategorySortIndices[type] = settingsManager.metadataCategorySortIndex(type).first()
            metadataCategoryDetailSongSortIndices[type] = settingsManager.metadataCategoryDetailSongSortIndex(type).first()
            metadataCategoryDetailAlbumSortIndices[type] = settingsManager.metadataCategoryDetailAlbumSortIndex(type).first()
        }
    }

    fun metadataCategorySortIndex(type: String): Int = metadataCategorySortIndices[type] ?: 0

    fun updateMetadataCategorySortIndex(type: String, index: Int) {
        metadataCategorySortIndices[type] = index.coerceAtLeast(0)
    }

    fun metadataCategoryDetailSongSortIndex(type: String): Int = metadataCategoryDetailSongSortIndices[type] ?: 0

    fun updateMetadataCategoryDetailSongSortIndex(type: String, index: Int) {
        metadataCategoryDetailSongSortIndices[type] = index.coerceAtLeast(0)
    }

    fun metadataCategoryDetailAlbumSortIndex(type: String): Int = metadataCategoryDetailAlbumSortIndices[type] ?: 0

    fun updateMetadataCategoryDetailAlbumSortIndex(type: String, index: Int) {
        metadataCategoryDetailAlbumSortIndices[type] = index.coerceAtLeast(0)
    }

    private val metadataCategoryTypes = listOf("folder", "genre", "year", "composer", "arranger", "lyricist")
}
