package com.abhil.buildtools.shape;

import net.minecraft.network.chat.Component;

public enum BrushMode {
    PAINT("paint"),
    ERASE("erase"),
    REPLACE("replace"),
    SMOOTH("smooth"),
    SCATTER("scatter"),
    OVERLAY("overlay"),
    BLEND("blend");

    private final String key;

    BrushMode(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.brush." + key);
    }

    public BrushMode next() {
        BrushMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
