// Kotlin adaptation of CatClawMusic.Plugins.Netease/Netease/NeteaseEapi.cs.
// Copyright (c) 2026 kankejiang. MIT; see assets/licenses/CatClawMusic-Netease-MIT.txt.
package com.ella.music.data.netease

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal object CatClawNeteaseCrypto {
    private const val SALT = "-36cd479b6b5-"
    private val key = SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES")
    fun encrypt(path: String, json: String): String {
        val apiPath = path.replaceFirst("/eapi/", "/api/")
        val digest = MessageDigest.getInstance("MD5")
            .digest("nobody${apiPath}use${json}md5forencrypt".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val payload = "$apiPath$SALT$json$SALT$digest"
        return cipher(Cipher.ENCRYPT_MODE, payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it.toInt() and 255) }
    }
    fun decryptResponse(bytes: ByteArray): String {
        // AES-ECB ciphertext is deterministic per plaintext block, and the first ciphertext block of
        // "{\"result\":{\"sear..." (cloudsearch) starts with 0x7B ('{'). Decrypt first; only treat the
        // raw body as plain JSON when it actually is JSON, never by looking at the first byte.
        runCatching { cipher(Cipher.DECRYPT_MODE, bytes) }.getOrNull()?.let { decoded ->
            val text = decoded.toString(Charsets.UTF_8)
            if (looksLikeJson(text)) return text.substringBeforeLast('}') + "}"
        }
        val plain = bytes.toString(Charsets.UTF_8).trim()
        if (looksLikeJson(plain)) return plain
        val decoded = cipher(Cipher.DECRYPT_MODE, Base64.getDecoder().decode(plain))
        return decoded.toString(Charsets.UTF_8).substringBeforeLast('}') + "}"
    }
    private fun looksLikeJson(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("{") && trimmed.contains('}')
    }
    private fun cipher(mode: Int, bytes: ByteArray): ByteArray =
        Cipher.getInstance("AES/ECB/PKCS5Padding").run { init(mode, key); doFinal(bytes) }
}
