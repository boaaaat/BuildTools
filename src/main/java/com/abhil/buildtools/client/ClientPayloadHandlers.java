package com.abhil.buildtools.client;

import com.abhil.buildtools.network.TextPromptPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleTextPrompt(TextPromptPayload payload) {
        Minecraft.getInstance().setScreen(new TextPromptScreen(
                Component.translatable(payload.titleKey()),
                Component.translatable(payload.promptKey()),
                payload.initialValue(),
                payload.maxLength()));
    }
}
