package com.ella.music.ui.listmodel

internal enum class SortDirection {
    Ascending,
    Descending
}

internal data class SortSpec<T>(
    val field: T,
    val direction: SortDirection = SortDirection.Ascending
)

internal data class SortedListResult<T>(
    val items: List<T>,
    val fastIndexKeysById: Map<Long, String> = emptyMap()
)

internal interface DisplaySpec<T> {
    fun displayTitleFor(item: T): String
}

internal data class ListScreenIdentity(
    val screen: String,
    val entityId: String = "",
    val sortKey: String = ""
)
