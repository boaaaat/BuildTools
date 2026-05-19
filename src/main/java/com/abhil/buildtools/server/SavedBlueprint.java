package com.abhil.buildtools.server;

public record SavedBlueprint(String name, String category, long createdOrder, long lastUsedTick, Blueprint blueprint) {
    public static final String DEFAULT_CATEGORY = "Uncategorized";

    public SavedBlueprint(String name, Blueprint blueprint) {
        this(name, DEFAULT_CATEGORY, 0L, 0L, blueprint);
    }

    public SavedBlueprint {
        name = normalize(name, "Blueprint");
        category = normalize(category, DEFAULT_CATEGORY);
        blueprint = blueprint == null ? new Blueprint(java.util.List.of(), java.util.List.of()) : blueprint;
    }

    public SavedBlueprint withName(String newName) {
        return new SavedBlueprint(newName, category, createdOrder, lastUsedTick, blueprint);
    }

    public SavedBlueprint withCategory(String newCategory) {
        return new SavedBlueprint(name, newCategory, createdOrder, lastUsedTick, blueprint);
    }

    public SavedBlueprint markUsed(long tick) {
        return new SavedBlueprint(name, category, createdOrder, tick, blueprint);
    }

    private static String normalize(String value, String fallback) {
        String normalized = value == null ? "" : value.strip();
        return normalized.isBlank() ? fallback : normalized;
    }
}
