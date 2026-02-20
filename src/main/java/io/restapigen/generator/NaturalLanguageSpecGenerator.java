package io.restapigen.generator;

import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.EntitySpec;
import io.restapigen.domain.FieldSpec;
import io.restapigen.domain.RelationshipSpec;
import io.restapigen.generator.EntityNameSuggester;
import io.restapigen.generator.text.NameTransforms;
import io.restapigen.generator.text.Pluralizer;
import io.restapigen.generator.text.RequestParsing;
import io.restapigen.generator.text.TypeInference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class NaturalLanguageSpecGenerator {
    private static final String DEFAULT_BASE_PACKAGE = "com.example.generated";
    private static final String DEFAULT_ID_TYPE = "Long";
    private static final String DEFAULT_ENTITY_NAME = "Item";

    public ApiSpecification generate(String userRequest) {
        String request = Objects.requireNonNullElse(userRequest, "").trim();
        List<EntityDefinition> definitions = buildEntityDefinitions(request);
        if (definitions.isEmpty()) {
            definitions = List.of(defaultEntityDefinition(DEFAULT_ENTITY_NAME));
        }

        String projectName = defaultProjectName(definitions);
        List<String> suggestions = hasExplicitEntityName(request) ? List.of() : EntityNameSuggester.suggest(request, projectName);

        return new ApiSpecification(projectName, DEFAULT_BASE_PACKAGE, definitions, suggestions);
    }

    private static List<EntityDefinition> buildEntityDefinitions(String request) {
        if (request == null || request.isBlank()) {
            return List.of();
        }
        List<String> segments = RequestParsing.splitIntoEntitySegments(request);
        List<EntityDefinition> definitions = new ArrayList<>();
        Set<String> usedNames = new LinkedHashSet<>();
        for (String segment : segments) {
            String rawName = RequestParsing.extractEntityName(segment);
            String entityName = NameTransforms.toPascalCase(rawName == null ? DEFAULT_ENTITY_NAME : rawName);
            if (entityName.isBlank() || !usedNames.add(entityName)) {
                continue;
            }

            List<FieldSpec> fields = buildFields(segment);
            List<RelationshipSpec> relationships = buildRelationships(segment);
            EntitySpec entity = new EntitySpec(entityName, defaultTableName(entityName), DEFAULT_ID_TYPE, fields);
            ApiSpec api = buildApi(segment, entityName);
            definitions.add(new EntityDefinition(entity, api, relationships));
        }
        return definitions;
    }

    private static boolean hasExplicitEntityName(String request) {
        if (request == null || request.isBlank()) {
            return false;
        }
        return RequestParsing.extractEntityName(request) != null;
    }

    private static EntityDefinition defaultEntityDefinition(String entityName) {
        EntitySpec entity = defaultEntity(entityName);
        ApiSpec api = defaultApiForEntity(entity);
        return new EntityDefinition(entity, api, List.of());
    }

    private static EntitySpec defaultEntity(String entityName) {
        return new EntitySpec(entityName, defaultTableName(entityName), DEFAULT_ID_TYPE, List.of());
    }

    private static ApiSpec defaultApiForEntity(EntitySpec entity) {
        return new ApiSpec(defaultResourcePath(entity.name), true, true, true);
    }

    private static ApiSpec buildApi(String request, String entityName) {
        String lower = request.toLowerCase(Locale.ROOT);
        boolean crud = !RequestParsing.containsDisableCrud(lower);
        boolean pagination = !RequestParsing.containsDisablePagination(lower);
        boolean sorting = !RequestParsing.containsDisableSorting(lower);
        return new ApiSpec(defaultResourcePath(entityName), crud, pagination, sorting);
    }

    private static List<FieldSpec> buildFields(String request) {
        List<RequestParsing.ParsedField> parsedFields = RequestParsing.extractFields(request);
        List<FieldSpec> fields = new ArrayList<>();
        Set<String> usedNames = new LinkedHashSet<>();
        for (RequestParsing.ParsedField parsed : parsedFields) {
            String camel = NameTransforms.toCamelCase(parsed.name());
            if (camel.isBlank() || !usedNames.add(camel)) {
                continue;
            }

            String type = TypeInference.normalizeType(parsed.typeHint(), camel);
            boolean nullable = parsed.nullable();
            boolean unique = parsed.unique();
            Integer min = parsed.min();
            Integer max = parsed.max();
            String format = parsed.format();
            boolean encrypted = parsed.encrypted();
            List<String> enumValues = parsed.enumValues();
            String defaultValue = parsed.defaultValue();
            String calculatedExpression = parsed.calculatedExpression();

            List<String> validation = buildValidationTokens(camel, type, nullable, min, max, format);

            fields.add(new FieldSpec(
                    camel,
                    type,
                    validation,
                    unique,
                    nullable,
                    min,
                    max,
                    format,
                    encrypted,
                    enumValues,
                    defaultValue,
                    calculatedExpression
            ));
        }
        return fields;
    }

    private static List<RelationshipSpec> buildRelationships(String request) {
        List<RequestParsing.ParsedRelationship> parsed = RequestParsing.extractRelationships(request);
        List<RelationshipSpec> relationships = new ArrayList<>();
        for (RequestParsing.ParsedRelationship relation : parsed) {
            String target = NameTransforms.toPascalCase(relation.target());
            String fieldName = NameTransforms.toCamelCase(relation.fieldName());
            relationships.add(new RelationshipSpec(relation.type(), target, fieldName));
        }
        return List.copyOf(relationships);
    }

    private static List<String> buildValidationTokens(
            String fieldName,
            String type,
            boolean nullable,
            Integer min,
            Integer max,
            String format
    ) {
        if (nullable) {
            return List.of();
        }
        List<String> validation = new ArrayList<>();
        if ("String".equals(type)) {
            validation.add("NotBlank");
            if ("email".equalsIgnoreCase(format) || fieldName.toLowerCase(Locale.ROOT).contains("email")) {
                validation.add("Email");
            }
            if (min != null || max != null) {
                validation.add("Size:" + (min == null ? 0 : min) + ":" + (max == null ? 255 : max));
            } else if (TypeInference.looksLikeNameField(fieldName)) {
                validation.add("Size:2:50");
            }
        } else if ("Integer".equals(type) || "Long".equals(type) || "BigDecimal".equals(type) || "Double".equals(type)) {
            if (min != null) {
                validation.add("Min:" + min);
            }
            if (max != null) {
                validation.add("Max:" + max);
            }
        }
        return List.copyOf(validation);
    }

    private static String defaultProjectName(List<EntityDefinition> definitions) {
        if (definitions.isEmpty()) {
            return defaultProjectName(DEFAULT_ENTITY_NAME);
        }
        String plural = Pluralizer.pluralize(NameTransforms.toKebabCase(definitions.get(0).entity.name));
        return plural + "-api";
    }

    private static String defaultProjectName(String entityName) {
        String plural = Pluralizer.pluralize(NameTransforms.toKebabCase(entityName));
        return plural + "-api";
    }

    private static String defaultTableName(String entityName) {
        String snake = NameTransforms.toSnakeCase(entityName);
        return Pluralizer.pluralize(snake);
    }

    private static String defaultResourcePath(String entityName) {
        String kebab = NameTransforms.toKebabCase(entityName);
        String plural = Pluralizer.pluralize(kebab);
        return "/api/" + plural;
    }
}
