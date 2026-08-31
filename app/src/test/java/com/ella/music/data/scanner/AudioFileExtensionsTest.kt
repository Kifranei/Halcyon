package com.ella.music.data.scanner

import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileExtensionsTest {
    @Test
    fun includesLosslessAndSurroundAudioExtensions() {
        listOf("dsf", "dff", "dsdiff", "ape", "dts", "dtshd", "wv", "tta", "mpc", "shn", "mka")
            .forEach { extension ->
                assertTrue("$extension should be discoverable", extension in supportedAudioFileExtensions)
            }
    }

    @Test
    fun includesCommonTagLibContainerAliases() {
        listOf("mp2", "m4b", "m4r", "m4p", "aifc", "afc")
            .forEach { extension ->
                assertTrue("$extension should be discoverable", extension in supportedAudioFileExtensions)
            }
    }
}
