package com.ella.music.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Stable observations with immediate feedback while DataStore completes its disk write. */
internal class PinnedPreferences(private val store: DataStore<Preferences>) {
    private data class Pending(val revision: Long, val keys: List<String>, val committed: Boolean)
    private inner class Entry(val namespace: String) {
        val key = stringPreferencesKey("pinned_$namespace")
        private var revision = 0L
        val pending = MutableStateFlow<Pending?>(null)
        @Volatile var persisted: List<String>? = null
        val flow: Flow<List<String>> = combine(
            store.data.map { read(it) }.distinctUntilChanged(), pending
        ) { saved, change ->
            persisted = saved
            if (change?.committed == true && change.keys == saved) pending.compareAndSet(change, null)
            change?.keys ?: saved
        }.distinctUntilChanged()

        fun read(prefs: Preferences): List<String> = prefs[key].orEmpty()
            .split('\n').map(String::trim).filter(String::isNotBlank).distinct()

        @Synchronized fun begin(transform: (List<String>) -> List<String>): Long {
            val request = ++revision
            (pending.value?.keys ?: persisted)?.let { pending.value = Pending(request, transform(it), false) }
            return request
        }

        @Synchronized fun publish(request: Long, keys: List<String>, committed: Boolean) {
            if (revision == request) pending.value = Pending(request, keys, committed)
        }
    }
    private val entries = ConcurrentHashMap<String, Entry>()
    private fun entry(namespace: String): Entry = entries.getOrPut(namespace) { Entry(namespace) }

    fun keys(namespace: String): Flow<List<String>> = entry(namespace).flow

    suspend fun set(namespace: String, key: String, pinned: Boolean) {
        val clean = key.trim().takeIf(String::isNotBlank) ?: return
        mutate(namespace) { current ->
            val remaining = current.filterNot { sameKey(namespace, it, clean) }
            if (pinned) listOf(clean) + remaining else remaining
        }
    }

    suspend fun pinInOrder(namespace: String, keys: List<String>) {
        val clean = keys.map(String::trim).filter(String::isNotBlank)
            .distinctBy { if (ignoresCase(namespace)) it.lowercase(java.util.Locale.ROOT) else it }
        if (clean.isEmpty()) return
        mutate(namespace) { current -> clean + current.filterNot { old -> clean.any { sameKey(namespace, old, it) } } }
    }

    private suspend fun mutate(namespace: String, transform: (List<String>) -> List<String>) {
        val entry = entry(namespace)
        val request = entry.begin(transform)
        try {
            var saved = emptyList<String>()
            store.edit { prefs ->
                saved = transform(entry.read(prefs))
                entry.publish(request, saved, false)
                prefs[entry.key] = saved.joinToString("\n")
            }
            entry.publish(request, saved, true)
        } catch (error: Throwable) {
            val pending = entry.pending.value
            if (pending?.revision == request) entry.pending.compareAndSet(pending, null)
            throw error
        }
    }

    private fun ignoresCase(namespace: String): Boolean = namespace == "folder" || namespace == "category:folder"
    private fun sameKey(namespace: String, first: String, second: String): Boolean =
        first.equals(second, ignoreCase = ignoresCase(namespace))
}
