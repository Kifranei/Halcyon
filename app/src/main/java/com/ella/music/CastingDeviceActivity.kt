package com.ella.music

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.media3.cast.MediaRouteButtonFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.mediarouter.app.MediaRouteButton
import com.ella.music.data.SettingsManager
import com.ella.music.player.DlnaCastManager
import com.ella.music.player.DlnaRenderer
import com.ella.music.player.PlaybackService
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.player.openPlatformOutputSwitcher
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.MONET_COVER
import com.ella.music.ui.theme.THEME_DARK
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

class CastingDeviceActivity : FragmentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)

    override fun attachBaseContext(newBase: Context) {
        val language = runBlocking(Dispatchers.IO) {
            SettingsManager.getInstance(newBase).appLanguage.first()
        }
        super.attachBaseContext(newBase.withHalcyonLocale(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        connectController()
        setContent {
            val settings = remember { SettingsManager.getInstance(this) }
            val themeMode by settings.themeMode.collectAsState(initial = THEME_FOLLOW_SYSTEM)
            val monetMode by settings.monetColorMode.collectAsState(initial = 0)
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            EllaTheme(
                themeMode = themeMode,
                monetMode = if (monetMode == MONET_COVER) 0 else monetMode,
                systemDarkOverride = systemDark
            ) {
                CastingDeviceScreen(controller = controller, onBack = ::finish)
            }
        }
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onDestroy() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        controller = null
        super.onDestroy()
    }
}

@Composable
private fun CastingDeviceScreen(controller: MediaController?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by DlnaCastManager.devices.collectAsState()
    val isScanning by DlnaCastManager.isScanning.collectAsState()
    val activeDlnaName by DlnaCastManager.status.collectAsState()
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) scope.launch { DlnaCastManager.discover(context) }
    }
    fun scan() {
        if (Build.VERSION.SDK_INT >= 37 && !permissionGranted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            scope.launch { DlnaCastManager.discover(context) }
        }
    }
    LaunchedEffect(Unit) { scan() }

    Column(modifier = Modifier.fillMaxSize()) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.casting_devices_title),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CastingSectionTitle(stringResource(R.string.casting_chromecast))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.casting_chromecast),
                                fontSize = 18.sp,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.casting_chromecast_summary),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        CastRouteButton(modifier = Modifier.size(52.dp))
                    }
                }
            }
            item {
                CastingRouteRow(
                    title = stringResource(R.string.casting_system_output),
                    summary = stringResource(R.string.casting_system_output_summary),
                    onClick = { openPlatformOutputSwitcher(context) }
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CastingSectionTitle(stringResource(R.string.casting_dlna), Modifier.weight(1f))
                    Text(
                        text = stringResource(if (isScanning) R.string.casting_scanning else R.string.casting_scan),
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(enabled = !isScanning, onClick = ::scan)
                            .padding(10.dp)
                    )
                }
            }
            if (!permissionGranted) {
                item { CastingHint(stringResource(R.string.casting_local_network_permission)) }
            } else if (!isScanning && devices.isEmpty()) {
                item { CastingHint(stringResource(R.string.casting_no_dlna_devices)) }
            }
            items(devices, key = DlnaRenderer::avTransportControlUrl) { renderer ->
                CastingRouteRow(
                    title = renderer.name,
                    summary = if (activeDlnaName == renderer.name) {
                        stringResource(R.string.casting_dlna_active)
                    } else {
                        stringResource(R.string.casting_dlna_tap)
                    },
                    onClick = {
                        val mediaItem = controller?.currentMediaItem
                        if (mediaItem == null) {
                            Toast.makeText(context, R.string.casting_no_current_song, Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                DlnaCastManager.play(
                                    context = context,
                                    renderer = renderer,
                                    mediaItem = mediaItem,
                                    positionMs = controller.currentPosition
                                ).onSuccess {
                                    controller.pause()
                                    Toast.makeText(context, R.string.casting_dlna_started, Toast.LENGTH_SHORT).show()
                                }.onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.casting_dlna_failed, error.message.orEmpty()),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    }
}

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
private fun CastRouteButton(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            val themedContext = ContextThemeWrapper(context, R.style.Theme_Ella_MediaRouterBridge)
            MediaRouteButton(themedContext).also { button ->
                MediaRouteButtonFactory.setUpMediaRouteButton(themedContext, button)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun CastingSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = MiuixTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun CastingRouteRow(title: String, summary: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        cornerRadius = 20.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(text = title, fontSize = 18.sp, color = MiuixTheme.colorScheme.onSurface)
            Text(text = summary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun CastingHint(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.fillMaxWidth().padding(18.dp)
    )
}
