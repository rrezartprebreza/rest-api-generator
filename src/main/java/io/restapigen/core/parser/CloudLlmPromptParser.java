package io.restapigen.core.parser;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.domain.ApiSpecification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final NaturalLanguagePromptParser fallback;
    private final HttpClient http;

    public CloudLlmPromptParser() {
        this(
            System.getenv(ENV_LLM_API_KEY),
            envOrDefault(ENV_LLM_BASE_URL, DEFAULT_BASE_URL),
            envOrDefault(ENV_LLM_MODEL,    DEFAULT_MODEL)
        );
    }

    public CloudLlmPromptParser(String apiKey, String baseUrl, String model) {
        this.apiKey   = apiKey;
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
            String structured = callApi(prompt);
            if (structured != null && !structured.isBlank()) {
                LOG.info("[CloudLlmPromptParser] LLM-structured prompt length=" + structured.length());
                return fallback.parse(structured, config);
            }
        } catch (Exception e) {
            LOG.warning("[CloudLlmPromptParser] API unavailable, falling back to deterministic. Reason: " + e.getMessage());
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
    public String getModel()   { return model; }

    // ── private ───────────────────────────────────────────────────────────────

    private String callApi(String userPrompt) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("LLM_API_KEY is not set");
        }
        String body = buildRequestBody(userPrompt);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type",  "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            String preview = res.body().substring(0, Math.min(200, res.body().length()));
            throw new IOException("API returned HTTP " + res.statusCode() + ": " + preview);
        }
        return extractContent(res.body());
    }

    private String buildRequestBody(String userPrompt) {
        String safeSystem = jsonEscape(SYSTEM_PROMPT);
        String safePrompt = jsonEscape(userPrompt);
        // OpenAI-compatible chat completions format
        return """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user",   "content": "%s"}
                  ],
                  "temperature": 0.1,
                  "max_tokens": 1024
                }
                """.formatted(jsonEscape(model), safeSystem, safePrompt);
    }

    /**
     * OpenAI /chat/completions response shape:
     *   {"choices":[{"message":{"role":"assistant","content":"<text>"}}]}
     * Extract the assistant message content.
     */
    private static String extractContent(String json) {
        Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return unescapeJson(m.group(1));
        }
        return null;
    }

    private static String jsonEscape(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        return s
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }
}

