package io.restapigen.core.orchestrator;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.core.validator.SpecValidator;
import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.EntitySpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationOrchestratorTest {

    @Test
    void rejectsUnsafePathReturnedByPlugin() {
        GeneratorPlugin unsafePlugin = new GeneratorPlugin() {
            @Override public String getName() { return "unsafe"; }
            @Override public String getVersion() { return "1.0.0"; }

            @Override
            public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
                return List.of(new GeneratedFile("../outside.txt", "unsafe"));
            }
        };
        GenerationConfig defaults = GenerationConfig.defaults();
        GenerationConfig config = new GenerationConfig(
                defaults.project(),
                defaults.standards(),
                defaults.features(),
                new GenerationConfig.PluginsConfig(List.of("unsafe"), List.of(), List.of(), List.of())
        );
        ApiSpecification specification = new ApiSpecification(
                "products-api",
                "com.example.generated",
                List.of(new EntityDefinition(
                        new EntitySpec("Product", "products", "Long", List.of()),
                        new ApiSpec("/api/products", true, true, true),
                        List.of()
                )),
                List.of()
        );

        GenerationOrchestrator orchestrator = new GenerationOrchestrator(new SpecValidator(), List.of(unsafePlugin));

        assertThrows(IllegalArgumentException.class, () -> orchestrator.generateZip(specification, config));
    }
}
