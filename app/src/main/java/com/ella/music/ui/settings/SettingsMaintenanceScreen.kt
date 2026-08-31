package com.ella.music.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsMaintenanceScreen(
    onBack: () -> Unit,
    onNavigateToSetupWizard: () -> Unit,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_maintenance),
            color = ellaPageBackground(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberSettingsScrollState("settings_maintenance"))
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsCardGroup {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.settings_setup_wizard),
                        summary = stringResource(R.string.settings_setup_wizard_summary),
                        onClick = onNavigateToSetupWizard
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_clear_online_cache),
                        summary = stringResource(R.string.settings_clear_online_cache_summary),
                        onClick = {
                            scope.launch {
                                mainViewModel.clearOnlineMetadataCache()
                                playerViewModel.clearOnlineMetadataCache()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_clear_online_cache_done),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_clear_artist_image_cache),
                        summary = stringResource(R.string.settings_clear_artist_image_cache_summary),
                        onClick = {
                            scope.launch {
                                mainViewModel.clearDownloadedArtistImageCache()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_clear_artist_image_cache_done),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_clear_library_snapshot_cache),
                        summary = stringResource(R.string.settings_clear_library_snapshot_cache_summary),
                        onClick = {
                            mainViewModel.clearLibrarySnapshotCache()
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_clear_library_snapshot_cache_done),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_restore_defaults),
                        summary = stringResource(R.string.settings_restore_defaults_summary),
                        onClick = { confirmReset = true }
                    )
                }
            }
            Spacer(modifier = Modifier.height(120.dp))
        }
    }

    ConfirmDangerDialog(
        show = confirmReset,
        title = stringResource(R.string.settings_restore_defaults),
        message = stringResource(R.string.settings_restore_defaults_confirm),
        onDismiss = { confirmReset = false },
        onConfirm = {
            confirmReset = false
            scope.launch {
                settingsManager.resetToDefaults()
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_restore_defaults_done),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )
}
