package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ControllerGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "controller-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getDependencies() {
        return List.of("service-generator", "repository-generator");
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage = context.config().project().basePackage();
        String javaBase = "src/main/java/" + context.basePackagePath();
        String controllerSuffix = context.config().standards().naming().controllerSuffix();
        String serviceSuffix = context.config().standards().naming().serviceSuffix();
        String repositorySuffix = context.config().standards().naming().repositorySuffix();
        boolean useServiceLayer = context.config().standards().layering().includeServiceLayer();

        for (EntityDefinition definition : specification.entities) {
            String entityName = definition.entity.name;
            String collaboratorClass = entityName + (useServiceLayer ? serviceSuffix : repositorySuffix);
            String collaboratorPackage = basePackage + (useServiceLayer ? ".service." : ".repository.") + collaboratorClass;
            String className = entityName + controllerSuffix;
            String createCall = useServiceLayer ? "collaborator.create(entity);" : "collaborator.save(entity);";
            String content = context.templates().render(
                    context.templatePack().templatePath("controller.java.tpl"),
                    Map.of(
                            "basePackage", basePackage,
                            "entityName", entityName,
                            "className", className,
                            "collaboratorImport", collaboratorPackage,
                            "collaboratorClass", collaboratorClass,
                            "createCall", createCall
                    )
            );
            out.add(new GeneratedFile(javaBase + "/controller/" + className + ".java", content));
        }
        return out;
    }
}
