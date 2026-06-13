package com.abhil.buildtools.shape;

import net.minecraft.network.chat.Component;

public enum TowerTopStyle {
    FLAT("flat"),
    BATTLEMENTS("battlements"),
    ROOF("roof");

    private final String key;

    TowerTopStyle(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.tower_top." + key);
    }

    public TowerTopStyle next(int step) {
        TowerTopStyle[] values = values();
        return values[Math.floorMod(ordinal() + step, values.length)];
    }
}
