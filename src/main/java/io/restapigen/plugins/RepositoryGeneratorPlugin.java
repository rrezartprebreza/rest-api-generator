package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RepositoryGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "repository-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage = context.config().project().basePackage();
        String javaBase = "src/main/java/" + context.basePackagePath();
        String suffix = context.config().standards().naming().repositorySuffix();
        for (EntityDefinition definition : specification.entities) {
            String entityName = definition.entity.name;
            String className = entityName + suffix;
            String content = context.templates().render(
                    context.templatePack().templatePath("repository.java.tpl"),
                    Map.of(
                            "basePackage", basePackage,
                            "entityName", entityName,
                            "className", className
                    )
            );
            out.add(new GeneratedFile(javaBase + "/repository/" + className + ".java", content));
        }
        return out;
    }
}
