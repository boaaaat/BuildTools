package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import com.abhil.buildtools.server.BuildOperationEngine;
import com.abhil.buildtools.server.BuildToolsModeMenu;
import com.abhil.buildtools.server.BuildToolsState;
import com.abhil.buildtools.server.MaterialSelectionMenu;
import com.abhil.buildtools.shape.SelectionShape;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShortcutActionPayload(String action, String direction, int amount) implements CustomPacketPayload {
    public static final Type<ShortcutActionPayload> TYPE = new Type<>(BuildTools.id("shortcut_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShortcutActionPayload> STREAM_CODEC = CustomPacketPayload.codec(
            ShortcutActionPayload::write,
            ShortcutActionPayload::read);

    private static ShortcutActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new ShortcutActionPayload(buffer.readUtf(32), buffer.readUtf(16), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(action, 32);
        buffer.writeUtf(direction == null ? "" : direction, 16);
        buffer.writeVarInt(amount);
    }

    public static void handle(ShortcutActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        switch (payload.action()) {
            case "open_menu" -> openMenu(player);
            case "open_materials" -> openMaterials(player);
            case "cycle_shape" -> BuildToolsState.cycleShape(player);
            case "cycle_mode" -> BuildToolsState.cycleMode(player);
            case "confirm_preview" -> confirm(player);
            case "cancel_preview" -> cancel(player);
            case "undo" -> BuildOperationEngine.undo(player);
            case "redo" -> BuildOperationEngine.redo(player);
            case "apply_brush" -> applyBrush(player);
            case "nudge" -> nudge(player, payload.direction(), payload.amount());
            case "nudge_relative" -> nudgeRelative(player, payload.direction(), payload.amount());
            case "cycle_shape_step" -> cycleShape(player, payload.amount());
            case "cycle_mode_step" -> cycleMode(player, payload.amount());
            case "adjust_option" -> adjustCurrentOption(player, payload.amount());
            case "toggle_shape_option" -> toggleShapeOption(player);
            default -> {
            }
        }
    }

    private static void openMenu(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.is(ModItems.ADVANCED_BUILDER_WAND.get()) || held.is(ModItems.ADVANCED_SELECTION_STAFF.get())) {
            AdvancedBuildToolsModeMenu.open(player);
        } else if (isMenuTool(held)) {
            BuildToolsModeMenu.open(player);
        }
    }

    private static boolean isMenuTool(ItemStack stack) {
        return stack.is(ModItems.SELECTION_STAFF.get())
                || stack.is(ModItems.BUILDER_WAND.get())
                || stack.is(ModItems.BUILDER_BRUSH.get())
                || stack.is(ModItems.AREA_BREAKER.get())
                || stack.is(ModItems.BLUEPRINT_TROWEL.get())
                || stack.is(ModItems.UNDO_TOKEN.get())
                || stack.is(ModItems.REDO_TOKEN.get());
    }

    private static boolean isShortcutBuildTool(ItemStack stack) {
        return isShapeTool(stack)
                || stack.is(ModItems.BLUEPRINT_TROWEL.get())
                || stack.is(ModItems.UNDO_TOKEN.get())
                || stack.is(ModItems.REDO_TOKEN.get());
    }

    private static boolean isShapeTool(ItemStack stack) {
        return stack.is(ModItems.SELECTION_STAFF.get())
                || stack.is(ModItems.ADVANCED_SELECTION_STAFF.get())
                || stack.is(ModItems.BUILDER_WAND.get())
                || stack.is(ModItems.ADVANCED_BUILDER_WAND.get())
                || stack.is(ModItems.BUILDER_BRUSH.get())
                || stack.is(ModItems.AREA_BREAKER.get());
    }

    private static boolean isModeTool(ItemStack stack) {
        return stack.is(ModItems.BUILDER_WAND.get())
                || stack.is(ModItems.ADVANCED_BUILDER_WAND.get());
    }

    private static boolean isBrushTool(ItemStack stack) {
        return stack.is(ModItems.BUILDER_BRUSH.get());
    }

    private static boolean isMaterialSelectionTool(ItemStack stack) {
        return stack.is(ModItems.BUILDER_WAND.get())
                || stack.is(ModItems.ADVANCED_BUILDER_WAND.get())
                || stack.is(ModItems.BUILDER_BRUSH.get());
    }

    private static void openMaterials(ServerPlayer player) {
        if (isMaterialSelectionTool(player.getMainHandItem())) {
            MaterialSelectionMenu.open(player);
        }
    }

    private static void confirm(ServerPlayer player) {
        if (!BuildOperationEngine.confirmPendingOperation(player)) {
            BuildOperationEngine.confirmPendingBlueprintPaste(player);
        }
    }

    private static void applyBrush(ServerPlayer player) {
        if (isBrushTool(player.getMainHandItem())) {
            BuildOperationEngine.applyBrushAtLook(player);
        }
    }

    private static void cancel(ServerPlayer player) {
        BuildOperationEngine.clearPendingOperation(player);
        BuildToolsState.clearPendingPaste(player);
    }

    private static void nudge(ServerPlayer player, String directionName, int amount) {
        Direction direction = parseDirection(directionName);
        if (direction == null) {
            return;
        }
        nudge(player, direction, amount);
    }

    private static void nudgeRelative(ServerPlayer player, String relativeDirection, int amount) {
        if (!isShortcutBuildTool(player.getMainHandItem())) {
            return;
        }
        Direction direction = relativeDirection(relativeDirection, player.getDirection());
        if (direction == null) {
            return;
        }
        nudge(player, direction, amount);
    }

    private static void nudge(ServerPlayer player, Direction direction, int amount) {
        int steps = Math.max(1, Math.min(10, Math.abs(amount)));
        for (int i = 0; i < steps; i++) {
            if (BuildToolsState.pendingPasteOrigin(player).isPresent()) {
                BuildOperationEngine.nudgePendingBlueprintPaste(player, direction);
            } else {
                BuildToolsState.nudgeSelection(player, direction);
            }
        }
    }

    private static void cycleShape(ServerPlayer player, int step) {
        if (isShapeTool(player.getMainHandItem())) {
            BuildToolsState.cycleShape(player, step);
        }
    }

    private static void cycleMode(ServerPlayer player, int step) {
        if (isModeTool(player.getMainHandItem())) {
            BuildToolsState.cycleMode(player, step);
        }
    }

    private static void adjustCurrentOption(ServerPlayer player, int step) {
        ItemStack held = player.getMainHandItem();
        SelectionShape shape = BuildToolsState.selectionShape(player);
        if (isShapeTool(held) && shape == SelectionShape.ROAD) {
            BuildToolsState.changeRoadWidth(player, step);
        } else if (isShapeTool(held) && shape == SelectionShape.ARCH) {
            BuildToolsState.changeArchPeak(player, step);
        } else if (isShapeTool(held) && shape == SelectionShape.STAIRS) {
            BuildToolsState.cycleStairDirection(player, step);
        } else if (isBrushTool(held)) {
            BuildToolsState.changeBrushRadius(player, step);
        } else if (isModeTool(held)) {
            BuildToolsState.cycleMode(player, step);
        } else if (isShapeTool(held)) {
            BuildToolsState.cycleShape(player, step);
        }
    }

    private static void toggleShapeOption(ServerPlayer player) {
        if (!isShapeTool(player.getMainHandItem())) {
            return;
        }
        SelectionShape shape = BuildToolsState.selectionShape(player);
        if (shape == SelectionShape.ARCH) {
            BuildToolsState.toggleArchEdgeWalls(player);
        } else if (shape == SelectionShape.SPHERE || shape == SelectionShape.ELLIPSOID) {
            BuildToolsState.toggleShapeHollow(player, shape);
        }
    }

    private static Direction relativeDirection(String value, Direction facing) {
        return switch (value) {
            case "LEFT" -> facing.getCounterClockWise();
            case "RIGHT" -> facing.getClockWise();
            case "FORWARD" -> facing;
            case "BACK" -> facing.getOpposite();
            case "UP" -> Direction.UP;
            case "DOWN" -> Direction.DOWN;
            default -> null;
        };
    }

    private static Direction parseDirection(String value) {
        try {
            return value == null || value.isBlank() ? null : Direction.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
