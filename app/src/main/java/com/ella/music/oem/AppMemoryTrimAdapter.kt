package com.ella.music.oem

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import coil3.imageLoader
import com.ella.music.data.AppLogStore
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.components.clearArtworkModelMemoryCache
import com.ella.music.ui.player.clearPlayerPaletteMemoryCache

/** Releases recreatable artwork and metadata caches under Android memory pressure. */
internal object AppMemoryTrimAdapter : ComponentCallbacks2 {
    private const val TAG = "AppMemoryTrim"

    override fun onTrimMemory(level: Int) {
        if (level !in setOf(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
                ComponentCallbacks2.TRIM_MEMORY_MODERATE,
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE
            )
        ) return
        releaseCaches("trim:$level")
    }

    override fun onLowMemory() = releaseCaches("low-memory")

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    private fun releaseCaches(reason: String) {
        val context = appContext ?: return
        MusicRepository.clearMemoryCachesIfInitialized()
        clearArtworkModelMemoryCache()
        clearPlayerPaletteMemoryCache()
        runCatching { context.imageLoader.memoryCache?.clear() }
            .onFailure { Log.w(TAG, "Failed to clear Coil memory cache", it) }
        AppLogStore.info(context, TAG, "Released recreatable caches ($reason)")
    }

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        context.registerComponentCallbacks(this)
    }
}
