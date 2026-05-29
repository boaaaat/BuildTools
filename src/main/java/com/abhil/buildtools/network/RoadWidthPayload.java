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

public record RoadWidthPayload(int delta) implements CustomPacketPayload {
    public static final Type<RoadWidthPayload> TYPE = new Type<>(BuildTools.id("road_width"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoadWidthPayload> STREAM_CODEC = CustomPacketPayload.codec(
            RoadWidthPayload::write,
            RoadWidthPayload::read);

    private static RoadWidthPayload read(RegistryFriendlyByteBuf buffer) {
        return new RoadWidthPayload(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(delta);
    }

    public static void handle(RoadWidthPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            if (player.containerMenu instanceof BuildToolsModeMenu menu) {
                menu.adjustRoadWidth(player, payload.delta());
                return;
            }
            if (player.containerMenu instanceof AdvancedBuildToolsModeMenu menu) {
                menu.adjustRoadWidth(player, payload.delta());
                return;
            }
            BuildToolsState.changeRoadWidth(player, payload.delta());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
