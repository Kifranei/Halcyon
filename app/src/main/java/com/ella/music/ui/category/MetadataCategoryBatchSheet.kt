package com.ella.music.ui.category

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.batchTagFieldValue
import com.ella.music.data.metadataCategoryEditKey
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaGlassListPopup
import com.ella.music.ui.components.SettingsEditSheet
import com.ella.music.viewmodel.BatchFieldOp
import com.ella.music.viewmodel.MainViewModel
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val KEEP_MARKER = "<keep>"
private val PART_SEPARATORS = Regex("[/、,，;；]")

internal data class BatchFieldSpec(val tag: String, @StringRes val labelRes: Int, val rawLabel: String? = null)

private fun spec(tag: String, @StringRes labelRes: Int) = BatchFieldSpec(tag, labelRes)

/**
 * Editable field sets per entry point (#725 §2): album rows get the full tag set, person
 * categories get their own key plus the fields the issue allows, custom tags get the key
 * itself followed by the standard library fields.
 */
internal val ALBUM_BATCH_FIELD_SPECS = listOf("ALBUM", "ALBUMARTIST", "DATE", "GENRE", "DISC",
    "ARTIST", "COMPOSER", "ARRANGER", "LYRICIST", "PRODUCER", "LYRICS").map(::batchFieldSpecFor)
internal val ARTIST_BATCH_FIELD_SPECS = listOf("ARTIST", "ALBUMARTIST", "COMPOSER").map(::batchFieldSpecFor)

internal fun batchFieldSpecs(type: String, categoryName: String? = null): List<BatchFieldSpec> {
    val key = metadataCategoryEditKey(type) ?: return emptyList()
    return when (key.uppercase(Locale.ROOT)) {
        "ALBUM" -> ALBUM_BATCH_FIELD_SPECS
        "ARTIST" -> ARTIST_BATCH_FIELD_SPECS
        "COMPOSER" -> listOf("COMPOSER", "ARTIST", "ALBUMARTIST").map(::batchFieldSpecFor)
        "ARRANGER", "LYRICIST", "PRODUCER" -> listOf(batchFieldSpecFor(key))
        "GENRE", "DATE", "YEAR" -> listOf(batchFieldSpecFor(key)) +
            listOf("COMPOSER", "LYRICS").map(::batchFieldSpecFor)
        else -> listOf(BatchFieldSpec(key, R.string.category_batch_value, rawLabel = categoryName ?: key)) +
            listOf("ARTIST", "ALBUM", "ALBUMARTIST", "DATE", "GENRE", "DISC",
                "COMPOSER", "ARRANGER", "LYRICIST", "PRODUCER", "LYRICS").map(::batchFieldSpecFor)
    }
}

/** Direct spec for album/artist screens that are not metadata categories. */
internal fun batchFieldSpecFor(tag: String): BatchFieldSpec = when (tag.uppercase(Locale.ROOT)) {
    "TITLE" -> spec(tag, R.string.song_more_metadata_title)
    "ARTIST" -> spec(tag, R.string.song_more_metadata_artist)
    "ALBUM" -> spec(tag, R.string.song_more_metadata_album)
    "ALBUMARTIST", "ALBUM ARTIST" -> spec("ALBUMARTIST", R.string.song_more_metadata_album_artist)
    "GENRE" -> spec(tag, R.string.song_more_metadata_genre)
    "DATE", "YEAR" -> spec("DATE", R.string.song_more_metadata_year)
    "TRACK", "TRACKNUMBER", "TRACK NUMBER" -> spec("TRACK", R.string.song_more_metadata_track_number)
    "DISC", "DISCNUMBER", "DISC NUMBER" -> spec("DISC", R.string.song_more_metadata_disc_number)
    "COMPOSER" -> spec(tag, R.string.song_more_metadata_composer)
    "ARRANGER" -> spec(tag, R.string.song_more_metadata_arranger)
    "LYRICIST" -> spec(tag, R.string.song_more_metadata_lyricist)
    "PRODUCER" -> spec(tag, R.string.category_producer)
    "LYRICS" -> spec(tag, R.string.song_more_metadata_lyrics)
    else -> BatchFieldSpec(tag.uppercase(Locale.ROOT), R.string.category_batch_value, rawLabel = tag)
}

/** Common full-value prefill (#725 §4): identical → value, mixed → keep, all empty → blank. */
private fun fullPrefill(values: List<String>): Pair<String, Boolean> {
    val distinct = values.distinct()
    return when {
        distinct.isEmpty() || distinct.all { it.isBlank() } -> "" to false
        distinct.size == 1 -> distinct.single() to false
        else -> KEEP_MARKER to true
    }
}

/**
 * Partial-replace prefill (#725 §5): identical → value; mixed with a shared segment
 * (split on /、,，;；) → the common segments; otherwise keep; all empty → blank.
 */
private fun partialPrefill(values: List<String>): Pair<String, Boolean> {
    fullPrefill(values).let { (value, keep) -> if (!keep) return value to false }
    val segments = values.map { value -> value.split(PART_SEPARATORS).map(String::trim).filter { it.isNotEmpty() } }
    val common = segments.fold(segments.first().toSet()) { acc, parts -> acc intersect parts.toSet() }
    val ordered = segments.first().filter { it in common }.distinct()
    return if (ordered.isNotEmpty()) ordered.joinToString("/") to false else KEEP_MARKER to true
}

