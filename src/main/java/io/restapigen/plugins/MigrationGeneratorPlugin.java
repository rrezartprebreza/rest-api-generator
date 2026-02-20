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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class MigrationGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "migration-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        String tool = context.config().standards().database().migrationTool().toLowerCase(Locale.ROOT);
        if ("none".equals(tool)) {
            return List.of();
        }
        List<GeneratedFile> out = new ArrayList<>();
        Map<String, String> tableByEntity = buildTableMap(specification);
        int version = 1;
        for (EntityDefinition definition : specification.entities) {
            String fileName = migrationFilePath(tool, version, definition.entity.table);
            String content = switch (tool) {
                case "liquibase" -> liquibaseForEntity(definition, tableByEntity, version);
                default -> sqlForEntity(definition, tableByEntity, version);
            };
            out.add(new GeneratedFile(fileName, content));
            version++;
        }
        return out;
    }

    private String migrationFilePath(String tool, int version, String table) {
        if ("liquibase".equals(tool)) {
            return "src/main/resources/db/changelog/" + version + "-create-" + table + ".xml";
        }
        return "src/main/resources/db/migration/V" + version + "__create_" + table + "_table.sql";
    }

    private Map<String, String> buildTableMap(ApiSpecification specification) {
        Map<String, String> map = new HashMap<>();
        for (EntityDefinition definition : specification.entities) {
            map.put(definition.entity.name, definition.entity.table);
        }
        return map;
    }

    private String sqlForEntity(EntityDefinition definition, Map<String, String> tableByEntity, int version) {
        StringBuilder out = new StringBuilder();
        out.append("-- V").append(version).append(" create ").append(definition.entity.table).append('\n');
        out.append("CREATE TABLE ").append(definition.entity.table).append(" (\n");
        List<String> columns = new ArrayList<>();
        columns.add("id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY");
        for (FieldSpec field : definition.entity.fields) {
            StringBuilder fieldColumn = new StringBuilder();
            fieldColumn.append(field.name).append(" ").append(toSqlType(field.type));
            if (!field.nullable) {
                fieldColumn.append(" NOT NULL");
            }
            if (field.unique) {
                fieldColumn.append(" UNIQUE");
            }
            if (field.defaultValue != null && !field.defaultValue.isBlank()) {
                fieldColumn.append(" DEFAULT '").append(field.defaultValue).append("'");
            }
            columns.add(fieldColumn.toString());
        }
        columns.addAll(foreignKeyColumns(definition.relationships));
        out.append(columns.stream().map(value -> "    " + value).collect(Collectors.joining(",\n")));
        out.append("\n);\n\n");

        for (RelationshipSpec relationship : definition.relationships) {
            if ("ManyToMany".equals(relationship.type)) {
                out.append(joinTableSql(definition, relationship, tableByEntity));
            } else if ("ManyToOne".equals(relationship.type) || "OneToOne".equals(relationship.type)) {
                String targetTable = tableByEntity.get(relationship.target);
                String fkColumn = relationship.fieldName + "_id";
                out.append("ALTER TABLE ").append(definition.entity.table)
                        .append(" ADD CONSTRAINT fk_").append(definition.entity.table).append("_").append(fkColumn)
                        .append(" FOREIGN KEY (").append(fkColumn).append(") REFERENCES ").append(targetTable).append("(id);\n");
            }
        }
        return out.toString();
    }

    private String liquibaseForEntity(EntityDefinition definition, Map<String, String> tableByEntity, int version) {
        String id = "v" + version + "-create-" + definition.entity.table;
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<databaseChangeLog\n")
                .append("        xmlns=\"http://www.liquibase.org/xml/ns/dbchangelog\"\n")
                .append("        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
                .append("        xsi:schemaLocation=\"http://www.liquibase.org/xml/ns/dbchangelog\n")
                .append("        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd\">\n\n")
                .append("    <changeSet id=\"").append(id).append("\" author=\"rest-api-generator\">\n")
                .append("        <createTable tableName=\"").append(definition.entity.table).append("\">\n")
                .append("            <column name=\"id\" type=\"BIGINT\" autoIncrement=\"true\">\n")
                .append("                <constraints primaryKey=\"true\" nullable=\"false\"/>\n")
                .append("            </column>\n");

        for (FieldSpec field : definition.entity.fields) {
            out.append("            <column name=\"").append(field.name).append("\" type=\"").append(toSqlType(field.type)).append("\">\n")
                    .append("                <constraints nullable=\"").append(field.nullable).append("\" unique=\"").append(field.unique).append("\"/>\n")
                    .append("            </column>\n");
        }

        for (String fkColumn : foreignKeyColumns(definition.relationships)) {
            String[] parts = fkColumn.split(" ");
            out.append("            <column name=\"").append(parts[0]).append("\" type=\"BIGINT\"/>\n");
        }

        out.append("        </createTable>\n");

        for (RelationshipSpec relationship : definition.relationships) {
            if ("ManyToOne".equals(relationship.type) || "OneToOne".equals(relationship.type)) {
                String targetTable = tableByEntity.get(relationship.target);
                String fkColumn = relationship.fieldName + "_id";
                out.append("        <addForeignKeyConstraint baseTableName=\"").append(definition.entity.table)
                        .append("\" baseColumnNames=\"").append(fkColumn)
                        .append("\" referencedTableName=\"").append(targetTable)
                        .append("\" referencedColumnNames=\"id\"/>\n");
            }
        }
        out.append("    </changeSet>\n")
                .append("</databaseChangeLog>\n");
        return out.toString();
    }

    private List<String> foreignKeyColumns(List<RelationshipSpec> relationships) {
        List<String> columns = new ArrayList<>();
        for (RelationshipSpec relationship : relationships) {
            if ("ManyToOne".equals(relationship.type) || "OneToOne".equals(relationship.type)) {
                columns.add(relationship.fieldName + "_id BIGINT");
            }
        }
        return columns;
    }

    private String joinTableSql(EntityDefinition source, RelationshipSpec relationship, Map<String, String> tableByEntity) {
        String sourceTable = source.entity.table;
        String targetTable = tableByEntity.get(relationship.target);
        String joinTable = sourceTable + "_" + targetTable;
        return "CREATE TABLE " + joinTable + " (\n"
                + "    " + sourceTable + "_id BIGINT NOT NULL,\n"
                + "    " + targetTable + "_id BIGINT NOT NULL,\n"
                + "    PRIMARY KEY (" + sourceTable + "_id, " + targetTable + "_id),\n"
                + "    FOREIGN KEY (" + sourceTable + "_id) REFERENCES " + sourceTable + "(id),\n"
                + "    FOREIGN KEY (" + targetTable + "_id) REFERENCES " + targetTable + "(id)\n"
                + ");\n\n";
    }

    private String toSqlType(String javaType) {
        return switch (javaType) {
            case "Long" -> "BIGINT";
            case "Integer" -> "INTEGER";
            case "Boolean" -> "BOOLEAN";
            case "BigDecimal" -> "DECIMAL(19,2)";
            case "LocalDate" -> "DATE";
            case "LocalDateTime" -> "TIMESTAMP";
            default -> "VARCHAR(255)";
        };
    }
}
