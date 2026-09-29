package com.ella.music.ui.online

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import top.yukonga.miuix.kmp.basic.InputField

@Composable
fun OnlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    InputField(
        query = value,
        onQueryChange = onValueChange,
        onSearch = {
            keyboardController?.hide()
            focusManager.clearFocus()
            onSearch()
        },
        expanded = expanded,
        onExpandedChange = { if (it) expanded = true },
        label = placeholder,
        modifier = modifier.fillMaxWidth()
    )
}
