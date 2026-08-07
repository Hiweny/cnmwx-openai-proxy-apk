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

        String[] split = text.trim().split("\\\\+n?|\\n{2,}");
        for (String s : split) {
            String part = s.trim().replace("\n", " ");
            if (!part.isEmpty()) parsed.parts.add(part);
        }
        if (parsed.parts.isEmpty() && !text.trim().isEmpty()) {
            parsed.parts.add(text.trim());
        }
        return parsed;
    }
}
