package com.abhil.buildtools.server;

public record NamedSelectionPreset(String name, SelectionPreset preset) {
    public NamedSelectionPreset {
        name = name == null || name.isBlank() ? "Preset" : name.strip();
    }
}
