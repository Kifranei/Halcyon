package com.ella.music.viewmodel

import com.ella.music.data.NameSplitConfigStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.parseNameSplitSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal fun CoroutineScope.launchNameSplitConfigObservers(
    settingsManager: SettingsManager,
    onNameSplitConfigChanged: () -> Unit,
    onAlbumIdentityConfigChanged: suspend () -> Unit
) {
    launch {
        settingsManager.artistSeparators.distinctUntilChanged().collect {
            NameSplitConfigStore.artistCustomSeparators = parseNameSplitSetting(it)
            onNameSplitConfigChanged()
        }
    }
    launch {
        settingsManager.artistProtectedNames.distinctUntilChanged().collect {
            NameSplitConfigStore.artistProtectedNames = parseNameSplitSetting(it)
            onNameSplitConfigChanged()
        }
    }
    launch {
        settingsManager.genreSeparators.distinctUntilChanged().collect {
            NameSplitConfigStore.genreCustomSeparators = parseNameSplitSetting(it)
            onNameSplitConfigChanged()
        }
    }
    launch {
        settingsManager.genreProtectedNames.distinctUntilChanged().collect {
            NameSplitConfigStore.genreProtectedNames = parseNameSplitSetting(it)
            onNameSplitConfigChanged()
        }
    }
    launch {
        settingsManager.tagIgnoreCase.distinctUntilChanged().collect {
            NameSplitConfigStore.tagIgnoreCase = it
            onNameSplitConfigChanged()
            // Album identity is the only derived library model affected by this setting.
            // Do not re-aggregate the entire library for unrelated preference writes.
            onAlbumIdentityConfigChanged()
        }
    }
    launch {
        settingsManager.parseFeaturedArtists.distinctUntilChanged().collect {
            NameSplitConfigStore.parseFeaturedArtists = it
            onNameSplitConfigChanged()
        }
    }
}
