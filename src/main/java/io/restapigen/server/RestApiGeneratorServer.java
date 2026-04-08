package io.restapigen.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.restapigen.codegen.CodeGenerator;
import io.restapigen.core.config.GenerationConfig;
import io.restapigen.core.parser.CloudLlmPromptParser;
import io.restapigen.core.parser.NaturalLanguagePromptParser;
import io.restapigen.core.parser.OllamaPromptParser;
import io.restapigen.core.parser.PromptParser;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.generator.parser.SpecInputExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class RestApiGeneratorServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RestApiGeneratorServer.class.getName());
    private static final int DEFAULT_THREAD_POOL = 8;
    private static final String APP_VERSION = loadAppVersion();

    private final HttpServer           server;
    private final PromptParser         parser;
    private final OllamaPromptParser   ollamaParser;    // non-null when OLLAMA_URL is set
    private final CloudLlmPromptParser cloudLlmParser;  // non-null when LLM_API_KEY is set
    private final CodeGenerator        codeGenerator;
    private final GenerationConfig     config;
    private final ObjectMapper         mapper;

    public RestApiGeneratorServer(int port) throws IOException {
        this(port, GenerationConfig.defaults());
    }

    public RestApiGeneratorServer(int port, GenerationConfig config) throws IOException {
        this.server        = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(DEFAULT_THREAD_POOL));
        this.config        = config == null ? GenerationConfig.defaults() : config;
        this.mapper        = new ObjectMapper().findAndRegisterModules();
        this.codeGenerator = new CodeGenerator();

        String ollamaUrl = System.getenv(OllamaPromptParser.ENV_OLLAMA_URL);
        String llmApiKey = System.getenv(CloudLlmPromptParser.ENV_LLM_API_KEY);

        if (ollamaUrl != null && !ollamaUrl.isBlank()) {
            // Local Ollama (Docker Compose --profile llm)
            this.ollamaParser   = new OllamaPromptParser();
            this.cloudLlmParser = null;
            this.parser         = this.ollamaParser;
            LOG.info("Prompt parser: Ollama at " + this.ollamaParser.getBaseUrl()
                    + " model=" + this.ollamaParser.getModel() + " (fallback: deterministic)");
        } else if (llmApiKey != null && !llmApiKey.isBlank()) {
            // Cloud LLM — Groq, OpenAI, or any OpenAI-compatible endpoint
            this.ollamaParser   = null;
            this.cloudLlmParser = new CloudLlmPromptParser();
            this.parser         = this.cloudLlmParser;
            LOG.info("Prompt parser: cloud LLM at " + this.cloudLlmParser.getBaseUrl()
                    + " model=" + this.cloudLlmParser.getModel() + " (fallback: deterministic)");
        } else {
            // Fully deterministic — no LLM required
            this.ollamaParser   = null;
            this.cloudLlmParser = null;
            this.parser         = new NaturalLanguagePromptParser();
            LOG.info("Prompt parser: deterministic (set OLLAMA_URL or LLM_API_KEY to enable LLM mode)");
        }

        registerContexts();
    }

    public void start()  { server.start(); }

    @Override
    public void close()  { server.stop(0); }

    private void registerContexts() {
        server.createContext("/generator/spec", new SpecHandler());
        server.createContext("/generator/code", new CodeHandler());
        server.createContext("/about",           new AboutHandler());
        server.createContext("/health",          new HealthHandler());
        server.createContext("/",                new StaticFileHandler());
    }

    // ── CORS helper ───────────────────────────────────────────────────────────

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Accept");
    }

    /** Returns true if this was a preflight OPTIONS request (already handled). */
    private static boolean handlePreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    // ── POST /generator/spec ──────────────────────────────────────────────────

    private final class SpecHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, jsonError("METHOD_NOT_ALLOWED", "Only POST is supported"), "application/json");
                return;
            }
            String requestBody = readBody(exchange);
            String prompt = extractPrompt(requestBody);
            if (prompt.isBlank()) {
                respond(exchange, 400, jsonError("MISSING_PROMPT", "Field 'prompt' is required and must not be blank"), "application/json");
                return;
            }
            // Basic length guard — prevents abuse
            if (prompt.length() > 32_000) {
                respond(exchange, 400, jsonError("PROMPT_TOO_LONG", "Prompt must be 32 000 characters or fewer"), "application/json");
                return;
            }
            try {
                String userRequest = SpecInputExtractor.extractUserRequestOrWholeInput(prompt);
                ApiSpecification spec = parser.parse(userRequest, config);
                byte[] payload = mapper.writeValueAsBytes(spec);
                respond(exchange, 200, payload, "application/json");
            } catch (Exception e) {
                respond(exchange, 500, jsonError("PARSE_ERROR", "Failed to parse prompt: " + sanitize(e.getMessage())), "application/json");
            }
        }

        private String extractPrompt(String body) {
            if (body.isEmpty()) return "";
            try {
                SpecRequest req = mapper.readValue(body, SpecRequest.class);
                if (req.prompt() != null && !req.prompt().isBlank()) return req.prompt();
            } catch (JsonProcessingException ignored) {}
            return body;
        }
    }

    // ── POST /generator/code ──────────────────────────────────────────────────

    private final class CodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, jsonError("METHOD_NOT_ALLOWED", "Only POST is supported"), "application/json");
                return;
            }
            String requestBody = readBody(exchange);
            if (requestBody.isEmpty()) {
                respond(exchange, 400, jsonError("MISSING_SPEC", "Request body (API spec JSON) is required"), "application/json");
                return;
            }
            ApiSpecification spec;
            try {
                spec = mapper.readValue(requestBody, ApiSpecification.class);
            } catch (JsonProcessingException e) {
                respond(exchange, 400, jsonError("INVALID_SPEC", "invalid spec payload: " + sanitize(e.getMessage())), "application/json");
                return;
            }
            try {
                byte[] zip = codeGenerator.generateZip(spec, config);
                String filename = (spec.projectName != null && !spec.projectName.isBlank())
                        ? spec.projectName + ".zip"
                        : "scaffold.zip";
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                respond(exchange, 200, zip, "application/zip");
            } catch (IllegalArgumentException e) {
                respond(exchange, 400, jsonError("BAD_SPEC", sanitize(e.getMessage())), "application/json");
            } catch (Exception e) {
                respond(exchange, 500, jsonError("GENERATION_ERROR", "Code generation failed"), "application/json");
            }
        }
    }

    // ── GET /about ────────────────────────────────────────────────────────────

    private final class AboutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, jsonError("METHOD_NOT_ALLOWED", "Only GET is supported"), "application/json");
                return;
            }

            boolean llmConfigured;
            boolean llmAvailable;
            String  parserMode;

            if (ollamaParser != null) {
                llmConfigured = true;
                llmAvailable  = ollamaParser.isAvailable();
                parserMode    = llmAvailable ? "ollama" : "ollama-offline";
            } else if (cloudLlmParser != null) {
                llmConfigured = true;
                llmAvailable  = cloudLlmParser.isAvailable();
                String url    = cloudLlmParser.getBaseUrl();
                String tag    = url.contains("groq.com")    ? "groq"
                              : url.contains("openai.com")  ? "openai"
                              : "cloud-llm";
                parserMode    = llmAvailable ? tag : (tag + "-offline");
            } else {
                llmConfigured = false;
                llmAvailable  = false;
                parserMode    = "deterministic";
            }

            String aboutJson = """
                    {
                      "name": "REST API Generator",
                      "version": "%s",
                      "description": "Generate production-ready Spring Boot REST APIs from plain English in seconds.",
                      "promptParser": "%s",
                      "llmConfigured": %s,
                      "llmAvailable": %s,
                      "endpoints": [
                        {"method": "GET",  "path": "/",                "description": "Web UI"},
                        {"method": "GET",  "path": "/about",           "description": "Project information"},
                        {"method": "GET",  "path": "/health",          "description": "Health check"},
                        {"method": "POST", "path": "/generator/spec",  "description": "Parse a natural-language prompt into an API specification"},
                        {"method": "POST", "path": "/generator/code",  "description": "Generate a runnable Spring Boot ZIP from an API specification"}
                      ],
                      "repository": "https://github.com/rrezartprebreza/rest-api-generator"
                    }""".formatted(APP_VERSION, parserMode, llmConfigured, llmAvailable);
            respond(exchange, 200, aboutJson, "application/json");
        }
    }

    // ── GET /health ───────────────────────────────────────────────────────────

    private final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            respond(exchange, 200, "{\"status\":\"UP\"}", "application/json");
        }
    }

    // ── GET / → serve index.html from classpath ───────────────────────────────

    private final class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "Method Not Allowed", "text/plain");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            // Only serve root — all other paths that didn't match a context fall here
            if (!"/".equals(path) && !path.isBlank()) {
                respond(exchange, 404, jsonError("NOT_FOUND", "No resource at " + path), "application/json");
                return;
            }
            try (InputStream in = getClass().getResourceAsStream("/static/index.html")) {
                if (in == null) {
                    String fallback = "<html><body><h2>REST API Generator</h2>"
                            + "<p>Server is running. See <a href='/about'>/about</a> for API details.</p>"
                            + "<p>Deploy the Web UI by placing index.html in src/main/resources/static/</p></body></html>";
                    respond(exchange, 200, fallback, "text/html; charset=utf-8");
                    return;
                }
                byte[] html = in.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(200, html.length);
                exchange.getResponseBody().write(html);
            } finally {
                exchange.close();
            }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    private static String jsonError(String code, String message) {
        // Simple JSON — avoids a dependency on ObjectMapper for error paths
        String safeMessage = message == null ? "" : message.replace("\"", "'");
        return "{\"code\":\"" + code + "\",\"message\":\"" + safeMessage + "\"}";
    }

    private static String sanitize(String input) {
        if (input == null) return "unknown error";
        // Strip stack trace hints and limit length
        String normalized = input.replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(normalized.length(), 200));
    }

    private static String loadAppVersion() {
        try (InputStream in = RestApiGeneratorServer.class.getResourceAsStream("/rest-api-generator.properties")) {
            if (in == null) return "dev";
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("app.version");
            if (version == null || version.isBlank() || version.contains("${")) {
                return "dev";
            }
            return version;
        } catch (IOException ignored) {
            return "dev";
        }
    }

    private void respond(HttpExchange exchange, int status, byte[] payload, String contentType) throws IOException {
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private void respond(HttpExchange exchange, int status, String message, String contentType) throws IOException {
        respond(exchange, status, message.getBytes(StandardCharsets.UTF_8), contentType);
    }

    record SpecRequest(String prompt) {}
}
