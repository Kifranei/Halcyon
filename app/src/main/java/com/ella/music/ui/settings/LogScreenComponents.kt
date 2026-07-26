package com.ella.music.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.AppLogEntry
import com.ella.music.data.AppLogStore
import com.ella.music.ui.components.EllaMiuixBadge
import com.ella.music.ui.components.EllaMiuixBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppLogItem(
    entry: AppLogEntry,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        BasicComponent(
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeverityBadge(entry.level)
                    Text(
                        text = stringResource(entry.detectType().labelRes),
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = formatTimeOnly(entry.time),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.tag,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SeverityBadge(level: String) {
    val normalized = level.uppercase(Locale.ROOT)
    val background = when (normalized) {
        "ERROR", "CRASH" -> MiuixTheme.colorScheme.error
        "WARN", "WARNING" -> MiuixTheme.colorScheme.tertiaryContainer
        "DEBUG" -> MiuixTheme.colorScheme.secondaryContainer
        else -> MiuixTheme.colorScheme.primary
    }
    val content = when (normalized) {
        "ERROR", "CRASH" -> MiuixTheme.colorScheme.onError
        "INFO" -> MiuixTheme.colorScheme.onPrimary
        else -> MiuixTheme.colorScheme.onSurface
    }
    EllaMiuixBadge(
        text = if (normalized == "WARN") "WARNING" else normalized,
        color = background,
        contentColor = content
    )
}

@Composable
internal fun AppLogDetailSheet(
    show: Boolean,
    entry: AppLogEntry?,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onCopy: (AppLogEntry) -> Unit
) {
    EllaMiuixBottomSheet(
        show = show,
        enableNestedScroll = false,
        title = stringResource(R.string.logs_detail_title),
        endAction = {
            entry?.let {
                IconButton(onClick = { onCopy(it) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Copy,
                        contentDescription = stringResource(R.string.logs_copy_action)
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished
    ) {
        entry ?: return@EllaMiuixBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            DetailRow(label = stringResource(R.string.logs_field_time), value = AppLogStore.formatTime(entry.time))
            DetailRow(label = stringResource(R.string.logs_field_level), value = entry.level)
            DetailRow(label = stringResource(R.string.logs_field_type), value = stringResource(entry.detectType().labelRes))
            DetailRow(label = "Tag", value = entry.tag)
            entry.relatedId?.takeIf { it.isNotBlank() }?.let {
                DetailRow(label = stringResource(R.string.logs_field_related), value = it)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.logs_field_message),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontWeight = FontWeight.Bold
            )
            SelectionContainer {
                Text(
                    text = entry.message,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
            entry.throwable?.takeIf { it.isNotBlank() }?.let { detail ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.logs_field_detail),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontWeight = FontWeight.Bold
                )
                SelectionContainer {
                    Text(
                        text = detail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = MiuixTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontWeight = FontWeight.Bold
        )
        SelectionContainer {
            Text(
                text = value,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal enum class EllaLogLevelFilter(val labelRes: Int, private val aliases: Set<String>) {
    DEBUG(R.string.logs_level_debug, setOf("DEBUG")),
    INFO(R.string.logs_level_info, setOf("INFO")),
    WARNING(R.string.logs_level_warning, setOf("WARN", "WARNING")),
    ERROR(R.string.logs_level_error, setOf("ERROR")),
    CRASH(R.string.logs_level_crash, setOf("CRASH"));

    fun matches(entry: AppLogEntry): Boolean = entry.level.uppercase(Locale.ROOT) in aliases
}

internal enum class EllaLogTypeFilter(val storageNames: Set<String>, val keywords: Set<String>) {
    APP(setOf("APP"), emptySet()),
    PLAYBACK(setOf("PLAYBACK"), setOf("player", "playback", "play", "exo", "media", "audio", "decoder", "queue", "播放", "播放器", "解码", "队列")),
    LYRICS(setOf("LYRICS"), setOf("lyric", "lyrics", "ticker", "superlyric", "lyricon", "flyme", "samsung", "bluetooth", "词幕", "歌词")),
    LIBRARY(setOf("LIBRARY"), setOf("scan", "scanner", "library", "folder", "album", "artist", "cover", "音乐库", "扫描", "文件夹", "专辑", "艺术家", "封面")),
    METADATA(setOf("METADATA"), setOf("tag", "metadata", "taglib", "wav", "alac", "元数据", "标签")),
    ONLINE(setOf("ONLINE"), setOf("lx", "download", "api", "在线", "下载")),
    NETWORK(setOf("NETWORK"), setOf("network", "http", "okhttp", "webdav", "request", "response", "网络")),
    DATABASE(setOf("DATABASE"), setOf("database", "db", "room", "dao", "backup", "restore", "playlist", "stats", "数据库", "备份", "恢复")),
    CRASH(setOf("CRASH"), setOf("crash", "exception", "fatal", "崩溃", "闪退"));

    val labelRes: Int
        get() = when (this) {
            APP -> R.string.logs_type_app
            PLAYBACK -> R.string.logs_type_playback
            LYRICS -> R.string.logs_type_lyrics
            LIBRARY -> R.string.logs_type_library
            METADATA -> R.string.logs_type_metadata
            ONLINE -> R.string.logs_type_online
            NETWORK -> R.string.logs_type_network
            DATABASE -> R.string.logs_type_database
            CRASH -> R.string.logs_type_crash
        }

    fun label(context: Context): String = context.getString(labelRes)

    fun matches(entry: AppLogEntry): Boolean = entry.detectType() == this
}

internal fun AppLogEntry.detectType(): EllaLogTypeFilter {
    if (level.equals("CRASH", ignoreCase = true)) return EllaLogTypeFilter.CRASH
    EllaLogTypeFilter.entries.firstOrNull { filter ->
        type.uppercase(Locale.ROOT) in filter.storageNames
    }?.let { return it }
    val haystack = "$tag $message ${throwable.orEmpty()}".lowercase(Locale.ROOT)
    return EllaLogTypeFilter.entries
        .asSequence()
        .filter { it != EllaLogTypeFilter.APP }
        .firstOrNull { type -> type.keywords.any { it.lowercase(Locale.ROOT) in haystack } }
        ?: EllaLogTypeFilter.APP
}

internal fun AppLogEntry.matchesKeyword(context: Context, keyword: String): Boolean {
    return level.contains(keyword, ignoreCase = true) ||
        tag.contains(keyword, ignoreCase = true) ||
        message.contains(keyword, ignoreCase = true) ||
        throwable.orEmpty().contains(keyword, ignoreCase = true) ||
        AppLogStore.formatTime(time).contains(keyword, ignoreCase = true) ||
        detectType().label(context).contains(keyword, ignoreCase = true)
}

internal fun AppLogEntry.formatForCopy(context: Context): String = buildString {
    appendLine("${AppLogStore.formatTime(time)} [$level/${detectType().label(context)}] $tag")
    appendLine(message)
    throwable?.takeIf { it.isNotBlank() }?.let {
        appendLine()
        appendLine(it)
    }
}

private fun formatTimeOnly(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
