package com.ella.music.data.musicfree

import org.junit.Assert.*
import org.junit.Test

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
}
