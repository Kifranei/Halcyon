package com.ella.music.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared "N 首歌曲 · 排序方式" list header used above sorted library lists. */
@Composable
internal fun SortSummaryHeader(
    text: String,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        leadingContent?.invoke(this)
        Text(
            text = text,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
internal fun ShuffleAllSummaryButton(
    visible: Boolean,
    onClick: () -> Unit
) {
    if (!visible) return
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_shuffle),
            contentDescription = stringResource(R.string.shuffle),
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
internal fun sortLabel(@StringRes fieldRes: Int, descending: Boolean): String =
    sortLabel(
        field = stringResource(fieldRes),
        descending = descending
    )

@Composable
internal fun sortLabel(field: String, descending: Boolean): String =
    "${field.withoutEmbeddedSortDirection()} · ${stringResource(if (descending) R.string.common_sort_descending else R.string.common_sort_ascending)}"

/** Legacy sort strings included their direction, which made summaries say e.g. "ascending · ascending". */
private fun String.withoutEmbeddedSortDirection(): String =
    replace(Regex("\\s*(正序|倒序|升序|降序|ascending|descending|absteigend|aufsteigend|croissante|décroissante|昇順|降順|오름차순|내림차순|по возрастанию|по убыванию)\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*[（(]\\s*(逆順|反向|升冪|降冪)\\s*[）)]\\s*"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
