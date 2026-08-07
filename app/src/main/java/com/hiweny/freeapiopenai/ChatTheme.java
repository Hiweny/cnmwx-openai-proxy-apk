package com.hiweny.freeapiopenai;

import android.graphics.Color;

class ChatTheme {
    final String id;
    final String name;
    final boolean dark;
    final int primary;
    final int headerBg;
    final int chatBg;
    final int bubbleUser;
    final int bubbleAi;
    final int bubbleUserText;
    final int bubbleAiText;
    final int textPrimary;
    final int textSecondary;
    final int textMuted;
    final int border;
    final int avatarUser;
    final int avatarAi;
    final int inputBg;
    final int inputBorder;
    final int sendButton;
    final int sendButtonText;
    final int overlay;
    final int bubbleRadius;
    final int avatarRadius;
    final int inputRadius;
    final boolean bubbleArrow;
    final boolean avatarCircle;

    ChatTheme(String id, String name, boolean dark, int primary, int headerBg, int chatBg,
              int bubbleUser, int bubbleAi, int bubbleUserText, int bubbleAiText,
              int textPrimary, int textSecondary, int textMuted, int border,
              int avatarUser, int avatarAi, int inputBg, int inputBorder,
              int sendButton, int sendButtonText, int overlay,
              int bubbleRadius, int avatarRadius, int inputRadius,
              boolean bubbleArrow, boolean avatarCircle) {
        this.id = id;
        this.name = name;
        this.dark = dark;
        this.primary = primary;
        this.headerBg = headerBg;
        this.chatBg = chatBg;
        this.bubbleUser = bubbleUser;
        this.bubbleAi = bubbleAi;
        this.bubbleUserText = bubbleUserText;
        this.bubbleAiText = bubbleAiText;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.textMuted = textMuted;
        this.border = border;
        this.avatarUser = avatarUser;
        this.avatarAi = avatarAi;
        this.inputBg = inputBg;
        this.inputBorder = inputBorder;
        this.sendButton = sendButton;
        this.sendButtonText = sendButtonText;
        this.overlay = overlay;
        this.bubbleRadius = bubbleRadius;
        this.avatarRadius = avatarRadius;
        this.inputRadius = inputRadius;
        this.bubbleArrow = bubbleArrow;
        this.avatarCircle = avatarCircle;
    }

    static ChatTheme byId(String id) {
        for (ChatTheme t : all()) if (t.id.equals(id)) return t;
        return WECHAT_DARK;
    }

    static ChatTheme[] all() {
        return new ChatTheme[]{WECHAT_DARK, WECHAT, QQ, QQ_DARK, IOS, IOS_DARK, TELEGRAM, TELEGRAM_DARK, DISCORD};
    }

    static final ChatTheme WECHAT_DARK = new ChatTheme(
            "wechat_dark", "微信深色", true,
            Color.rgb(7, 193, 96), Color.rgb(25, 25, 25), Color.rgb(17, 17, 17),
            Color.rgb(42, 181, 86), Color.rgb(38, 38, 38),
            Color.WHITE, Color.rgb(238, 238, 238),
            Color.rgb(242, 242, 242), Color.rgb(170, 170, 170), Color.rgb(118, 118, 118),
            Color.rgb(47, 47, 47), Color.rgb(7, 193, 96), Color.rgb(7, 193, 96),
            Color.rgb(35, 35, 35), Color.rgb(62, 62, 62),
            Color.rgb(7, 193, 96), Color.WHITE, Color.argb(105, 0, 0, 0),
            6, 6, 18, true, false
    );

    static final ChatTheme WECHAT = new ChatTheme(
            "wechat", "微信", false,
            Color.rgb(7, 193, 96), Color.rgb(245, 245, 245), Color.rgb(245, 245, 245),
            Color.rgb(149, 236, 105), Color.WHITE,
            Color.BLACK, Color.BLACK,
            Color.rgb(25, 25, 25), Color.rgb(102, 102, 102), Color.rgb(178, 178, 178),
            Color.rgb(214, 214, 214), Color.rgb(7, 193, 96), Color.rgb(7, 193, 96),
            Color.WHITE, Color.rgb(229, 229, 229),
            Color.rgb(7, 193, 96), Color.WHITE, Color.argb(30, 255, 255, 255),
            4, 4, 4, true, false
    );

    static final ChatTheme QQ = new ChatTheme(
            "qq", "QQ", false,
            Color.rgb(18, 183, 245), Color.WHITE, Color.rgb(245, 246, 247),
            Color.rgb(18, 183, 245), Color.WHITE,
            Color.WHITE, Color.rgb(51, 51, 51),
            Color.rgb(51, 51, 51), Color.rgb(102, 102, 102), Color.rgb(153, 153, 153),
            Color.rgb(232, 232, 232), Color.rgb(18, 183, 245), Color.rgb(255, 149, 0),
            Color.WHITE, Color.rgb(232, 232, 232),
            Color.rgb(18, 183, 245), Color.WHITE, Color.argb(26, 18, 183, 245),
            18, 21, 20, false, true
    );

