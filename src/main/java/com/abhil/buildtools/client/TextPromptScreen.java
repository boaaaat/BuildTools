package com.abhil.buildtools.client;

import com.abhil.buildtools.network.TextPromptResponsePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class TextPromptScreen extends Screen {
    private final Component prompt;
    private final String initialValue;
    private final int maxLength;
    private EditBox input;
    private Button submitButton;
    private boolean resolved;

    public TextPromptScreen(Component title, Component prompt, String initialValue, int maxLength) {
        super(title);
        this.prompt = prompt;
        this.initialValue = initialValue == null ? "" : initialValue;
        this.maxLength = Math.max(1, maxLength);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int inputY = this.height / 2 - 10;
        this.input = new EditBox(this.font, centerX - 100, inputY, 200, 20, this.prompt);
        this.input.setMaxLength(this.maxLength);
        this.input.setValue(this.initialValue);
        this.input.setResponder(value -> updateSubmitButton());
        this.addRenderableWidget(this.input);

        this.submitButton = Button.builder(Component.translatable("buildtools.prompt.save"), button -> submit())
                .bounds(centerX - 100, inputY + 30, 96, 20)
                .build();
        this.addRenderableWidget(this.submitButton);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> cancel())
                .bounds(centerX + 4, inputY + 30, 96, 20)
                .build());
        this.setInitialFocus(this.input);
        this.input.setCursorPosition(this.input.getValue().length());
        this.input.setHighlightPos(0);
        updateSubmitButton();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int inputY = this.height / 2 - 10;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, inputY - 42, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(this.font, this.prompt, centerX, inputY - 24, 0xFFB8C4CE);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!this.resolved) {
            sendResponse("", true);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void submit() {
        if (this.input == null || this.input.getValue().strip().isEmpty()) {
            return;
        }
        sendResponse(this.input.getValue(), false);
        closeResolved();
    }

    private void cancel() {
        sendResponse("", true);
        closeResolved();
    }

    private void closeResolved() {
        this.resolved = true;
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private void sendResponse(String value, boolean cancelled) {
        if (!this.resolved) {
            PacketDistributor.sendToServer(new TextPromptResponsePayload(value, cancelled));
        }
    }

    private void updateSubmitButton() {
        if (this.submitButton != null) {
            this.submitButton.active = this.input != null && !this.input.getValue().strip().isEmpty();
        }
    }
}
