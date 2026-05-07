package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.RelationshipSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Map<String, String> idTypeByEntity = idTypeByEntity(specification);

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
                    ? buildRelationEndpoints(dtoClass, definition.relationships, idTypeByEntity)
                    : "";
            String idTypeImport = idTypeImport(definition, idTypeByEntity);

            // Custom endpoint stubs from "include login, logout, register" DSL
            String customEndpointsBlock = buildCustomEndpoints(definition.api.customEndpoints);

            String content = context.templates().render(
                    context.templatePack().templatePath("controller.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage",           basePackage),
                            Map.entry("entityName",            entityName),
                            Map.entry("dtoClass",              dtoClass),
                            Map.entry("className",             className),
                            Map.entry("idType",                definition.entity.idType),
                            Map.entry("idTypeImport",          idTypeImport),
                            Map.entry("resourcePath",          definition.api.resourcePath),
                            Map.entry("collaboratorImport",    collaboratorPackage),
                            Map.entry("collaboratorClass",     collaboratorClass),
                            Map.entry("createCall",            createCall),
                            Map.entry("findByIdCall",          findByIdCall),
                            Map.entry("updateCall",            updateCall),
                            Map.entry("deleteCall",            deleteCall),
                            Map.entry("relationEndpoints",     relationEndpoints),
                            Map.entry("customEndpointsBlock",  customEndpointsBlock)
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
    private String buildRelationEndpoints(String dtoClass, List<RelationshipSpec> relationships,
                                          Map<String, String> idTypeByEntity) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec rel : relationships) {
            if (!"ManyToOne".equals(rel.type) && !"OneToOne".equals(rel.type)) continue;

            String fieldName    = rel.fieldName;                     // "user"
            String capitalField = capitalize(fieldName);             // "User"
            String kebabField   = toKebab(fieldName);               // "user"
            String targetIdType = idTypeByEntity.getOrDefault(rel.target, "Long");

            sb.append("\n")
              .append("    /**\n")
              .append("     * GET /").append(kebabField).append("-{").append(fieldName).append("Id}\n")
              .append("     * Find all ").append(dtoClass.replace("DTO", ""))
              .append(" records belonging to a specific ").append(rel.target).append(".\n")
              .append("     */\n")
              .append("    @GetMapping(\"/by-").append(kebabField).append("/{").append(fieldName).append("Id}\")\n")
              .append("    public java.util.List<").append(dtoClass).append("> findBy").append(capitalField)
              .append("Id(@PathVariable ").append(targetIdType).append(" ").append(fieldName).append("Id) {\n")
              .append("        return collaborator.findBy").append(capitalField).append("Id(").append(fieldName).append("Id);\n")
              .append("    }\n");
        }
        return sb.toString();
    }

    private String idTypeImport(EntityDefinition definition, Map<String, String> idTypeByEntity) {
        Set<String> imports = new LinkedHashSet<>();
        TemplateSupport.addTypeImport(imports, definition.entity.idType);
        definition.relationships.stream()
                .filter(r -> "ManyToOne".equals(r.type) || "OneToOne".equals(r.type))
                .map(r -> idTypeByEntity.getOrDefault(r.target, "Long"))
                .forEach(type -> TemplateSupport.addTypeImport(imports, type));
        return imports.stream()
                .map(value -> "import " + value + ";")
                .collect(Collectors.joining("\n"));
    }

    private Map<String, String> idTypeByEntity(ApiSpecification specification) {
        Map<String, String> idTypes = new HashMap<>();
        for (EntityDefinition definition : specification.entities) {
            idTypes.put(definition.entity.name, definition.entity.idType);
        }
        return idTypes;
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** camelCase → kebab-case  e.g. "orderItem" → "order-item" */
    private static String toKebab(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    /**
     * Generates POST stub endpoints for custom endpoint names from "include X, Y, Z" DSL.
     * e.g., "login" → POST /login stub.
     */
    private String buildCustomEndpoints(List<String> customEndpoints) {
        if (customEndpoints == null || customEndpoints.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String endpoint : customEndpoints) {
            String kebab = toKebab(endpoint);
            String method = inferHttpMethod(endpoint);
            String annotation = "@" + capitalize(method.toLowerCase()) + "Mapping(\"/" + kebab + "\")";
            String methodName = toCamel(endpoint);
            sb.append("\n")
              .append("    /**\n")
              .append("     * ").append(method).append(" /").append(kebab).append(" — custom endpoint.\n")
              .append("     * TODO: implement ").append(methodName).append(" logic.\n")
              .append("     */\n")
              .append("    ").append(annotation).append("\n")
              .append("    public ResponseEntity<Void> ").append(methodName).append("() {\n")
              .append("        throw new UnsupportedOperationException(\"").append(methodName).append(" not yet implemented\");\n")
              .append("    }\n");
        }
        return sb.toString();
    }

    private static String inferHttpMethod(String endpoint) {
        String lower = endpoint.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("get") || lower.equals("list") || lower.equals("search") || lower.equals("find")) return "GET";
        if (lower.startsWith("delete") || lower.startsWith("remove")) return "DELETE";
        return "POST";
    }

    private static String toCamel(String s) {
        if (s == null || s.isBlank()) return s;
        String[] parts = s.split("[_\\-\\s]+");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase(java.util.Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }
}
