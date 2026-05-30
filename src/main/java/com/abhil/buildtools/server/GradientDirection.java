package com.abhil.buildtools.server;

import net.minecraft.network.chat.Component;

public enum GradientDirection {
    X("x"),
    Y("y"),
    Z("z"),
    POINT_ORDER("point_order");

    private final String key;

    GradientDirection(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.gradient_direction." + key);
    }

    public GradientDirection next() {
        return next(1);
    }

    public GradientDirection next(int step) {
        GradientDirection[] values = values();
        return values[Math.floorMod(ordinal() + (step >= 0 ? 1 : -1), values.length)];
    }
}
