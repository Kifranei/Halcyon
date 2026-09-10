package com.ella.music.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EllaSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean? = null,
    autoSelectAll: Boolean = false,
    onAutoSelectAllConsumed: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.surfaceContainerHigh
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val autoShowSearchKeyboard by settingsManager.autoShowSearchKeyboard.collectAsState(initial = true)
    val shouldAutoFocus = autoFocus ?: autoShowSearchKeyboard
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }

    fun submitSearch() {
        keyboardController?.hide()
        focusManager.clearFocus()
        onSearch()
    }

    LaunchedEffect(autoSelectAll, query) {
        if (autoSelectAll && query.isNotEmpty()) {
            delay(180L)
            focusRequester.requestFocus()
            if (shouldAutoFocus) keyboardController?.show()
            onAutoSelectAllConsumed()
        }
    }

    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus && !autoSelectAll) {
            delay(180L)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { submitSearch() },
        expanded = expanded,
        onExpandedChange = { if (it) expanded = true },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) },
        label = placeholder,
        color = containerColor
    )
}
