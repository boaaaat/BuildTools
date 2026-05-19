package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.shape.Selection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialChecklist {
    private MaterialChecklist() {
    }

    public static List<BlockState> targetsFor(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.is(ModItems.BLUEPRINT_TROWEL.get())) {
            return BuildToolsState.blueprint(player)
                    .map(blueprint -> blueprint.entries().stream().map(Blueprint.Entry::state).toList())
                    .orElse(List.of());
        }
        if (held.is(ModItems.BUILDER_BRUSH.get())) {
            BlockState target = materialState(player.getOffhandItem());
            int radius = BuildToolsState.brushRadius(player);
            int roughCount = Math.max(1, radius * radius * radius);
            return repeated(target, roughCount);
        }
        Selection selection = BuildToolsState.selection(player);
        if (selection.dimension() == null || !selection.dimension().equals(player.level().dimension())) {
            return List.of();
        }
        List<net.minecraft.core.BlockPos> generated = BuildToolsState.generatedSelection(player);
        List<PaletteEntry> palette = held.is(ModItems.ADVANCED_BUILDER_WAND.get()) ? BuildToolsState.paletteEntries(player) : List.of();
        BlockState fallback = materialState(player.getOffhandItem());
        if (fallback == null && !palette.isEmpty()) {
            fallback = palette.getFirst().state();
        }
        if (fallback == null) {
            return List.of();
        }
        List<BlockState> targets = new ArrayList<>();
        for (net.minecraft.core.BlockPos pos : generated) {
            BlockState previous = player.level().getBlockState(pos);
            if (!previous.canBeReplaced()) {
                continue;
            }
            targets.add(fallback);
        }
        return targets;
    }

    private static List<BlockState> repeated(BlockState target, int count) {
        if (target == null) {
            return List.of();
        }
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            states.add(target);
        }
        return states;
    }

    private static BlockState materialState(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState();
        }
        if (stack.is(Items.WATER_BUCKET)) {
            return Blocks.WATER.defaultBlockState();
        }
        if (stack.is(Items.LAVA_BUCKET)) {
            return Blocks.LAVA.defaultBlockState();
        }
        return null;
    }
}
