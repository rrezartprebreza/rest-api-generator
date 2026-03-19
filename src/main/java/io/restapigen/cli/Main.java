package io.restapigen.cli;

import io.restapigen.codegen.CodeGenerator;
import io.restapigen.core.config.ConfigLoader;
import io.restapigen.core.config.GenerationConfig;
import io.restapigen.core.parser.NaturalLanguagePromptParser;
import io.restapigen.core.parser.PromptParser;
import io.restapigen.core.plugin.PluginLoader;
import io.restapigen.core.template.TemplatePack;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.generator.parser.SpecInputExtractor;
import io.restapigen.output.openapi.OpenApiWriter;
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
    private static final Path DEFAULT_CONFIG = Path.of(".rest-api-generator.yml");
    private static final Path DEFAULT_OUTPUT_ZIP = Path.of("scaffold.zip");

    public static void main(String[] args) throws Exception {
        CliArgs cliArgs = CliArgs.parse(args);
        if (cliArgs.showHelp) {
            CliArgs.printHelpAndExit(0);
            return;
        }

        ConfigLoader configLoader = new ConfigLoader();
        Path configPath = cliArgs.configPath == null ? DEFAULT_CONFIG : cliArgs.configPath;
        if (cliArgs.mode == CliMode.INIT_CONFIG) {
            configLoader.writeDefault(configPath, cliArgs.templateName);
            System.out.println("Wrote default config to " + configPath);
            if (cliArgs.templateName != null && !cliArgs.templateName.isBlank()) {
                System.out.println("Template selected: " + cliArgs.templateName);
            }
            return;
        }

        if (cliArgs.mode == CliMode.TEMPLATE_LIST) {
            TemplatePack.availablePacks().forEach(System.out::println);
            return;
        }

        GenerationConfig config = configLoader.load(configPath);
        if (cliArgs.templateName != null && !cliArgs.templateName.isBlank()) {
            config = config.withTemplatePack(cliArgs.templateName);
        }
        if (cliArgs.mode == CliMode.PLUGIN_LIST) {
            final GenerationConfig activeConfig = config;
            PluginLoader loader = new PluginLoader();
            loader.load(activeConfig).forEach(plugin -> {
                String status = activeConfig.plugins().isEnabled(plugin.getName()) ? "enabled" : "disabled";
                System.out.println(plugin.getName() + "@" + plugin.getVersion() + " [" + status + "]");
            });
            return;
        }
        if (cliArgs.mode == CliMode.VALIDATE_CONFIG) {
            System.out.println("Configuration is valid: " + configPath);
            return;
        }

        if (cliArgs.mode == CliMode.SERVE) {
            try (RestApiGeneratorServer server = new RestApiGeneratorServer(cliArgs.port, config)) {
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

        PromptParser parser = new NaturalLanguagePromptParser();
        ApiSpecification spec = parser.parse(userRequest, config);
        if (cliArgs.mode == CliMode.OPENAPI_EXPORT) {
            System.out.print(OpenApiWriter.write(spec));
            return;
        }
        if (cliArgs.mode == CliMode.GENERATE_ZIP) {
            CodeGenerator codeGenerator = new CodeGenerator();
            byte[] zip = codeGenerator.generateZip(spec, config);
            Path output = cliArgs.outputZipPath == null ? DEFAULT_OUTPUT_ZIP : cliArgs.outputZipPath;
            Files.write(output, zip);
            System.out.println("Generated ZIP: " + output.toAbsolutePath());
            return;
        }
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

    enum CliMode {
        GENERATE,
        GENERATE_ZIP,
        SERVE,
        INIT_CONFIG,
        VALIDATE_CONFIG,
        TEMPLATE_LIST,
        PLUGIN_LIST,
        OPENAPI_EXPORT
    }

    static final class CliArgs {
        final CliMode mode;
        final Path inputFile;
        final String userRequest;
        final boolean pretty;
        final int port;
        final Path configPath;
        final String templateName;
        final Path outputZipPath;
        final boolean showHelp;

        private CliArgs(
                CliMode mode,
                Path inputFile,
                String userRequest,
                boolean pretty,
                int port,
                Path configPath,
                String templateName,
                Path outputZipPath,
                boolean showHelp
        ) {
            this.mode = mode;
            this.inputFile = inputFile;
            this.userRequest = userRequest;
            this.pretty = pretty;
            this.port = port;
            this.configPath = configPath;
            this.templateName = templateName;
            this.outputZipPath = outputZipPath;
            this.showHelp = showHelp;
        }

        static CliArgs parse(String[] args) {
            if (args.length > 0 && !args[0].startsWith("--")) {
                return parseCommandStyle(args);
            }
            return parseFlagStyle(args);
        }

        private static CliArgs parseCommandStyle(String[] args) {
            String command = args[0];
            List<String> argList = List.of(args);
            return switch (command) {
                case "init" -> parseForMode(CliMode.INIT_CONFIG, argList, 1);
                case "generate" -> parseForMode(CliMode.GENERATE, argList, 1);
                case "generate-zip" -> parseForMode(CliMode.GENERATE_ZIP, argList, 1);
                case "serve" -> parseForMode(CliMode.SERVE, argList, 1);
                case "validate" -> parseForMode(CliMode.VALIDATE_CONFIG, argList, 1);
                case "openapi" -> parseForMode(CliMode.OPENAPI_EXPORT, argList, 1);
                case "templates" -> {
                    if (argList.size() > 1 && "list".equalsIgnoreCase(argList.get(1))) {
                        yield new CliArgs(CliMode.TEMPLATE_LIST, null, null, false, DEFAULT_PORT, DEFAULT_CONFIG, null, null, false);
                    }
                    System.err.println("Unknown templates command. Use: templates list");
                    printHelpAndExit(2);
                    yield new CliArgs(CliMode.TEMPLATE_LIST, null, null, false, DEFAULT_PORT, DEFAULT_CONFIG, null, null, false);
                }
                case "plugins" -> {
                    if (argList.size() > 1 && "list".equalsIgnoreCase(argList.get(1))) {
                        yield parseForMode(CliMode.PLUGIN_LIST, argList, 2);
                    }
                    System.err.println("Unknown plugins command. Use: plugins list");
                    printHelpAndExit(2);
                    yield new CliArgs(CliMode.PLUGIN_LIST, null, null, false, DEFAULT_PORT, DEFAULT_CONFIG, null, null, false);
                }
                case "--help", "-h", "help" -> new CliArgs(CliMode.GENERATE, null, null, false, DEFAULT_PORT, DEFAULT_CONFIG, null, null, true);
                default -> {
                    System.err.println("Unknown command: " + command);
                    printHelpAndExit(2);
                    yield new CliArgs(CliMode.GENERATE, null, null, false, DEFAULT_PORT, DEFAULT_CONFIG, null, null, false);
                }
            };
        }

        private static CliArgs parseFlagStyle(String[] args) {
            Path inputFile = null;
            String userRequest = null;
            boolean pretty = false;
            CliMode mode = CliMode.GENERATE;
            int port = DEFAULT_PORT;
            Path configPath = DEFAULT_CONFIG;
            String templateName = null;
            Path outputZipPath = null;
            boolean showHelp = false;

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
                    case "--generate-zip" -> mode = CliMode.GENERATE_ZIP;
                    case "--serve" -> mode = CliMode.SERVE;
                    case "--port" -> {
                        ensureHasValue(argList, i, "--port");
                        port = parsePort(argList.get(++i));
                    }
                    case "--config" -> {
                        ensureHasValue(argList, i, "--config");
                        configPath = Path.of(argList.get(++i));
                    }
                    case "--init-config" -> mode = CliMode.INIT_CONFIG;
                    case "--validate-config" -> mode = CliMode.VALIDATE_CONFIG;
                    case "--template" -> {
                        ensureHasValue(argList, i, "--template");
                        templateName = argList.get(++i);
                    }
                    case "--out" -> {
                        ensureHasValue(argList, i, "--out");
                        outputZipPath = Path.of(argList.get(++i));
                    }
                    case "--help", "-h" -> {
                        showHelp = true;
                    }
                    default -> {
                        System.err.println("Unknown argument: " + arg);
                        printHelpAndExit(2);
                        return new CliArgs(CliMode.GENERATE, null, null, false, DEFAULT_PORT, DEFAULT_CONFIG, null, null, false);
                    }
                }
            }

            if ((mode == CliMode.GENERATE || mode == CliMode.OPENAPI_EXPORT || mode == CliMode.GENERATE_ZIP)
                    && (userRequest == null || userRequest.isBlank()) && inputFile == null && System.console() != null) {
                System.err.println("No input provided. Use --user-request, --input, or pipe stdin.");
                printHelpAndExit(2);
            }

            return new CliArgs(mode, inputFile, userRequest, pretty, port, configPath, templateName, outputZipPath, showHelp);
        }

        private static CliArgs parseForMode(CliMode mode, List<String> argList, int startIndex) {
            Path inputFile = null;
            String userRequest = null;
            boolean pretty = false;
            int port = DEFAULT_PORT;
            Path configPath = DEFAULT_CONFIG;
            String templateName = null;
            Path outputZipPath = null;
            boolean showHelp = false;

            for (int i = startIndex; i < argList.size(); i++) {
                String arg = argList.get(i);
                switch (arg) {
                    case "--prompt", "--user-request" -> {
                        ensureHasValue(argList, i, arg);
                        userRequest = argList.get(++i);
                    }
                    case "--file", "--input" -> {
                        ensureHasValue(argList, i, arg);
                        inputFile = Path.of(argList.get(++i));
                    }
                    case "--pretty" -> pretty = true;
                    case "--port" -> {
                        ensureHasValue(argList, i, "--port");
                        port = parsePort(argList.get(++i));
                    }
                    case "--config" -> {
                        ensureHasValue(argList, i, "--config");
                        configPath = Path.of(argList.get(++i));
                    }
                    case "--template" -> {
                        ensureHasValue(argList, i, "--template");
                        templateName = argList.get(++i);
                    }
                    case "--out" -> {
                        ensureHasValue(argList, i, "--out");
                        outputZipPath = Path.of(argList.get(++i));
                    }
                    case "--help", "-h" -> showHelp = true;
                    default -> {
                        System.err.println("Unknown argument: " + arg);
                        printHelpAndExit(2);
                        return new CliArgs(mode, null, null, false, DEFAULT_PORT, DEFAULT_CONFIG, null, null, false);
                    }
                }
            }

            if ((mode == CliMode.GENERATE || mode == CliMode.OPENAPI_EXPORT || mode == CliMode.GENERATE_ZIP)
                    && (userRequest == null || userRequest.isBlank())
                    && inputFile == null
                    && System.console() != null) {
                System.err.println("No input provided. Use --prompt/--user-request, --file/--input, or pipe stdin.");
                printHelpAndExit(2);
            }
            return new CliArgs(mode, inputFile, userRequest, pretty, port, configPath, templateName, outputZipPath, showHelp);
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

        static void printHelpAndExit(int code) {
            System.err.println("""
                    Usage:
                      ./gradlew run --args="generate --prompt \\"Create a CRUD API for Book with title, authorName, publishedDate\\""
                      ./gradlew run --args="generate --file path/to/prompt.txt --pretty"
                      ./gradlew run --args="generate-zip --file path/to/prompt.txt --out scaffold.zip"
                      ./gradlew run --args="serve --port 8080"
                      ./gradlew run --args="init --config .rest-api-generator.yml --template spring-boot-3-standard"
                      ./gradlew run --args="validate --config .rest-api-generator.yml"
                      ./gradlew run --args="openapi --prompt \\"Create API for User with email\\""
                      ./gradlew run --args="templates list"
                      ./gradlew run --args="plugins list --config .rest-api-generator.yml"
                      cat prompt.txt | ./gradlew run

                    Options:
                      --prompt <text>         Natural-language request (alias: --user-request)
                      --file <path>           File containing either a user request or a full prompt with a USER REQUEST section (alias: --input)
                      --pretty                Pretty-print JSON
                      --port <number>         Port for serve mode (default 8080)
                      --config <path>         Path to .rest-api-generator.yml (default ./.rest-api-generator.yml)
                      --template <name>       Template pack name for init/generate metadata
                      --out <path>            Output ZIP path for generate-zip (default ./scaffold.zip)

                    Legacy flags (still supported):
                      --serve                 Equivalent to serve
                      --init-config           Equivalent to init
                      --validate-config       Equivalent to validate
                      --generate-zip          Equivalent to generate-zip
                      --help, -h              Show help
                    """);
            System.exit(code);
        }
    }
}
