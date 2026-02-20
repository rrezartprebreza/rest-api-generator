package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;

import java.util.List;

public final class ProjectReadmeGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "project-readme-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(specification.projectName).append("\n\n");
        out.append("Generated REST API entries from ").append(specification.basePackage).append("\n\n");
        for (EntityDefinition definition : specification.entities) {
            out.append("## ").append(definition.entity.name).append("\n");
            out.append("- Resource path: ").append(definition.api.resourcePath).append("\n");
            for (FieldSpec field : definition.entity.fields) {
                out.append("- ").append(field.name).append(" (").append(field.type).append(")\n");
            }
            if (!definition.relationships.isEmpty()) {
                out.append("### Relationships\n");
                definition.relationships.forEach(r -> out.append("- ").append(r.type).append(" -> ").append(r.target).append("\n"));
            }
            out.append("\n");
        }
        if (!specification.suggestions.isEmpty()) {
            out.append("## Suggestions\n");
            specification.suggestions.forEach(s -> out.append("- ").append(s).append("\n"));
        }
        return List.of(new GeneratedFile("README.md", out.toString()));
    }
}
