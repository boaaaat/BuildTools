package com.abhil.buildtools.server;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public record Blueprint(List<Entry> entries, List<CapturedEntity> entities) {
    public Blueprint(List<Entry> entries) {
        this(entries, List.of());
    }

    public record Entry(BlockPos offset, BlockState state, CompoundTag blockEntity) {
        public Entry(BlockPos offset, BlockState state) {
            this(offset, state, null);
        }

        public Entry {
            blockEntity = blockEntity == null ? null : blockEntity.copy();
        }
    }
}
