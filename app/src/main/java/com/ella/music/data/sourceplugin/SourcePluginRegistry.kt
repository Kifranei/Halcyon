package com.ella.music.data.sourceplugin

import android.content.Context
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.ConcurrentHashMap

/**
 * Single entry point for the account-source plugin system. Owns the installed-plugin registry
 * and hands out per-plugin library stores so ViewModels, the repository and the player can work
 * with a `plugin:<id>` library source exactly like the built-in NetEase source.
 */
object SourcePluginRegistry {
    private var appContext: Context? = null
    private val stores = ConcurrentHashMap<String, SourcePluginLibraryStore>()

    private fun context(): Context = appContext
        ?: throw IllegalStateException("SourcePluginRegistry not initialized")

    fun init(context: Context) {
        appContext = context.applicationContext
        store(context)
        client(context)
        accountStore(context)
    }

    fun store(context: Context): SourcePluginStore = SourcePluginStore.getInstance(context)
    fun client(context: Context): SourcePluginClient = SourcePluginClient(context.applicationContext)
    fun accountStore(context: Context): SourcePluginAccountStore = SourcePluginAccountStore.getInstance(context)

    fun account(context: Context, pluginId: String): SourcePluginAccount? =
        accountStore(context).account(pluginId)

    fun plugins(context: Context): Flow<List<SourcePluginConfig>> = store(context).plugins

    fun config(context: Context, pluginId: String): SourcePluginConfig? = store(context).plugin(pluginId)

    /** Library store for the plugin; null when the plugin isn't installed. */
    fun libraryStore(context: Context, pluginId: String): SourcePluginLibraryStore? {
        val config = config(context, pluginId) ?: return null
        val appContext = context.applicationContext
        return stores.getOrPut(pluginId) {
            SourcePluginLibraryStore(appContext, config, accountStore(appContext), client(appContext))
        }
    }

    fun libraryStoreForSource(context: Context, source: String): SourcePluginLibraryStore? {
        if (!isSourcePluginKey(source)) return null
        return libraryStore(context, sourcePluginKeyOf(source))
    }

    fun playlistsFor(context: Context, source: String): Flow<List<UserPlaylist>> =
        libraryStoreForSource(context, source)?.playlists ?: flowOf(emptyList())

    fun favoritesFor(context: Context, source: String): Flow<List<Song>> =
        libraryStoreForSource(context, source)?.favorites ?: flowOf(emptyList())

    fun recentPlaysFor(context: Context, source: String): Flow<List<SourcePluginRecentPlay>> =
        libraryStoreForSource(context, source)?.recentPlays ?: flowOf(emptyList())

    /** Forget runtime state for a deleted plugin (called by the settings UI). */
    fun evict(pluginId: String) {
        stores.remove(pluginId)
    }

    /** Visible name of a plugin source, for source menus and badges. */
    fun displayName(context: Context, source: String): String {
        if (!isSourcePluginKey(source)) return source
        return config(context, sourcePluginKeyOf(source))?.name ?: sourcePluginKeyOf(source)
    }
}
