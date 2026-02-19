package io.restapigen.generator;

import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalLanguageSpecGeneratorTest {
    @Test
    void generatesDefaultsAndValidations() {
        String request = "Create a REST API for User with name, email unique, age (Integer) optional.";
        ApiSpecification spec = new NaturalLanguageSpecGenerator().generate(request);

        assertEquals("users-api", spec.projectName);
        assertEquals("com.example.generated", spec.basePackage);

        assertEquals(1, spec.entities.size());
        EntityDefinition definition = spec.entities.get(0);
        assertEquals("User", definition.entity.name);
        assertEquals("users", definition.entity.table);
        assertEquals("Long", definition.entity.idType);

        assertEquals("/api/users", definition.api.resourcePath);
        assertTrue(definition.api.crud);
        assertTrue(definition.api.pagination);
        assertTrue(definition.api.sorting);

        assertEquals(3, definition.entity.fields.size());

        FieldSpec name = definition.entity.fields.get(0);
        assertEquals("name", name.name);
        assertEquals("String", name.type);
        assertFalse(name.nullable);
        assertFalse(name.unique);
        assertTrue(name.validation.contains("NotBlank"));
        assertTrue(name.validation.contains("Size:2:50"));

        FieldSpec email = definition.entity.fields.get(1);
        assertEquals("email", email.name);
        assertEquals("String", email.type);
        assertFalse(email.nullable);
        assertTrue(email.unique);
        assertTrue(email.validation.contains("NotBlank"));
        assertTrue(email.validation.contains("Email"));

        FieldSpec age = definition.entity.fields.get(2);
        assertEquals("age", age.name);
        assertEquals("Integer", age.type);
        assertTrue(age.nullable);
        assertFalse(age.unique);
        assertEquals(0, age.validation.size());
        assertTrue(spec.suggestions.isEmpty());
    }

    @Test
    void splitsMultipleSegmentsIntoMultipleEntities() {
        String request = """
                Create an API for Product with name, price

                Create an API for Employee with firstName, lastName
                """;
        ApiSpecification spec = new NaturalLanguageSpecGenerator().generate(request);

        assertEquals(2, spec.entities.size());
        EntityDefinition product = spec.entities.get(0);
        EntityDefinition employee = spec.entities.get(1);

        assertEquals("Product", product.entity.name);
        assertEquals("Employee", employee.entity.name);
        assertEquals("/api/products", product.api.resourcePath);
        assertEquals("/api/employees", employee.api.resourcePath);
    }

    @Test
    void suggestsEntityWhenMissing() {
        String request = "Generate a service for project inventory";
        ApiSpecification spec = new NaturalLanguageSpecGenerator().generate(request);

        assertEquals(1, spec.entities.size());
        EntityDefinition definition = spec.entities.get(0);
        assertEquals("Item", definition.entity.name);
        assertFalse(spec.suggestions.isEmpty());
        assertTrue(spec.suggestions.contains("Inventory"));
    }
}
