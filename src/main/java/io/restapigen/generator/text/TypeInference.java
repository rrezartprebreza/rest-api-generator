package io.restapigen.generator.text;

import java.util.Locale;

public final class TypeInference {
    private TypeInference() {}

    public static String normalizeType(String typeHint, String fieldNameCamel) {
        String name = fieldNameCamel == null ? "" : fieldNameCamel;
        String hinted = typeHint == null ? "" : typeHint.trim();
        if (!hinted.isEmpty()) {
            String normalized = hinted.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("list<") && normalized.endsWith(">")) {
                String inner = normalized.substring(5, normalized.length() - 1).trim();
                return "List<" + normalizeCollectionInnerType(inner) + ">";
            }
            if (normalized.startsWith("array<") && normalized.endsWith(">")) {
                String inner = normalized.substring(6, normalized.length() - 1).trim();
                return "List<" + normalizeCollectionInnerType(inner) + ">";
            }
            if (normalized.endsWith("[]")) {
                String inner = normalized.substring(0, normalized.length() - 2).trim();
                return "List<" + normalizeCollectionInnerType(inner) + ">";
            }
            return switch (normalized) {
                case "string" -> "String";
                case "long" -> "Long";
                case "integer", "int" -> "Integer";
                case "boolean", "bool" -> "Boolean";
                case "bigdecimal", "decimal", "money" -> "BigDecimal";
                case "float" -> "Float";
                case "double" -> "Double";
                case "localdate", "date" -> "LocalDate";
                case "localdatetime", "datetime", "timestamp" -> "LocalDateTime";
                case "json", "jsonb", "object", "map" -> "Map<String,Object>";
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

    private static String normalizeCollectionInnerType(String innerType) {
        return switch (innerType) {
            case "string" -> "String";
            case "integer", "int" -> "Integer";
            case "long" -> "Long";
            case "decimal", "bigdecimal", "money" -> "BigDecimal";
            case "boolean", "bool" -> "Boolean";
            case "date" -> "LocalDate";
            case "datetime", "timestamp" -> "LocalDateTime";
            default -> "String";
        };
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
