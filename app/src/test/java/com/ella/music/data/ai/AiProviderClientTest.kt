package com.ella.music.data.ai

import com.ella.music.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderClientTest {

    @Test
    fun compatibleChatEndpointAppendsCompletionsPath() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            aiChatEndpoint("https://api.deepseek.com/v1", AiApiProtocol.Compatible)
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            aiChatEndpoint(
                "https://api.deepseek.com/v1/chat/completions",
                AiApiProtocol.Compatible
            )
        )
    }

    @Test
    fun anthropicChatEndpointAppendsMessagesPath() {
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            aiChatEndpoint("https://api.anthropic.com/v1", AiApiProtocol.Anthropic)
        )
    }

    @Test
    fun modelsEndpointIsSharedAcrossProtocols() {
        assertEquals(
            "https://api.deepseek.com/v1/models",
            aiModelsEndpoint("https://api.deepseek.com/v1", AiApiProtocol.Compatible)
        )
        assertEquals(
            "https://api.anthropic.com/v1/models",
            aiModelsEndpoint("https://api.anthropic.com/v1", AiApiProtocol.Anthropic)
        )
    }

    @Test
    fun protocolResolvesFromStoredValueOrAnthropicUrl() {
        assertEquals(
            AiApiProtocol.Anthropic,
            resolveAiApiProtocol(SettingsManager.AI_API_PROTOCOL_ANTHROPIC, "https://api.deepseek.com/v1")
        )
        assertEquals(
            AiApiProtocol.Anthropic,
            resolveAiApiProtocol(SettingsManager.AI_API_PROTOCOL_COMPATIBLE, "https://api.anthropic.com/v1")
        )
        assertEquals(
            AiApiProtocol.Compatible,
            resolveAiApiProtocol(SettingsManager.AI_API_PROTOCOL_COMPATIBLE, "https://api.deepseek.com/v1")
        )
    }
}
