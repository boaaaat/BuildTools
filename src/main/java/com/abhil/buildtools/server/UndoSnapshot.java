package com.abhil.buildtools.server;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public record UndoSnapshot(ResourceKey<Level> dimension, List<Entry> entries, Map<ItemStackKey, Integer> refund) {
    public record Entry(
            BlockPos pos,
            BlockState previousState,
            CompoundTag previousBlockEntity,
            BlockState redoneState,
            CompoundTag redoneBlockEntity,
            boolean mayRestorePrevious) {
        public Entry(BlockPos pos, BlockState previousState, BlockState redoneState, boolean mayRestorePrevious) {
            this(pos, previousState, null, redoneState, null, mayRestorePrevious);
        }
    }
}
