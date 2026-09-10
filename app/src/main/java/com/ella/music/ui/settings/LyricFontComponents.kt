package com.ella.music.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.ScriptFontPaths
import com.ella.music.ui.components.loadScriptAwareTypeface
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.ella.music.ui.components.wallpaperAwareCardColors

@Composable
internal fun LyricFontTargetCard(
    title: String,
    currentName: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.padding(vertical = 4.dp),
        colors = wallpaperAwareCardColors(defaultAlpha = 0.42f),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        BasicComponent(
            title = title,
            summary = stringResource(R.string.settings_lyric_font_target_summary, currentName),
            endActions = {
                if (selected) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        )
    }
}

@Composable
internal fun SystemDefaultFontCard(
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.padding(vertical = 4.dp),
        colors = wallpaperAwareCardColors(defaultAlpha = 0.42f),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        BasicComponent(
            title = stringResource(R.string.settings_system_default),
            summary = stringResource(R.string.settings_lyric_font_system_default_summary),
            endActions = {
                if (selected) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        )
    }
}

@Composable
internal fun LyricFontWeightCard(
    westernFontPath: String,
    cjkFontPath: String,
    lyricFontWeight: Int,
    onWeightChange: (Int) -> Unit
) {
    // Local state for immediate slider feedback; persisted value is debounced so
    // DataStore writes and desktop-lyric Service restarts don't fire on every drag frame.
    var localWeight by remember { mutableIntStateOf(lyricFontWeight) }
    LaunchedEffect(lyricFontWeight) {
        localWeight = lyricFontWeight
    }
    LaunchedEffect(localWeight) {
        if (localWeight != lyricFontWeight) {
            delay(300)
            onWeightChange(localWeight)
        }
    }
    val safeWeight = localWeight.coerceIn(100, 900)
    // Preview should render with the full Western + CJK script-aware typeface, so the English
    // part of the sample can reflect the variable weight axis (e.g. Inter at weight 100) while
    // CJK characters still use the selected CJK font. Using only the active target path made the
    // whole preview fall back to a single font and masked weight changes on variable fonts.
    val previewTypeface = remember(westernFontPath, cjkFontPath, safeWeight) {
        val paths = ScriptFontPaths(westernFontPath, cjkFontPath)
        runCatching {
            loadScriptAwareTypeface(paths, safeWeight, italic = false, boldFallback = false)
        }.getOrNull()
    }
    val previewFamily = previewTypeface?.let { FontFamily(it) }
    SettingsCardGroup(modifier = Modifier.padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_lyric_font_weight),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_lyric_font_weight_current, safeWeight),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.settings_lyric_font_preview_sample),
                    fontSize = 18.sp,
                    fontWeight = FontWeight(safeWeight),
                    fontFamily = previewFamily,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Slider(
                value = (safeWeight - 100) / 800f,
                onValueChange = { fraction ->
                    val weight = (100 + fraction.coerceIn(0f, 1f) * 800f).toInt()
                    localWeight = weight
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_lyric_font_weight_light),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.settings_lyric_font_weight_heavy),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
internal fun SystemFontEntryCard(
    currentSystemFontName: String?,
    currentSystemFontPath: String?,
    currentWeight: Int,
    onClick: () -> Unit
) {
    val previewFamily = remember(currentSystemFontPath, currentWeight) {
        currentSystemFontPath?.toFontFamilyOrNull(currentWeight, false)
    }
    SettingsCardGroup(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_font_system_fonts),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = currentSystemFontName ?: stringResource(R.string.settings_font_system_pick),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = previewFamily,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (currentSystemFontName != null) {
                    Text(
                        text = stringResource(R.string.settings_font_system_pick),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun LyricFontListTitle() {
    Text(
        text = stringResource(R.string.settings_lyric_font_list),
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
internal fun FontChoiceItem(
    font: FontChoice,
    currentWeight: Int,
    italic: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val fontFamily = remember(font.path, currentWeight, italic) {
        font.path.toFontFamilyOrNull(currentWeight, italic)
    }

    SettingsCardGroup(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = font.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.settings_lyric_font_preview_sample),
                    fontSize = 18.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight(currentWeight.coerceIn(100, 900)),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = font.source,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (selected) {
                Icon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (onDelete != null) {
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun previewFontFamily(path: String, weight: Int, italic: Boolean): FontFamily? {
    return remember(path, weight, italic) { path.toFontFamilyOrNull(weight, italic) }
}
