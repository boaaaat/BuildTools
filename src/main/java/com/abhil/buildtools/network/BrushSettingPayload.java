package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.BuildToolsModeMenu;
import com.abhil.buildtools.server.BuildToolsState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BrushSettingPayload(int setting, int delta) implements CustomPacketPayload {
    public static final int RADIUS = 0;
    public static final int DEPTH = 1;
    public static final int DENSITY = 2;
    public static final Type<BrushSettingPayload> TYPE = new Type<>(BuildTools.id("brush_setting"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BrushSettingPayload> STREAM_CODEC = CustomPacketPayload.codec(
            BrushSettingPayload::write,
            BrushSettingPayload::read);

    private static BrushSettingPayload read(RegistryFriendlyByteBuf buffer) {
        return new BrushSettingPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(setting);
        buffer.writeVarInt(delta);
    }

    public static void handle(BrushSettingPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            if (player.containerMenu instanceof BuildToolsModeMenu menu) {
                menu.adjustBrushSetting(player, payload.setting(), payload.delta());
                return;
            }
            switch (payload.setting()) {
                case RADIUS -> BuildToolsState.changeBrushRadius(player, payload.delta());
                case DEPTH -> BuildToolsState.changeBrushDepth(player, payload.delta());
                case DENSITY -> BuildToolsState.changeBrushDensity(player, payload.delta());
                default -> {
                }
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
