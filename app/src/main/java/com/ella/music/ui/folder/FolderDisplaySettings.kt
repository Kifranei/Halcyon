package com.ella.music.ui.folder

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ella.music.R
import com.ella.music.ui.components.*
import com.ella.music.ui.settings.SettingsIntSliderPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.semantics.contentDescription
import java.io.File

internal data class FolderDisplaySettings(val sizePercent: Int = 100, val widthPercent: Int = 100) {
    val columns: Int get() = folderDisplayColumns(sizePercent, widthPercent)
}
internal fun folderDisplayColumns(sizePercent: Int, widthPercent: Int): Int =
    (10_000 / (sizePercent.coerceIn(65, 140) * widthPercent.coerceIn(25, 100))).coerceIn(1, 4)
internal fun folderFastIndexTargets(letters: List<String>, columns: Int): Map<String, Int> = buildMap {
    letters.forEachIndexed { index, letter -> putIfAbsent(letter, index / columns.coerceAtLeast(1)) }
}
private object FolderDisplayStore {
    var state: MutableStateFlow<FolderDisplaySettings>? = null
    fun get(context: Context): MutableStateFlow<FolderDisplaySettings> {
        state?.let { return it }
        val prefs = context.getSharedPreferences("folder_display", Context.MODE_PRIVATE)
        return MutableStateFlow(FolderDisplaySettings(prefs.getInt("size", 100).coerceIn(65, 140), prefs.getInt("width", 100).coerceIn(25, 100))).also { state = it }
    }
    fun update(context: Context, value: FolderDisplaySettings) {
        context.getSharedPreferences("folder_display", Context.MODE_PRIVATE).edit().putInt("size", value.sizePercent).putInt("width", value.widthPercent).apply()
        get(context).value = value
    }
}
@Composable internal fun rememberFolderDisplaySettings(): FolderDisplaySettings = FolderDisplayStore.get(LocalContext.current).collectAsState().value

@Composable internal fun FolderDisplayButton() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    val display = rememberFolderDisplaySettings()
    IconButton(onClick = { show = true }) {
        top.yukonga.miuix.kmp.basic.Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_folder_display),
            contentDescription = stringResource(R.string.folder_display_settings),
            tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)
        )
    }
    EllaMiuixBottomSheet(show = show, title = stringResource(R.string.folder_display_settings), onDismissRequest = { show = false }) {
        Column {
        SettingsIntSliderPreference(title = stringResource(R.string.folder_display_size), summary = "", valueText = "${display.sizePercent}%", value = display.sizePercent,
            valueRange = 65..140, onValueChange = { FolderDisplayStore.update(context, display.copy(sizePercent = it)) })
        SettingsIntSliderPreference(title = stringResource(R.string.folder_display_width), summary = "", valueText = "${display.widthPercent}%", value = display.widthPercent,
            valueRange = 25..100, onValueChange = { FolderDisplayStore.update(context, display.copy(widthPercent = it)) })
        }
    }
}

@Composable internal fun FolderHierarchyCover(folder: FolderTreeEntry, modifier: Modifier) {
    val fallback = folder.coverSong.folderPlaylistCoverModel()
    val model by produceState<Any?>(fallback, folder.path, fallback) {
        value = withContext(Dispatchers.IO) {
            File(folder.path).listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                ?.sortedWith(compareBy<File> { if (it.nameWithoutExtension.lowercase() in setOf("cover", "folder", "front", "album")) 0 else 1 }.thenBy { it.name })?.firstOrNull() ?: fallback
        }
    }
    if (model != null) SafeCoverImage(model, folder.name, modifier.clip(RoundedCornerShape(10.dp)), sizePx = 320)
    else FolderOutlineIcon(tint = MiuixTheme.colorScheme.primary, modifier = modifier)
}
