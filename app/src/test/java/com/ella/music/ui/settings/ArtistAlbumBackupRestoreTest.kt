package com.ella.music.ui.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

class ArtistAlbumBackupRestoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun exportDescriptionsJsonExportsPropertiesFileContent() {
        val rootDir = tempFolder.newFolder("filesDir")
        val file = File(rootDir, "artist_descriptions.properties")
        Properties().apply {
            setProperty("artist:Aimer", "Aimer is a Japanese pop singer.")
            setProperty("artist:YOASOBI", "YOASOBI is a Japanese music duo.")
            file.writer(Charsets.UTF_8).use { store(it, null) }
        }

        val exported = exportDescriptionsJson(rootDir, "artist_descriptions.properties")
        assertNotNull(exported)
        assertEquals("Aimer is a Japanese pop singer.", exported!!.optString("artist:Aimer"))
        assertEquals("YOASOBI is a Japanese music duo.", exported.optString("artist:YOASOBI"))
    }

    @Test
    fun exportDescriptionsJsonReturnsNullWhenFileMissingOrEmpty() {
        val rootDir = tempFolder.newFolder("emptyDir")
        assertNull(exportDescriptionsJson(rootDir, "non_existent.properties"))

        val emptyFile = File(rootDir, "empty.properties").apply { createNewFile() }
        assertNull(exportDescriptionsJson(rootDir, emptyFile.name))
    }

    @Test
    fun restoreDescriptionsFromJsonMergesAndWritesProperties() {
        val rootDir = tempFolder.newFolder("restoreDir")
        val file = File(rootDir, "album_descriptions.properties")
        Properties().apply {
            setProperty("album:Penny Rain", "Initial review")
            file.writer(Charsets.UTF_8).use { store(it, null) }
        }

        val json = JSONObject().apply {
            put("album:Penny Rain", "Updated review")
            put("album:Sun Dance", "New review")
        }

        restoreDescriptionsFromJson(rootDir, "album_descriptions.properties", json)

        val restored = Properties().apply {
            file.reader(Charsets.UTF_8).use { load(it) }
        }
        assertEquals("Updated review", restored.getProperty("album:Penny Rain"))
        assertEquals("New review", restored.getProperty("album:Sun Dance"))
    }

    @Test
    fun mergePropertiesFilesCombinesBothSources() {
        val sourceFile = tempFolder.newFile("source.properties")
        val targetFile = tempFolder.newFile("target.properties")

        Properties().apply {
            setProperty("key1", "val1_target")
            setProperty("key2", "val2_target")
            targetFile.writer(Charsets.UTF_8).use { store(it, null) }
        }

        Properties().apply {
            setProperty("key2", "val2_source")
            setProperty("key3", "val3_source")
            sourceFile.writer(Charsets.UTF_8).use { store(it, null) }
        }

        mergePropertiesFiles(sourceFile, targetFile)

        val result = Properties().apply {
            targetFile.reader(Charsets.UTF_8).use { load(it) }
        }
        assertEquals("val1_target", result.getProperty("key1"))
        assertEquals("val2_source", result.getProperty("key2"))
        assertEquals("val3_source", result.getProperty("key3"))
    }
}
