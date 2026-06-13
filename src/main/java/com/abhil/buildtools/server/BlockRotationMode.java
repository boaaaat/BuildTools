package com.abhil.buildtools.server;

import net.minecraft.network.chat.Component;

public enum BlockRotationMode {
    UNCHANGED("unchanged"),
    FIXED("fixed"),
    FACE_CENTER("face_center"),
    FACE_AWAY_CENTER("face_away_center"),
    FOLLOW_PATH("follow_path");

    private final String key;

    BlockRotationMode(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.rotation_mode." + key);
    }

    public Component description() {
        return Component.translatable("buildtools.rotation_mode." + key + ".description");
    }

    public BlockRotationMode next(int step) {
        BlockRotationMode[] values = values();
        return values[Math.floorMod(ordinal() + (step >= 0 ? 1 : -1), values.length)];
    }
}
