package com.ella.music.data

import java.io.IOException
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

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
