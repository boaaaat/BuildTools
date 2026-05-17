package com.abhil.buildtools.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ItemStackKey(Item item) {
    public ItemStack stack(int count) {
        return new ItemStack(item, count);
    }

    public String displayId() {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? item.toString() : id.toString();
    }
}
