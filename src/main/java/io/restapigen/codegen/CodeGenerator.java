package io.restapigen.codegen;

import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CodeGenerator {
    private static final Map<String, String> TYPE_IMPORTS = Map.of(
            "BigDecimal", "java.math.BigDecimal",
            "LocalDate", "java.time.LocalDate",
            "LocalDateTime", "java.time.LocalDateTime"
    );

    public byte[] generateZip(ApiSpecification spec) throws IOException {
        String basePackagePath = spec.basePackage.replace('.', '/');
        String javaBase = "src/main/java/" + basePackagePath;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer)) {
            addEntry(zip, "README.md", buildReadme(spec));
            for (EntityDefinition definition : spec.entities) {
                String entityName = definition.entity.name;
                addEntry(zip, javaBase + "/entity/" + entityName + ".java", buildEntity(spec, definition));
                addEntry(zip, javaBase + "/dto/" + entityName + "Dto.java", buildDto(spec, definition));
                addEntry(zip, javaBase + "/repository/" + entityName + "Repository.java", buildRepository(spec, definition));
                addEntry(zip, javaBase + "/controller/" + entityName + "Controller.java", buildController(spec, definition));
            }
            zip.finish();
            return buffer.toByteArray();
        }
    }

    private void addEntry(ZipOutputStream zip, String name, String body) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String buildReadme(ApiSpecification spec) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(spec.projectName).append("\n\n");
        out.append("Generated REST API entries from ").append(spec.basePackage).append("\n\n");
        for (EntityDefinition definition : spec.entities) {
            out.append("## ").append(definition.entity.name).append("\n");
            out.append("- Resource path: ").append(definition.api.resourcePath).append("\n");
            out.append("- CRUD: ").append(definition.api.crud).append("\n");
            out.append("- Pagination: ").append(definition.api.pagination).append("\n");
            out.append("- Sorting: ").append(definition.api.sorting).append("\n");
            out.append("### Fields\n");
            for (FieldSpec field : definition.entity.fields) {
                out.append("- `").append(field.name).append("` (").append(field.type).append(")");
                out.append(field.unique ? " unique" : "");
                out.append(field.nullable ? " nullable" : " required");
                if (!field.validation.isEmpty()) {
                    out.append(" [").append(String.join(", ", field.validation)).append("]");
                }
                out.append("\n");
            }
            out.append("\n");
        }
        if (!spec.suggestions.isEmpty()) {
            out.append("## Suggestions\n");
            for (String suggestion : spec.suggestions) {
                out.append("- ").append(suggestion).append("\n");
            }
            out.append("\n");
        }
        return out.toString();
    }

    private String buildEntity(ApiSpecification spec, EntityDefinition definition) {
        String entityName = definition.entity.name;
        String pkg = spec.basePackage + ".entity";
        Set<String> imports = collectImports(definition.entity.fields);
        return buildClass(pkg, entityName, imports, buildFields(definition.entity.fields), buildConstructor(entityName, definition.entity.fields), buildGetters(definition.entity.fields));
    }

    private String buildDto(ApiSpecification spec, EntityDefinition definition) {
        String dtoName = definition.entity.name + "Dto";
        String pkg = spec.basePackage + ".dto";
        String fields = buildFields(definition.entity.fields);
        String constructor = buildDtoConstructor(dtoName, definition.entity.fields);
        String getters = buildGetters(definition.entity.fields);
        return buildClass(pkg, dtoName, Set.of(), fields, constructor, getters);
    }

    private String buildRepository(ApiSpecification spec, EntityDefinition definition) {
        String pkg = spec.basePackage + ".repository";
        String entityName = definition.entity.name;
        StringBuilder body = new StringBuilder();
        body.append("    private final List<").append(entityName).append("> store = new ArrayList<>();\n\n");
        body.append("    public List<").append(entityName).append("> findAll() {\n");
        body.append("        return new ArrayList<>(store);\n");
        body.append("    }\n\n");
        body.append("    public void save(").append(entityName).append(" entity) {\n");
        body.append("        store.add(entity);\n");
        body.append("    }\n");
        String imports = "import java.util.ArrayList;\nimport java.util.List;\n\n";
        String classBody = "public final class " + entityName + "Repository {\n\n" + body + "}";
        return new StringBuilder()
                .append("package ").append(pkg).append(";\n\n")
                .append(imports)
                .append(classBody)
                .append("\n")
                .toString();
    }

    private String buildController(ApiSpecification spec, EntityDefinition definition) {
        String pkg = spec.basePackage + ".controller";
        String entityName = definition.entity.name;
        String repository = spec.basePackage + ".repository." + entityName + "Repository";
        String entity = spec.basePackage + ".entity." + entityName;
        StringBuilder out = new StringBuilder();
        out.append("package ").append(pkg).append(";\n\n");
        out.append("import ").append(entity).append(";\n");
        out.append("import ").append(repository).append(";\n");
        out.append("import java.util.List;\n\n");
        out.append("public final class ").append(entityName).append("Controller {\n\n");
        out.append("    private final ").append(entityName).append("Repository repository;\n\n");
        out.append("    public ").append(entityName).append("Controller(").append(entityName).append("Repository repository) {\n");
        out.append("        this.repository = repository;\n");
        out.append("    }\n\n");
        out.append("    public List<").append(entityName).append("> list() {\n");
        out.append("        return repository.findAll();\n");
        out.append("    }\n\n");
        out.append("    public void create(").append(entityName).append(" toSave) {\n");
        out.append("        repository.save(toSave);\n");
        out.append("    }\n");
        out.append("}\n");
        return out.toString();
    }

    private String buildClass(String pkg, String className, Set<String> imports, String fields, String constructor, String getters) {
        StringBuilder out = new StringBuilder();
        out.append("package ").append(pkg).append(";\n\n");
        if (!imports.isEmpty()) {
            for (String imp : imports) {
                out.append("import ").append(imp).append(";\n");
            }
            out.append("\n");
        }
        out.append("public final class ").append(className).append(" {\n\n");
        out.append(fields);
        out.append(constructor);
        out.append(getters);
        out.append("}\n");
        return out.toString();
    }

    private Set<String> collectImports(List<FieldSpec> fields) {
        Set<String> imports = new LinkedHashSet<>();
        for (FieldSpec field : fields) {
            String target = TYPE_IMPORTS.get(field.type);
            if (target != null) {
                imports.add(target);
            }
        }
        return imports;
    }

    private String buildFields(List<FieldSpec> fields) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec field : fields) {
            out.append("    private final ").append(field.type).append(" ").append(field.name).append(";\n");
        }
        if (!fields.isEmpty()) {
            out.append("\n");
        }
        return out.toString();
    }

    private String buildConstructor(String className, List<FieldSpec> fields) {
        StringBuilder paramList = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            FieldSpec field = fields.get(i);
            if (i > 0) {
                paramList.append(", ");
            }
            paramList.append(field.type).append(" ").append(field.name);
            body.append("        this.").append(field.name).append(" = ").append(field.name).append(";\n");
        }
        return "    public " + className + "(" + paramList + ") {\n" +
                body +
                "    }\n\n";
    }

    private String buildDtoConstructor(String dtoName, List<FieldSpec> fields) {
        StringBuilder params = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            FieldSpec field = fields.get(i);
            if (i > 0) {
                params.append(", ");
            }
            params.append(field.type).append(" ").append(field.name);
            body.append("        this.").append(field.name).append(" = ").append(field.name).append(";\n");
        }
        return "    public " + dtoName + "(" + params + ") {\n" +
                body +
                "    }\n\n";
    }

    private String buildGetters(List<FieldSpec> fields) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec field : fields) {
            out.append("    public ").append(field.type).append(" get").append(capitalize(field.name)).append("() {\n");
            out.append("        return ").append(field.name).append(";\n");
            out.append("    }\n\n");
        }
        return out.toString();
    }

    private String capitalize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }
}
