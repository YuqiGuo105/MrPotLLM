package com.example.MrPot.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QaExtractor {

    private QaExtractor() {
    }

    /**
     * Pattern for Chinese-style markers: 【问题】...【回答】...
     */
    private static final Pattern CN_QA = Pattern.compile(
            "【问题】\\s*(.+?)\\s*【回答】\\s*(.+)",
            Pattern.DOTALL
    );

    /**
     * Pattern for bracket markers: [Q]... [A]...
     */
    private static final Pattern BRACKET_QA = Pattern.compile(
            "\\[Q\\]\\s*(.+?)\\s*\\[A\\]\\s*(.+)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern for colon markers: Q:... A:...
     */
    private static final Pattern COLON_QA = Pattern.compile(
            "(?is)\\bQ\\s*[:：]\\s*(.+?)\\s*\\bA\\s*[:：]\\s*(.+)"
    );

    public static Optional<QaPair> extract(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        Optional<QaPair> result = match(CN_QA, trimmed);
        if (result.isPresent()) {
            return result;
        }

        result = match(BRACKET_QA, trimmed);
        if (result.isPresent()) {
            return result;
        }

        result = match(COLON_QA, trimmed);
        if (result.isPresent()) {
            return result;
        }

        return Optional.empty();
    }

    private static Optional<QaPair> match(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String question = safe(matcher.group(1));
        String answer = safe(matcher.group(2));
        if (question.isEmpty() || answer.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QaPair(question, answer));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record QaPair(String question, String answer) {
    }
}
