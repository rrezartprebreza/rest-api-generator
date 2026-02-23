package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProjectScaffoldGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "project-scaffold-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage = context.config().project().basePackage();
        String basePackagePath = context.basePackagePath();
        String appClass = appClassName(specification.projectName);

        out.add(new GeneratedFile("settings.gradle", settingsGradle(specification.projectName)));
        out.add(new GeneratedFile("build.gradle", buildGradle(context)));
        out.add(new GeneratedFile(
                "src/main/java/" + basePackagePath + "/" + appClass + ".java",
                springBootApplication(basePackage, appClass)
        ));
        out.add(new GeneratedFile("src/main/resources/application.yml", applicationYaml(context)));
        return out;
    }

    private String settingsGradle(String projectName) {
        return "rootProject.name = '" + projectName + "'\n";
    }

    private String buildGradle(PluginContext context) {
        String springBootVersion = context.config().project().springBootVersion();
        String javaVersion = context.config().project().javaVersion();
        String dbDependency = databaseDependency(context.config().standards().database().type());
        String migrationDependency = migrationDependency(context.config().standards().database().migrationTool());

        return "plugins {\n"
                + "    id 'java'\n"
                + "    id 'org.springframework.boot' version '" + springBootVersion + "'\n"
                + "    id 'io.spring.dependency-management' version '1.1.5'\n"
                + "}\n\n"
                + "group = '" + context.config().project().basePackage() + "'\n"
                + "version = '0.0.1-SNAPSHOT'\n\n"
                + "java {\n"
                + "    toolchain {\n"
                + "        languageVersion = JavaLanguageVersion.of(" + javaVersion + ")\n"
                + "    }\n"
                + "}\n\n"
                + "repositories {\n"
                + "    mavenCentral()\n"
                + "}\n\n"
                + "dependencies {\n"
                + "    implementation 'org.springframework.boot:spring-boot-starter-web'\n"
                + "    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'\n"
                + "    implementation 'org.springframework.boot:spring-boot-starter-validation'\n"
                + "    implementation 'org.mapstruct:mapstruct:1.5.5.Final'\n"
                + "    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'\n"
                + migrationDependency
                + dbDependency
                + "    compileOnly 'org.projectlombok:lombok'\n"
                + "    annotationProcessor 'org.projectlombok:lombok'\n"
                + "    annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'\n"
                + "    testImplementation 'org.springframework.boot:spring-boot-starter-test'\n"
                + "    testImplementation 'org.mockito:mockito-junit-jupiter'\n"
                + "    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'\n"
                + "}\n\n"
                + "tasks.named('test') {\n"
                + "    useJUnitPlatform()\n"
                + "}\n";
    }

    private String springBootApplication(String basePackage, String appClass) {
        return "package " + basePackage + ";\n\n"
                + "import org.springframework.boot.SpringApplication;\n"
                + "import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
                + "@SpringBootApplication\n"
                + "public class " + appClass + " {\n\n"
                + "    public static void main(String[] args) {\n"
                + "        SpringApplication.run(" + appClass + ".class, args);\n"
                + "    }\n"
                + "}\n";
    }

    private String applicationYaml(PluginContext context) {
        String database = context.config().standards().database().type().toLowerCase(Locale.ROOT);
        return switch (database) {
            case "h2" -> """
                    spring:
                      datasource:
                        url: jdbc:h2:mem:testdb
                        driver-class-name: org.h2.Driver
                        username: sa
                        password:
                      jpa:
                        hibernate:
                          ddl-auto: update
                      h2:
                        console:
                          enabled: true
                    server:
                      port: 8080
                    """;
            case "mysql" -> """
                    spring:
                      datasource:
                        url: jdbc:mysql://localhost:3306/appdb
                        username: app
                        password: app
                      jpa:
                        hibernate:
                          ddl-auto: update
                    server:
                      port: 8080
                    """;
            default -> """
                    spring:
                      datasource:
                        url: jdbc:postgresql://localhost:5432/appdb
                        username: app
                        password: app
                      jpa:
                        hibernate:
                          ddl-auto: update
                    server:
                      port: 8080
                    """;
        };
    }

    private String databaseDependency(String databaseType) {
        String normalized = databaseType == null ? "" : databaseType.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mysql" -> "    runtimeOnly 'com.mysql:mysql-connector-j'\n";
            case "h2" -> "    runtimeOnly 'com.h2database:h2'\n";
            default -> "    runtimeOnly 'org.postgresql:postgresql'\n";
        };
    }

    private String migrationDependency(String migrationTool) {
        String normalized = migrationTool == null ? "" : migrationTool.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "liquibase" -> "    implementation 'org.liquibase:liquibase-core'\n";
            case "none" -> "";
            default -> "    implementation 'org.flywaydb:flyway-core'\n";
        };
    }

    private String appClassName(String projectName) {
        String base = projectName == null || projectName.isBlank() ? "GeneratedApi" : projectName;
        String normalized = base.replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (normalized.isBlank()) {
            return "GeneratedApiApplication";
        }
        StringBuilder out = new StringBuilder();
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            out.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                out.append(token.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out + "Application";
    }
}
