package com.ella.music.plugin.i18n

import com.ella.music.plugin.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PluginStringsTest {
    @get:Rule val temp = TemporaryFolder()
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun positionalFormattingIsStrictAndSupportsReordering() {
        assertEquals("3 / 50% music / 50% music / %", PluginStringFormat.format(
            "%2\$d / %1\$s / %1\$s / %%", listOf("50% music", 3)))
        assertEquals("-2", PluginStringFormat.format("%d", listOf(-2)))
        listOf("%s %d", "%1\$s %d", "%0\$s", "%2\$s", "%1\$s %1\$d", "%f", "%1\$%").forEach {
            assertThrows(IllegalArgumentException::class.java) { PluginStringFormat.signature(it) }
        }
        listOf(emptyList(), listOf("3"), listOf(1.2), listOf(Double.POSITIVE_INFINITY), listOf(2, 3)).forEach {
            assertThrows(IllegalArgumentException::class.java) { PluginStringFormat.format("%d", it) }
        }
    }

    private fun fixture(): Pair<File, PluginManifest> {
        val root = temp.newFolder()
        File(root, "en.json").writeText("""{"title":"Language","group":"Settings","count":"%2${'$'}d for %1${'$'}s","body":"Help"}""")
        File(root, "zh.json").writeText("""{"title":"语言","group":"设置"}""")
        File(root, "tw.json").writeText("""{"title":"語言","group":"設定"}""")
        return root to PluginManifest(
            id = "test.plugin",
            name = "Original",
            versionCode = 1,
            versionName = "1",
            apiVersion = 5,
            minHostApiVersion = 4,
            i18n = PluginI18n("en", mapOf("en" to "en.json", "zh-Hans" to "zh.json", "zh-Hant" to "tw.json")),
            configFields = listOf(
                PluginConfigField(
                    key = "language",
                    title = "@title",
                    group = "@group",
                    type = PluginConfigFieldType.DROPDOWN,
                    defaultValue = JsonPrimitive("zh-Hans"),
                    options = listOf(PluginConfigOption("stable-value", "Label"))
                )
            )
        )
    }

    @Test fun scriptMatchingAndPreferencesAreDeterministic() {
        val (root, manifest) = fixture()
        val strings = PluginStrings.load(root, manifest)
        mapOf("zh-CN" to "zh-Hans", "zh-TW" to "zh-Hant", "zh-HK" to "zh-Hant", "en-AU" to "en", "de-DE" to "en").forEach { (tag, expected) ->
            assertEquals(expected, strings.snapshot(listOf(tag)).locale)
        }
        assertEquals("zh-Hans", strings.snapshot(listOf("ja-JP", "zh-CN")).locale)
    }

    @Test fun fallbackAndLocalizationDoNotMutateBusinessValues() {
        val (root, manifest) = fixture()
        val snapshot = PluginStrings.load(root, manifest).snapshot(listOf("zh-TW"))
        assertEquals("2 for Apple", snapshot.format("count", listOf("Apple", 2)))
        assertEquals("Fallback", snapshot.text("Fallback"))
        val field = snapshot.localize(manifest).configFields.single()
        assertEquals("語言", field.title)
        assertEquals("設定", field.groupTitle)
        assertEquals("@group", field.group)
        assertEquals(JsonPrimitive("zh-Hans"), field.defaultValue)
        assertEquals("stable-value", field.options.single().value)
        assertEquals("@title", manifest.configFields.single().title)
    }

    @Test fun unsafePathsMissingKeysAndIncompatibleFormatsFailAtInstall() {
        val (root, manifest) = fixture()
        assertThrows(IllegalArgumentException::class.java) { PluginStrings.load(root, manifest.copy(name = "@missing")) }
        assertThrows(IllegalArgumentException::class.java) { PluginStrings.load(root, manifest.copy(name = "@")) }
        assertThrows(IllegalArgumentException::class.java) { PluginStrings.load(root, manifest.copy(i18n = null)) }
        assertThrows(IllegalArgumentException::class.java) { PluginStrings.load(root, manifest.copy(minHostApiVersion = 3)) }
        assertThrows(IllegalArgumentException::class.java) { PluginStrings.load(root, manifest.copy(i18n = PluginI18n("en", mapOf("en" to "../escape.json")))) }
        File(root, "zh.json").writeText("""{"count":"%1${'$'}s %2${'$'}s"}""")
        assertThrows(IllegalArgumentException::class.java) { PluginStrings.load(root, manifest) }
    }

    @Test fun legacyManifestStillUsesOriginalText() {
        val manifest = json.decodeFromString<PluginManifest>("""{"id":"old.plugin","name":"Legacy","versionCode":1,"versionName":"1","apiVersion":1}""")
        assertEquals(manifest, PluginStrings.load(temp.root, manifest).snapshot(listOf("zh-TW")).localize(manifest))
    }

    @Test fun referencesEscapeOnceAndStaticPercentagesAreLiteral() {
        val (root, base) = fixture()
        File(root, "en.json").writeText("""{"title":"100% https://example.test/a%20b %s","group":"Same title","body":"@another.key"}""")
        val manifest = base.copy(
            description = "@@mention",
            configFields = listOf(
                base.configFields.single().copy(
                    key = "@business",
                    defaultValue = JsonPrimitive("@untouched"),
                    dependency = PluginConfigDependency(PluginConfigDependencyMatch("@key", "@value")),
                    options = listOf(PluginConfigOption("@option", "@@visible"))
                ),
                PluginConfigField(
                    key = "markdown",
                    title = "@@title",
                    type = PluginConfigFieldType.MARKDOWN,
                    group = "Same title",
                    defaultValue = JsonPrimitive("@body")
                )
            )
        )
        val snapshot = PluginStrings.load(root, manifest).snapshot(listOf("en"))
        val resolved = snapshot.localize(manifest)
        assertEquals("@mention", resolved.description)
        assertEquals("100% https://example.test/a%20b %s", resolved.configFields[0].title)
        assertEquals("@business", resolved.configFields[0].key)
        assertEquals(JsonPrimitive("@untouched"), resolved.configFields[0].defaultValue)
        assertEquals(manifest.configFields[0].dependency, resolved.configFields[0].dependency)
        assertEquals("@option", resolved.configFields[0].options.single().value)
        assertEquals("@visible", resolved.configFields[0].options.single().label)
        assertEquals(JsonPrimitive("@another.key"), resolved.configFields[1].defaultValue)
        assertEquals(2, resolved.configFields.groupBy { it.group }.size)
        assertEquals(1, resolved.configFields.map { it.groupTitle }.distinct().size)
        assertEquals("@another.key", snapshot.format("body", emptyList()))
    }
}
