package com.abhil.buildtools.server;

import com.abhil.buildtools.shape.StairDirectionOverride;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class DirectionDisplay {
    static final String FORWARD = "\u2191";
    static final String BACK = "\u2193";
    static final String LEFT = "\u2190";
    static final String RIGHT = "\u2192";
    static final String UP = "\u21E7";
    static final String DOWN = "\u21E9";
    static final String NONE = "\u2022";

    private DirectionDisplay() {
    }

    static String arrow(ServerPlayer player, Direction direction) {
        if (direction == Direction.UP) {
            return UP;
        }
        if (direction == Direction.DOWN) {
            return DOWN;
        }
        Direction facing = player == null ? Direction.NORTH : player.getDirection();
        if (direction == facing) {
            return FORWARD;
        }
        if (direction == facing.getOpposite()) {
            return BACK;
        }
        if (direction == facing.getClockWise()) {
            return RIGHT;
        }
        if (direction == facing.getCounterClockWise()) {
            return LEFT;
        }
        return NONE;
    }

    static Component direction(ServerPlayer player, Direction direction) {
        return Component.literal(arrow(player, direction) + " " + title(direction.getName()));
    }

    static Component direction(ServerPlayer player, Direction direction, Component name) {
        return Component.literal(arrow(player, direction) + " ").append(name);
    }

    static Component stairDirection(ServerPlayer player, StairDirectionOverride direction) {
        return direction.direction() == null
                ? direction.displayName()
                : direction(player, direction.direction(), direction.displayName());
    }

    static Component gradientDirection(ServerPlayer player, GradientDirection direction) {
        return switch (direction) {
            case X -> Component.literal(axis(player, Direction.WEST, Direction.EAST) + " ").append(direction.displayName());
            case Y -> Component.literal(DOWN + UP + " ").append(direction.displayName());
            case Z -> Component.literal(axis(player, Direction.NORTH, Direction.SOUTH) + " ").append(direction.displayName());
            case POINT_ORDER -> direction.displayName();
        };
    }

    private static String axis(ServerPlayer player, Direction first, Direction second) {
        return arrow(player, first) + arrow(player, second);
    }

    private static String title(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
