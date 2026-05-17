package com.abhil.buildtools.shape;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record Selection(UUID owner, ResourceKey<Level> dimension, BlockPos first, BlockPos second, SelectionShape shape) {
    public Optional<BlockPos> firstOptional() {
        return Optional.ofNullable(first);
    }

    public Optional<BlockPos> secondOptional() {
        return Optional.ofNullable(second);
    }

    public boolean isComplete() {
        return first != null && second != null;
    }

    public Selection withFirst(ResourceKey<Level> newDimension, BlockPos pos) {
        return new Selection(owner, newDimension, pos.immutable(), sameDimension(newDimension) ? second : null, shape);
    }

    public Selection withSecond(ResourceKey<Level> newDimension, BlockPos pos) {
        return new Selection(owner, newDimension, sameDimension(newDimension) ? first : null, pos.immutable(), shape);
    }

    public Selection withShape(SelectionShape newShape) {
        return new Selection(owner, dimension, first, second, newShape);
    }

    private boolean sameDimension(ResourceKey<Level> otherDimension) {
        return dimension == null || dimension.equals(otherDimension);
    }

    public static Selection empty(UUID owner) {
        return new Selection(owner, null, null, null, SelectionShape.CUBOID);
    }
}
