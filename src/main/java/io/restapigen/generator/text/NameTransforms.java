package io.restapigen.generator.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NameTransforms {
    private NameTransforms() {}

    public static String toPascalCase(String input) {
        List<String> parts = splitWords(input);
        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            out.append(capitalize(part.toLowerCase(Locale.ROOT)));
        }
        return out.toString();
    }

    public static String toCamelCase(String input) {
        String pascal = toPascalCase(input);
        if (pascal.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    public static String toSnakeCase(String input) {
        if (input == null) {
            return "";
        }
        String s = input.trim();
        if (s.isEmpty()) {
            return "";
        }
        s = s.replace('-', '_').replace(' ', '_');
        StringBuilder out = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                    out.append('_');
                }
                prev = c;
                continue;
            }
            if (Character.isUpperCase(c) && i > 0 && prev != '_' && !Character.isUpperCase(prev)) {
                out.append('_');
            }
            out.append(Character.toLowerCase(c));
            prev = c;
        }
        return trimRepeated(out.toString(), '_');
    }

    public static String toKebabCase(String input) {
        return toSnakeCase(input).replace('_', '-');
    }

    private static List<String> splitWords(String input) {
        if (input == null) {
            return List.of();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String normalized = trimmed.replace('-', ' ').replace('_', ' ');
        String[] tokens = normalized.split("\\s+");
        List<String> parts = new ArrayList<>();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            parts.add(token);
        }
        return parts;
    }

    private static String capitalize(String part) {
        if (part.isEmpty()) {
            return part;
        }
        return Character.toUpperCase(part.charAt(0)) + part.substring(1);
    }

    private static String trimRepeated(String input, char sep) {
        String s = input;
        while (s.startsWith(String.valueOf(sep))) {
            s = s.substring(1);
        }
        while (s.endsWith(String.valueOf(sep))) {
            s = s.substring(0, s.length() - 1);
        }
        while (s.contains("" + sep + sep)) {
            s = s.replace("" + sep + sep, String.valueOf(sep));
        }
        return s;
    }
}
