package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A self-hosted Emby / Navidrome / WebDAV machine is usually reachable over plain HTTP: it sits on a
 * home LAN or has a public IPv6 address and no certificate. Addresses the user typed themselves may
 * therefore be cleartext, while a TLS session that gets downgraded to cleartext stays forbidden, and
 * credentials embedded in the address itself stay rejected.
 */
class SecureNetworkTest {

    @Test
    fun remoteServerAddressAcceptsCleartextAndTls() {
        assertEquals("http://home-server:4533/", "http://home-server:4533".requireRemoteServerUrl("Server"))
        assertEquals("http://[240e:1:2::5]:8096/", "http://[240e:1:2::5]:8096".requireRemoteServerUrl("Server"))
        assertEquals("https://dav.example.com/", "https://dav.example.com".requireRemoteServerUrl("Server"))
        assertEquals("http://nas.local/emby", "http://nas.local/emby".requireRemoteServerUrl("Server"))
    }

    @Test
    fun remoteServerAddressRejectsEmbeddedCredentials() {
        listOf(
            "http://user:pass@home-server:4533",
            "https://user:pass@dav.example.com"
        ).forEach { raw ->
            val failure = runCatching { raw.requireRemoteServerUrl("Server") }.exceptionOrNull()
            assertTrue("$raw must be rejected, got $failure", failure is IllegalArgumentException)
        }
    }

    @Test
    fun remoteServerAddressRejectsUnparsableAddresses() {
        listOf("", "   ", "not a url").forEach { raw ->
            assertTrue("'$raw' must be rejected", runCatching { raw.requireRemoteServerUrl("Server") }.isFailure)
        }
    }

    @Test
    fun cleartextIsAllowedOnlyWhenTheCallStartedCleartext() {
        // A cleartext server the user configured is fine; an HTTPS call that redirects down to HTTP
        // is not, because that would silently drop an encrypted session into the open.
        assertFalse(refusesCleartextDowngrade(startedSecure = false, requestIsHttps = false))
        assertFalse(refusesCleartextDowngrade(startedSecure = false, requestIsHttps = true))
        assertFalse(refusesCleartextDowngrade(startedSecure = true, requestIsHttps = true))
        assertTrue(refusesCleartextDowngrade(startedSecure = true, requestIsHttps = false))
    }

    @Test
    fun insecureHttpUrlOnlyMatchesExplicitCleartextAddresses() {
        assertTrue(isInsecureHttpUrl("http://home-server:4533"))
        assertTrue(isInsecureHttpUrl("  http://[240e:1:2::5]:8096/  "))
        assertFalse(isInsecureHttpUrl("https://dav.example.com"))
        assertFalse(isInsecureHttpUrl("home-server:4533"))
        assertFalse(isInsecureHttpUrl(""))
    }

    @Test
    fun thirdPartyEndpointsStillRequireHttps() {
        // AI providers and plugin imports keep the strict policy; only user-configured library
        // servers opt into cleartext.
        assertTrue(runCatching { "http://example.com".requireHttpsUrl("AI provider") }.isFailure)
        assertEquals("https://example.com/", "https://example.com".requireHttpsUrl("AI provider"))
    }
}
