package com.ella.music.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.components.SettingsEditSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NameSplitSettingsSheet(show: Boolean, values: List<String>, onDismissRequest: () -> Unit, onSave: (List<String>) -> Unit) {
    if (!show) return
    // The player sheet opens on the DataStore flow's initial value, then the saved separators
    // arrive a frame later. Key the fields on that snapshot so the sheet shows the settings (#770).
    val seed = values.take(4).joinToString("\u0001")
    var artists by rememberSaveable(seed) { mutableStateOf(values.getOrElse(0) { "" }) }
    var artistNames by rememberSaveable(seed) { mutableStateOf(values.getOrElse(1) { "" }) }
    var genres by rememberSaveable(seed) { mutableStateOf(values.getOrElse(2) { "" }) }
    var genreNames by rememberSaveable(seed) { mutableStateOf(values.getOrElse(3) { "" }) }
    SettingsEditSheet(true, stringResource(R.string.settings_artist_separators),
        onDismissRequest = onDismissRequest, onSave = { onSave(listOf(artists, artistNames, genres, genreNames)); onDismissRequest() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SplitRuleField(R.string.settings_artist_separators, R.string.settings_artist_separators_summary, artists) { artists = it }
            SplitRuleField(R.string.settings_artist_protected_names, R.string.settings_artist_protected_names_summary, artistNames) { artistNames = it }
            SplitRuleField(R.string.settings_genre_separators, R.string.settings_genre_separators_summary, genres) { genres = it }
            SplitRuleField(R.string.settings_genre_protected_names, R.string.settings_genre_protected_names_summary, genreNames) { genreNames = it }
        }
    }
}

@Composable
private fun SplitRuleField(label: Int, summary: Int, value: String, onChange: (String) -> Unit) {
    TextField(value, onChange, label = stringResource(label), modifier = Modifier.fillMaxWidth())
    Text(stringResource(summary), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}
