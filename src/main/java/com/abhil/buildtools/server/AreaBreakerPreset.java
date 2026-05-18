package com.abhil.buildtools.server;

import net.minecraft.network.chat.Component;

public enum AreaBreakerPreset {
    NORMAL("normal"),
    CLEAR_SNOW_CROPS("clear_snow_crops");

    private final String key;

    AreaBreakerPreset(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.area_breaker_preset." + key);
    }
}
