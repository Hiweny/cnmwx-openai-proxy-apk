package com.hiweny.freeapiopenai;

import java.util.regex.Pattern;

/**
 * Filter ad content from upstream API responses.
 * The upstream injects "欢迎使用 公益站! 站长合作邮箱：wxgpt@qq.com<br/>"
 * as the first SSE frame of every response.
 */
public class AdFilter {

    private static final String AD_FULL = "欢迎使用 公益站! 站长合作邮箱：wxgpt@qq.com";
    private static final String AD_WITH_BR = AD_FULL + "<br/>";
    private static final Pattern AD_PATTERN = Pattern.compile(
            "欢迎使用\\s*公益站!\\s*站长合作邮箱[：:]\\s*\\S+\\s*<br\\s*/?>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AD_LINE_PATTERN = Pattern.compile(
            ".*站长合作邮箱.*|.*欢迎使用\\s*公益站.*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Clean a single SSE delta chunk. Removes ad content and HTML line break tags.
     */
    public static String cleanDelta(String text) {
        if (text == null) return "";
        String cleaned = text;
        // Remove exact ad match
        cleaned = cleaned.replace(AD_WITH_BR, "");
        cleaned = cleaned.replace(AD_FULL, "");
        // Remove with regex for robustness
        cleaned = AD_PATTERN.matcher(cleaned).replaceAll("");
        // Remove HTML break tags
        cleaned = cleaned.replace("<br/>", "").replace("<br>", "");
        // Check if entire chunk is just ad content
        if (AD_LINE_PATTERN.matcher(cleaned.trim()).matches()) {
            return "";
        }
        return cleaned;
    }

    /**
     * Clean the full aggregated text. Removes ads and converts HTML breaks to newlines.
     */
    public static String cleanAll(String text) {
        if (text == null) return "";
        String cleaned = text;
        // Remove ad with regex
        cleaned = AD_PATTERN.matcher(cleaned).replaceAll("");
        // Remove standalone ad lines
        cleaned = AD_LINE_PATTERN.matcher(cleaned).replaceAll("");
        // Convert HTML breaks to newlines
        cleaned = cleaned.replace("<br/>", "\n").replace("<br>", "\n");
        // Clean up multiple consecutive newlines
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        return cleaned.trim();
    }

    /**
     * Check if a text chunk contains ad content.
     */
    public static boolean containsAd(String text) {
        if (text == null) return false;
        return text.contains("站长合作邮箱") || text.contains("公益站");
    }
}
