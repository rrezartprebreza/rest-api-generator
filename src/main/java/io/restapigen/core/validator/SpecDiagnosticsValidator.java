package io.restapigen.core.validator;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;
import io.restapigen.domain.RelationshipSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Non-blocking validator used by the Web UI to surface actionable diagnostics.
 */
public final class SpecDiagnosticsValidator {

    private static final Set<String> AUDIT_FIELDS = Set.of("createdAt", "updatedAt");

    public ValidationReport validate(ApiSpecification spec, GenerationConfig config) {
        List<ValidationIssue> warnings = new ArrayList<>();
        List<ValidationIssue> errors = new ArrayList<>();
        List<FixSuggestion> fixSuggestions = new ArrayList<>();

        if (spec == null) {
            errors.add(new ValidationIssue("INVALID_SPEC", "Specification payload is empty", null, null));
            return new ValidationReport(warnings, errors, fixSuggestions);
        }

        Map<String, Integer> entityCounts = new HashMap<>();
        Set<String> knownEntities = new HashSet<>();
        for (EntityDefinition definition : spec.entities) {
            String entityName = definition.entity.name;
            entityCounts.merge(entityName, 1, Integer::sum);
            knownEntities.add(entityName);

            Map<String, Integer> fieldCounts = new HashMap<>();
            for (FieldSpec field : definition.entity.fields) {
                fieldCounts.merge(field.name, 1, Integer::sum);
                if (isAuditFieldConflict(field.name, config)) {
                    warnings.add(new ValidationIssue(
                            "AUDIT_FIELD_CONFLICT",
                            "Field '" + field.name + "' is auto-managed when auditing is enabled.",
                            entityName,
                            field.name
                    ));
                    fixSuggestions.add(new FixSuggestion(
                            "REMOVE_FIELD",
                            "Remove auto-managed field '" + field.name + "' from prompt",
                            entityName,
                            field.name
                    ));
                }
            }
            fieldCounts.forEach((fieldName, count) -> {
                if (count > 1) {
                    errors.add(new ValidationIssue(
                            "DUPLICATE_FIELD",
                            "Duplicate field '" + fieldName + "' in entity '" + entityName + "'.",
                            entityName,
                            fieldName
                    ));
                    fixSuggestions.add(new FixSuggestion(
                            "REMOVE_DUPLICATE_FIELD",
                            "Keep one '" + fieldName + "' field and remove duplicates",
                            entityName,
                            fieldName
                    ));
                }
            });
        }

        entityCounts.forEach((entityName, count) -> {
            if (count > 1) {
                errors.add(new ValidationIssue(
                        "DUPLICATE_ENTITY",
                        "Duplicate entity name '" + entityName + "'.",
                        entityName,
                        null
                ));
            }
        });

        for (EntityDefinition definition : spec.entities) {
            String entityName = definition.entity.name;
            for (RelationshipSpec relationship : definition.relationships) {
                if (relationship.target.isBlank()) {
                    continue;
                }
                if (!knownEntities.contains(relationship.target)) {
                    errors.add(new ValidationIssue(
                            "UNKNOWN_RELATIONSHIP_TARGET",
                            "Entity '" + entityName + "' references unknown target '" + relationship.target + "'.",
                            entityName,
                            relationship.fieldName
                    ));
                    fixSuggestions.add(new FixSuggestion(
                            "REMOVE_RELATIONSHIP",
                            "Remove relationship to unknown target '" + relationship.target + "'",
                            entityName,
                            relationship.fieldName
                    ));
                }
            }
        }

        return new ValidationReport(List.copyOf(warnings), List.copyOf(errors), List.copyOf(fixSuggestions));
    }

    private static boolean isAuditFieldConflict(String fieldName, GenerationConfig config) {
        if (config == null || config.features() == null || !config.features().auditing()) {
            return false;
        }
        return AUDIT_FIELDS.contains(fieldName);
    }

    public record ValidationIssue(String code, String message, String entity, String field) {}

    public record FixSuggestion(String actionType, String message, String entity, String field) {}

    public record ValidationReport(List<ValidationIssue> warnings, List<ValidationIssue> errors,
                                   List<FixSuggestion> fixSuggestions) {}
}
