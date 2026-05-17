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
        GradientDirection[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
