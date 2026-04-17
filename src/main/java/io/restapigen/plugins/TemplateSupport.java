package io.restapigen.plugins;

import io.restapigen.domain.FieldSpec;
import io.restapigen.domain.RelationshipSpec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TemplateSupport {
    private static final Map<String, String> TYPE_IMPORTS = Map.of(
            "BigDecimal", "java.math.BigDecimal",
            "LocalDate", "java.time.LocalDate",
            "LocalDateTime", "java.time.LocalDateTime",
            "Map", "java.util.Map",
            "List", "java.util.List"
    );

    private TemplateSupport() {
    }

    static Set<String> collectEntityImports(List<FieldSpec> fields, List<RelationshipSpec> relationships) {
        Set<String> imports = new LinkedHashSet<>();
        // Always needed
        imports.add("jakarta.persistence.Entity");
        imports.add("jakarta.persistence.Table");
        imports.add("jakarta.persistence.Id");
        imports.add("jakarta.persistence.GeneratedValue");
        imports.add("jakarta.persistence.GenerationType");

        // Relationship-specific imports — only add what is actually used
        if (!relationships.isEmpty()) {
            for (RelationshipSpec rel : relationships) {
                imports.add("jakarta.persistence." + rel.type);
            }
            imports.add("jakarta.persistence.JoinColumn");
            boolean hasManyToMany = relationships.stream().anyMatch(r -> "ManyToMany".equals(r.type));
            if (hasManyToMany) imports.add("jakarta.persistence.JoinTable");
            boolean hasFetchLazy = relationships.stream().anyMatch(r -> "ManyToOne".equals(r.type) || "OneToOne".equals(r.type));
            if (hasFetchLazy) imports.add("jakarta.persistence.FetchType");
            boolean hasCascade = relationships.stream().anyMatch(r -> "OneToMany".equals(r.type) || "ManyToMany".equals(r.type));
            if (hasCascade) imports.add("jakarta.persistence.CascadeType");
        }
        if (hasCollectionRelationship(relationships)) {
            imports.add("java.util.List");
            imports.add("java.util.ArrayList");
        }

        // Field-specific imports — only when those annotation types are actually used
        boolean hasListField  = fields.stream().anyMatch(f -> f.type.startsWith("List<"));
        boolean hasEnumField  = fields.stream().anyMatch(f -> !f.enumValues.isEmpty());
        if (hasListField) {
            imports.add("jakarta.persistence.ElementCollection");
            imports.add("jakarta.persistence.CollectionTable");
            imports.add("jakarta.persistence.Column");
        }
        if (hasEnumField) {
            imports.add("jakarta.persistence.Enumerated");
            imports.add("jakarta.persistence.EnumType");
        }

        imports.addAll(collectImports(fields));
        return imports;
    }

    static Set<String> collectImports(List<FieldSpec> fields) {
        Set<String> imports = new LinkedHashSet<>();
        for (FieldSpec field : fields) {
            String target = TYPE_IMPORTS.get(rawType(field.type));
            if (target != null) {
                imports.add(target);
            }
        }
        return imports;
    }

    static String fieldsBlock(List<FieldSpec> fields) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec field : fields) {
            if (!field.enumValues.isEmpty()) {
                out.append("    @Enumerated(EnumType.STRING)\n");
            } else if (field.type.startsWith("List<")) {
                out.append("    @ElementCollection\n");
                out.append("    @CollectionTable(name = \"").append(toSnakeCase(field.name)).append("_items\")\n");
                out.append("    @Column(name = \"value\")\n");
            }
            out.append("    private ").append(field.type).append(" ").append(field.name).append(";\n");
        }
        if (!fields.isEmpty()) {
            out.append("\n");
        }
        return out.toString();
    }

    static String idFieldBlock(String idType) {
        return "    @Id\n"
                + "    @GeneratedValue(strategy = GenerationType.IDENTITY)\n"
                + "    private " + idType + " id;\n\n";
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

    static String settersBlock(List<FieldSpec> fields) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec field : fields) {
            out.append("    public void set")
                    .append(capitalize(field.name)).append("(")
                    .append(field.type).append(" ").append(field.name).append(") {\n")
                    .append("        this.").append(field.name).append(" = ").append(field.name).append(";\n")
                    .append("    }\n\n");
        }
        return out.toString();
    }

    static String entityGettersBlock(String idType, List<FieldSpec> fields) {
        return "    public " + idType + " getId() {\n"
                + "        return id;\n"
                + "    }\n\n"
                + "    public void setId(" + idType + " id) {\n"
                + "        this.id = id;\n"
                + "    }\n\n"
                + gettersBlock(fields)
                + settersBlock(fields);
    }

    static String noArgConstructorBlock(String className) {
        return "    protected " + className + "() {\n"
                + "    }\n\n";
    }

    static String relationshipBlock(String ownerClassName, List<RelationshipSpec> relationships) {
        StringBuilder out = new StringBuilder();
        for (RelationshipSpec relationship : relationships) {
            String target = relationship.target;
            String fieldName = relationship.fieldName;
            switch (relationship.type) {
                case "ManyToOne" -> {
                    out.append("    @ManyToOne(fetch = FetchType.LAZY)\n");
                    out.append("    @JoinColumn(name = \"").append(fieldName).append("_id\")\n");
                    out.append("    private ").append(target).append(" ").append(fieldName).append(";\n\n");
                }
                case "OneToOne" -> {
                    out.append("    @OneToOne(fetch = FetchType.LAZY)\n");
                    out.append("    @JoinColumn(name = \"").append(fieldName).append("_id\")\n");
                    out.append("    private ").append(target).append(" ").append(fieldName).append(";\n\n");
                }
                case "OneToMany" -> {
                    out.append("    @OneToMany(mappedBy = \"")
                            .append(decapitalize(ownerClassName))
                            .append("\", cascade = {CascadeType.PERSIST, CascadeType.MERGE})\n");
                    out.append("    private List<").append(target).append("> ").append(fieldName).append(" = new ArrayList<>();\n\n");
                }
                case "ManyToMany" -> {
                    String joinTable = toSnakeCase(ownerClassName) + "_" + toSnakeCase(target);
                    String sourceColumn = toSnakeCase(ownerClassName) + "_id";
                    String targetColumn = toSnakeCase(target) + "_id";
                    out.append("    @ManyToMany\n");
                    out.append("    @JoinTable(name = \"").append(joinTable).append("\",\n");
                    out.append("            joinColumns = @JoinColumn(name = \"").append(sourceColumn).append("\"),\n");
                    out.append("            inverseJoinColumns = @JoinColumn(name = \"").append(targetColumn).append("\"))\n");
                    out.append("    private List<").append(target).append("> ").append(fieldName).append(" = new ArrayList<>();\n\n");
                }
                default -> {
                }
            }
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

    private static boolean hasCollectionRelationship(List<RelationshipSpec> relationships) {
        for (RelationshipSpec relationship : relationships) {
            if ("OneToMany".equals(relationship.type) || "ManyToMany".equals(relationship.type)) {
                return true;
            }
        }
        return false;
    }

    private static String decapitalize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return Character.toLowerCase(input.charAt(0)) + input.substring(1);
    }

    private static String toSnakeCase(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return input
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    private static String rawType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return typeName;
        }
        int genericIndex = typeName.indexOf('<');
        if (genericIndex > 0) {
            return typeName.substring(0, genericIndex);
        }
        return typeName;
    }
}
