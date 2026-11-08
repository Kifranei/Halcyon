package com.ella.music.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Close
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.SettingsManager
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EllaSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    // null = follow the "auto popup IME on search" setting; explicit true/false overrides it.
    autoFocus: Boolean? = null,
    autoSelectAll: Boolean = false,
    selectionRequestKey: Int = 0,
    onAutoSelectAllConsumed: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
    active: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val autoShowSearchKeyboard by settingsManager.autoShowSearchKeyboard.collectAsState(initial = true)
    val shouldAutoFocus = autoFocus ?: autoShowSearchKeyboard
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (fieldValue.text != query) fieldValue = TextFieldValue(query, TextRange(query.length))
    }

    fun submitSearch() {
        keyboardController?.hide()
        focusManager.clearFocus()
        onSearch()
    }

    LaunchedEffect(selectionRequestKey) {
        if (!autoSelectAll) fieldValue = TextFieldValue(query, TextRange(query.length))
    }

    LaunchedEffect(active) {
        if (!active && focused) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    // Autofocus is an entry gesture, not a response to `active` toggling. The main-page
    // pager drops `active` during any swipe and restores it when the gesture settles
    // (#742) — re-requesting focus on that return edge was reopening the IME after
    // every scroll. Only the first composition or an explicit selectionRequestKey
    // bump may auto-request focus now.
    var autoFocusHandledKey by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(active, shouldAutoFocus, autoSelectAll, query, selectionRequestKey) {
        if (active && autoSelectAll && query.isNotEmpty() && autoFocusHandledKey != selectionRequestKey) {
            // Mark before the delay: a cancelled attempt still counts as handled so a
            // swipe that briefly drops `active` cannot reschedule the IME on return.
            autoFocusHandledKey = selectionRequestKey
            delay(180L)
            fieldValue = TextFieldValue(query, TextRange(0, query.length))
            // Selecting a saved query must not implicitly open the keyboard.
            if (shouldAutoFocus) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            onAutoSelectAllConsumed()
        }
    }

    LaunchedEffect(active, shouldAutoFocus, selectionRequestKey) {
        if (active && shouldAutoFocus && !autoSelectAll && autoFocusHandledKey != selectionRequestKey) {
            autoFocusHandledKey = selectionRequestKey
            delay(180L)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { value ->
            fieldValue = value
            if (value.text != query) onQueryChange(value.text)
        },
        singleLine = true,
        textStyle = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp)
            .focusProperties { canFocus = active }
            .focusRequester(focusRequester).onFocusChanged {
                if (focused && !it.isFocused && !active) keyboardController?.hide()
                focused = it.isFocused
                onFocusChange(it.isFocused)
            },
        decorationBox = { input ->
            Row(Modifier.fillMaxWidth().background(containerColor, CircleShape), verticalAlignment = Alignment.CenterVertically) {
                Icon(MiuixIcons.Regular.Search, null,
                    modifier = Modifier.padding(start = SearchBarDefaults.LeadingIconStartPadding, end = SearchBarDefaults.LeadingIconEndPadding))
                Box(Modifier.weight(1f).heightIn(min = SearchBarDefaults.InputFieldMinHeight), contentAlignment = Alignment.CenterStart) {
                    if (fieldValue.text.isEmpty()) Text(placeholder,
                        color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                        fontSize = SearchBarDefaults.InputFieldFontSize, fontWeight = FontWeight.Medium)
                    input()
                }
                if (fieldValue.text.isNotEmpty()) IconButton(onClick = {
                    fieldValue = TextFieldValue()
                    onQueryChange("")
                }) {
                    Icon(MiuixIcons.Regular.Close, stringResource(R.string.common_clear), modifier = Modifier.size(18.dp))
                }
            }
        }
    )
}
