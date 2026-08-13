package com.abhil.buildtools.client;

import java.util.List;
import net.minecraft.network.chat.Component;

public final class ClientToolStatusData {
    private static Component title = Component.empty();
    private static List<Component> lines = List.of();
    private static int accentColor;
    private static long updatedAtMillis;

    private ClientToolStatusData() {
    }

    public static void set(Component newTitle, List<Component> newLines, int newAccentColor) {
        title = newTitle;
        lines = List.copyOf(newLines);
        accentColor = newAccentColor;
        updatedAtMillis = System.currentTimeMillis();
    }

    public static void clear() {
        title = Component.empty();
        lines = List.of();
        accentColor = 0;
        updatedAtMillis = 0L;
    }

    public static boolean visible() {
        if (title.getString().isEmpty()) {
            return false;
        }
        int autoHideSeconds = com.abhil.buildtools.config.BuildToolsClientConfig.OVERLAY_AUTO_HIDE_SECONDS.get();
        return autoHideSeconds == 0 || System.currentTimeMillis() - updatedAtMillis < autoHideSeconds * 1000L;
    }

    public static Component title() {
        return title;
    }

    public static List<Component> lines() {
        return lines;
    }

    public static int accentColor() {
        return accentColor;
    }
}
