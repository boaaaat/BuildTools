package com.abhil.buildtools.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class BuildToolServerEvents {
    private BuildToolServerEvents() {
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        BuildOperationEngine.tick();
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BuildToolsState.clearPlayer(player);
        }
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BuildToolsState.clearPlayer(player);
        }
    }
}
