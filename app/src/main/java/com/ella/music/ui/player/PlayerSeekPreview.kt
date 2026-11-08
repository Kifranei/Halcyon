package com.ella.music.ui.player

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf

/**
 * #727: shared seek-preview channel for scrubs that originate outside the seek bar itself
 * (e.g. dragging horizontally on the opening blank lyric row). While set, surfaces report a
 * 0..1 preview fraction through the provided state so every seek bar shows the same scrub
 * preview text it renders for direct seek-bar drags; null clears the preview.
 *
 * Provided once by [PlayerScreen]. Seek bars read it as an override for their local
 * `previewProgress` state so a lyric-driven scrub looks identical to a seek-bar drag.
 */
internal val LocalPlayerSeekPreviewOverride =
    compositionLocalOf<MutableState<Float?>?> { null }
