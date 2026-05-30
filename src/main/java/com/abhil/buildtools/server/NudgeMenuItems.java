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
        return item(player, direction, "nudge", descriptionKey);
    }

    static ItemStack expandItem(ServerPlayer player, Direction direction) {
        return item(player, direction, "expand", "buildtools.menu.expand.description");
    }

    private static ItemStack item(ServerPlayer player, Direction direction, String action, String descriptionKey) {
        ItemStack stack = new ItemStack(icon(player, direction));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(DirectionDisplay.arrow(player, direction) + " ")
                .append(Component.translatable(nameKey(action, direction))));
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
        return switch (DirectionDisplay.arrow(player, direction)) {
            case DirectionDisplay.FORWARD -> Items.SPECTRAL_ARROW;
            case DirectionDisplay.BACK -> Items.ARROW;
            case DirectionDisplay.LEFT -> Items.FEATHER;
            case DirectionDisplay.RIGHT -> Items.PRISMARINE_SHARD;
            default -> Items.COMPASS;
        };
    }

    private static String nameKey(String action, Direction direction) {
        return switch (direction) {
            case WEST -> "buildtools.menu." + action + "_west";
            case EAST -> "buildtools.menu." + action + "_east";
            case DOWN -> "buildtools.menu." + action + "_down";
            case UP -> "buildtools.menu." + action + "_up";
            case NORTH -> "buildtools.menu." + action + "_north";
            case SOUTH -> "buildtools.menu." + action + "_south";
        };
    }
}
