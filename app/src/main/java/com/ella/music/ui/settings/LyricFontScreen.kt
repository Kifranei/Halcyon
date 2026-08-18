package com.ella.music.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.player.DesktopLyricService
import com.ella.music.ui.components.EllaSmallTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricFontScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val legacyWesternFontName by settingsManager.lyricWesternFontName.collectAsState(initial = "")
    val legacyWesternFontPath by settingsManager.lyricWesternFontPath.collectAsState(initial = "")
    val legacyCjkFontName by settingsManager.lyricCjkFontName.collectAsState(initial = "")
    val legacyCjkFontPath by settingsManager.lyricCjkFontPath.collectAsState(initial = "")
    val globalWesternFontName by settingsManager.globalWesternFontName.collectAsState(initial = "")
    val globalWesternFontPath by settingsManager.globalWesternFontPath.collectAsState(initial = "")
    val globalCjkFontName by settingsManager.globalCjkFontName.collectAsState(initial = "")
    val globalCjkFontPath by settingsManager.globalCjkFontPath.collectAsState(initial = "")
    val originalWesternFontName by settingsManager.lyricOriginalWesternFontName.collectAsState(initial = "")
    val originalWesternFontPath by settingsManager.lyricOriginalWesternFontPath.collectAsState(initial = "")
    val originalCjkFontName by settingsManager.lyricOriginalCjkFontName.collectAsState(initial = "")
    val originalCjkFontPath by settingsManager.lyricOriginalCjkFontPath.collectAsState(initial = "")
    val translationWesternFontName by settingsManager.lyricTranslationWesternFontName.collectAsState(initial = "")
    val translationWesternFontPath by settingsManager.lyricTranslationWesternFontPath.collectAsState(initial = "")
    val translationCjkFontName by settingsManager.lyricTranslationCjkFontName.collectAsState(initial = "")
    val translationCjkFontPath by settingsManager.lyricTranslationCjkFontPath.collectAsState(initial = "")
    val lyricFontWeight by settingsManager.lyricFontWeight.collectAsState(initial = 800)
    val lyricFontItalic by settingsManager.lyricFontItalic.collectAsState(initial = false)
    val lyricShareUseLyricFont by settingsManager.lyricShareUseLyricFont.collectAsState(initial = false)
    val lyricFontApplyToPage by settingsManager.lyricFontApplyToPage.collectAsState(initial = true)
    val lyricFontApplyToDesktop by settingsManager.lyricFontApplyToDesktop.collectAsState(initial = true)
    var fonts by remember { mutableStateOf<List<FontChoice>>(emptyList()) }
    var systemFonts by remember { mutableStateOf<List<FontChoice>>(emptyList()) }
    var showSystemFontPicker by remember { mutableStateOf(false) }
    var activeTarget by remember { mutableStateOf(LyricFontTarget.GlobalWestern) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
    val selectedFontPath = when (activeTarget) {
        LyricFontTarget.GlobalWestern -> globalWesternFontPath.ifBlank { legacyWesternFontPath }
        LyricFontTarget.GlobalCjk -> globalCjkFontPath
        LyricFontTarget.OriginalWestern -> originalWesternFontPath.ifBlank { legacyWesternFontPath }
        LyricFontTarget.OriginalCjk -> originalCjkFontPath.ifBlank { legacyCjkFontPath }
        LyricFontTarget.TranslationWestern -> translationWesternFontPath.ifBlank { legacyWesternFontPath }
        LyricFontTarget.TranslationCjk -> translationCjkFontPath.ifBlank { legacyCjkFontPath }
    }
    val currentSystemFont = remember(selectedFontPath, systemFonts) {
        systemFonts.firstOrNull { it.path == selectedFontPath }
    }
    val globalWesternDisplayName = globalWesternFontName.ifBlank { legacyWesternFontName.ifBlank { "系统默认" } }
    val globalCjkDisplayName = globalCjkFontName.ifBlank { "系统补缺" }
    val originalWesternDisplayName = originalWesternFontName.ifBlank { legacyWesternFontName.ifBlank { "Inter" } }
    val originalCjkDisplayName = originalCjkFontName.ifBlank { legacyCjkFontName.ifBlank { "MiSans Bold" } }
    val translationWesternDisplayName = translationWesternFontName.ifBlank { legacyWesternFontName.ifBlank { "Inter" } }
    val translationCjkDisplayName = translationCjkFontName.ifBlank { legacyCjkFontName.ifBlank { "MiSans Bold" } }
    val listState = rememberSettingsLazyListState("settings_lyric_font")

    suspend fun applyFont(font: FontChoice) {
        when (activeTarget) {
            LyricFontTarget.GlobalWestern -> settingsManager.setGlobalFont(font.name, font.path, globalCjkFontName, globalCjkFontPath)
            LyricFontTarget.GlobalCjk -> settingsManager.setGlobalFont(globalWesternFontName, globalWesternFontPath, font.name, font.path)
            LyricFontTarget.OriginalWestern -> settingsManager.setLyricOriginalFont(font.name, font.path, originalCjkFontName, originalCjkFontPath)
            LyricFontTarget.OriginalCjk -> settingsManager.setLyricOriginalFont(originalWesternFontName, originalWesternFontPath, font.name, font.path)
            LyricFontTarget.TranslationWestern -> settingsManager.setLyricTranslationFont(font.name, font.path, translationCjkFontName, translationCjkFontPath)
            LyricFontTarget.TranslationCjk -> settingsManager.setLyricTranslationFont(translationWesternFontName, translationWesternFontPath, font.name, font.path)
        }
        notifyDesktopLyricFontChanged(context, settingsManager)
    }

    suspend fun clearActiveFont() {
        when (activeTarget) {
            LyricFontTarget.GlobalWestern -> settingsManager.setGlobalFont("", "", globalCjkFontName, globalCjkFontPath)
            LyricFontTarget.GlobalCjk -> settingsManager.setGlobalFont(globalWesternFontName, globalWesternFontPath, "", "")
            LyricFontTarget.OriginalWestern -> settingsManager.setLyricOriginalFont("", "", originalCjkFontName, originalCjkFontPath)
            LyricFontTarget.OriginalCjk -> settingsManager.setLyricOriginalFont(originalWesternFontName, originalWesternFontPath, "", "")
            LyricFontTarget.TranslationWestern -> settingsManager.setLyricTranslationFont("", "", translationCjkFontName, translationCjkFontPath)
            LyricFontTarget.TranslationCjk -> settingsManager.setLyricTranslationFont(translationWesternFontName, translationWesternFontPath, "", "")
        }
        notifyDesktopLyricFontChanged(context, settingsManager)
    }

    LaunchedEffect(Unit) {
        fonts = withContext(Dispatchers.IO) { collectFontChoices(context) }
        systemFonts = withContext(Dispatchers.IO) { collectSystemFontChoices(context) }
    }
    LaunchedEffect(lyricFontItalic) {
        if (lyricFontItalic) settingsManager.setLyricFontItalic(false)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { copyImportedFont(context, uri) }
            }.onSuccess { font ->
                applyFont(font)
                fonts = withContext(Dispatchers.IO) { collectFontChoices(context) }
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_lyric_font_applied, font.name),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.settings_lyric_font_import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_font_screen_title),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(onClick = { importLauncher.launch(SUPPORTED_FONT_MIME_TYPES) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Download,
                        contentDescription = stringResource(R.string.settings_lyric_font_import),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            color = pageBackground
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = WindowInsets.navigationBars
                .asPaddingValues()
                .let { PaddingValues(bottom = it.calculateBottomPadding() + 96.dp) }
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                LyricFontTargetCard(
                    title = stringResource(R.string.settings_font_global_western),
                    currentName = globalWesternDisplayName,
                    selected = activeTarget == LyricFontTarget.GlobalWestern,
                    onClick = { activeTarget = LyricFontTarget.GlobalWestern }
                )
                LyricFontTargetCard(
                    title = stringResource(R.string.settings_font_global_cjk),
                    currentName = globalCjkDisplayName,
                    selected = activeTarget == LyricFontTarget.GlobalCjk,
                    onClick = { activeTarget = LyricFontTarget.GlobalCjk }
                )
                LyricFontTargetCard(
                    title = stringResource(R.string.settings_font_original_western),
                    currentName = originalWesternDisplayName,
                    selected = activeTarget == LyricFontTarget.OriginalWestern,
                    onClick = { activeTarget = LyricFontTarget.OriginalWestern }
                )
                LyricFontTargetCard(
                    title = stringResource(R.string.settings_font_original_cjk),
                    currentName = originalCjkDisplayName,
                    selected = activeTarget == LyricFontTarget.OriginalCjk,
                    onClick = { activeTarget = LyricFontTarget.OriginalCjk }
                )
                LyricFontTargetCard(
                    title = stringResource(R.string.settings_font_translation_western),
                    currentName = translationWesternDisplayName,
                    selected = activeTarget == LyricFontTarget.TranslationWestern,
                    onClick = { activeTarget = LyricFontTarget.TranslationWestern }
                )
                LyricFontTargetCard(
                    title = stringResource(R.string.settings_font_translation_cjk),
                    currentName = translationCjkDisplayName,
                    selected = activeTarget == LyricFontTarget.TranslationCjk,
                    onClick = { activeTarget = LyricFontTarget.TranslationCjk }
                )
                SettingsCardGroup {
                    SwitchPreference(
                        title = stringResource(R.string.settings_lyric_share_use_lyric_font),
                        summary = stringResource(R.string.settings_lyric_share_use_lyric_font_summary),
                        checked = lyricShareUseLyricFont,
                        onCheckedChange = { value ->
                            scope.launch { settingsManager.setLyricShareUseLyricFont(value) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_lyric_font_apply_to_page),
                        summary = stringResource(R.string.settings_lyric_font_apply_to_page_summary),
                        checked = lyricFontApplyToPage,
                        onCheckedChange = { value ->
                            scope.launch { settingsManager.setLyricFontApplyToPage(value) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_lyric_font_apply_to_desktop),
                        summary = stringResource(R.string.settings_lyric_font_apply_to_desktop_summary),
                        checked = lyricFontApplyToDesktop,
                        onCheckedChange = { value ->
                            scope.launch {
                                settingsManager.setLyricFontApplyToDesktop(value)
                                notifyDesktopLyricFontChanged(context, settingsManager)
                            }
                        }
                    )
                }
                LyricFontWeightCard(
                    westernFontPath = originalWesternFontPath.ifBlank { legacyWesternFontPath },
                    cjkFontPath = originalCjkFontPath.ifBlank { legacyCjkFontPath },
                    lyricFontWeight = lyricFontWeight,
                    onWeightChange = { weight ->
                        scope.launch {
                            settingsManager.setLyricFontWeight(weight)
                            notifyDesktopLyricFontChanged(context, settingsManager)
                        }
                    }
                )
                LyricFontListTitle()
            }

            items(fonts, key = { it.path }) { font ->
                FontChoiceItem(
                    font = font,
                    currentWeight = lyricFontWeight,
                    italic = false,
                    selected = selectedFontPath == font.path,
                    onClick = {
                        scope.launch {
                            applyFont(font)
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_lyric_font_applied, font.name),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onDelete = if (font.sourceRank == FONT_SOURCE_IMPORTED) {
                        {
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) {
                                    deleteImportedFont(font)
                                }

                                clearImportedFontReferences(settingsManager, font.path)
                                notifyDesktopLyricFontChanged(context, settingsManager)

                                fonts = withContext(Dispatchers.IO) { collectFontChoices(context) }

                                Toast.makeText(
                                    context,
                                    if (deleted) {
                                        context.getString(R.string.settings_lyric_font_deleted)
                                    } else {
                                        context.getString(R.string.settings_lyric_font_delete_failed)
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        null
                    }
                )
            }

            item {
                SystemFontEntryCard(
                    currentSystemFontName = currentSystemFont?.name,
                    currentSystemFontPath = currentSystemFont?.path,
                    currentWeight = lyricFontWeight,
                    onClick = { showSystemFontPicker = true }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showSystemFontPicker) {
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.settings_font_system_pick_title),
            onDismissRequest = { showSystemFontPicker = false }
        ) {
            if (systemFonts.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_font_system_empty),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(18.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .heightIn(max = 480.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    items(systemFonts, key = { it.path }) { font ->
                        FontChoiceItem(
                            font = font,
                            currentWeight = lyricFontWeight,
                            italic = false,
                            selected = selectedFontPath == font.path,
                            onClick = {
                                scope.launch {
                                    applyFont(font)
                                    showSystemFontPicker = false
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_lyric_font_applied, font.name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private enum class LyricFontTarget {
    GlobalWestern,
    GlobalCjk,
    OriginalWestern,
    OriginalCjk,
    TranslationWestern,
    TranslationCjk
}

private const val DEFAULT_FONT_CHOICE_PATH = "__lyric_slot_default__"

private suspend fun notifyDesktopLyricFontChanged(
    context: Context,
    settingsManager: SettingsManager
) {
    if (!settingsManager.desktopLyricEnabled.first()) return
    context.startService(
        Intent(context, DesktopLyricService::class.java)
            .setAction(DesktopLyricService.ACTION_APPLY_SETTINGS)
    )
}

private suspend fun clearImportedFontReferences(settingsManager: SettingsManager, path: String) {
    // Clear only the slot holding the deleted file. Empty slots deliberately fall back through
    // the migration/default chain, so a deleted imported font never leaves a broken Typeface.
    if (settingsManager.globalWesternFontPath.first() == path) {
        settingsManager.setGlobalFont("", "", settingsManager.globalCjkFontName.first(), settingsManager.globalCjkFontPath.first())
    }
    if (settingsManager.globalCjkFontPath.first() == path) {
        settingsManager.setGlobalFont(settingsManager.globalWesternFontName.first(), settingsManager.globalWesternFontPath.first(), "", "")
    }
    if (settingsManager.lyricOriginalWesternFontPath.first() == path) {
        settingsManager.setLyricOriginalFont("", "", settingsManager.lyricOriginalCjkFontName.first(), settingsManager.lyricOriginalCjkFontPath.first())
    }
    if (settingsManager.lyricOriginalCjkFontPath.first() == path) {
        settingsManager.setLyricOriginalFont(settingsManager.lyricOriginalWesternFontName.first(), settingsManager.lyricOriginalWesternFontPath.first(), "", "")
    }
    if (settingsManager.lyricTranslationWesternFontPath.first() == path) {
        settingsManager.setLyricTranslationFont("", "", settingsManager.lyricTranslationCjkFontName.first(), settingsManager.lyricTranslationCjkFontPath.first())
    }
    if (settingsManager.lyricTranslationCjkFontPath.first() == path) {
        settingsManager.setLyricTranslationFont(settingsManager.lyricTranslationWesternFontName.first(), settingsManager.lyricTranslationWesternFontPath.first(), "", "")
    }
}
