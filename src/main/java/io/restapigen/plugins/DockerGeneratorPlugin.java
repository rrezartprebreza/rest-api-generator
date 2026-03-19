package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;

import java.util.List;
import java.util.Locale;

public final class DockerGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "docker-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        if (!context.config().features().dockerArtifacts()) {
            return List.of();
        }

        String dockerfile = "FROM gradle:8.10-jdk17 AS builder\n"
                + "WORKDIR /workspace\n"
                + "COPY . .\n"
                + "RUN gradle --no-daemon bootJar -x test\n\n"
                + "FROM eclipse-temurin:17-jre\n"
                + "WORKDIR /app\n"
                + "COPY --from=builder /workspace/build/libs/*.jar app.jar\n"
                + "EXPOSE 8080\n"
                + "ENTRYPOINT [\"java\",\"-jar\",\"/app/app.jar\"]\n";

        String compose = compose(specification.projectName, context.config().standards().database().type());

        return List.of(
                new GeneratedFile("Dockerfile", dockerfile),
                new GeneratedFile("docker-compose.yml", compose)
        );
    }

    private String compose(String projectName, String databaseType) {
        String normalized = databaseType == null ? "" : databaseType.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mysql" -> "services:\n"
                    + "  db:\n"
                    + "    image: mysql:8.4\n"
                    + "    environment:\n"
                    + "      MYSQL_DATABASE: appdb\n"
                    + "      MYSQL_USER: app\n"
                    + "      MYSQL_PASSWORD: app\n"
                    + "      MYSQL_ROOT_PASSWORD: root\n"
                    + "    ports:\n"
                    + "      - \"3306:3306\"\n"
                    + "    volumes:\n"
                    + "      - db-data:/var/lib/mysql\n"
                    + "  " + projectName + ":\n"
                    + "    build: .\n"
                    + "    depends_on:\n"
                    + "      - db\n"
                    + "    ports:\n"
                    + "      - \"8080:8080\"\n"
                    + "    environment:\n"
                    + "      SPRING_PROFILES_ACTIVE: docker\n"
                    + "      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/appdb\n"
                    + "      SPRING_DATASOURCE_USERNAME: app\n"
                    + "      SPRING_DATASOURCE_PASSWORD: app\n"
                    + "  adminer:\n"
                    + "    image: adminer:4\n"
                    + "    profiles:\n"
                    + "      - tools\n"
                    + "    depends_on:\n"
                    + "      - db\n"
                    + "    ports:\n"
                    + "      - \"8081:8080\"\n"
                    + "volumes:\n"
                    + "  db-data:\n";
            case "h2" -> "services:\n"
                    + "  " + projectName + ":\n"
                    + "    build: .\n"
                    + "    ports:\n"
                    + "      - \"8080:8080\"\n"
                    + "    environment:\n"
                    + "      SPRING_PROFILES_ACTIVE: docker\n"
                    + "      SPRING_DATASOURCE_URL: jdbc:h2:mem:testdb\n"
                    + "      SPRING_DATASOURCE_USERNAME: sa\n"
                    + "      SPRING_DATASOURCE_PASSWORD: \n";
            default -> "services:\n"
                    + "  db:\n"
                    + "    image: postgres:16-alpine\n"
                    + "    environment:\n"
                    + "      POSTGRES_DB: appdb\n"
                    + "      POSTGRES_USER: app\n"
                    + "      POSTGRES_PASSWORD: app\n"
                    + "    ports:\n"
                    + "      - \"5432:5432\"\n"
                    + "    volumes:\n"
                    + "      - db-data:/var/lib/postgresql/data\n"
                    + "  " + projectName + ":\n"
                    + "    build: .\n"
                    + "    depends_on:\n"
                    + "      - db\n"
                    + "    ports:\n"
                    + "      - \"8080:8080\"\n"
                    + "    environment:\n"
                    + "      SPRING_PROFILES_ACTIVE: docker\n"
                    + "      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/appdb\n"
                    + "      SPRING_DATASOURCE_USERNAME: app\n"
                    + "      SPRING_DATASOURCE_PASSWORD: app\n"
                    + "  adminer:\n"
                    + "    image: adminer:4\n"
                    + "    profiles:\n"
                    + "      - tools\n"
                    + "    depends_on:\n"
                    + "      - db\n"
                    + "    ports:\n"
                    + "      - \"8081:8080\"\n"
                    + "volumes:\n"
                    + "  db-data:\n";
        };
    }
}
