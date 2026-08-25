package com.ella.music.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ella.music.data.lx.LxSearchPlatform
import com.ella.music.data.lx.LxOnlineSong

class LxOnlineViewModel : ViewModel() {
    var importUrl by mutableStateOf("")
    var searchQuery by mutableStateOf("")
    var searchPlatform by mutableStateOf(LxSearchPlatform.Kuwo)
    var importExpanded by mutableStateOf(false)
    var isBusy by mutableStateOf(false)
    var results by mutableStateOf<List<LxOnlineSong>>(emptyList())
    var message by mutableStateOf("")

    fun clearResults() {
        results = emptyList()
        message = ""
    }
}
