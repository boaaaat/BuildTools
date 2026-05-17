package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PaletteWeightPayload(int slot, int delta) implements CustomPacketPayload {
    public static final Type<PaletteWeightPayload> TYPE = new Type<>(BuildTools.id("palette_weight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PaletteWeightPayload> STREAM_CODEC = CustomPacketPayload.codec(
            PaletteWeightPayload::write,
            PaletteWeightPayload::read);

    private static PaletteWeightPayload read(RegistryFriendlyByteBuf buffer) {
        return new PaletteWeightPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(slot);
        buffer.writeVarInt(delta);
    }

    public static void handle(PaletteWeightPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AdvancedBuildToolsModeMenu menu) {
            menu.adjustPaletteWeight(player, payload.slot(), payload.delta());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
