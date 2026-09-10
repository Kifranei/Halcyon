package com.ella.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import sh.calvin.reorderable.ReorderableColumn
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class ReorderableSelectionItem(
    val id: String,
    val title: String,
    val summary: String? = null,
    val enabled: Boolean = true
)

@Composable
fun XiaomiCircleCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val uncheckedColor = if (isDark) Color(0x28FFFFFF) else Color(0x18000000)
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) MiuixTheme.colorScheme.primary else uncheckedColor)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ReorderableSelectionSheet(
    show: Boolean,
    title: String,
    subtitle: String? = stringResource(R.string.custom_sort_or_hide_summary),
    items: List<ReorderableSelectionItem>,
    defaultItems: List<ReorderableSelectionItem>? = null,
    maxSelectCount: Int? = null,
    onExceedMaxSelect: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onSave: (List<ReorderableSelectionItem>) -> Unit,
    onReset: (() -> Unit)? = null
) {
    if (!show) return

    var localItems by remember(items, show) { mutableStateOf(items) }
    val cardColor = MiuixTheme.colorScheme.secondaryContainer

    EllaMiuixBottomSheet(
        show = show,
        title = null,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            // Header: Close / Title + Subtitle / Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Close,
                        contentDescription = stringResource(R.string.common_cancel),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        onSave(localItems)
                        onDismissRequest()
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Ok,
                        contentDescription = stringResource(R.string.common_save),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReorderableColumn(
                    list = localItems,
                    onSettle = { fromIndex, toIndex ->
                        if (fromIndex in localItems.indices && toIndex in localItems.indices && fromIndex != toIndex) {
                            localItems = localItems.toMutableList().apply {
                                add(toIndex, removeAt(fromIndex))
                            }
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { _, item, isDragging ->
                    key(item.id) {
                        ReorderableItem {
                            ReorderableSelectionCard(
                                item = item,
                                isDragging = isDragging,
                                cardColor = cardColor,
                                dragHandleModifier = Modifier.draggableHandle(),
                                onToggle = {
                                    val next = !item.enabled
                                    if (next && maxSelectCount != null && localItems.count { it.enabled } >= maxSelectCount) {
                                        onExceedMaxSelect?.invoke()
                                    } else {
                                        localItems = localItems.map {
                                            if (it.id == item.id) it.copy(enabled = next) else it
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                if (defaultItems != null || onReset != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (defaultItems != null) {
                                    localItems = defaultItems
                                }
                                onReset?.invoke()
                            },
                        cornerRadius = 16.dp,
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        colors = CardDefaults.defaultColors(color = cardColor)
                    ) {
                        Text(
                            text = stringResource(R.string.common_restore),
                            color = MiuixTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ReorderableSelectionCard(
    item: ReorderableSelectionItem,
    isDragging: Boolean,
    cardColor: Color,
    dragHandleModifier: Modifier,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(
            color = if (isDragging) {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                cardColor
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = dragHandleModifier.size(32.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "☰",
                    fontSize = 18.sp,
                    color = if (isDragging) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle
                    )
            ) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                if (!item.summary.isNullOrBlank()) {
                    Text(
                        text = item.summary,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            XiaomiCircleCheckbox(
                checked = item.enabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
