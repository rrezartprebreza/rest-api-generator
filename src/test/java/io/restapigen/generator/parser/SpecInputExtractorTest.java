package io.restapigen.generator.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpecInputExtractorTest {
    @Test
    void extractsUserRequestSectionWhenPresent() {
        String input = """
                ========================
                USER REQUEST
                ========================
                Create a CRUD API for Book with title, authorName.
                ========================
                FINAL INSTRUCTION
                ========================
                Generate the JSON API specification now.
                """;

        assertEquals("Create a CRUD API for Book with title, authorName.", SpecInputExtractor.extractUserRequestOrWholeInput(input));
    }

    @Test
    void returnsWholeInputWhenNoUserRequestHeader() {
        String input = "Create a CRUD API for Book with title.";
        assertEquals(input, SpecInputExtractor.extractUserRequestOrWholeInput(input));
    }
}
