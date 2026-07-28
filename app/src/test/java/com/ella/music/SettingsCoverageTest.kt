package com.ella.music

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guard tests that catch resource and backup coverage regressions when new features are added:
 *  - a new DataStore setting that is exported but never re-applied by restoreSettingsJson, and
 *  - a new string that only lands in the default resources and never in the locale files, or
 *  - Chinese fallback text copied into a non-CJK locale.
 *
 * They parse the source/resource files directly (no instrumentation) so they run as plain JVM
 * unit tests on CI.
 */
class SettingsCoverageTest {

    private fun moduleFile(relative: String): File {
        // Gradle runs unit tests with the module dir as the working dir; fall back to repo-root.
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate $relative from ${File(".").absolutePath}")
    }

    @Test
    fun everyPreferenceKeyIsRestoredFromBackup() {
        val source = moduleFile("src/main/java/com/ella/music/data/SettingsManager.kt").readText()

        val declared = Regex("""val\s+(KEY_[A-Za-z0-9_]+)\s*=\s*\w*[Pp]referencesKey\(""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSortedSet()

        val restoreStart = source.indexOf("fun restoreSettingsJson")
        val restoreEnd = source.indexOf("fun isRestoredCustomImageAvailable")
        assertTrue("restoreSettingsJson not found", restoreStart in 0 until restoreEnd)
        val restoreBody = source.substring(restoreStart, restoreEnd)
        val referenced = Regex("""KEY_[A-Za-z0-9_]+""")
            .findAll(restoreBody)
            .map { it.value }
            .toHashSet()

        // Keys deliberately excluded from backup/restore (none today). Document additions here.
        val intentionallyExcluded = emptySet<String>()

        val missing = declared - referenced - intentionallyExcluded
        assertTrue(
            "These preference keys are exported but never restored by restoreSettingsJson — " +
                "add a set*/setString/setInt/setBoolean call for each (or list them as intentionally " +
                "excluded): $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun everyDefaultStringExistsInAllLocales() {
        val resDir = moduleFile("src/main/res")
        val nameRegex = Regex("""<string\s+name="([^"]+)"""")

        fun stringNames(file: File): Set<String> =
            if (file.exists()) {
                nameRegex.findAll(file.readText()).map { it.groupValues[1] }.toHashSet()
            } else {
                emptySet()
            }

        val defaultNames = stringNames(File(resDir, "values/strings.xml"))
        assertTrue("default strings.xml is empty/missing", defaultNames.isNotEmpty())

        val localeDirs = resDir.listFiles { f ->
            f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists()
        }.orEmpty()
        assertTrue("no locale strings.xml found", localeDirs.isNotEmpty())

        val problems = buildString {
            for (dir in localeDirs.sortedBy { it.name }) {
                val missing = defaultNames - stringNames(File(dir, "strings.xml"))
                if (missing.isNotEmpty()) {
                    appendLine("${dir.name}: missing ${missing.size} -> ${missing.sorted()}")
                }
            }
        }
        assertTrue("Locale string files are out of sync:\n$problems", problems.isBlank())
    }

    @Test
    fun nonCjkLocalesDoNotContainChineseFallbackText() {
        val resDir = moduleFile("src/main/res")
        val checkedLocales = listOf("values-de", "values-fr", "values-ko", "values-ru")
        val hanRegex = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF]")
        val factory = DocumentBuilderFactory.newInstance()

        val problems = buildString {
            for (locale in checkedLocales) {
                val file = File(resDir, "$locale/strings.xml")
                val strings = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
                for (index in 0 until strings.length) {
                    val element = strings.item(index)
                    val value = element.textContent
                    if (hanRegex.containsMatchIn(value)) {
                        appendLine("$locale: ${element.attributes.getNamedItem("name").nodeValue} -> $value")
                    }
                }
            }
        }

        assertTrue("Chinese fallback text found in non-CJK locales:\n$problems", problems.isBlank())
    }
}
