package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.RelationshipSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ControllerGeneratorPlugin implements GeneratorPlugin {
    @Override public String getName()    { return "controller-generator"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public List<String> getDependencies() {
        return List.of("service-generator", "repository-generator");
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage       = context.config().project().basePackage();
        String javaBase          = "src/main/java/" + context.basePackagePath();
        String controllerSuffix  = context.config().standards().naming().controllerSuffix();
        String serviceSuffix     = context.config().standards().naming().serviceSuffix();
        String repositorySuffix  = context.config().standards().naming().repositorySuffix();
        String dtoSuffix         = context.config().standards().naming().dtoSuffix();
        boolean useServiceLayer  = context.config().standards().layering().includeServiceLayer();

        for (EntityDefinition definition : specification.entities) {
            String entityName          = definition.entity.name;
            String collaboratorClass   = entityName + (useServiceLayer ? serviceSuffix : repositorySuffix);
            String collaboratorPackage = basePackage + (useServiceLayer ? ".service." : ".repository.") + collaboratorClass;
            String className           = entityName + controllerSuffix;
            String dtoClass            = entityName + dtoSuffix;

            String createCall    = useServiceLayer ? "collaborator.create(dto)"         : "throw new UnsupportedOperationException(\"Service layer required\")";
            String findByIdCall  = useServiceLayer ? "collaborator.findById(id)"        : "throw new UnsupportedOperationException(\"Service layer required\")";
            String updateCall    = useServiceLayer ? "collaborator.update(id, dto)"     : "throw new UnsupportedOperationException(\"Service layer required\")";
            String deleteCall    = useServiceLayer ? "collaborator.delete(id);"         : "throw new UnsupportedOperationException(\"Service layer required\");";

            // Relationship-query endpoints  e.g. GET /api/orders/by-user/{userId}
            String relationEndpoints = useServiceLayer
                    ? buildRelationEndpoints(dtoClass, definition.relationships)
                    : "";

            String content = context.templates().render(
                    context.templatePack().templatePath("controller.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage",        basePackage),
                            Map.entry("entityName",         entityName),
                            Map.entry("dtoClass",           dtoClass),
                            Map.entry("className",          className),
                            Map.entry("resourcePath",       definition.api.resourcePath),
                            Map.entry("collaboratorImport", collaboratorPackage),
                            Map.entry("collaboratorClass",  collaboratorClass),
                            Map.entry("createCall",         createCall),
                            Map.entry("findByIdCall",       findByIdCall),
                            Map.entry("updateCall",         updateCall),
                            Map.entry("deleteCall",         deleteCall),
                            Map.entry("relationEndpoints",  relationEndpoints)
                    )
            );
            out.add(new GeneratedFile(javaBase + "/controller/" + className + ".java", content));
        }
        return out;
    }

    /**
     * Generates a GET endpoint per ManyToOne/OneToOne relationship, e.g.:
     *   GET /api/orders/by-user/{userId}     → List<OrderDTO>
     *   GET /api/orders/by-category/{categoryId} → List<OrderDTO>
     */
    private String buildRelationEndpoints(String dtoClass, List<RelationshipSpec> relationships) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec rel : relationships) {
            if (!"ManyToOne".equals(rel.type) && !"OneToOne".equals(rel.type)) continue;

            String fieldName    = rel.fieldName;                     // "user"
            String capitalField = capitalize(fieldName);             // "User"
            String kebabField   = toKebab(fieldName);               // "user"

            sb.append("\n")
              .append("    /**\n")
              .append("     * GET /").append(kebabField).append("-{").append(fieldName).append("Id}\n")
              .append("     * Find all ").append(dtoClass.replace("DTO", ""))
              .append(" records belonging to a specific ").append(rel.target).append(".\n")
              .append("     */\n")
              .append("    @GetMapping(\"/by-").append(kebabField).append("/{").append(fieldName).append("Id}\")\n")
              .append("    public java.util.List<").append(dtoClass).append("> findBy").append(capitalField)
              .append("Id(@PathVariable Long ").append(fieldName).append("Id) {\n")
              .append("        return collaborator.findBy").append(capitalField).append("Id(").append(fieldName).append("Id);\n")
              .append("    }\n");
        }
        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** camelCase → kebab-case  e.g. "orderItem" → "order-item" */
    private static String toKebab(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}