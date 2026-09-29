package com.ella.music.data.netease

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class NeteaseAccount(val cookie: String = "", val userId: Long = 0, val nickname: String = "") {
    val loggedIn get() = userId > 0 && neteaseCookies(cookie)["MUSIC_U"].orEmpty().isNotBlank()
    override fun toString() = "NeteaseAccount(userId=$userId, loggedIn=$loggedIn)"
}

/** Account secrets are Keystore-encrypted, outside Android backup and app preference exports. */
class NeteaseAccountStore private constructor(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "netease_account.enc"))
    private val prefs = context.getSharedPreferences("netease_device", Context.MODE_PRIVATE)
    val deviceId: String = prefs.getString("id", null) ?: UUID.randomUUID().toString().replace("-", "").also {
        check(prefs.edit().putString("id", it).commit())
    }
    private val mutable = MutableStateFlow(read())
    val account = mutable.asStateFlow()
    @Synchronized fun save(account: NeteaseAccount) {
        val plain = JSONObject().put("cookie", account.cookie).put("userId", account.userId)
            .put("nickname", account.nickname).toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val stream = file.startWrite()
        try { stream.write(cipher.iv + cipher.doFinal(plain)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
        mutable.value = account
    }
    @Synchronized fun clear() { file.delete(); mutable.value = NeteaseAccount() }
    private fun read(): NeteaseAccount = runCatching {
        val data = file.readFully()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        }
        val json = JSONObject(cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8))
        NeteaseAccount(json.optString("cookie"), json.optLong("userId"), json.optString("nickname"))
    }.getOrDefault(NeteaseAccount())
    private fun key(): SecretKey {
        val name = "halcyon_netease_account_v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(name, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(name, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object {
        @Volatile private var instance: NeteaseAccountStore? = null
        fun getInstance(context: Context): NeteaseAccountStore = instance ?: synchronized(this) {
            instance ?: NeteaseAccountStore(context.applicationContext).also { instance = it }
        }
    }
}

internal fun neteaseCookies(cookie: String): Map<String, String> = cookie.split(';').mapNotNull {
    val pair = it.trim().split('=', limit = 2)
    if (pair.size == 2 && pair[0] in setOf("MUSIC_U", "__csrf", "NMTID", "MUSIC_A", "__remember_me"))
        pair[0] to pair[1].trim() else null
}.toMap()
