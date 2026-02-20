package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ServiceGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "service-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getDependencies() {
        return List.of("repository-generator");
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        if (!context.config().standards().layering().includeServiceLayer()) {
            return List.of();
        }
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage = context.config().project().basePackage();
        String javaBase = "src/main/java/" + context.basePackagePath();
        String suffix = context.config().standards().naming().serviceSuffix();
        String repositorySuffix = context.config().standards().naming().repositorySuffix();
        for (EntityDefinition definition : specification.entities) {
            String entityName = definition.entity.name;
            String className = entityName + suffix;
            String repositoryClass = entityName + repositorySuffix;
            String content = context.templates().render(
                    context.templatePack().templatePath("service.java.tpl"),
                    Map.of(
                            "basePackage", basePackage,
                            "entityName", entityName,
                            "className", className,
                            "repositoryClass", repositoryClass
                    )
            );
            out.add(new GeneratedFile(javaBase + "/service/" + className + ".java", content));
        }
        return out;
    }
}
