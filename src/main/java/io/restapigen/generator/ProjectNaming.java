package io.restapigen.generator;

import io.restapigen.domain.EntityDefinition;
import io.restapigen.generator.text.NameTransforms;
import io.restapigen.generator.text.Pluralizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectNaming {
    public static final String DEFAULT_PROJECT_NAME = "generated-api";
    public static final String DEFAULT_BASE_PACKAGE = "io.backend.generated";

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "(?i)\\b([A-Za-z][A-Za-z0-9]*(?:[ -][A-Za-z0-9]+){0,2})\\s+(api|application|app|platform|system|service|portal)\\b"
    );

    /**
     * Matches a domain label at the very start of the prompt, before a colon.
     * Examples: "e-commerce: ...", "Blog Platform: ...", "inventory management: ..."
     */
    private static final Pattern COLON_PREFIX_PATTERN = Pattern.compile(
            "^\\s*([A-Za-z][A-Za-z0-9 -]{1,40})\\s*:"
    );

    private static final Set<String> LEADING_NOISE = Set.of(
            "a", "an", "the", "my", "new", "simple", "complete", "modern", "production", "ready",
            "create", "generate", "build", "make", "design", "develop", "implement",
            "with", "for", "including", "having", "plus", "and", "or"
    );

    private static final Set<String> GENERIC_TOKENS = Set.of(
            "rest", "api", "application", "app", "platform", "system", "service", "portal", "backend"
    );

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while"
    );

    private ProjectNaming() {}

    public static String inferProjectName(String request, List<EntityDefinition> definitions) {
        String domainName = inferDomainName(request);
        if (domainName != null) {
            return NameTransforms.toKebabCase(domainName) + "-api";
        }
        if (definitions == null || definitions.isEmpty()) {
            return DEFAULT_PROJECT_NAME;
        }
        String plural = Pluralizer.pluralize(NameTransforms.toKebabCase(definitions.get(0).entity.name));
        return plural + "-api";
    }

    public static String inferBasePackage(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            return DEFAULT_BASE_PACKAGE;
        }

        String normalized = projectName
                .replaceAll("(?i)-api$", "")
                .replaceAll("(?i)-application$", "")
                .replaceAll("(?i)-service$", "")
                .trim();

        List<String> rawSegments = new ArrayList<>(List.of(NameTransforms.toSnakeCase(normalized).split("_")));
        List<String> mergedSegments = mergeSingleLetterPrefixes(rawSegments);
        List<String> segments = new ArrayList<>();
        for (String segment : mergedSegments) {
            String cleaned = sanitizePackageSegment(segment);
            if (cleaned.isBlank() || GENERIC_TOKENS.contains(cleaned)) {
                continue;
            }
            segments.add(cleaned);
        }

        if (segments.isEmpty()) {
            return DEFAULT_BASE_PACKAGE;
        }
        return "io.backend." + String.join(".", segments);
    }

    private static String inferDomainName(String request) {
        if (request == null || request.isBlank()) {
            return null;
        }
        // 1. Colon-prefix pattern: "e-commerce: ...", "Blog Platform: ..."
        Matcher colonMatcher = COLON_PREFIX_PATTERN.matcher(request);
        if (colonMatcher.find()) {
            String candidate = normalizeDomainCandidate(colonMatcher.group(1));
            if (candidate != null) {
                return candidate;
            }
        }
        // 2. Inline "X api / X platform / X system" pattern
        Matcher matcher = DOMAIN_PATTERN.matcher(request);
        while (matcher.find()) {
            String candidate = normalizeDomainCandidate(matcher.group(1));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String normalizeDomainCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String[] tokens = candidate.trim().split("\\s+");
        List<String> cleaned = new ArrayList<>();
        for (String token : tokens) {
            String normalized = token.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
            if (normalized.isBlank() || LEADING_NOISE.contains(normalized) || GENERIC_TOKENS.contains(normalized)) {
                continue;
            }
            cleaned.add(normalized);
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        return String.join(" ", cleaned);
    }

    private static List<String> mergeSingleLetterPrefixes(List<String> rawSegments) {
        List<String> merged = new ArrayList<>();
        for (int i = 0; i < rawSegments.size(); i++) {
            String current = rawSegments.get(i);
            if (current == null || current.isBlank()) {
                continue;
            }
            if (current.length() == 1 && i + 1 < rawSegments.size()) {
                String next = rawSegments.get(i + 1);
                if (next != null && !next.isBlank()) {
                    merged.add(current + next);
                    i++;
                    continue;
                }
            }
            merged.add(current);
        }
        return merged;
    }

    private static String sanitizePackageSegment(String segment) {
        if (segment == null) {
            return "";
        }
        String cleaned = segment.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (cleaned.isBlank()) {
            return "";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "app" + cleaned;
        }
        if (JAVA_KEYWORDS.contains(cleaned)) {
            cleaned = cleaned + "app";
        }
        return cleaned;
    }
}
