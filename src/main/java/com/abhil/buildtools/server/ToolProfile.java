package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModItems;
import net.minecraft.world.item.ItemStack;

public enum ToolProfile {
    SELECTION,
    ADVANCED_SELECTION,
    BUILDER,
    ADVANCED_BUILDER,
    BRUSH,
    BREAKER,
    TROWEL,
    UNDO,
    REDO;

    public static ToolProfile from(ItemStack stack) {
        if (stack.is(ModItems.SELECTION_STAFF.get())) {
            return SELECTION;
        }
        if (stack.is(ModItems.ADVANCED_SELECTION_STAFF.get())) {
            return ADVANCED_SELECTION;
        }
        if (stack.is(ModItems.BUILDER_WAND.get())) {
            return BUILDER;
        }
        if (stack.is(ModItems.ADVANCED_BUILDER_WAND.get())) {
            return ADVANCED_BUILDER;
        }
        if (stack.is(ModItems.BUILDER_BRUSH.get())) {
            return BRUSH;
        }
        if (stack.is(ModItems.AREA_BREAKER.get())) {
            return BREAKER;
        }
        if (stack.is(ModItems.BLUEPRINT_TROWEL.get())) {
            return TROWEL;
        }
        if (stack.is(ModItems.UNDO_TOKEN.get())) {
            return UNDO;
        }
        if (stack.is(ModItems.REDO_TOKEN.get())) {
            return REDO;
        }
        return BUILDER;
    }
}
