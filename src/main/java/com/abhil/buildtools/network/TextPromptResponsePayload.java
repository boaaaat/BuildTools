package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.BuildToolsState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TextPromptResponsePayload(String value, boolean cancelled) implements CustomPacketPayload {
    public static final Type<TextPromptResponsePayload> TYPE = new Type<>(BuildTools.id("text_prompt_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TextPromptResponsePayload> STREAM_CODEC = CustomPacketPayload.codec(
            TextPromptResponsePayload::write,
            TextPromptResponsePayload::read);

    private static TextPromptResponsePayload read(RegistryFriendlyByteBuf buffer) {
        return new TextPromptResponsePayload(buffer.readUtf(64), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(value == null ? "" : value, 64);
        buffer.writeBoolean(cancelled);
    }

    public static void handle(TextPromptResponsePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !BuildToolsState.hasPendingTextPrompt(player)) {
            return;
        }
        if (payload.cancelled()) {
            BuildToolsState.cancelPendingTextPrompt(player);
        } else {
            BuildToolsState.completePendingTextPrompt(player, payload.value());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
