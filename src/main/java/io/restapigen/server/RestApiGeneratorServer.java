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
import io.restapigen.core.validator.SpecDiagnosticsValidator;
import io.restapigen.core.validator.SpecValidator;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.generator.parser.SpecInputExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class RestApiGeneratorServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RestApiGeneratorServer.class.getName());
    private static final int DEFAULT_THREAD_POOL = 8;
    private static final String APP_VERSION = loadAppVersion();
    static final String ENV_RUNTIME_PROFILE = "REST_API_GENERATOR_ENV";
    static final String ENV_APP_ENV = "APP_ENV";

    private final HttpServer           server;
    private final PromptParser         parser;
    private final OllamaPromptParser   ollamaParser;    // non-null when OLLAMA_URL is set
    private final CloudLlmPromptParser cloudLlmParser;  // non-null when LLM_API_KEY is set
    private final CodeGenerator        codeGenerator;
    private final GenerationConfig     config;
    private final ObjectMapper         mapper;
    private final SpecDiagnosticsValidator diagnosticsValidator;
    private final SpecValidator            specValidator;
    private final boolean                  confidenceFailPolicyEnabled;

    public RestApiGeneratorServer(int port) throws IOException {
        this(port, GenerationConfig.defaults());
    }

    public RestApiGeneratorServer(int port, GenerationConfig config) throws IOException {
        this.server        = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(DEFAULT_THREAD_POOL));
        this.config        = config == null ? GenerationConfig.defaults() : config;
        this.mapper        = new ObjectMapper().findAndRegisterModules();
        this.codeGenerator = new CodeGenerator();
        this.diagnosticsValidator = new SpecDiagnosticsValidator();
        this.specValidator = new SpecValidator();
        this.confidenceFailPolicyEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("CONFIDENCE_FAIL_POLICY", "false"));

        Map<String, String> env = System.getenv();
        PromptParserMode parserMode = selectPromptParser(env);

        if (parserMode == PromptParserMode.CLOUD) {
            // Cloud LLM — Groq (default), OpenAI, or any OpenAI-compatible endpoint.
            // Activate in production by setting APP_ENV=production and LLM_API_KEY=<your-groq-key>
            this.ollamaParser   = null;
            this.cloudLlmParser = new CloudLlmPromptParser();
            this.parser         = this.cloudLlmParser;
            LOG.info("Prompt parser: cloud LLM at " + this.cloudLlmParser.getBaseUrl()
                    + " model=" + this.cloudLlmParser.getModel()
                    + " profile=" + runtimeProfile(env)
                    + " (fallback: deterministic)");
        } else if (parserMode == PromptParserMode.OLLAMA) {
            // Local Ollama — preferred when APP_ENV=local and OLLAMA_URL is set
            this.ollamaParser   = new OllamaPromptParser();
            this.cloudLlmParser = null;
            this.parser         = this.ollamaParser;
            LOG.info("Prompt parser: Ollama at " + this.ollamaParser.getBaseUrl()
                    + " model=" + this.ollamaParser.getModel()
                    + " profile=" + runtimeProfile(env)
                    + " (fallback: deterministic)");
        } else {
            // Fully deterministic — no LLM required
            this.ollamaParser   = null;
            this.cloudLlmParser = null;
            this.parser         = new NaturalLanguagePromptParser();
            LOG.info("Prompt parser: deterministic (set APP_ENV=local with OLLAMA_URL for local Ollama, or APP_ENV=production with LLM_API_KEY for Groq/cloud)");
        }

        registerContexts();
    }

    static PromptParserMode selectPromptParser(Map<String, String> env) {
        boolean hasCloud  = hasText(env.get(CloudLlmPromptParser.ENV_LLM_API_KEY));
        boolean hasOllama = hasText(env.get(OllamaPromptParser.ENV_OLLAMA_URL));
        String profile    = runtimeProfile(env);

        if (isLocalProfile(profile)) {
            if (hasOllama) return PromptParserMode.OLLAMA;
            if (hasCloud)  return PromptParserMode.CLOUD;
            return PromptParserMode.DETERMINISTIC;
        }
        if (isProductionProfile(profile)) {
            if (hasCloud)  return PromptParserMode.CLOUD;
            if (hasOllama) return PromptParserMode.OLLAMA;
            return PromptParserMode.DETERMINISTIC;
        }
        if (hasCloud)  return PromptParserMode.CLOUD;
        if (hasOllama) return PromptParserMode.OLLAMA;
        return PromptParserMode.DETERMINISTIC;
    }

    static String runtimeProfile(Map<String, String> env) {
        String explicit = trimToNull(env.get(ENV_RUNTIME_PROFILE));
        if (explicit != null) {
            return explicit.toLowerCase(Locale.ROOT);
        }
        String appEnv = trimToNull(env.get(ENV_APP_ENV));
        if (appEnv != null) {
            return appEnv.toLowerCase(Locale.ROOT);
        }
        return "auto";
    }

    private static boolean isLocalProfile(String profile) {
        return "local".equals(profile) || "dev".equals(profile) || "development".equals(profile);
    }

    private static boolean isProductionProfile(String profile) {
        return "prod".equals(profile) || "production".equals(profile);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    enum PromptParserMode {
        OLLAMA,
        CLOUD,
        DETERMINISTIC
    }

    public void start()  { server.start(); }

    @Override
    public void close()  { server.stop(0); }

    private void registerContexts() {
        server.createContext("/generator/spec", new SpecHandler());
        server.createContext("/generator/confidence", new ConfidenceHandler());
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
                SpecDiagnosticsValidator.ValidationReport report = diagnosticsValidator.validate(spec, config);
                SpecResponse response = new SpecResponse(spec, report.warnings(), report.errors(), report.fixSuggestions());
                byte[] payload = mapper.writeValueAsBytes(response);
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
                // Fallback: try parsing as a prompt instead of a spec
                try {
                    var promptJson = mapper.readTree(requestBody);
                    if (promptJson.has("prompt") && !promptJson.has("projectName")) {
                        String prompt = promptJson.get("prompt").asText();
                        LOG.info("Detected prompt-only payload; auto-parsing: " + (prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt));
                        spec = parser.parse(prompt, config);
                    } else {
                        throw e;
                    }
                } catch (Exception fallbackErr) {
                    respond(exchange, 400, jsonError("INVALID_SPEC", "invalid spec payload: " + sanitize(e.getMessage())), "application/json");
                    return;
                }
            }
            try {
                ConfidenceResponse confidence = evaluateConfidence(spec);
                if (confidenceFailPolicyEnabled && "fail".equals(confidence.confidenceStatus())) {
                    respond(exchange, 400, jsonError("CONFIDENCE_FAIL", confidence.reason()), "application/json");
                    return;
                }
                byte[] zip = codeGenerator.generateZip(spec, configWithSecurityHint(spec, config));
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

    // ── POST /generator/confidence ────────────────────────────────────────────

    private final class ConfidenceHandler implements HttpHandler {
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

            try {
                ApiSpecification spec = mapper.readValue(requestBody, ApiSpecification.class);
                ConfidenceResponse confidence = evaluateConfidence(spec);
                respond(exchange, 200, mapper.writeValueAsBytes(confidence), "application/json");
            } catch (JsonProcessingException e) {
                respond(exchange, 400, jsonError("INVALID_SPEC", "invalid spec payload: " + sanitize(e.getMessage())), "application/json");
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
                      "confidenceFailPolicyEnabled": %s,
                      "endpoints": [
                        {"method": "GET",  "path": "/",                "description": "Web UI"},
                        {"method": "GET",  "path": "/about",           "description": "Project information"},
                        {"method": "GET",  "path": "/health",          "description": "Health check"},
                        {"method": "POST", "path": "/generator/spec",  "description": "Parse a natural-language prompt into an API specification"},
                        {"method": "POST", "path": "/generator/confidence", "description": "Evaluate likely compile readiness for an API specification"},
                        {"method": "POST", "path": "/generator/code",  "description": "Generate a runnable Spring Boot ZIP from an API specification"}
                      ],
                      "repository": "https://github.com/rrezartprebreza/rest-api-generator"
                    }""".formatted(APP_VERSION, parserMode, llmConfigured, llmAvailable, confidenceFailPolicyEnabled);
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

    /**
     * Returns a copy of the given config with the security plugin enabled when
     * the parsed spec carries a non-"none" securityHint (e.g. "jwt", "oauth2", "basic").
     */
    private static GenerationConfig configWithSecurityHint(ApiSpecification spec, GenerationConfig base) {
        String hint = spec.securityHint;
        if (hint == null || hint.isBlank() || "none".equalsIgnoreCase(hint)) return base;
        if (base.standards().security().enabled()) return base; // already configured
        var standards = base.standards();
        var newSecurity = new GenerationConfig.SecurityConfig(true, hint);
        var newStandards = new GenerationConfig.StandardsConfig(
                standards.naming(), standards.layering(), standards.database(),
                standards.validation(), standards.documentation(), standards.testing(),
                newSecurity, standards.errorHandling(), standards.responseFormat()
        );
        // Enable the security-generator plugin
        var plugins = base.plugins();
        java.util.List<String> enabled = new java.util.ArrayList<>(plugins.enabled());
        if (!enabled.contains("security-generator")) enabled.add("security-generator");
        java.util.List<String> disabled = new java.util.ArrayList<>(plugins.disabled());
        disabled.remove("security-generator");
        var newPlugins = new GenerationConfig.PluginsConfig(
                enabled, disabled, plugins.externalDirectories(), plugins.externalClassNames()
        );
        return new GenerationConfig(base.project(), newStandards, base.features(), newPlugins);
    }

    private ConfidenceResponse evaluateConfidence(ApiSpecification spec) {
        if (spec == null) {
            return new ConfidenceResponse("fail", "Specification payload is empty", confidenceFailPolicyEnabled, 0, 1);
        }

        try {
            specValidator.validate(spec);
        } catch (IllegalArgumentException e) {
            return new ConfidenceResponse("fail", sanitize(e.getMessage()), confidenceFailPolicyEnabled, 0, 1);
        }

        SpecDiagnosticsValidator.ValidationReport report = diagnosticsValidator.validate(spec, config);
        int warningCount = report.warnings().size();
        int errorCount = report.errors().size();

        if (errorCount > 0) {
            String reason = report.errors().get(0).message();
            return new ConfidenceResponse("fail", sanitize(reason), confidenceFailPolicyEnabled, warningCount, errorCount);
        }
        if (warningCount > 0) {
            String reason = report.warnings().get(0).message();
            return new ConfidenceResponse("warn", sanitize(reason), confidenceFailPolicyEnabled, warningCount, 0);
        }
        return new ConfidenceResponse("pass", "No blocking compile-readiness issues detected", confidenceFailPolicyEnabled, 0, 0);
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
    record SpecResponse(ApiSpecification spec, java.util.List<SpecDiagnosticsValidator.ValidationIssue> warnings,
                        java.util.List<SpecDiagnosticsValidator.ValidationIssue> errors,
                        java.util.List<SpecDiagnosticsValidator.FixSuggestion> fixSuggestions) {}
    record ConfidenceResponse(String confidenceStatus, String reason, boolean failPolicyEnabled,
                              int warningCount, int errorCount) {}
}