    static final ChatTheme QQ_DARK = new ChatTheme(
            "qq_dark", "QQ 深色", true,
            Color.rgb(18, 183, 245), Color.rgb(20, 28, 38), Color.rgb(16, 22, 31),
            Color.rgb(18, 145, 220), Color.rgb(31, 41, 55),
            Color.WHITE, Color.rgb(235, 241, 247),
            Color.rgb(240, 246, 252), Color.rgb(168, 181, 196), Color.rgb(108, 122, 137),
            Color.rgb(48, 60, 76), Color.rgb(18, 183, 245), Color.rgb(255, 149, 0),
            Color.rgb(27, 37, 50), Color.rgb(58, 72, 90),
            Color.rgb(18, 183, 245), Color.WHITE, Color.argb(105, 0, 0, 0),
            18, 21, 20, false, true
    );

    static final ChatTheme IOS = new ChatTheme(
            "ios", "iOS", false,
            Color.rgb(0, 122, 255), Color.WHITE, Color.WHITE,
            Color.rgb(0, 122, 255), Color.rgb(233, 233, 235),
            Color.WHITE, Color.BLACK,
            Color.BLACK, Color.rgb(142, 142, 147), Color.rgb(199, 199, 204),
            Color.rgb(198, 198, 200), Color.rgb(0, 122, 255), Color.rgb(52, 199, 89),
            Color.rgb(242, 242, 247), Color.rgb(198, 198, 200),
            Color.rgb(0, 122, 255), Color.WHITE, Color.argb(20, 0, 122, 255),
            18, 21, 18, false, true
    );

    static final ChatTheme IOS_DARK = new ChatTheme(
            "ios_dark", "iOS 深色", true,
            Color.rgb(10, 132, 255), Color.rgb(18, 18, 20), Color.rgb(0, 0, 0),
            Color.rgb(10, 132, 255), Color.rgb(44, 44, 46),
            Color.WHITE, Color.rgb(242, 242, 247),
            Color.rgb(245, 245, 247), Color.rgb(174, 174, 178), Color.rgb(99, 99, 102),
            Color.rgb(58, 58, 60), Color.rgb(10, 132, 255), Color.rgb(48, 209, 88),
            Color.rgb(28, 28, 30), Color.rgb(72, 72, 74),
            Color.rgb(10, 132, 255), Color.WHITE, Color.argb(115, 0, 0, 0),
            18, 21, 18, false, true
    );

    static final ChatTheme TELEGRAM = new ChatTheme(
            "telegram", "Telegram", false,
            Color.rgb(42, 171, 238), Color.rgb(81, 125, 162), Color.rgb(230, 235, 238),
            Color.rgb(239, 253, 222), Color.WHITE,
            Color.BLACK, Color.BLACK,
            Color.BLACK, Color.rgb(112, 132, 153), Color.rgb(160, 173, 184),
            Color.rgb(218, 220, 224), Color.rgb(42, 171, 238), Color.rgb(255, 87, 34),
            Color.WHITE, Color.rgb(218, 220, 224),
            Color.rgb(42, 171, 238), Color.WHITE, Color.argb(25, 42, 171, 238),
            12, 21, 20, true, true
    );

    static final ChatTheme TELEGRAM_DARK = new ChatTheme(
            "telegram_dark", "Telegram 深色", true,
            Color.rgb(42, 171, 238), Color.rgb(33, 45, 62), Color.rgb(15, 23, 31),
            Color.rgb(44, 90, 72), Color.rgb(30, 41, 53),
            Color.rgb(232, 255, 232), Color.rgb(232, 238, 244),
            Color.rgb(236, 242, 248), Color.rgb(154, 176, 195), Color.rgb(112, 132, 153),
            Color.rgb(50, 65, 78), Color.rgb(42, 171, 238), Color.rgb(255, 112, 67),
            Color.rgb(30, 41, 53), Color.rgb(61, 79, 92),
            Color.rgb(42, 171, 238), Color.WHITE, Color.argb(108, 0, 0, 0),
            12, 21, 20, true, true
    );

    static final ChatTheme DISCORD = new ChatTheme(
            "discord", "Discord", true,
            Color.rgb(88, 101, 242), Color.rgb(49, 51, 56), Color.rgb(49, 51, 56),
            Color.rgb(88, 101, 242), Color.rgb(56, 58, 64),
            Color.WHITE, Color.rgb(219, 222, 225),
            Color.rgb(242, 243, 245), Color.rgb(181, 186, 193), Color.rgb(148, 155, 164),
            Color.rgb(63, 65, 71), Color.rgb(88, 101, 242), Color.rgb(87, 242, 135),
            Color.rgb(56, 58, 64), Color.rgb(30, 31, 34),
            Color.rgb(88, 101, 242), Color.WHITE, Color.argb(35, 0, 0, 0),
            8, 21, 8, false, true
    );
}
