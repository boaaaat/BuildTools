package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.shape.BuildMode;
import com.abhil.buildtools.shape.Selection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
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
            BlockState target = BuildToolsState.primaryMaterial(player);
            int radius = BuildToolsState.brushRadius(player);
            int roughCount = Math.max(1, radius * radius * radius);
            return repeated(target, roughCount);
        }
        Selection selection = BuildToolsState.selection(player);
        if (selection.dimension() == null || !selection.dimension().equals(player.level().dimension())) {
            return List.of();
        }
        List<net.minecraft.core.BlockPos> generated = BuildToolsState.generatedSelection(player);
        List<PaletteEntry> materials = BuildToolsState.materialSelections(player);
        if (materials.isEmpty()) {
            return List.of();
        }
        boolean multiMaterial = held.is(ModItems.ADVANCED_BUILDER_WAND.get())
                && BuildToolsState.paletteMode(player) != PaletteMode.SINGLE
                && materials.size() > 1;
        BuildMode mode = BuildToolsState.mode(player);
        BlockState replaceMatch = BuildToolsState.replaceTarget(player);
        List<net.minecraft.core.BlockPos> placementCandidates = mode == BuildMode.SURFACE ? SurfacePlacementSupport.candidates(player.level(), generated) : generated;
        List<BlockState> targets = new ArrayList<>();
        int index = 0;
        for (net.minecraft.core.BlockPos pos : placementCandidates) {
            BlockState previous = player.level().getBlockState(pos);
            if (!canPlaceForMode(player, pos, previous, mode, replaceMatch)) {
                continue;
            }
            targets.add(multiMaterial ? materials.get(index++ % materials.size()).state() : materials.getFirst().state());
        }
        return targets;
    }

    private static boolean canPlaceForMode(ServerPlayer player, net.minecraft.core.BlockPos pos, BlockState previous, BuildMode mode, BlockState replaceMatch) {
        if (!previous.canBeReplaced()) {
            return false;
        }
        return switch (mode) {
            case FILL -> true;
            case REPLACE -> touchesMatchingBlock(player, pos, replaceMatch);
            case SURFACE -> SurfacePlacementSupport.touchesSolidBlock(player.level(), pos);
        };
    }

    private static boolean touchesMatchingBlock(ServerPlayer player, net.minecraft.core.BlockPos pos, BlockState match) {
        if (match == null || match.isAir()) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (player.level().getBlockState(pos.relative(direction)).is(match.getBlock())) {
                return true;
            }
        }
        return false;
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

}
