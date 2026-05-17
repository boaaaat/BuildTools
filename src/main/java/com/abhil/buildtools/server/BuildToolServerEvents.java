package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.server.BuildToolsState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class BuildToolServerEvents {
    private BuildToolServerEvents() {
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        BuildOperationEngine.tick();
        BuildToolActionbar.tick(event.getServer());
        MagnetCollector.tick(event.getServer());
        BuildingStorageManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        boolean selectionStaff = event.getItemStack().is(ModItems.SELECTION_STAFF.get());
        boolean advancedSelectionStaff = event.getItemStack().is(ModItems.ADVANCED_SELECTION_STAFF.get());
        boolean menuTool = isMenuTool(event.getItemStack());
        if (!selectionStaff && !advancedSelectionStaff && !menuTool) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            if (selectionStaff) {
                BuildToolsState.setFirst(player, event.getPos());
            } else if (advancedSelectionStaff && BuildToolsState.beginAdvancedSelectionAction(player)) {
                if (player.isShiftKeyDown()) {
                    BuildToolsState.addAdvancedPoint(player, event.getPos().relative(event.getFace()));
                } else {
                    net.minecraft.core.BlockPos target = BuildToolsState.advancedSelectionTarget(player).orElse(event.getPos());
                    BuildToolsState.removeAdvancedPoint(player, target);
                }
            } else if (event.getItemStack().is(ModItems.ADVANCED_BUILDER_WAND.get())) {
                AdvancedBuildToolsModeMenu.open(player);
            } else {
                BuildToolsModeMenu.open(player);
            }
        }
    }

    private static boolean isMenuTool(net.minecraft.world.item.ItemStack stack) {
        return stack.is(ModItems.BUILDER_WAND.get())
                || stack.is(ModItems.ADVANCED_BUILDER_WAND.get())
                || stack.is(ModItems.BUILDER_BRUSH.get())
                || stack.is(ModItems.AREA_BREAKER.get())
                || stack.is(ModItems.BLUEPRINT_TROWEL.get())
                || stack.is(ModItems.UNDO_TOKEN.get())
                || stack.is(ModItems.REDO_TOKEN.get());
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            BuildingStorageManager.unmark(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BuildToolsState.loadPlayer(player);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BuildToolsState.savePlayer(player);
            BuildToolsState.discardLoadedPlayer(player);
        }
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BuildToolsState.sync(player);
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BuildToolsState.sync(player);
        }
    }
}
