package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.BlueprintLibraryMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BlueprintLibraryQueryPayload(String query) implements CustomPacketPayload {
    public static final Type<BlueprintLibraryQueryPayload> TYPE = new Type<>(BuildTools.id("blueprint_library_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintLibraryQueryPayload> STREAM_CODEC = CustomPacketPayload.codec(
            BlueprintLibraryQueryPayload::write,
            BlueprintLibraryQueryPayload::read);

    private static BlueprintLibraryQueryPayload read(RegistryFriendlyByteBuf buffer) {
        return new BlueprintLibraryQueryPayload(buffer.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(query == null ? "" : query, 64);
    }

    public static void handle(BlueprintLibraryQueryPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof BlueprintLibraryMenu menu) {
            menu.setSearchQuery(payload.query());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
