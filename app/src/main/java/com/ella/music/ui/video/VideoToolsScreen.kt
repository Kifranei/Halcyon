package com.ella.music.ui.video

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.MusicVideoLauncher
import com.ella.music.R
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.settings.SettingsCardGroup
import com.ella.music.video.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun VideoToolsScreen(onBack: () -> Unit, initialSource: String = "", initialMime: String? = null,
                              autoPlay: Boolean = false) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var address by rememberSaveable(initialSource) { mutableStateOf(initialSource) }
    var sources by remember { mutableStateOf(emptyList<VideoSource>()) }
    var resolving by remember { mutableStateOf(false) }
    var preparingDownload by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val download by VideoDownloadService.state.collectAsState()
    fun play(source: VideoSource) {
        scope.launch {
            try {
                val resolved=if(source.needsResolution) VideoSourceResolver.resolve(context,source.uri) else listOf(source)
                if(resolved.size==1) {
                    val item=resolved.single()
                    MusicVideoLauncher.openVideo(context,Uri.parse(item.uri),source.title,item.mimeType)
                } else sources=resolved
            } catch(cancel:CancellationException) { throw cancel }
            catch(failure:Exception) { error=failure.message }
        }
    }
    fun resolve(source: String, startPlayback: Boolean, mime: String? = null) {
        if(resolving) return
        address=source;error=null;sources=emptyList();resolving=true
        scope.launch {
            try {
                sources=VideoSourceResolver.resolve(context,source.trim(),mime)
                if(startPlayback && sources.size==1) play(sources.single())
            } catch(cancel:CancellationException) { throw cancel }
            catch(failure:Exception) { error=failure.message }
            finally { resolving=false }
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            resolve(it.toString(),true,context.contentResolver.getType(it))
        }
    }
    LaunchedEffect(initialSource) { if(initialSource.isNotBlank()) resolve(initialSource,autoPlay,initialMime) }
    Scaffold(topBar={
        EllaSmallTopAppBar(title=stringResource(R.string.video_tools_title),navigationIcon={
            IconButton(onClick=onBack) { Icon(MiuixIcons.Regular.Back,stringResource(R.string.common_back)) }
        })
    }) { insets ->
        LazyColumn(modifier=Modifier.fillMaxSize().padding(insets),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.video_tools_summary),color=MiuixTheme.colorScheme.onBackground)
                Spacer(Modifier.height(16.dp))
                SettingsCardGroup {
                    Box(Modifier.fillMaxWidth().padding(18.dp)) {
                        if(address.isBlank()) Text(stringResource(R.string.video_source_hint),color=MiuixTheme.colorScheme.onSurface.copy(alpha=.5f))
                        BasicTextField(value=address,onValueChange={ address=it;sources=emptyList();error=null },
                            enabled=!resolving,modifier=Modifier.fillMaxWidth(),maxLines=4,
                            textStyle=MiuixTheme.textStyles.body1.copy(color=MiuixTheme.colorScheme.onSurface),
                            cursorBrush=SolidColor(MiuixTheme.colorScheme.primary))
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={ resolve(address,false) },enabled=address.isNotBlank() && !resolving,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.video_open_source)) }
                    Button(onClick={ picker.launch(arrayOf("video/*","application/vnd.apple.mpegurl","application/x-mpegurl","audio/x-mpegurl","text/plain")) },enabled=!resolving,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.video_open_file)) }
                }
                if(resolving) { Spacer(Modifier.height(12.dp));Text(stringResource(R.string.video_resolving)) }
                error?.let { Spacer(Modifier.height(12.dp));Text(it,color=MiuixTheme.colorScheme.error) }
            }
            if(download.busy || download.savedUri!=null || download.error!=null) item {
                SettingsCardGroup {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(download.title)
                        if(download.busy) {
                            Text(stringResource(R.string.video_download_progress,download.progress?.let { "$it%" } ?: "…"))
                            Button(onClick={ VideoDownloadService.cancel(context) }) { Text(stringResource(R.string.common_cancel)) }
                        } else if(download.savedUri!=null) {
                            Text(stringResource(R.string.video_download_complete))
                            Text("Download/Halcyon/Videos",color=MiuixTheme.colorScheme.onSurface.copy(alpha=.6f))
                            Button(onClick={ play(VideoSource(download.savedUri!!,download.title,context.contentResolver.getType(Uri.parse(download.savedUri!!)))) }) { Text(stringResource(R.string.video_play)) }
                        } else download.error?.let { Text(it,color=MiuixTheme.colorScheme.error) }
                    }
                }
            }
            items(sources,key={ it.uri }) { source ->
                SettingsCardGroup {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(source.title,style=MiuixTheme.textStyles.title4)
                        Text(source.uri,maxLines=2,color=MiuixTheme.colorScheme.onSurface.copy(alpha=.6f))
                        if(source.hls && !source.exportable) Text(stringResource(R.string.video_live_export_unavailable))
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(onClick={ play(source) },modifier=Modifier.weight(1f)) { Text(stringResource(R.string.video_play)) }
                            Button(onClick={
                                preparingDownload = true
                                scope.launch {
                                    try {
                                        // A plain M3U can link to an HLS playlist; resolve the entry before export.
                                        val resolved=VideoSourceResolver.resolve(context,source.uri,source.mimeType)
                                        if(resolved.size==1) VideoDownloadService.start(context,resolved.single().copy(title=source.title))
                                        else sources=resolved
                                    } catch(cancel:CancellationException) { throw cancel }
                                    catch(failure:Exception) { error=failure.message }
                                    finally { preparingDownload = false }
                                }
                            },enabled=!preparingDownload && !download.busy && (!source.hls || source.exportable),modifier=Modifier.weight(1f)) {
                                Text(stringResource(if(source.hls) R.string.video_download_mp4 else R.string.video_download))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(150.dp)) }
        }
    }
}
