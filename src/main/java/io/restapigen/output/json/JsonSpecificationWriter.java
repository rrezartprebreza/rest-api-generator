package io.restapigen.output.json;

import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;
import io.restapigen.domain.RelationshipSpec;

import java.util.Iterator;
import java.util.List;

public final class JsonSpecificationWriter {
    private JsonSpecificationWriter() {}

    public static String writeApiSpecification(ApiSpecification spec, boolean pretty) {
        StringBuilder out = new StringBuilder(512);
        Indent indent = new Indent(pretty);
        writeObjectStart(out);
        indent.down();
        indent.newline(out);

        writeField(out, indent, "projectName", spec.projectName, true);
        writeField(out, indent, "basePackage", spec.basePackage, true);

        writeName(out, indent, "entities");
        writeArrayStart(out);
        if (!spec.entities.isEmpty()) {
            indent.down();
            for (int i = 0; i < spec.entities.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                indent.newline(out);
                indent.write(out);
                writeEntityDefinition(out, indent, spec.entities.get(i));
            }
            indent.up();
            indent.newline(out);
            indent.write(out);
        }
        writeArrayEnd(out);
        out.append(',');
        indent.newline(out);

        writeName(out, indent, "suggestions");
        writeStringArray(out, indent, spec.suggestions);
        indent.newline(out);

        indent.up();
        indent.newline(out);
        writeObjectEnd(out);
        if (pretty) {
            out.append('\n');
        }
        return out.toString();
    }

    private static void writeEntityDefinition(StringBuilder out, Indent indent, EntityDefinition definition) {
        writeObjectStart(out);
        indent.down();
        indent.newline(out);

        writeName(out, indent, "entity");
        writeObjectStart(out);
        indent.down();
        indent.newline(out);
        writeField(out, indent, "name", definition.entity.name, true);
        writeField(out, indent, "table", definition.entity.table, true);
        writeField(out, indent, "idType", definition.entity.idType, true);

        writeName(out, indent, "fields");
        writeArrayStart(out);
        if (!definition.entity.fields.isEmpty()) {
            indent.down();
            for (int i = 0; i < definition.entity.fields.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                indent.newline(out);
                indent.write(out);
                writeFieldObject(out, indent, definition.entity.fields.get(i));
            }
            indent.up();
            indent.newline(out);
            indent.write(out);
        }
        writeArrayEnd(out);

        indent.up();
        indent.newline(out);
        indent.write(out);
        writeObjectEnd(out);
        out.append(',');
        indent.newline(out);

        writeName(out, indent, "api");
        writeObjectStart(out);
        indent.down();
        indent.newline(out);
        writeApiObject(out, indent, definition.api);
        indent.up();
        indent.newline(out);
        indent.write(out);
        writeObjectEnd(out);
        out.append(',');
        indent.newline(out);

        writeName(out, indent, "relationships");
        writeRelationshipArray(out, indent, definition.relationships);

        indent.up();
        indent.newline(out);
        indent.write(out);
        writeObjectEnd(out);
    }

    private static void writeRelationshipArray(StringBuilder out, Indent indent, List<RelationshipSpec> relationships) {
        writeArrayStart(out);
        if (!relationships.isEmpty()) {
            indent.down();
            for (int i = 0; i < relationships.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                indent.newline(out);
                indent.write(out);
                writeObjectStart(out);
                indent.down();
                indent.newline(out);
                writeField(out, indent, "type", relationships.get(i).type, true);
                writeField(out, indent, "target", relationships.get(i).target, true);
                writeName(out, indent, "fieldName");
                writeString(out, relationships.get(i).fieldName);
                indent.up();
                indent.newline(out);
                indent.write(out);
                writeObjectEnd(out);
            }
            indent.up();
            indent.newline(out);
            indent.write(out);
        }
        writeArrayEnd(out);
    }

    private static void writeApiObject(StringBuilder out, Indent indent, ApiSpec api) {
        writeField(out, indent, "resourcePath", api.resourcePath, true);
        writeField(out, indent, "crud", api.crud, true);
        writeField(out, indent, "pagination", api.pagination, true);
        writeLastField(out, indent, "sorting", api.sorting);
    }

