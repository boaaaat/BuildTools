package com.abhil.buildtools.client;

import java.util.ArrayList;
import java.util.List;
import com.abhil.buildtools.BuildToolsClient;
import com.abhil.buildtools.config.BuildToolsClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
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
        List<Component> statusLines = filteredLines(ClientToolStatusData.lines());
        List<FormattedCharSequence> lines = wrappedLines(font, statusLines, MAX_WIDTH - 20);
        List<FormattedCharSequence> shortcutLines = wrappedLines(font, shortcutLines(minecraft).stream()
                .<Component>map(Component::literal)
                .toList(), MAX_WIDTH - 20);
        int width = Math.max(110, Math.min(MAX_WIDTH, contentWidth(font, ClientToolStatusData.title(), lines, shortcutLines) + 20));
        int height = 22 + lines.size() * 10 + (shortcutLines.isEmpty() ? 8 : 20 + shortcutLines.size() * 10);
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
        for (FormattedCharSequence line : lines) {
            guiGraphics.drawString(font, line, x + 9, lineY, MUTED_TEXT, false);
            lineY += 10;
        }
        if (!shortcutLines.isEmpty()) {
            lineY += 4;
            guiGraphics.hLine(x + 8, x + width - 9, lineY, PANEL_BORDER);
            lineY += 6;
            guiGraphics.drawString(font, net.minecraft.network.chat.Component.translatable("buildtools.overlay.shortcuts"), x + 9, lineY, TEXT, false);
            lineY += 10;
            for (FormattedCharSequence line : shortcutLines) {
                guiGraphics.drawString(font, line, x + 9, lineY, MUTED_TEXT, false);
                lineY += 10;
            }
        }
        guiGraphics.pose().popPose();
    }

    private static List<String> shortcutLines(Minecraft minecraft) {
        if (minecraft.player == null) {
            return List.of();
        }
        ItemStack mainHand = minecraft.player.getMainHandItem();
        List<String> lines = BuildToolsClient.shortcutHintLines(mainHand);
        if (!lines.isEmpty()) {
            return lines;
        }
        return BuildToolsClient.shortcutHintLines(minecraft.player.getOffhandItem());
    }

    private static List<Component> filteredLines(List<Component> lines) {
        boolean showMaterials = BuildToolsClientConfig.SHOW_OVERLAY_MATERIALS.get();
        boolean showLimits = BuildToolsClientConfig.SHOW_OVERLAY_LIMITS.get();
        List<Component> filtered = new ArrayList<>();
        for (Component line : lines) {
            String key = line.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
            if (!showMaterials && isMaterialStatusKey(key)) {
                continue;
            }
            if (!showLimits && isLimitStatusKey(key)) {
                continue;
            }
            filtered.add(line);
        }
        return filtered;
    }

    private static boolean isMaterialStatusKey(String key) {
        return key.contains(".material")
                || key.contains(".palette")
                || key.endsWith(".select_material")
                || key.endsWith(".mode_material")
                || key.endsWith(".block_target")
                || key.endsWith(".replace_target");
    }

    private static boolean isLimitStatusKey(String key) {
        return key.contains(".warning.")
                || key.endsWith(".over_limit")
                || key.endsWith(".selection_other_dimension");
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

    private static List<FormattedCharSequence> wrappedLines(Font font, List<Component> source, int maxWidth) {
        List<FormattedCharSequence> result = new ArrayList<>();
        for (Component line : source) {
            result.addAll(font.split(line, maxWidth));
        }
        return result;
    }

    private static int contentWidth(Font font, Component title, List<FormattedCharSequence> lines, List<FormattedCharSequence> shortcutLines) {
        int width = font.width(title);
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, font.width(line));
        }
        if (!shortcutLines.isEmpty()) {
            width = Math.max(width, font.width(net.minecraft.network.chat.Component.translatable("buildtools.overlay.shortcuts")));
            for (FormattedCharSequence line : shortcutLines) {
                width = Math.max(width, font.width(line));
            }
        }
        return width;
    }
}
