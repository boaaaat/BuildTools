package com.abhil.buildtools.shape;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public enum StairDirectionOverride {
    POINT_ORDER("point_order", null),
    NORTH("north", Direction.NORTH),
    SOUTH("south", Direction.SOUTH),
    EAST("east", Direction.EAST),
    WEST("west", Direction.WEST);

    private final String key;
    private final Direction direction;

    StairDirectionOverride(String key, Direction direction) {
        this.key = key;
        this.direction = direction;
    }

    public Component displayName() {
        return Component.translatable("buildtools.stair_direction." + key);
    }

    public Direction direction() {
        return direction;
    }

    public StairDirectionOverride next(int step) {
        StairDirectionOverride[] values = values();
        int index = Math.floorMod(ordinal() + (step >= 0 ? 1 : -1), values.length);
        return values[index];
    }
}
