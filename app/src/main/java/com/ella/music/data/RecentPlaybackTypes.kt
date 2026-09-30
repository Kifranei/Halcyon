package com.ella.music.data

internal fun parseRecentPlaybackTypes(raw: String?, defaults: Set<String>): Set<String> =
    raw?.split(',')?.map(String::trim)?.filter { it in defaults }?.toSet() ?: defaults
