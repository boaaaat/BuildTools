package com.abhil.buildtools.server;

import com.abhil.buildtools.shape.SelectionShape;
import net.minecraft.core.BlockPos;

public record SelectionPreset(BlockPos offset, SelectionShape shape) {
}
