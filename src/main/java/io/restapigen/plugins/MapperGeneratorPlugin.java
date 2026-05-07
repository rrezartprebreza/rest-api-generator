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
        Map<String, String> idTypeByEntity = idTypeByEntity(specification);

        for (EntityDefinition definition : specification.entities) {
            String entityName = definition.entity.name;
            String entityClass = entityName + entitySuffix;
            String className = entityName + "Mapper";
            String dtoClass = entityName + dtoSuffix;
            String toEntityRelationshipMappings = toEntityRelationshipMappings(definition.relationships);
            String toDtoRelationshipMappings = toDtoRelationshipMappings(definition.relationships);
            String mapperHelperMethods = mapperHelperMethods(entitySuffix, definition.relationships, idTypeByEntity);
            String extraImports = extraImports(basePackage, entitySuffix, definition.relationships, idTypeByEntity);
            String content = context.templates().render(
                    context.templatePack().templatePath("mapper.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage", basePackage),
                            Map.entry("entityName", entityClass),
                            Map.entry("className", className),
                            Map.entry("dtoClass", dtoClass),
                            Map.entry("extraImports", extraImports),
                            Map.entry("toEntityRelationshipMappings", toEntityRelationshipMappings),
                            Map.entry("toDtoRelationshipMappings", toDtoRelationshipMappings),
                            Map.entry("mapperHelperMethods", mapperHelperMethods)
                    )
            );
            out.add(new GeneratedFile(javaBase + "/mapper/" + className + ".java", content));
        }
        return out;
    }

    private String toEntityRelationshipMappings(List<RelationshipSpec> relationships) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec relationship : relationships) {
            if ("ManyToOne".equals(relationship.type) || "OneToOne".equals(relationship.type)
                    || "ManyToMany".equals(relationship.type) || "OneToMany".equals(relationship.type)) {
                sb.append("    @Mapping(target = \"").append(relationship.fieldName).append("\", ignore = true)\n");
            }
        }
        return sb.toString();
    }

    private String toDtoRelationshipMappings(List<RelationshipSpec> relationships) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec relationship : relationships) {
            String capitalField = capitalize(relationship.fieldName);
            switch (relationship.type) {
                case "ManyToOne", "OneToOne" -> sb.append("    @Mapping(target = \"")
                        .append(relationship.fieldName).append("Id\", source = \"")
                        .append(relationship.fieldName).append(".id\")\n");
                case "OneToMany", "ManyToMany" -> sb.append("    @Mapping(target = \"")
                        .append(relationship.fieldName).append("Ids\", expression = \"java(map")
                        .append(capitalField).append("Ids(entity.get").append(capitalField).append("()))\")\n");
                default -> {
                }
            }
        }
        return sb.toString();
    }

    private String mapperHelperMethods(String entitySuffix, List<RelationshipSpec> relationships, Map<String, String> idTypeByEntity) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec relationship : relationships) {
            if (!"OneToMany".equals(relationship.type) && !"ManyToMany".equals(relationship.type)) {
                continue;
            }
            String capitalField = capitalize(relationship.fieldName);
            String targetClass = relationship.target + entitySuffix;
            String targetIdType = idTypeByEntity.getOrDefault(relationship.target, "Long");
            sb.append("\n")
                    .append("    default java.util.List<").append(targetIdType).append("> map").append(capitalField)
                    .append("Ids(java.util.List<").append(targetClass).append("> values) {\n")
                    .append("        if (values == null) {\n")
                    .append("            return java.util.List.of();\n")
                    .append("        }\n")
                    .append("        return values.stream().map(").append(targetClass)
                    .append("::getId).toList();\n")
                    .append("    }\n");
        }
        return sb.toString();
    }

    private String extraImports(String basePackage, String entitySuffix, List<RelationshipSpec> relationships,
                                Map<String, String> idTypeByEntity) {
        Set<String> imports = new LinkedHashSet<>();
        for (RelationshipSpec relationship : relationships) {
            if ("OneToMany".equals(relationship.type) || "ManyToMany".equals(relationship.type)) {
                imports.add(basePackage + ".entity." + relationship.target + entitySuffix);
                TemplateSupport.addTypeImport(imports, idTypeByEntity.getOrDefault(relationship.target, "Long"));
            }
        }
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

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
