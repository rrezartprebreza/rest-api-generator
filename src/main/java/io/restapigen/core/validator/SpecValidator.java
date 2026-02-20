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
    private static final Pattern ENTITY_NAME = Pattern.compile("^[A-Z][A-Za-z0-9_]*$");
    private static final Pattern FIELD_NAME = Pattern.compile("^[a-z][A-Za-z0-9_]*$");
    private static final Set<String> SUPPORTED_RELATIONS = Set.of("OneToOne", "OneToMany", "ManyToOne", "ManyToMany");

    public void validate(ApiSpecification specification) {
        Objects.requireNonNull(specification, "specification");
        if (specification.projectName == null || specification.projectName.isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }
        if (specification.basePackage == null || specification.basePackage.isBlank()) {
            throw new IllegalArgumentException("Base package is required");
        }
        if (specification.entities.isEmpty()) {
            throw new IllegalArgumentException("Specification must contain at least one entity");
        }

        Set<String> entityNames = new HashSet<>();
        for (EntityDefinition definition : specification.entities) {
            String name = definition.entity.name;
            if (name == null || !ENTITY_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid entity name: " + name);
            }
            if (!entityNames.add(name)) {
                throw new IllegalArgumentException("Duplicate entity name: " + name);
            }
            if (definition.entity.table == null || definition.entity.table.isBlank()) {
                throw new IllegalArgumentException("Entity table is required for " + name);
            }
            if (definition.api.resourcePath == null || !definition.api.resourcePath.startsWith("/")) {
                throw new IllegalArgumentException("API resourcePath must start with '/': " + definition.api.resourcePath);
            }
            validateFields(name, definition.entity.fields);
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
            if (field.type == null || field.type.isBlank()) {
                throw new IllegalArgumentException("Field type is required for '" + field.name + "' in entity " + entityName);
            }
            if (field.min != null && field.max != null && field.min > field.max) {
                throw new IllegalArgumentException("Invalid range for field '" + field.name + "' in entity " + entityName + ": min > max");
            }
            if (!field.enumValues.isEmpty() && !"String".equals(field.type)) {
                throw new IllegalArgumentException("Enum values require String type for field '" + field.name + "' in entity " + entityName);
            }
        }
    }

    private void validateRelationships(String entityName, List<RelationshipSpec> relationships, Set<String> entityNames) {
        Set<String> relationKeys = new HashSet<>();
        for (RelationshipSpec relationship : relationships) {
            if (relationship.type == null || !SUPPORTED_RELATIONS.contains(relationship.type)) {
                throw new IllegalArgumentException("Unsupported relationship type '" + relationship.type + "' in entity " + entityName);
            }
            if (relationship.target == null || !entityNames.contains(relationship.target)) {
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
