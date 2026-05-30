package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import com.abhil.buildtools.server.BuildToolsState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GradientDirectionPayload(int delta) implements CustomPacketPayload {
    public static final Type<GradientDirectionPayload> TYPE = new Type<>(BuildTools.id("gradient_direction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GradientDirectionPayload> STREAM_CODEC = CustomPacketPayload.codec(
            GradientDirectionPayload::write,
            GradientDirectionPayload::read);

    private static GradientDirectionPayload read(RegistryFriendlyByteBuf buffer) {
        return new GradientDirectionPayload(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(delta);
    }

    public static void handle(GradientDirectionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            if (player.containerMenu instanceof AdvancedBuildToolsModeMenu menu) {
                menu.adjustGradientDirection(player, payload.delta());
                return;
            }
            BuildToolsState.cycleGradientDirection(player, payload.delta());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
