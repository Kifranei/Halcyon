package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R

@Composable
internal fun SpectrumViewerChoiceSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    externalOnly: Boolean = false
) {
    val options = SpectrumViewerLauncher.viewerIds.filterNot { externalOnly && it == "builtin" }
    EllaMiuixBottomSheet(
        show = show,
        enableNestedScroll = false,
        title = stringResource(if (externalOnly) R.string.spectrum_open_external else R.string.song_more_view_spectrum),
        onDismissRequest = onDismiss
    ) {
        EllaMiuixSheetColumn(verticalPadding = 8.dp, spacing = 0.dp, showHandle = false) {
            EllaMiuixActionMenuGroup {
                options.forEach { id ->
                    val label = when (id) {
                        "builtin" -> R.string.settings_spectrum_builtin
                        SpectrumViewerLauncher.ASPECT_PRO -> R.string.settings_spectrum_aspect_pro
                        SpectrumViewerLauncher.KASPEK -> R.string.settings_spectrum_kaspek
                        else -> R.string.settings_spectrum_salt
                    }
                    SongMenuItem(stringResource(label), onClick = { onSelect(id) })
                }
            }
        }
    }
}
