package com.hiweny.freeapiopenai;

public class AdFilter {
    private static final String AD_WITH_BR = "欢迎使用 公益站! 站长合作邮箱：wxgpt@qq.com<br/>";
    private static final String AD = "欢迎使用 公益站! 站长合作邮箱：wxgpt@qq.com";

    public static String cleanDelta(String text) {
        if (text == null) return "";
        String cleaned = text
                .replace(AD_WITH_BR, "")
                .replace(AD, "")
                .replace("<br/>", "")
                .replace("<br>", "");
        if (cleaned.contains("站长合作邮箱") || cleaned.contains("欢迎使用 公益站")) {
            return "";
        }
        return cleaned;
    }

    public static String cleanAll(String text) {
        if (text == null) return "";
        String cleaned = text
                .replace(AD_WITH_BR, "")
                .replace(AD, "")
                .replace("<br/>", "\n")
                .replace("<br>", "\n");
        return cleaned.replaceAll("(?m)^.*站长合作邮箱.*$", "")
                .replaceAll("(?m)^.*欢迎使用 公益站.*$", "");
    }
}
