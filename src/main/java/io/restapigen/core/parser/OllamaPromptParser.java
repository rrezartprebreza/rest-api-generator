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
 *   <li>The call times out ({@value #DEFAULT_TIMEOUT_SECONDS}s by default)</li>
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
    public static final String ENV_OLLAMA_TIMEOUT_SECONDS = "OLLAMA_TIMEOUT_SECONDS";

    private static final String DEFAULT_URL             = "http://localhost:11434";
    private static final String DEFAULT_MODEL           = "llama3.2";
    private static final int    DEFAULT_TIMEOUT_SECONDS = 90;

    /** Shared Jackson mapper. ObjectMapper is thread-safe after configuration. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a precise REST API specification assistant.
            Your job: convert any free-form description into a structured entity list.

            STRICT RULES:
            1. Extract EVERY entity the user mentions (explicitly named or clearly implied).
            2. Use the EXACT entity names the user provides — never rename them.
            3. If the user mentions "Product", write "Create an API for Product" — not "Item", not "Goods".
            4. For each entity produce a block in EXACTLY this format:

            Create an API for <EntityName> with:
            - <fieldName> (<type>[, required][, unique][, min <n>][, max <n>][, valid email])
            - <fieldName> (enum: VALUE1, VALUE2, VALUE3)
            - <fieldName> (timestamp)
            - belongs to <OtherEntity>
            - has many <OtherEntity>
            - many-to-many with <OtherEntity>

            5. Separate each entity block with exactly ONE blank line.
            6. Allowed field types: string, integer, decimal, boolean, date, timestamp, email.
            7. Output ONLY the structured blocks. No explanation, no markdown, no preamble.

            EXAMPLE INPUT:
            Blog platform: Post, User, Comment, Tag. Post has title and body. User has email and password. Comment belongs to Post and User. Tag has name, many-to-many with Post.

            EXAMPLE OUTPUT:
            Create an API for Post with:
            - title (string, required)
            - body (string, required)
            - publishedAt (timestamp)
            - belongs to User
            - has many Comment
            - many-to-many with Tag

            Create an API for User with:
            - email (email, required, unique)
            - password (string, required)
            - createdAt (timestamp)

            Create an API for Comment with:
            - body (string, required)
            - createdAt (timestamp)
            - belongs to Post
            - belongs to User

            Create an API for Tag with:
            - name (string, required, unique)
            - many-to-many with Post

            Now convert the following user description using the same rules:
            """;


    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final NaturalLanguagePromptParser fallback;
    private final HttpClient http;

    public OllamaPromptParser() {
        this(
            envOrDefault(ENV_OLLAMA_URL,   DEFAULT_URL),
            envOrDefault(ENV_OLLAMA_MODEL, DEFAULT_MODEL),
            envIntOrDefault(ENV_OLLAMA_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
        );
    }

    public OllamaPromptParser(String baseUrl, String model) {
        this(baseUrl, model, DEFAULT_TIMEOUT_SECONDS);
    }

    public OllamaPromptParser(String baseUrl, String model, int timeoutSeconds) {
        this.baseUrl  = baseUrl.replaceAll("/+$", "");
        this.model    = model;
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
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
                ApiSpecification structuredSpec = fallback.parse(structured, config);
                return NaturalLanguagePromptParser.applyProjectIdentity(prompt, structuredSpec, config);
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
                .timeout(Duration.ofSeconds(timeoutSeconds))
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
                .put("temperature", 0.05)
                .put("num_predict", 2048);
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

    private static int envIntOrDefault(String key, int fallback) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
