package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import com.abhil.buildtools.server.BuildToolsModeMenu;
import com.abhil.buildtools.server.BuildToolsState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BridgeWidthPayload(int delta) implements CustomPacketPayload {
    public static final Type<BridgeWidthPayload> TYPE = new Type<>(BuildTools.id("bridge_width"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeWidthPayload> STREAM_CODEC = CustomPacketPayload.codec(
            BridgeWidthPayload::write,
            BridgeWidthPayload::read);

    private static BridgeWidthPayload read(RegistryFriendlyByteBuf buffer) {
        return new BridgeWidthPayload(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(delta);
    }

    public static void handle(BridgeWidthPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            if (player.containerMenu instanceof BuildToolsModeMenu menu) {
                menu.adjustBridgeWidth(player, payload.delta());
                return;
            }
            if (player.containerMenu instanceof AdvancedBuildToolsModeMenu menu) {
                menu.adjustBridgeWidth(player, payload.delta());
                return;
            }
            BuildToolsState.changeBridgeWidth(player, payload.delta());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
