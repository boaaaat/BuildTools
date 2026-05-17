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
        return !title.isEmpty() && System.currentTimeMillis() - updatedAtMillis < 1500L;
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
