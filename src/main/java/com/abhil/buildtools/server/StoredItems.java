package com.abhil.buildtools.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

public final class StoredItems {
    private StoredItems() {
    }

    public static List<ItemStack> copyOf(List<ItemStack> stacks) {
        List<ItemStack> copied = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                copied.add(stack.copy());
            }
        }
        return List.copyOf(copied);
    }

    public static List<ItemStack> fromCounts(Map<ItemStackKey, Integer> items) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<ItemStackKey, Integer> entry : items.entrySet()) {
            int remaining = entry.getValue();
            int max = entry.getKey().stack(1).getMaxStackSize();
            while (remaining > 0) {
                int count = Math.min(remaining, max);
                stacks.add(entry.getKey().stack(count));
                remaining -= count;
            }
        }
        return List.copyOf(stacks);
    }

    public static Map<ItemStackKey, Integer> toCounts(List<ItemStack> stacks) {
        Map<ItemStackKey, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                counts.merge(new ItemStackKey(stack.getItem()), stack.getCount(), Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    public static int total(List<ItemStack> stacks) {
        return stacks.stream().filter(stack -> !stack.isEmpty()).mapToInt(ItemStack::getCount).sum();
    }
}
