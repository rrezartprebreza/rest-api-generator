package io.restapigen.generator.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluralizerTest {

    @Test
    void pluralizesRegularWords() {
        assertEquals("products", Pluralizer.pluralize("product"));
        assertEquals("users", Pluralizer.pluralize("user"));
    }

    @Test
    void handlesWordsEndingInSs() {
        assertEquals("addresses", Pluralizer.pluralize("address"));
        assertEquals("classes", Pluralizer.pluralize("class"));
    }

    @Test
    void handlesWordsEndingInUs() {
        assertEquals("statuses", Pluralizer.pluralize("status"));
        assertEquals("campuses", Pluralizer.pluralize("campus"));
    }

    @Test
    void handlesWordsEndingInIs() {
        assertEquals("analyses", Pluralizer.pluralize("analysis"));
        assertEquals("crises", Pluralizer.pluralize("crisis"));
    }

    @Test
    void handlesWordsEndingInShOrCh() {
        assertEquals("branches", Pluralizer.pluralize("branch"));
        assertEquals("dishes", Pluralizer.pluralize("dish"));
    }

    @Test
    void handlesWordsEndingInY() {
        assertEquals("categories", Pluralizer.pluralize("category"));
        assertEquals("days", Pluralizer.pluralize("day"));
    }

    @Test
    void leavesAlreadyPluralWords() {
        assertEquals("buses", Pluralizer.pluralize("buses"));
        assertEquals("products", Pluralizer.pluralize("products"));
    }

    @Test
    void handlesNullAndEmpty() {
        assertEquals("", Pluralizer.pluralize(null));
        assertEquals("", Pluralizer.pluralize(""));
        assertEquals("", Pluralizer.pluralize("  "));
    }
}
