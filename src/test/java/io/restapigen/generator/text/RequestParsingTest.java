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
}
