package com.ella.music.ui.components

import android.content.ClipData
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ella.music.R
import com.ella.music.data.AppLogStore
import com.ella.music.data.AppLogType
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.model.Song
import java.io.File

data class TagEditorOption(
    val id: String,
    val label: String,
    val summary: String,
    val kind: TagEditorOptionKind,
    val intents: List<Intent>,
    val sourceSong: Song? = null
)

enum class TagEditorOptionKind {
    Metadata,
    LyricTiming
}

object TagEditorOptionIds {
    const val ASK_EACH_TIME = ""
    const val LYRICO = "lyrico"
    const val BUILTIN_CUSTOM_TAG = "builtin_custom_tag"
    const val BUILTIN_METADATA_EDITOR = BUILTIN_CUSTOM_TAG
    const val BUILTIN_LYRIC_TIMING = "builtin_lyric_timing"
    const val LUNABEAT_METADATA = "lunabeat_metadata"
    const val LUNABEAT_LYRIC_TIMING = "lunabeat_lyric_timing"
    const val MUSIC_TAG = "music_tag"
}

object TagEditorEditTracker {
    var pendingSong: Song? = null
        private set
    var launchedAtMs: Long = 0L
        private set

    fun mark(song: Song?) {
        pendingSong = song
        launchedAtMs = System.currentTimeMillis()
    }

    fun consume(): Song? {
        val song = pendingSong
        pendingSong = null
        launchedAtMs = 0L
        return song
    }
}

