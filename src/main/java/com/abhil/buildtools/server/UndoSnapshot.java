package com.abhil.buildtools.server;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public record UndoSnapshot(
        ResourceKey<Level> dimension,
        List<Entry> entries,
        List<ItemStack> refund,
        List<ItemStack> producedDrops,
        List<CapturedEntity> removedEntities,
        List<CapturedEntity> addedEntities) {
    public UndoSnapshot(ResourceKey<Level> dimension, List<Entry> entries, List<ItemStack> refund, List<ItemStack> producedDrops) {
        this(dimension, entries, refund, producedDrops, List.of(), List.of());
    }

    public UndoSnapshot {
        refund = copyStacks(refund);
        producedDrops = copyStacks(producedDrops);
        removedEntities = List.copyOf(removedEntities);
        addedEntities = List.copyOf(addedEntities);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    public record Entry(
            BlockPos pos,
            BlockState previousState,
            CompoundTag previousBlockEntity,
            BlockState redoneState,
            CompoundTag redoneBlockEntity,
            boolean mayRestorePrevious,
            UUID previousOwner) {
        public Entry(BlockPos pos, BlockState previousState, BlockState redoneState, boolean mayRestorePrevious) {
            this(pos, previousState, null, redoneState, null, mayRestorePrevious, null);
        }

        public Entry(BlockPos pos, BlockState previousState, CompoundTag previousBlockEntity, BlockState redoneState, CompoundTag redoneBlockEntity, boolean mayRestorePrevious) {
            this(pos, previousState, previousBlockEntity, redoneState, redoneBlockEntity, mayRestorePrevious, null);
        }
    }
}
