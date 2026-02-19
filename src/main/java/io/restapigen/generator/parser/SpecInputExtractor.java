package io.restapigen.generator.parser;

import java.util.Locale;

public final class SpecInputExtractor {
    private SpecInputExtractor() {}

    public static String extractUserRequestOrWholeInput(String rawInput) {
        if (rawInput == null) {
            return "";
        }
        String input = rawInput.trim();
        if (input.isEmpty()) {
            return "";
        }

        String lower = input.toLowerCase(Locale.ROOT);
        int userRequestIndex = lower.indexOf("user request");
        if (userRequestIndex < 0) {
            return input;
        }

        int start = input.indexOf('\n', userRequestIndex);
        if (start < 0) {
            return input;
        }
        start = skipDelimitersAndBlankLines(input, start + 1);

        int end = indexOfNextDelimiter(lower, start);
        String extracted = (end < 0 ? input.substring(start) : input.substring(start, end)).trim();

        if (extracted.equals("{{USER_REQUEST}}") || extracted.equalsIgnoreCase("{{user_request}}") || extracted.isBlank()) {
            return "";
        }
        return extracted;
    }

    private static int skipDelimitersAndBlankLines(String input, int index) {
        int i = Math.max(0, index);
        while (i < input.length()) {
            int lineEnd = input.indexOf('\n', i);
            if (lineEnd < 0) {
                lineEnd = input.length();
            }
            String line = input.substring(i, lineEnd).trim();
            if (!line.isEmpty() && !line.startsWith("=")) {
                return i;
            }
            i = Math.min(input.length(), lineEnd + 1);
        }
        return i;
    }

    private static int indexOfNextDelimiter(String lowerInput, int fromIndex) {
        int nextSeparator = lowerInput.indexOf("========================", fromIndex);
        int nextFinalInstruction = lowerInput.indexOf("final instruction", fromIndex);
        if (nextSeparator < 0) {
            return nextFinalInstruction;
        }
        if (nextFinalInstruction < 0) {
            return nextSeparator;
        }
        return Math.min(nextSeparator, nextFinalInstruction);
    }
}
