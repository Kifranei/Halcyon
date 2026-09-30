package com.ella.music.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.spotify.SpotifyCanvasConnection
import com.ella.music.data.spotify.SpotifyCanvasCredentialsStore
import com.ella.music.data.spotify.SpotifyCanvasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun SettingsSpotifyCanvasSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember(context) { SettingsManager.getInstance(context) }
    val credentials = remember(context) { SpotifyCanvasCredentialsStore.getInstance(context) }
    val repository = remember(context) { SpotifyCanvasRepository.getInstance(context) }
    val enabled by settings.spotifyCanvasEnabled.collectAsState(initial = false)
    val cookie by credentials.cookie.collectAsState()
    val connection by repository.connection.collectAsState()
    SettingsCardGroup {
        Column {
            // search-anchor:start
            SettingsSearchAnchor(R.string.settings_spotify_canvas) {
                SwitchPreference(
                    title = stringResource(R.string.settings_spotify_canvas),
                    summary = stringResource(R.string.settings_spotify_canvas_summary),
                    checked = enabled,
                    onCheckedChange = { scope.launch { settings.setSpotifyCanvasEnabled(it) } }
                )
            } // search-anchor:end
            // search-anchor:start
            SettingsSearchAnchor(R.string.settings_spotify_canvas_cookie) {
                SplitSettingTextField(
                    label = stringResource(R.string.settings_spotify_canvas_cookie),
                    value = cookie,
                    summary = stringResource(R.string.settings_spotify_canvas_cookie_summary),
                    isPassword = true, singleLine = true,
                    onValueChange = { value ->
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { credentials.setCookie(value) }
                                repository.credentialChanged()
                            }
                            catch (error: Exception) {
                                if (error is kotlinx.coroutines.CancellationException) throw error
                                Toast.makeText(context, R.string.settings_spotify_canvas_cookie_save_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            } // search-anchor:end
            // search-anchor:start
            SettingsSearchAnchor(R.string.settings_spotify_canvas_connect) {
                ArrowPreference(
                    title = stringResource(R.string.settings_spotify_canvas_connect),
                    summary = stringResource(when {
                        cookie.isBlank() -> R.string.settings_spotify_canvas_not_configured
                        connection == SpotifyCanvasConnection.Connecting -> R.string.settings_spotify_canvas_connecting
                        connection == SpotifyCanvasConnection.Connected -> R.string.settings_spotify_canvas_connected
                        connection == SpotifyCanvasConnection.Failed -> R.string.settings_spotify_canvas_connection_failed
                        else -> R.string.settings_spotify_canvas_connect_summary
                    }),
                    enabled = cookie.isNotBlank() && connection != SpotifyCanvasConnection.Connecting,
                    onClick = { scope.launch { repository.connect(cookie) } }
                )
            } // search-anchor:end
        }
    }
}
