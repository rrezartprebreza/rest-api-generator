package io.restapigen.output.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.EntitySpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonSpecificationWriterTest {

    @Test
    void preservesSecurityHintAndCustomEndpoints() throws Exception {
        ApiSpecification specification = new ApiSpecification(
                "users-api",
                "com.example.generated",
                List.of(new EntityDefinition(
                        new EntitySpec("User", "users", "Long", List.of()),
                        new ApiSpec("/api/users", true, true, true, List.of("login")),
                        List.of()
                )),
                List.of(),
                "jwt"
        );

        JsonNode json = new ObjectMapper().readTree(
                JsonSpecificationWriter.writeApiSpecification(specification, true)
        );

        assertEquals("jwt", json.path("securityHint").asText());
        assertEquals("login", json.path("entities").path(0).path("api").path("customEndpoints").path(0).asText());
    }
}
