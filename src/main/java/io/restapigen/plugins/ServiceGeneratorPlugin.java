package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;
import io.restapigen.domain.RelationshipSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ServiceGeneratorPlugin implements GeneratorPlugin {
    @Override public String getName()    { return "service-generator"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public List<String> getDependencies() {
        return List.of("repository-generator", "mapper-generator");
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        if (!context.config().standards().layering().includeServiceLayer()) {
            return List.of();
        }
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage      = context.config().project().basePackage();
        String javaBase         = "src/main/java/" + context.basePackagePath();
        String suffix           = context.config().standards().naming().serviceSuffix();
        String repositorySuffix = context.config().standards().naming().repositorySuffix();
        String dtoSuffix        = context.config().standards().naming().dtoSuffix();
        String entitySuffix     = context.config().standards().naming().entitySuffix();

        for (EntityDefinition definition : specification.entities) {
            String entityName       = definition.entity.name;
            String entityClass      = entityName + entitySuffix;
            String className        = entityName + suffix;
            String repositoryClass  = entityName + repositorySuffix;
            String mapperClass      = entityName + "Mapper";
            String dtoClass         = entityName + dtoSuffix;

            String filterPredicates       = buildFilterPredicates(definition.entity.fields);
            String relationServiceImports = buildRelationServiceImports(basePackage, entitySuffix, definition.relationships);
            String relationServiceMethods = buildRelationServiceMethods(entityClass, dtoClass, entitySuffix, definition.relationships);

            String content = context.templates().render(
                    context.templatePack().templatePath("service.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage",             basePackage),
                            Map.entry("entityName",              entityName),
                            Map.entry("entityClass",             entityClass),
                            Map.entry("className",               className),
                            Map.entry("repositoryClass",         repositoryClass),
                            Map.entry("mapperClass",             mapperClass),
                            Map.entry("dtoClass",                dtoClass),
                            Map.entry("filterPredicates",        filterPredicates),
                            Map.entry("relationServiceImports",  relationServiceImports),
                            Map.entry("relationServiceMethods",  relationServiceMethods)
                    )
            );
            out.add(new GeneratedFile(javaBase + "/service/" + className + ".java", content));
        }
        return out;
    }

    private static final java.util.Set<String> SENSITIVE_FIELDS = java.util.Set.of(
            "password", "secret", "token", "apiKey", "privateKey", "accessToken", "refreshToken"
    );

    private String buildFilterPredicates(List<FieldSpec> fields) {
        List<String> stringFields = fields.stream()
                .filter(f -> "String".equals(f.type))
                .map(f -> f.name)
                .filter(name -> !SENSITIVE_FIELDS.contains(name))
                .collect(Collectors.toList());
        if (stringFields.isEmpty()) {
            return "cb.isTrue(cb.literal(true))";
        }
        return stringFields.stream()
                .map(name -> "cb.like(cb.lower(root.get(\"" + name + "\")), likePattern)")
                .collect(Collectors.joining(",\n            "));
    }

    private String buildRelationServiceImports(String basePackage, String entitySuffix,
                                               List<RelationshipSpec> relationships) {
        return relationships.stream()
                .filter(r -> "ManyToOne".equals(r.type) || "OneToOne".equals(r.type))
                .map(r -> "import " + basePackage + ".entity." + r.target + entitySuffix + ";")
                .distinct()
                .collect(Collectors.joining("\n"));
    }

    private String buildRelationServiceMethods(String entityClass, String dtoClass,
                                               String entitySuffix,
                                               List<RelationshipSpec> relationships) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec rel : relationships) {
            if (!"ManyToOne".equals(rel.type) && !"OneToOne".equals(rel.type)) continue;
            String relEntity    = rel.target + entitySuffix;
            String fieldName    = rel.fieldName;
            String capitalField = capitalize(fieldName);

            sb.append("\n")
              .append("    /** Get all ").append(entityClass).append(" records for a given ")
              .append(rel.target).append(" id. */\n")
              .append("    public List<").append(dtoClass).append("> findBy").append(capitalField)
              .append("Id(Long ").append(fieldName).append("Id) {\n")
              .append("        return repository.findBy").append(capitalField).append("Id(").append(fieldName).append("Id)\n")
              .append("                .stream().map(mapper::toDto).collect(java.util.stream.Collectors.toList());\n")
              .append("    }\n")
              .append("\n")
              .append("    /** Paginated: get ").append(entityClass).append(" records for a given ")
              .append(rel.target).append(". */\n")
              .append("    public Page<").append(dtoClass).append("> findBy").append(capitalField)
              .append("(").append(relEntity).append(" ").append(fieldName)
              .append(", int page, int size) {\n")
              .append("        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));\n")
              .append("        return repository.findBy").append(capitalField).append("(").append(fieldName).append(", pageable)\n")
              .append("                .map(mapper::toDto);\n")
              .append("    }\n");
        }
        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
