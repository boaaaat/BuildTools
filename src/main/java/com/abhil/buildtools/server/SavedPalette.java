package com.abhil.buildtools.server;

import java.util.List;

public record SavedPalette(String name, List<PaletteEntry> entries) {
    public SavedPalette {
        name = name == null || name.isBlank() ? "Palette" : name.strip();
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
