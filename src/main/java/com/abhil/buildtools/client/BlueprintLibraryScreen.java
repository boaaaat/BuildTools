package com.abhil.buildtools.client;

import com.abhil.buildtools.server.BlueprintLibraryMenu;
import com.abhil.buildtools.network.BlueprintLibraryQueryPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BlueprintLibraryScreen extends AbstractContainerScreen<BlueprintLibraryMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 6;
    private EditBox searchBox;

    public BlueprintLibraryScreen(BlueprintLibraryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 114 + ROWS * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(this.font, this.leftPos + 8, this.topPos + 5, 88, 10, Component.translatable("buildtools.menu.blueprint_search"));
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setResponder(query -> PacketDistributor.sendToServer(new BlueprintLibraryQueryPayload(query)));
        addRenderableWidget(searchBox);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (searchBox != null && searchBox.getValue().isBlank() && !searchBox.isFocused()) {
            guiGraphics.drawString(this.font, Component.translatable("buildtools.menu.blueprint_search"), this.leftPos + 8, this.topPos + 5, 0xFF777777, false);
        }
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BACKGROUND, x, y, 0, 0, this.imageWidth, ROWS * 18 + 17);
        guiGraphics.blit(BACKGROUND, x, y + ROWS * 18 + 17, 0, 126, this.imageWidth, 96);
    }
}
