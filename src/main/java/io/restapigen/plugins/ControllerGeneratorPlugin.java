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
        String dtoSuffix = context.config().standards().naming().dtoSuffix();
        boolean useServiceLayer = context.config().standards().layering().includeServiceLayer();

        for (EntityDefinition definition : specification.entities) {
            String entityName = definition.entity.name;
            String collaboratorClass = entityName + (useServiceLayer ? serviceSuffix : repositorySuffix);
            String collaboratorPackage = basePackage + (useServiceLayer ? ".service." : ".repository.") + collaboratorClass;
            String className = entityName + controllerSuffix;
            String createCall = useServiceLayer ? "return collaborator.create(entity);" : "throw new UnsupportedOperationException(\"Service layer required for DTO create flow\");";
            String findByIdCall = useServiceLayer ? "return collaborator.findById(id);" : "throw new UnsupportedOperationException(\"Service layer required for DTO read flow\");";
            String updateCall = useServiceLayer ? "return collaborator.update(id, entity);" : "throw new UnsupportedOperationException(\"Service layer required for DTO update flow\");";
            String deleteCall = useServiceLayer ? "collaborator.delete(id);" : "throw new UnsupportedOperationException(\"Service layer required for DTO delete flow\");";
            String dtoClass = entityName + dtoSuffix;
            String content = context.templates().render(
                    context.templatePack().templatePath("controller.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage", basePackage),
                            Map.entry("entityName", entityName),
                            Map.entry("dtoClass", dtoClass),
                            Map.entry("className", className),
                            Map.entry("resourcePath", definition.api.resourcePath),
                            Map.entry("collaboratorImport", collaboratorPackage),
                            Map.entry("collaboratorClass", collaboratorClass),
                            Map.entry("createCall", createCall),
                            Map.entry("findByIdCall", findByIdCall),
                            Map.entry("updateCall", updateCall),
                            Map.entry("deleteCall", deleteCall)
                    )
            );
            out.add(new GeneratedFile(javaBase + "/controller/" + className + ".java", content));
        }
        return out;
    }
}
