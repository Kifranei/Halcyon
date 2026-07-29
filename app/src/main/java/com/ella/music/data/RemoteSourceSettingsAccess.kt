package com.ella.music.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ella.music.R
import com.ella.music.data.SettingsManager.Companion.DEFAULT_OPENAI_BASE_URL
import com.ella.music.data.SettingsManager.Companion.DEFAULT_OPENAI_MODEL
import com.ella.music.data.SettingsManager.Companion.LEGACY_EMBY_SERVER_ID
import com.ella.music.data.SettingsManager.Companion.LEGACY_NAVIDROME_SERVER_ID
import com.ella.music.data.SettingsManager.Companion.LIBRARY_SOURCE_LOCAL
import com.ella.music.data.SettingsManager.Companion.normalizeLibrarySource
import com.ella.music.data.SettingsManager.Companion.KEY_EMBY_ACTIVE_ID
import com.ella.music.data.SettingsManager.Companion.KEY_EMBY_SERVER_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_EMBY_SERVERS
import com.ella.music.data.SettingsManager.Companion.KEY_EMBY_TOKEN
import com.ella.music.data.SettingsManager.Companion.KEY_EMBY_URL
import com.ella.music.data.SettingsManager.Companion.KEY_EMBY_USER_ID
import com.ella.music.data.SettingsManager.Companion.KEY_EMBY_USERNAME
import com.ella.music.data.SettingsManager.Companion.KEY_LIBRARY_SOURCE
import com.ella.music.data.SettingsManager.Companion.KEY_LX_SELECTED_SOURCE_ID
import com.ella.music.data.SettingsManager.Companion.KEY_LX_SOURCE_NAME
import com.ella.music.data.SettingsManager.Companion.KEY_LX_SOURCE_SCRIPT
import com.ella.music.data.SettingsManager.Companion.KEY_LX_SOURCE_URL
import com.ella.music.data.SettingsManager.Companion.KEY_LX_SOURCES_JSON
import com.ella.music.data.SettingsManager.Companion.KEY_MCP_SERVER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_WEB_MUSIC_SERVER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_NAVIDROME_ACTIVE_ID
import com.ella.music.data.SettingsManager.Companion.KEY_NAVIDROME_PASSWORD
import com.ella.music.data.SettingsManager.Companion.KEY_NAVIDROME_SERVERS
import com.ella.music.data.SettingsManager.Companion.KEY_NAVIDROME_URL
import com.ella.music.data.SettingsManager.Companion.KEY_NAVIDROME_USERNAME
import com.ella.music.data.SettingsManager.Companion.KEY_ONLINE_SELECTED_PROVIDER
import com.ella.music.data.SettingsManager.Companion.KEY_OPENAI_API_KEY
import com.ella.music.data.SettingsManager.Companion.KEY_OPENAI_BASE_URL
import com.ella.music.data.SettingsManager.Companion.KEY_OPENAI_MODEL
import com.ella.music.data.SettingsManager.Companion.KEY_OPENSUBSONIC_ACTIVE_ID
import com.ella.music.data.SettingsManager.Companion.KEY_OPENSUBSONIC_SERVERS
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_AUTO_BACKUP_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_AUTO_BACKUP_INTERVAL_HOURS
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_AUTO_BACKUP_LAST_AT
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_BACKUP_PASSWORD
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_BACKUP_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_BACKUP_URL
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_BACKUP_USERNAME
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_LAST_URL
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_PASSWORD
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_URL
import com.ella.music.data.SettingsManager.Companion.KEY_WEBDAV_USERNAME
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.RemoteMusicSourceConfig
import com.ella.music.data.remote.SavedRemoteServer
import com.ella.music.data.remote.toRemoteServersJson
import com.ella.music.data.remote.toSavedRemoteServers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Online sources and integrations: library source selection, WebDAV (browse + backup),
 * LX scripts, Navidrome / OpenSubsonic / Emby multi-server configs, OpenAI and the MCP server.
 *
 * Extracted verbatim from [SettingsManager], which implements this interface via class
 * delegation so every call site keeps using settingsManager.<member> unchanged. All flow
 * properties MUST stay eagerly-initialised stored properties (never computed get() =):
 * Compose collectAsState keys on the flow instance, and a fresh instance per access would
 * restart collection on every recomposition.
 */
