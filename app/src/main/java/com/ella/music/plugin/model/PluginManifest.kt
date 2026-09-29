package com.ella.music.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Lyrico-compatible plugin manifest (manifest.json). */
@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val versionCode: Int = 0,
    val versionName: String = "",
    val author: String = "",
    val description: String = "",
    val apiVersion: Int = 1,
    val minHostApiVersion: Int = 1,
    val entry: String = "source.js",
    val includeDirs: List<String> = emptyList(),
    val icon: String? = null,
    val capabilities: Set<PluginCapability> = emptySet(),
    val configFields: List<PluginConfigField> = emptyList(),
    val i18n: PluginI18n? = null
)

@Serializable
data class PluginI18n(
    val defaultLocale: String,
    val resources: Map<String, String>
)

@Serializable
enum class PluginCapability {
    @SerialName("searchSongs")
    SEARCH_SONGS,

    @SerialName("getLyrics")
    GET_LYRICS,

    @SerialName("searchCovers")
    SEARCH_COVERS
}

@Serializable
data class PluginConfigField(
    val key: String,
    val title: String,
    val summary: String? = null,
    val group: String = "",
    val type: PluginConfigFieldType = PluginConfigFieldType.TEXT,
    val required: Boolean = false,
    val defaultValue: JsonElement = JsonPrimitive(""),
    val options: List<PluginConfigOption> = emptyList(),
    val dependency: PluginConfigDependency? = null,
    // Resolved presentation only; group remains the stable grouping identity.
    @kotlinx.serialization.Transient
    val groupTitle: String? = null
)

fun PluginConfigField.defaultValueString(): String =
    (defaultValue as? JsonPrimitive)?.let { primitive ->
        primitive.contentOrNull ?: primitive.booleanOrNull?.toString()
    }.orEmpty()

@Serializable
enum class PluginConfigFieldType {
    @SerialName("text")
    TEXT,

    @SerialName("password")
    PASSWORD,

    @SerialName("number")
    NUMBER,

    @SerialName("switch")
    SWITCH,

    @SerialName("dropdown")
    DROPDOWN,

    @SerialName("textarea")
    TEXTAREA,

    @SerialName("markdown")
    MARKDOWN
}

@Serializable
data class PluginConfigOption(
    val value: String,
    val label: String,
    val summary: String = ""
)

@Serializable
data class PluginConfigDependency(
    val match: PluginConfigDependencyMatch? = null
)

@Serializable
data class PluginConfigDependencyMatch(
    val key: String,
    val value: String
)
