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
