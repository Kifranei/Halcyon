package com.ella.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
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
    containerColor: Color = MiuixTheme.colorScheme.surfaceContainerHigh
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val autoShowSearchKeyboard by settingsManager.autoShowSearchKeyboard.collectAsState(initial = true)
    val shouldAutoFocus = autoFocus ?: autoShowSearchKeyboard
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }

    LaunchedEffect(query) {
        if (query != fieldValue.text) {
            fieldValue = TextFieldValue(query, selection = TextRange(query.length))
        }
    }

    fun submitSearch() {
        keyboardController?.hide()
        focusManager.clearFocus()
        onSearch()
    }

    LaunchedEffect(autoSelectAll, query) {
        if (autoSelectAll && query.isNotEmpty()) {
            fieldValue = TextFieldValue(query, selection = TextRange(0, query.length))
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

    BasicTextField(
        value = fieldValue,
        onValueChange = { value ->
            fieldValue = value
            if (value.text != query) onQueryChange(value.text)
        },
        singleLine = true,
        textStyle = TextStyle(
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(containerColor)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(21.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 45.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (fieldValue.text.isBlank()) {
                        Text(
                            text = placeholder,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    innerTextField()
                }
                AnimatedVisibility(
                    visible = fieldValue.text.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.SearchCleanup,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(21.dp)
                            .clip(CircleShape)
                            .clickable {
                                fieldValue = TextFieldValue("", selection = TextRange.Zero)
                                onQueryChange("")
                            }
                    )
                }
            }
        }
    )
}
