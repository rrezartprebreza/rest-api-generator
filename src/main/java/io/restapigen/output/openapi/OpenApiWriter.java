package io.restapigen.output.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.restapigen.core.validator.SpecValidator;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenApiWriter {
    private static final ObjectMapper YAML = new ObjectMapper(
            YAMLFactory.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build()
    );

    private OpenApiWriter() {
    }

    public static String write(ApiSpecification specification) {
        new SpecValidator().validate(specification);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("openapi", "3.0.3");
        document.put("info", Map.of("title", specification.projectName, "version", "1.0.0"));
        document.put("paths", buildPaths(specification));
        document.put("components", Map.of("schemas", buildSchemas(specification)));

        try {
            return YAML.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OpenAPI document", e);
        }
    }

    private static Map<String, Object> buildPaths(ApiSpecification specification) {
        Map<String, Object> paths = new LinkedHashMap<>();
        for (EntityDefinition definition : specification.entities) {
            String entityName = definition.entity.name;
            String schemaRef = "#/components/schemas/" + entityName + "DTO";

            Map<String, Object> collection = new LinkedHashMap<>();
            collection.put("get", listOperation(entityName, schemaRef));
            collection.put("post", writeOperation("Create " + entityName, "201", "Created", schemaRef, schemaRef));
            paths.put(definition.api.resourcePath, collection);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("parameters", List.of(idParameter(definition.entity.idType)));
            item.put("get", readOperation("Get " + entityName + " by id", "200", "Found", schemaRef));
            item.put("put", writeOperation("Update " + entityName, "200", "Updated", schemaRef, schemaRef));
            item.put("delete", readOperation("Delete " + entityName, "204", "Deleted", null));
            paths.put(definition.api.resourcePath + "/{id}", item);
        }
        return paths;
    }

    private static Map<String, Object> listOperation(String entityName, String schemaRef) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("summary", "List " + entityName);
        operation.put("parameters", List.of(
                queryParameter("page", "integer", "int32", 0),
                queryParameter("size", "integer", "int32", 20),
                queryParameter("sortBy", "string", null, "id"),
                queryParameter("sortDir", "string", null, "asc"),
                optionalQueryParameter("filter")
        ));
        Map<String, Object> pageSchema = new LinkedHashMap<>();
        pageSchema.put("type", "object");
        pageSchema.put("description", "Spring Data page containing " + entityName + "DTO items");
        pageSchema.put("properties", Map.of(
                "content", Map.of("type", "array", "items", Map.of("$ref", schemaRef)),
                "totalElements", Map.of("type", "integer", "format", "int64"),
                "totalPages", Map.of("type", "integer", "format", "int32"),
                "number", Map.of("type", "integer", "format", "int32"),
                "size", Map.of("type", "integer", "format", "int32")
        ));
        operation.put("responses", Map.of("200", response("OK", pageSchema)));
        return operation;
    }

    private static Map<String, Object> readOperation(String summary, String status, String description, String schemaRef) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("summary", summary);
        operation.put("responses", Map.of(status, response(description, schemaRef == null ? null : Map.of("$ref", schemaRef))));
        return operation;
    }

    private static Map<String, Object> writeOperation(
            String summary,
            String status,
            String description,
            String requestSchemaRef,
            String responseSchemaRef
    ) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("summary", summary);
        operation.put("requestBody", Map.of(
                "required", true,
                "content", Map.of("application/json", Map.of("schema", Map.of("$ref", requestSchemaRef)))
        ));
        operation.put("responses", Map.of(status, response(description, Map.of("$ref", responseSchemaRef))));
        return operation;
    }

    private static Map<String, Object> response(String description, Map<String, Object> schema) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", description);
        if (schema != null) {
            response.put("content", Map.of("application/json", Map.of("schema", schema)));
        }
        return response;
    }

    private static Map<String, Object> idParameter(String idType) {
        return Map.of(
                "name", "id",
                "in", "path",
                "required", true,
                "schema", scalarSchema(idType)
        );
    }

    private static Map<String, Object> queryParameter(String name, String type, String format, Object defaultValue) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        if (format != null) schema.put("format", format);
        schema.put("default", defaultValue);
        return Map.of("name", name, "in", "query", "required", false, "schema", schema);
    }

    private static Map<String, Object> optionalQueryParameter(String name) {
        return Map.of("name", name, "in", "query", "required", false, "schema", Map.of("type", "string"));
    }

    private static Map<String, Object> buildSchemas(ApiSpecification specification) {
        Map<String, Object> schemas = new LinkedHashMap<>();
        for (EntityDefinition definition : specification.entities) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("id", scalarSchema(definition.entity.idType));
            List<String> required = new ArrayList<>();
            for (FieldSpec field : definition.entity.fields) {
                properties.put(field.name, fieldSchema(field));
                if (!field.nullable) required.add(field.name);
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", properties);
            if (!required.isEmpty()) schema.put("required", required);
            schemas.put(definition.entity.name + "DTO", schema);
        }
        return schemas;
    }

    private static Map<String, Object> fieldSchema(FieldSpec field) {
        if (!field.enumValues.isEmpty()) {
            return Map.of("type", "string", "enum", field.enumValues);
        }
        if (field.type.startsWith("List<")) {
            String itemType = field.type.substring(5, field.type.length() - 1);
            return Map.of("type", "array", "items", scalarSchema(itemType));
        }
        if (field.type.startsWith("Map<")) {
            return Map.of("type", "object", "additionalProperties", true);
        }
        return scalarSchema(field.type);
    }

    private static Map<String, Object> scalarSchema(String javaType) {
        Map<String, Object> schema = new LinkedHashMap<>();
        switch (javaType) {
            case "Long", "BigInteger" -> {
                schema.put("type", "integer");
                schema.put("format", "int64");
            }
            case "Integer" -> {
                schema.put("type", "integer");
                schema.put("format", "int32");
            }
            case "Float" -> {
                schema.put("type", "number");
                schema.put("format", "float");
            }
            case "Double", "BigDecimal" -> {
                schema.put("type", "number");
                schema.put("format", "double");
            }
            case "Boolean" -> schema.put("type", "boolean");
            case "LocalDate" -> {
                schema.put("type", "string");
                schema.put("format", "date");
            }
            case "LocalDateTime", "Instant" -> {
                schema.put("type", "string");
                schema.put("format", "date-time");
            }
            case "UUID" -> {
                schema.put("type", "string");
                schema.put("format", "uuid");
            }
            default -> schema.put("type", "string");
        }
        return schema;
    }
}
