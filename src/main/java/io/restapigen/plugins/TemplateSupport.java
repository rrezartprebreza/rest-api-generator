package io.restapigen.plugins;

import io.restapigen.domain.FieldSpec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TemplateSupport {
    private static final Map<String, String> TYPE_IMPORTS = Map.of(
            "BigDecimal", "java.math.BigDecimal",
            "LocalDate", "java.time.LocalDate",
            "LocalDateTime", "java.time.LocalDateTime"
    );

    private TemplateSupport() {
    }

    static Set<String> collectImports(List<FieldSpec> fields) {
        Set<String> imports = new LinkedHashSet<>();
        for (FieldSpec field : fields) {
            String target = TYPE_IMPORTS.get(field.type);
            if (target != null) {
                imports.add(target);
            }
        }
        return imports;
    }

    static String fieldsBlock(List<FieldSpec> fields) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec field : fields) {
            out.append("    private ").append(field.type).append(" ").append(field.name).append(";\n");
        }
        if (!fields.isEmpty()) {
            out.append("\n");
        }
        return out.toString();
    }

    static String constructorBlock(String className, List<FieldSpec> fields) {
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
        return "    public " + className + "(" + params + ") {\n" + body + "    }\n\n";
    }

    static String gettersBlock(List<FieldSpec> fields) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec field : fields) {
            out.append("    public ").append(field.type).append(" get")
                    .append(capitalize(field.name)).append("() {\n")
                    .append("        return ").append(field.name).append(";\n")
                    .append("    }\n\n");
        }
        return out.toString();
    }

    static String classFile(String pkg, String className, Set<String> imports, String body) {
        StringBuilder out = new StringBuilder();
        out.append("package ").append(pkg).append(";\n\n");
        if (!imports.isEmpty()) {
            for (String imp : imports) {
                out.append("import ").append(imp).append(";\n");
            }
            out.append("\n");
        }
        out.append("public class ").append(className).append(" {\n\n");
        out.append(body);
        out.append("}\n");
        return out.toString();
    }

    static String capitalize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }
}
