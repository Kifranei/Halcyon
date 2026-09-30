package com.ella.music.data

import java.io.IOException
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * User-typed server addresses (Navidrome / OpenSubsonic / Emby / WebDAV) may be plain HTTP: a
 * self-hosted machine on a home LAN or a public IPv6 address usually has no certificate, and
 * forcing TLS would lock those libraries out. Embedded credentials stay rejected so the address
 * itself never doubles as a secret store.
 */
internal fun String.requireRemoteServerUrl(label: String): String {
    val parsed = trim().toHttpUrlOrNull()
        ?: throw IllegalArgumentException("$label URL is invalid")
    return parsed.requireRemoteServerUrl(label).toString()
}

/** [HttpUrl] counterpart of [requireRemoteServerUrl] for callers that already parsed the address. */
internal fun HttpUrl.requireRemoteServerUrl(label: String = "Request"): HttpUrl {
    require(username.isEmpty() && password.isEmpty()) {
        "$label URL must not contain embedded credentials"
    }
    return this
}

/** True when the address is an explicit cleartext HTTP URL (a bare host or an HTTPS URL is not). */
internal fun isInsecureHttpUrl(rawUrl: String): Boolean =
    rawUrl.trim().toHttpUrlOrNull()?.isHttps == false

/**
 * True when a call that started on TLS must not continue because it dropped to cleartext.
 * Cleartext is allowed when the user configured a cleartext server; an HTTPS origin that redirects
 * down to HTTP is still refused, so nothing that began encrypted ever travels in the open.
 */
internal fun refusesCleartextDowngrade(startedSecure: Boolean, requestIsHttps: Boolean): Boolean =
    startedSecure && !requestIsHttps

/**
 * Accepts the cleartext server the user configured while refusing HTTPS-to-HTTP downgrades.
 * Used by the remote library clients and the playback / cache paths that talk to them.
 */
internal fun OkHttpClient.Builder.allowUserConfiguredCleartext(): OkHttpClient.Builder =
    addNetworkInterceptor { chain ->
        val request = chain.request()
        if (refusesCleartextDowngrade(chain.call().request().url.isHttps, request.url.isHttps)) {
            throw IOException("HTTPS requests must not be redirected to cleartext HTTP")
        }
        chain.proceed(request)
    }

internal fun String.requireHttpsUrl(label: String): String {
    val parsed = trim().toHttpUrlOrNull()
        ?: throw IllegalArgumentException("$label URL is invalid")
    require(parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty()) {
        "$label URL must use HTTPS and must not contain embedded credentials"
    }
    return parsed.toString()
}

internal fun HttpUrl.requireHttpsUrl(label: String = "Request"): HttpUrl {
    require(isHttps && username.isEmpty() && password.isEmpty()) {
        "$label URL must use HTTPS and must not contain embedded credentials"
    }
    return this
}

/** Rejects HTTPS-to-HTTP redirects as well as initially configured cleartext URLs. */
internal fun OkHttpClient.Builder.requireHttpsRequests(): OkHttpClient.Builder =
    addNetworkInterceptor { chain ->
        val request = chain.request()
        if (!request.url.isHttps) throw IOException("HTTPS is required for this request")
        chain.proceed(request)
    }

/** Keeps ordinary cleartext media endpoints usable without putting auth data on the wire. */
internal fun OkHttpClient.Builder.blockCredentialedHttpRequests(): OkHttpClient.Builder =
    addNetworkInterceptor { chain ->
        val request = chain.request()
        if (!request.url.isHttps && request.containsCredentials()) {
            throw IOException("Credential-bearing requests require HTTPS")
        }
        chain.proceed(request)
    }

private fun Request.containsCredentials(): Boolean {
    val sensitiveQueryKeys = setOf(
        "api_key", "apikey", "key", "access_token", "auth", "password", "token", "secret",
        "credential", "client_secret", "session_key", "sk", "api_sig"
    )
    val sensitiveHeaderFragments = listOf(
        "authorization", "auth", "cookie", "api-key", "api_key", "apikey", "request-key", "token", "secret", "credential"
    )
    return url.username.isNotEmpty() || url.password.isNotEmpty() ||
        url.queryParameterNames.any { key ->
            val normalized = key.lowercase(Locale.ROOT)
            normalized in sensitiveQueryKeys || sensitiveHeaderFragments.any(normalized::contains)
        } || headers.names().any { name ->
            val normalized = name.lowercase(Locale.ROOT)
            sensitiveHeaderFragments.any(normalized::contains)
        }
}
