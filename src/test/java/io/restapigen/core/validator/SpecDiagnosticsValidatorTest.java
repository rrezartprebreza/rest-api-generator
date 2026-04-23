package io.restapigen.core.validator;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.EntitySpec;
import io.restapigen.domain.FieldSpec;
import io.restapigen.domain.RelationshipSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecDiagnosticsValidatorTest {

    @Test
    void reportsDuplicateFieldsAsErrors() {
        ApiSpecification spec = new ApiSpecification(
                "shop-api",
                "io.backend.shop",
                List.of(new EntityDefinition(
                        new EntitySpec("Product", "products", "Long", List.of(
                                field("name"),
                                field("name")
                        )),
                        new ApiSpec("/api/products", true, true, true),
                        List.of()
                )),
                List.of()
        );

        SpecDiagnosticsValidator.ValidationReport report = new SpecDiagnosticsValidator().validate(spec, GenerationConfig.defaults());

        assertEquals(1, report.errors().size());
        assertEquals("DUPLICATE_FIELD", report.errors().get(0).code());
        assertTrue(report.fixSuggestions().stream().anyMatch(i -> "REMOVE_DUPLICATE_FIELD".equals(i.actionType()) && "name".equals(i.field())));
    }

    @Test
    void reportsUnknownRelationshipTargetsAsErrors() {
        ApiSpecification spec = new ApiSpecification(
                "shop-api",
                "io.backend.shop",
                List.of(new EntityDefinition(
                        new EntitySpec("Product", "products", "Long", List.of(field("name"))),
                        new ApiSpec("/api/products", true, true, true),
                        List.of(new RelationshipSpec("ManyToOne", "Category", "category"))
                )),
                List.of()
        );

        SpecDiagnosticsValidator.ValidationReport report = new SpecDiagnosticsValidator().validate(spec, GenerationConfig.defaults());

        assertEquals(1, report.errors().size());
        assertEquals("UNKNOWN_RELATIONSHIP_TARGET", report.errors().get(0).code());
        assertTrue(report.fixSuggestions().stream().anyMatch(i -> "REMOVE_RELATIONSHIP".equals(i.actionType()) && "category".equals(i.field())));
    }

    @Test
    void reportsAuditingFieldConflictsAsWarnings() {
        ApiSpecification spec = new ApiSpecification(
                "shop-api",
                "io.backend.shop",
                List.of(new EntityDefinition(
                        new EntitySpec("Product", "products", "Long", List.of(field("createdAt"), field("updatedAt"))),
                        new ApiSpec("/api/products", true, true, true),
                        List.of()
                )),
                List.of()
        );

        SpecDiagnosticsValidator.ValidationReport report = new SpecDiagnosticsValidator().validate(spec, GenerationConfig.defaults());

        assertTrue(report.warnings().stream().anyMatch(i -> "AUDIT_FIELD_CONFLICT".equals(i.code()) && "createdAt".equals(i.field())));
        assertTrue(report.warnings().stream().anyMatch(i -> "AUDIT_FIELD_CONFLICT".equals(i.code()) && "updatedAt".equals(i.field())));
        assertTrue(report.fixSuggestions().stream().anyMatch(i -> "REMOVE_FIELD".equals(i.actionType()) && "createdAt".equals(i.field())));
        assertTrue(report.fixSuggestions().stream().anyMatch(i -> "REMOVE_FIELD".equals(i.actionType()) && "updatedAt".equals(i.field())));
    }

    private static FieldSpec field(String name) {
        return new FieldSpec(name, "String", List.of(), false, false, null, null, null, false, List.of(), null, null);
    }
}