interface RemoteSourceSettingsAccess {
    val mcpServerEnabled: Flow<Boolean>
    val webMusicServerEnabled: Flow<Boolean>
    val webDavUrl: Flow<String>
    val webDavUsername: Flow<String>
    val webDavPassword: Flow<String>
    val webDavLastUrl: Flow<String>
    val webDavBackupUrl: Flow<String>
    val webDavBackupPath: Flow<String>
    val webDavBackupUsername: Flow<String>
    val webDavBackupPassword: Flow<String>
    val webDavAutoBackupEnabled: Flow<Boolean>
    val webDavAutoBackupIntervalHours: Flow<Int>
    val webDavAutoBackupLastAt: Flow<Long>
    val lxSources: Flow<List<LxSourceConfig>>
    val selectedLxSourceId: Flow<String>
    val selectedLxSource: Flow<LxSourceConfig?>
    val lxSourceUrl: Flow<String>
    val lxSourceName: Flow<String>
    val lxSourceScript: Flow<String>
    val selectedOnlineProvider: Flow<RemoteMusicProvider>
    val navidromeServers: Flow<List<SavedRemoteServer>>
    val openSubsonicServers: Flow<List<SavedRemoteServer>>
    val embyServers: Flow<List<SavedRemoteServer>>
    val navidromeActiveServerId: Flow<String>
    val openSubsonicActiveServerId: Flow<String>
    val embyActiveServerId: Flow<String>
    val navidromeConfig: Flow<RemoteMusicSourceConfig>
    val openSubsonicConfig: Flow<RemoteMusicSourceConfig>
    val embyConfig: Flow<RemoteMusicSourceConfig>
    val librarySource: Flow<String>
    val openAiApiKey: Flow<String>
    val openAiBaseUrl: Flow<String>
    val openAiModel: Flow<String>
    suspend fun setMcpServerEnabled(enabled: Boolean)
    suspend fun setWebMusicServerEnabled(enabled: Boolean)
    suspend fun setLibrarySource(source: String)
    suspend fun setWebDavConfig(url: String, username: String, password: String)
    suspend fun setWebDavLastUrl(url: String)
    suspend fun clearWebDavConfig()
    suspend fun setWebDavBackupUrl(url: String)
    suspend fun setWebDavBackupPath(path: String)
    suspend fun setLxSource(url: String, name: String, script: String)
    suspend fun clearLxSource()
    suspend fun selectLxSource(id: String)
    suspend fun removeLxSource(id: String)
    suspend fun selectOnlineProvider(provider: RemoteMusicProvider)
    fun newRemoteServerId(): String
    suspend fun upsertNavidromeServer(server: SavedRemoteServer)
    suspend fun deleteNavidromeServer(id: String)
    suspend fun setActiveNavidromeServer(id: String)
    suspend fun upsertOpenSubsonicServer(server: SavedRemoteServer)
    suspend fun setWebDavBackupCredentials(username: String, password: String)
    suspend fun setWebDavAutoBackupEnabled(enabled: Boolean)
    suspend fun setWebDavAutoBackupIntervalHours(hours: Int)
    suspend fun setWebDavAutoBackupLastAt(timestamp: Long)
    suspend fun deleteOpenSubsonicServer(id: String)
    suspend fun setActiveOpenSubsonicServer(id: String)
    suspend fun upsertEmbyServer(server: SavedRemoteServer)
    suspend fun deleteEmbyServer(id: String)
    suspend fun setActiveEmbyServer(id: String)
    suspend fun setOpenAiApiKey(apiKey: String)
    suspend fun setOpenAiBaseUrl(baseUrl: String)
    suspend fun setOpenAiModel(model: String)
}

