package com.ella.music.ui.player

/** Lyric pages always sit on the upper Apple Music anchor. */
internal fun resolveLyricPageFocusOffsetRatio(upperAlignmentRatio: Float): Float =
    upperAlignmentRatio.coerceIn(0f, 1f)
