package com.ella.music.ui.settings

import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ella.music.R
import com.ella.music.data.CustomLauncherIconItem
import com.ella.music.data.CustomLauncherIconStore
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CustomLauncherIconPreference() {
    var showSheet by remember { mutableStateOf(false) }

    ArrowPreference(
        title = stringResource(R.string.settings_custom_launcher_icon),
        summary = stringResource(R.string.settings_custom_launcher_icon_summary),
        onClick = { showSheet = true }
    )

    if (showSheet) {
        CustomLauncherIconSheet(
            show = true,
            onDismissRequest = { showSheet = false }
        )
    }
}

@Composable
internal fun CustomLauncherIconSheet(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var icons by remember { mutableStateOf<List<CustomLauncherIconItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<CustomLauncherIconItem?>(null) }

    LaunchedEffect(Unit) {
        icons = CustomLauncherIconStore.loadIcons(context)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    val manager = context.getSystemService(ShortcutManager::class.java)
                    if (manager == null || !manager.isRequestPinShortcutSupported) {
                        Toast.makeText(context, R.string.playlist_shortcut_unsupported, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val bitmap = withContext(Dispatchers.IO) {
                        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                            val scale = 512f / maxOf(info.size.width, info.size.height)
                            decoder.setTargetSize(
                                (info.size.width * scale.coerceAtMost(1f)).toInt().coerceAtLeast(1),
                                (info.size.height * scale.coerceAtMost(1f)).toInt().coerceAtLeast(1)
                            )
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                        try {
                            Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { output ->
                                val edge = minOf(decoded.width, decoded.height)
                                val left = (decoded.width - edge) / 2
                                val top = (decoded.height - edge) / 2
                                Canvas(output).drawBitmap(decoded, Rect(left, top, left + edge, top + edge), Rect(0, 0, 512, 512), Paint(Paint.FILTER_BITMAP_FLAG))
                            }
                        } finally {
                            decoded.recycle()
                        }
                    }
                    try {
                        val appLabel = context.getString(R.string.app_name)
                        val savedItem = CustomLauncherIconStore.saveIcon(context, bitmap, appLabel)
                        icons = CustomLauncherIconStore.loadIcons(context)
                        val accepted = CustomLauncherIconStore.pinShortcut(context, savedItem, bitmap)
                        Toast.makeText(
                            context,
                            if (accepted) context.getString(R.string.playlist_shortcut_requested, appLabel)
                            else context.getString(R.string.playlist_shortcut_unsupported),
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        bitmap.recycle()
                    }
                } catch (error: Exception) {
                    Toast.makeText(context, context.getString(R.string.settings_custom_image_save_failed), Toast.LENGTH_LONG).show()
                } finally {
                    busy = false
                }
            }
        }
    }

    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.custom_launcher_icon_manage),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // Add new icon button
            Button(
                enabled = !busy,
                onClick = { picker.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.custom_launcher_icon_add),
                    color = MiuixTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (icons.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.custom_launcher_icon_empty),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                    icons.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch {
                                        val accepted = CustomLauncherIconStore.pinShortcut(context, item)
                                        Toast.makeText(
                                            context,
                                            if (accepted) context.getString(R.string.playlist_shortcut_requested, item.label)
                                            else context.getString(R.string.playlist_shortcut_unsupported),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                            cornerRadius = 16.dp,
                            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = File(item.filePath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.label,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = dateFormat.format(Date(item.createdAt)),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val accepted = CustomLauncherIconStore.pinShortcut(context, item)
                                            Toast.makeText(
                                                context,
                                                if (accepted) context.getString(R.string.playlist_shortcut_requested, item.label)
                                                else context.getString(R.string.playlist_shortcut_unsupported),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Regular.Pin,
                                        contentDescription = stringResource(R.string.playlist_shortcut_requested, item.label),
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { pendingDeleteItem = item }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Regular.Delete,
                                        contentDescription = stringResource(R.string.custom_launcher_icon_delete),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ConfirmDangerDialog(
        show = pendingDeleteItem != null,
        title = stringResource(R.string.custom_launcher_icon_delete),
        message = stringResource(R.string.custom_launcher_icon_delete_confirm),
        onDismiss = { pendingDeleteItem = null },
        onConfirm = {
            val item = pendingDeleteItem ?: return@ConfirmDangerDialog
            pendingDeleteItem = null
            scope.launch {
                CustomLauncherIconStore.deleteIcon(context, item.id)
                icons = CustomLauncherIconStore.loadIcons(context)
            }
        }
    )
}
