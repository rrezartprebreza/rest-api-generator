package io.restapigen.generator.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void parsesRelationships() {
        String request = "Create API for Product with name. Product belongs to Category and has many Review and has many Tag (many-to-many).";
        var relations = RequestParsing.extractRelationships(request);
        assertEquals(3, relations.size());
        assertEquals("ManyToOne", relations.get(0).type());
        assertEquals("Category", relations.get(0).target());
        assertEquals("OneToMany", relations.get(1).type());
        assertEquals("ManyToMany", relations.get(2).type());
    }
}
