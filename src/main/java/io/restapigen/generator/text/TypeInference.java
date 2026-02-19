package io.restapigen.generator.text;

import java.util.Locale;

public final class TypeInference {
    private TypeInference() {}

    public static String normalizeType(String typeHint, String fieldNameCamel) {
        String name = fieldNameCamel == null ? "" : fieldNameCamel;
        String hinted = typeHint == null ? "" : typeHint.trim();
        if (!hinted.isEmpty()) {
            String normalized = hinted.toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "string" -> "String";
                case "long" -> "Long";
                case "integer", "int" -> "Integer";
                case "boolean", "bool" -> "Boolean";
                case "bigdecimal", "decimal", "money" -> "BigDecimal";
                case "double" -> "Double";
                case "localdate", "date" -> "LocalDate";
                case "localdatetime", "datetime", "timestamp" -> "LocalDateTime";
                default -> "String";
            };
        }

        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("email")) {
            return "String";
        }
        if (lower.endsWith("date")) {
            return "LocalDate";
        }
        if (lower.endsWith("at") || lower.endsWith("time") || lower.endsWith("timestamp")) {
            return "LocalDateTime";
        }
        if (lower.startsWith("is") || lower.startsWith("has")) {
            return "Boolean";
        }
        if (lower.contains("amount") || lower.contains("price") || lower.contains("cost") || lower.contains("balance")) {
            return "BigDecimal";
        }
        if (lower.contains("count") || lower.endsWith("number") || lower.endsWith("qty") || lower.endsWith("quantity") || lower.endsWith("age")) {
            return "Integer";
        }
        return "String";
    }

    public static boolean looksLikeNameField(String fieldNameCamel) {
        if (fieldNameCamel == null || fieldNameCamel.isBlank()) {
            return false;
        }
        String lower = fieldNameCamel.toLowerCase(Locale.ROOT);
        return lower.equals("name")
                || lower.endsWith("name")
                || lower.equals("firstname")
                || lower.equals("lastname")
                || lower.endsWith("firstname")
                || lower.endsWith("lastname");
    }
}
