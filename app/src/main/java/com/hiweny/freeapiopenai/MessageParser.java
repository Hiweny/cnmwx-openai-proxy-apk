package com.hiweny.freeapiopenai;

import java.util.ArrayList;
import java.util.List;

class MessageParser {
    static class Parsed {
        boolean recall;
        final List<String> parts = new ArrayList<>();
    }

    static Parsed parse(String raw) {
        Parsed parsed = new Parsed();
        if (raw == null) raw = "";
        String text = raw;
        text = text.replace("[tickle]", "").replace("[tickle_self]", "");
        if (text.contains("[recall]")) {
            parsed.recall = true;
            text = text.replace("[recall]", "");
        }

        String[] split = normalizeSeparators(text).trim().split("\\n+");
        for (String s : split) {
            String part = cleanVisibleText(s.trim().replace("\n", " "));
            if (!part.isEmpty()) parsed.parts.add(part);
        }
        if (parsed.parts.isEmpty() && !text.trim().isEmpty()) {
            parsed.parts.add(cleanVisibleText(text.trim()));
        }
        if (parsed.parts.size() == 1 && parsed.parts.get(0).length() > 52) {
            parsed.parts.clear();
            splitLongText(normalizeSeparators(text).trim(), parsed.parts);
        }
        return parsed;
    }

    static String preview(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "正在连接...";
        String text = normalizeSeparators(raw)
                .replace("[recall]", "")
                .replace("[tickle]", "")
                .replace("[tickle_self]", "")
                .replace("\n", " ")
                .trim();
        return cleanVisibleText(text);
    }

    private static String normalizeSeparators(String text) {
        if (text == null) return "";
        String normalized = text.replace("\\n", "\n");
        normalized = normalized.replaceAll("\\\\+", "\n");
        normalized = normalized.replaceAll("(?<![A-Za-z0-9:])/+(?![A-Za-z0-9/])", "\n");
        normalized = normalized.replace("／", "\n");
        return normalized;
    }

    private static String cleanVisibleText(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static void splitLongText(String text, List<String> out) {
        StringBuilder current = new StringBuilder();
        String[] sentences = text.split("(?<=[。！？!?~～…])");
        for (String sentence : sentences) {
            String s = cleanVisibleText(sentence.trim());
            if (s.isEmpty()) continue;
            if (current.length() > 0 && current.length() + s.length() > 46) {
                out.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(s);
        }
        if (current.length() > 0) out.add(current.toString().trim());
        if (out.isEmpty()) out.add(text);
    }
}
