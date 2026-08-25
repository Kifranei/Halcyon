package com.ella.music.plugin.runtime

import android.util.Base64
import android.util.Log
import androidx.annotation.Keep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

/**
 * OkHttp client used for plugin host HTTP calls. Uses tight timeouts (and an overall call
 * timeout) so a single slow/unreachable lyric source can't stall the whole search — searches run
 * across sources in parallel, so the worst case is bounded by the slowest *responsive* source.
 */
private fun defaultPluginHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .writeTimeout(6, TimeUnit.SECONDS)
    .callTimeout(8, TimeUnit.SECONDS)
    .build()

/**
 * Pure-Kotlin implementation of the Platform.* host calls a Lyrico plugin makes. Engine-agnostic:
 * the JS side calls `__hostCall(name, payloadJson)` and this maps it to http/crypto/xml/log work.
 * Ported from Lyrico so the existing plugins run unchanged.
 */
@Keep
class QuickJsHostApi(
    private val appInfo: HostAppInfo = HostAppInfo(),
    private val runtimeInfo: HostRuntimeInfo = HostRuntimeInfo(),
    private val okHttpClient: OkHttpClient = defaultPluginHttpClient(),
    private val pluginId: String = "default",
    private val cacheRootDir: File? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
) {
    private companion object {
        const val CACHE_LOG_TAG = "PlatformPluginCache"
    }

    fun call(name: String, payloadJson: String): String {
        val payload = runCatching {
            json.parseToJsonElement(payloadJson).jsonObject
        }.getOrDefault(JsonObject(emptyMap()))

        return when (name) {
            "app.info" -> value(appInfo.toJsonObject())

            "app.userAgent" -> text(buildDefaultUserAgent(appInfo))

            "runtime.info" -> value(runtimeInfo.toJsonObject())

            "cache.get" -> text(cacheGet(payload.string("key")))

            "cache.set" -> {
                cacheSet(
                    key = payload.string("key"),
                    value = payload.string("value"),
                    ttlMs = payload.longOrNull("ttlMs")
                )
                text("")
            }

            "cache.remove" -> {
                cacheRemove(payload.string("key"))
                text("")
            }

            "cache.clear" -> {
                cacheClear()
                text("")
            }

            "crypto.md5" -> text(md5(payload.string("text")))

            "crypto.sha256" -> text(digestHex("SHA-256", payload.string("text")))

            "crypto.aesEcbPkcs5EncryptBase64" -> text(
                aesEcbPkcs5EncryptBase64(
                    text = payload.string("text"),
                    key = payload.string("key")
                )
            )

            "crypto.aesEcbPkcs5EncryptHex" -> text(
                aesEcbPkcs5Encrypt(
                    text = payload.string("text"),
                    key = payload.string("key")
                ).toHex()
            )

            "crypto.aesEcbPkcs5DecryptBase64ToText" -> text(
                aesEcbPkcs5DecryptBase64ToText(
                    base64 = payload.string("base64"),
                    key = payload.string("key")
                )
            )

            "base64.encodeText" -> text(
                Base64.encodeToString(
                    payload.string("text").toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
            )

            "base64.decodeText" -> text(
                String(
                    Base64.decode(payload.string("base64"), Base64.DEFAULT),
                    Charsets.UTF_8
                )
            )

            "base64.dropBytes" -> text(
                Base64.encodeToString(
                    Base64.decode(payload.string("base64"), Base64.DEFAULT)
                        .drop(payload.intOrNull("count") ?: 0)
                        .toByteArray(),
                    Base64.NO_WRAP
                )
            )

            "base64.decodeBytes" -> bytes(
                Base64.decode(payload.string("base64"), Base64.DEFAULT)
            )

            "base64.encodeBytes" -> text(
                Base64.encodeToString(payload.bytes("bytes"), Base64.NO_WRAP)
            )

            "base64.encodeUrlText" -> text(
                Base64.encodeToString(
                    payload.string("text").toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            )

            "base64.decodeUrlText" -> text(
                String(
                    Base64.decode(fromBase64Url(payload.string("base64Url")), Base64.DEFAULT),
                    Charsets.UTF_8
                )
            )

            "base64.encodeUrlBytes" -> text(
                Base64.encodeToString(
                    payload.bytes("bytes"),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            )

            "base64.decodeUrlBytes" -> bytes(
                Base64.decode(fromBase64Url(payload.string("base64Url")), Base64.DEFAULT)
            )

            "base64.toUrl" -> text(toBase64Url(payload.string("base64")))

            "base64.fromUrl" -> text(fromBase64Url(payload.string("base64Url")))

            "bytes.xor" -> bytes(
                xor(
                    bytes = payload.bytes("bytes"),
                    key = payload.bytes("key")
                )
            )

            "bytes.xorBase64" -> text(
                Base64.encodeToString(
                    xor(
                        bytes = Base64.decode(payload.string("base64"), Base64.DEFAULT),
                        key = payload.bytes("key")
                    ),
                    Base64.NO_WRAP
                )
            )

            "compression.inflateBytesToText" -> text(
                inflate(payload.bytes("bytes"))
            )

            "compression.inflateBase64ToText" -> text(
                inflate(Base64.decode(payload.string("base64"), Base64.DEFAULT))
            )

            "http.getText" -> text(
                executeHttp(
                    method = "GET",
                    payload = payload,
                    binaryResponse = false
                ).bodyText
            )

            "http.postText" -> text(
                executeHttp(
                    method = "POST",
                    payload = payload,
                    binaryResponse = false
                ).bodyText
            )

            "http.postBytes" -> text(
                executeHttp(
                    method = "POST",
                    payload = payload,
                    binaryResponse = true
                ).bodyBase64
            )

            "http.get" -> value(
                executeHttp(
                    method = "GET",
                    payload = payload,
                    binaryResponse = false
                ).toJsonObject()
            )

            "http.post" -> value(
                executeHttp(
                    method = "POST",
                    payload = payload,
                    binaryResponse = false
                ).toJsonObject()
            )

            "http.getBytes" -> value(
                executeHttp(
                    method = "GET",
                    payload = payload,
                    binaryResponse = true
                ).toJsonObject()
            )

            "http.postBytesResponse" -> value(
                executeHttp(
                    method = "POST",
                    payload = payload,
                    binaryResponse = true
                ).toJsonObject()
            )

            "xml.getRootAttributes" -> value(
                HostXmlApi.getRootAttributes(
                    xml = payload.string("xml")
                )
            )

            "xml.findElements" -> value(
                HostXmlApi.findElements(
                    xml = payload.string("xml"),
                    query = payload.obj("query") ?: JsonObject(emptyMap()),
                    json = json
                )
            )

            "xml.replaceChildrenByAttr" -> text(
                HostXmlApi.replaceChildrenByAttr(
                    xml = payload.string("xml"),
                    options = payload.obj("options") ?: JsonObject(emptyMap())
                )
            )

            "xml.removeElements" -> text(
                HostXmlApi.removeElements(
                    xml = payload.string("xml"),
                    query = payload.obj("query") ?: JsonObject(emptyMap())
                )
            )

            "log.debug" -> {
                Log.d(payload.logTag(), payload.string("message"))
                text("")
            }

            "log.warn" -> {
                Log.w(payload.logTag(), payload.string("message"))
                text("")
            }

            "log.error" -> {
                Log.e(payload.logTag(), payload.string("message"))
                text("")
            }

            else -> error("Unsupported host api: $name")
        }
    }

    private fun executeHttp(
        method: String,
        payload: JsonObject,
        binaryResponse: Boolean
    ): HostHttpResponse {
        val urlText = payload.string("url")
        require(urlText.isNotBlank()) { "HTTP url is blank" }

        val contentType = payload.string("contentType").ifBlank {
            if (binaryResponse) {
                "application/octet-stream"
            } else {
                "application/json; charset=utf-8"
            }
        }

        val requestBuilder = Request.Builder().url(urlText)
        if (method == "POST" || method == "PUT" || method == "PATCH") {
            requestBuilder.header("Content-Type", contentType)
        }

        payload.obj("headers")?.forEach { (key, value) ->
            val headerValue = value.headerString()
            if (key.equals("User-Agent", ignoreCase = true) && headerValue.isBlank()) {
                return@forEach
            }
            requestBuilder.header(key, headerValue)
        }

        if (!hasNonBlankHeader(payload.obj("headers"), "User-Agent")) {
            requestBuilder.header("User-Agent", buildDefaultUserAgent(appInfo))
        }

        val requestBody = if (method == "POST" || method == "PUT" || method == "PATCH") {
            payload.requestBodyBytes().toRequestBody(contentType.toMediaType())
        } else {
            null
        }

        requestBuilder.method(method, requestBody)

        val client = okHttpClient.newBuilder()
            .followRedirects(payload.booleanOrNull("followRedirects") ?: true)
            .followSslRedirects(payload.booleanOrNull("followRedirects") ?: true)
            .connectTimeout((payload.intOrNull("connectTimeoutMs") ?: 8_000).toLong(), TimeUnit.MILLISECONDS)
            .readTimeout((payload.intOrNull("readTimeoutMs") ?: 12_000).toLong(), TimeUnit.MILLISECONDS)
            .build()

        return client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBytes = response.body?.bytes() ?: ByteArray(0)
            val bodyText = if (binaryResponse) {
                ""
            } else {
                responseBytes.toString(Charsets.UTF_8)
            }

            val bodyBase64 = if (binaryResponse) {
                Base64.encodeToString(responseBytes, Base64.NO_WRAP)
            } else {
                ""
            }

            HostHttpResponse(
                code = response.code,
                message = response.message,
                headers = response.headers.toMultimap(),
                bodyText = bodyText,
                bodyBase64 = bodyBase64
            )
        }
    }

    private fun hasNonBlankHeader(headers: JsonObject?, targetName: String): Boolean {
        if (headers == null) return false
        return headers.any { (key, value) ->
            key.equals(targetName, ignoreCase = true) && value.headerString().isNotBlank()
        }
    }

    private fun JsonObject.requestBodyBytes(): ByteArray {
        val bodyBase64 = string("bodyBase64")
        if (bodyBase64.isNotBlank()) {
            return Base64.decode(bodyBase64, Base64.DEFAULT)
        }

        val bodyBytes = this["bodyBytes"] as? JsonArray
        if (bodyBytes != null) {
            return ByteArray(bodyBytes.size) { index ->
                bodyBytes[index].jsonPrimitive.int.toByte()
            }
        }

        return string("body").toByteArray(Charsets.UTF_8)
    }

    private fun md5(text: String): String {
        return digestHex("MD5", text)
    }

    private fun digestHex(algorithm: String, text: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(
            text.toByteArray(Charsets.UTF_8)
        )
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cacheGet(key: String): String {
        val normalizedKey = key.trim()
        val file = cacheFile(normalizedKey) ?: return ""
        if (!file.isFile) return ""
        val entry = runCatching {
            json.parseToJsonElement(file.readText()).jsonObject
        }.getOrNull() ?: run {
            file.delete()
            return ""
        }
        val expiresAt = entry.longOrNull("expiresAt") ?: 0L
        if (expiresAt > 0L && expiresAt <= System.currentTimeMillis()) {
            file.delete()
            return ""
        }
        return entry.string("value")
    }

    private fun cacheSet(key: String, value: String, ttlMs: Long?) {
        val file = cacheFile(key.trim()) ?: return
        val normalizedTtlMs = ttlMs ?: 0L
        val expiresAt = if (normalizedTtlMs > 0L) {
            System.currentTimeMillis() + normalizedTtlMs
        } else {
            0L
        }
        file.parentFile?.mkdirs()
        file.writeText(
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("value", value)
                    put("expiresAt", expiresAt)
                }
            )
        )
        Log.d(CACHE_LOG_TAG, "set plugin=$pluginId key=${key.trim()} ttlMs=$normalizedTtlMs")
    }

    private fun cacheRemove(key: String) {
        cacheFile(key.trim())?.delete()
    }

    private fun cacheClear() {
        pluginCacheDir()?.deleteRecursively()
    }

    private fun cacheFile(key: String): File? {
        if (key.isBlank()) return null
        return File(pluginCacheDir() ?: return null, "${md5(key)}.json")
    }

    private fun pluginCacheDir(): File? {
        val root = cacheRootDir ?: return null
        return File(root, md5(pluginId.ifBlank { "default" }))
    }

    private fun toBase64Url(base64: String): String = base64
        .trim()
        .replace('+', '-')
        .replace('/', '_')
        .trimEnd('=')

    private fun fromBase64Url(base64Url: String): String {
        val normalized = base64Url
            .trim()
            .replace('-', '+')
            .replace('_', '/')
        val padding = when (normalized.length % 4) {
            0 -> ""
            2 -> "=="
            3 -> "="
            else -> ""
        }
        return normalized + padding
    }

    private fun aesEcbPkcs5EncryptBase64(text: String, key: String): String {
        return Base64.encodeToString(
            aesEcbPkcs5Encrypt(text, key),
            Base64.NO_WRAP
        )
    }

    private fun aesEcbPkcs5Encrypt(text: String, key: String): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }

    private fun aesEcbPkcs5DecryptBase64ToText(base64: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return String(
            cipher.doFinal(Base64.decode(base64, Base64.DEFAULT)),
            Charsets.UTF_8
        )
    }

    private fun xor(bytes: ByteArray, key: ByteArray): ByteArray {
        if (key.isEmpty()) return bytes
        return ByteArray(bytes.size) { index ->
            (bytes[index].toInt() xor key[index % key.size].toInt()).toByte()
        }
    }

    private fun inflate(bytes: ByteArray): String {
        val inflater = Inflater()
        inflater.setInput(bytes)

        val buffer = ByteArray(4096)
        val output = ByteArrayOutputStream()

        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                output.write(buffer, 0, count)
            }
        } finally {
            inflater.end()
        }

        return output.toString("UTF-8")
    }

    private fun text(value: String): String {
        return value(JsonPrimitive(value))
    }

    private fun bytes(value: ByteArray): String {
        return value(
            JsonArray(
                value.map { byte ->
                    JsonPrimitive(byte.toInt() and 0xff)
                }
            )
        )
    }

    private fun value(value: JsonElement): String {
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("value", value)
            }
        )
    }

    private data class HostHttpResponse(
        val code: Int,
        val message: String,
        val headers: Map<String, List<String>>,
        val bodyText: String,
        val bodyBase64: String
    ) {
        fun toJsonObject(): JsonObject {
            return buildJsonObject {
                put("code", code)
                put("message", message)

                put(
                    "headers",
                    JsonObject(
                        headers.mapValues { (_, values) ->
                            JsonArray(values.map { JsonPrimitive(it) })
                        }
                    )
                )

                put("body", bodyText)
                put("bodyBase64", bodyBase64)
            }
        }
    }
}

