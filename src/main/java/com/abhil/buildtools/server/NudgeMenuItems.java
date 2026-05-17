package com.abhil.buildtools.server;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

final class NudgeMenuItems {
    private NudgeMenuItems() {
    }

    static ItemStack item(ServerPlayer player, Direction direction, String descriptionKey) {
        ItemStack stack = new ItemStack(icon(player, direction));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(arrow(player, direction) + " ")
                .append(Component.translatable(nameKey(direction))));
        Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        return stack;
    }

    private static Item icon(ServerPlayer player, Direction direction) {
        if (direction == Direction.UP) {
            return Items.FIREWORK_ROCKET;
        }
        if (direction == Direction.DOWN) {
            return Items.ANVIL;
        }
        return switch (arrow(player, direction)) {
            case "↑" -> Items.SPECTRAL_ARROW;
            case "↓" -> Items.ARROW;
            case "←" -> Items.FEATHER;
            case "→" -> Items.PRISMARINE_SHARD;
            default -> Items.COMPASS;
        };
    }

    private static String arrow(ServerPlayer player, Direction direction) {
        if (direction == Direction.UP) {
            return "⇧";
        }
        if (direction == Direction.DOWN) {
            return "⇩";
        }
        Direction facing = player == null ? Direction.NORTH : player.getDirection();
        if (direction == facing) {
            return "↑";
        }
        if (direction == facing.getOpposite()) {
            return "↓";
        }
        if (direction == facing.getClockWise()) {
            return "→";
        }
        if (direction == facing.getCounterClockWise()) {
            return "←";
        }
        return "•";
    }

    private static String nameKey(Direction direction) {
        return switch (direction) {
            case WEST -> "buildtools.menu.nudge_west";
            case EAST -> "buildtools.menu.nudge_east";
            case DOWN -> "buildtools.menu.nudge_down";
            case UP -> "buildtools.menu.nudge_up";
            case NORTH -> "buildtools.menu.nudge_north";
            case SOUTH -> "buildtools.menu.nudge_south";
        };
    }
}