fun buildTagEditorOptions(context: Context, song: Song): List<TagEditorOption> {
    if (song.path.isHttpAudioSource()) {
        return emptyList()
    }

    val mediaStoreUri = song.id.takeIf { it > 0L }?.let { id ->
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }
    val songFile = File(song.path)
    val fileUri = songFile.takeIf { it.exists() && it.isFile }?.let { file ->
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
    val defaultEditUri = fileUri ?: mediaStoreUri ?: return emptyList()

    val mimeType = song.mimeType
        .takeIf { it.startsWith("audio/") }
        ?: "audio/*"

    fun tagEditorIntent(
        label: String,
        action: String? = null,
        component: ComponentName? = null,
        packageName: String? = null,
        dataUri: Uri = defaultEditUri,
        streamUri: Uri = defaultEditUri,
        contentUri: Uri = dataUri,
        includeStreamExtra: Boolean = true
    ): Intent {
        return Intent(action ?: Intent.ACTION_EDIT).apply {
            component?.let { setComponent(it) }
            packageName?.let { setPackage(it) }
            if (this.action == Intent.ACTION_SEND) {
                type = mimeType
            } else {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(dataUri, mimeType)
            }
            if (includeStreamExtra) {
                putExtra(Intent.EXTRA_STREAM, streamUri)
            }
            putExtra(Intent.EXTRA_TITLE, "${song.title} - ${song.artist}")
            putExtra("title", song.title)
            putExtra("artist", song.artist)
            putExtra("album", song.album)
            putExtra("path", song.path)
            putExtra("filePath", song.path)
            putExtra("id", song.id)
            putExtra("songId", song.id)
            putExtra("mediaId", song.id)
            putExtra("uri", dataUri.toString())
            putExtra("contentUrl", contentUri.toString())
            putExtra("contentUri", contentUri.toString())
            putExtra("contenturl", contentUri.toString())
            putExtra("content_uri", contentUri.toString())
            mediaStoreUri?.let { putExtra("mediaStoreUri", it.toString()) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(tagEditorActivityFlags())
            clipData = ClipData.newUri(context.contentResolver, label, dataUri)
        }
    }

    fun Intent.canOpen(): Boolean {
        component?.let { explicitComponent ->
            val explicitOpen = runCatching {
                context.packageManager.getActivityInfo(explicitComponent, 0)
                true
            }.getOrDefault(false)
            if (explicitOpen) return true
        }
        return context.packageManager.queryIntentActivities(
            this,
            PackageManager.MATCH_DEFAULT_ONLY
        ).isNotEmpty()
    }

    val musicTagComponent = ComponentName(
        "com.xjcheng.musictageditor",
        "com.xjcheng.musictageditor.SongDetailActivity"
    )
    val musicTagEditUri = defaultEditUri
    fun musicTagFilePathIntent(): Intent {
        return Intent(Intent.ACTION_EDIT).apply {
            component = musicTagComponent
            putExtra(Intent.EXTRA_TITLE, "${song.title} - ${song.artist}")
            putExtra("title", song.title)
            putExtra("artist", song.artist)
            putExtra("album", song.album)
            putExtra("display_name", song.fileName.ifBlank { song.title })
            putExtra("filepath", song.path)
            putExtra("path", song.path)
            putExtra("filePath", song.path)
            putExtra("id", song.id)
            putExtra("songId", song.id)
            putExtra("mediaId", song.id)
            mediaStoreUri?.let {
                putExtra("uri", it.toString())
                putExtra("mediaStoreUri", it.toString())
            }
            addFlags(tagEditorActivityFlags())
        }
    }

    fun lunaBeatIntent(component: ComponentName): Intent {
        return Intent(Intent.ACTION_EDIT).apply {
            this.component = component
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(defaultEditUri, mimeType)
            putExtra(Intent.EXTRA_TITLE, "${song.title} - ${song.artist}")
            putExtra(Intent.EXTRA_STREAM, defaultEditUri)
            putExtra("audioPath", song.path)
            putExtra("audio_path", song.path)
            putExtra("source_audio_path", song.path)
            putExtra("sourceTitle", song.title)
            putExtra("sourceArtist", song.artist)
            putExtra("media_store_id", song.id)
            putExtra("title", song.title)
            putExtra("artist", song.artist)
            putExtra("album", song.album)
            putExtra("path", song.path)
            putExtra("filePath", song.path)
            putExtra("id", song.id)
            putExtra("songId", song.id)
            putExtra("mediaId", song.id)
            putExtra("uri", defaultEditUri.toString())
            putExtra("contentUrl", defaultEditUri.toString())
            putExtra("contentUri", defaultEditUri.toString())
            putExtra("contenturl", defaultEditUri.toString())
            putExtra("content_uri", defaultEditUri.toString())
            mediaStoreUri?.let { putExtra("mediaStoreUri", it.toString()) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(tagEditorActivityFlags())
            clipData = ClipData.newUri(context.contentResolver, "LunaBeat", defaultEditUri)
        }
    }

    fun lunaBeatSendIntent(label: String, component: ComponentName? = null): Intent {
        return tagEditorIntent(
            label = label,
            action = Intent.ACTION_SEND,
            component = component,
            packageName = if (component == null) "com.example.LyricBox" else null
        )
    }

    return listOf(
        TagEditorOption(
            id = TagEditorOptionIds.LYRICO,
            label = "Lyrico",
            summary = context.getString(R.string.tag_editor_lyrico_summary),
            kind = TagEditorOptionKind.Metadata,
            intents = listOf(
                tagEditorIntent(
                    label = "Lyrico",
                    action = "com.lonx.lyrico.action.EDIT_TAG",
                    packageName = "com.lonx.lyrico"
                )
            ),
            sourceSong = song
        ),
        TagEditorOption(
            id = TagEditorOptionIds.LUNABEAT_METADATA,
            label = context.getString(R.string.settings_editor_lunabeat_metadata),
            summary = context.getString(R.string.tag_editor_lunabeat_metadata_summary),
            kind = TagEditorOptionKind.Metadata,
            intents = listOf(
                lunaBeatIntent(
                    ComponentName("com.example.LyricBox", "com.example.LyricBox.SongMetadataEditActivity")
                ),
                lunaBeatIntent(
                    ComponentName("com.example.lyricbox", "com.example.LyricBox.SongMetadataEditActivity")
                ),
                lunaBeatSendIntent(
                    label = context.getString(R.string.settings_editor_lunabeat_metadata),
                    component = ComponentName("com.example.LyricBox", "com.example.LyricBox.SongMetadataEditActivity")
                )
            ),
            sourceSong = song
        ),
        TagEditorOption(
            id = TagEditorOptionIds.LUNABEAT_LYRIC_TIMING,
            label = context.getString(R.string.settings_editor_lunabeat_lyric_timing),
            summary = context.getString(R.string.tag_editor_lunabeat_lyric_timing_summary),
            kind = TagEditorOptionKind.LyricTiming,
            intents = listOf(
                lunaBeatIntent(
                    ComponentName("com.example.LyricBox", "com.example.LyricBox.LyricTimingActivity")
                ),
                lunaBeatIntent(
                    ComponentName("com.example.lyricbox", "com.example.LyricBox.LyricTimingActivity")
                ),
                lunaBeatSendIntent(
                    label = context.getString(R.string.settings_editor_lunabeat_lyric_timing),
                    component = ComponentName("com.example.LyricBox", "com.example.LyricBox.LyricTimingActivity")
                )
            ),
            sourceSong = song
        ),
        TagEditorOption(
            id = TagEditorOptionIds.MUSIC_TAG,
            label = context.getString(R.string.settings_editor_music_tag),
            summary = context.getString(R.string.tag_editor_music_tag_summary),
            kind = TagEditorOptionKind.Metadata,
            intents = listOf(
                musicTagFilePathIntent(),
                tagEditorIntent(
                    label = context.getString(R.string.settings_editor_music_tag),
                    action = Intent.ACTION_VIEW,
                    component = musicTagComponent,
                    dataUri = musicTagEditUri,
                    streamUri = musicTagEditUri,
                    contentUri = musicTagEditUri,
                    includeStreamExtra = false
                ),
                tagEditorIntent(
                    label = context.getString(R.string.settings_editor_music_tag),
                    action = Intent.ACTION_VIEW,
                    packageName = "com.xjcheng.musictageditor",
                    dataUri = musicTagEditUri,
                    streamUri = musicTagEditUri,
                    contentUri = musicTagEditUri,
                    includeStreamExtra = false
                ),
                tagEditorIntent(
                    label = context.getString(R.string.settings_editor_music_tag),
                    action = Intent.ACTION_SEND,
                    packageName = "com.xjcheng.musictageditor",
                    dataUri = musicTagEditUri,
                    streamUri = musicTagEditUri,
                    contentUri = musicTagEditUri
                )
            ),
            sourceSong = song
        )
    ).mapNotNull { option ->
        option.copy(intents = option.intents.filter { it.canOpen() })
            .takeIf { it.intents.isNotEmpty() }
    }
}

fun launchTagEditorOption(context: Context, option: TagEditorOption) {
    val launched = option.intents.any { intent ->
        runCatching {
            intent.addFlags(tagEditorActivityFlags())
            val targetPackage = intent.component?.packageName ?: intent.`package`
            targetPackage?.let { packageName ->
                context.grantTagEditorUriPermission(packageName, intent.data)
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
                context.grantTagEditorUriPermission(packageName, streamUri)
            }
            context.startActivity(intent)
            TagEditorEditTracker.mark(option.sourceSong)
            AppLogStore.info(
                context,
                "TagEditor",
                "Launched ${option.label}: ${intent.describeForLog()}",
                AppLogType.METADATA
            )
            true
        }.onFailure { error ->
            AppLogStore.error(
                context,
                "TagEditor",
                "Launch ${option.label} failed: ${intent.describeForLog()}",
                error,
                AppLogType.METADATA
            )
        }.getOrDefault(false)
    }
    if (!launched) {
        Toast.makeText(context, R.string.tag_editor_open_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun tagEditorActivityFlags(): Int = 0

private fun Context.grantTagEditorUriPermission(packageName: String, uri: Uri?) {
    if (uri == null) return
    runCatching {
        grantUriPermission(
            packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }.onFailure { error ->
        AppLogStore.warn(
            this,
            "TagEditor",
            "Grant uri permission failed for $packageName uri=$uri",
            error
        )
    }
}

private fun Intent.describeForLog(): String {
    val target = component?.flattenToShortString() ?: `package`.orEmpty()
    return "action=$action target=$target data=$data type=$type"
}
