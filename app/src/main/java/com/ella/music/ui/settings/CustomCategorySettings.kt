package com.ella.music.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.basic.Card
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.CustomMetadataCategory
import com.ella.music.data.SettingsManager
import com.ella.music.data.customCategoryIsAlbum
import com.ella.music.data.customCategoryUsesSeparators
import com.ella.music.data.normalizeCustomCategories
import com.ella.music.data.recentEditOrder
import com.ella.music.ui.components.SettingsEditSheet
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun albumMergeFieldLabel(field: String): Int = when (field) {
    CustomMetadataCategory.MERGE_ALBUM_ARTIST -> R.string.custom_category_merge_album_artist
    CustomMetadataCategory.MERGE_ARTIST -> R.string.category_artist
    CustomMetadataCategory.MERGE_COMPOSER -> R.string.category_composer
    CustomMetadataCategory.MERGE_DATE -> R.string.custom_category_merge_date
    else -> R.string.custom_category_merge_album
}

@Composable
internal fun CustomCategorySettings(settings: SettingsManager) {
    val saved by settings.customMetadataCategories.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(-1) }
    var draft by remember { mutableStateOf(emptyList<CustomMetadataCategory>()) }
    SettingsCardGroup {
        SettingsSearchAnchor(R.string.custom_categories_title) {
            ArrowPreference(title = stringResource(R.string.custom_categories_title),
                summary = saved.joinToString(" / ") { it.name }.ifBlank { stringResource(R.string.custom_categories_summary) },
                onClick = { draft = saved; show = true })
        }
    }
    SettingsEditSheet(show && !editing, stringResource(R.string.custom_categories_title),
        subtitle = stringResource(R.string.custom_categories_summary),
        onDismissRequest = { show = false }, onSave = {
            val result = draft
            scope.launch { settings.setCustomMetadataCategories(result) }
            show = false
        }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ArrowPreference(title = stringResource(R.string.custom_category_add),
                summary = stringResource(R.string.custom_category_example), onClick = { index = -1; editing = true })
            draft.recentEditOrder().forEach { category ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArrowPreference(title = category.name, summary = category.tagKey, modifier = Modifier.weight(1f),
                            onClick = { index = draft.indexOfFirst { it.type == category.type }; editing = true })
                        IconButton(onClick = { draft = draft.filter { it.type != category.type } }) {
                            Icon(MiuixIcons.Regular.Delete, stringResource(R.string.common_delete), tint = MiuixTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if (editing) {
        val original = draft.getOrNull(index)
        var name by rememberSaveable(original) { mutableStateOf(original?.name.orEmpty()) }
        var tag by rememberSaveable(original) { mutableStateOf(original?.tagKey.orEmpty()) }
        var listStyle by rememberSaveable(original) { mutableStateOf(original?.listStyle ?: true) }
        var albumTab by rememberSaveable(original) { mutableStateOf(original?.albumTab ?: false) }
        var collectionMode by rememberSaveable(original) { mutableStateOf(original?.collectionMode ?: false) }
        var collectionNames by rememberSaveable(original) { mutableStateOf(original?.collectionNames.orEmpty().joinToString("\n")) }
        var collectionMembers by rememberSaveable(original) { mutableStateOf(original?.collectionMembers.orEmpty().joinToString("\n")) }
        var applySeparators by rememberSaveable(original) { mutableStateOf(original?.applySeparators ?: true) }
        var albumMergeText by rememberSaveable(original) {
            mutableStateOf((original?.albumMergeFields ?: CustomMetadataCategory.DEFAULT_ALBUM_MERGE_FIELDS).joinToString(","))
        }
        val albumMergeFields = albumMergeText.split(',').filter(String::isNotEmpty)
        val candidate = normalizeCustomCategories(listOf(CustomMetadataCategory(name, tag, listStyle, albumTab,
            collectionMode = collectionMode,
            collectionNames = collectionNames.lines().map(String::trim).dropLastWhile(String::isEmpty),
            collectionMembers = collectionMembers.lines().map(String::trim).dropLastWhile(String::isEmpty),
            applySeparators = applySeparators,
            albumMergeFields = albumMergeFields))).singleOrNull()
        SettingsEditSheet(true, stringResource(if (original == null) R.string.custom_category_add else R.string.custom_category_edit),
            subtitle = stringResource(R.string.custom_category_example), saveEnabled = candidate != null,
            onDismissRequest = { editing = false }, onSave = {
                candidate?.let { item ->
                    // Duplicate tag keys stay allowed (#751): each copy gets the smallest
                    // free instance so its `type` (pins/tiles/routes) stays unique.
                    val usedInstances = draft.filterIndexed { i, _ -> i != index }
                        .filter { it.tagKey == item.tagKey }.map { it.instance }.toSet()
                    val resolved = item.copy(
                        updatedAt = System.currentTimeMillis(),
                        instance = if (index in draft.indices && draft[index].tagKey == item.tagKey) {
                            draft[index].instance.takeIf { it !in usedInstances } ?: generateSequence(0) { it + 1 }.first { it !in usedInstances }
                        } else {
                            generateSequence(0) { it + 1 }.first { it !in usedInstances }
                        }
                    )
                    draft = draft.toMutableList().apply { if (index in draft.indices) set(index, resolved) else add(resolved) }
                }
                editing = false
            }) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(name, { name = it }, label = stringResource(R.string.custom_category_name), singleLine = true, modifier = Modifier.fillMaxWidth())
                TextField(tag, { tag = it }, label = stringResource(R.string.custom_category_tag), singleLine = true, modifier = Modifier.fillMaxWidth())
                SwitchPreference(title = stringResource(R.string.custom_category_list_style),
                    summary = stringResource(R.string.custom_category_list_style_summary),
                    checked = listStyle, onCheckedChange = { listStyle = it })
                SwitchPreference(title = stringResource(R.string.custom_category_album_tab),
                    summary = stringResource(R.string.custom_category_album_tab_summary),
                    checked = albumTab, onCheckedChange = { albumTab = it })
                if (customCategoryUsesSeparators(tag)) {
                    SwitchPreference(title = stringResource(R.string.custom_category_apply_separators),
                        summary = stringResource(R.string.custom_category_apply_separators_summary),
                        checked = applySeparators, onCheckedChange = { applySeparators = it })
                }
                if (customCategoryIsAlbum(tag)) {
                    Text(stringResource(R.string.custom_category_album_merge),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                    CustomMetadataCategory.ALBUM_MERGE_FIELD_ORDER.forEach { field ->
                        val locked = field == CustomMetadataCategory.MERGE_ALBUM
                        SwitchPreference(title = stringResource(albumMergeFieldLabel(field)),
                            checked = locked || field in albumMergeFields, enabled = !locked,
                            onCheckedChange = { checked ->
                                albumMergeText = (if (checked) albumMergeFields + field else albumMergeFields - field).joinToString(",")
                            })
                    }
                }
                SwitchPreference(title = stringResource(R.string.custom_category_collection_mode),
                    summary = stringResource(R.string.custom_category_collection_mode_summary),
                    checked = collectionMode, onCheckedChange = { collectionMode = it })
                if (collectionMode) {
                    TextField(collectionNames, { collectionNames = it },
                        label = stringResource(R.string.custom_category_collection_names),
                        singleLine = false, minLines = 3, modifier = Modifier.fillMaxWidth())
                    TextField(collectionMembers, { collectionMembers = it },
                        label = stringResource(R.string.custom_category_collection_members),
                        singleLine = false, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.custom_category_collection_hint),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize)
                }
            }
        }
    }
}
