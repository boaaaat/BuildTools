package com.abhil.buildtools.server;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public record BuildOperation(
        ServerPlayer player,
        ServerLevel level,
        ResourceKey<Level> dimension,
        List<BlockPos> positions,
        List<BlockState> targetStates,
        List<CompoundTag> targetBlockEntities,
        List<UndoSnapshot.Entry> undoEntries,
        List<ItemStack> refund,
        List<ItemStack> producedDrops,
        List<ItemStack> expectedDropBudget,
        List<CapturedEntity> removedEntities,
        List<CapturedEntity> addedEntities,
        boolean trackHistory) {
}
