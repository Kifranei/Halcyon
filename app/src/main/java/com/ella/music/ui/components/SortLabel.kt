package com.ella.music.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.R

@Composable
internal fun sortLabel(@StringRes fieldRes: Int, descending: Boolean): String =
    sortLabel(
        field = stringResource(fieldRes),
        descending = descending
    )

@Composable
internal fun sortLabel(field: String, descending: Boolean): String =
    "$field · ${stringResource(if (descending) R.string.common_sort_descending else R.string.common_sort_ascending)}"
