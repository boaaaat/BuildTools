package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.MaterialSelectionMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MaterialSelectionQueryPayload(String query) implements CustomPacketPayload {
    public static final Type<MaterialSelectionQueryPayload> TYPE = new Type<>(BuildTools.id("material_selection_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialSelectionQueryPayload> STREAM_CODEC = CustomPacketPayload.codec(
            MaterialSelectionQueryPayload::write,
            MaterialSelectionQueryPayload::read);

    private static MaterialSelectionQueryPayload read(RegistryFriendlyByteBuf buffer) {
        return new MaterialSelectionQueryPayload(buffer.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(query == null ? "" : query, 64);
    }

    public static void handle(MaterialSelectionQueryPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof MaterialSelectionMenu menu) {
            menu.setSearchQuery(payload.query());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
