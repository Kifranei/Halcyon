package com.ella.music.ui.online

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ella.music.ui.components.EllaSearchBar

@Composable
fun OnlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    EllaSearchBar(
        query = value,
        onQueryChange = onValueChange,
        placeholder = placeholder,
        onSearch = onSearch,
        modifier = modifier.fillMaxWidth(),
        autoFocus = false
    )
}
