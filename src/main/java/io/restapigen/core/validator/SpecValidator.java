package io.restapigen.core.validator;

import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;
import io.restapigen.domain.RelationshipSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class SpecValidator {
    private static final Pattern PROJECT_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");
    private static final Pattern BASE_PACKAGE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$");
    private static final Pattern ENTITY_NAME = Pattern.compile("^[A-Z][A-Za-z0-9_]*$");
    private static final Pattern FIELD_NAME = Pattern.compile("^[a-z][A-Za-z0-9_]*$");
    private static final Pattern TABLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");
    private static final Pattern RESOURCE_PATH = Pattern.compile("^/(?:[A-Za-z0-9_-]+/?)*$");
    private static final Pattern CUSTOM_ENDPOINT = Pattern.compile("^[a-z][A-Za-z0-9_-]{0,63}$");
    private static final Pattern ENUM_VALUE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final Set<String> SUPPORTED_RELATIONS = Set.of("OneToOne", "OneToMany", "ManyToOne", "ManyToMany");
    private static final Set<String> SUPPORTED_ID_TYPES = Set.of("Long", "UUID", "String");
    private static final Set<String> SUPPORTED_FIELD_TYPES = Set.of(
            "String", "Long", "Integer", "Boolean", "BigDecimal", "BigInteger",
            "Float", "Double", "LocalDate", "LocalDateTime", "Instant", "UUID",
            "Map<String,Object>", "List<String>", "List<Long>", "List<Integer>",
            "List<Boolean>", "List<BigDecimal>", "List<Float>", "List<Double>",
            "List<LocalDate>", "List<LocalDateTime>", "List<Instant>", "List<UUID>"
    );
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "record", "sealed",
            "permits", "non-sealed", "var", "yield"
    );

    public void validate(ApiSpecification specification) {
        Objects.requireNonNull(specification, "specification");
        if (specification.projectName == null || !PROJECT_NAME.matcher(specification.projectName).matches()) {
            throw new IllegalArgumentException("Project name must start with a letter and contain only letters, numbers, '_' or '-'");
        }
        if (specification.basePackage == null || !BASE_PACKAGE.matcher(specification.basePackage).matches()
                || containsJavaKeyword(specification.basePackage)) {
            throw new IllegalArgumentException("Base package must contain valid non-keyword Java identifiers");
        }
        if (specification.entities.isEmpty()) {
            throw new IllegalArgumentException("Specification must contain at least one entity");
        }
        if (!"none".equalsIgnoreCase(specification.securityHint)) {
            throw new IllegalArgumentException(
                    "Security generation is not available yet; remove the security hint or provide security manually"
            );
        }

        Set<String> entityNames = new HashSet<>();
        Set<String> tableNames = new HashSet<>();
        Set<String> resourcePaths = new HashSet<>();
        for (EntityDefinition definition : specification.entities) {
            String name = definition.entity.name;
            if (name == null || !ENTITY_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid entity name: " + name);
            }
            if (!entityNames.add(name)) {
                throw new IllegalArgumentException("Duplicate entity name: " + name);
            }
            if (definition.entity.table == null || !TABLE_NAME.matcher(definition.entity.table).matches()) {
                throw new IllegalArgumentException("Invalid table name for " + name + ": " + definition.entity.table);
            }
            if (!tableNames.add(definition.entity.table)) {
                throw new IllegalArgumentException("Duplicate table name: " + definition.entity.table);
            }
            if (!SUPPORTED_ID_TYPES.contains(definition.entity.idType)) {
                throw new IllegalArgumentException("Unsupported id type for " + name + ": " + definition.entity.idType);
            }
            if (!isValidResourcePath(definition.api.resourcePath)) {
                throw new IllegalArgumentException("Invalid API resourcePath: " + definition.api.resourcePath);
            }
            if (!resourcePaths.add(definition.api.resourcePath)) {
                throw new IllegalArgumentException("Duplicate API resourcePath: " + definition.api.resourcePath);
            }
            if (!definition.api.crud || !definition.api.pagination || !definition.api.sorting) {
                throw new IllegalArgumentException(
                        "Only CRUD APIs with pagination and sorting are currently supported for " + name
                );
            }
            validateFields(name, definition.entity.fields);
            validateCustomEndpoints(name, definition.api.customEndpoints);
        }

        for (EntityDefinition definition : specification.entities) {
            String name = definition.entity.name;
            validateRelationships(name, definition.relationships, entityNames);
        }
    }

    private void validateFields(String entityName, List<FieldSpec> fields) {
        Set<String> names = new HashSet<>();
        for (FieldSpec field : fields) {
            if (field.name == null || !FIELD_NAME.matcher(field.name).matches()) {
                throw new IllegalArgumentException("Invalid field name '" + field.name + "' in entity " + entityName);
            }
            if (!names.add(field.name)) {
                throw new IllegalArgumentException("Duplicate field '" + field.name + "' in entity " + entityName);
            }
            if (JAVA_KEYWORDS.contains(field.name)) {
                throw new IllegalArgumentException("Field name is a Java keyword: " + field.name);
            }
            if ("id".equals(field.name)) {
                throw new IllegalArgumentException("Field 'id' is generated automatically in entity " + entityName);
            }
            validateFieldType(entityName, field);
            for (String validation : field.validation) {
                if (!isSupportedValidation(validation)) {
                    throw new IllegalArgumentException(
                            "Unsupported validation token '" + validation + "' for field '" + field.name + "' in entity " + entityName
                    );
                }
            }
            if (field.min != null && field.max != null && field.min > field.max) {
                throw new IllegalArgumentException("Invalid range for field '" + field.name + "' in entity " + entityName + ": min > max");
            }
            if (field.defaultValue != null && (field.defaultValue.length() > 256 || containsControlCharacter(field.defaultValue))) {
                throw new IllegalArgumentException("Invalid default value for field '" + field.name + "' in entity " + entityName);
            }
            if (field.encrypted) {
                throw new IllegalArgumentException("Encrypted fields are not implemented yet: " + field.name);
            }
            if (field.calculatedExpression != null && !field.calculatedExpression.isBlank()) {
                throw new IllegalArgumentException("Calculated fields are not implemented yet: " + field.name);
            }
        }
    }

    private void validateFieldType(String entityName, FieldSpec field) {
        if (field.type == null || field.type.isBlank()) {
            throw new IllegalArgumentException("Field type is required for '" + field.name + "' in entity " + entityName);
        }
        if (field.enumValues.isEmpty()) {
            if (!SUPPORTED_FIELD_TYPES.contains(field.type)) {
                throw new IllegalArgumentException("Unsupported field type '" + field.type + "' in entity " + entityName);
            }
            return;
        }
        if (!ENTITY_NAME.matcher(field.type).matches()) {
            throw new IllegalArgumentException("Invalid enum type '" + field.type + "' in entity " + entityName);
        }
        Set<String> values = new HashSet<>();
        for (String value : field.enumValues) {
            if (value == null || !ENUM_VALUE.matcher(value).matches() || !values.add(value.toUpperCase())) {
                throw new IllegalArgumentException("Invalid or duplicate enum value '" + value + "' for field '" + field.name + "'");
            }
        }
    }

    private void validateCustomEndpoints(String entityName, List<String> endpoints) {
        Set<String> names = new HashSet<>();
        for (String endpoint : endpoints) {
            if (endpoint == null || !CUSTOM_ENDPOINT.matcher(endpoint).matches() || !names.add(endpoint)) {
                throw new IllegalArgumentException("Invalid or duplicate custom endpoint '" + endpoint + "' in entity " + entityName);
            }
        }
        if (!endpoints.isEmpty()) {
            throw new IllegalArgumentException("Custom endpoint implementation is not available yet for entity " + entityName);
        }
    }

    private boolean isSupportedValidation(String token) {
        if (token == null) return false;
        return token.matches("NotBlank|Email")
                || token.matches("Size:\\d+:\\d+")
                || token.matches("(?:Min|Max):-?\\d+")
                || token.matches("Decimal(?:Min|Max):-?\\d+(?:\\.\\d+)?")
                || token.matches("OneOf:[A-Za-z0-9_]+(?:\\|[A-Za-z0-9_]+)*");
    }

    private boolean isValidResourcePath(String path) {
        return path != null
                && RESOURCE_PATH.matcher(path).matches()
                && !path.contains("//")
                && !path.contains("..");
    }

    private boolean containsJavaKeyword(String packageName) {
        for (String segment : packageName.split("\\.")) {
            if (JAVA_KEYWORDS.contains(segment)) return true;
        }
        return false;
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private void validateRelationships(String entityName, List<RelationshipSpec> relationships, Set<String> entityNames) {
        Set<String> relationKeys = new HashSet<>();
        for (RelationshipSpec relationship : relationships) {
            if (relationship.type == null || !SUPPORTED_RELATIONS.contains(relationship.type)) {
                throw new IllegalArgumentException("Unsupported relationship type '" + relationship.type + "' in entity " + entityName);
            }
            if (relationship.target == null || relationship.target.equals(entityName)) {
                throw new IllegalArgumentException("Relationship '" + relationship.fieldName + "' target must differ from the owning entity in entity " + entityName);
            }
            if (!entityNames.contains(relationship.target)) {
                throw new IllegalArgumentException("Unknown relationship target '" + relationship.target + "' in entity " + entityName);
            }
            if (relationship.fieldName == null || !FIELD_NAME.matcher(relationship.fieldName).matches()) {
                throw new IllegalArgumentException("Invalid relationship field name '" + relationship.fieldName + "' in entity " + entityName);
            }
            String key = relationship.type + ":" + relationship.target + ":" + relationship.fieldName;
            if (!relationKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate relationship '" + key + "' in entity " + entityName);
            }
        }
    }
}
