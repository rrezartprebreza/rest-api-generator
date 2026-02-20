package io.restapigen.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerationConfig(
        ProjectConfig project,
        StandardsConfig standards,
        FeaturesConfig features,
        PluginsConfig plugins
) {
    public GenerationConfig {
        project = project == null ? ProjectConfig.defaults() : project;
        standards = standards == null ? StandardsConfig.defaults() : standards;
        features = features == null ? FeaturesConfig.defaults() : features;
        plugins = plugins == null ? PluginsConfig.defaults() : plugins;
    }

    public static GenerationConfig defaults() {
        return new GenerationConfig(ProjectConfig.defaults(), StandardsConfig.defaults(), FeaturesConfig.defaults(), PluginsConfig.defaults());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectConfig(
            String name,
            String basePackage,
            String springBootVersion,
            String javaVersion,
            String templatePack
    ) {
        public ProjectConfig {
            name = normalize(name, "generated-api");
            basePackage = normalize(basePackage, "com.example.generated");
            springBootVersion = normalize(springBootVersion, "3.2.1");
            javaVersion = normalize(javaVersion, "17");
            templatePack = normalize(templatePack, "spring-boot-3-standard");
        }

        static ProjectConfig defaults() {
            return new ProjectConfig("generated-api", "com.example.generated", "3.2.1", "17", "spring-boot-3-standard");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandardsConfig(
            NamingConfig naming,
            LayeringConfig layering,
            DatabaseConfig database,
            ValidationConfig validation,
            DocumentationConfig documentation,
            TestingConfig testing,
            SecurityConfig security,
            ErrorHandlingConfig errorHandling,
            ResponseFormatConfig responseFormat
    ) {
        public StandardsConfig {
            naming = naming == null ? NamingConfig.defaults() : naming;
            layering = layering == null ? LayeringConfig.defaults() : layering;
            database = database == null ? DatabaseConfig.defaults() : database;
            validation = validation == null ? ValidationConfig.defaults() : validation;
            documentation = documentation == null ? DocumentationConfig.defaults() : documentation;
            testing = testing == null ? TestingConfig.defaults() : testing;
            security = security == null ? SecurityConfig.defaults() : security;
            errorHandling = errorHandling == null ? ErrorHandlingConfig.defaults() : errorHandling;
            responseFormat = responseFormat == null ? ResponseFormatConfig.defaults() : responseFormat;
        }

        static StandardsConfig defaults() {
            return new StandardsConfig(
                    NamingConfig.defaults(),
                    LayeringConfig.defaults(),
                    DatabaseConfig.defaults(),
                    ValidationConfig.defaults(),
                    DocumentationConfig.defaults(),
                    TestingConfig.defaults(),
                    SecurityConfig.defaults(),
                    ErrorHandlingConfig.defaults(),
                    ResponseFormatConfig.defaults()
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NamingConfig(String entitySuffix, String dtoSuffix, String repositorySuffix, String serviceSuffix, String controllerSuffix) {
        public NamingConfig {
            entitySuffix = normalize(entitySuffix, "");
            dtoSuffix = normalize(dtoSuffix, "DTO");
            repositorySuffix = normalize(repositorySuffix, "Repository");
            serviceSuffix = normalize(serviceSuffix, "Service");
            controllerSuffix = normalize(controllerSuffix, "Controller");
        }

        static NamingConfig defaults() {
            return new NamingConfig("", "DTO", "Repository", "Service", "Controller");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LayeringConfig(String architecture, boolean includeServiceLayer, boolean includeDtoMapper) {
        public LayeringConfig {
            architecture = normalize(architecture, "layered");
        }

        static LayeringConfig defaults() {
            return new LayeringConfig("layered", true, true);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DatabaseConfig(String type, String namingStrategy, boolean includeAuditing, String migrationTool) {
        public DatabaseConfig {
            type = normalize(type, "postgresql");
            namingStrategy = normalize(namingStrategy, "snake_case");
            migrationTool = normalize(migrationTool, "flyway");
        }

        static DatabaseConfig defaults() {
            return new DatabaseConfig("postgresql", "snake_case", true, "flyway");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValidationConfig(String library, boolean includeCustomValidators) {
        public ValidationConfig {
            library = normalize(library, "jakarta");
        }

        static ValidationConfig defaults() {
            return new ValidationConfig("jakarta", true);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentationConfig(String tool, boolean includeExamples) {
        public DocumentationConfig {
            tool = normalize(tool, "springdoc");
        }

        static DocumentationConfig defaults() {
            return new DocumentationConfig("springdoc", true);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TestingConfig(boolean includeUnitTests, boolean includeIntegrationTests, String testFramework, String mockingLibrary) {
        public TestingConfig {
            testFramework = normalize(testFramework, "junit5");
            mockingLibrary = normalize(mockingLibrary, "mockito");
        }

        static TestingConfig defaults() {
            return new TestingConfig(true, true, "junit5", "mockito");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecurityConfig(boolean enabled, String type) {
        public SecurityConfig {
            type = normalize(type, "none");
        }

        static SecurityConfig defaults() {
            return new SecurityConfig(false, "none");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorHandlingConfig(String strategy, boolean includeCustomExceptions, String responseFormat) {
        public ErrorHandlingConfig {
            strategy = normalize(strategy, "global-exception-handler");
            responseFormat = normalize(responseFormat, "rfc7807");
        }

        static ErrorHandlingConfig defaults() {
            return new ErrorHandlingConfig("global-exception-handler", true, "rfc7807");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseFormatConfig(boolean wrapResponses, String successWrapper, String errorWrapper, boolean includePagination) {
        public ResponseFormatConfig {
            successWrapper = normalize(successWrapper, "ApiResponse");
            errorWrapper = normalize(errorWrapper, "ErrorResponse");
        }

        static ResponseFormatConfig defaults() {
            return new ResponseFormatConfig(true, "ApiResponse", "ErrorResponse", true);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeaturesConfig(boolean auditing, boolean softDelete, boolean versioning, boolean caching) {
        static FeaturesConfig defaults() {
            return new FeaturesConfig(true, false, false, false);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PluginsConfig(
            List<String> enabled,
            List<String> disabled,
            List<String> externalDirectories,
            List<String> externalClassNames
    ) {
        public PluginsConfig {
            enabled = enabled == null ? List.of(
                    "project-readme-generator",
                    "entity-generator",
                    "dto-generator",
                    "repository-generator",
                    "service-generator",
                    "controller-generator",
                    "test-generator",
                    "migration-generator",
                    "documentation-generator"
            ) : List.copyOf(enabled);
            disabled = disabled == null ? List.of("security-generator") : List.copyOf(disabled);
            externalDirectories = externalDirectories == null ? List.of("plugins") : List.copyOf(externalDirectories);
            externalClassNames = externalClassNames == null ? List.of() : List.copyOf(externalClassNames);
        }

        static PluginsConfig defaults() {
            return new PluginsConfig(List.of(
                    "project-readme-generator",
                    "entity-generator",
                    "dto-generator",
                    "repository-generator",
                    "service-generator",
                    "controller-generator",
                    "test-generator",
                    "migration-generator",
                    "documentation-generator"
            ), List.of("security-generator"), List.of("plugins"), List.of());
        }

        public boolean isEnabled(String pluginName) {
            Objects.requireNonNull(pluginName, "pluginName");
            return enabled.contains(pluginName) && !disabled.contains(pluginName);
        }
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    public GenerationConfig withTemplatePack(String templatePack) {
        ProjectConfig updatedProject = new ProjectConfig(
                project.name(),
                project.basePackage(),
                project.springBootVersion(),
                project.javaVersion(),
                templatePack
        );
        return new GenerationConfig(updatedProject, standards, features, plugins);
    }
}
