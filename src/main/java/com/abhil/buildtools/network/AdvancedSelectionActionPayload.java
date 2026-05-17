package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.server.BuildToolsState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AdvancedSelectionActionPayload() implements CustomPacketPayload {
    public static final Type<AdvancedSelectionActionPayload> TYPE = new Type<>(BuildTools.id("advanced_selection_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedSelectionActionPayload> STREAM_CODEC = CustomPacketPayload.codec(
            AdvancedSelectionActionPayload::write,
            AdvancedSelectionActionPayload::read);

    private static AdvancedSelectionActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new AdvancedSelectionActionPayload();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    public static void handle(AdvancedSelectionActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.getMainHandItem().is(ModItems.ADVANCED_SELECTION_STAFF.get())) {
            return;
        }
        if (player.isShiftKeyDown()) {
            BuildToolsState.removeAdvancedPointAtLook(player);
        } else {
            BuildToolsState.addAdvancedPointAtLook(player);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
