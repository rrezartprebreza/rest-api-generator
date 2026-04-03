package io.restapigen.cli;

import io.restapigen.codegen.CodeGenerator;
import io.restapigen.core.config.ConfigLoader;
import io.restapigen.core.config.GenerationConfig;
import io.restapigen.core.parser.NaturalLanguagePromptParser;
import io.restapigen.core.plugin.PluginLoader;
import io.restapigen.core.template.TemplatePack;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.generator.parser.SpecInputExtractor;
import io.restapigen.output.openapi.OpenApiWriter;
import io.restapigen.output.json.JsonSpecificationWriter;
import io.restapigen.server.RestApiGeneratorServer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

@Command(
        name = "rest-api-gen",
        description = "Generate production-ready Spring Boot REST APIs from natural language",
        mixinStandardHelpOptions = true,
        subcommands = {
                Main.GenerateCommand.class,
                Main.GenerateZipCommand.class,
                Main.ServeCommand.class,
                Main.InitCommand.class,
                Main.ValidateCommand.class,
                Main.OpenApiCommand.class,
                Main.TemplatesCommand.class,
                Main.PluginsCommand.class
        })
public final class Main implements Callable<Integer> {

    // ── Legacy top-level flags kept for backward compatibility ────────────────

    @Option(names = "--generate-zip", hidden = true)
    boolean legacyGenerateZip;

    @Option(names = "--serve", hidden = true)
    boolean legacyServe;

    @Option(names = "--init-config", hidden = true)
    boolean legacyInitConfig;

    @Option(names = "--validate-config", hidden = true)
    boolean legacyValidateConfig;

    @Option(names = {"--prompt", "--user-request"}, hidden = true)
    String userRequest;

    @Option(names = {"--file", "--input"}, hidden = true)
    Path inputFile;

    @Option(names = "--pretty", hidden = true)
    boolean pretty;

    @Option(names = "--port", hidden = true, defaultValue = "8080")
    int port;

    @Option(names = "--config", hidden = true, defaultValue = ".rest-api-generator.yml")
    Path configPath;

    @Option(names = "--template", hidden = true)
    String templateName;

    @Option(names = "--out", hidden = true)
    Path outputZipPath;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        ConfigLoader configLoader = new ConfigLoader();

        if (legacyInitConfig) {
            configLoader.writeDefault(configPath, templateName);
            err.println("Wrote default config to " + configPath);
            if (templateName != null && !templateName.isBlank()) {
                err.println("Template selected: " + templateName);
            }
            return 0;
        }

        if (legacyValidateConfig) {
            configLoader.load(configPath);
            err.println("Configuration is valid: " + configPath);
            return 0;
        }

        GenerationConfig config = configLoader.load(configPath);
        if (templateName != null && !templateName.isBlank()) {
            config = config.withTemplatePack(templateName);
        }

