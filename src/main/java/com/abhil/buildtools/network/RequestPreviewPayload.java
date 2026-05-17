package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.BuildToolsState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestPreviewPayload() implements CustomPacketPayload {
    public static final Type<RequestPreviewPayload> TYPE = new Type<>(BuildTools.id("request_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPreviewPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestPreviewPayload());

    public static void handle(RequestPreviewPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BuildToolsState.sendPreview(player);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
