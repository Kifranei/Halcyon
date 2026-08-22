package com.ella.music.player

internal object OPlusLyricPublishPolicy {
    const val COMPAT_REAPPLY_DELAY_MS = 800L
    const val INITIAL_PREPARE_TIMEOUT_MS = 1_500L
    val COMPAT_REAPPLY_DELAYS_MS = longArrayOf(COMPAT_REAPPLY_DELAY_MS)

    fun actionFor(
        currentLyricInfo: String?,
        currentRawLyric: String?,
        targetLyricInfo: String?,
        targetRawLyric: String?,
        force: Boolean = false
    ): OPlusLyricPublishAction {
        return if (targetLyricInfo.isNullOrBlank()) {
            if (currentLyricInfo != null || currentRawLyric != null) {
                OPlusLyricPublishAction.Clear
            } else {
                OPlusLyricPublishAction.None
            }
        } else if (!force && currentLyricInfo == targetLyricInfo && currentRawLyric == targetRawLyric) {
            OPlusLyricPublishAction.None
        } else {
            OPlusLyricPublishAction.Write
        }
    }

    /**
     * Resolves the lyricInfo JSON that the first MediaSession metadata snapshot for [songKey]
     * should carry. Overlay wins when it already belongs to this song; otherwise a cache hit is
     * used so a track change does not publish `hasLyric=false` before the handler catches up.
     */
    fun presentationJson(
        songKey: String,
        overlaySongKey: String?,
        overlayJson: String?,
        cachedJson: String?
    ): String? {
        if (overlaySongKey == songKey) {
            overlayJson?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return cachedJson?.takeIf { it.isNotBlank() }
    }

    fun shouldKeepSongIdentityMetadata(colorOsLockScreenLyricEnabled: Boolean): Boolean =
        colorOsLockScreenLyricEnabled
}

internal enum class OPlusLyricPublishAction {
    None,
    Clear,
    Write
}