    private static void writeFieldObject(StringBuilder out, Indent indent, FieldSpec field) {
        writeObjectStart(out);
        indent.down();
        indent.newline(out);
        writeField(out, indent, "name", field.name, true);
        writeField(out, indent, "type", field.type, true);

        writeName(out, indent, "validation");
        writeStringArray(out, indent, field.validation);
        out.append(',');
        indent.newline(out);

        writeField(out, indent, "unique", field.unique, true);
        writeField(out, indent, "nullable", field.nullable, true);
        writeNullableIntegerField(out, indent, "min", field.min, true);
        writeNullableIntegerField(out, indent, "max", field.max, true);
        writeNullableStringField(out, indent, "format", field.format, true);
        writeField(out, indent, "encrypted", field.encrypted, true);

        writeName(out, indent, "enumValues");
        writeStringArray(out, indent, field.enumValues);
        out.append(',');
        indent.newline(out);

        writeNullableStringField(out, indent, "defaultValue", field.defaultValue, true);
        writeNullableStringField(out, indent, "calculatedExpression", field.calculatedExpression, false);

        indent.up();
        indent.newline(out);
        indent.write(out);
        writeObjectEnd(out);
    }

    private static void writeStringArray(StringBuilder out, Indent indent, List<String> values) {
        writeArrayStart(out);
        if (!values.isEmpty()) {
            indent.down();
            Iterator<String> it = values.iterator();
            while (it.hasNext()) {
                indent.newline(out);
                indent.write(out);
                writeString(out, it.next());
                if (it.hasNext()) {
                    out.append(',');
                }
            }
            indent.up();
            indent.newline(out);
            indent.write(out);
        }
        writeArrayEnd(out);
    }

    private static void writeField(StringBuilder out, Indent indent, String name, String value, boolean trailingComma) {
        writeName(out, indent, name);
        writeString(out, value);
        if (trailingComma) {
            out.append(',');
        }
        indent.newline(out);
    }

    private static void writeField(StringBuilder out, Indent indent, String name, boolean value, boolean trailingComma) {
        writeName(out, indent, name);
        out.append(value);
        if (trailingComma) {
            out.append(',');
        }
        indent.newline(out);
    }

    private static void writeLastField(StringBuilder out, Indent indent, String name, boolean value) {
        writeName(out, indent, name);
        out.append(value);
    }

    private static void writeNullableIntegerField(
            StringBuilder out,
            Indent indent,
            String name,
            Integer value,
            boolean trailingComma
    ) {
        writeName(out, indent, name);
        if (value == null) {
            out.append("null");
        } else {
            out.append(value);
        }
        if (trailingComma) {
            out.append(',');
        }
        indent.newline(out);
    }

    private static void writeNullableStringField(
            StringBuilder out,
            Indent indent,
            String name,
            String value,
            boolean trailingComma
    ) {
        writeName(out, indent, name);
        if (value == null) {
            out.append("null");
        } else {
            writeString(out, value);
        }
        if (trailingComma) {
            out.append(',');
        }
        indent.newline(out);
    }

    private static void writeName(StringBuilder out, Indent indent, String name) {
        indent.write(out);
        writeString(out, name);
        out.append(indent.pretty ? ": " : ":");
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c <= 0x1F) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
        }
        out.append('"');
    }

    private static void writeObjectStart(StringBuilder out) {
        out.append('{');
    }

    private static void writeObjectEnd(StringBuilder out) {
        out.append('}');
    }

    private static void writeArrayStart(StringBuilder out) {
        out.append('[');
    }

    private static void writeArrayEnd(StringBuilder out) {
        out.append(']');
    }

    private static final class Indent {
        final boolean pretty;
        private int level = 0;

        private Indent(boolean pretty) {
            this.pretty = pretty;
        }

        void down() {
            level++;
        }

        void up() {
            level = Math.max(0, level - 1);
        }

        void write(StringBuilder out) {
            if (!pretty) {
                return;
            }
            out.append("  ".repeat(level));
        }

        void newline(StringBuilder out) {
            if (pretty) {
                out.append('\n');
            }
        }
    }
}
