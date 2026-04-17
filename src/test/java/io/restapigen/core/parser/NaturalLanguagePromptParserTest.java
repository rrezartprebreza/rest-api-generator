package io.restapigen.core.parser;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.EntitySpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NaturalLanguagePromptParserTest {
    @Test
    void usesInferredNamesWhenConfigStillHasDefaults() {
        ApiSpecification spec = new NaturalLanguagePromptParser().parse(
                "Create a REST API for User with name and email",
                GenerationConfig.defaults()
        );

        assertEquals("users-api", spec.projectName);
        assertEquals("io.backend.users", spec.basePackage);
    }

    @Test
    void keepsExplicitConfigOverridesWhenProvided() {
        GenerationConfig defaults = GenerationConfig.defaults();
        GenerationConfig config = new GenerationConfig(
                new GenerationConfig.ProjectConfig(
                        "customer-ops-api",
                        "io.acme.customerops",
                        defaults.project().springBootVersion(),
                        defaults.project().javaVersion(),
                        defaults.project().templatePack()
                ),
                defaults.standards(),
                defaults.features(),
                defaults.plugins()
        );

        ApiSpecification spec = new NaturalLanguagePromptParser().parse(
                "Create a REST API for User with name and email",
                config
        );

        assertEquals("customer-ops-api", spec.projectName);
        assertEquals("io.acme.customerops", spec.basePackage);
    }

    @Test
    void keepsOriginalPromptNamingWhenLlmStructuredPromptUsesEntityNames() {
        ApiSpecification structuredSpec = new ApiSpecification(
                "categories-api",
                "com.example.categories",
                List.of(
                        new EntityDefinition(
                                new EntitySpec("Category", "categories", "Long", List.of()),
                                new ApiSpec("/api/categories", true, true, true),
                                List.of()
                        ),
                        new EntityDefinition(
                                new EntitySpec("Product", "products", "Long", List.of()),
                                new ApiSpec("/api/products", true, true, true),
                                List.of()
                        )
                ),
                List.of()
        );

        ApiSpecification spec = NaturalLanguagePromptParser.applyProjectIdentity(
                "Create a complete e-commerce API with Category and Product",
                structuredSpec,
                GenerationConfig.defaults()
        );

        assertEquals("e-commerce-api", spec.projectName);
        assertEquals("io.backend.ecommerce", spec.basePackage);
    }
}
