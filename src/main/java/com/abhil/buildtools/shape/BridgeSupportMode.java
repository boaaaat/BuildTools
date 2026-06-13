package com.abhil.buildtools.shape;

import net.minecraft.network.chat.Component;

public enum BridgeSupportMode {
    NONE("none"),
    POSTS("posts"),
    ARCHES("arches");

    private final String key;

    BridgeSupportMode(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.bridge_support." + key);
    }

    public BridgeSupportMode next(int step) {
        BridgeSupportMode[] values = values();
        return values[Math.floorMod(ordinal() + step, values.length)];
    }
}
