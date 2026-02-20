package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;

import java.util.ArrayList;
import java.util.List;

public final class DocumentationGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "documentation-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        if ("none".equalsIgnoreCase(context.config().standards().documentation().tool())) {
            return List.of();
        }
        StringBuilder out = new StringBuilder();
        out.append("openapi: 3.0.1\ninfo:\n");
        out.append("  title: ").append(specification.projectName).append("\n");
        out.append("  version: 1.0.0\npaths:\n");
        for (EntityDefinition definition : specification.entities) {
            out.append("  ").append(definition.api.resourcePath).append(":\n");
            out.append("    get:\n");
            out.append("      summary: List ").append(definition.entity.name).append("\n");
            out.append("    post:\n");
            out.append("      summary: Create ").append(definition.entity.name).append("\n");
        }
        return List.of(new GeneratedFile("src/main/resources/openapi.yaml", out.toString()));
    }
}
