package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.server.BuildToolsState;
import com.abhil.buildtools.shape.SelectionShape;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ScrollToolPayload(int direction, boolean alternate) implements CustomPacketPayload {
    public static final Type<ScrollToolPayload> TYPE = new Type<>(BuildTools.id("scroll_tool"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScrollToolPayload> STREAM_CODEC = CustomPacketPayload.codec(
            ScrollToolPayload::write,
            ScrollToolPayload::read);

    private static ScrollToolPayload read(RegistryFriendlyByteBuf buffer) {
        return new ScrollToolPayload(buffer.readVarInt(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(direction);
        buffer.writeBoolean(alternate);
    }

    public static void handle(ScrollToolPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        int step = payload.direction() >= 0 ? 1 : -1;
        ItemStack held = player.getMainHandItem();
        if (isShapeOptionScrollTool(held) && BuildToolsState.selectionShape(player) == SelectionShape.ROAD) {
            BuildToolsState.changeRoadWidth(player, step);
            return;
        }
        if (isShapeOptionScrollTool(held) && BuildToolsState.selectionShape(player) == SelectionShape.ARCH) {
            BuildToolsState.changeArchPeak(player, step);
            return;
        }
        if (held.is(ModItems.ADVANCED_SELECTION_STAFF.get())) {
            if (BuildToolsState.selectionShape(player) == SelectionShape.STAIRS) {
                BuildToolsState.cycleStairDirection(player, step);
                return;
            }
            int orderDelta = payload.direction() >= 0 ? -1 : 1;
            if (!BuildToolsState.moveAdvancedPointAtLook(player, orderDelta)) {
                BuildToolsState.cycleShape(player);
            }
        } else if (held.is(ModItems.AREA_BREAKER.get())) {
            if (BuildToolsState.selectionShape(player) == SelectionShape.STAIRS) {
                BuildToolsState.cycleStairDirection(player, step);
            } else {
                BuildToolsState.cycleShape(player);
            }
        } else if (held.is(ModItems.SELECTION_STAFF.get())) {
            BuildToolsState.cycleShape(player);
        } else if (held.is(ModItems.ADVANCED_BUILDER_WAND.get())) {
            if (BuildToolsState.selectionShape(player) == SelectionShape.STAIRS) {
                BuildToolsState.cycleStairDirection(player, step);
            } else {
                BuildToolsState.cycleMode(player);
            }
        } else if (held.is(ModItems.BUILDER_WAND.get())) {
            BuildToolsState.cycleMode(player);
        }
    }

    private static boolean isShapeOptionScrollTool(ItemStack held) {
        return held.is(ModItems.SELECTION_STAFF.get())
                || held.is(ModItems.ADVANCED_SELECTION_STAFF.get())
                || held.is(ModItems.BUILDER_WAND.get())
                || held.is(ModItems.ADVANCED_BUILDER_WAND.get())
                || held.is(ModItems.AREA_BREAKER.get());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
