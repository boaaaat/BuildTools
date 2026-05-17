package com.abhil.buildtools.server;

import net.minecraft.nbt.CompoundTag;

public record CapturedEntity(double offsetX, double offsetY, double offsetZ, CompoundTag tag) {
    public CapturedEntity {
        tag = tag.copy();
    }

    public CapturedEntity withOffset(double x, double y, double z) {
        return new CapturedEntity(x, y, z, tag);
    }
}
