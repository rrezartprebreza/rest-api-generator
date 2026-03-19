package io.restapigen.generator.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestParsingTest {
    @Test
    void parsesWithFieldsPrefix() {
        String request = "Create an API for Book with fields: title, authorName, publishedDate.";
        var fields = RequestParsing.extractFields(request);
        assertEquals(3, fields.size());
        assertEquals("title", fields.get(0).name());
        assertEquals("authorName", fields.get(1).name());
        assertEquals("publishedDate", fields.get(2).name());
    }

    @Test
    void extractsEntityNameFromHeaderStyleSegment() {
        String request = """
                Category:
                - name (string, required)
                """;
        assertEquals("Category", RequestParsing.extractEntityName(request));
    }

    @Test
    void parsesRelationships() {
        String request = "Create API for Product with name. Product belongs to Category and has many Review and has many Tag (many-to-many).";
        var relations = RequestParsing.extractRelationships(request);
        assertEquals(3, relations.size());
        assertEquals("ManyToOne", relations.get(0).type());
        assertEquals("Category", relations.get(0).target());
        assertEquals("OneToMany", relations.get(1).type());
        assertEquals("ManyToMany", relations.get(2).type());
    }

    @Test
    void doesNotTreatRelationshipBulletsAsFields() {
        String request = """
                Create API for Product with:
                - name (string)
                - belongs to Category
                - has many Tag (many-to-many)
                """;
        var fields = RequestParsing.extractFields(request);

        assertTrue(fields.stream().anyMatch(field -> "name".equals(field.name())));
        assertTrue(fields.stream().noneMatch(field -> "belongsToCategory".equals(field.name())));
        assertTrue(fields.stream().noneMatch(field -> "hasManyTagManyToMany".equals(field.name())));
    }

    @Test
    void parsesValidationHintsWithoutColon() {
        String request = "Create API for User with fields: age (integer min 18 max 120), email (string valid email), status (enum ACTIVE, INACTIVE).";
        var fields = RequestParsing.extractFields(request);

        assertTrue(fields.stream().anyMatch(field -> "age".equals(field.name()) && Integer.valueOf(18).equals(field.min()) && Integer.valueOf(120).equals(field.max())));
        assertTrue(fields.stream().anyMatch(field -> "email".equals(field.name()) && "email".equalsIgnoreCase(field.format())));
        assertTrue(fields.stream().anyMatch(field -> "status".equals(field.name()) && field.enumValues().size() == 2));
    }

    @Test
    void parsesAdvancedTypeHints() {
        String request = "Create API for Product with: - tags (list<string>) - metadata (json) - active (boolean) - createdAt (timestamp)";
        var fields = RequestParsing.extractFields(request);

        assertTrue(fields.stream().anyMatch(field -> "tags".equals(field.name()) && "list<string>".equalsIgnoreCase(field.typeHint())));
        assertTrue(fields.stream().anyMatch(field -> "metadata".equals(field.name()) && "json".equalsIgnoreCase(field.typeHint())));
        assertTrue(fields.stream().anyMatch(field -> "active".equals(field.name()) && "boolean".equalsIgnoreCase(field.typeHint())));
        assertTrue(fields.stream().anyMatch(field -> "createdAt".equals(field.name()) && "timestamp".equalsIgnoreCase(field.typeHint())));
    }
}
