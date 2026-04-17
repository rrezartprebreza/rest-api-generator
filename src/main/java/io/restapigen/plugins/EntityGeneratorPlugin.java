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
    @Override public String getName()    { return "entity-generator"; }
    @Override public String getVersion() { return "1.0.0"; }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        List<GeneratedFile> out  = new ArrayList<>();
        String basePackage       = context.config().project().basePackage();
        String javaBase          = "src/main/java/" + context.basePackagePath();
        boolean lombokModels     = context.config().features().lombokModels();
        boolean auditing         = context.config().features().auditing();

        for (EntityDefinition definition : specification.entities) {
            String className = definition.entity.name + context.config().standards().naming().entitySuffix();

            // Filter out 'id' — managed by @Id / @GeneratedValue in idFieldBlock.
            // Also filter out createdAt/updatedAt when auditing is on — buildAuditBlock() owns those
            // fields with @CreatedDate/@LastModifiedDate; keeping them in fieldsBlock causes duplicate
            // declarations and compile errors in the generated project.
            List<FieldSpec> entityFields = definition.entity.fields.stream()
                    .filter(f -> !"id".equals(f.name))
                    .filter(f -> !auditing || (!"createdAt".equals(f.name) && !"updatedAt".equals(f.name)))
                    .collect(Collectors.toList());

            Set<String> imports = TemplateSupport.collectEntityImports(entityFields, definition.relationships);

            if (auditing) {
                imports.add("org.springframework.data.annotation.CreatedDate");
                imports.add("org.springframework.data.annotation.LastModifiedDate");
                imports.add("org.springframework.data.jpa.domain.support.AuditingEntityListener");
                imports.add("jakarta.persistence.EntityListeners");
                imports.add("jakarta.persistence.Column");   // used by @Column in audit block
                imports.add("java.time.Instant");
            }
            if (lombokModels) {
                imports.add("lombok.Getter");
                imports.add("lombok.Setter");
            }

            String auditBlock = auditing ? buildAuditBlock() : "";
            String classAnnotations = buildClassAnnotations(lombokModels, auditing);
            // Audit getters/setters (not needed when Lombok @Getter/@Setter is active)
            String auditAccessors = (!lombokModels && auditing) ? buildAuditAccessors() : "";

            String content = context.templates().render(
                    context.templatePack().templatePath("entity.java.tpl"),
                    Map.ofEntries(
                            Map.entry("basePackage",          basePackage),
                            Map.entry("entityName",           definition.entity.name),
                            Map.entry("className",            className),
                            Map.entry("tableName",            definition.entity.table),
                            Map.entry("imports",              imports.stream().map(it -> "import " + it + ";").collect(Collectors.joining("\n"))),
                            Map.entry("idFieldBlock",         TemplateSupport.idFieldBlock(definition.entity.idType)),
                            Map.entry("auditBlock",           auditBlock),
                            Map.entry("fieldsBlock",          TemplateSupport.fieldsBlock(entityFields)),
                            Map.entry("relationshipBlock",    TemplateSupport.relationshipBlock(definition.entity.name, definition.relationships)),
                            Map.entry("classAnnotations",     classAnnotations),
                            Map.entry("noArgConstructorBlock",TemplateSupport.noArgConstructorBlock(className)),
                            Map.entry("constructorBlock",     TemplateSupport.constructorBlock(className, entityFields)),
                            Map.entry("gettersBlock",         lombokModels ? "" : TemplateSupport.entityGettersBlock(definition.entity.idType, entityFields) + auditAccessors)
                    )
            );
            out.add(new GeneratedFile(javaBase + "/entity/" + className + ".java", content));

            // Generate enum classes (use original fields, not filtered)
            for (FieldSpec field : definition.entity.fields) {
                if (field.enumValues.isEmpty()) continue;
                out.add(new GeneratedFile(
                        javaBase + "/entity/" + field.type + ".java",
                        buildEnumContent(basePackage, field.type, field.enumValues)
                ));
            }
        }
        return out;
    }

    private String buildAuditBlock() {
        return """
                    @CreatedDate
                    @Column(name = "created_at", nullable = false, updatable = false)
                    private Instant createdAt;

                    @LastModifiedDate
                    @Column(name = "updated_at")
                    private Instant updatedAt;

                """;
    }

    /** Getters and setters for the audit fields createdAt / updatedAt (non-Lombok path). */
    private String buildAuditAccessors() {
        return "    public Instant getCreatedAt() {\n"
                + "        return createdAt;\n"
                + "    }\n\n"
                + "    public Instant getUpdatedAt() {\n"
                + "        return updatedAt;\n"
                + "    }\n\n";
    }

    private String buildClassAnnotations(boolean lombok, boolean auditing) {
        StringBuilder sb = new StringBuilder();
        if (auditing) sb.append("@EntityListeners(AuditingEntityListener.class)\n");
        if (lombok)   sb.append("@Getter\n@Setter\n");
        return sb.toString();
    }

    private String buildEnumContent(String basePackage, String enumName, List<String> values) {
        String constants = values.stream()
                .map(v -> v.replaceAll("[^A-Za-z0-9_]", "").toUpperCase())
                .filter(v -> !v.isBlank())
                .collect(Collectors.joining(",\n    "));
        return "package " + basePackage + ".entity;\n\n"
                + "public enum " + enumName + " {\n"
                + "    " + constants + "\n"
                + "}\n";
    }
}
