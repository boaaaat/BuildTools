package com.abhil.buildtools.server;

import com.abhil.buildtools.network.BuildToolsNetworking;
import com.abhil.buildtools.network.PreviewPayload;
import com.abhil.buildtools.network.SelectionSyncPayload;
import com.abhil.buildtools.shape.BuildMode;
import com.abhil.buildtools.shape.Selection;
import com.abhil.buildtools.shape.SelectionShape;
import com.abhil.buildtools.shape.ShapeGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BuildToolsState {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, BuildMode> MODES = new HashMap<>();
    private static final Map<UUID, UndoSnapshot> UNDO = new HashMap<>();
    private static final Map<UUID, Blueprint> BLUEPRINTS = new HashMap<>();

    private BuildToolsState() {
    }

    public static Selection selection(ServerPlayer player) {
        return SELECTIONS.computeIfAbsent(player.getUUID(), Selection::empty);
    }

    public static BuildMode mode(ServerPlayer player) {
        return MODES.getOrDefault(player.getUUID(), BuildMode.FILL);
    }

    public static void setFirst(ServerPlayer player, BlockPos pos) {
        Selection selection = selection(player).withFirst(player.level().dimension(), pos);
        SELECTIONS.put(player.getUUID(), selection);
        sync(player);
        player.displayClientMessage(Component.translatable("buildtools.message.first", format(pos)), true);
    }

    public static void setSecond(ServerPlayer player, BlockPos pos) {
        Selection selection = selection(player).withSecond(player.level().dimension(), pos);
        SELECTIONS.put(player.getUUID(), selection);
        sync(player);
        player.displayClientMessage(Component.translatable("buildtools.message.second", format(pos)), true);
    }

    public static void cycleShape(ServerPlayer player) {
        Selection selection = selection(player);
        selection = selection.withShape(selection.shape().next());
        SELECTIONS.put(player.getUUID(), selection);
        sync(player);
        player.displayClientMessage(Component.translatable("buildtools.message.shape", selection.shape().displayName()), true);
    }

    public static void cycleMode(ServerPlayer player) {
        BuildMode next = mode(player).next();
        MODES.put(player.getUUID(), next);
        player.displayClientMessage(Component.translatable("buildtools.message.mode", next.displayName()), true);
    }

    public static void setUndo(ServerPlayer player, UndoSnapshot snapshot) {
        UNDO.put(player.getUUID(), snapshot);
    }

    public static Optional<UndoSnapshot> takeUndo(ServerPlayer player) {
        return Optional.ofNullable(UNDO.remove(player.getUUID()));
    }

    public static void setBlueprint(ServerPlayer player, Blueprint blueprint) {
        BLUEPRINTS.put(player.getUUID(), blueprint);
    }

    public static Optional<Blueprint> blueprint(ServerPlayer player) {
        return Optional.ofNullable(BLUEPRINTS.get(player.getUUID()));
    }

    public static void clearPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        SELECTIONS.remove(uuid);
        MODES.remove(uuid);
        UNDO.remove(uuid);
        BLUEPRINTS.remove(uuid);
    }

    public static void sync(ServerPlayer player) {
        Selection selection = selection(player);
        PacketDistributor.sendToPlayer(player, new SelectionSyncPayload(
                selection.dimension() == null ? "" : selection.dimension().location().toString(),
                selection.firstOptional(),
                selection.secondOptional(),
                selection.shape()));
        sendPreview(player);
    }

    public static void sendPreview(ServerPlayer player) {
        Selection selection = selection(player);
        List<BlockPos> preview = selection.dimension() != null
                && selection.dimension().equals(player.level().dimension())
                ? ShapeGenerator.generate(selection)
                : List.of();
        if (preview.size() > BuildToolsNetworking.MAX_PREVIEW_POSITIONS) {
            preview = preview.subList(0, BuildToolsNetworking.MAX_PREVIEW_POSITIONS);
        }
        PacketDistributor.sendToPlayer(player, new PreviewPayload(preview));
    }

    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
