package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import com.abhil.buildtools.server.BuildToolsModeMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenToolMenuPayload() implements CustomPacketPayload {
    public static final Type<OpenToolMenuPayload> TYPE = new Type<>(BuildTools.id("open_tool_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenToolMenuPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OpenToolMenuPayload::write,
            OpenToolMenuPayload::read);

    private static OpenToolMenuPayload read(RegistryFriendlyByteBuf buffer) {
        return new OpenToolMenuPayload();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    public static void handle(OpenToolMenuPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.is(ModItems.ADVANCED_BUILDER_WAND.get())) {
            AdvancedBuildToolsModeMenu.open(player);
        } else if (isMenuTool(held)) {
            BuildToolsModeMenu.open(player);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static boolean isMenuTool(ItemStack stack) {
        return stack.is(ModItems.BUILDER_WAND.get())
                || stack.is(ModItems.BUILDER_BRUSH.get())
                || stack.is(ModItems.AREA_BREAKER.get())
                || stack.is(ModItems.BLUEPRINT_TROWEL.get())
                || stack.is(ModItems.UNDO_TOKEN.get())
                || stack.is(ModItems.REDO_TOKEN.get());
    }
}
