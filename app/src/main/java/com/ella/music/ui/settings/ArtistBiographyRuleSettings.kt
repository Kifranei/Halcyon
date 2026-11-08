package com.ella.music.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.*
import com.ella.music.ui.components.SettingsEditSheet
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ArtistBiographyRuleSettings(settings: SettingsManager) {
    val saved by settings.artistBiographyMatchRules.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(-1) }
    var draft by remember { mutableStateOf(emptyList<ArtistBiographyMatchRule>()) }
    SettingsCardGroup {
        SettingsSearchAnchor(R.string.artist_bio_match_rules) {
            // search-anchor:start
            SettingsSearchAnchor(R.string.artist_bio_match_rules) {
            ArrowPreference(title = stringResource(R.string.artist_bio_match_rules),
                summary = stringResource(R.string.artist_bio_match_rules_summary),
                onClick = { draft = saved; visible = true })
            } // search-anchor:end
        }
    }
    SettingsEditSheet(visible && !editor, stringResource(R.string.artist_bio_match_rules),
        subtitle = stringResource(R.string.artist_bio_match_rules_summary),
        onDismissRequest = { visible = false }, onSave = {
            val result = draft
            scope.launch { settings.setArtistBiographyMatchRules(result) }
            visible = false
        }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSearchAnchor(R.string.artist_bio_match_add) {
            // search-anchor:start
            SettingsSearchAnchor(R.string.artist_bio_match_add) {
            ArrowPreference(title = stringResource(R.string.artist_bio_match_add), onClick = { index = -1; editor = true })
            } // search-anchor:end
            }
            draft.recentEditOrder().forEach { rule ->
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArrowPreference(title = "${rule.search} → ${rule.alias}", modifier = Modifier.weight(1f),
                            summary = if (rule.sources.isEmpty()) stringResource(R.string.artist_bio_match_all_sources)
                                else rule.sources.mapNotNull { artistBiographyProviderNames[it] }.joinToString(" / "),
                            onClick = { index = draft.indexOf(rule); editor = true })
                        Switch(rule.enabled, { value ->
                            draft = draft.map { if (it == rule) it.copy(enabled = value) else it }
                        })
                        IconButton(onClick = { draft = draft.filter { it != rule } }) {
                            Icon(MiuixIcons.Regular.Delete, stringResource(R.string.common_delete), tint = MiuixTheme.colorScheme.error)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    }
    if (editor) {
        val original = draft.getOrNull(index)
        var search by remember(original) { mutableStateOf(original?.search.orEmpty()) }
        var alias by remember(original) { mutableStateOf(original?.alias.orEmpty()) }
        var sources by remember(original) { mutableStateOf(original?.sources.orEmpty()) }
        SettingsEditSheet(true, stringResource(R.string.artist_bio_match_add),
            saveEnabled = search.isNotBlank() && alias.isNotBlank(), onDismissRequest = { editor = false },
            onSave = {
                val rule = ArtistBiographyMatchRule(search, alias, original?.enabled ?: true, sources,
                    updatedAt = System.currentTimeMillis())
                draft = draft.toMutableList().apply { if (index in indices) set(index, rule) else add(rule) }
                editor = false
            }) {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(search, { search = it }, label = stringResource(R.string.artist_bio_match_search), singleLine = true)
                TextField(alias, { alias = it }, label = stringResource(R.string.artist_bio_match_alias), singleLine = true)
                Text(stringResource(R.string.artist_bio_match_all_sources))
                Card {
                    artistBiographyProviderNames.forEach { (id, label) ->
                        SwitchPreference(title = label, checked = id in sources, onCheckedChange = { checked ->
                            sources = if (checked) sources + id else sources - id
                        })
                    }
                }
            }
        }
    }
}
