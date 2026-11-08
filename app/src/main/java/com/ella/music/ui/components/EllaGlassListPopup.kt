package com.ella.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.glass.GlassDropdownPopupContent
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.utils.WindowNavigationEventScope

/**
 * OS4 glass dropdown popup. Hosts [GlassDropdownPopupContent] inside a platform [Popup]
 * positioned at the window origin; the parent layout's bounds are captured by the
 * position provider, matching the anchor semantics [WindowListPopup] had.
 *
 * Stays composed while the exit animation runs; [onDismissFinished] reporting keeps the
 * window alive until the glass panel has fully retracted.
 */
@Composable
internal fun EllaGlassListPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchorBoundsOverride: Rect? = null,
    maxHeight: Dp = GlassPopupDefaults.MaxHeight,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var capturedAnchor by remember { mutableStateOf(Rect.Zero) }
    val anchorBounds = anchorBoundsOverride ?: capturedAnchor
    val latestOnDismissFinished by rememberUpdatedState(onDismissFinished)
    LaunchedEffect(show) { if (show) open = true }
    if (!open) return
    Popup(
        popupPositionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchor: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    capturedAnchor = Rect(
                        anchor.left.toFloat(), anchor.top.toFloat(),
                        anchor.right.toFloat(), anchor.bottom.toFloat()
                    )
                    return IntOffset(-anchor.left, -anchor.top)
                }
            }
        },
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismissRequest,
    ) {
        // The popup window owns its own back-event dispatcher; re-resolve it so predictive
        // back inside the panel tracks the system gesture instead of the host composition's.
        WindowNavigationEventScope {
            Box(Modifier.fillMaxSize()) {
                GlassDropdownPopupContent(
                    show = show,
                    onDismissRequest = onDismissRequest,
                    anchorBounds = anchorBounds,
                    backdrop = LocalBackdrop.current,
                    sizing = GlassPopupSizing(maxHeight = maxHeight),
                    modifier = modifier,
                    onDismissFinished = {
                        open = false
                        latestOnDismissFinished?.invoke()
                    },
                    content = content,
                )
            }
        }
    }
}
