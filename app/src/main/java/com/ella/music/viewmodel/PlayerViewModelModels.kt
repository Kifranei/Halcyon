package com.ella.music.viewmodel

import com.ella.music.data.model.Song

internal data class LazyOnlineQueue(
    val songs: List<Song>,
    var index: Int,
    val resolver: suspend (Song) -> Song
)

internal enum class AbRepeatPhase {
    IDLE,
    A_SET,
    ACTIVE
}

internal data class AbRepeatState(
    val phase: AbRepeatPhase = AbRepeatPhase.IDLE,
    val songKey: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null
)

internal fun Song.lyricIdentityKey(): String {
    return when {
        onlineSource.isNotBlank() || onlineId.isNotBlank() -> "online:$onlineSource:$onlineId:$path"
        path.isNotBlank() -> "path:$path:$id:$dateModified"
        else -> "id:$id"
    }
}
