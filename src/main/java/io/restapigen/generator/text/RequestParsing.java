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
    private static final Pattern BELONGS_TO = Pattern.compile("(?i)\\bbelongs\\s+to\\s+([A-Za-z][A-Za-z0-9_]*)\\b");
    private static final Pattern HAS_MANY = Pattern.compile("(?i)\\bhas\\s+many\\s+([A-Za-z][A-Za-z0-9_]*)(?:\\s*\\(([^)]+)\\))?");

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
            out.add(new ParsedField(
                    name,
                    type,
                    containsNullable(tail),
                    containsUnique(tail),
                    extractMin(tail),
                    extractMax(tail),
                    extractFormat(tail),
                    containsEncrypted(tail),
                    extractEnumValues(tail),
                    extractDefault(tail),
                    extractCalculated(tail)
            ));
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
            out.add(new ParsedField(inlineTyped.group(1), inlineTyped.group(2), false, false, null, null, null, false, List.of(), null, null));
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

    public static List<ParsedRelationship> extractRelationships(String request) {
        List<ParsedRelationship> out = new ArrayList<>();
        if (request == null || request.isBlank()) {
            return out;
        }
        Matcher belongsTo = BELONGS_TO.matcher(request);
        while (belongsTo.find()) {
            String target = belongsTo.group(1);
            out.add(new ParsedRelationship("ManyToOne", target, target));
        }

        Matcher hasMany = HAS_MANY.matcher(request);
        while (hasMany.find()) {
            String target = hasMany.group(1);
            String hint = hasMany.group(2) == null ? "" : hasMany.group(2).toLowerCase(Locale.ROOT);
            String type = hint.contains("many-to-many") ? "ManyToMany" : "OneToMany";
            out.add(new ParsedRelationship(type, target, target + "List"));
        }
        return out;
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

    public record ParsedField(
            String name,
            String typeHint,
            boolean nullable,
            boolean unique,
            Integer min,
            Integer max,
            String format,
            boolean encrypted,
            List<String> enumValues,
            String defaultValue,
            String calculatedExpression
    ) {}
    public record ParsedRelationship(String type, String target, String fieldName) {}

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
            return new ParsedField(
                    name,
                    type,
                    containsNullable(tail),
                    containsUnique(tail),
                    extractMin(tail),
                    extractMax(tail),
                    extractFormat(tail),
                    containsEncrypted(tail),
                    extractEnumValues(tail),
                    extractDefault(tail),
                    extractCalculated(tail)
            );
        }

        Matcher typed = Pattern.compile("(?i)^([A-Za-z][A-Za-z0-9_]*)\\s*(?::|-)\\s*([A-Za-z][A-Za-z0-9]*)\\s*(.*)$").matcher(cleaned);
        if (typed.find()) {
            String name = typed.group(1);
            String type = typed.group(2);
            String tail = typed.group(3) == null ? "" : typed.group(3);
            return new ParsedField(
                    name,
                    type,
                    containsNullable(tail),
                    containsUnique(tail),
                    extractMin(tail),
                    extractMax(tail),
                    extractFormat(tail),
                    containsEncrypted(tail),
                    extractEnumValues(tail),
                    extractDefault(tail),
                    extractCalculated(tail)
            );
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);
        boolean nullable = containsNullable(lower);
        boolean unique = containsUnique(lower);
        String nameOnly = cleaned.replaceAll("(?i)\\b(optional|nullable|unique)\\b", "").trim();
        nameOnly = nameOnly.replaceAll("[()]", "").trim();
        if (nameOnly.isEmpty()) {
            return null;
        }
        return new ParsedField(
                nameOnly,
                null,
                nullable,
                unique,
                extractMin(cleaned),
                extractMax(cleaned),
                extractFormat(cleaned),
                containsEncrypted(cleaned),
                extractEnumValues(cleaned),
                extractDefault(cleaned),
                extractCalculated(cleaned)
        );
    }

    private static boolean containsNullable(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lower.contains("optional") || lower.contains("nullable") || lower.contains("null");
    }

    private static boolean containsUnique(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lower.contains("unique") || lower.contains("must be unique");
    }

    private static boolean containsEncrypted(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lower.contains("encrypted");
    }

    private static Integer extractMin(String text) {
        return extractInt(text, "(?i)\\bmin\\s*[:=]\\s*(\\d+)");
    }

    private static Integer extractMax(String text) {
        return extractInt(text, "(?i)\\bmax\\s*[:=]\\s*(\\d+)");
    }

    private static String extractFormat(String text) {
        Matcher matcher = Pattern.compile("(?i)\\bformat\\s*[:=]\\s*([A-Za-z0-9_-]+)").matcher(text == null ? "" : text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static List<String> extractEnumValues(String text) {
        Matcher matcher = Pattern.compile("(?i)\\benum\\s*[:=]\\s*([A-Za-z0-9_,\\s-]+)").matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return List.of();
        }
        String[] parts = matcher.group(1).split(",");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (!value.isEmpty()) {
                out.add(value.replaceAll("[^A-Za-z0-9_]", ""));
            }
        }
        return out;
    }

    private static String extractDefault(String text) {
        Matcher matcher = Pattern.compile("(?i)\\bdefault\\s*[:=]\\s*([A-Za-z0-9_\\-]+)").matcher(text == null ? "" : text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractCalculated(String text) {
        Matcher matcher = Pattern.compile("(?i)\\bcalculated(?:\\s*[:=]\\s*([^,]+))?").matcher(text == null ? "" : text);
        if (matcher.find()) {
            String value = matcher.group(1);
            return value == null ? "true" : value.trim();
        }
        return null;
    }

    private static Integer extractInt(String text, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
