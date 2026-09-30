package com.ella.music.data.spotify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun normalizeSpotifySessionCookie(input: String): String? {
    val raw = input.trim().removePrefix("Cookie:").trim()
    if (raw.isBlank()) return ""
    val value = if (raw.contains("sp_dc=")) {
        raw.split(';').firstOrNull { it.trim().startsWith("sp_dc=") }?.trim()?.removePrefix("sp_dc=") ?: return null
    } else raw
    return value.takeIf { it.length <= 8192 && it.all { char -> char.code in 33..126 && char != ';' && char != ',' } }
}

/** A session credential lives in encrypted no-backup storage, like Last.fm authorization. */
internal class SpotifyCanvasCredentialsStore private constructor(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "spotify_canvas_session.enc"))
    private val mutableCookie = MutableStateFlow(read())
    val cookie = mutableCookie.asStateFlow()

    @Synchronized fun setCookie(input: String) {
        val cookie = normalizeSpotifySessionCookie(input) ?: throw IllegalArgumentException("Invalid Spotify session cookie")
        if (cookie.isBlank()) file.delete() else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.iv + cipher.doFinal(cookie.toByteArray(Charsets.UTF_8))
            val stream = file.startWrite()
            try {
                stream.write(Base64.encode(encrypted, Base64.NO_WRAP))
                file.finishWrite(stream)
            } catch (error: Throwable) { file.failWrite(stream); throw error }
        }
        mutableCookie.value = cookie
    }

    private fun read(): String = runCatching {
        if (!file.baseFile.isFile) return ""
        val bytes = Base64.decode(file.openRead().use { it.readBytes() }, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        normalizeSpotifySessionCookie(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)).orEmpty()
    }.getOrDefault("")

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "halcyon_spotify_canvas_session_v1"
        @Volatile private var instance: SpotifyCanvasCredentialsStore? = null
        fun getInstance(context: Context): SpotifyCanvasCredentialsStore = instance ?: synchronized(this) {
            instance ?: SpotifyCanvasCredentialsStore(context.applicationContext).also { instance = it }
        }
    }
}
