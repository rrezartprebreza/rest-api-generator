package io.restapigen.core.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restapigen.core.config.GenerationConfig;
import io.restapigen.domain.ApiSpecification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Prompt parser that calls any OpenAI-compatible chat completions API
 * (Groq, OpenAI, Together.ai, etc.) to normalise free-form prompts.
 *
 * <p>Activate by setting the {@value #ENV_LLM_API_KEY} environment variable.
 * Optional overrides: {@value #ENV_LLM_BASE_URL}, {@value #ENV_LLM_MODEL}.
 *
 * <p>Defaults to <a href="https://console.groq.com">Groq</a>
 * ({@value #DEFAULT_BASE_URL}) with model {@value #DEFAULT_MODEL}.
 * Groq offers a free tier — no credit card required.
 *
 * <p><b>Automatic model rotation:</b> when the active model returns HTTP 429
 * (rate-limit exceeded), the parser automatically tries the next model in
 * {@link #GROQ_FREE_MODELS}. The working model index is remembered so
 * subsequent requests start from the last-known working model.
 * If all models are exhausted the call returns {@code null} and the caller
 * falls back to {@link NaturalLanguagePromptParser}.
 *
 * <p>Falls back to {@link NaturalLanguagePromptParser} automatically when
 * the API is unreachable, the key is invalid, or a timeout occurs.
 */
public final class CloudLlmPromptParser implements PromptParser {

    private static final Logger LOG = Logger.getLogger(CloudLlmPromptParser.class.getName());

    public static final String ENV_LLM_API_KEY  = "LLM_API_KEY";
    public static final String ENV_LLM_BASE_URL = "LLM_BASE_URL";
    public static final String ENV_LLM_MODEL    = "LLM_MODEL";

    private static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";
    private static final String DEFAULT_MODEL    = "llama-3.3-70b-versatile";
    private static final int    TIMEOUT_SECONDS  = 30;

    /** Shared Jackson mapper. ObjectMapper is thread-safe after configuration. */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Ordered list of Groq free-tier models used as the rotation chain.
     * When the primary model hits its rate limit the parser tries each
     * entry in order until one succeeds.
     *
     * Daily free-tier request limits (approximate, verify at console.groq.com):
     *   llama-3.3-70b-versatile  —  1 000 req / day  (best quality)
     *   llama3-8b-8192           — 14 400 req / day  (fast)
     *   gemma2-9b-it             — 14 400 req / day
     *   llama-3.1-8b-instant     — 14 400 req / day  (fastest)
     */
    private static final List<String> GROQ_FREE_MODELS = List.of(
            "llama-3.3-70b-versatile",
            "llama3-8b-8192",
            "gemma2-9b-it",
            "llama-3.1-8b-instant"
    );

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

    private final String       apiKey;
    private final String       baseUrl;
    private final List<String> modelChain;   // primary first, then fallbacks
    private final AtomicInteger modelIndex;  // index into modelChain, rotates on 429
    private final NaturalLanguagePromptParser fallback;
    private final HttpClient   http;

    public CloudLlmPromptParser() {
        this(
            System.getenv(ENV_LLM_API_KEY),
            envOrDefault(ENV_LLM_BASE_URL, DEFAULT_BASE_URL),
            envOrDefault(ENV_LLM_MODEL,    DEFAULT_MODEL)
        );
    }

    public CloudLlmPromptParser(String apiKey, String baseUrl, String primaryModel) {
        this.apiKey    = apiKey;
        this.baseUrl   = baseUrl.replaceAll("/+$", "");
        this.modelChain = buildModelChain(primaryModel);
        this.modelIndex = new AtomicInteger(0);
        this.fallback   = new NaturalLanguagePromptParser();
        this.http       = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        LOG.info("[CloudLlmPromptParser] model chain: " + this.modelChain);
    }

    @Override
    public ApiSpecification parse(String prompt, GenerationConfig config) {
        try {
            String structured = callApiWithFallback(prompt);
            if (structured != null && !structured.isBlank()) {
                LOG.info("[CloudLlmPromptParser] LLM-structured prompt length=" + structured.length()
                        + " via model=" + activeModel());
                ApiSpecification structuredSpec = fallback.parse(structured, config);
                return NaturalLanguagePromptParser.applyProjectIdentity(prompt, structuredSpec, config);
            }
        } catch (Exception e) {
            LOG.warning("[CloudLlmPromptParser] All models exhausted or error, using deterministic. Reason: "
                    + e.getMessage());
        }
        return fallback.parse(prompt, config);
    }

    /**
     * Returns {@code true} if the API key is set and the models endpoint responds.
     * Used by the /about endpoint to show real-time availability.
     */
    public boolean isAvailable() {
        if (apiKey == null || apiKey.isBlank()) return false;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models"))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public String getBaseUrl() { return baseUrl; }

    /** Returns the primary (configured) model name. */
    public String getModel()   { return modelChain.get(0); }

    /** Returns the currently active model (may differ from primary if rotated). */
    public String activeModel() { return modelChain.get(modelIndex.get() % modelChain.size()); }

    // ── private ───────────────────────────────────────────────────────────────

    /**
     * Tries each model in the chain starting from the last-known working index.
     * On HTTP 429 it advances to the next model; on any other error it gives up
     * immediately (wrong key, server error, timeout — retrying won't help).
     *
     * @return structured prompt text, or {@code null} if every model is rate-limited
     */
    private String callApiWithFallback(String userPrompt) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("LLM_API_KEY is not set");
        }
        int size       = modelChain.size();
        int startIndex = modelIndex.get() % size;

        for (int i = 0; i < size; i++) {
            int    idx   = (startIndex + i) % size;
            String model = modelChain.get(idx);
            try {
                String result = callApiWithModel(model, userPrompt);
                // Success — remember this index for the next request
                modelIndex.set(idx);
                return result;
            } catch (RateLimitException e) {
                int nextIdx = (idx + 1) % size;
                LOG.warning("[CloudLlmPromptParser] 429 on model=" + model
                        + " — rotating to " + modelChain.get(nextIdx));
                // Advance index so next request also skips the exhausted model
                modelIndex.compareAndSet(idx, nextIdx);
                // continue loop to try the next model in this request
            }
            // Any other IOException / InterruptedException propagates immediately
        }
        LOG.warning("[CloudLlmPromptParser] All " + size + " models rate-limited — using deterministic fallback");
        return null;
    }

    private String callApiWithModel(String model, String userPrompt)
            throws IOException, InterruptedException, RateLimitException {
        String body = buildRequestBody(model, userPrompt);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type",  "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429) {
            throw new RateLimitException("rate-limited on model=" + model);
        }
        if (res.statusCode() != 200) {
            String preview = res.body().substring(0, Math.min(200, res.body().length()));
            throw new IOException("API returned HTTP " + res.statusCode() + ": " + preview);
        }
        return extractContent(res.body());
    }

    private String buildRequestBody(String model, String userPrompt) throws IOException {
        ObjectNode root = JSON.createObjectNode()
                .put("model", model)
                .put("temperature", 0.1)
                .put("max_tokens", 1024);
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", userPrompt);
        return JSON.writeValueAsString(root);
    }

    /**
     * Build the model rotation chain: primaryModel first, then all GROQ_FREE_MODELS
     * that are different from the primary (preserving order, de-duplicated).
     */
    private static List<String> buildModelChain(String primaryModel) {
        List<String> chain = new ArrayList<>();
        chain.add(primaryModel);
        for (String m : GROQ_FREE_MODELS) {
            if (!m.equalsIgnoreCase(primaryModel)) {
                chain.add(m);
            }
        }
        return List.copyOf(chain);
    }

    /**
     * OpenAI /chat/completions response shape:
     *   {"choices":[{"message":{"role":"assistant","content":"<text>"}}]}
     * Navigate the tree via Jackson — no regex, no hand-rolled escape handling.
     * Unlike the old regex-based extractor, this cannot match a stray
     * {@code "content"} key elsewhere in the payload (e.g. inside tool_calls).
     */
    private static String extractContent(String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            return null;
        }
        return content.asText();
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }

    /** Thrown when the API returns HTTP 429 (rate limit exceeded). */
    private static final class RateLimitException extends Exception {
        RateLimitException(String message) { super(message); }
    }
}
