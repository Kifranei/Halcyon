package com.ella.music.video

import android.app.*
import android.content.*
import android.net.Uri
import android.os.IBinder
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.ella.music.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class VideoDownloadState(val busy: Boolean = false, val title: String = "",
    val progress: Int? = null, val savedUri: String? = null, val error: String? = null)

/** Owns download jobs beyond the settings screen; only publishes complete output files. */
class VideoDownloadService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var runner: Job?=null
    private var publishingUri: Uri?=null
    private var lastUpdate=0L
    private val manager get()=getSystemService(NotificationManager::class.java)
    private val prefs get()=getSharedPreferences("video_download_recovery",MODE_PRIVATE)
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel(CHANNEL,getString(R.string.video_tools_title),NotificationManager.IMPORTANCE_LOW))
        prefs.getString("pending",null)?.let { runCatching { contentResolver.delete(Uri.parse(it),null,null) } }
        prefs.edit().remove("pending").apply()
        File(cacheDir,"video-tools").listFiles()?.filter { it.name.startsWith("export-") || it.name.startsWith("playlist-") }?.forEach { it.delete() }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action==CANCEL) { runner?.cancel();if(runner==null) stopSelf();return START_NOT_STICKY }
        if(runner?.isActive==true) return START_NOT_STICKY
        val source=intent?.getStringExtra("uri") ?: run { stopSelf();return START_NOT_STICKY }
        val item=VideoSource(source,intent.getStringExtra("title").orEmpty(),intent.getStringExtra("mime"),
            intent.getBooleanExtra("hls",false),intent.getLongExtra("duration",0L))
        mutableState.value=VideoDownloadState(true,item.title)
        startForeground(NOTIFICATION,notification(item.title,null).build())
        runner=scope.launch {
            var finalState = VideoDownloadState(title=item.title)
            try {
                val saved=withContext(Dispatchers.IO) { download(item) }
                finalState=VideoDownloadState(title=item.title,savedUri=saved.toString())
                manager.notify(RESULT,notification(getString(R.string.video_download_complete),100,saved).setOngoing(false).setAutoCancel(true).build())
            } catch(cancel:CancellationException) {
                finalState=VideoDownloadState(title=item.title)
            } catch(error:Exception) {
                android.util.Log.w("VideoDownload","Video download failed",error)
                finalState=VideoDownloadState(title=item.title,error=error.message ?: error.javaClass.simpleName)
                manager.notify(RESULT,notification(getString(R.string.video_download_failed),null).setOngoing(false).setAutoCancel(true).clearActions().build())
            } finally {
                withContext(NonCancellable+Dispatchers.IO) {
                    publishingUri?.let { contentResolver.delete(it,null,null) };publishingUri=null
                    prefs.edit().remove("pending").commit()
                }
                runner=null
                stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId)
                mutableState.value=finalState
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
    override fun onTimeout(startId: Int, fgsType: Int) { runner?.cancel();stopSelf(startId) }

    private fun notification(title: String, percent: Int?, saved: Uri?=null): NotificationCompat.Builder {
        val open=Intent(this,com.ella.music.VideoPlayerActivity::class.java).apply {
            if(saved!=null) { action=Intent.ACTION_VIEW;data=saved;addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        val pending=PendingIntent.getActivity(this,NOTIFICATION,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(com.ella.music.data.AppIconManager.notificationIconRes())
            .setContentTitle(getString(R.string.video_tools_title)).setContentText(title)
            .setContentIntent(pending).setOnlyAlertOnce(true).setOngoing(true)
            .apply {
                if(saved==null) {
                    setProgress(100,percent ?: 0,percent==null)
                    val cancel=PendingIntent.getService(this@VideoDownloadService,0,Intent(this@VideoDownloadService,VideoDownloadService::class.java).setAction(CANCEL),PendingIntent.FLAG_IMMUTABLE)
                    addAction(0,getString(R.string.common_cancel),cancel)
                }
            }
    }
    private fun progress(item: VideoSource, value: Int?) {
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastUpdate<400) return
        lastUpdate=now
        mutableState.value=VideoDownloadState(true,item.title,value)
        manager.notify(NOTIFICATION,notification(item.title,value).build())
    }
    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    private suspend fun download(item: VideoSource): Uri {
        val input=Uri.parse(item.uri)
        if(item.hls) {
            val folder=File(cacheDir,"video-tools").apply { mkdirs() }
            val output=File(folder,"export-${UUID.randomUUID()}.mp4")
            var localPlaylist: File?=null
            try {
                // FFmpeg cannot directly open a content URI. Copy the playlist text and keep
                // absolute network references, while rejecting inaccessible SAF-relative segments.
                val source=if(input.scheme=="content") {
                    localPlaylist=File(folder,"playlist-${UUID.randomUUID()}.m3u8")
                    contentResolver.openInputStream(input)?.use { i -> localPlaylist!!.outputStream().use { i.copyTo(it) } }
                        ?: throw IOException("Cannot open playlist")
                    localPlaylist!!.absolutePath
                } else if(input.scheme=="file") input.path.orEmpty() else item.uri
                remux(item,source,output)
                require(output.exists() && output.length()>0) { "No video output" }
                return output.inputStream().use { publish(it,safeVideoName(item.title,"mp4"),"video/mp4",output.length(),item) }
            } finally { output.delete();localPlaylist?.delete() }
        }
        if(input.scheme in listOf("https","http")) {
            val call=VideoSourceResolver.http.newCall(Request.Builder().url(item.uri).build())
            val cancellation=currentCoroutineContext()[Job]!!.invokeOnCompletion(onCancelling=true,invokeImmediately=true) { if(it is CancellationException) call.cancel() }
            try {
                call.execute().use { response ->
                    if(!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body=response.body ?: throw IOException("Empty response")
                    if(isPlaylistContentType(response.header("Content-Type"))) throw IOException(getString(R.string.video_resolve_first))
                    val mime=response.header("Content-Type")?.substringBefore(';') ?: item.mimeType ?: "video/mp4"
                    val ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                        ?: Uri.parse(response.request.url.toString()).lastPathSegment?.substringAfterLast('.',"")?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,5}")) } ?: "mp4"
                    return body.byteStream().use { publish(it,safeVideoName(item.title,ext),mime,body.contentLength(),item) }
                }
            } finally { cancellation.dispose() }
        }
        val mime=item.mimeType ?: contentResolver.getType(input) ?: "video/mp4"
        val ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: input.lastPathSegment?.substringAfterLast('.',"mp4") ?: "mp4"
        return contentResolver.openInputStream(input)?.use { publish(it,safeVideoName(item.title,ext),mime,-1,item) }
            ?: throw IOException("Cannot open video")
    }
    private suspend fun publish(input: java.io.InputStream, name: String, mime: String, total: Long, item: VideoSource): Uri {
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Halcyon/Videos")
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val target=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: throw IOException("Cannot create download")
        publishingUri=target;prefs.edit().putString("pending",target.toString()).commit()
        var copied=0L
        contentResolver.openOutputStream(target,"w")?.use { output ->
            val buffer=ByteArray(128*1024)
            while(true) {
                currentCoroutineContext().ensureActive()
                val n=input.read(buffer);if(n<0) break
                output.write(buffer,0,n);copied+=n
                progress(item,if(total>0) (copied*100/total).toInt().coerceIn(0,99) else null)
            }
        } ?: throw IOException("Cannot save download")
        require(copied>0) { "Empty video" }
        if(total>0 && copied!=total) throw IOException("Incomplete download")
        contentResolver.update(target,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
        publishingUri=null;prefs.edit().remove("pending").commit()
        return target
    }
    private suspend fun remux(item: VideoSource, source: String, output: File) = suspendCancellableCoroutine<Unit> { continuation ->
        val args=arrayOf("-nostdin","-y","-protocol_whitelist",if(source.startsWith("http")) "http,https,tcp,tls,crypto,data" else "file,http,https,tcp,tls,crypto,data","-i",source,
            "-map","0:v:0?","-map","0:a:0?","-c","copy","-movflags","+faststart",output.absolutePath)
        val session=FFmpegKit.executeWithArgumentsAsync(args,{ finished ->
            if(continuation.isActive) {
                if(ReturnCode.isSuccess(finished.returnCode)) continuation.resume(Unit)
                else continuation.resumeWithException(IOException(finished.allLogsAsString.lineSequence().filter { it.isNotBlank() }.toList().takeLast(4).joinToString(" ").take(600)))
            }
        },{ _ -> },{ statistics ->
            val percent=if(item.durationMs>0) (statistics.time.toLong()*100/item.durationMs).toInt().coerceIn(0,99) else null
            if (continuation.isActive) progress(item,percent)
        })
        continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }
    companion object {
        private const val CHANNEL="video_downloads"
        private const val NOTIFICATION=0x5649
        private const val RESULT=0x564A
        private const val CANCEL="com.ella.music.CANCEL_VIDEO_DOWNLOAD"
        private val mutableState=MutableStateFlow(VideoDownloadState())
        internal val state=mutableState.asStateFlow()
        internal fun start(context: Context, source: VideoSource) {
            if(state.value.busy) return
            require(!source.hls || source.exportable) { context.getString(R.string.video_live_export_unavailable) }
            val intent=Intent(context,VideoDownloadService::class.java).putExtra("uri",source.uri)
                .putExtra("title",source.title).putExtra("mime",source.mimeType).putExtra("hls",source.hls).putExtra("duration",source.durationMs)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData=ClipData.newUri(context.contentResolver,source.title,Uri.parse(source.uri)) }
            mutableState.value=VideoDownloadState(true,source.title)
            try { ContextCompat.startForegroundService(context,intent) }
            catch(error:Exception) { mutableState.value=VideoDownloadState(error=error.message);throw error }
        }
        internal fun cancel(context: Context) { if(state.value.busy) context.startService(Intent(context,VideoDownloadService::class.java).setAction(CANCEL)) }
    }
}
