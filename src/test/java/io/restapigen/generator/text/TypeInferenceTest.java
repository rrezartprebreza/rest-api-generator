package io.restapigen.generator.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeInferenceTest {

    @Test
    void normalizesFloatType() {
        assertEquals("Float", TypeInference.normalizeType("float", "value"));
    }

    @Test
    void normalizesCommonTypes() {
        assertEquals("String", TypeInference.normalizeType("string", "field"));
        assertEquals("Integer", TypeInference.normalizeType("int", "field"));
        assertEquals("Long", TypeInference.normalizeType("long", "field"));
        assertEquals("Boolean", TypeInference.normalizeType("boolean", "field"));
        assertEquals("BigDecimal", TypeInference.normalizeType("decimal", "field"));
        assertEquals("Double", TypeInference.normalizeType("double", "field"));
        assertEquals("LocalDate", TypeInference.normalizeType("date", "field"));
        assertEquals("LocalDateTime", TypeInference.normalizeType("timestamp", "field"));
        assertEquals("Map<String,Object>", TypeInference.normalizeType("json", "field"));
    }

    @Test
    void infersTypeFromFieldName() {
        assertEquals("LocalDate", TypeInference.normalizeType(null, "birthDate"));
        assertEquals("LocalDateTime", TypeInference.normalizeType(null, "createdAt"));
        assertEquals("Boolean", TypeInference.normalizeType(null, "isActive"));
        assertEquals("BigDecimal", TypeInference.normalizeType(null, "totalPrice"));
        assertEquals("Integer", TypeInference.normalizeType(null, "itemCount"));
    }

    @Test
    void normalizesListTypes() {
        assertEquals("List<String>", TypeInference.normalizeType("list<string>", "tags"));
        assertEquals("List<Integer>", TypeInference.normalizeType("list<int>", "ids"));
    }
}
