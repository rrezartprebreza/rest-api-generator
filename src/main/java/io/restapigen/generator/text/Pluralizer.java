package io.restapigen.generator.text;

import java.util.Locale;

public final class Pluralizer {
    private Pluralizer() {}

    public static String pluralize(String singular) {
        if (singular == null) {
            return "";
        }
        String s = singular.trim();
        if (s.isEmpty()) {
            return "";
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.endsWith("s")) {
            return s;
        }
        if (lower.endsWith("ch") || lower.endsWith("sh") || lower.endsWith("x") || lower.endsWith("z")) {
            return s + "es";
        }
        if (lower.endsWith("y") && s.length() >= 2) {
            char beforeY = lower.charAt(lower.length() - 2);
            if (!isVowel(beforeY)) {
                return s.substring(0, s.length() - 1) + "ies";
            }
        }
        return s + "s";
    }

    private static boolean isVowel(char c) {
        return switch (Character.toLowerCase(c)) {
            case 'a', 'e', 'i', 'o', 'u' -> true;
            default -> false;
        };
    }
}
