package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.server.BuildMaterialSource;
import com.abhil.buildtools.server.BuildToolsState;
import com.abhil.buildtools.server.BuildingStorageManager;
import com.abhil.buildtools.server.ItemStackKey;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PickMaterialPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<PickMaterialPayload> TYPE = new Type<>(BuildTools.id("pick_material"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PickMaterialPayload> STREAM_CODEC = CustomPacketPayload.codec(
            PickMaterialPayload::write,
            PickMaterialPayload::read);

    private static PickMaterialPayload read(RegistryFriendlyByteBuf buffer) {
        return new PickMaterialPayload(buffer.readBlockPos());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    public static void handle(PickMaterialPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !isMaterialTool(player.getMainHandItem())) {
            return;
        }
        if (!player.level().hasChunkAt(payload.pos())) {
            return;
        }
        BlockState state = player.level().getBlockState(payload.pos());
        ItemStack material = BuildMaterialSource.stackForState(state);
        if (material.isEmpty() || material.is(Items.AIR)) {
            player.displayClientMessage(Component.translatable("buildtools.error.unsupported_block"), false);
            return;
        }
        if (!player.gameMode.isCreative()
                && !BuildingStorageManager.accessibleMaterialCounts(player).containsKey(new ItemStackKey(material.getItem()))) {
            player.displayClientMessage(Component.translatable("buildtools.error.material_unavailable"), false);
            return;
        }
        BuildToolsState.setPrimaryMaterial(player, state);
        player.displayClientMessage(Component.translatable(
                "buildtools.message.material_selected",
                state.getBlock().getName().copy().withStyle(ChatFormatting.GREEN)), true);
    }

    private static boolean isMaterialTool(ItemStack stack) {
        return stack.is(ModItems.BUILDER_WAND.get())
                || stack.is(ModItems.ADVANCED_BUILDER_WAND.get())
                || stack.is(ModItems.BUILDER_BRUSH.get());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
