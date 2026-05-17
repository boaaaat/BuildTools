package com.abhil.buildtools.shape;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record Selection(UUID owner, ResourceKey<Level> dimension, BlockPos first, BlockPos second, SelectionShape shape, List<BlockPos> points) {
    public Selection {
        points = points == null ? List.of() : points.stream().map(BlockPos::immutable).toList();
    }

    public Selection(UUID owner, ResourceKey<Level> dimension, BlockPos first, BlockPos second, SelectionShape shape) {
        this(owner, dimension, first, second, shape, List.of());
    }

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
        return new Selection(owner, dimension, first, second, newShape, points);
    }

    private boolean sameDimension(ResourceKey<Level> otherDimension) {
        return dimension == null || dimension.equals(otherDimension);
    }

    public static Selection empty(UUID owner) {
        return new Selection(owner, null, null, null, SelectionShape.CUBOID);
    }
}
