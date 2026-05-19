package com.abhil.buildtools.server;

import net.minecraft.server.level.ServerPlayer;

public final class ToolMenuNavigation {
    private ToolMenuNavigation() {
    }

    public static void openActiveToolMenu(ServerPlayer player) {
        switch (BuildToolsState.activeToolProfile(player)) {
            case ADVANCED_BUILDER, ADVANCED_SELECTION -> AdvancedBuildToolsModeMenu.open(player);
            default -> BuildToolsModeMenu.open(player);
        }
    }
}
