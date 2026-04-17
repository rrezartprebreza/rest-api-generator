package io.restapigen.core.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restapigen.core.config.GenerationConfig;
import io.restapigen.domain.ApiSpecification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Prompt parser that calls a running Ollama instance to normalise free-form
 * natural language into the structured format the deterministic
 * {@link NaturalLanguagePromptParser} understands.
 *
 * <p>Falls back to {@link NaturalLanguagePromptParser} automatically when:
 * <ul>
 *   <li>Ollama is unreachable or returns an error</li>
 *   <li>The LLM output cannot be extracted</li>
 *   <li>The call times out ({@value #TIMEOUT_SECONDS}s)</li>
 * </ul>
 *
 * <p>Configure via environment variable {@code OLLAMA_URL} (default:
 * {@code http://localhost:11434}).  The model is controlled by
 * {@code OLLAMA_MODEL} (default: {@code llama3.2}).
 */
public final class OllamaPromptParser implements PromptParser {

    private static final Logger LOG = Logger.getLogger(OllamaPromptParser.class.getName());

    public static final String ENV_OLLAMA_URL   = "OLLAMA_URL";
    public static final String ENV_OLLAMA_MODEL = "OLLAMA_MODEL";

    private static final String DEFAULT_URL   = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.2";
    private static final int    TIMEOUT_SECONDS = 30;

    /** Shared Jackson mapper. ObjectMapper is thread-safe after configuration. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a precise API specification assistant.
            Convert the user's free-form description into a structured prompt
            that follows this exact format:

            Create an API for <EntityName> with:
            - <fieldName> (<type>[, required][, min <n>][, max <n>][, valid email][, unique])
            - <fieldName> (enum: VALUE1, VALUE2, VALUE3)
            - <fieldName> (timestamp)
            - belongs to <OtherEntity>
            - has many <OtherEntity>
            - many-to-many with <OtherEntity>

            Repeat the block for each entity the user mentions.
            Separate entity blocks with a blank line.
            Allowed types: string, integer, decimal, boolean, date, timestamp, email.
            Output ONLY the structured prompt. No explanation, no markdown, no extra text.
            """;

    private final String baseUrl;
    private final String model;
    private final NaturalLanguagePromptParser fallback;
    private final HttpClient http;

    public OllamaPromptParser() {
        this(
            envOrDefault(ENV_OLLAMA_URL,   DEFAULT_URL),
            envOrDefault(ENV_OLLAMA_MODEL, DEFAULT_MODEL)
        );
    }

    public OllamaPromptParser(String baseUrl, String model) {
        this.baseUrl  = baseUrl.replaceAll("/+$", "");
        this.model    = model;
        this.fallback = new NaturalLanguagePromptParser();
        this.http     = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public ApiSpecification parse(String prompt, GenerationConfig config) {
        try {
            String structured = callOllama(prompt);
            if (structured != null && !structured.isBlank()) {
                LOG.info("[OllamaPromptParser] LLM-structured prompt length=" + structured.length());
                return fallback.parse(structured, config);
            }
        } catch (Exception e) {
            LOG.warning("[OllamaPromptParser] Ollama unavailable, using deterministic parser. Reason: " + e.getMessage());
        }
        return fallback.parse(prompt, config);
    }

    /** Returns {@code true} if Ollama responds to a quick health ping. */
    public boolean isAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public String getBaseUrl() { return baseUrl; }
    public String getModel()   { return model; }

    // ── private ───────────────────────────────────────────────────────────────

    private String callOllama(String userPrompt) throws IOException, InterruptedException {
        String body = buildRequestBody(userPrompt);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Ollama returned HTTP " + res.statusCode());
        }
        return extractResponse(res.body());
    }

    private String buildRequestBody(String userPrompt) throws IOException {
        ObjectNode root = JSON.createObjectNode()
                .put("model", model)
                .put("system", SYSTEM_PROMPT)
                .put("prompt", userPrompt)
                .put("stream", false);
        root.putObject("options")
                .put("temperature", 0.1)
                .put("num_predict", 1024);
        return JSON.writeValueAsString(root);
    }

    /**
     * Ollama /api/generate returns a single JSON object when stream=false.
     * Extract the "response" field via Jackson — JSON escape handling, nesting,
     * and edge cases are the parser's problem, not ours.
     */
    private static String extractResponse(String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        JsonNode response = root.path("response");
        if (response.isMissingNode() || response.isNull()) {
            return null;
        }
        return response.asText();
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }
}

