package com.ella.music.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ella.music.R
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.isAppWallpaperVisible
import com.ella.music.ui.components.wallpaperAwareCardColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Unified page background color for diagnostics and log screens.
 * In dark mode, returns deep black (#101014), or transparent if app wallpaper is visible.
 * In light mode, returns soft light gray (#F4F4F7).
 */
@Composable
fun diagnosticsPageBackground(): Color = ellaPageBackground()

/**
 * Unified card surface color for diagnostics and log screens.
 * In dark mode, returns elevated gray surfaceContainer (~#242428) on black background.
 * In light mode, returns pure white (#FFFFFF).
 * If wallpaper is visible, returns wallpaper-aware translucent card color.
 */
@Composable
fun diagnosticsCardColor(): Color {
    if (isAppWallpaperVisible()) {
        return wallpaperAwareCardColor(defaultAlpha = 0.42f)
    }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        MiuixTheme.colorScheme.surfaceContainer
    } else {
        Color(0xFFFFFFFF)
    }
}

/**
 * Card outline border for diagnostics cards.
 */
@Composable
fun diagnosticsCardBorder(): BorderStroke {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return BorderStroke(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = if (isDark) 0.16f else 0.10f))
}

/**
 * Standard reusable card component across diagnostics and log screens.
 * Ensures consistent black background with gray card surface, 16dp rounded corners,
 * and subtle outline border.
 */
@Composable
fun DiagnosticsCard(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(color = diagnosticsCardColor()),
        cornerRadius = 16.dp,
        insideMargin = insideMargin
    ) {
        content()
    }
}

/**
 * Reusable search bar for filtering logs and diagnostic items.
 */
@Composable
fun DiagnosticsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.logs_search_label)
) {
    var expanded by remember { mutableStateOf(false) }
    InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = {},
        expanded = expanded,
        onExpandedChange = { if (it) expanded = true },
        label = label,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    )
}

/**
 * Reusable empty state card for diagnostics and log screens.
 */
@Composable
fun DiagnosticsEmptyCard(
    text: String,
    modifier: Modifier = Modifier
) {
    DiagnosticsCard(modifier = modifier) {
        BasicComponent(title = text)
    }
}

/**
 * Shares a generated text file (logs or performance report) via system share sheet.
 */
fun shareDiagnosticsTextFile(
    context: Context,
    file: File,
    subject: String = context.getString(R.string.logs_share_subject),
    chooserTitle: String = context.getString(R.string.logs_share_chooser_title),
    noAppMessage: String = context.getString(R.string.share_no_available_app)
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TITLE, file.name)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, subject, uri)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, chooserTitle)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }.onFailure {
        Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Copies diagnostic text or log entry to system clipboard and shows feedback toast.
 */
fun copyDiagnosticsText(
    context: Context,
    text: String,
    label: String = context.getString(R.string.logs_clip_label),
    toastMessage: String = context.getString(R.string.logs_copied)
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

/**
 * Formats timestamps to HH:mm:ss for log items.
 */
fun formatDiagnosticsTimeOnly(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
