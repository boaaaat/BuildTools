package com.abhil.buildtools.server;

import net.minecraft.world.level.block.state.BlockState;

public record PaletteEntry(BlockState state, int weight) {
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 100;
    public static final int DEFAULT_WEIGHT = 10;

    public PaletteEntry {
        weight = Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
    }
}
