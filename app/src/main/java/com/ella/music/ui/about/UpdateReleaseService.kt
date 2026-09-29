package com.ella.music.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.BuildConfig
import com.ella.music.R
import com.ella.music.data.AppNetworkLoggingInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

internal sealed interface UpdateUiState {
    data object Loading : UpdateUiState
    data class Ready(val release: GithubRelease, val hasUpdate: Boolean) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

internal object AppUpdateStateHolder {
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow<UpdateUiState>(UpdateUiState.Loading)
    val uiState: kotlinx.coroutines.flow.StateFlow<UpdateUiState> = _uiState

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var checkJob: kotlinx.coroutines.Job? = null

    fun checkUpdate(context: Context, force: Boolean = false) {
        if (!force && _uiState.value is UpdateUiState.Ready) return
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            _uiState.value = UpdateUiState.Loading
            runCatching {
                val release = fetchLatestRelease(includePrereleases = UpdateChannelPreferences.includesPrereleases(context))
                val hasUpdate = compareVersionNames(release.versionName, BuildConfig.VERSION_NAME) > 0
                UpdateUiState.Ready(release, hasUpdate)
            }.onSuccess {
                _uiState.value = it
            }.onFailure {
                _uiState.value = UpdateUiState.Error(it.localizedMessage.orEmpty())
            }
        }
    }
}

internal data class ReleaseApkAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

internal data class GithubRelease(
    val tagName: String,
    val title: String,
    val body: String,
    val htmlUrl: String,
    val downloadUrl: String?,
    val publishedAt: String,
    val assets: List<ReleaseApkAsset> = emptyList(),
    val matchedAsset: ReleaseApkAsset? = null
) {
    val versionName: String get() = tagName.trim().removePrefix("v").removePrefix("V")
}

@Composable
internal fun UpdateUiState.heroTitle(): String = when (this) {
    UpdateUiState.Loading -> stringResource(R.string.update_checking)
    is UpdateUiState.Error -> stringResource(R.string.update_unavailable)
    is UpdateUiState.Ready -> if (hasUpdate) stringResource(R.string.update_found_version, release.tagName) else stringResource(R.string.update_already_latest)
}

@Composable
internal fun UpdateUiState.heroSummary(): String = when (this) {
    UpdateUiState.Loading -> stringResource(R.string.update_connecting_github)
    is UpdateUiState.Error -> message
    is UpdateUiState.Ready -> if (hasUpdate) {
        stringResource(R.string.update_has_update_summary, BuildConfig.VERSION_NAME)
    } else {
        stringResource(R.string.update_no_update_summary, BuildConfig.VERSION_NAME)
    }
}

@Composable
internal fun UpdateUiState.updateButtonText(): String = when (this) {
    UpdateUiState.Loading -> stringResource(R.string.update_checking_short)
    is UpdateUiState.Error -> stringResource(R.string.update_view_github)
    is UpdateUiState.Ready -> if (hasUpdate) stringResource(R.string.update_download) else stringResource(R.string.update_view_github)
}

internal fun UpdateUiState.updateButtonTargetUrl(): String? = when (this) {
    UpdateUiState.Loading -> null
    is UpdateUiState.Error -> GITHUB_RELEASES_URL
    is UpdateUiState.Ready -> if (hasUpdate) {
        release.downloadUrl ?: release.htmlUrl
    } else {
        release.htmlUrl.ifBlank { GITHUB_RELEASES_URL }
    }
}

private const val GITHUB_RELEASES_URL = "https://github.com/Kifranei/Halcyon/releases"

internal fun matchAssetForDevice(
    assets: List<ReleaseApkAsset>,
    supportedAbis: Array<String> = android.os.Build.SUPPORTED_ABIS
): ReleaseApkAsset? {
    val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    if (apkAssets.isEmpty()) return null
    if (apkAssets.size == 1) return apkAssets.first()

    // 1. Try matching preferred supported ABIs in order
    for (abi in supportedAbis) {
        val matched = when (abi.lowercase()) {
            "arm64-v8a" -> apkAssets.firstOrNull { asset ->
                val name = asset.name.lowercase()
                (name.contains("arm64-v8a") || name.contains("arm64") || name.contains("aarch64") || name.contains("v8a")) &&
                    !name.contains("v7a")
            }
            "armeabi-v7a" -> apkAssets.firstOrNull { asset ->
                val name = asset.name.lowercase()
                (name.contains("armeabi-v7a") || name.contains("armv7a") || name.contains("armv7") || name.contains("v7a")) &&
                    !name.contains("arm64") && !name.contains("v8a")
            }
            "armeabi" -> apkAssets.firstOrNull { asset ->
                val name = asset.name.lowercase()
                name.contains("armeabi") && !name.contains("v7a") && !name.contains("v8a") && !name.contains("arm64")
            }
            "x86_64" -> apkAssets.firstOrNull { asset ->
                val name = asset.name.lowercase()
                name.contains("x86_64") || name.contains("x64")
            }
            "x86" -> apkAssets.firstOrNull { asset ->
                val name = asset.name.lowercase()
                name.contains("x86") && !name.contains("x86_64") && !name.contains("x64")
            }
            else -> apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
        }
        if (matched != null) return matched
    }

    // 2. Try universal / all / fat
    val universal = apkAssets.firstOrNull { asset ->
        val name = asset.name.lowercase()
        name.contains("universal") || name.contains("all") || name.contains("fat")
    }
    if (universal != null) return universal

    // 3. Try generic apk without other abi keywords
    val generic = apkAssets.firstOrNull { asset ->
        val name = asset.name.lowercase()
        !name.contains("arm") && !name.contains("x86") && !name.contains("v7") && !name.contains("v8")
    }
    if (generic != null) return generic

    // 4. Fallback to first apk
    return apkAssets.first()
}

