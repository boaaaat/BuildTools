package com.abhil.buildtools.client;

import java.util.ArrayList;
import java.util.List;
import com.abhil.buildtools.config.BuildToolsClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class BuildToolStatusOverlay {
    private static final int PANEL_BACKGROUND = 0xAA101820;
    private static final int PANEL_BORDER = 0xCC263442;
    private static final int TEXT = 0xFFEAF2F8;
    private static final int MUTED_TEXT = 0xFFC5D0D8;
    private static final int MAX_WIDTH = 220;

    private BuildToolStatusOverlay() {
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen != null || !BuildToolsClientConfig.OVERLAY_ENABLED.get() || !ClientToolStatusData.visible()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = minecraft.font;
        List<String> statusLines = filteredLines(ClientToolStatusData.lines());
        List<String> lines = wrappedLines(font, statusLines, MAX_WIDTH - 20);
        int width = Math.max(110, Math.min(MAX_WIDTH, contentWidth(font, ClientToolStatusData.title(), lines) + 20));
        int height = 22 + lines.size() * 10 + 8;
        double scale = BuildToolsClientConfig.OVERLAY_SCALE.get();
        int scaledWidth = (int) Math.ceil(width * scale);
        int scaledHeight = (int) Math.ceil(height * scale);
        int x = overlayX(guiGraphics.guiWidth(), scaledWidth);
        int y = overlayY(guiGraphics.guiHeight(), scaledHeight);
        int accent = 0xFF000000 | ClientToolStatusData.accentColor();

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale((float) scale, (float) scale, 1.0F);
        x = 0;
        y = 0;
        guiGraphics.fill(x, y, x + width, y + height, PANEL_BACKGROUND);
        guiGraphics.fill(x, y, x + 3, y + height, accent);
        guiGraphics.hLine(x, x + width - 1, y, PANEL_BORDER);
        guiGraphics.hLine(x, x + width - 1, y + height - 1, PANEL_BORDER);
        guiGraphics.vLine(x + width - 1, y, y + height - 1, PANEL_BORDER);

        guiGraphics.drawString(font, ClientToolStatusData.title(), x + 9, y + 7, TEXT, false);
        int lineY = y + 21;
        for (String line : lines) {
            guiGraphics.drawString(font, line, x + 9, lineY, MUTED_TEXT, false);
            lineY += 10;
        }
        guiGraphics.pose().popPose();
    }

    private static List<String> filteredLines(List<String> lines) {
        boolean showMaterials = BuildToolsClientConfig.SHOW_OVERLAY_MATERIALS.get();
        boolean showLimits = BuildToolsClientConfig.SHOW_OVERLAY_LIMITS.get();
        List<String> filtered = new ArrayList<>();
        for (String line : lines) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (!showMaterials && (lower.contains("need") || lower.contains("missing") || lower.contains("materials"))) {
                continue;
            }
            if (!showLimits && (lower.contains("limit") || lower.contains("warning") || lower.contains("too far") || lower.contains("unloaded"))) {
                continue;
            }
            filtered.add(line);
        }
        return filtered;
    }

    private static int overlayX(int screenWidth, int width) {
        return switch (BuildToolsClientConfig.OVERLAY_POSITION.get()) {
            case BOTTOM_RIGHT, TOP_RIGHT -> Math.max(8, screenWidth - width - 8);
            default -> 8;
        };
    }

    private static int overlayY(int screenHeight, int height) {
        return switch (BuildToolsClientConfig.OVERLAY_POSITION.get()) {
            case TOP_LEFT, TOP_RIGHT -> 8;
            default -> Math.max(8, screenHeight - height - 72);
        };
    }

    private static List<String> wrappedLines(Font font, List<String> source, int maxWidth) {
        List<String> result = new ArrayList<>();
        for (String line : source) {
            if (font.width(line) <= maxWidth) {
                result.add(line);
                continue;
            }

            StringBuilder current = new StringBuilder();
            for (String word : line.split(" ")) {
                String next = current.isEmpty() ? word : current + " " + word;
                if (font.width(next) > maxWidth && !current.isEmpty()) {
                    result.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(next);
                }
            }
            if (!current.isEmpty()) {
                result.add(current.toString());
            }
        }
        return result;
    }

    private static int contentWidth(Font font, String title, List<String> lines) {
        int width = font.width(title);
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        return width;
    }
}