        if (legacyServe) {
            try (RestApiGeneratorServer server = new RestApiGeneratorServer(port, config)) {
                server.start();
                err.printf("Listening on http://localhost:%d%n", port);
                new CountDownLatch(1).await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return 0;
        }

        if (legacyGenerateZip || userRequest != null || inputFile != null) {
            String rawInput = InputMixin.readInput(userRequest, inputFile);
            String request = SpecInputExtractor.extractUserRequestOrWholeInput(rawInput);
            ApiSpecification apiSpec = new NaturalLanguagePromptParser().parse(request, config);

            if (legacyGenerateZip) {
                byte[] zip = new CodeGenerator().generateZip(apiSpec, config);
                Path output = outputZipPath != null ? outputZipPath : Path.of("scaffold.zip");
                Files.write(output, zip);
                err.println("Generated ZIP: " + output.toAbsolutePath());
                return 0;
            }

            out.print(JsonSpecificationWriter.writeApiSpecification(apiSpec, pretty));
            out.flush();
            return 0;
        }

        // No subcommand, no legacy flags, no input – show help
        spec.commandLine().usage(err);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                    commandLine.getErr().println("Error: " + ex.getMessage());
                    return 1;
                })
                .execute(args);
        System.exit(exitCode);
    }

    // ── Shared mixins ─────────────────────────────────────────────────────────

    static final class ConfigMixin {
        @Option(names = {"--config", "-c"},
                description = "Path to .rest-api-generator.yml (default: ${DEFAULT-VALUE})",
                defaultValue = ".rest-api-generator.yml")
        Path configPath;

        @Option(names = "--template",
                description = "Template pack name")
        String templateName;

        GenerationConfig load() throws IOException {
            GenerationConfig cfg = new ConfigLoader().load(configPath);
            if (templateName != null && !templateName.isBlank()) {
                cfg = cfg.withTemplatePack(templateName);
            }
            return cfg;
        }
    }

    static final class InputMixin {
        @Option(names = {"--prompt", "--user-request"},
                description = "Natural-language prompt")
        String prompt;

        @Option(names = {"--file", "--input"},
                description = "File containing the prompt")
        Path inputFile;

        String read() throws IOException {
            return readInput(prompt, inputFile);
        }

        static String readInput(String prompt, Path file) throws IOException {
            if (prompt != null && !prompt.isBlank()) return prompt;
            if (file != null) return Files.readString(file, StandardCharsets.UTF_8);
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ── Subcommands ───────────────────────────────────────────────────────────

    @Command(name = "generate",
            description = "Parse a prompt and output the API specification as JSON",
            mixinStandardHelpOptions = true)
    static final class GenerateCommand implements Callable<Integer> {
        @Mixin ConfigMixin config;
        @Mixin InputMixin input;

        @Option(names = "--pretty", description = "Pretty-print JSON output")
        boolean pretty;

        @Spec CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            GenerationConfig cfg = config.load();
            ApiSpecification apiSpec = new NaturalLanguagePromptParser()
                    .parse(SpecInputExtractor.extractUserRequestOrWholeInput(input.read()), cfg);
            PrintWriter out = spec.commandLine().getOut();
            out.print(JsonSpecificationWriter.writeApiSpecification(apiSpec, pretty));
            out.flush();
            return 0;
        }
    }

    @Command(name = "generate-zip",
            description = "Generate a runnable ZIP scaffold from a prompt",
            mixinStandardHelpOptions = true)
    static final class GenerateZipCommand implements Callable<Integer> {
        @Mixin ConfigMixin config;
        @Mixin InputMixin input;

        @Option(names = {"--out", "-o"},
                description = "Output ZIP path (default: ${DEFAULT-VALUE})",
                defaultValue = "scaffold.zip")
        Path outputZip;

        @Spec CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            GenerationConfig cfg = config.load();
            ApiSpecification apiSpec = new NaturalLanguagePromptParser()
                    .parse(SpecInputExtractor.extractUserRequestOrWholeInput(input.read()), cfg);
            byte[] zip = new CodeGenerator().generateZip(apiSpec, cfg);
            Files.write(outputZip, zip);
            spec.commandLine().getErr().println("Generated ZIP: " + outputZip.toAbsolutePath());
            return 0;
        }
    }

    @Command(name = "serve",
            description = "Start the HTTP API server",
            mixinStandardHelpOptions = true)
    static final class ServeCommand implements Callable<Integer> {
        @Mixin ConfigMixin config;

        @Option(names = {"--port", "-p"},
                description = "Port to listen on (default: ${DEFAULT-VALUE})",
                defaultValue = "8080")
        int port;

        @Spec CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            GenerationConfig cfg = config.load();
            try (RestApiGeneratorServer server = new RestApiGeneratorServer(port, cfg)) {
                server.start();
                spec.commandLine().getErr().printf("Listening on http://localhost:%d%n", port);
                new CountDownLatch(1).await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return 0;
        }
    }

    @Command(name = "init",
            description = "Write a default .rest-api-generator.yml config file",
            mixinStandardHelpOptions = true)
    static final class InitCommand implements Callable<Integer> {
        @Option(names = {"--config", "-c"},
                description = "Config file path (default: ${DEFAULT-VALUE})",
                defaultValue = ".rest-api-generator.yml")
        Path configPath;

        @Option(names = "--template",
                description = "Template pack name")
        String templateName;

        @Spec CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            new ConfigLoader().writeDefault(configPath, templateName);
            PrintWriter err = spec.commandLine().getErr();
            err.println("Wrote default config to " + configPath);
            if (templateName != null && !templateName.isBlank()) {
                err.println("Template selected: " + templateName);
            }
            return 0;
        }
    }

    @Command(name = "validate",
            description = "Validate a .rest-api-generator.yml config file",
            mixinStandardHelpOptions = true)
    static final class ValidateCommand implements Callable<Integer> {
        @Option(names = {"--config", "-c"},
                description = "Config file path (default: ${DEFAULT-VALUE})",
                defaultValue = ".rest-api-generator.yml")
        Path configPath;

        @Spec CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            new ConfigLoader().load(configPath);
            spec.commandLine().getErr().println("Configuration is valid: " + configPath);
            return 0;
        }
    }

    @Command(name = "openapi",
            description = "Export an OpenAPI specification from a prompt",
            mixinStandardHelpOptions = true)
    static final class OpenApiCommand implements Callable<Integer> {
        @Mixin ConfigMixin config;
        @Mixin InputMixin input;

        @Spec CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            GenerationConfig cfg = config.load();
            ApiSpecification apiSpec = new NaturalLanguagePromptParser()
                    .parse(SpecInputExtractor.extractUserRequestOrWholeInput(input.read()), cfg);
            PrintWriter out = spec.commandLine().getOut();
            out.print(OpenApiWriter.write(apiSpec));
            out.flush();
            return 0;
        }
    }

    @Command(name = "templates",
            description = "Manage template packs",
            subcommands = {TemplatesCommand.ListCommand.class},
            mixinStandardHelpOptions = true)
    static final class TemplatesCommand implements Runnable {
        @Spec CommandSpec spec;

        @Override
        public void run() {
            spec.commandLine().usage(spec.commandLine().getErr());
        }

        @Command(name = "list",
                description = "List available template packs",
                mixinStandardHelpOptions = true)
        static final class ListCommand implements Callable<Integer> {
            @Spec CommandSpec spec;

            @Override
            public Integer call() {
                PrintWriter out = spec.commandLine().getOut();
                TemplatePack.availablePacks().forEach(out::println);
                out.flush();
                return 0;
            }
        }
    }

    @Command(name = "plugins",
            description = "Manage plugins",
            subcommands = {PluginsCommand.ListCommand.class},
            mixinStandardHelpOptions = true)
    static final class PluginsCommand implements Runnable {
        @Spec CommandSpec spec;

        @Override
        public void run() {
            spec.commandLine().usage(spec.commandLine().getErr());
        }

        @Command(name = "list",
                description = "List available plugins",
                mixinStandardHelpOptions = true)
        static final class ListCommand implements Callable<Integer> {
            @Mixin ConfigMixin config;
            @Spec CommandSpec spec;

            @Override
            public Integer call() throws Exception {
                GenerationConfig cfg = config.load();
                PrintWriter out = spec.commandLine().getOut();
                new PluginLoader().load(cfg).forEach(plugin -> {
                    String status = cfg.plugins().isEnabled(plugin.getName()) ? "enabled" : "disabled";
                    out.println(plugin.getName() + "@" + plugin.getVersion() + " [" + status + "]");
                });
                out.flush();
                return 0;
            }
        }
    }
}
