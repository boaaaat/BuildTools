package com.abhil.buildtools.shape;

import net.minecraft.network.chat.Component;

public enum ArchDirection {
    X("x"),
    Z("z");

    private final String key;

    ArchDirection(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.arch_direction." + key);
    }

    public ArchDirection next() {
        ArchDirection[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
