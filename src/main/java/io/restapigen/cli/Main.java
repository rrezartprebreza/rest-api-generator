package io.restapigen.cli;

import io.restapigen.domain.ApiSpecification;
import io.restapigen.generator.NaturalLanguageSpecGenerator;
import io.restapigen.generator.parser.SpecInputExtractor;
import io.restapigen.output.json.JsonSpecificationWriter;
import io.restapigen.server.RestApiGeneratorServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class Main {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        CliArgs cliArgs = CliArgs.parse(args);
        if (cliArgs.serve) {
            try (RestApiGeneratorServer server = new RestApiGeneratorServer(cliArgs.port)) {
                server.start();
                System.out.printf("Listening on http://localhost:%d%n", cliArgs.port);
                new CountDownLatch(1).await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        String rawInput = readInput(cliArgs);
        String userRequest = SpecInputExtractor.extractUserRequestOrWholeInput(rawInput);

        ApiSpecification spec = new NaturalLanguageSpecGenerator().generate(userRequest);
        String json = JsonSpecificationWriter.writeApiSpecification(spec, cliArgs.pretty);
        System.out.print(json);
    }

    private static String readInput(CliArgs cliArgs) throws IOException {
        if (cliArgs.userRequest != null && !cliArgs.userRequest.isBlank()) {
            return cliArgs.userRequest;
        }
        if (cliArgs.inputFile != null) {
            return Files.readString(cliArgs.inputFile, StandardCharsets.UTF_8);
        }
        return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    }

    static final class CliArgs {
        final Path inputFile;
        final String userRequest;
        final boolean pretty;
        final boolean serve;
        final int port;

        private CliArgs(Path inputFile, String userRequest, boolean pretty, boolean serve, int port) {
            this.inputFile = inputFile;
            this.userRequest = userRequest;
            this.pretty = pretty;
            this.serve = serve;
            this.port = port;
        }

        static CliArgs parse(String[] args) {
            Path inputFile = null;
            String userRequest = null;
            boolean pretty = false;
            boolean serve = false;
            int port = DEFAULT_PORT;

            List<String> argList = List.of(args);
            for (int i = 0; i < argList.size(); i++) {
                String arg = argList.get(i);
                switch (arg) {
                    case "--input" -> {
                        ensureHasValue(argList, i, "--input");
                        inputFile = Path.of(argList.get(++i));
                    }
                    case "--user-request" -> {
                        ensureHasValue(argList, i, "--user-request");
                        userRequest = argList.get(++i);
                    }
                    case "--pretty" -> pretty = true;
                    case "--serve" -> serve = true;
                    case "--port" -> {
                        ensureHasValue(argList, i, "--port");
                        port = parsePort(argList.get(++i));
                    }
                    case "--help", "-h" -> {
                        printHelpAndExit(0);
                        return new CliArgs(null, null, false, false, DEFAULT_PORT);
                    }
                    default -> {
                        System.err.println("Unknown argument: " + arg);
                        printHelpAndExit(2);
                        return new CliArgs(null, null, false, false, DEFAULT_PORT);
                    }
                }
            }

            if (!serve && (userRequest == null || userRequest.isBlank()) && inputFile == null && System.console() != null) {
                System.err.println("No input provided. Use --user-request, --input, or pipe stdin.");
                printHelpAndExit(2);
            }

            return new CliArgs(inputFile, userRequest, pretty, serve, port);
        }

        private static void ensureHasValue(List<String> args, int index, String flag) {
            if (index + 1 >= args.size() || args.get(index + 1).startsWith("--")) {
                System.err.println("Missing value for " + flag);
                printHelpAndExit(2);
            }
        }

        private static int parsePort(String raw) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + raw);
                printHelpAndExit(2);
                return DEFAULT_PORT;
            }
        }

        private static void printHelpAndExit(int code) {
            System.err.println("""
                    Usage:
                      ./gradlew run --args="--user-request \\"Create a CRUD API for Book with title, authorName, publishedDate\\""
                      ./gradlew run --args="--input path/to/prompt.txt"
                      ./gradlew run --args="--serve --port 8080"
                      cat prompt.txt | ./gradlew run

                    Options:
                      --user-request <text>   Natural-language request (preferred)
                      --input <path>          File containing either a user request or a full prompt with a USER REQUEST section
                      --pretty                Pretty-print JSON
                      --serve                 Start an HTTP server that exposes /generator/spec and /generator/code
                      --port <number>         Port for the HTTP server when --serve is provided (default 8080)
                      --help, -h              Show help
                    """);
            System.exit(code);
        }
    }
}
