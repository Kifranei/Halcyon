package com.ella.music.ui.about

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.ella.music.BuildConfig
import com.ella.music.data.AppNetworkLoggingInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(
        val assetName: String,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : UpdateDownloadState

    data class Completed(
        val apkFile: File,
        val assetName: String
    ) : UpdateDownloadState

    data class Failed(
        val error: String,
        val assetName: String
    ) : UpdateDownloadState
}

internal object UpdateDownloadManager {
    private const val TAG = "UpdateDownloadManager"

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AppNetworkLoggingInterceptor("UpdateDownload"))
            .build()
    }

    fun getUpdatesDir(context: Context): File {
        return File(context.cacheDir, "updates").apply { mkdirs() }
    }

    fun checkExistingApk(
        context: Context,
        asset: ReleaseApkAsset,
        expectedVersion: String? = null
    ): File? {
        val updatesDir = getUpdatesDir(context)
        val targetFile = File(updatesDir, asset.name)
        if (isApkValid(context, targetFile, expectedVersion)) {
            _downloadState.value = UpdateDownloadState.Completed(targetFile, asset.name)
            return targetFile
        }
        return null
    }

    fun isApkValid(
        context: Context,
        file: File,
        expectedVersion: String? = null
    ): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        return runCatching {
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            }
            if (archiveInfo == null) return false
            if (archiveInfo.packageName != context.packageName) return false
            if (expectedVersion != null) {
                val fileVer = archiveInfo.versionName?.trim()?.removePrefix("v")?.removePrefix("V")
                val expVer = expectedVersion.trim().removePrefix("v").removePrefix("V")
                if (fileVer != null && fileVer != expVer) return false
            }
            true
        }.getOrDefault(false)
    }

    fun startDownload(
        context: Context,
        asset: ReleaseApkAsset,
        expectedVersion: String? = null
    ) {
        if (_downloadState.value is UpdateDownloadState.Downloading) {
            return
        }

        val updatesDir = getUpdatesDir(context)
        val targetFile = File(updatesDir, asset.name)
        if (isApkValid(context, targetFile, expectedVersion)) {
            _downloadState.value = UpdateDownloadState.Completed(targetFile, asset.name)
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            val tempFile = File(updatesDir, "${asset.name}.downloading")
            try {
                // Clean up old apk or temporary files in updates directory
                updatesDir.listFiles()?.forEach { file ->
                    if (file.name != asset.name && file.name != tempFile.name) {
                        runCatching { file.delete() }
                    }
                }

                if (tempFile.exists()) {
                    tempFile.delete()
                }

                _downloadState.value = UpdateDownloadState.Downloading(
                    assetName = asset.name,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = asset.sizeBytes,
                    speedBytesPerSec = 0L
                )

                val request = Request.Builder()
                    .url(asset.downloadUrl)
                    .header("User-Agent", "Halcyon/${BuildConfig.VERSION_NAME}")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Server returned HTTP ${response.code}")
                    }
                    val body = response.body ?: error("Empty response body")
                    val totalBytes = if (asset.sizeBytes > 0) asset.sizeBytes else body.contentLength()

                    val buffer = ByteArray(32 * 1024)
                    var downloadedBytes = 0L
                    var lastSampleTime = System.currentTimeMillis()
                    var lastSampleBytes = 0L
                    var speed = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloadedBytes += read

                                val now = System.currentTimeMillis()
                                val timeDiff = now - lastSampleTime
                                if (timeDiff >= 300) {
                                    val bytesDiff = downloadedBytes - lastSampleBytes
                                    val instantaneousSpeed = (bytesDiff * 1000L) / timeDiff
                                    speed = if (speed == 0L) instantaneousSpeed else (speed * 3 + instantaneousSpeed) / 4
                                    lastSampleTime = now
                                    lastSampleBytes = downloadedBytes

                                    val progress = if (totalBytes > 0) {
                                        (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                    } else {
                                        0f
                                    }

                                    _downloadState.value = UpdateDownloadState.Downloading(
                                        assetName = asset.name,
                                        progress = progress,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = speed
                                    )
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                if (!isApkValid(context, targetFile, expectedVersion)) {
                    targetFile.delete()
                    error("Downloaded file is not a valid APK")
                }

                _downloadState.value = UpdateDownloadState.Completed(
                    apkFile = targetFile,
                    assetName = asset.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                if (tempFile.exists()) {
                    runCatching { tempFile.delete() }
                }
                _downloadState.value = UpdateDownloadState.Failed(
                    error = e.localizedMessage ?: "Download failed",
                    assetName = asset.name
                )
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val opened = runCatching {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        true
                    }.getOrDefault(false)
                    if (opened) return
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start install intent", e)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return "${formatBytes(bytesPerSec)}/s"
    }
}
