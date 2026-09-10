package com.ella.music.ui.components

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.MusicVideoLauncher
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.player.DynamicCoverSource
import com.ella.music.ui.player.PlayerVideoRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LyricVideoEffectDialog(
    show: Boolean,
    currentEffect: LyricVideoEffect,
    onDismiss: () -> Unit,
    onSelectEffect: (LyricVideoEffect) -> Unit
) {
    if (!show) return
    var selected by remember(currentEffect) { mutableStateOf(currentEffect) }

    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.lyric_video_effect_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                colors = CardDefaults.defaultColors(
                    color = ellaOverlayCardColor()
                )
            ) {
                LyricVideoEffect.entries.forEach { effect ->
                    val isCurrent = effect == selected
                    BasicComponent(
                        title = stringResource(effect.labelRes),
                        onClick = { selected = effect },
                        insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        endActions = {
                            SelectionCheck(
                                selected = isCurrent,
                                size = 22.dp,
                                unselectedColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.18f)
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSelectEffect(selected)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.common_confirm)
                )
            }
        }
    }
}

@Composable
internal fun LyricVideoCompletedSheet(
    show: Boolean,
    videoUri: Uri?,
    song: Song?,
    destinationTreeUri: String = "",
    onDismiss: () -> Unit
) {
    if (!show || videoUri == null) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.lyric_video_completed_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 预览
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val resolvedSong = song ?: Song(
                                id = 0L,
                                title = "Lyric Video",
                                artist = "",
                                album = "",
                                albumId = 0L,
                                duration = 0L,
                                path = videoUri.toString(),
                                fileName = "lyric_video.mp4"
                            )
                            MusicVideoLauncher.open(
                                context = context,
                                song = resolvedSong,
                                source = DynamicCoverSource(
                                    uri = videoUri,
                                    failureKey = videoUri.toString(),
                                    aspectRatio = 1f,
                                    role = PlayerVideoRole.MusicVideo
                                )
                            )
                        },
                    cornerRadius = 16.dp,
                    colors = CardDefaults.defaultColors(color = ellaOverlayCardColor())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Play,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.lyric_video_preview),
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 保存
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !saving) {
                            saving = true
                            scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    saveVideoToGallery(context, videoUri, song?.title ?: "lyric_video")
                                }
                                saving = false
                                if (success) {
                                    Toast.makeText(context, context.getString(R.string.lyric_video_saved_toast), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.settings_custom_image_save_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    cornerRadius = 16.dp,
                    colors = CardDefaults.defaultColors(color = ellaOverlayCardColor())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Download,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.lyric_video_save),
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 分享
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            shareLyricVideoFile(context, videoUri, destinationTreeUri)
                        },
                    cornerRadius = 16.dp,
                    colors = CardDefaults.defaultColors(color = ellaOverlayCardColor())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Share,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.common_share),
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

internal fun saveVideoToGallery(context: Context, videoUri: Uri, title: String): Boolean {
    val resolver = context.contentResolver
    val cleanTitle = title.replace(Regex("[\\/:*?\"<>|]"), "_").take(40).ifBlank { "lyric_video" }
    val fileName = "${cleanTitle}_${System.currentTimeMillis()}.mp4"
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Halcyon")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    val itemUri = resolver.insert(collection, contentValues) ?: return false
    return runCatching {
        resolver.openInputStream(videoUri)?.use { input ->
            resolver.openOutputStream(itemUri)?.use { output ->
                input.copyTo(output)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
        }
        true
    }.getOrDefault(false)
}
