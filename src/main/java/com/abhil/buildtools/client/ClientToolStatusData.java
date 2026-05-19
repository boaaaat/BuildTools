package com.abhil.buildtools.client;

import java.util.List;

public final class ClientToolStatusData {
    private static String title = "";
    private static List<String> lines = List.of();
    private static int accentColor;
    private static long updatedAtMillis;

    private ClientToolStatusData() {
    }

    public static void set(String newTitle, List<String> newLines, int newAccentColor) {
        title = newTitle;
        lines = List.copyOf(newLines);
        accentColor = newAccentColor;
        updatedAtMillis = System.currentTimeMillis();
    }

    public static void clear() {
        title = "";
        lines = List.of();
        accentColor = 0;
        updatedAtMillis = 0L;
    }

    public static boolean visible() {
        if (title.isEmpty()) {
            return false;
        }
        int autoHideSeconds = com.abhil.buildtools.config.BuildToolsClientConfig.OVERLAY_AUTO_HIDE_SECONDS.get();
        return autoHideSeconds == 0 || System.currentTimeMillis() - updatedAtMillis < autoHideSeconds * 1000L;
    }

    public static String title() {
        return title;
    }

    public static List<String> lines() {
        return lines;
    }

    public static int accentColor() {
        return accentColor;
    }
}
