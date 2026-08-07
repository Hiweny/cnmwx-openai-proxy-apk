package com.hiweny.freeapiopenai;

import java.util.ArrayList;
import java.util.List;

class MessageParser {
    static class Parsed {
        boolean tickle;
        boolean tickleSelf;
        boolean recall;
        final List<String> parts = new ArrayList<>();
    }

    static Parsed parse(String raw) {
        Parsed parsed = new Parsed();
        if (raw == null) raw = "";
        String text = raw;
        if (text.contains("[tickle]")) {
            parsed.tickle = true;
            text = text.replace("[tickle]", "");
        }
        if (text.contains("[tickle_self]")) {
            parsed.tickleSelf = true;
            text = text.replace("[tickle_self]", "");
        }
        if (text.contains("[recall]")) {
            parsed.recall = true;
            text = text.replace("[recall]", "");
        }

        String[] split = text.trim().split("\\\\+n?|\\n+");
        for (String s : split) {
            String part = s.trim().replace("\n", " ");
            if (!part.isEmpty()) parsed.parts.add(part);
        }
        if (parsed.parts.isEmpty() && !text.trim().isEmpty()) {
            parsed.parts.add(text.trim());
        }
        if (parsed.parts.size() == 1 && parsed.parts.get(0).length() > 52) {
            parsed.parts.clear();
            splitLongText(text.trim(), parsed.parts);
        }
        return parsed;
    }

    private static void splitLongText(String text, List<String> out) {
        StringBuilder current = new StringBuilder();
        String[] sentences = text.split("(?<=[。！？!?~～…])");
        for (String sentence : sentences) {
            String s = sentence.trim();
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
