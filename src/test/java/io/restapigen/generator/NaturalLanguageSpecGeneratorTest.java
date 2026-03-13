package io.restapigen.generator;

import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;
import org.junit.jupiter.api.Test;

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

    @Test
    void parsesRelationships() {
        String request = """
                Create an API for Product with name, price
                - belongs to Category
                - has many Review
                - has many Tag (many-to-many)
                """;
        ApiSpecification spec = new NaturalLanguageSpecGenerator().generate(request);

        EntityDefinition definition = spec.entities.get(0);
        assertEquals(3, definition.relationships.size());
        assertEquals("ManyToOne", definition.relationships.get(0).type);
        assertEquals("Category", definition.relationships.get(0).target);
        assertEquals("OneToMany", definition.relationships.get(1).type);
        assertEquals("ManyToMany", definition.relationships.get(2).type);
    }

    @Test
    void relationshipHintsDoNotCreateSyntheticFields() {
        String request = """
                Create an API for Product with:
                - name (string, required)
                - belongs to Category
                - has many Tag (many-to-many)
                """;
        ApiSpecification spec = new NaturalLanguageSpecGenerator().generate(request);
        EntityDefinition definition = spec.entities.get(0);

        assertTrue(definition.entity.fields.stream().anyMatch(field -> "name".equals(field.name)));
        assertFalse(definition.entity.fields.stream().anyMatch(field -> "belongsToCategory".equals(field.name)));
        assertFalse(definition.entity.fields.stream().anyMatch(field -> "hasManyTagManyToMany".equals(field.name)));
    }

    @Test
    void generatesAdvancedValidationTokens() {
        String request = """
                Create an API for Product with:
                - price (decimal, required, min 0, max 1000)
                - status (enum: ACTIVE, INACTIVE)
                - ownerEmail (string, required, valid email)
                """;
        ApiSpecification spec = new NaturalLanguageSpecGenerator().generate(request);
        EntityDefinition definition = spec.entities.get(0);

        FieldSpec price = definition.entity.fields.stream().filter(field -> "price".equals(field.name)).findFirst().orElseThrow();
        assertTrue(price.validation.contains("DecimalMin:0"));
        assertTrue(price.validation.contains("DecimalMax:1000"));

        FieldSpec status = definition.entity.fields.stream().filter(field -> "status".equals(field.name)).findFirst().orElseThrow();
        assertEquals("ProductStatus", status.type);
        assertEquals(2, status.enumValues.size());
        assertTrue(status.validation.isEmpty());

        FieldSpec ownerEmail = definition.entity.fields.stream().filter(field -> "ownerEmail".equals(field.name)).findFirst().orElseThrow();
        assertTrue(ownerEmail.validation.contains("Email"));
    }

    @Test
    void infersAdvancedFieldTypes() {
        String request = """
                Create an API for Product with:
                - metadata (json)
                - tags (list<string>)
                - active (boolean)
                - createdAt (timestamp)
                """;
        ApiSpecification spec = new NaturalLanguageSpecGenerator().generate(request);
        EntityDefinition definition = spec.entities.get(0);

        FieldSpec metadata = definition.entity.fields.stream().filter(field -> "metadata".equals(field.name)).findFirst().orElseThrow();
        assertEquals("Map<String,Object>", metadata.type);

        FieldSpec tags = definition.entity.fields.stream().filter(field -> "tags".equals(field.name)).findFirst().orElseThrow();
        assertEquals("List<String>", tags.type);

        FieldSpec active = definition.entity.fields.stream().filter(field -> "active".equals(field.name)).findFirst().orElseThrow();
        assertEquals("Boolean", active.type);

        FieldSpec createdAt = definition.entity.fields.stream().filter(field -> "createdAt".equals(field.name)).findFirst().orElseThrow();
        assertEquals("LocalDateTime", createdAt.type);
    }
}
