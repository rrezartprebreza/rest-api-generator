package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProjectScaffoldGeneratorPlugin implements GeneratorPlugin {
    @Override public String getName()    { return "project-scaffold-generator"; }
    @Override public String getVersion() { return "1.0.0"; }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage     = context.config().project().basePackage();
        String basePackagePath = context.basePackagePath();
        String appClass        = appClassName(specification.projectName);
        boolean auditing       = context.config().features().auditing();

        out.add(new GeneratedFile("settings.gradle", settingsGradle(specification.projectName)));
        out.add(new GeneratedFile("build.gradle",    buildGradle(context)));
        out.add(new GeneratedFile(
                "src/main/java/" + basePackagePath + "/" + appClass + ".java",
                springBootApplication(basePackage, appClass, auditing)
        ));
        out.add(new GeneratedFile("src/main/resources/application.yml",        applicationYaml(context)));
        out.add(new GeneratedFile("src/test/resources/application-test.yml",   testApplicationYaml()));
        out.add(new GeneratedFile(".env.example",                               envExample()));
        out.add(new GeneratedFile(".gitignore",                                 gitignore()));
        return out;
    }

    private String settingsGradle(String projectName) {
        return "rootProject.name = '" + projectName + "'\n";
    }

    private String buildGradle(PluginContext context) {
        String springBootVersion  = context.config().project().springBootVersion();
        String javaVersion        = context.config().project().javaVersion();
        String dbDependency       = databaseDependency(context.config().standards().database().type());
        String migrationDep       = migrationDependency(context.config().standards().database().migrationTool());
        boolean auditing          = context.config().features().auditing();

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
                + migrationDep
                + dbDependency
                + "    compileOnly 'org.projectlombok:lombok'\n"
                + "    annotationProcessor 'org.projectlombok:lombok'\n"
                + "    annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'\n"
                + "    testImplementation 'org.springframework.boot:spring-boot-starter-test'\n"
                + "    testImplementation 'org.mockito:mockito-junit-jupiter'\n"
                + "    testImplementation 'com.h2database:h2'\n"
                + "    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'\n"
                + "}\n\n"
                + "tasks.named('test') {\n"
                + "    useJUnitPlatform()\n"
                + "}\n";
    }

    private String springBootApplication(String basePackage, String appClass, boolean auditing) {
        String auditImport = auditing ? "\nimport org.springframework.data.jpa.repository.config.EnableJpaAuditing;" : "";
        String auditAnnotation = auditing ? "\n@EnableJpaAuditing" : "";
        return "package " + basePackage + ";\n\n"
                + "import org.springframework.boot.SpringApplication;\n"
                + "import org.springframework.boot.autoconfigure.SpringBootApplication;"
                + auditImport + "\n\n"
                + "@SpringBootApplication"
                + auditAnnotation + "\n"
                + "public class " + appClass + " {\n\n"
                + "    public static void main(String[] args) {\n"
                + "        SpringApplication.run(" + appClass + ".class, args);\n"
                + "    }\n"
                + "}\n";
    }

    private String applicationYaml(PluginContext context) {
        String database = context.config().standards().database().type().toLowerCase(Locale.ROOT);
        // All credentials use environment variable placeholders — never hardcode secrets
        return switch (database) {
            case "h2" -> """
                    spring:
                      datasource:
                        url: ${DB_URL:jdbc:h2:mem:appdb}
                        driver-class-name: org.h2.Driver
                        username: ${DB_USER:sa}
                        password: ${DB_PASSWORD:}
                      jpa:
                        hibernate:
                          ddl-auto: validate
                        show-sql: false
                      h2:
                        console:
                          enabled: false
                    server:
                      port: ${SERVER_PORT:8080}
                    springdoc:
                      swagger-ui:
                        path: /swagger-ui.html
                    """;
            case "mysql" -> """
                    spring:
                      datasource:
                        url: ${DB_URL:jdbc:mysql://localhost:3306/appdb}
                        username: ${DB_USER:app}
                        password: ${DB_PASSWORD:app}
                      jpa:
                        hibernate:
                          ddl-auto: validate
                        show-sql: false
                    server:
                      port: ${SERVER_PORT:8080}
                    springdoc:
                      swagger-ui:
                        path: /swagger-ui.html
                    """;
            default -> """
                    spring:
                      datasource:
                        url: ${DB_URL:jdbc:postgresql://localhost:5432/appdb}
                        username: ${DB_USER:app}
                        password: ${DB_PASSWORD:app}
                      jpa:
                        hibernate:
                          ddl-auto: validate
                        show-sql: false
                    server:
                      port: ${SERVER_PORT:8080}
                    springdoc:
                      swagger-ui:
                        path: /swagger-ui.html
                    """;
        };
    }

    private String testApplicationYaml() {
        // Integration tests always use H2 in-memory — no external DB needed
        return """
                spring:
                  datasource:
                    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
                    driver-class-name: org.h2.Driver
                    username: sa
                    password:
                  jpa:
                    hibernate:
                      ddl-auto: create-drop
                    show-sql: true
                  h2:
                    console:
                      enabled: false
                """;
    }

    private String envExample() {
        return """
                # Copy this file to .env and fill in your values
                # Never commit .env to version control

                DB_URL=jdbc:postgresql://localhost:5432/appdb
                DB_USER=app
                DB_PASSWORD=changeme
                SERVER_PORT=8080
                """;
    }

    private String gitignore() {
        return """
                # Build
                build/
                .gradle/
                *.jar

                # Secrets
                .env
                *.env

                # IDE
                .idea/
                *.iml
                .vscode/

                # OS
                .DS_Store
                Thumbs.db
                """;
    }

    private String databaseDependency(String databaseType) {
        return switch (databaseType == null ? "" : databaseType.toLowerCase(Locale.ROOT)) {
            case "mysql" -> "    runtimeOnly 'com.mysql:mysql-connector-j'\n";
            case "h2"    -> "    runtimeOnly 'com.h2database:h2'\n";
            default      -> "    runtimeOnly 'org.postgresql:postgresql'\n";
        };
    }

    private String migrationDependency(String migrationTool) {
        return switch (migrationTool == null ? "" : migrationTool.toLowerCase(Locale.ROOT)) {
            case "liquibase" -> "    implementation 'org.liquibase:liquibase-core'\n";
            case "none"      -> "";
            // flyway-core is sufficient for Flyway 9.x (Spring Boot 3.2.x).
            // On Spring Boot 3.3+ (Flyway 10+) add the database-specific module manually if needed.
            default          -> "    implementation 'org.flywaydb:flyway-core'\n";
        };
    }

    private String appClassName(String projectName) {
        String base = (projectName == null || projectName.isBlank()) ? "GeneratedApi" : projectName;
        String normalized = base.replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (normalized.isBlank()) return "GeneratedApiApplication";
        StringBuilder out = new StringBuilder();
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                out.append(Character.toUpperCase(token.charAt(0)));
                if (token.length() > 1) out.append(token.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out + "Application";
    }
}
