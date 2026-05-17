package com.abhil.buildtools.server;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public record BuildPlan(ResourceKey<Level> dimension, List<Entry> entries) {
    public record Entry(BlockPos pos, BlockState state, CompoundTag blockEntity) {
        public Entry(BlockPos pos, BlockState state) {
            this(pos, state, null);
        }

        public Entry {
            blockEntity = blockEntity == null ? null : blockEntity.copy();
        }
    }
}
