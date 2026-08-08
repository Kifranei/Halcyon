package com.ella.music.data.model

import org.json.JSONArray
import org.json.JSONObject

data class FolderPlaylist(
    val id: String,
    val name: String,
    val folders: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    /** Stable song identity keys in the user's manual order. */
    val songOrder: List<String> = emptyList(),
    /** Normalized folder paths in the user's manual order. */
    val folderOrder: List<String> = emptyList(),
    /** Folder cards hidden from the detail view without removing their songs. */
    val hiddenFolders: List<String> = emptyList()
)

fun List<FolderPlaylist>.toFolderPlaylistJson(): String =
    JSONArray().also { array ->
        forEach { playlist ->
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("createdAt", playlist.createdAt)
                    .put("updatedAt", playlist.updatedAt)
                    .put("folders", JSONArray().also { folders ->
                        playlist.folders.forEach { folders.put(it) }
                    })
                    .put("songOrder", JSONArray().also { order ->
                        playlist.songOrder.forEach { order.put(it) }
                    })
                    .put("folderOrder", JSONArray().also { order ->
                        playlist.folderOrder.forEach { order.put(it) }
                    })
                    .put("hiddenFolders", JSONArray().also { hidden ->
                        playlist.hiddenFolders.forEach { hidden.put(it) }
                    })
            )
        }
    }.toString()

fun String.toFolderPlaylists(): List<FolderPlaylist> =
    runCatching {
        val array = JSONArray(this)
        List(array.length()) { index ->
            val json = array.optJSONObject(index) ?: return@List null
            val folders = json.optJSONArray("folders")
            val songOrder = json.optJSONArray("songOrder")
            val folderOrder = json.optJSONArray("folderOrder")
            val hiddenFolders = json.optJSONArray("hiddenFolders")
            FolderPlaylist(
                id = json.optString("id").trim(),
                name = json.optString("name").trim(),
                folders = List(folders?.length() ?: 0) { folderIndex ->
                    folders?.optString(folderIndex).orEmpty().trim()
                }.filter { it.isNotBlank() }.distinctBy { it.lowercase() },
                createdAt = json.optLong("createdAt"),
                updatedAt = json.optLong("updatedAt"),
                songOrder = List(songOrder?.length() ?: 0) { orderIndex ->
                    songOrder?.optString(orderIndex).orEmpty().trim()
                }.filter(String::isNotBlank).distinct(),
                folderOrder = List(folderOrder?.length() ?: 0) { orderIndex ->
                    folderOrder?.optString(orderIndex).orEmpty().trim()
                }.filter(String::isNotBlank).distinctBy { it.lowercase() },
                hiddenFolders = List(hiddenFolders?.length() ?: 0) { orderIndex ->
                    hiddenFolders?.optString(orderIndex).orEmpty().trim()
                }.filter(String::isNotBlank).distinctBy { it.lowercase() }
            )
        }.filterNotNull()
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.folders.isNotEmpty() }
    }.getOrDefault(emptyList())