private class BatchFieldUi(
    val spec: BatchFieldSpec,
    val keepMode: Boolean,
    full: String,
    match: String,
    replacement: String
) {
    var fullText by mutableStateOf(full)
    var matchText by mutableStateOf(match)
    var replaceText by mutableStateOf(replacement)
    var touched by mutableStateOf(false)

    val effectiveKeep get() = keepMode && !touched

    fun toOp(partial: Boolean): BatchFieldOp = when {
        effectiveKeep -> BatchFieldOp(spec.tag, keep = true)
        partial -> if (matchText.isBlank()) BatchFieldOp(spec.tag, keep = true)
            else BatchFieldOp(spec.tag, match = matchText, replacement = replaceText)
        else -> BatchFieldOp(spec.tag, replacement = fullText)
    }
}

@Composable
internal fun BatchTagEditSheet(
    type: String,
    category: String?,
    songs: List<Song>,
    specs: List<BatchFieldSpec>,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val editor = mainViewModel.metadataBatchEditor
    val state by editor.state.collectAsState()
    val active = state?.takeIf { it.type == type }
    val target = active?.category ?: category ?: return
    var keepModifiedTime by rememberSaveable(type, target) { mutableStateOf(true) }
    var partialMode by rememberSaveable(type, target) { mutableStateOf(true) }
    val fields = remember(specs, target) {
        specs.map { fieldSpec ->
            val values = songs.map { it.batchTagFieldValue(fieldSpec.tag) }
            val (full, keep) = fullPrefill(values)
            val (partial, _) = partialPrefill(values)
            BatchFieldUi(fieldSpec, keepMode = keep, full = full, match = partial, replacement = partial)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        editor.answerPermission(it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(active?.permission) {
        editor.takePermissionLaunch()?.let { error ->
            runCatching { launcher.launch(IntentSenderRequest.Builder(error.intentSender).build()) }
                .onFailure { editor.answerPermission(false) }
        }
    }
    val fieldLabels = specs.map { it.rawLabel ?: stringResource(it.labelRes) }
    val running = active?.running == true
    val finished = active != null && !running
    val subtitle = when {
        finished -> stringResource(R.string.category_batch_result, active.succeeded, active.failed, active.skipped)
        running -> stringResource(R.string.category_batch_progress, active.processed, active.total)
        else -> stringResource(R.string.category_batch_description, songs.size, fieldLabels.joinToString("/"))
    }
    val ops = fields.map { it.toOp(partialMode) }
    SettingsEditSheet(true, stringResource(R.string.category_batch_edit), subtitle,
        saveEnabled = !running && (finished || ops.any { !it.keep }),
        closeEnabled = !running, onDismissRequest = { editor.dismiss(); onDismiss() }, onSave = {
            if (finished) { editor.dismiss(); onDismiss() }
            else editor.start(type, target, ops, songs, keepModifiedTime)
        }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (active == null) {
                BatchToggleRow(stringResource(R.string.batch_keep_modified_time), keepModifiedTime) { keepModifiedTime = it }
                BatchToggleRow(stringResource(R.string.batch_partial_replace), partialMode) { partialMode = it }
                fields.forEach { field -> BatchFieldRow(field, partialMode, songs) }
                Text(stringResource(R.string.category_batch_write_hint), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(stringResource(R.string.category_batch_placeholder_hint), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            } else Text(active.value, color = MiuixTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun BatchToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MiuixTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun BatchFieldRow(field: BatchFieldUi, partialMode: Boolean, songs: List<Song>) {
    val label = field.spec.rawLabel ?: stringResource(field.spec.labelRes)
    var pickerVisible by remember { mutableStateOf(false) }
    val distinctValues = remember(songs, field.spec.tag) {
        songs.map { it.batchTagFieldValue(field.spec.tag) }.filter { it.isNotBlank() }.distinct().take(30)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (distinctValues.isNotEmpty()) {
                TextButton(text = stringResource(R.string.batch_pick_from_songs), onClick = { pickerVisible = true })
            }
        }
        if (partialMode) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(field.matchText, {
                    field.matchText = it; field.touched = true
                }, label = stringResource(R.string.batch_match_label), singleLine = true,
                    modifier = Modifier.weight(1f))
                TextField(field.replaceText, {
                    field.replaceText = it; field.touched = true
                }, label = stringResource(R.string.batch_replace_label), singleLine = true,
                    modifier = Modifier.weight(1f))
            }
        } else {
            TextField(field.fullText, {
                field.fullText = it; field.touched = true
            }, singleLine = field.spec.tag != "LYRICS", modifier = Modifier.fillMaxWidth())
        }
    }
    EllaGlassListPopup(show = pickerVisible, onDismissRequest = { pickerVisible = false }) {
        distinctValues.forEach { value ->
            GlassPopupItem(
                text = value,
                onClick = {
                    field.fullText = value
                    field.matchText = value
                    field.touched = true
                    pickerVisible = false
                }
            )
        }
    }
}

@Composable
internal fun MetadataCategoryBatchSheet(type: String, category: String?, mainViewModel: MainViewModel, onDismiss: () -> Unit) {
    val target = category ?: return
    val songs = remember(type, target) { mainViewModel.getSongsForMetadataCategory(type, target) }
    val specs = remember(type, target) { batchFieldSpecs(type, target) }
    if (specs.isEmpty()) return
    BatchTagEditSheet(type, target, songs, specs, mainViewModel, onDismiss)
}
