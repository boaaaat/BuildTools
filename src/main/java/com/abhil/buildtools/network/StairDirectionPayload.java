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

public record StairDirectionPayload(int delta) implements CustomPacketPayload {
    public static final Type<StairDirectionPayload> TYPE = new Type<>(BuildTools.id("stair_direction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StairDirectionPayload> STREAM_CODEC = CustomPacketPayload.codec(
            StairDirectionPayload::write,
            StairDirectionPayload::read);

    private static StairDirectionPayload read(RegistryFriendlyByteBuf buffer) {
        return new StairDirectionPayload(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(delta);
    }

    public static void handle(StairDirectionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            if (player.containerMenu instanceof BuildToolsModeMenu menu) {
                menu.adjustStairDirection(player, payload.delta());
                return;
            }
            if (player.containerMenu instanceof AdvancedBuildToolsModeMenu menu) {
                menu.adjustStairDirection(player, payload.delta());
                return;
            }
            BuildToolsState.cycleStairDirection(player, payload.delta());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
