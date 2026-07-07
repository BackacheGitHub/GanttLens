package com.ganttlens.parser;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preprocesses PlantUML Gantt content before ANTLR parsing.
 * Handles syntax that would cause lexer conflicts or uses non-Gantt sub-languages.
 */
public class GanttPreprocessor {

    private static final Pattern DEFINE_PATTERN =
        Pattern.compile("^!define\\s+(\\w+)\\s+(.+)$");
    private static final Pattern STYLE_BLOCK_PATTERN =
        Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL);
    private static final Pattern CLOSED_BG_COLOR_PATTERN =
        Pattern.compile("closed\\s*\\{[^}]*BackgroundColor\\s+(\\w+)[^}]*\\}", Pattern.DOTALL);
    private static final Pattern TODAY_IS_DATE_PATTERN =
        Pattern.compile("^today\\s+is\\s+(\\d{4}-\\d{2}-\\d{2})\\s*$");
    private static final Pattern TODAY_IS_COLORED_PATTERN =
        Pattern.compile("^today\\s+is\\s+colored\\s+in\\s+(\\w+)\\s*$");
    private static final Pattern DATE_IS_COLORED_PATTERN =
        Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})\\s+is\\s+colored\\s+in\\s+(\\w+)\\s*$");

    /**
     * Result of preprocessing: cleaned content + extracted metadata.
     */
    public record PreprocessResult(
        String content,
        String closedDayColor,
        Map<LocalDate, String> dateColors
    ) {}

    /**
     * Preprocesses raw PlantUML Gantt content.
     * Strips !define macros, style blocks, today directives, and date coloring lines.
     */
    public static PreprocessResult preprocess(String rawContent) {
        // Phase 1: Extract !define macros
        Map<String, String> macros = new LinkedHashMap<>();
        StringBuilder afterDefines = new StringBuilder();
        for (String line : rawContent.split("\n", -1)) {
            Matcher m = DEFINE_PATTERN.matcher(line.trim());
            if (m.matches()) {
                macros.put(m.group(1), m.group(2).trim());
            } else {
                afterDefines.append(line).append("\n");
            }
        }

        // Phase 2: Macro replacement
        String content = afterDefines.toString();
        for (Map.Entry<String, String> entry : macros.entrySet()) {
            content = content.replace(entry.getKey(), entry.getValue());
        }

        // Phase 3: Extract <style> blocks and parse closedDayColor
        String closedDayColor = null;
        Matcher styleMatcher = STYLE_BLOCK_PATTERN.matcher(content);
        if (styleMatcher.find()) {
            String styleBlock = styleMatcher.group(1);
            Matcher bgMatcher = CLOSED_BG_COLOR_PATTERN.matcher(styleBlock);
            if (bgMatcher.find()) {
                closedDayColor = bgMatcher.group(1);
            }
            content = styleMatcher.replaceAll("");
        }

        // Phase 4: Process line by line - extract today/date-color directives
        Map<LocalDate, String> dateColors = new LinkedHashMap<>();
        StringBuilder cleaned = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                cleaned.append(line).append("\n");
                continue;
            }

            // today is DATE
            Matcher todayDate = TODAY_IS_DATE_PATTERN.matcher(trimmed);
            if (todayDate.matches()) {
                // Silently discard
                continue;
            }

            // today is colored in COLOR
            Matcher todayColored = TODAY_IS_COLORED_PATTERN.matcher(trimmed);
            if (todayColored.matches()) {
                // Silently discard
                continue;
            }

            // DATE is colored in COLOR
            Matcher dateColored = DATE_IS_COLORED_PATTERN.matcher(trimmed);
            if (dateColored.matches()) {
                LocalDate date = LocalDate.parse(dateColored.group(1));
                String color = dateColored.group(2);
                dateColors.put(date, color);
                continue;
            }

            cleaned.append(line).append("\n");
        }

        // Remove trailing newline added by processing
        String finalContent = cleaned.toString();
        if (finalContent.endsWith("\n") && !rawContent.endsWith("\n")) {
            finalContent = finalContent.substring(0, finalContent.length() - 1);
        }

        return new PreprocessResult(finalContent, closedDayColor, dateColors);
    }
}
