package io.restapigen.server;

import io.restapigen.core.parser.CloudLlmPromptParser;
import io.restapigen.core.parser.OllamaPromptParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestApiGeneratorServerParserSelectionTest {

    @Test
    void localProfilePrefersOllamaWhenBothProvidersAreConfigured() {
        Map<String, String> env = Map.of(
                RestApiGeneratorServer.ENV_APP_ENV, "local",
                OllamaPromptParser.ENV_OLLAMA_URL, "http://ollama:11434",
                CloudLlmPromptParser.ENV_LLM_API_KEY, "groq-key"
        );

        assertEquals(RestApiGeneratorServer.PromptParserMode.OLLAMA, RestApiGeneratorServer.selectPromptParser(env));
    }

    @Test
    void productionProfilePrefersCloudWhenBothProvidersAreConfigured() {
        Map<String, String> env = Map.of(
                RestApiGeneratorServer.ENV_APP_ENV, "production",
                OllamaPromptParser.ENV_OLLAMA_URL, "http://ollama:11434",
                CloudLlmPromptParser.ENV_LLM_API_KEY, "groq-key"
        );

        assertEquals(RestApiGeneratorServer.PromptParserMode.CLOUD, RestApiGeneratorServer.selectPromptParser(env));
    }

    @Test
    void localProfileFallsBackToCloudWhenOllamaIsNotConfigured() {
        Map<String, String> env = Map.of(
                RestApiGeneratorServer.ENV_RUNTIME_PROFILE, "local",
                CloudLlmPromptParser.ENV_LLM_API_KEY, "groq-key"
        );

        assertEquals(RestApiGeneratorServer.PromptParserMode.CLOUD, RestApiGeneratorServer.selectPromptParser(env));
    }

    @Test
    void autoProfileKeepsCloudFirstPriority() {
        Map<String, String> env = Map.of(
                OllamaPromptParser.ENV_OLLAMA_URL, "http://ollama:11434",
                CloudLlmPromptParser.ENV_LLM_API_KEY, "groq-key"
        );

        assertEquals(RestApiGeneratorServer.PromptParserMode.CLOUD, RestApiGeneratorServer.selectPromptParser(env));
    }

    @Test
    void noProvidersUsesDeterministicParser() {
        assertEquals(
                RestApiGeneratorServer.PromptParserMode.DETERMINISTIC,
                RestApiGeneratorServer.selectPromptParser(Map.of())
        );
    }
}
