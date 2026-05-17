package com.abhil.buildtools.server;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public record BuildOperation(
        ServerPlayer player,
        ServerLevel level,
        ResourceKey<Level> dimension,
        List<BlockPos> positions,
        List<BlockState> targetStates,
        List<UndoSnapshot.Entry> undoEntries,
        Map<ItemStackKey, Integer> refund) {
}