internal fun detectArchLabel(assetName: String): String? {
    val name = assetName.lowercase()
    return when {
        name.contains("arm64-v8a") || name.contains("arm64") || name.contains("aarch64") || name.contains("v8a") -> "arm64-v8a"
        name.contains("armeabi-v7a") || name.contains("armv7a") || name.contains("armv7") || name.contains("v7a") -> "armeabi-v7a"
        name.contains("x86_64") || name.contains("x64") -> "x86_64"
        name.contains("x86") -> "x86"
        name.contains("universal") -> "universal"
        else -> null
    }
}

internal fun fetchLatestRelease(includePrereleases: Boolean = false): GithubRelease {
    val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("UpdateCheck"))
        .build()
    val request = Request.Builder()
        .url("https://api.github.com/repos/Kifranei/Halcyon/releases?per_page=100")
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Halcyon/${BuildConfig.VERSION_NAME}")
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("GitHub returned HTTP ${response.code}")
        val body = response.body?.string().orEmpty()
        val json = selectRelease(JSONArray(body), includePrereleases)
            ?: error("No published release available for this channel")
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val assetList = (0 until assets.length())
            .asSequence()
            .mapNotNull { index -> assets.optJSONObject(index) }
            .mapNotNull { obj ->
                val name = obj.optString("name")
                val downloadUrl = obj.optString("browser_download_url")
                val size = obj.optLong("size", 0L)
                if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    ReleaseApkAsset(name = name, downloadUrl = downloadUrl, sizeBytes = size)
                } else {
                    null
                }
            }
            .toList()
        val matchedAsset = matchAssetForDevice(assetList)
        val downloadUrl = matchedAsset?.downloadUrl
            ?: assetList.firstOrNull()?.downloadUrl
            ?: (0 until assets.length())
                .asSequence()
                .mapNotNull { index -> assets.optJSONObject(index) }
                .firstOrNull { asset ->
                    asset.optString("name").endsWith(".apk", ignoreCase = true)
                }
                ?.optString("browser_download_url")
                ?.takeIf { it.isNotBlank() }

        return GithubRelease(
            tagName = json.optString("tag_name").ifBlank { json.optString("name") },
            title = json.optString("name").ifBlank { json.optString("tag_name") },
            body = json.optString("body"),
            htmlUrl = json.optString("html_url").ifBlank { "https://github.com/Kifranei/Halcyon/releases" },
            downloadUrl = downloadUrl,
            publishedAt = json.optString("published_at").take(10),
            assets = assetList,
            matchedAsset = matchedAsset
        )
    }
}

internal object UpdateChannelPreferences {
    fun includesPrereleases(context: Context): Boolean =
        context.getSharedPreferences("app_update", Context.MODE_PRIVATE).getBoolean("prereleases", false)
    fun setIncludesPrereleases(context: Context, enabled: Boolean) {
        context.getSharedPreferences("app_update", Context.MODE_PRIVATE).edit().putBoolean("prereleases", enabled).apply()
    }
}

internal fun selectRelease(releases: JSONArray, includePrereleases: Boolean): JSONObject? =
    (0 until releases.length()).mapNotNull(releases::optJSONObject)
        .filter { !it.optBoolean("draft") && (includePrereleases ||
            (!it.optBoolean("prerelease") && !isPrereleaseVersion(it.optString("tag_name")))) }
        .filter { it.optString("tag_name").isNotBlank() }
        .maxWithOrNull { a, b -> compareVersionNames(a.optString("tag_name"), b.optString("tag_name")) }

internal fun isPrereleaseVersion(version: String): Boolean =
    Regex("(?i)(?:[-_.]|\\s)(?:alpha|beta|rc|preview|pre|dev|snapshot|nightly)[0-9.-]*")
        .containsMatchIn(version.substringBefore('+'))

internal fun compareVersionNames(left: String, right: String): Int {
    fun core(value: String) = Regex("^[vV]?([0-9]+(?:\\.[0-9]+)*)").find(value.trim())?.groupValues?.get(1).orEmpty()
    val l = core(left); val r = core(right)
    val lp = l.split('.').map { it.toLongOrNull() ?: 0 }; val rp = r.split('.').map { it.toLongOrNull() ?: 0 }
    repeat(maxOf(lp.size, rp.size)) { i ->
        val compared = (lp.getOrElse(i) { 0 }).compareTo(rp.getOrElse(i) { 0 })
        if (compared != 0) return compared
    }
    fun suffix(value: String, core: String) = value.trim().removePrefix("v").removePrefix("V")
        .removePrefix(core).substringBefore('+').trimStart('-', '_', '.').lowercase()
    val ls = suffix(left, l); val rs = suffix(right, r)
    if (ls.isEmpty() || rs.isEmpty()) return when { ls == rs -> 0; ls.isEmpty() -> 1; else -> -1 }
    val tokens = Regex("[a-z]+|[0-9]+")
    val lt = tokens.findAll(ls).map { it.value }.toList(); val rt = tokens.findAll(rs).map { it.value }.toList()
    repeat(minOf(lt.size, rt.size)) { i ->
        val ln = lt[i].toLongOrNull(); val rn = rt[i].toLongOrNull()
        val compared = when { ln != null && rn != null -> ln.compareTo(rn); ln != null -> -1; rn != null -> 1; else -> lt[i].compareTo(rt[i]) }
        if (compared != 0) return compared
    }
    return lt.size.compareTo(rt.size)
}

internal fun Context.openUrl(url: String) {
    if (url.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
