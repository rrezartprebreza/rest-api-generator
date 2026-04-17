package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;

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

            // Build filter predicates for string fields only
            String filterPredicates = buildFilterPredicates(definition.entity.fields);

            String content = context.templates().render(
                    context.templatePack().templatePath("service.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage",      basePackage),
                            Map.entry("entityName",       entityName),
                            Map.entry("entityClass",      entityClass),
                            Map.entry("className",        className),
                            Map.entry("repositoryClass",  repositoryClass),
                            Map.entry("mapperClass",      mapperClass),
                            Map.entry("dtoClass",         dtoClass),
                            Map.entry("filterPredicates", filterPredicates)
                    )
            );
            out.add(new GeneratedFile(javaBase + "/service/" + className + ".java", content));
        }
        return out;
    }

    private static final java.util.Set<String> SENSITIVE_FIELDS = java.util.Set.of(
            "password", "secret", "token", "apiKey", "privateKey", "accessToken", "refreshToken"
    );

    /**
     * Generates cb.like(...) predicates for every String field in the entity.
     * Excludes sensitive fields (password, token, secret, etc.).
     * Falls back to a safe no-op when no String fields exist.
     */
    private String buildFilterPredicates(List<FieldSpec> fields) {
        List<String> stringFields = fields.stream()
                .filter(f -> "String".equals(f.type))
                .map(f -> f.name)
                .filter(name -> !SENSITIVE_FIELDS.contains(name))
                .collect(Collectors.toList());

        if (stringFields.isEmpty()) {
            // No searchable string fields — return a predicate that always matches
            return "cb.isTrue(cb.literal(true))";
        }

        return stringFields.stream()
                .map(name -> "cb.like(cb.lower(root.get(\"" + name + "\")), likePattern)")
                .collect(Collectors.joining(",\n            "));
    }
}
