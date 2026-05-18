package com.abhil.buildtools.server;

public record SavedBlueprint(String name, Blueprint blueprint) {
    public SavedBlueprint {
        name = name == null ? "" : name;
    }
}
