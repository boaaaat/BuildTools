package com.abhil.buildtools.server;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BuildMaterialSource {
    private BuildMaterialSource() {
    }

    public static BlockState stateFromStack(ItemStack stack) {
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

    public static ItemStack stackForState(BlockState state) {
        if (state.is(Blocks.WATER)) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        if (state.is(Blocks.LAVA)) {
            return new ItemStack(Items.LAVA_BUCKET);
        }
        return new ItemStack(state.getBlock().asItem());
    }
}
