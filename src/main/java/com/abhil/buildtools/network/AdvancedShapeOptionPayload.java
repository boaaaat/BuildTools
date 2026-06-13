package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import com.abhil.buildtools.server.AdvancedShapeOption;
import com.abhil.buildtools.server.BuildToolsModeMenu;
import com.abhil.buildtools.server.BuildToolsState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AdvancedShapeOptionPayload(AdvancedShapeOption option, int delta) implements CustomPacketPayload {
    public static final Type<AdvancedShapeOptionPayload> TYPE = new Type<>(BuildTools.id("advanced_shape_option"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedShapeOptionPayload> STREAM_CODEC = CustomPacketPayload.codec(
            AdvancedShapeOptionPayload::write,
            AdvancedShapeOptionPayload::read);

    private static AdvancedShapeOptionPayload read(RegistryFriendlyByteBuf buffer) {
        return new AdvancedShapeOptionPayload(buffer.readEnum(AdvancedShapeOption.class), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(option);
        buffer.writeVarInt(delta);
    }

    public static void handle(AdvancedShapeOptionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            if (player.containerMenu instanceof BuildToolsModeMenu menu) {
                menu.adjustAdvancedShapeOption(player, payload.option(), payload.delta());
                return;
            }
            if (player.containerMenu instanceof AdvancedBuildToolsModeMenu menu) {
                menu.adjustAdvancedShapeOption(player, payload.option(), payload.delta());
                return;
            }
            BuildToolsState.adjustAdvancedShapeOption(player, payload.option(), payload.delta());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
