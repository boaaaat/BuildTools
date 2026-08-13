package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.PresetLibraryMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PresetLibraryQueryPayload(String query) implements CustomPacketPayload {
    public static final Type<PresetLibraryQueryPayload> TYPE = new Type<>(BuildTools.id("preset_library_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PresetLibraryQueryPayload> STREAM_CODEC = CustomPacketPayload.codec(
            PresetLibraryQueryPayload::write,
            PresetLibraryQueryPayload::read);

    private static PresetLibraryQueryPayload read(RegistryFriendlyByteBuf buffer) {
        return new PresetLibraryQueryPayload(buffer.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(query == null ? "" : query, 64);
    }

    public static void handle(PresetLibraryQueryPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof PresetLibraryMenu menu) {
            menu.setSearchQuery(payload.query());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
