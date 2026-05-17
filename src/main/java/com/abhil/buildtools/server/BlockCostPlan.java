package com.abhil.buildtools.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockCostPlan {
    private final Map<ItemStackKey, Integer> required;
    private final Map<ItemStackKey, Integer> missing;

    private BlockCostPlan(Map<ItemStackKey, Integer> required, Map<ItemStackKey, Integer> missing) {
        this.required = required;
        this.missing = missing;
    }

    public static BlockCostPlan create(ServerPlayer player, List<BlockState> states) {
        Map<ItemStackKey, Integer> required = new LinkedHashMap<>();
        for (BlockState state : states) {
            if (state.isAir()) {
                continue;
            }
            ItemStack item = materialItem(state);
            if (item.isEmpty() || item.is(Blocks.AIR.asItem())) {
                ItemStackKey key = new ItemStackKey(Blocks.BARRIER.asItem());
                required.merge(key, 1, Integer::sum);
            } else {
                required.merge(new ItemStackKey(item.getItem()), 1, Integer::sum);
            }
        }

        Map<ItemStackKey, Integer> missing = new LinkedHashMap<>();
        if (!isCreative(player)) {
            for (Map.Entry<ItemStackKey, Integer> entry : required.entrySet()) {
                int available = BuildingStorageManager.count(player, entry.getKey())
                        + count(player.getInventory(), entry.getKey());
                if (available < entry.getValue()) {
                    missing.put(entry.getKey(), entry.getValue() - available);
                }
            }
        }
        return new BlockCostPlan(required, missing);
    }

    public boolean canAfford() {
        return missing.isEmpty();
    }

    public Map<ItemStackKey, Integer> required() {
        return required;
    }

    public Map<ItemStackKey, Integer> missing() {
        return missing;
    }

    public void consume(ServerPlayer player) {
        if (isCreative(player)) {
            return;
        }
        Inventory inventory = player.getInventory();
        int emptyBuckets = 0;
        for (Map.Entry<ItemStackKey, Integer> entry : required.entrySet()) {
            int remaining = entry.getValue() - BuildingStorageManager.extract(player, entry.getKey(), entry.getValue());
            for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && stack.is(entry.getKey().item())) {
                    int taken = Math.min(remaining, stack.getCount());
                    stack.shrink(taken);
                    remaining -= taken;
                }
            }
            if (entry.getKey().item() == Items.WATER_BUCKET || entry.getKey().item() == Items.LAVA_BUCKET) {
                emptyBuckets += entry.getValue();
            }
        }
        inventory.setChanged();
        if (emptyBuckets > 0) {
            BuildingStorageManager.depositOrGiveStack(player, new ItemStack(Items.BUCKET, emptyBuckets));
        }
    }

    public Component missingMessage() {
        StringBuilder builder = new StringBuilder("Missing materials: ");
        boolean first = true;
        for (Map.Entry<ItemStackKey, Integer> entry : missing.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getValue()).append("x ").append(entry.getKey().displayId());
            first = false;
        }
        return Component.literal(builder.toString());
    }

    private static int count(Inventory inventory, ItemStackKey key) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(key.item())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean isCreative(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        return mode.isCreative();
    }

    private static ItemStack materialItem(BlockState state) {
        if (state.is(Blocks.WATER)) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        if (state.is(Blocks.LAVA)) {
            return new ItemStack(Items.LAVA_BUCKET);
        }
        return new ItemStack(state.getBlock().asItem());
    }
}
