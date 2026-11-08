package com.ella.music.data

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArtistBiographyMatchRulesTest {
    @Test fun aliasPriorityIsScopedAndDisabledRulesDoNotAffectOtherProviders() {
        val rules = listOf(ArtistBiographyMatchRule("鈴木このみ", "铃木木乃美", sources = setOf("qq")),
            ArtistBiographyMatchRule("鈴木このみ", "wrong", enabled = false))
        assertEquals(listOf("铃木木乃美", "鈴木このみ", "铃木このみ"), artistBiographyMatchCandidates("鈴木このみ", "qq", rules))
        assertEquals(listOf("鈴木このみ", "铃木このみ"), artistBiographyMatchCandidates("鈴木このみ", "netease", rules))
        assertEquals(listOf("Muse"), artistBiographyMatchCandidates("Muse", "qq", rules))
        assertEquals(listOf("Alias", "Muse"), artistBiographyMatchCandidates("Muse", "wikipedia", listOf(ArtistBiographyMatchRule("Muse", "Alias"))))
    }
    @Test fun rulesRoundTripThroughSettingsBackupWithScopesAndSwitches() = runBlocking {
        val settings = SettingsManager.getInstance(RuntimeEnvironment.getApplication())
        val rules = listOf(ArtistBiographyMatchRule("倉木麻衣", "Mai Kuraki", sources = setOf("lastfm", "wikipedia")),
            ArtistBiographyMatchRule("Muse", "Other", enabled = false))
        settings.setArtistBiographyMatchRules(rules)
        val backup = settings.exportSettingsJson()
        settings.setArtistBiographyMatchRules(emptyList())
        settings.restoreSettingsJson(backup)
        assertEquals(rules, settings.artistBiographyMatchRules.first())
        assertTrue(decodeArtistBiographyMatchRules("bad json").isEmpty())
        val ordered = listOf(ArtistBiographyMatchRule("A", "a"),
            ArtistBiographyMatchRule("B", "b", updatedAt = 5), ArtistBiographyMatchRule("C", "c"))
        assertEquals(listOf("B", "C", "A"), ordered.recentEditOrder().map { it.search })
        assertEquals(5L, decodeArtistBiographyMatchRules(encodeArtistBiographyMatchRules(ordered))[1].updatedAt)
    }
}