data class HostAppInfo(
    val name: String = "Halcyon",
    val packageName: String = "com.ella.music",
    val versionName: String = "0.0.0",
    val versionCode: Long = 0,
    val buildType: String = "unknown",
    val debug: Boolean = false
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("packageName", packageName)
            put("versionName", versionName)
            put("versionCode", versionCode)
            put("buildType", buildType)
            put("debug", debug)
        }
    }
}

data class HostRuntimeInfo(
    val pluginApiVersion: Int = HostApiRegistry.PLUGIN_API_VERSION,
    val hostApiVersion: Int = HostApiRegistry.HOST_API_VERSION,
    val engine: String = "quickjs",
    val engineVersion: String? = null,
    val supportedHostApis: Set<String> = HostApiRegistry.SUPPORTED_HOST_APIS
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("pluginApiVersion", pluginApiVersion)
            put("hostApiVersion", hostApiVersion)
            put("engine", engine)
            if (engineVersion != null) {
                put("engineVersion", engineVersion)
            } else {
                put("engineVersion", JsonNull)
            }
            put(
                "supportedHostApis",
                JsonArray(supportedHostApis.sorted().map { JsonPrimitive(it) })
            )
        }
    }
}

fun buildDefaultUserAgent(appInfo: HostAppInfo): String {
    return "${appInfo.name}/${appInfo.versionName}"
}

private fun JsonObject.string(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.intOrNull(key: String): Int? {
    return this[key]?.jsonPrimitive?.int
}

private fun JsonObject.longOrNull(key: String): Long? {
    return this[key]?.jsonPrimitive?.longOrNull
}

private fun JsonObject.booleanOrNull(key: String): Boolean? {
    return this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
}

private fun JsonObject.obj(key: String): JsonObject? {
    return this[key] as? JsonObject
}

private fun JsonObject.bytes(key: String): ByteArray {
    val array = this[key]?.jsonArray ?: return ByteArray(0)
    return ByteArray(array.size) { index ->
        array[index].jsonPrimitive.int.toByte()
    }
}

private fun JsonElement.headerString(): String {
    return when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> joinToString(", ") {
            it.jsonPrimitive.contentOrNull.orEmpty()
        }
        else -> toString()
    }
}

private fun ByteArray.toHex(): String {
    return joinToString("") { "%02X".format(it) }
}

private fun JsonObject.logTag(): String {
    return string("tag")
        .ifBlank { "PlatformPlugin" }
        .take(48)
}
