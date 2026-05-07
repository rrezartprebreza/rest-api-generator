package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;
import io.restapigen.domain.RelationshipSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Map<String, String> idTypeByEntity = idTypeByEntity(specification);

        for (EntityDefinition definition : specification.entities) {
            String entityName       = definition.entity.name;
            String entityClass      = entityName + entitySuffix;
            String className        = entityName + suffix;
            String repositoryClass  = entityName + repositorySuffix;
            String mapperClass      = entityName + "Mapper";
            String dtoClass         = entityName + dtoSuffix;

            String filterPredicates       = buildFilterPredicates(definition.entity.fields);
            String relationServiceImports = buildRelationServiceImports(basePackage, entitySuffix, repositorySuffix, definition, idTypeByEntity);
            String relationServiceMethods = buildRelationServiceMethods(entityClass, dtoClass, entitySuffix, definition.relationships, idTypeByEntity);
            String relatedRepositoryFields = buildRelatedRepositoryFields(repositorySuffix, definition.relationships);
            String relatedRepositoryConstructorParams = buildRelatedRepositoryConstructorParams(repositorySuffix, definition.relationships);
            String relatedRepositoryAssignments = buildRelatedRepositoryAssignments(definition.relationships);
            String applyRelationshipsMethod = buildApplyRelationshipsMethod(dtoClass, entityClass, entitySuffix, repositorySuffix, definition.relationships);
            String applyRelationshipsCall = applyRelationshipsMethod.isBlank() ? "" : "        applyRelationships(dto, entity);\n";

            String content = context.templates().render(
                    context.templatePack().templatePath("service.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage",             basePackage),
                            Map.entry("entityName",              entityName),
                            Map.entry("entityClass",             entityClass),
                            Map.entry("className",               className),
                            Map.entry("idType",                  definition.entity.idType),
                            Map.entry("repositoryClass",         repositoryClass),
                            Map.entry("mapperClass",             mapperClass),
                            Map.entry("dtoClass",                dtoClass),
                            Map.entry("filterPredicates",        filterPredicates),
                            Map.entry("relationServiceImports",  relationServiceImports),
                            Map.entry("relationServiceMethods",  relationServiceMethods),
                            Map.entry("relatedRepositoryFields", relatedRepositoryFields),
                            Map.entry("relatedRepositoryConstructorParams", relatedRepositoryConstructorParams),
                            Map.entry("relatedRepositoryAssignments", relatedRepositoryAssignments),
                            Map.entry("applyRelationshipsCall", applyRelationshipsCall),
                            Map.entry("applyRelationshipsMethod", applyRelationshipsMethod)
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
                                               String repositorySuffix,
                                               EntityDefinition definition,
                                               Map<String, String> idTypeByEntity) {
        Set<String> imports = new LinkedHashSet<>();
        TemplateSupport.addTypeImport(imports, definition.entity.idType);
        definition.relationships.stream()
                .filter(r -> "ManyToOne".equals(r.type) || "OneToOne".equals(r.type))
                .map(r -> "import " + basePackage + ".entity." + r.target + entitySuffix + ";")
                .distinct()
                .forEach(imports::add);
        writableRelationships(definition.relationships).stream()
                .map(r -> "import " + basePackage + ".entity." + r.target + entitySuffix + ";")
                .distinct()
                .forEach(imports::add);
        writableRelationships(definition.relationships).stream()
                .map(r -> "import " + basePackage + ".repository." + r.target + repositorySuffix + ";")
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

    private String buildRelationServiceMethods(String entityClass, String dtoClass,
                                               String entitySuffix,
                                               List<RelationshipSpec> relationships,
                                               Map<String, String> idTypeByEntity) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec rel : relationships) {
            if (!"ManyToOne".equals(rel.type) && !"OneToOne".equals(rel.type)) continue;
            String relEntity    = rel.target + entitySuffix;
            String fieldName    = rel.fieldName;
            String capitalField = capitalize(fieldName);
            String targetIdType = idTypeByEntity.getOrDefault(rel.target, "Long");

            sb.append("\n")
              .append("    /** Get all ").append(entityClass).append(" records for a given ")
              .append(rel.target).append(" id. */\n")
              .append("    public List<").append(dtoClass).append("> findBy").append(capitalField)
              .append("Id(").append(targetIdType).append(" ").append(fieldName).append("Id) {\n")
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

    private String buildRelatedRepositoryFields(String repositorySuffix, List<RelationshipSpec> relationships) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec relationship : writableRelationships(relationships)) {
            sb.append("    private final ").append(relationship.target).append(repositorySuffix).append(" ")
                    .append(decapitalize(relationship.target)).append("Repository;\n");
        }
        return sb.toString();
    }

    private String buildRelatedRepositoryConstructorParams(String repositorySuffix, List<RelationshipSpec> relationships) {
        return writableRelationships(relationships).stream()
                .map(r -> ", " + r.target + repositorySuffix + " " + decapitalize(r.target) + "Repository")
                .collect(Collectors.joining());
    }

    private String buildRelatedRepositoryAssignments(List<RelationshipSpec> relationships) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec relationship : writableRelationships(relationships)) {
            String repositoryName = decapitalize(relationship.target) + "Repository";
            sb.append("        this.").append(repositoryName).append(" = ").append(repositoryName).append(";\n");
        }
        return sb.toString();
    }

    private String buildApplyRelationshipsMethod(String dtoClass, String entityClass, String entitySuffix,
                                                 String repositorySuffix, List<RelationshipSpec> relationships) {
        List<RelationshipSpec> writable = writableRelationships(relationships);
        if (writable.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("    private void applyRelationships(").append(dtoClass).append(" dto, ")
                .append(entityClass).append(" entity) {\n");
        for (RelationshipSpec relationship : writable) {
            String fieldName = relationship.fieldName;
            String capitalField = capitalize(fieldName);
            String targetClass = relationship.target + entitySuffix;
            String repositoryName = decapitalize(relationship.target) + "Repository";
            if ("ManyToOne".equals(relationship.type) || "OneToOne".equals(relationship.type)) {
                sb.append("        if (dto.get").append(capitalField).append("Id() != null) {\n")
                        .append("            ").append(targetClass).append(" ").append(fieldName)
                        .append(" = ").append(repositoryName).append(".findById(dto.get")
                        .append(capitalField).append("Id())\n")
                        .append("                    .orElseThrow(() -> new ResourceNotFoundException(\"")
                        .append(relationship.target).append("\", \"id\", dto.get").append(capitalField).append("Id()));\n")
                        .append("            entity.set").append(capitalField).append("(").append(fieldName).append(");\n")
                        .append("        } else {\n")
                        .append("            entity.set").append(capitalField).append("(null);\n")
                        .append("        }\n");
            } else if ("ManyToMany".equals(relationship.type)) {
                String idsGetter = "get" + capitalField + "Ids()";
                sb.append("        if (dto.").append(idsGetter).append(" != null) {\n")
                        .append("            List<").append(targetClass).append("> ").append(fieldName)
                        .append(" = ").append(repositoryName).append(".findAllById(dto.").append(idsGetter).append(");\n")
                        .append("            if (").append(fieldName).append(".size() != dto.").append(idsGetter).append(".size()) {\n")
                        .append("                throw new ResourceNotFoundException(\"").append(relationship.target)
                        .append("\", \"ids\", dto.").append(idsGetter).append(");\n")
                        .append("            }\n")
                        .append("            entity.set").append(capitalField).append("(").append(fieldName).append(");\n")
                        .append("        }\n");
            }
        }
        sb.append("    }\n\n");
        return sb.toString();
    }

    private List<RelationshipSpec> writableRelationships(List<RelationshipSpec> relationships) {
        return relationships.stream()
                .filter(r -> "ManyToOne".equals(r.type) || "OneToOne".equals(r.type) || "ManyToMany".equals(r.type))
                .toList();
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

    private static String decapitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
