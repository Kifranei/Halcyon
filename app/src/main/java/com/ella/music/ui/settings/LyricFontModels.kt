package com.ella.music.ui.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import com.ella.music.R
import com.ella.music.ui.components.ScriptFontPaths
import com.ella.music.ui.components.loadScriptAwareTypeface
import java.io.File

internal data class FontChoice(
    val name: String,
    val path: String,
    val source: String,
    val sourceRank: Int
)

internal fun collectFontChoices(context: Context): List<FontChoice> {
    val bundledFonts = ensureBundledFontChoices(context)
    val importedDir = File(context.filesDir, IMPORTED_FONT_DIR)
    val importedFonts = importedDir.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_FONT_EXTENSIONS && it.canRead() }
        ?.map { file ->
            FontChoice(
                name = file.nameWithoutExtension.cleanFontName(context),
                path = file.absolutePath,
                source = context.getString(R.string.settings_lyric_font_source_imported),
                sourceRank = FONT_SOURCE_IMPORTED
            )
        }
        ?.toList()
        .orEmpty()
    return (bundledFonts + importedFonts)
        .distinctBy { it.path }
        .sortedWith(compareBy<FontChoice> { it.sourceRank }.thenBy { it.name.lowercase() })
}

/** System fonts: the "system default" pseudo-font plus every readable font file under the system font dirs. */
internal fun collectSystemFontChoices(context: Context): List<FontChoice> {
    val choices = mutableListOf(
        FontChoice(
            name = context.getString(R.string.settings_lyric_font_system_default),
            path = SYSTEM_FONT_PATH,
            source = context.getString(R.string.settings_lyric_font_source_system),
            sourceRank = FONT_SOURCE_SYSTEM
        )
    )
    val seen = HashSet<String>()
    SYSTEM_FONT_DIRS.forEach { dirPath ->
        File(dirPath).listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_FONT_EXTENSIONS && it.canRead() }
            ?.forEach { file ->
                if (seen.add(file.absolutePath)) {
                    choices += FontChoice(
                        name = file.nameWithoutExtension.cleanFontName(context),
                        path = file.absolutePath,
                        source = context.getString(R.string.settings_font_system_fonts),
                        sourceRank = FONT_SOURCE_SYSTEM_FILE
                    )
                }
            }
    }
    return choices
        .distinctBy { it.path }
        .sortedWith(compareBy<FontChoice> { it.sourceRank }.thenBy { it.name.lowercase() })
}

internal fun String.toFontFamilyOrNull(weight: Int, italic: Boolean): FontFamily? {
    if (this == SYSTEM_FONT_PATH) {
        return runCatching {
            FontFamily(Typeface.create(Typeface.DEFAULT, weight.coerceIn(100, 900), italic))
        }.getOrNull()
    }
    val file = File(this)
    if (!file.exists() || !file.canRead()) return null
    // Use the same script-aware loading path as the actual lyric rendering so the
    // preview honours variable-font weight axes (e.g. Inter at 900) instead of
    // falling back to synthetic bolding via Typeface.create(base, weight, italic).
    return runCatching {
        val paths = ScriptFontPaths(this, "")
        val typeface = loadScriptAwareTypeface(paths, weight, italic, boldFallback = false)
        FontFamily(typeface)
    }.getOrNull()
}

internal fun copyImportedFont(context: Context, uri: Uri): FontChoice {
    val rawName = context.resolveDisplayName(uri).ifBlank { "lyric_font.ttf" }
    val safeName = rawName.sanitizeFileName().ensureFontExtension()
    val dir = File(context.filesDir, IMPORTED_FONT_DIR).apply { mkdirs() }
    val target = File(dir, "${System.currentTimeMillis()}_$safeName")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Unable to open font")
    return FontChoice(
        name = target.nameWithoutExtension.cleanFontName(context),
        path = target.absolutePath,
        source = context.getString(R.string.settings_lyric_font_source_imported),
        sourceRank = FONT_SOURCE_IMPORTED
    )
}

internal fun deleteImportedFont(font: FontChoice): Boolean {
    if (font.sourceRank != FONT_SOURCE_IMPORTED) return false

    val file = File(font.path)
    if (!file.exists()) return true

    return runCatching {
        file.delete()
    }.getOrDefault(false)
}

private fun ensureBundledFontChoices(context: Context): List<FontChoice> {
    val bundledDir = File(context.filesDir, BUNDLED_FONT_DIR).apply { mkdirs() }
    val legacyTarget = File(bundledDir, "MiSansVF.ttf")
    if (legacyTarget.exists()) {
        runCatching { legacyTarget.delete() }
    }
    return BUNDLED_FONT_SPECS.mapNotNull { spec ->
        val target = File(bundledDir, spec.fileName)
        runCatching {
            if (!target.exists() || target.length() <= 0L) {
                context.assets.open(spec.assetPath).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }.onFailure {
            if (target.exists() && target.length() <= 0L) target.delete()
        }
        if (target.exists() && target.canRead() && target.length() > 0L) {
            FontChoice(
                name = spec.displayName,
                path = target.absolutePath,
                source = context.getString(R.string.settings_lyric_font_source_builtin),
                sourceRank = FONT_SOURCE_BUNDLED
            )
        } else {
            null
        }
    }
}

internal data class BundledFontSpec(
    val displayName: String,
    val fileName: String,
    val assetPath: String
)

internal val BUNDLED_FONT_SPECS = listOf(
    BundledFontSpec("Inter Bold", "Inter-Bold.ttf", "fonts/Inter-Bold.ttf"),
    BundledFontSpec("MiSans Bold", "MiSans-Bold.ttf", "fonts/MiSans-Bold.ttf")
)

private fun Context.resolveDisplayName(uri: Uri): String {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
    }.orEmpty().ifBlank {
        uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }
}

private fun String.sanitizeFileName(): String {
    return replace(Regex("""[\\/:*?"<>|]+"""), "_").trim().ifBlank { "lyric_font.ttf" }
}

private fun String.ensureFontExtension(): String {
    return if (substringAfterLast('.', "").lowercase() in SUPPORTED_FONT_EXTENSIONS) this else "$this.ttf"
}

private fun String.cleanFontName(context: Context): String {
    return replace('_', ' ').replace('-', ' ').trim().ifBlank { context.getString(R.string.settings_lyric_font) }
}

private const val IMPORTED_FONT_DIR = "lyric_fonts"
private const val BUNDLED_FONT_DIR = "lyric_builtin_fonts"
private const val FONT_SOURCE_BUNDLED = 0
private const val FONT_SOURCE_SYSTEM = 1
internal const val FONT_SOURCE_IMPORTED = 2
private const val FONT_SOURCE_SYSTEM_FILE = 3
const val SYSTEM_FONT_PATH = "__system_default__"
private val SYSTEM_FONT_DIRS = listOf("/system/fonts", "/product/fonts", "/system/font")
private val SUPPORTED_FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
internal val SUPPORTED_FONT_MIME_TYPES = arrayOf(
    "font/ttf",
    "font/otf",
    "font/ttc",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream"
)