internal class RemoteSourceSettingsAccessImpl(private val context: Context) : RemoteSourceSettingsAccess {

    override val mcpServerEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MCP_SERVER_ENABLED] ?: false }
    override val webMusicServerEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_WEB_MUSIC_SERVER_ENABLED] ?: false }

    override val webDavUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_URL] ?: "" }
    override val webDavUsername: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_USERNAME] ?: "" }
    override val webDavPassword: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_PASSWORD] ?: "" }
    override val webDavLastUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_LAST_URL] ?: "" }
    override val webDavBackupUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_BACKUP_URL] ?: "" }
    override val webDavBackupPath: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_BACKUP_PATH] ?: "" }
    override val webDavBackupUsername: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_BACKUP_USERNAME] ?: "" }
    override val webDavBackupPassword: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_BACKUP_PASSWORD] ?: "" }
    override val webDavAutoBackupEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_WEBDAV_AUTO_BACKUP_ENABLED] ?: false }
    override val webDavAutoBackupIntervalHours: Flow<Int> = context.dataStore.data.map {
        (it[KEY_WEBDAV_AUTO_BACKUP_INTERVAL_HOURS] ?: 24).coerceIn(1, 168)
    }
    override val webDavAutoBackupLastAt: Flow<Long> = context.dataStore.data.map {
        it[KEY_WEBDAV_AUTO_BACKUP_LAST_AT]?.toLongOrNull() ?: 0L
    }
    override val lxSources: Flow<List<LxSourceConfig>> = context.dataStore.data.map { prefs -> prefs.lxSources() }
    override val selectedLxSourceId: Flow<String> = context.dataStore.data.map { it[KEY_LX_SELECTED_SOURCE_ID] ?: "" }
    override val selectedLxSource: Flow<LxSourceConfig?> = context.dataStore.data.map { prefs ->
        val sources = prefs.lxSources()
        val selectedId = prefs[KEY_LX_SELECTED_SOURCE_ID].orEmpty()
        sources.firstOrNull { it.id == selectedId } ?: sources.firstOrNull()
    }
    override val lxSourceUrl: Flow<String> = selectedLxSource.map { it?.url.orEmpty() }
    override val lxSourceName: Flow<String> = selectedLxSource.map { it?.name.orEmpty() }
    override val lxSourceScript: Flow<String> = selectedLxSource.map { it?.script.orEmpty() }
    override val selectedOnlineProvider: Flow<RemoteMusicProvider> =
        context.dataStore.data.map { RemoteMusicProvider.fromId(it[KEY_ONLINE_SELECTED_PROVIDER].orEmpty()) }
    // ---- Multi-address remote sources ----
    // The stored server list is the source of truth. A legacy single config (old KEY_*_URL keys) is
    // synthesized read-only into a one-entry list so existing setups keep working until the user
    // edits them (the first write persists the migrated list).
    private fun readNavidromeServers(prefs: Preferences): List<SavedRemoteServer> {
        val stored = prefs[KEY_NAVIDROME_SERVERS].orEmpty().toSavedRemoteServers(RemoteMusicProvider.Navidrome)
        if (stored.isNotEmpty()) return stored
        val legacyUrl = prefs[KEY_NAVIDROME_URL].orEmpty()
        if (legacyUrl.isBlank()) return emptyList()
        return listOf(
            SavedRemoteServer(
                id = LEGACY_NAVIDROME_SERVER_ID,
                name = legacyUrl.remoteServerDisplayName(),
                config = RemoteMusicSourceConfig(
                    provider = RemoteMusicProvider.Navidrome,
                    baseUrl = legacyUrl,
                    username = prefs[KEY_NAVIDROME_USERNAME].orEmpty(),
                    password = prefs[KEY_NAVIDROME_PASSWORD].orEmpty()
                )
            )
        )
    }

    private fun readEmbyServers(prefs: Preferences): List<SavedRemoteServer> {
        val stored = prefs[KEY_EMBY_SERVERS].orEmpty().toSavedRemoteServers(RemoteMusicProvider.Emby)
        if (stored.isNotEmpty()) return stored
        val legacyUrl = prefs[KEY_EMBY_URL].orEmpty()
        if (legacyUrl.isBlank()) return emptyList()
        return listOf(
            SavedRemoteServer(
                id = LEGACY_EMBY_SERVER_ID,
                name = prefs[KEY_EMBY_SERVER_NAME].orEmpty().ifBlank { legacyUrl.remoteServerDisplayName() },
                config = RemoteMusicSourceConfig(
                    provider = RemoteMusicProvider.Emby,
                    baseUrl = legacyUrl,
                    username = prefs[KEY_EMBY_USERNAME].orEmpty(),
                    token = prefs[KEY_EMBY_TOKEN].orEmpty(),
                    userId = prefs[KEY_EMBY_USER_ID].orEmpty(),
                    serverName = prefs[KEY_EMBY_SERVER_NAME].orEmpty()
                )
            )
        )
    }

    private fun readOpenSubsonicServers(prefs: Preferences): List<SavedRemoteServer> =
        prefs[KEY_OPENSUBSONIC_SERVERS].orEmpty().toSavedRemoteServers(RemoteMusicProvider.OpenSubsonic)

    private fun activeServer(servers: List<SavedRemoteServer>, activeId: String?): SavedRemoteServer? =
        servers.firstOrNull { it.id == activeId } ?: servers.firstOrNull()

    override val navidromeServers: Flow<List<SavedRemoteServer>> = context.dataStore.data.map { readNavidromeServers(it) }
    override val openSubsonicServers: Flow<List<SavedRemoteServer>> = context.dataStore.data.map { readOpenSubsonicServers(it) }
    override val embyServers: Flow<List<SavedRemoteServer>> = context.dataStore.data.map { readEmbyServers(it) }
    override val navidromeActiveServerId: Flow<String> = context.dataStore.data.map { prefs ->
        activeServer(readNavidromeServers(prefs), prefs[KEY_NAVIDROME_ACTIVE_ID])?.id.orEmpty()
    }
    override val openSubsonicActiveServerId: Flow<String> = context.dataStore.data.map { prefs ->
        activeServer(readOpenSubsonicServers(prefs), prefs[KEY_OPENSUBSONIC_ACTIVE_ID])?.id.orEmpty()
    }
    override val embyActiveServerId: Flow<String> = context.dataStore.data.map { prefs ->
        activeServer(readEmbyServers(prefs), prefs[KEY_EMBY_ACTIVE_ID])?.id.orEmpty()
    }
    override val navidromeConfig: Flow<RemoteMusicSourceConfig> = context.dataStore.data.map { prefs ->
        activeServer(readNavidromeServers(prefs), prefs[KEY_NAVIDROME_ACTIVE_ID])?.config
            ?: RemoteMusicSourceConfig(provider = RemoteMusicProvider.Navidrome, baseUrl = "")
    }
    override val openSubsonicConfig: Flow<RemoteMusicSourceConfig> = context.dataStore.data.map { prefs ->
        activeServer(readOpenSubsonicServers(prefs), prefs[KEY_OPENSUBSONIC_ACTIVE_ID])?.config
            ?: RemoteMusicSourceConfig(provider = RemoteMusicProvider.OpenSubsonic, baseUrl = "")
    }
    override val embyConfig: Flow<RemoteMusicSourceConfig> = context.dataStore.data.map { prefs ->
        activeServer(readEmbyServers(prefs), prefs[KEY_EMBY_ACTIVE_ID])?.config
            ?: RemoteMusicSourceConfig(provider = RemoteMusicProvider.Emby, baseUrl = "")
    }
    override val librarySource: Flow<String> = context.dataStore.data.map {
        it[KEY_LIBRARY_SOURCE] ?: LIBRARY_SOURCE_LOCAL
    }
    override val openAiApiKey: Flow<String> = context.dataStore.data.map { it[KEY_OPENAI_API_KEY] ?: "" }
    override val openAiBaseUrl: Flow<String> =
        context.dataStore.data.map { it[KEY_OPENAI_BASE_URL] ?: DEFAULT_OPENAI_BASE_URL }
    override val openAiModel: Flow<String> =
        context.dataStore.data.map { it[KEY_OPENAI_MODEL] ?: DEFAULT_OPENAI_MODEL }

    override suspend fun setMcpServerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MCP_SERVER_ENABLED] = enabled }
    }

    override suspend fun setWebMusicServerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WEB_MUSIC_SERVER_ENABLED] = enabled }
    }

    override suspend fun setLibrarySource(source: String) {
        val normalized = normalizeLibrarySource(source)
        context.dataStore.edit { it[KEY_LIBRARY_SOURCE] = normalized }
    }

    override suspend fun setWebDavConfig(url: String, username: String, password: String) {
        context.dataStore.edit {
            it[KEY_WEBDAV_URL] = url.trim()
            it[KEY_WEBDAV_USERNAME] = username
            it[KEY_WEBDAV_PASSWORD] = password
            it[KEY_WEBDAV_LAST_URL] = url.trim()
        }
    }

    override suspend fun setWebDavLastUrl(url: String) {
        context.dataStore.edit {
            if (url.isBlank()) it.remove(KEY_WEBDAV_LAST_URL) else it[KEY_WEBDAV_LAST_URL] = url.trim()
        }
    }

    override suspend fun clearWebDavConfig() {
        context.dataStore.edit {
            it.remove(KEY_WEBDAV_URL)
            it.remove(KEY_WEBDAV_USERNAME)
            it.remove(KEY_WEBDAV_PASSWORD)
            it.remove(KEY_WEBDAV_LAST_URL)
        }
    }

    override suspend fun setWebDavBackupUrl(url: String) {
        context.dataStore.edit {
            if (url.isBlank()) it.remove(KEY_WEBDAV_BACKUP_URL) else it[KEY_WEBDAV_BACKUP_URL] = url.trim()
        }
    }

    override suspend fun setWebDavBackupPath(path: String) {
        context.dataStore.edit {
            if (path.isBlank()) it.remove(KEY_WEBDAV_BACKUP_PATH) else it[KEY_WEBDAV_BACKUP_PATH] = path.trim()
        }
    }

    override suspend fun setLxSource(url: String, name: String, script: String) {
        context.dataStore.edit {
            val source = LxSourceConfig(
                id = url.toLxSourceId(script),
                url = url.trim(),
                name = name.ifBlank { context.getString(R.string.settings_default_lx_source_name) },
                script = script
            )
            val sources = it.lxSources().filterNot { existing -> existing.id == source.id } + source
            it[KEY_LX_SOURCES_JSON] = sources.toLxSourcesJson()
            it[KEY_LX_SELECTED_SOURCE_ID] = source.id
            it[KEY_LX_SOURCE_URL] = source.url
            it[KEY_LX_SOURCE_NAME] = source.name
            it[KEY_LX_SOURCE_SCRIPT] = source.script
        }
    }

    override suspend fun clearLxSource() {
        context.dataStore.edit {
            it.remove(KEY_LX_SOURCES_JSON)
            it.remove(KEY_LX_SELECTED_SOURCE_ID)
            it.remove(KEY_LX_SOURCE_URL)
            it.remove(KEY_LX_SOURCE_NAME)
            it.remove(KEY_LX_SOURCE_SCRIPT)
        }
    }

    override suspend fun selectLxSource(id: String) {
        context.dataStore.edit { prefs ->
            val source = prefs.lxSources().firstOrNull { it.id == id } ?: return@edit
            prefs[KEY_LX_SELECTED_SOURCE_ID] = source.id
            prefs[KEY_LX_SOURCE_URL] = source.url
            prefs[KEY_LX_SOURCE_NAME] = source.name
            prefs[KEY_LX_SOURCE_SCRIPT] = source.script
        }
    }

    override suspend fun removeLxSource(id: String) {
        context.dataStore.edit { prefs ->
            val sources = prefs.lxSources().filterNot { it.id == id }
            if (sources.isEmpty()) {
                prefs.remove(KEY_LX_SOURCES_JSON)
                prefs.remove(KEY_LX_SELECTED_SOURCE_ID)
                prefs.remove(KEY_LX_SOURCE_URL)
                prefs.remove(KEY_LX_SOURCE_NAME)
                prefs.remove(KEY_LX_SOURCE_SCRIPT)
            } else {
                val selected = sources.firstOrNull { it.id == prefs[KEY_LX_SELECTED_SOURCE_ID] } ?: sources.first()
                prefs[KEY_LX_SOURCES_JSON] = sources.toLxSourcesJson()
                prefs[KEY_LX_SELECTED_SOURCE_ID] = selected.id
                prefs[KEY_LX_SOURCE_URL] = selected.url
                prefs[KEY_LX_SOURCE_NAME] = selected.name
                prefs[KEY_LX_SOURCE_SCRIPT] = selected.script
            }
        }
    }

    override suspend fun selectOnlineProvider(provider: RemoteMusicProvider) {
        context.dataStore.edit { it[KEY_ONLINE_SELECTED_PROVIDER] = provider.id }
    }

    private fun String.remoteServerDisplayName(): String =
        substringAfter("://").substringBefore('/').trim().ifBlank { trim() }

    override fun newRemoteServerId(): String = "server-${System.currentTimeMillis()}-${(0..9999).random()}"

    override suspend fun upsertNavidromeServer(server: SavedRemoteServer) {
        context.dataStore.edit { prefs ->
            val current = readNavidromeServers(prefs)
            val next = if (current.any { it.id == server.id }) {
                current.map { if (it.id == server.id) server else it }
            } else {
                current + server
            }
            prefs[KEY_NAVIDROME_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_NAVIDROME_ACTIVE_ID].isNullOrBlank()) prefs[KEY_NAVIDROME_ACTIVE_ID] = server.id
        }
    }

    override suspend fun deleteNavidromeServer(id: String) {
        context.dataStore.edit { prefs ->
            val next = readNavidromeServers(prefs).filterNot { it.id == id }
            prefs[KEY_NAVIDROME_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_NAVIDROME_ACTIVE_ID] == id) {
                val fallback = next.firstOrNull()?.id
                if (fallback == null) prefs.remove(KEY_NAVIDROME_ACTIVE_ID) else prefs[KEY_NAVIDROME_ACTIVE_ID] = fallback
            }
        }
    }

    override suspend fun setActiveNavidromeServer(id: String) {
        context.dataStore.edit { it[KEY_NAVIDROME_ACTIVE_ID] = id }
    }

    override suspend fun upsertOpenSubsonicServer(server: SavedRemoteServer) {
        context.dataStore.edit { prefs ->
            val current = readOpenSubsonicServers(prefs)
            val next = if (current.any { it.id == server.id }) {
                current.map { if (it.id == server.id) server else it }
            } else {
                current + server
            }
            prefs[KEY_OPENSUBSONIC_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_OPENSUBSONIC_ACTIVE_ID].isNullOrBlank()) prefs[KEY_OPENSUBSONIC_ACTIVE_ID] = server.id
        }
    }

    override suspend fun setWebDavBackupCredentials(username: String, password: String) {
        context.dataStore.edit {
            if (username.isBlank()) it.remove(KEY_WEBDAV_BACKUP_USERNAME) else it[KEY_WEBDAV_BACKUP_USERNAME] = username
            if (password.isBlank()) it.remove(KEY_WEBDAV_BACKUP_PASSWORD) else it[KEY_WEBDAV_BACKUP_PASSWORD] = password
        }
    }

    override suspend fun setWebDavAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WEBDAV_AUTO_BACKUP_ENABLED] = enabled }
    }

    override suspend fun setWebDavAutoBackupIntervalHours(hours: Int) {
        context.dataStore.edit { it[KEY_WEBDAV_AUTO_BACKUP_INTERVAL_HOURS] = hours.coerceIn(1, 168) }
    }

    override suspend fun setWebDavAutoBackupLastAt(timestamp: Long) {
        context.dataStore.edit { it[KEY_WEBDAV_AUTO_BACKUP_LAST_AT] = timestamp.coerceAtLeast(0L).toString() }
    }

    override suspend fun deleteOpenSubsonicServer(id: String) {
        context.dataStore.edit { prefs ->
            val next = readOpenSubsonicServers(prefs).filterNot { it.id == id }
            prefs[KEY_OPENSUBSONIC_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_OPENSUBSONIC_ACTIVE_ID] == id) {
                val fallback = next.firstOrNull()?.id
                if (fallback == null) prefs.remove(KEY_OPENSUBSONIC_ACTIVE_ID) else prefs[KEY_OPENSUBSONIC_ACTIVE_ID] = fallback
            }
        }
    }

    override suspend fun setActiveOpenSubsonicServer(id: String) {
        context.dataStore.edit { it[KEY_OPENSUBSONIC_ACTIVE_ID] = id }
    }

    override suspend fun upsertEmbyServer(server: SavedRemoteServer) {
        context.dataStore.edit { prefs ->
            val current = readEmbyServers(prefs)
            val next = if (current.any { it.id == server.id }) {
                current.map { if (it.id == server.id) server else it }
            } else {
                current + server
            }
            prefs[KEY_EMBY_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_EMBY_ACTIVE_ID].isNullOrBlank()) prefs[KEY_EMBY_ACTIVE_ID] = server.id
        }
    }

    override suspend fun deleteEmbyServer(id: String) {
        context.dataStore.edit { prefs ->
            val next = readEmbyServers(prefs).filterNot { it.id == id }
            prefs[KEY_EMBY_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_EMBY_ACTIVE_ID] == id) {
                val fallback = next.firstOrNull()?.id
                if (fallback == null) prefs.remove(KEY_EMBY_ACTIVE_ID) else prefs[KEY_EMBY_ACTIVE_ID] = fallback
            }
        }
    }

    override suspend fun setActiveEmbyServer(id: String) {
        context.dataStore.edit { it[KEY_EMBY_ACTIVE_ID] = id }
    }

    override suspend fun setOpenAiApiKey(apiKey: String) {
        context.dataStore.edit {
            val trimmed = apiKey.trim()
            if (trimmed.isBlank()) it.remove(KEY_OPENAI_API_KEY) else it[KEY_OPENAI_API_KEY] = trimmed
        }
    }

    override suspend fun setOpenAiBaseUrl(baseUrl: String) {
        context.dataStore.edit {
            it[KEY_OPENAI_BASE_URL] = baseUrl.trim().ifBlank { DEFAULT_OPENAI_BASE_URL }
        }
    }

    override suspend fun setOpenAiModel(model: String) {
        context.dataStore.edit {
            it[KEY_OPENAI_MODEL] = model.trim().ifBlank { DEFAULT_OPENAI_MODEL }
        }
    }

    private fun Preferences.lxSources(): List<LxSourceConfig> {
        val defaultName = context.getString(R.string.settings_default_lx_source_name)
        val parsed = parseLxSourcesJson(this[KEY_LX_SOURCES_JSON].orEmpty(), defaultName)
        if (parsed.isNotEmpty()) return parsed

        val legacyUrl = this[KEY_LX_SOURCE_URL].orEmpty()
        val legacyScript = this[KEY_LX_SOURCE_SCRIPT].orEmpty()
        if (legacyUrl.isBlank() && legacyScript.isBlank()) return emptyList()

        return listOf(
            LxSourceConfig(
                id = legacyUrl.toLxSourceId(legacyScript),
                url = legacyUrl,
                name = this[KEY_LX_SOURCE_NAME].orEmpty().ifBlank { defaultName },
                script = legacyScript
            )
        )
    }
}
