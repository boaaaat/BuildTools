package com.abhil.buildtools.shape;

import net.minecraft.network.chat.Component;

public enum RoofDirection {
    AUTO("auto"),
    X("x"),
    Z("z");

    private final String key;

    RoofDirection(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.roof_direction." + key);
    }

    public RoofDirection next() {
        return next(1);
    }

    public RoofDirection next(int step) {
        RoofDirection[] values = values();
        return values[Math.floorMod(ordinal() + step, values.length)];
    }
}
