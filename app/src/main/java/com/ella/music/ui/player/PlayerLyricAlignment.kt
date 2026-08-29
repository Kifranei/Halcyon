package com.ella.music.ui.player

/**
 * Keeps the player lyric page anchored near the top of the viewport.
 *
 * The old center option is intentionally ignored here as well as being hidden from settings, so
 * users who had it saved before the option was removed are migrated without a visual jump back to
 * the retired layout.
 */
internal fun resolveLyricPageFocusOffsetRatio(
    _alignment: Int,
    upperAlignmentRatio: Float
): Float = upperAlignmentRatio.coerceIn(0f, 1f)
