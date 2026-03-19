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
import java.util.Set;
import java.util.stream.Collectors;

public final class EntityGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "entity-generator";
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
        boolean lombokModels = context.config().features().lombokModels();
        for (EntityDefinition definition : specification.entities) {
            String className = definition.entity.name + context.config().standards().naming().entitySuffix();
            Set<String> imports = TemplateSupport.collectEntityImports(definition.entity.fields, definition.relationships);
            if (lombokModels) {
                imports.add("lombok.Getter");
                imports.add("lombok.Setter");
            }
            String content = context.templates().render(
                    context.templatePack().templatePath("entity.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage", basePackage),
                            Map.entry("entityName", definition.entity.name),
                            Map.entry("className", className),
                            Map.entry("tableName", definition.entity.table),
                            Map.entry("imports", imports.stream().map(it -> "import " + it + ";").collect(Collectors.joining("\n"))),
                            Map.entry("idFieldBlock", TemplateSupport.idFieldBlock(definition.entity.idType)),
                            Map.entry("fieldsBlock", TemplateSupport.fieldsBlock(definition.entity.fields)),
                            Map.entry("relationshipBlock", TemplateSupport.relationshipBlock(definition.entity.name, definition.relationships)),
                            Map.entry("classAnnotations", lombokModels ? "@Getter\n@Setter\n" : ""),
                            Map.entry("noArgConstructorBlock", TemplateSupport.noArgConstructorBlock(className)),
                            Map.entry("constructorBlock", TemplateSupport.constructorBlock(className, definition.entity.fields)),
                            Map.entry("gettersBlock", lombokModels ? "" : TemplateSupport.entityGettersBlock(definition.entity.idType, definition.entity.fields))
                    )
            );
            out.add(new GeneratedFile(javaBase + "/entity/" + className + ".java", content));
            for (FieldSpec field : definition.entity.fields) {
                if (field.enumValues.isEmpty()) {
                    continue;
                }
                String enumContent = buildEnumContent(basePackage, field.type, field.enumValues);
                out.add(new GeneratedFile(javaBase + "/entity/" + field.type + ".java", enumContent));
            }
        }
        return out;
    }

    private String buildEnumContent(String basePackage, String enumName, List<String> values) {
        String constants = values.stream()
                .map(value -> value.replaceAll("[^A-Za-z0-9_]", "").toUpperCase())
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(",\n    "));
        return "package " + basePackage + ".entity;\n\n"
                + "public enum " + enumName + " {\n"
                + "    " + constants + "\n"
                + "}\n";
    }
}
