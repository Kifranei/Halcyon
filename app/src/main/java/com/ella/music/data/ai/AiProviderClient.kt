package com.ella.music.data.ai

import android.content.Context
import com.ella.music.R
import com.ella.music.data.SettingsManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class AiApiProtocol {
    Compatible,
    Anthropic
}

fun Int.toAiApiProtocol(): AiApiProtocol =
    if (this == SettingsManager.AI_API_PROTOCOL_ANTHROPIC) {
        AiApiProtocol.Anthropic
    } else {
        AiApiProtocol.Compatible
    }

internal fun resolveAiApiProtocol(stored: Int, baseUrl: String): AiApiProtocol {
    if (stored == SettingsManager.AI_API_PROTOCOL_ANTHROPIC) return AiApiProtocol.Anthropic
    return if (baseUrl.contains("anthropic", ignoreCase = true)) {
        AiApiProtocol.Anthropic
    } else {
        AiApiProtocol.Compatible
    }
}

internal fun aiRootUrl(baseUrl: String): String =
    baseUrl.trim().ifBlank { SettingsManager.DEFAULT_OPENAI_BASE_URL }.trimEnd('/')
        .removeSuffix("/chat/completions")
        .removeSuffix("/messages")
        .removeSuffix("/models")
        .trimEnd('/')

internal fun aiChatEndpoint(baseUrl: String, protocol: AiApiProtocol): String {
    val root = aiRootUrl(baseUrl)
    return when (protocol) {
        AiApiProtocol.Anthropic ->
            if (root.endsWith("/messages")) root else "$root/messages"
        AiApiProtocol.Compatible -> when {
            root.endsWith("/chat/completions") -> root
            root.endsWith("/responses") -> root.removeSuffix("/responses") + "/chat/completions"
            else -> "$root/chat/completions"
        }
    }
}

internal fun aiModelsEndpoint(baseUrl: String, protocol: AiApiProtocol): String {
    val root = aiRootUrl(baseUrl)
    return if (root.endsWith("/models")) root else "$root/models"
}

internal fun parseAiModelIds(body: String): List<String> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    val data = root.optJSONArray("data") ?: return emptyList()
    val ids = mutableListOf<String>()
    for (i in 0 until data.length()) {
        val id = data.optJSONObject(i)?.optString("id").orEmpty().trim()
        if (id.isNotBlank()) ids += id
    }
    return ids.distinct()
}

internal fun parseAiChatText(body: String, protocol: AiApiProtocol): String {
    val root = JSONObject(body)
    if (protocol == AiApiProtocol.Anthropic) {
        val content = root.optJSONArray("content") ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            val text = item.optString("text")
            if (text.isNotBlank()) parts += text
        }
        return parts.joinToString("\n").trim()
    }
    root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
    val choices = root.optJSONArray("choices")
    if (choices != null) {
        val parts = mutableListOf<String>()
        for (i in 0 until choices.length()) {
            val text = choices
                .optJSONObject(i)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (text.isNotBlank()) parts += text
        }
        if (parts.isNotEmpty()) return parts.joinToString("\n").trim()
    }
    val output = root.optJSONArray("output") ?: return ""
    val parts = mutableListOf<String>()
    for (i in 0 until output.length()) {
        val item = output.optJSONObject(i) ?: continue
        val content = item.optJSONArray("content") ?: continue
        for (j in 0 until content.length()) {
            val contentItem = content.optJSONObject(j) ?: continue
            val text = contentItem.optString("text")
                .ifBlank { contentItem.optString("output_text") }
            if (text.isNotBlank()) parts += text
        }
    }
    return parts.joinToString("\n").trim()
}

class AiProviderClient(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun completeChat(
        config: OpenAiSongInterpretationConfig,
        systemPrompt: String,
        userPrompt: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        temperature: Double,
        topP: Double,
        maxTokens: Int
    ): String {
        val apiKey = config.apiKey.trim()
        if (apiKey.isBlank()) error(context.getString(R.string.error_openai_missing_api_key))
        val protocol = config.protocol
        val model = config.model.trim().ifBlank { SettingsManager.DEFAULT_OPENAI_MODEL }
        val requestBody = when (protocol) {
            AiApiProtocol.Anthropic -> anthropicBody(
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                conversationHistory = conversationHistory,
                maxTokens = maxTokens
            )
            AiApiProtocol.Compatible -> compatibleBody(
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                conversationHistory = conversationHistory,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens
            )
        }
        val request = Request.Builder()
            .url(aiChatEndpoint(config.baseUrl, protocol))
            .apply { applyAuthHeaders(apiKey, protocol) }
            .header("Content-Type", "application/json")
            .header("User-Agent", "Halcyon")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwAiHttpError(response.code, body)
            }
            val text = parseAiChatText(body, protocol)
            if (text.isBlank()) error(context.getString(R.string.error_openai_empty_response))
            text.trim()
        }
    }

    fun listModels(config: OpenAiSongInterpretationConfig): List<String> {
        val apiKey = config.apiKey.trim()
        if (apiKey.isBlank()) error(context.getString(R.string.error_openai_missing_api_key))
        val protocol = config.protocol
        val request = Request.Builder()
            .url(aiModelsEndpoint(config.baseUrl, protocol))
            .apply { applyAuthHeaders(apiKey, protocol) }
            .header("User-Agent", "Halcyon")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwAiHttpError(response.code, body)
            }
            parseAiModelIds(body)
        }
    }

    private fun Request.Builder.applyAuthHeaders(apiKey: String, protocol: AiApiProtocol) {
        when (protocol) {
            AiApiProtocol.Anthropic -> {
                header("x-api-key", apiKey)
                header("anthropic-version", ANTHROPIC_VERSION)
            }
            AiApiProtocol.Compatible -> header("Authorization", "Bearer $apiKey")
        }
    }

    private fun throwAiHttpError(code: Int, body: String): Nothing {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()
        error(
            context.getString(
                R.string.error_openai_request_failed,
                code,
                message.takeIf { it.isNotBlank() }
                    ?.let { "${context.getString(R.string.error_openai_api_error_separator)}$it" }
                    .orEmpty()
            )
        )
    }

    private fun compatibleBody(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        conversationHistory: List<Pair<String, String>>,
        temperature: Double,
        topP: Double,
        maxTokens: Int
    ): JSONObject {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
        conversationHistory.forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userPrompt))
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", temperature)
            .put("top_p", topP)
            .put("max_tokens", maxTokens)
    }

    private fun anthropicBody(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        conversationHistory: List<Pair<String, String>>,
        maxTokens: Int
    ): JSONObject {
        val messages = JSONArray()
        conversationHistory.forEach { (role, content) ->
            if (role == "system") return@forEach
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userPrompt))
        return JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("system", systemPrompt)
            .put("messages", messages)
    }

    companion object {
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
