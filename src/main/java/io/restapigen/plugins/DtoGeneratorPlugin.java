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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class DtoGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "dto-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage = context.config().project().basePackage();
        String javaBase = "src/main/java/" + context.basePackagePath();
        String dtoSuffix = context.config().standards().naming().dtoSuffix();
        boolean lombokModels = context.config().features().lombokModels();
        Map<String, String> idTypeByEntity = idTypeByEntity(specification);
        for (EntityDefinition definition : specification.entities) {
            String className = definition.entity.name + dtoSuffix;
            StringBuilder body = new StringBuilder();
            List<FieldSpec> dtoFields = dtoFields(definition, idTypeByEntity);
            Set<String> imports = TemplateSupport.collectImports(dtoFields);
            TemplateSupport.addTypeImport(imports, definition.entity.idType);
            for (FieldSpec field : dtoFields) {
                if (!field.enumValues.isEmpty()) {
                    imports.add(basePackage + ".entity." + field.type);
                }
            }
            if (lombokModels) {
                imports.add("lombok.AllArgsConstructor");
                imports.add("lombok.Getter");
                imports.add("lombok.NoArgsConstructor");
                imports.add("lombok.Setter");
            }
            body.append("    private ").append(definition.entity.idType).append(" id;\n\n");
            for (FieldSpec field : definition.entity.fields) {
                for (String validation : field.validation) {
                    body.append("    @").append(validationToAnnotation(validation)).append("\n");
                }
                body.append("    private ").append(field.type).append(" ").append(field.name).append(";\n\n");
            }
            for (FieldSpec field : relationshipIdFields(definition.relationships, idTypeByEntity)) {
                body.append("    private ").append(field.type).append(" ").append(field.name).append(";\n\n");
            }
            if (!lombokModels) {
                body.append(TemplateSupport.noArgConstructorBlock(className));
                body.append(TemplateSupport.constructorBlock(className, dtoFields));
                body.append(TemplateSupport.gettersBlock(dtoFields));
                body.append(TemplateSupport.settersBlock(dtoFields));
            }
            String content = context.templates().render(
                    context.templatePack().templatePath("dto.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage", basePackage),
                            Map.entry("className", className),
                            Map.entry("classAnnotations", lombokModels ? "@Getter\n@Setter\n@NoArgsConstructor\n@AllArgsConstructor\n" : ""),
                            Map.entry("imports", imports.stream().map(it -> "import " + it + ";").collect(Collectors.joining("\n"))),
                            Map.entry("fieldsBlock", body.toString()),
                            Map.entry("constructorBlock", ""),
                            Map.entry("gettersBlock", "")
                    )
            );
            out.add(new GeneratedFile(javaBase + "/dto/" + className + ".java", content));
        }
        return out;
    }

    private Map<String, String> idTypeByEntity(ApiSpecification specification) {
        Map<String, String> idTypes = new HashMap<>();
        for (EntityDefinition definition : specification.entities) {
            idTypes.put(definition.entity.name, definition.entity.idType);
        }
        return idTypes;
    }

    private List<FieldSpec> dtoFields(EntityDefinition definition, Map<String, String> idTypeByEntity) {
        List<FieldSpec> fields = new ArrayList<>();
        fields.add(new FieldSpec("id", definition.entity.idType, List.of(), true, true, null, null, null, false, List.of(), null, null));
        fields.addAll(definition.entity.fields);
        fields.addAll(relationshipIdFields(definition.relationships, idTypeByEntity));
        return fields;
    }

    private List<FieldSpec> relationshipIdFields(List<RelationshipSpec> relationships, Map<String, String> idTypeByEntity) {
        List<FieldSpec> fields = new ArrayList<>();
        for (RelationshipSpec relationship : relationships) {
            String targetIdType = idTypeByEntity.getOrDefault(relationship.target, "Long");
            String fieldName = switch (relationship.type) {
                case "ManyToOne", "OneToOne" -> relationship.fieldName + "Id";
                case "OneToMany", "ManyToMany" -> relationship.fieldName + "Ids";
                default -> null;
            };
            if (fieldName == null) {
                continue;
            }
            String fieldType = switch (relationship.type) {
                case "OneToMany", "ManyToMany" -> "List<" + targetIdType + ">";
                default -> targetIdType;
            };
            fields.add(new FieldSpec(fieldName, fieldType, List.of(), false, true, null, null, null, false, List.of(), null, null));
        }
        return fields;
    }

    private String validationToAnnotation(String token) {
        if (token.startsWith("Size:")) {
            String[] parts = token.split(":");
            if (parts.length == 3) {
                return "Size(min = " + parts[1] + ", max = " + parts[2] + ")";
            }
        }
        if (token.startsWith("Min:")) {
            String[] parts = token.split(":");
            if (parts.length == 2) {
                return "Min(" + parts[1] + ")";
            }
        }
        if (token.startsWith("Max:")) {
            String[] parts = token.split(":");
            if (parts.length == 2) {
                return "Max(" + parts[1] + ")";
            }
        }
        if (token.startsWith("DecimalMin:")) {
            String[] parts = token.split(":");
            if (parts.length == 2) {
                return "DecimalMin(\"" + parts[1] + "\")";
            }
        }
        if (token.startsWith("DecimalMax:")) {
            String[] parts = token.split(":");
            if (parts.length == 2) {
                return "DecimalMax(\"" + parts[1] + "\")";
            }
        }
        if (token.startsWith("OneOf:")) {
            String[] parts = token.split(":", 2);
            if (parts.length == 2) {
                return "Pattern(regexp = \"^(" + parts[1] + ")$\")";
            }
        }
        return token;
    }
}
