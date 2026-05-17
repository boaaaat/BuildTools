package com.abhil.buildtools.server;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record Blueprint(List<Entry> entries) {
    public record Entry(BlockPos offset, BlockState state) {
    }
}
