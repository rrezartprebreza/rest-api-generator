package io.restapigen.generator;

import io.restapigen.generator.text.NameTransforms;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EntityNameSuggester {
    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("(?i)project\\s+([A-Za-z][A-Za-z0-9_-]*)");

    private EntityNameSuggester() {}

    public static List<String> suggest(String request, String projectName) {
        Set<String> hints = new LinkedHashSet<>();
        addIfPresent(hints, fromProjectName(projectName));
        addIfPresent(hints, fromExplicitKeyword(request, PROJECT_NAME_PATTERN));
        addIfPresent(hints, fromFirstWord(request));
        if (hints.isEmpty()) {
            addIfPresent(hints, "Item");
        }
        return List.copyOf(hints);
    }

    private static String fromProjectName(String projectName) {
        if (projectName == null) {
            return null;
        }
        String trimmed = projectName.toLowerCase(Locale.ROOT).replaceAll("-api$", "");
        if (trimmed.isEmpty()) {
            return null;
        }
        return NameTransforms.toPascalCase(trimmed);
    }

    private static String fromExplicitKeyword(String request, Pattern pattern) {
        if (request == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(request);
        if (!matcher.find()) {
            return null;
        }
        return NameTransforms.toPascalCase(matcher.group(1));
    }

    private static String fromFirstWord(String request) {
        if (request == null) {
            return null;
        }
        String trimmed = request.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] tokens = trimmed.split("\\s+");
        return NameTransforms.toPascalCase(tokens[0]);
    }

    private static void addIfPresent(Set<String> hints, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        hints.add(candidate);
    }
}
