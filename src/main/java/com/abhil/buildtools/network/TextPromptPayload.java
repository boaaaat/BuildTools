package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.client.ClientPayloadHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TextPromptPayload(String titleKey, String promptKey, String initialValue, int maxLength) implements CustomPacketPayload {
    public static final Type<TextPromptPayload> TYPE = new Type<>(BuildTools.id("text_prompt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TextPromptPayload> STREAM_CODEC = CustomPacketPayload.codec(
            TextPromptPayload::write,
            TextPromptPayload::read);

    private static TextPromptPayload read(RegistryFriendlyByteBuf buffer) {
        return new TextPromptPayload(buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(64), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(titleKey, 128);
        buffer.writeUtf(promptKey, 128);
        buffer.writeUtf(initialValue == null ? "" : initialValue, 64);
        buffer.writeVarInt(maxLength);
    }

    public static void handle(TextPromptPayload payload, IPayloadContext context) {
        ClientPayloadHandlers.handleTextPrompt(payload);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
