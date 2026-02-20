package io.restapigen.core.validator;

import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.EntitySpec;
import io.restapigen.domain.FieldSpec;
import io.restapigen.domain.RelationshipSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpecValidatorTest {

    @Test
    void rejectsInvalidFieldRange() {
        ApiSpecification spec = new ApiSpecification(
                "products-api",
                "com.example.generated",
                List.of(new EntityDefinition(
                        new EntitySpec("Product", "products", "Long", List.of(
                                new FieldSpec("price", "BigDecimal", List.of(), false, false, 100, 10, null, false, List.of(), null, null)
                        )),
                        new ApiSpec("/api/products", true, true, true),
                        List.of()
                )),
                List.of()
        );

        assertThrows(IllegalArgumentException.class, () -> new SpecValidator().validate(spec));
    }

    @Test
    void rejectsUnknownRelationshipTarget() {
        ApiSpecification spec = new ApiSpecification(
                "products-api",
                "com.example.generated",
                List.of(new EntityDefinition(
                        new EntitySpec("Product", "products", "Long", List.of()),
                        new ApiSpec("/api/products", true, true, true),
                        List.of(new RelationshipSpec("ManyToOne", "Category", "category"))
                )),
                List.of()
        );

        assertThrows(IllegalArgumentException.class, () -> new SpecValidator().validate(spec));
    }

    @Test
    void acceptsValidCrossEntityRelationship() {
        ApiSpecification spec = new ApiSpecification(
                "shop-api",
                "com.example.generated",
                List.of(
                        new EntityDefinition(
                                new EntitySpec("Category", "categories", "Long", List.of()),
                                new ApiSpec("/api/categories", true, true, true),
                                List.of()
                        ),
                        new EntityDefinition(
                                new EntitySpec("Product", "products", "Long", List.of()),
                                new ApiSpec("/api/products", true, true, true),
                                List.of(new RelationshipSpec("ManyToOne", "Category", "category"))
                        )
                ),
                List.of()
        );

        assertDoesNotThrow(() -> new SpecValidator().validate(spec));
    }
}
