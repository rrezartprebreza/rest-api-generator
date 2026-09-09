package io.restapigen.output.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.EntitySpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiWriterTest {

    @Test
    void rendersValidCrudContractForEntities() throws Exception {
        ApiSpecification specification = new ApiSpecification(
                "users-api",
                "com.example.generated",
                List.of(new EntityDefinition(
                        new EntitySpec("User", "users", "Long", List.of()),
                        new ApiSpec("/api/users", true, true, true),
                        List.of()
                )),
                List.of()
        );

        String yaml = OpenApiWriter.write(specification);
        JsonNode document = new ObjectMapper(new YAMLFactory()).readTree(yaml);

        assertEquals("3.0.3", document.path("openapi").asText());
        assertEquals("Create User", document.path("paths").path("/api/users").path("post").path("summary").asText());
        assertTrue(document.path("paths").path("/api/users").path("get").path("responses").has("200"));
        assertEquals("id", document.path("paths").path("/api/users/{id}").path("parameters").path(0).path("name").asText());
        assertTrue(document.path("paths").path("/api/users/{id}").path("delete").path("responses").has("204"));
        assertEquals("object", document.path("components").path("schemas").path("UserDTO").path("type").asText());
    }
}
