package io.restapigen.generator.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RequestParsing {
    private static final Pattern ENTITY_EXPLICIT = Pattern.compile("(?im)^\\s*entity\\s*[:=]\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*$");
    private static final Pattern ENTITY_API_FOR = Pattern.compile("(?i)\\bapi\\s+for\\s+(?:an?\\s+|the\\s+)?([A-Za-z][A-Za-z0-9_-]*)\\b");
    private static final Pattern ENTITY_MANAGE = Pattern.compile("(?i)\\bmanage\\s+(?:an?\\s+|the\\s+)?([A-Za-z][A-Za-z0-9_-]*)\\b");
    private static final Pattern BULLET_FIELD = Pattern.compile("(?m)^\\s*[-*]\\s*([A-Za-z][A-Za-z0-9_]*)\\s*(?::|-)\\s*([A-Za-z][A-Za-z0-9]*)?\\s*(.*)$");
    private static final Pattern WITH_FIELDS = Pattern.compile("(?i)\\bwith\\b\\s+([^\\n\\r.]+)");
    private static final Pattern INLINE_TYPED = Pattern.compile("(?i)\\b([a-z][a-z0-9_]*)\\s*\\(\\s*([A-Za-z][A-Za-z0-9]*)\\s*\\)");
    private static final Pattern SEGMENT_SPLIT = Pattern.compile("(?m)(?:\\r?\\n){2,}");

    private RequestParsing() {}

    public static String extractEntityName(String request) {
        if (request == null) {
            return null;
        }
        Matcher explicit = ENTITY_EXPLICIT.matcher(request);
        if (explicit.find()) {
            return explicit.group(1);
        }
        Matcher apiFor = ENTITY_API_FOR.matcher(request);
        if (apiFor.find()) {
            return apiFor.group(1);
        }
        Matcher manage = ENTITY_MANAGE.matcher(request);
        if (manage.find()) {
            return manage.group(1);
        }

        Matcher withFields = WITH_FIELDS.matcher(request);
        if (withFields.find()) {
            String beforeWith = request.substring(0, withFields.start()).trim();
            String[] tokens = beforeWith.split("\\s+");
            for (int i = tokens.length - 1; i >= 0; i--) {
                String t = tokens[i].replaceAll("[^A-Za-z0-9_-]", "");
                if (looksLikeEntityToken(t)) {
                    return t;
                }
            }
        }
        return null;
    }

    public static List<ParsedField> extractFields(String request) {
        List<ParsedField> out = new ArrayList<>();
        if (request == null || request.isBlank()) {
            return out;
        }

        Matcher bullet = BULLET_FIELD.matcher(request);
        while (bullet.find()) {
            String name = bullet.group(1);
            String type = bullet.group(2);
            String tail = bullet.group(3) == null ? "" : bullet.group(3);
            out.add(new ParsedField(name, type, containsNullable(tail), containsUnique(tail)));
        }

        Matcher withFields = WITH_FIELDS.matcher(request);
        while (withFields.find()) {
            String list = withFields.group(1);
            if (list == null || list.isBlank()) {
                continue;
            }
            list = stripFieldListPrefix(list);
            for (String raw : splitFieldList(list)) {
                ParsedField parsed = parseSimpleFieldToken(raw);
                if (parsed != null) {
                    out.add(parsed);
                }
            }
        }

        Matcher inlineTyped = INLINE_TYPED.matcher(request);
        while (inlineTyped.find()) {
            out.add(new ParsedField(inlineTyped.group(1), inlineTyped.group(2), false, false));
        }

        return out;
    }

    public static List<String> splitIntoEntitySegments(String request) {
        if (request == null || request.isBlank()) {
            return List.of();
        }
        return Arrays.stream(SEGMENT_SPLIT.split(request))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    public static boolean containsDisableCrud(String lowerRequest) {
        return lowerRequest.contains("no crud")
                || lowerRequest.contains("without crud")
                || lowerRequest.contains("disable crud")
                || lowerRequest.contains("read-only")
                || lowerRequest.contains("readonly");
    }

    public static boolean containsDisablePagination(String lowerRequest) {
        return lowerRequest.contains("no pagination")
                || lowerRequest.contains("without pagination")
                || lowerRequest.contains("disable pagination");
    }

    public static boolean containsDisableSorting(String lowerRequest) {
        return lowerRequest.contains("no sorting")
                || lowerRequest.contains("without sorting")
                || lowerRequest.contains("disable sorting");
    }

    public record ParsedField(String name, String typeHint, boolean nullable, boolean unique) {}

    private static boolean looksLikeEntityToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        return !lower.equals("api")
                && !lower.equals("crud")
                && !lower.equals("rest")
                && !lower.equals("service")
                && !lower.equals("endpoint")
                && !lower.equals("endpoints");
    }

    private static List<String> splitFieldList(String list) {
        String normalized = list.replace(" and ", ",");
        String[] parts = normalized.split(",");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static String stripFieldListPrefix(String list) {
        return list.replaceFirst("(?i)^\\s*(fields?|attributes?)\\s*[:=-]?\\s*", "");
    }

    private static ParsedField parseSimpleFieldToken(String token) {
        String cleaned = token.replaceAll("[.]", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        Matcher parenType = Pattern.compile("(?i)^([A-Za-z][A-Za-z0-9_]*)\\s*\\(\\s*([A-Za-z][A-Za-z0-9]*)\\s*\\)\\s*(.*)$").matcher(cleaned);
        if (parenType.find()) {
            String name = parenType.group(1);
            String type = parenType.group(2);
            String tail = parenType.group(3) == null ? "" : parenType.group(3);
            return new ParsedField(name, type, containsNullable(tail), containsUnique(tail));
        }

        Matcher typed = Pattern.compile("(?i)^([A-Za-z][A-Za-z0-9_]*)\\s*(?::|-)\\s*([A-Za-z][A-Za-z0-9]*)\\s*(.*)$").matcher(cleaned);
        if (typed.find()) {
            String name = typed.group(1);
            String type = typed.group(2);
            String tail = typed.group(3) == null ? "" : typed.group(3);
            return new ParsedField(name, type, containsNullable(tail), containsUnique(tail));
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);
        boolean nullable = containsNullable(lower);
        boolean unique = containsUnique(lower);
        String nameOnly = cleaned.replaceAll("(?i)\\b(optional|nullable|unique)\\b", "").trim();
        nameOnly = nameOnly.replaceAll("[()]", "").trim();
        if (nameOnly.isEmpty()) {
            return null;
        }
        return new ParsedField(nameOnly, null, nullable, unique);
    }

    private static boolean containsNullable(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lower.contains("optional") || lower.contains("nullable") || lower.contains("null");
    }

    private static boolean containsUnique(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lower.contains("unique") || lower.contains("must be unique");
    }
}
