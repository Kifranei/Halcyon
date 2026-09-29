package com.ella.music.ui.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.ella.music.R
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class PlayerVisualizerPermissionState(
    val effectiveEnabled: Boolean,
    val setEnabled: (Boolean) -> Unit
)

@Composable
internal fun rememberPlayerVisualizerPermissionState(
    context: Context,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    audioVisualizerEnabled: Boolean,
    isPlaying: Boolean
): PlayerVisualizerPermissionState {
    var hasVisualizerPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val visualizerPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasVisualizerPermission = granted
        scope.launch {
            settingsManager.setAudioVisualizerEnabled(granted)
        }
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.player_need_record_audio_permission),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    return PlayerVisualizerPermissionState(
        effectiveEnabled = audioVisualizerEnabled &&
            hasVisualizerPermission &&
            isPlaying,
        setEnabled = { enabled ->
            if (enabled && !hasVisualizerPermission) {
                visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                scope.launch {
                    settingsManager.setAudioVisualizerEnabled(enabled)
                }
            }
        }
    )
}
