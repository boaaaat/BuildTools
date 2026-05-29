package com.abhil.buildtools.client;

import com.abhil.buildtools.network.ArchPeakPayload;
import com.abhil.buildtools.network.PaletteWeightPayload;
import com.abhil.buildtools.network.RoadWidthPayload;
import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AdvancedBuildToolsModeScreen extends AbstractContainerScreen<AdvancedBuildToolsModeMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 6;

    public AdvancedBuildToolsModeScreen(AdvancedBuildToolsModeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 114 + ROWS * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BACKGROUND, x, y, 0, 0, this.imageWidth, ROWS * 18 + 17);
        guiGraphics.blit(BACKGROUND, x, y + ROWS * 18 + 17, 0, 126, this.imageWidth, 96);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Slot slot = this.hoveredSlot;
        if (slot != null && this.menu.isRoadShapeSlot(slot) && slot.getItem().is(Items.RAIL)) {
            int step = scrollY >= 0.0D ? 1 : -1;
            PacketDistributor.sendToServer(new RoadWidthPayload(step));
            return true;
        }
        if (slot != null && this.menu.isArchShapeSlot(slot) && slot.getItem().is(Items.STONE_BRICK_STAIRS)) {
            int step = scrollY >= 0.0D ? 1 : -1;
            PacketDistributor.sendToServer(new ArchPeakPayload(step));
            return true;
        }
        if (slot != null && AdvancedBuildToolsModeMenu.isPaletteSlot(slot.index) && !slot.getItem().isEmpty()) {
            int step = scrollY >= 0.0D ? 1 : -1;
            PacketDistributor.sendToServer(new PaletteWeightPayload(slot.index, step * (hasShiftDown() ? 10 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
