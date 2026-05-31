package com.abhil.buildtools.client;

import com.abhil.buildtools.BuildToolsClient;
import com.abhil.buildtools.network.MaterialWeightPayload;
import com.abhil.buildtools.server.MaterialSelectionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MaterialSelectionScreen extends AbstractContainerScreen<MaterialSelectionMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 6;

    public MaterialSelectionScreen(MaterialSelectionMenu menu, Inventory playerInventory, Component title) {
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (BuildToolsClient.isOpenMaterialSelectionKey(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Slot slot = this.hoveredSlot;
        int slotId = slot == null ? -1 : this.menu.slots.indexOf(slot);
        if (slotId >= 0 && slotId < MaterialSelectionMenu.PAGE_SIZE && !slot.getItem().isEmpty()) {
            int step = scrollY >= 0.0D ? 1 : -1;
            PacketDistributor.sendToServer(new MaterialWeightPayload(slotId, step * (hasShiftDown() ? 10 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
