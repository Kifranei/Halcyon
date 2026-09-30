package com.ella.music.data.musicfree

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class MusicFreeImportTest {
    @Test fun acceptsAPlatformWithoutAnyBuiltInProviderMapping() {
        val (name, script) = MusicFreePluginService().importPluginScript(
            "module.exports = { platform: 'New Provider', async search(query,page,type) { return {data:[],isEnd:true}; } };",
            allowRuntimeInspect = false
        )
        assertEquals("New Provider", name)
        assertTrue(script.contains("search"))
    }
    @Test fun normalizesDefaultExports() {
        val (_, script) = MusicFreePluginService().importPluginScript(
            "export default { platform: 'Example', async search(query) { return {data:[]}; } };",
            allowRuntimeInspect = false
        )
        assertTrue(script.startsWith("module.exports = "))
    }
    @Test(expected = IllegalStateException::class)
    fun rejectsScriptsWithoutMusicCapabilities() {
        MusicFreePluginService().importPluginScript(
            "module.exports = { platform: 'Not a music plugin', version: '1.0.0', author: 'Example' };",
            allowRuntimeInspect = false
        )
    }

    @Test fun preservesMusicFreePageDataAndTheExplicitEndFlag() {
        val data = JSONArray((1..20).map { JSONObject().put("id", it) })
        val first = MusicFreeRawSearchPage.fromJson(JSONObject().put("data", data).put("isEnd", false))
        assertEquals(20, first.data.length())
        assertEquals(false, first.isEnd)
        val last = MusicFreeRawSearchPage.fromJson(JSONObject().put("data", data).put("isEnd", true))
        assertEquals(20, last.data.length())
        assertEquals(true, last.isEnd)
    }

    @Test fun pluginsWithoutAnEndFlagCanContinueUntilAnEmptyPage() {
        val page = MusicFreeRawSearchPage.fromJson(JSONObject().put("data", JSONArray("[{\"id\":1}]")))
        assertEquals(1, page.data.length())
        assertNull(page.isEnd)
        val empty = MusicFreeRawSearchPage.fromJson(JSONObject())
        assertEquals(0, empty.data.length())
        assertNull(empty.isEnd)
    }
}
