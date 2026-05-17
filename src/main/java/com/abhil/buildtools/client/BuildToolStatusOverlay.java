package com.abhil.buildtools.client;

import java.util.ArrayList;
import java.util.List;
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
        if (minecraft.options.hideGui || minecraft.screen != null || !ClientToolStatusData.visible()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = minecraft.font;
        List<String> lines = wrappedLines(font, ClientToolStatusData.lines(), MAX_WIDTH - 20);
        int width = Math.max(110, Math.min(MAX_WIDTH, contentWidth(font, ClientToolStatusData.title(), lines) + 20));
        int height = 22 + lines.size() * 10 + 8;
        int x = 8;
        int y = Math.max(8, guiGraphics.guiHeight() - height - 72);
        int accent = 0xFF000000 | ClientToolStatusData.accentColor();

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
