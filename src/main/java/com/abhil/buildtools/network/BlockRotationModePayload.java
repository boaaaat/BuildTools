package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import com.abhil.buildtools.server.BuildToolsState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BlockRotationModePayload(int delta) implements CustomPacketPayload {
    public static final Type<BlockRotationModePayload> TYPE = new Type<>(BuildTools.id("block_rotation_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlockRotationModePayload> STREAM_CODEC = CustomPacketPayload.codec(
            BlockRotationModePayload::write,
            BlockRotationModePayload::read);

    private static BlockRotationModePayload read(RegistryFriendlyByteBuf buffer) {
        return new BlockRotationModePayload(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(delta);
    }

    public static void handle(BlockRotationModePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.containerMenu instanceof AdvancedBuildToolsModeMenu menu) {
            menu.adjustBlockRotationMode(player, payload.delta());
        } else {
            BuildToolsState.cycleBlockRotationMode(player, payload.delta());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
