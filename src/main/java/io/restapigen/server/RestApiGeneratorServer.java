package io.restapigen.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.restapigen.codegen.CodeGenerator;
import io.restapigen.core.config.GenerationConfig;
import io.restapigen.core.parser.NaturalLanguagePromptParser;
import io.restapigen.core.parser.PromptParser;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.generator.parser.SpecInputExtractor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class RestApiGeneratorServer implements AutoCloseable {
    private static final int DEFAULT_THREAD_POOL = 4;
    private final HttpServer server;
    private final PromptParser parser;
    private final CodeGenerator codeGenerator;
    private final GenerationConfig config;
    private final ObjectMapper mapper;

    public RestApiGeneratorServer(int port) throws IOException {
        this(port, GenerationConfig.defaults());
    }

    public RestApiGeneratorServer(int port, GenerationConfig config) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(DEFAULT_THREAD_POOL));
        this.parser = new NaturalLanguagePromptParser();
        this.codeGenerator = new CodeGenerator();
        this.config = config == null ? GenerationConfig.defaults() : config;
        this.mapper = new ObjectMapper().findAndRegisterModules();
        registerContexts();
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void registerContexts() {
        server.createContext("/generator/spec", new SpecHandler());
        server.createContext("/generator/code", new CodeHandler());
        server.createContext("/about", new AboutHandler());
    }

    private final class SpecHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "Only POST is supported", "text/plain");
                return;
            }
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            String prompt = parsePrompt(requestBody);
            if (prompt.isBlank()) {
                respond(exchange, 400, "prompt is missing", "text/plain");
                return;
            }
            String userRequest = SpecInputExtractor.extractUserRequestOrWholeInput(prompt);
            ApiSpecification spec = parser.parse(userRequest, config);
            byte[] payload = mapper.writeValueAsBytes(spec);
            respond(exchange, 200, payload, "application/json");
        }

        private String parsePrompt(String requestBody) {
            if (requestBody.isEmpty()) {
                return "";
            }
            try {
                SpecRequest request = mapper.readValue(requestBody, SpecRequest.class);
                if (request.prompt != null && !request.prompt.isBlank()) {
                    return request.prompt;
                }
            } catch (JsonProcessingException ignored) {
            }
            return requestBody;
        }
    }

    private final class CodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "Only POST is supported", "text/plain");
                return;
            }
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (requestBody.isEmpty()) {
                respond(exchange, 400, "spec is missing", "text/plain");
                return;
            }
            ApiSpecification spec;
            try {
                spec = mapper.readValue(requestBody, ApiSpecification.class);
            } catch (JsonProcessingException e) {
                respond(exchange, 400, "invalid spec payload", "text/plain");
                return;
            }
            byte[] zip = codeGenerator.generateZip(spec, config);
            respond(exchange, 200, zip, "application/zip");
        }
    }

    private final class AboutHandler implements HttpHandler {
        private static final String ABOUT_JSON = """
                {
                  "name": "REST API Generator",
                  "version": "1.0",
                  "description": "Generate production-ready Spring Boot REST APIs from plain English in seconds.",
                  "endpoints": [
                    {"method": "GET",  "path": "/about",          "description": "Project information"},
                    {"method": "POST", "path": "/generator/spec", "description": "Parse a natural-language prompt into an API specification"},
                    {"method": "POST", "path": "/generator/code", "description": "Generate a runnable Spring Boot ZIP from an API specification"}
                  ],
                  "repository": "https://github.com/rrezartprebreza/rest-api-generator"
                }""";

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "Only GET is supported", "text/plain");
                return;
            }
            respond(exchange, 200, ABOUT_JSON, "application/json");
        }
    }

    private void respond(HttpExchange exchange, int status, byte[] payload, String contentType) throws IOException {
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
