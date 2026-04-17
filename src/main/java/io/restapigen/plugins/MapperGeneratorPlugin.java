package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MapperGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "mapper-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getDependencies() {
        return List.of("entity-generator", "dto-generator");
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        if (!context.config().standards().layering().includeDtoMapper()) {
            return List.of();
        }
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage = context.config().project().basePackage();
        String javaBase = "src/main/java/" + context.basePackagePath();
        String dtoSuffix = context.config().standards().naming().dtoSuffix();
        String entitySuffix = context.config().standards().naming().entitySuffix();

        for (EntityDefinition definition : specification.entities) {
            String entityName = definition.entity.name;
            String entityClass = entityName + entitySuffix;
            String className = entityName + "Mapper";
            String dtoClass = entityName + dtoSuffix;
            String content = context.templates().render(
                    context.templatePack().templatePath("mapper.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage", basePackage),
                            Map.entry("entityName", entityClass),
                            Map.entry("className", className),
                            Map.entry("dtoClass", dtoClass)
                    )
            );
            out.add(new GeneratedFile(javaBase + "/mapper/" + className + ".java", content));
        }
        return out;
    }
}
