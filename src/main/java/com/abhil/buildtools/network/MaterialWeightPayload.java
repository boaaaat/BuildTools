package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.MaterialSelectionMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MaterialWeightPayload(int slot, int delta) implements CustomPacketPayload {
    public static final Type<MaterialWeightPayload> TYPE = new Type<>(BuildTools.id("material_weight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialWeightPayload> STREAM_CODEC = CustomPacketPayload.codec(
            MaterialWeightPayload::write,
            MaterialWeightPayload::read);

    private static MaterialWeightPayload read(RegistryFriendlyByteBuf buffer) {
        return new MaterialWeightPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(slot);
        buffer.writeVarInt(delta);
    }

    public static void handle(MaterialWeightPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof MaterialSelectionMenu menu) {
            menu.adjustMaterialWeight(player, payload.slot(), payload.delta());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
