package com.abhil.buildtools.shape;

import net.minecraft.network.chat.Component;

public enum ShapeDetailMode {
    PLAIN("plain"),
    DETAILED("detailed");

    private final String key;

    ShapeDetailMode(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.shape_detail." + key);
    }

    public ShapeDetailMode next() {
        return next(1);
    }

    public ShapeDetailMode next(int step) {
        ShapeDetailMode[] values = values();
        return values[Math.floorMod(ordinal() + step, values.length)];
    }
}
