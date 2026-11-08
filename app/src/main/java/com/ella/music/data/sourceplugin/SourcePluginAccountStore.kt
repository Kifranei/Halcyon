package com.ella.music.data.sourceplugin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Plugin account credentials, Keystore-encrypted in noBackupFilesDir so secrets leave neither
 * Android backups nor the app's own preference export. One encrypted file holds every plugin's
 * account keyed by plugin id — same model as NeteaseAccountStore.
 */
class SourcePluginAccountStore private constructor(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "source_plugin_accounts.enc"))
    private val mutable = MutableStateFlow(read())
    val accounts = mutable.asStateFlow()

    fun account(pluginId: String): SourcePluginAccount? =
        mutable.value[pluginId]?.takeIf { it.loggedIn }

    @Synchronized
    fun save(account: SourcePluginAccount) {
        val next = mutable.value + (account.pluginId to account)
        write(next)
        mutable.value = next
    }

    @Synchronized
    fun clear(pluginId: String) {
        if (pluginId !in mutable.value) return
        val next = mutable.value - pluginId
        write(next)
        mutable.value = next
    }

    @Synchronized
    fun clearAll() {
        file.delete()
        mutable.value = emptyMap()
    }

    private fun write(accounts: Map<String, SourcePluginAccount>) {
        val json = JSONObject()
        accounts.forEach { (id, account) -> json.put(id, account.toJson()) }
        val plain = json.toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val stream = file.startWrite()
        try {
            stream.write(cipher.iv + cipher.doFinal(plain))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    private fun read(): Map<String, SourcePluginAccount> = runCatching {
        val data = file.readFully()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        }
        val json = JSONObject(cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8))
        json.keys().asSequence().mapNotNull { id ->
            runCatching { SourcePluginAccount.fromJson(id, json.getJSONObject(id)) }.getOrNull()
        }.associateBy { it.pluginId }
    }.getOrDefault(emptyMap())

    private fun key(): SecretKey {
        val name = "halcyon_source_plugin_accounts_v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(name, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(name, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    companion object {
        @Volatile private var instance: SourcePluginAccountStore? = null
        fun getInstance(context: Context): SourcePluginAccountStore = instance ?: synchronized(this) {
            instance ?: SourcePluginAccountStore(context.applicationContext).also { instance = it }
        }
    }
}
