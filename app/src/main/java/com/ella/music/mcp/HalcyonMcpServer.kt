package com.ella.music.mcp

import com.ella.music.player.ExoPlayerManager
import com.ella.music.data.repository.MusicRepository
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Halcyon MCP Server — exposes music playback tools and library resources
 * via the Model Context Protocol for AI assistants like Claude.
 */
class HalcyonMcpServer(
    private val playerManager: ExoPlayerManager,
    private val repository: MusicRepository
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    val server: Server = Server(
        serverInfo = Implementation(
            name = "halcyon-music",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
                resources = ServerCapabilities.Resources(listChanged = true),
            ),
        ),
    )

    init {
        registerTools()
        registerResources()
    }

    private fun searchLibrary(query: String): List<com.ella.music.data.model.Song> {
        val q = query.lowercase()
        return repository.songs.value.filter { song ->
            song.title.lowercase().contains(q) ||
                song.artist.lowercase().contains(q) ||
                song.album.lowercase().contains(q)
        }
    }

    private fun registerTools() {
        server.addTool(
            name = "play_song",
            description = "Play a song by title, artist name, or query from the music library",
            inputSchema = schema("query" to "Song title, artist name, or search query")
        ) { request ->
            val query = request.arguments?.get("query")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (query.isBlank()) return@addTool ok("query is required")
            val songs = searchLibrary(query)
            if (songs.isEmpty()) return@addTool ok("No songs found for: $query")
            onMain { playerManager.playSong(songs.first()) }
            ok("Now playing: ${songs.first().title} — ${songs.first().artist}")
        }

        server.addTool(
            name = "search_music",
            description = "Search the music library for songs, artists, or albums",
            inputSchema = schema("query" to "Search query")
        ) { request ->
            val query = request.arguments?.get("query")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (query.isBlank()) return@addTool ok("query is required")
            val songs = searchLibrary(query).take(10)
            val items = songs.map { s ->
                buildJsonObject {
                    put("title", JsonPrimitive(s.title))
                    put("artist", JsonPrimitive(s.artist))
                    put("album", JsonPrimitive(s.album))
                    put("duration_ms", JsonPrimitive(s.duration))
                }
            }
            val obj = JsonObject(items.mapIndexed { i, v -> "song_${i + 1}" to v }.toMap())
            ok(json.encodeToString(JsonObject.serializer(), obj))
        }

        server.addTool(
            name = "get_now_playing",
            description = "Get information about the currently playing song and playback state",
            inputSchema = schema()
        ) { _ ->
            val song = playerManager.currentSong.value
                ?: return@addTool ok("""{"status":"idle"}""")
            val positionMs = onMain { playerManager.livePositionMs() }
            val result = buildJsonObject {
                put("title", JsonPrimitive(song.title))
                put("artist", JsonPrimitive(song.artist))
                put("album", JsonPrimitive(song.album))
                put("is_playing", JsonPrimitive(playerManager.isPlaying.value))
                put("position_ms", JsonPrimitive(positionMs))
                put("duration_ms", JsonPrimitive(playerManager.duration.value))
            }
            ok(json.encodeToString(JsonObject.serializer(), result))
        }

        server.addTool(
            name = "skip_next",
            description = "Skip to the next song in the queue",
            inputSchema = schema()
        ) { _ ->
            onMain { playerManager.skipToNext() }
            ok("Skipped to next song")
        }

        server.addTool(
            name = "skip_previous",
            description = "Skip to the previous song in the queue",
            inputSchema = schema()
        ) { _ ->
            onMain { playerManager.skipToPrevious() }
            ok("Skipped to previous song")
        }

        server.addTool(
            name = "toggle_play_pause",
            description = "Toggle between play and pause",
            inputSchema = schema()
        ) { _ ->
            onMain { playerManager.togglePlayPause() }
            ok("Playback ${if (playerManager.isPlaying.value) "playing" else "paused"}")
        }

        server.addTool(
            name = "toggle_shuffle",
            description = "Cycle playback mode (normal → shuffle → repeat)",
            inputSchema = schema()
        ) { _ ->
            onMain { playerManager.cyclePlaybackMode() }
            ok("Playback mode cycled")
        }

        server.addTool(
            name = "seek_to",
            description = "Seek to a position in the current song",
            inputSchema = schema("position_ms" to "Position in milliseconds")
        ) { request ->
            val pos = request.arguments?.get("position_ms")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: return@addTool ok("position_ms is required")
            onMain { playerManager.seekTo(pos) }
            ok("Seeked to ${pos}ms")
        }

        server.addTool(
            name = "get_queue",
            description = "Get the current playback queue",
            inputSchema = schema("limit" to "Max songs to return (default 20)")
        ) { request ->
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 20
            val playlist = playerManager.playlistFlow.value
            val current = playerManager.currentSong.value
            val startIdx = if (current != null) {
                playlist.indexOfFirst { it.id == current.id }.coerceAtLeast(0) + 1
            } else 0
            val upcoming = playlist.drop(startIdx).take(limit)
            val items = upcoming.map { s ->
                buildJsonObject {
                    put("title", JsonPrimitive(s.title))
                    put("artist", JsonPrimitive(s.artist))
                }
            }
            val obj = JsonObject(items.mapIndexed { i, v -> "track_${i + 1}" to v }.toMap())
            ok(json.encodeToString(JsonObject.serializer(), obj))
        }

        server.addTool(
            name = "get_library_stats",
            description = "Get music library statistics",
            inputSchema = schema()
        ) { _ ->
            val songs = repository.songs.value
            val result = buildJsonObject {
                put("total_songs", JsonPrimitive(songs.size))
                put("total_artists", JsonPrimitive(songs.map { it.artist }.distinct().size))
                put("total_albums", JsonPrimitive(songs.map { it.album }.distinct().size))
                put("total_duration_hours", JsonPrimitive(songs.sumOf { it.duration } / 3_600_000))
            }
            ok(json.encodeToString(JsonObject.serializer(), result))
        }
    }

    private fun registerResources() {
        server.addResource(
            uri = "halcyon://playback/current",
            name = "Current Playback",
            description = "Currently playing track and playback state",
            mimeType = "application/json"
        ) { _ ->
            val song = playerManager.currentSong.value
            val text = if (song != null) {
                json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("title", JsonPrimitive(song.title))
                    put("artist", JsonPrimitive(song.artist))
                    put("album", JsonPrimitive(song.album))
                    put("is_playing", JsonPrimitive(playerManager.isPlaying.value))
                    put("position_ms", JsonPrimitive(playerManager.currentPosition.value))
                    put("duration_ms", JsonPrimitive(playerManager.duration.value))
                })
            } else """{"status":"idle"}"""
            ReadResourceResult(contents = listOf(
                TextResourceContents(text = text, uri = "halcyon://playback/current", mimeType = "application/json")
            ))
        }

        server.addResource(
            uri = "halcyon://library/stats",
            name = "Library Stats",
            description = "Music library statistics",
            mimeType = "application/json"
        ) { _ ->
            val songs = repository.songs.value
            val text = json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("total_songs", JsonPrimitive(songs.size))
                put("total_artists", JsonPrimitive(songs.map { it.artist }.distinct().size))
                put("total_albums", JsonPrimitive(songs.map { it.album }.distinct().size))
            })
            ReadResourceResult(contents = listOf(
                TextResourceContents(text = text, uri = "halcyon://library/stats", mimeType = "application/json")
            ))
        }
    }
}

// ── Helpers ──

/**
 * Run [block] on the main thread. The Media3 [androidx.media3.session.MediaController]
 * may only be touched from the thread it was built on (the app's main looper); MCP
 * tool handlers run on Ktor worker threads, so every player mutation must hop here.
 */
private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

private fun ok(text: String) = CallToolResult(content = listOf(TextContent(text = text)))

/** Build a ToolSchema from property name→description pairs. */
private fun schema(vararg props: Pair<String, String>): ToolSchema {
    val properties = props.associate { (name, desc) ->
        name to buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("description", JsonPrimitive(desc))
        }
    }
    return ToolSchema(
        schema = "object",
        properties = JsonObject(properties),
        required = props.map { it.first },
    )
}
