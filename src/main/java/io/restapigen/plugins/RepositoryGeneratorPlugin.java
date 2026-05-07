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

public final class RepositoryGeneratorPlugin implements GeneratorPlugin {
    @Override public String getName()    { return "repository-generator"; }
    @Override public String getVersion() { return "1.0.0"; }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage  = context.config().project().basePackage();
        String javaBase     = "src/main/java/" + context.basePackagePath();
        String suffix       = context.config().standards().naming().repositorySuffix();
        String entitySuffix = context.config().standards().naming().entitySuffix();
        Map<String, String> idTypeByEntity = idTypeByEntity(specification);

        for (EntityDefinition definition : specification.entities) {
            String entityName  = definition.entity.name;
            String entityClass = entityName + entitySuffix;
            String className   = entityName + suffix;

            // Build extra imports and query methods for relationships
            String relationImports  = buildRelationImports(basePackage, entitySuffix, definition, idTypeByEntity);
            String relationMethods  = buildRelationMethods(entityClass, entitySuffix, definition.relationships, idTypeByEntity);

            String content = context.templates().render(
                    context.templatePack().templatePath("repository.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage",      basePackage),
                            Map.entry("entityName",       entityClass),
                            Map.entry("className",        className),
                            Map.entry("idType",           definition.entity.idType),
                            Map.entry("relationImports",  relationImports),
                            Map.entry("relationMethods",  relationMethods)
                    )
            );
            out.add(new GeneratedFile(javaBase + "/repository/" + className + ".java", content));
        }
        return out;
    }

    /**
     * Extra import lines for the related entity types used in query methods.
     * ManyToMany is excluded — those are navigated via the owning entity, not queried by FK.
     */
    private String buildRelationImports(String basePackage, String entitySuffix,
                                        EntityDefinition definition,
                                        Map<String, String> idTypeByEntity) {
        Set<String> imports = new LinkedHashSet<>();
        TemplateSupport.addTypeImport(imports, definition.entity.idType);
        definition.relationships.stream()
                .filter(r -> "ManyToOne".equals(r.type) || "OneToOne".equals(r.type))
                .map(r -> "import " + basePackage + ".entity." + r.target + entitySuffix + ";")
                .distinct()
                .forEach(imports::add);
        definition.relationships.stream()
                .filter(r -> "ManyToOne".equals(r.type) || "OneToOne".equals(r.type))
                .map(r -> idTypeByEntity.getOrDefault(r.target, "Long"))
                .forEach(type -> TemplateSupport.addTypeImport(imports, type));
        return imports.stream()
                .map(value -> value.startsWith("import ") ? value : "import " + value + ";")
                .collect(Collectors.joining("\n"));
    }

    /**
     * Derived-query methods for each ManyToOne / OneToOne relationship:
     *   List<Order>    findByUser(User user)
     *   List<Order>    findByUserId(Long userId)
     *   Page<Order>    findByUser(User user, Pageable pageable)
     * OneToMany is skipped — the parent entity is queried from the child side.
     */
    private String buildRelationMethods(String entityClass, String entitySuffix,
                                        List<RelationshipSpec> relationships,
                                        Map<String, String> idTypeByEntity) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec rel : relationships) {
            if (!"ManyToOne".equals(rel.type) && !"OneToOne".equals(rel.type)) continue;

            String relEntity    = rel.target + entitySuffix;               // e.g. User (no suffix usually)
            String fieldName    = rel.fieldName;                            // e.g. "user"
            String capitalField = capitalize(fieldName);                    // e.g. "User"
            String targetIdType = idTypeByEntity.getOrDefault(rel.target, "Long");

            sb.append("\n")
              .append("    /** Find all ").append(entityClass)
              .append(" records linked to a specific ").append(rel.target).append(". */\n")
              .append("    List<").append(entityClass).append("> findBy").append(capitalField)
              .append("(").append(relEntity).append(" ").append(fieldName).append(");\n")
              .append("\n")
              .append("    /** Find by ").append(rel.target).append(" id — avoids loading the parent object. */\n")
              .append("    List<").append(entityClass).append("> findBy").append(capitalField)
              .append("Id(").append(targetIdType).append(" ").append(fieldName).append("Id);\n")
              .append("\n")
              .append("    /** Paginated version for large result sets. */\n")
              .append("    org.springframework.data.domain.Page<").append(entityClass)
              .append("> findBy").append(capitalField).append("(")
              .append(relEntity).append(" ").append(fieldName)
              .append(", org.springframework.data.domain.Pageable pageable);\n");
        }
        return sb.toString();
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
}
