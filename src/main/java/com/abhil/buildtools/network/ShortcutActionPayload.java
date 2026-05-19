package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import com.abhil.buildtools.server.BuildOperationEngine;
import com.abhil.buildtools.server.BuildToolsModeMenu;
import com.abhil.buildtools.server.BuildToolsState;
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
            case "cycle_shape" -> BuildToolsState.cycleShape(player);
            case "cycle_mode" -> BuildToolsState.cycleMode(player);
            case "confirm_preview" -> confirm(player);
            case "cancel_preview" -> cancel(player);
            case "undo" -> BuildOperationEngine.undo(player);
            case "redo" -> BuildOperationEngine.redo(player);
            case "nudge" -> nudge(player, payload.direction(), payload.amount());
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

    private static void confirm(ServerPlayer player) {
        if (!BuildOperationEngine.confirmPendingOperation(player)) {
            BuildOperationEngine.confirmPendingBlueprintPaste(player);
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
        int steps = Math.max(1, Math.min(10, Math.abs(amount)));
        for (int i = 0; i < steps; i++) {
            if (BuildToolsState.pendingPasteOrigin(player).isPresent()) {
                BuildOperationEngine.nudgePendingBlueprintPaste(player, direction);
            } else {
                BuildToolsState.nudgeSelection(player, direction);
            }
        }
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
