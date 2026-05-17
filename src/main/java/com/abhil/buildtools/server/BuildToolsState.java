package com.abhil.buildtools.server;

import com.abhil.buildtools.network.BuildToolsNetworking;
import com.abhil.buildtools.network.PreviewPayload;
import com.abhil.buildtools.network.SelectionSyncPayload;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.shape.BrushMode;
import com.abhil.buildtools.shape.BuildMode;
import com.abhil.buildtools.shape.Selection;
import com.abhil.buildtools.shape.SelectionShape;
import com.abhil.buildtools.shape.ShapeGenerator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BuildToolsState {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, EnumMap<ToolProfile, BuildMode>> MODES = new HashMap<>();
    private static final Map<UUID, EnumMap<ToolProfile, SelectionShape>> SHAPES = new HashMap<>();
    private static final Map<UUID, Deque<UndoSnapshot>> UNDO = new HashMap<>();
    private static final Map<UUID, Deque<UndoSnapshot>> REDO = new HashMap<>();
    private static final Map<UUID, Blueprint> BLUEPRINTS = new HashMap<>();
    private static final Map<UUID, BlockPos> PENDING_PASTE_ORIGINS = new HashMap<>();
    private static final Map<UUID, BlockState> REPLACE_TARGETS = new HashMap<>();
    private static final Map<UUID, EnumMap<ToolProfile, List<PaletteEntry>>> PALETTES = new HashMap<>();
    private static final Map<UUID, SelectionPreset> PRESETS = new HashMap<>();
    private static final Map<UUID, BuildPlan> PLANS = new HashMap<>();
    private static final Map<UUID, EnumMap<ToolProfile, BrushMode>> BRUSH_MODES = new HashMap<>();
    private static final Map<UUID, EnumMap<ToolProfile, Integer>> BRUSH_RADII = new HashMap<>();
    private static final Map<UUID, EnumMap<ToolProfile, Boolean>> GRADIENTS = new HashMap<>();
    private static final Map<UUID, EnumMap<ToolProfile, PaletteMode>> PALETTE_MODES = new HashMap<>();
    private static final Map<UUID, EnumMap<ToolProfile, GradientDirection>> GRADIENT_DIRECTIONS = new HashMap<>();
    private static final Map<UUID, List<BlockPos>> ADVANCED_POINTS = new HashMap<>();
    private static final Map<UUID, Boolean> SHARED_SELECTION_VISIBILITY = new HashMap<>();
    private static final int HISTORY_LIMIT = 10;
    private static final int MIN_BRUSH_RADIUS = 1;
    private static final int MAX_BRUSH_RADIUS = 8;
    private static final int ADVANCED_SELECTION_ACTION_COOLDOWN = 6;
    private static final double ADVANCED_SELECTION_DISTANCE = 100.0D;
    private static final double ADVANCED_POINT_PICK_DISTANCE_SQR = 6.25D;

    private BuildToolsState() {
    }

    public static Selection selection(ServerPlayer player) {
        return SELECTIONS.computeIfAbsent(player.getUUID(), Selection::empty);
    }

    public static BuildMode mode(ServerPlayer player) {
        return modes(player).getOrDefault(activeProfile(player), BuildMode.FILL);
    }

    public static SelectionShape selectionShape(ServerPlayer player) {
        return shape(player);
    }

    public static BrushMode brushMode(ServerPlayer player) {
        return brushModes(player).getOrDefault(activeProfile(player), BrushMode.SPHERE);
    }

    public static int brushRadius(ServerPlayer player) {
        return brushRadii(player).getOrDefault(activeProfile(player), 2);
    }

    public static boolean gradientEnabled(ServerPlayer player) {
        return paletteMode(player) == PaletteMode.GRADIENT || gradients(player).getOrDefault(activeProfile(player), false);
    }

    public static PaletteMode paletteMode(ServerPlayer player) {
        ToolProfile profile = activeProfile(player);
        PaletteMode mode = paletteModes(player).get(profile);
        if (mode != null) {
            return mode;
        }
        return gradients(player).getOrDefault(profile, false) ? PaletteMode.GRADIENT : PaletteMode.SINGLE;
    }

    public static boolean randomPatternEnabled(ServerPlayer player) {
        return paletteMode(player) == PaletteMode.RANDOM;
    }

    public static GradientDirection gradientDirection(ServerPlayer player) {
        return gradientDirections(player).getOrDefault(activeProfile(player), GradientDirection.Y);
    }

    public static ToolProfile activeToolProfile(ServerPlayer player) {
        return activeProfile(player);
    }

    public static boolean selectionVisibleToOthers(ServerPlayer player) {
        return SHARED_SELECTION_VISIBILITY.getOrDefault(player.getUUID(), false);
    }

    public static void toggleSelectionVisibility(ServerPlayer player) {
        boolean visible = !selectionVisibleToOthers(player);
        SHARED_SELECTION_VISIBILITY.put(player.getUUID(), visible);
        persist(player);
        if (visible) {
            syncSharedSelectionFrom(player);
        } else {
            removeSharedSelectionFrom(player);
        }
        player.displayClientMessage(Component.translatable(visible
                ? "buildtools.message.selection_visible_to_others"
                : "buildtools.message.selection_hidden_from_others"), true);
    }

    public static void stopSharingSelection(ServerPlayer player) {
        removeSharedSelectionFrom(player);
    }

    public static void loadPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        clearInMemory(uuid);
        CompoundTag tag = BuildToolsPlayerStateData.get(player).getPlayer(uuid);
        if (!tag.isEmpty()) {
            readState(player, tag, player.serverLevel().registryAccess());
        }
        sync(player);
    }

    public static void savePlayer(ServerPlayer player) {
        persist(player);
    }

    public static void discardLoadedPlayer(ServerPlayer player) {
        clearInMemory(player.getUUID());
    }

    public static void setFirst(ServerPlayer player, BlockPos pos) {
        Selection selection = selection(player).withFirst(player.level().dimension(), pos).withShape(shape(player));
        SELECTIONS.put(player.getUUID(), selection);
        PENDING_PASTE_ORIGINS.remove(player.getUUID());
        BuildOperationEngine.clearPendingOperation(player);
        sync(player);
        player.displayClientMessage(Component.translatable("buildtools.message.first", format(pos)), true);
    }

    public static void setSecond(ServerPlayer player, BlockPos pos) {
        Selection selection = selection(player).withSecond(player.level().dimension(), pos).withShape(shape(player));
        SELECTIONS.put(player.getUUID(), selection);
        PENDING_PASTE_ORIGINS.remove(player.getUUID());
        BuildOperationEngine.clearPendingOperation(player);
        sync(player);
        player.displayClientMessage(Component.translatable("buildtools.message.second", format(pos)), true);
    }

    public static void cycleShape(ServerPlayer player) {
        setShape(player, shape(player).next());
    }

    public static void setShape(ServerPlayer player, SelectionShape shape) {
        shapes(player).put(activeProfile(player), shape);
        Selection selection = selection(player).withShape(shape);
        SELECTIONS.put(player.getUUID(), selection);
        BuildOperationEngine.clearPendingOperation(player);
        sync(player);
        player.displayClientMessage(Component.translatable("buildtools.message.shape", selection.shape().displayName()), true);
    }

    public static void cycleMode(ServerPlayer player) {
        setMode(player, mode(player).next());
    }

    public static void setMode(ServerPlayer player, BuildMode mode) {
        modes(player).put(activeProfile(player), mode);
        BuildOperationEngine.clearPendingOperation(player);
        player.displayClientMessage(Component.translatable("buildtools.message.mode", mode.displayName()), true);
        sendPreview(player);
    }

    public static void setUndo(ServerPlayer player, UndoSnapshot snapshot) {
        pushLimited(UNDO.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>()), snapshot);
        REDO.remove(player.getUUID());
        persist(player);
    }

    public static Optional<UndoSnapshot> takeUndo(ServerPlayer player) {
        Deque<UndoSnapshot> history = UNDO.get(player.getUUID());
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        Optional<UndoSnapshot> snapshot = Optional.of(history.removeFirst());
        persist(player);
        return snapshot;
    }

    public static Optional<UndoSnapshot> peekUndo(ServerPlayer player) {
        Deque<UndoSnapshot> history = UNDO.get(player.getUUID());
        return history == null || history.isEmpty() ? Optional.empty() : Optional.of(history.peekFirst());
    }

    public static void setRedo(ServerPlayer player, UndoSnapshot snapshot) {
        pushLimited(REDO.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>()), snapshot);
        persist(player);
    }

    public static void clearRedo(ServerPlayer player) {
        REDO.remove(player.getUUID());
        persist(player);
    }

    public static void clearHistory(ServerPlayer player) {
        UNDO.remove(player.getUUID());
        REDO.remove(player.getUUID());
        persist(player);
    }

    public static Optional<UndoSnapshot> takeRedo(ServerPlayer player) {
        Deque<UndoSnapshot> history = REDO.get(player.getUUID());
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        Optional<UndoSnapshot> snapshot = Optional.of(history.removeFirst());
        persist(player);
        return snapshot;
    }

    public static Optional<UndoSnapshot> peekRedo(ServerPlayer player) {
        Deque<UndoSnapshot> history = REDO.get(player.getUUID());
        return history == null || history.isEmpty() ? Optional.empty() : Optional.of(history.peekFirst());
    }

    public static int undoCount(ServerPlayer player) {
        Deque<UndoSnapshot> history = UNDO.get(player.getUUID());
        return history == null ? 0 : history.size();
    }

    public static int redoCount(ServerPlayer player) {
        Deque<UndoSnapshot> history = REDO.get(player.getUUID());
        return history == null ? 0 : history.size();
    }

    public static List<UndoSnapshot> undoHistory(ServerPlayer player) {
        Deque<UndoSnapshot> history = UNDO.get(player.getUUID());
        return history == null ? List.of() : List.copyOf(history);
    }

    public static List<UndoSnapshot> redoHistory(ServerPlayer player) {
        Deque<UndoSnapshot> history = REDO.get(player.getUUID());
        return history == null ? List.of() : List.copyOf(history);
    }

    public static Map<ItemStackKey, Integer> storedDrops(ServerPlayer player) {
        Map<ItemStackKey, Integer> drops = new LinkedHashMap<>();
        Deque<UndoSnapshot> history = UNDO.get(player.getUUID());
        if (history != null) {
            for (UndoSnapshot snapshot : history) {
                StoredItems.toCounts(snapshot.producedDrops()).forEach((key, count) -> drops.merge(key, count, Integer::sum));
            }
        }
        return Map.copyOf(drops);
    }

    public static List<ItemStack> storedDropStacks(ServerPlayer player) {
        List<ItemStack> drops = new ArrayList<>();
        Deque<UndoSnapshot> history = UNDO.get(player.getUUID());
        if (history != null) {
            for (UndoSnapshot snapshot : history) {
                drops.addAll(StoredItems.copyOf(snapshot.producedDrops()));
            }
        }
        return List.copyOf(drops);
    }

    public static void setBlueprint(ServerPlayer player, Blueprint blueprint) {
        BLUEPRINTS.put(player.getUUID(), blueprint);
        PENDING_PASTE_ORIGINS.remove(player.getUUID());
        BuildOperationEngine.clearPendingOperation(player);
        sendPreview(player);
    }

    public static Optional<Blueprint> blueprint(ServerPlayer player) {
        return Optional.ofNullable(BLUEPRINTS.get(player.getUUID()));
    }

    public static void rotateBlueprint(ServerPlayer player) {
        transformBlueprint(player, "buildtools.message.blueprint_rotated", offset -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX()));
    }

    public static void mirrorBlueprintX(ServerPlayer player) {
        transformBlueprint(player, "buildtools.message.blueprint_mirrored_x", offset -> new BlockPos(-offset.getX(), offset.getY(), offset.getZ()));
    }

    public static void mirrorBlueprintZ(ServerPlayer player) {
        transformBlueprint(player, "buildtools.message.blueprint_mirrored_z", offset -> new BlockPos(offset.getX(), offset.getY(), -offset.getZ()));
    }

    public static void setPlan(ServerPlayer player, BuildPlan plan) {
        PLANS.put(player.getUUID(), plan);
        persist(player);
        player.displayClientMessage(Component.translatable("buildtools.message.plan_saved", plan.entries().size()), true);
        sendPlanPreview(player);
    }

    public static Optional<BuildPlan> plan(ServerPlayer player) {
        return Optional.ofNullable(PLANS.get(player.getUUID()));
    }

    public static void clearPlayer(ServerPlayer player) {
        clearInMemory(player.getUUID());
    }

    public static void clearSelection(ServerPlayer player) {
        SELECTIONS.put(player.getUUID(), Selection.empty(player.getUUID()).withShape(shape(player)));
        ADVANCED_POINTS.remove(player.getUUID());
        PENDING_PASTE_ORIGINS.remove(player.getUUID());
        BuildOperationEngine.clearPendingOperation(player);
        sync(player);
        player.displayClientMessage(Component.translatable("buildtools.message.selection_cleared"), true);
    }

    public static Optional<BlockPos> pendingPasteOrigin(ServerPlayer player) {
        return Optional.ofNullable(PENDING_PASTE_ORIGINS.get(player.getUUID()));
    }

    public static void setPendingPastePreview(ServerPlayer player, BlockPos origin, List<BlockPos> positions) {
        PENDING_PASTE_ORIGINS.put(player.getUUID(), origin.immutable());
        if (positions.size() > BuildToolsNetworking.MAX_PREVIEW_POSITIONS) {
            positions = positions.subList(0, BuildToolsNetworking.MAX_PREVIEW_POSITIONS);
        }
        PacketDistributor.sendToPlayer(player, new PreviewPayload(positions, true));
        persist(player);
        player.displayClientMessage(Component.translatable("buildtools.message.paste_preview", positions.size()), true);
    }

    public static void clearPendingPaste(ServerPlayer player) {
        PENDING_PASTE_ORIGINS.remove(player.getUUID());
        BuildOperationEngine.clearPendingOperation(player);
        persist(player);
        sendPreview(player);
    }

    public static void sync(ServerPlayer player) {
        Selection selection = selection(player);
        PacketDistributor.sendToPlayer(player, new SelectionSyncPayload(
                player.getUUID(),
                false,
                false,
                selection.dimension() == null ? "" : selection.dimension().location().toString(),
                selection.firstOptional(),
                selection.secondOptional(),
                shape(player),
                selection.points(),
                List.of(),
                false));
        sendPreview(player);
        syncSharedSelectionsTo(player);
        syncSharedSelectionFrom(player);
    }

    public static void nudgeSelection(ServerPlayer player, Direction direction) {
        Selection selection = selection(player);
        if (!selection.isComplete() || selection.dimension() == null) {
            player.displayClientMessage(Component.translatable("buildtools.error.incomplete_selection"), false);
            return;
        }
        BlockPos offset = BlockPos.ZERO.offset(direction.getNormal());
        SELECTIONS.put(player.getUUID(), new Selection(
                player.getUUID(),
                selection.dimension(),
                selection.first().offset(offset),
                selection.second().offset(offset),
                shape(player)));
        BuildOperationEngine.clearPendingOperation(player);
        sync(player);
        player.displayClientMessage(Component.translatable("buildtools.message.selection_nudged", direction.getName()), true);
    }

    public static void resizeSelection(ServerPlayer player, Direction direction, int amount) {
        Selection selection = selection(player);
        if (!selection.isComplete() || selection.dimension() == null) {
            player.displayClientMessage(Component.translatable("buildtools.error.incomplete_selection"), false);
            return;
        }
        int minX = Math.min(selection.first().getX(), selection.second().getX());
        int minY = Math.min(selection.first().getY(), selection.second().getY());
        int minZ = Math.min(selection.first().getZ(), selection.second().getZ());
        int maxX = Math.max(selection.first().getX(), selection.second().getX());
        int maxY = Math.max(selection.first().getY(), selection.second().getY());
        int maxZ = Math.max(selection.first().getZ(), selection.second().getZ());

        int delta = amount >= 0 ? 1 : -1;
        switch (direction) {
            case EAST -> maxX += delta;
            case WEST -> minX -= delta;
            case UP -> maxY += delta;
            case DOWN -> minY -= delta;
            case SOUTH -> maxZ += delta;
            case NORTH -> minZ -= delta;
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            player.displayClientMessage(Component.translatable("buildtools.error.selection_too_small"), false);
            return;
        }

        SELECTIONS.put(player.getUUID(), new Selection(
                player.getUUID(),
                selection.dimension(),
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ),
                selection.shape()));
        BuildOperationEngine.clearPendingOperation(player);
        sync(player);
        player.displayClientMessage(Component.translatable(amount >= 0 ? "buildtools.message.selection_expanded" : "buildtools.message.selection_shrunk", direction.getName()), true);
    }

    public static void savePreset(ServerPlayer player) {
        Selection selection = selection(player);
        if (!selection.isComplete()) {
            player.displayClientMessage(Component.translatable("buildtools.error.incomplete_selection"), false);
            return;
        }
        PRESETS.put(player.getUUID(), new SelectionPreset(selection.second().subtract(selection.first()), shape(player)));
        persist(player);
        player.displayClientMessage(Component.translatable("buildtools.message.preset_saved"), true);
    }

    public static void loadPreset(ServerPlayer player) {
        SelectionPreset preset = PRESETS.get(player.getUUID());
        if (preset == null) {
            player.displayClientMessage(Component.translatable("buildtools.error.no_preset"), false);
            return;
        }
        BlockPos first = player.blockPosition();
        SELECTIONS.put(player.getUUID(), new Selection(
                player.getUUID(),
                player.level().dimension(),
                first,
                first.offset(preset.offset()),
                preset.shape()));
        sync(player);
        player.displayClientMessage(Component.translatable("buildtools.message.preset_loaded"), true);
    }

    public static void setReplaceTarget(ServerPlayer player, BlockState state) {
        REPLACE_TARGETS.put(player.getUUID(), state);
        palettes(player).put(activeProfile(player), List.of(new PaletteEntry(state, PaletteEntry.DEFAULT_WEIGHT)));
        BuildOperationEngine.clearPendingOperation(player);
        player.displayClientMessage(Component.translatable("buildtools.message.replace_target", state.getBlock().getName()), true);
        sendPreview(player);
    }

    public static void setReplaceTargets(ServerPlayer player, List<BlockState> states) {
        if (states.isEmpty()) {
            palettes(player).remove(activeProfile(player));
            player.displayClientMessage(Component.translatable("buildtools.message.replace_targets_cleared"), true);
        } else {
            palettes(player).put(activeProfile(player), states.stream()
                    .map(state -> new PaletteEntry(state, PaletteEntry.DEFAULT_WEIGHT))
                    .toList());
            REPLACE_TARGETS.put(player.getUUID(), states.get(0));
            player.displayClientMessage(Component.translatable("buildtools.message.replace_targets", states.size()), true);
        }
        BuildOperationEngine.clearPendingOperation(player);
        sendPreview(player);
    }

    public static BlockState replaceTarget(ServerPlayer player) {
        return replaceTargets(player).stream().findFirst()
                .orElseGet(() -> Optional.ofNullable(REPLACE_TARGETS.get(player.getUUID()))
                .orElseGet(() -> selection(player).firstOptional()
                        .map(pos -> player.level().getBlockState(pos))
                        .orElse(player.level().getBlockState(player.blockPosition()))));
    }

    public static List<BlockState> replaceTargets(ServerPlayer player) {
        return paletteEntries(player).stream().map(PaletteEntry::state).toList();
    }

    public static boolean matchesReplaceTargets(ServerPlayer player, BlockState state, BlockState fallback) {
        List<BlockState> states = replaceTargets(player);
        if (states.isEmpty()) {
            return state.is(fallback.getBlock());
        }
        for (BlockState target : states) {
            if (state.is(target.getBlock())) {
                return true;
            }
        }
        return false;
    }

    public static List<PaletteEntry> paletteEntries(ServerPlayer player) {
        return palettes(player).getOrDefault(activeProfile(player), List.of());
    }

    public static void setPaletteEntries(ServerPlayer player, List<PaletteEntry> entries) {
        if (entries.isEmpty()) {
            palettes(player).remove(activeProfile(player));
            player.displayClientMessage(Component.translatable("buildtools.message.palette_cleared"), true);
        } else {
            List<PaletteEntry> copy = List.copyOf(entries);
            palettes(player).put(activeProfile(player), copy);
            REPLACE_TARGETS.put(player.getUUID(), copy.getFirst().state());
            player.displayClientMessage(Component.translatable("buildtools.message.palette_saved", copy.size()), true);
        }
        BuildOperationEngine.clearPendingOperation(player);
        sendPreview(player);
    }

    public static void setPaletteMode(ServerPlayer player, PaletteMode mode) {
        paletteModes(player).put(activeProfile(player), mode);
        gradients(player).put(activeProfile(player), mode == PaletteMode.GRADIENT);
        BuildOperationEngine.clearPendingOperation(player);
        player.displayClientMessage(Component.translatable("buildtools.message.palette_mode", mode.displayName()), true);
        sendPreview(player);
    }

    public static void cycleBrushMode(ServerPlayer player) {
        setBrushMode(player, brushMode(player).next());
    }

    public static void setBrushMode(ServerPlayer player, BrushMode mode) {
        brushModes(player).put(activeProfile(player), mode);
        BuildOperationEngine.clearPendingOperation(player);
        player.displayClientMessage(Component.translatable("buildtools.message.brush_mode", mode.displayName()), true);
        sendPreview(player);
    }

    public static void changeBrushRadius(ServerPlayer player, int delta) {
        int radius = Math.max(MIN_BRUSH_RADIUS, Math.min(MAX_BRUSH_RADIUS, brushRadius(player) + delta));
        brushRadii(player).put(activeProfile(player), radius);
        BuildOperationEngine.clearPendingOperation(player);
        player.displayClientMessage(Component.translatable("buildtools.message.brush_radius", radius), true);
        sendPreview(player);
    }

    public static void toggleGradient(ServerPlayer player) {
        setPaletteMode(player, paletteMode(player) == PaletteMode.GRADIENT ? PaletteMode.SINGLE : PaletteMode.GRADIENT);
    }

    public static void toggleRandomPattern(ServerPlayer player) {
        setPaletteMode(player, paletteMode(player) == PaletteMode.RANDOM ? PaletteMode.SINGLE : PaletteMode.RANDOM);
    }

    public static void cycleGradientDirection(ServerPlayer player) {
        GradientDirection next = gradientDirection(player).next();
        gradientDirections(player).put(activeProfile(player), next);
        BuildOperationEngine.clearPendingOperation(player);
        player.displayClientMessage(Component.translatable("buildtools.message.gradient_direction", next.displayName()), true);
        sendPreview(player);
    }

    public static void sendPreview(ServerPlayer player) {
        if (PENDING_PASTE_ORIGINS.containsKey(player.getUUID())) {
            return;
        }
        Selection selection = selection(player);
        List<BlockPos> preview = selection.dimension() != null
                && selection.dimension().equals(player.level().dimension())
                ? filteredPreview(player, selection)
                : List.of();
        if (preview.size() > BuildToolsNetworking.MAX_PREVIEW_POSITIONS) {
            preview = preview.subList(0, BuildToolsNetworking.MAX_PREVIEW_POSITIONS);
        }
        persist(player);
        PacketDistributor.sendToPlayer(player, new PreviewPayload(preview, false));
    }

    private static void syncSharedSelectionFrom(ServerPlayer owner) {
        if (owner.getServer() == null) {
            return;
        }
        for (ServerPlayer viewer : owner.getServer().getPlayerList().getPlayers()) {
            if (!viewer.getUUID().equals(owner.getUUID())) {
                sendSharedSelection(owner, viewer);
            }
        }
    }

    private static void removeSharedSelectionFrom(ServerPlayer owner) {
        if (owner.getServer() == null) {
            return;
        }
        SelectionSyncPayload payload = removeSharedSelectionPayload(owner.getUUID());
        for (ServerPlayer viewer : owner.getServer().getPlayerList().getPlayers()) {
            if (!viewer.getUUID().equals(owner.getUUID())) {
                PacketDistributor.sendToPlayer(viewer, payload);
            }
        }
    }

    private static void syncSharedSelectionsTo(ServerPlayer viewer) {
        if (viewer.getServer() == null) {
            return;
        }
        for (ServerPlayer owner : viewer.getServer().getPlayerList().getPlayers()) {
            if (!owner.getUUID().equals(viewer.getUUID())) {
                sendSharedSelection(owner, viewer);
            }
        }
    }

    private static void sendSharedSelection(ServerPlayer owner, ServerPlayer viewer) {
        Selection selection = selection(owner);
        if (!selectionVisibleToOthers(owner)
                || selection.dimension() == null
                || !selection.dimension().equals(viewer.level().dimension())) {
            PacketDistributor.sendToPlayer(viewer, removeSharedSelectionPayload(owner.getUUID()));
            return;
        }
        List<BlockPos> preview = selection.isComplete()
                && selection.dimension().equals(owner.level().dimension())
                ? filteredPreview(owner, selection)
                : List.of();
        if (preview.size() > BuildToolsNetworking.MAX_PREVIEW_POSITIONS) {
            preview = preview.subList(0, BuildToolsNetworking.MAX_PREVIEW_POSITIONS);
        }
        PacketDistributor.sendToPlayer(viewer, new SelectionSyncPayload(
                owner.getUUID(),
                true,
                false,
                selection.dimension().location().toString(),
                selection.firstOptional(),
                selection.secondOptional(),
                shape(owner),
                selection.points(),
                preview,
                false));
    }

    private static SelectionSyncPayload removeSharedSelectionPayload(UUID owner) {
        return new SelectionSyncPayload(
                owner,
                true,
                true,
                "",
                Optional.empty(),
                Optional.empty(),
                SelectionShape.CUBOID,
                List.of(),
                List.of(),
                false);
    }

    public static void addAdvancedPoint(ServerPlayer player, BlockPos pos) {
        List<BlockPos> points = new ArrayList<>(ADVANCED_POINTS.getOrDefault(player.getUUID(), List.of()));
        BlockPos point = pos.immutable();
        if (points.contains(point)) {
            return;
        }
        points.add(point);
        setAdvancedPoints(player, points);
        player.displayClientMessage(Component.translatable("buildtools.message.advanced_point", points.size(), format(point)), true);
    }

    public static boolean beginAdvancedSelectionAction(ServerPlayer player) {
        if (player.getCooldowns().isOnCooldown(ModItems.ADVANCED_SELECTION_STAFF.get())) {
            return false;
        }
        player.getCooldowns().addCooldown(ModItems.ADVANCED_SELECTION_STAFF.get(), ADVANCED_SELECTION_ACTION_COOLDOWN);
        return true;
    }

    public static void addAdvancedPointAtLook(ServerPlayer player) {
        advancedSelectionTarget(player).ifPresentOrElse(
                pos -> addAdvancedPoint(player, pos),
                () -> player.displayClientMessage(Component.translatable("buildtools.error.no_target"), false));
    }

    public static void addAdvancedPointAdjacentAtLook(ServerPlayer player) {
        advancedSelectionHit(player).ifPresentOrElse(
                hit -> addAdvancedPoint(player, hit.getBlockPos().relative(hit.getDirection())),
                () -> player.displayClientMessage(Component.translatable("buildtools.error.no_target"), false));
    }

    public static void removeAdvancedPointAtLook(ServerPlayer player) {
        advancedSelectionTarget(player).ifPresentOrElse(
                pos -> removeAdvancedPoint(player, pos),
                () -> player.displayClientMessage(Component.translatable("buildtools.error.no_target"), false));
    }

    public static boolean moveAdvancedPointAtLook(ServerPlayer player, int delta) {
        Optional<BlockPos> target = advancedSelectionTarget(player);
        return target.isPresent() && moveAdvancedPoint(player, target.get(), delta);
    }

    public static void removeAdvancedPoint(ServerPlayer player, BlockPos target) {
        List<BlockPos> points = new ArrayList<>(ADVANCED_POINTS.getOrDefault(player.getUUID(), List.of()));
        int index = nearestAdvancedPoint(points, target);
        if (index < 0) {
            player.displayClientMessage(Component.translatable("buildtools.error.no_advanced_point"), false);
            return;
        }
        BlockPos removed = points.remove(index);
        setAdvancedPoints(player, points);
        player.displayClientMessage(Component.translatable("buildtools.message.advanced_point_removed", index + 1, format(removed)), true);
    }

    public static boolean moveAdvancedPoint(ServerPlayer player, BlockPos target, int delta) {
        List<BlockPos> points = new ArrayList<>(ADVANCED_POINTS.getOrDefault(player.getUUID(), List.of()));
        int index = nearestAdvancedPoint(points, target);
        if (index < 0) {
            return false;
        }
        int newIndex = Math.max(0, Math.min(points.size() - 1, index + delta));
        if (newIndex == index) {
            player.displayClientMessage(Component.translatable("buildtools.message.advanced_point_order_unchanged", index + 1), true);
            return true;
        }
        BlockPos point = points.remove(index);
        points.add(newIndex, point);
        setAdvancedPoints(player, points);
        player.displayClientMessage(Component.translatable("buildtools.message.advanced_point_moved", format(point), newIndex + 1), true);
        return true;
    }

    public static void clearAdvancedPoints(ServerPlayer player) {
        setAdvancedPoints(player, List.of());
        player.displayClientMessage(Component.translatable("buildtools.message.advanced_points_cleared"), true);
    }

    public static int advancedPointCount(ServerPlayer player) {
        return ADVANCED_POINTS.getOrDefault(player.getUUID(), List.of()).size();
    }

    public static Optional<BlockPos> advancedSelectionTarget(ServerPlayer player) {
        return advancedSelectionHit(player).map(BlockHitResult::getBlockPos);
    }

    private static Optional<BlockHitResult> advancedSelectionHit(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(ADVANCED_SELECTION_DISTANCE));
        BlockHitResult hit = player.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));
        return hit.getType() == HitResult.Type.BLOCK ? Optional.of(hit) : Optional.empty();
    }

    public static void rotateSelection(ServerPlayer player) {
        Selection selection = selection(player);
        if (!selection.isComplete() || selection.dimension() == null) {
            player.displayClientMessage(Component.translatable("buildtools.error.incomplete_selection"), false);
            return;
        }
        BlockPos offset = selection.second().subtract(selection.first());
        BlockPos rotated = new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
        SELECTIONS.put(player.getUUID(), new Selection(player.getUUID(), selection.dimension(), selection.first(), selection.first().offset(rotated), shape(player)));
        BuildOperationEngine.clearPendingOperation(player);
        sync(player);
        player.displayClientMessage(Component.translatable("buildtools.message.selection_rotated"), true);
    }

    public static void sendPlanPreview(ServerPlayer player) {
        BuildPlan plan = PLANS.get(player.getUUID());
        if (plan == null || !plan.dimension().equals(player.level().dimension())) {
            sendPreview(player);
            return;
        }
        List<BlockPos> preview = plan.entries().stream().map(BuildPlan.Entry::pos).toList();
        if (preview.size() > BuildToolsNetworking.MAX_PREVIEW_POSITIONS) {
            preview = preview.subList(0, BuildToolsNetworking.MAX_PREVIEW_POSITIONS);
        }
        PacketDistributor.sendToPlayer(player, new PreviewPayload(preview, true));
    }

    private static List<BlockPos> filteredPreview(ServerPlayer player, Selection selection) {
        List<BlockPos> generated = ShapeGenerator.generate(selection);
        BuildMode mode = mode(player);
        if (generated.isEmpty()) {
            return generated;
        }
        if (mode == BuildMode.OVERWRITE) {
            return generated;
        }

        List<BlockPos> filtered = new ArrayList<>();
        BlockState replaceMatch = replaceTarget(player);
        for (BlockPos pos : generated) {
            BlockState state = player.level().getBlockState(pos);
            if (mode == BuildMode.FILL && state.canBeReplaced()) {
                filtered.add(pos);
            } else if (mode == BuildMode.REPLACE && matchesReplaceTargets(player, state, replaceMatch)) {
                filtered.add(pos);
            }
        }
        return filtered;
    }

    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static void pushLimited(Deque<UndoSnapshot> history, UndoSnapshot snapshot) {
        history.addFirst(snapshot);
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    private static void setAdvancedPoints(ServerPlayer player, List<BlockPos> points) {
        if (points.isEmpty()) {
            ADVANCED_POINTS.remove(player.getUUID());
            SELECTIONS.put(player.getUUID(), Selection.empty(player.getUUID()).withShape(shape(player)));
            BuildOperationEngine.clearPendingOperation(player);
            sync(player);
            return;
        }
        List<BlockPos> immutablePoints = points.stream().map(BlockPos::immutable).toList();
        ADVANCED_POINTS.put(player.getUUID(), List.copyOf(immutablePoints));
        applyAdvancedPoints(player, immutablePoints);
    }

    private static void applyAdvancedPoints(ServerPlayer player, List<BlockPos> points) {
        if (points.isEmpty()) {
            return;
        }
        int minX = points.stream().mapToInt(BlockPos::getX).min().orElse(player.blockPosition().getX());
        int minY = points.stream().mapToInt(BlockPos::getY).min().orElse(player.blockPosition().getY());
        int minZ = points.stream().mapToInt(BlockPos::getZ).min().orElse(player.blockPosition().getZ());
        int maxX = points.stream().mapToInt(BlockPos::getX).max().orElse(player.blockPosition().getX());
        int maxY = points.stream().mapToInt(BlockPos::getY).max().orElse(player.blockPosition().getY());
        int maxZ = points.stream().mapToInt(BlockPos::getZ).max().orElse(player.blockPosition().getZ());
        SELECTIONS.put(player.getUUID(), new Selection(
                player.getUUID(),
                player.level().dimension(),
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ),
                shape(player),
                points));
        BuildOperationEngine.clearPendingOperation(player);
        sync(player);
    }

    private static int nearestAdvancedPoint(List<BlockPos> points, BlockPos target) {
        int bestIndex = -1;
        double bestDistance = ADVANCED_POINT_PICK_DISTANCE_SQR;
        for (int i = 0; i < points.size(); i++) {
            BlockPos point = points.get(i);
            double dx = point.getX() - target.getX();
            double dy = point.getY() - target.getY();
            double dz = point.getZ() - target.getZ();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static SelectionShape shape(ServerPlayer player) {
        return shapes(player).getOrDefault(activeProfile(player), selection(player).shape());
    }

    private static ToolProfile activeProfile(ServerPlayer player) {
        if (ToolProfile.isBuildTool(player.getMainHandItem())) {
            return ToolProfile.from(player.getMainHandItem());
        }
        if (ToolProfile.isBuildTool(player.getOffhandItem())) {
            return ToolProfile.from(player.getOffhandItem());
        }
        return ToolProfile.BUILDER;
    }

    private static EnumMap<ToolProfile, BuildMode> modes(ServerPlayer player) {
        return MODES.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(ToolProfile.class));
    }

    private static EnumMap<ToolProfile, SelectionShape> shapes(ServerPlayer player) {
        return SHAPES.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(ToolProfile.class));
    }

    private static EnumMap<ToolProfile, BrushMode> brushModes(ServerPlayer player) {
        return BRUSH_MODES.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(ToolProfile.class));
    }

    private static EnumMap<ToolProfile, Integer> brushRadii(ServerPlayer player) {
        return BRUSH_RADII.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(ToolProfile.class));
    }

    private static EnumMap<ToolProfile, Boolean> gradients(ServerPlayer player) {
        return GRADIENTS.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(ToolProfile.class));
    }

    private static EnumMap<ToolProfile, List<PaletteEntry>> palettes(ServerPlayer player) {
        return PALETTES.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(ToolProfile.class));
    }

    private static EnumMap<ToolProfile, PaletteMode> paletteModes(ServerPlayer player) {
        return PALETTE_MODES.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(ToolProfile.class));
    }

    private static EnumMap<ToolProfile, GradientDirection> gradientDirections(ServerPlayer player) {
        return GRADIENT_DIRECTIONS.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(ToolProfile.class));
    }

    private static void persist(ServerPlayer player) {
        BuildToolsPlayerStateData.get(player).putPlayer(player.getUUID(), writeState(player));
    }

    private static CompoundTag writeState(ServerPlayer player) {
        UUID uuid = player.getUUID();
        CompoundTag tag = new CompoundTag();
        Selection selection = SELECTIONS.get(uuid);
        if (selection != null) {
            tag.put("selection", writeSelection(selection));
        }
        tag.put("modes", writeEnumMap(MODES.get(uuid)));
        tag.put("shapes", writeEnumMap(SHAPES.get(uuid)));
        tag.put("brushModes", writeEnumMap(BRUSH_MODES.get(uuid)));
        tag.put("brushRadii", writeIntMap(BRUSH_RADII.get(uuid)));
        tag.put("gradients", writeBooleanMap(GRADIENTS.get(uuid)));
        tag.put("paletteModes", writeEnumMap(PALETTE_MODES.get(uuid)));
        tag.put("gradientDirections", writeEnumMap(GRADIENT_DIRECTIONS.get(uuid)));
        tag.put("palettes", writePaletteMap(PALETTES.get(uuid)));
        tag.putBoolean("selectionVisibleToOthers", SHARED_SELECTION_VISIBILITY.getOrDefault(uuid, false));
        tag.put("undo", writeUndoSnapshots(UNDO.get(uuid)));
        tag.put("redo", writeUndoSnapshots(REDO.get(uuid)));
        if (BLUEPRINTS.containsKey(uuid)) {
            tag.put("blueprint", writeBlueprint(BLUEPRINTS.get(uuid)));
        }
        if (REPLACE_TARGETS.containsKey(uuid)) {
            tag.put("replaceTarget", NbtUtils.writeBlockState(REPLACE_TARGETS.get(uuid)));
        }
        if (PRESETS.containsKey(uuid)) {
            tag.put("preset", writePreset(PRESETS.get(uuid)));
        }
        if (PLANS.containsKey(uuid)) {
            tag.put("plan", writePlan(PLANS.get(uuid)));
        }
        List<BlockPos> points = ADVANCED_POINTS.get(uuid);
        if (points != null && !points.isEmpty()) {
            tag.put("advancedPoints", writePositions(points));
        }
        return tag;
    }

    private static void readState(ServerPlayer player, CompoundTag tag, HolderLookup.Provider registries) {
        UUID uuid = player.getUUID();
        if (tag.contains("selection", Tag.TAG_COMPOUND)) {
            SELECTIONS.put(uuid, readSelection(uuid, tag.getCompound("selection")));
        }
        readBuildModes(tag.getList("modes", Tag.TAG_COMPOUND)).ifPresent(map -> MODES.put(uuid, map));
        readShapes(tag.getList("shapes", Tag.TAG_COMPOUND)).ifPresent(map -> SHAPES.put(uuid, map));
        readBrushModes(tag.getList("brushModes", Tag.TAG_COMPOUND)).ifPresent(map -> BRUSH_MODES.put(uuid, map));
        readIntMap(tag.getList("brushRadii", Tag.TAG_COMPOUND)).ifPresent(map -> BRUSH_RADII.put(uuid, map));
        readBooleanMap(tag.getList("gradients", Tag.TAG_COMPOUND)).ifPresent(map -> GRADIENTS.put(uuid, map));
        readPaletteModes(tag.getList("paletteModes", Tag.TAG_COMPOUND)).ifPresent(map -> PALETTE_MODES.put(uuid, map));
        readGradientDirections(tag.getList("gradientDirections", Tag.TAG_COMPOUND)).ifPresent(map -> GRADIENT_DIRECTIONS.put(uuid, map));
        readPaletteMap(tag.getList("palettes", Tag.TAG_COMPOUND), registries).ifPresent(map -> PALETTES.put(uuid, map));
        if (tag.contains("selectionVisibleToOthers", Tag.TAG_BYTE)) {
            SHARED_SELECTION_VISIBILITY.put(uuid, tag.getBoolean("selectionVisibleToOthers"));
        }
        Deque<UndoSnapshot> undo = readUndoSnapshots(tag.getList("undo", Tag.TAG_COMPOUND), registries);
        if (!undo.isEmpty()) {
            UNDO.put(uuid, undo);
        }
        Deque<UndoSnapshot> redo = readUndoSnapshots(tag.getList("redo", Tag.TAG_COMPOUND), registries);
        if (!redo.isEmpty()) {
            REDO.put(uuid, redo);
        }
        if (tag.contains("blueprint", Tag.TAG_COMPOUND)) {
            BLUEPRINTS.put(uuid, readBlueprint(tag.getCompound("blueprint"), registries));
        }
        if (tag.contains("replaceTarget", Tag.TAG_COMPOUND)) {
            REPLACE_TARGETS.put(uuid, readBlockState(tag.getCompound("replaceTarget"), registries));
        }
        if (tag.contains("preset", Tag.TAG_COMPOUND)) {
            PRESETS.put(uuid, readPreset(tag.getCompound("preset")));
        }
        if (tag.contains("plan", Tag.TAG_COMPOUND)) {
            PLANS.put(uuid, readPlan(tag.getCompound("plan"), registries));
        }
        List<BlockPos> points = readPositions(tag.getList("advancedPoints", Tag.TAG_INT_ARRAY));
        if (!points.isEmpty()) {
            ADVANCED_POINTS.put(uuid, points);
            applyAdvancedPoints(player, points);
        }
    }

    private static void clearInMemory(UUID uuid) {
        SELECTIONS.remove(uuid);
        MODES.remove(uuid);
        SHAPES.remove(uuid);
        UNDO.remove(uuid);
        REDO.remove(uuid);
        BLUEPRINTS.remove(uuid);
        PENDING_PASTE_ORIGINS.remove(uuid);
        REPLACE_TARGETS.remove(uuid);
        PALETTES.remove(uuid);
        PRESETS.remove(uuid);
        PLANS.remove(uuid);
        BRUSH_MODES.remove(uuid);
        BRUSH_RADII.remove(uuid);
        GRADIENTS.remove(uuid);
        PALETTE_MODES.remove(uuid);
        GRADIENT_DIRECTIONS.remove(uuid);
        ADVANCED_POINTS.remove(uuid);
        SHARED_SELECTION_VISIBILITY.remove(uuid);
    }

    private static CompoundTag writeSelection(Selection selection) {
        CompoundTag tag = new CompoundTag();
        if (selection.dimension() != null) {
            tag.putString("dimension", selection.dimension().location().toString());
        }
        selection.firstOptional().ifPresent(pos -> tag.put("first", NbtUtils.writeBlockPos(pos)));
        selection.secondOptional().ifPresent(pos -> tag.put("second", NbtUtils.writeBlockPos(pos)));
        tag.putString("shape", selection.shape().name());
        tag.put("points", writePositions(selection.points()));
        return tag;
    }

    private static Selection readSelection(UUID owner, CompoundTag tag) {
        ResourceKey<Level> dimension = readDimension(tag.getString("dimension")).orElse(null);
        BlockPos first = NbtUtils.readBlockPos(tag, "first").orElse(null);
        BlockPos second = NbtUtils.readBlockPos(tag, "second").orElse(null);
        SelectionShape shape = readEnum(SelectionShape.class, tag.getString("shape"), SelectionShape.CUBOID);
        List<BlockPos> points = readPositions(tag.getList("points", Tag.TAG_INT_ARRAY));
        return new Selection(owner, dimension, first, second, shape, points);
    }

    private static ListTag writePositions(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(NbtUtils.writeBlockPos(pos));
        }
        return list;
    }

    private static List<BlockPos> readPositions(ListTag list) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            int[] raw = list.getIntArray(i);
            if (raw.length == 3) {
                positions.add(new BlockPos(raw[0], raw[1], raw[2]).immutable());
            }
        }
        return List.copyOf(positions);
    }

    private static <E extends Enum<E>> ListTag writeEnumMap(EnumMap<ToolProfile, E> map) {
        ListTag list = new ListTag();
        if (map == null) {
            return list;
        }
        for (Map.Entry<ToolProfile, E> entry : map.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("profile", entry.getKey().name());
            tag.putString("value", entry.getValue().name());
            list.add(tag);
        }
        return list;
    }

    private static ListTag writeIntMap(EnumMap<ToolProfile, Integer> map) {
        ListTag list = new ListTag();
        if (map == null) {
            return list;
        }
        for (Map.Entry<ToolProfile, Integer> entry : map.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("profile", entry.getKey().name());
            tag.putInt("value", entry.getValue());
            list.add(tag);
        }
        return list;
    }

    private static ListTag writeBooleanMap(EnumMap<ToolProfile, Boolean> map) {
        ListTag list = new ListTag();
        if (map == null) {
            return list;
        }
        for (Map.Entry<ToolProfile, Boolean> entry : map.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("profile", entry.getKey().name());
            tag.putBoolean("value", entry.getValue());
            list.add(tag);
        }
        return list;
    }

    private static Optional<EnumMap<ToolProfile, BuildMode>> readBuildModes(ListTag list) {
        return readEnumMap(list, BuildMode.class);
    }

    private static Optional<EnumMap<ToolProfile, SelectionShape>> readShapes(ListTag list) {
        return readEnumMap(list, SelectionShape.class);
    }

    private static Optional<EnumMap<ToolProfile, BrushMode>> readBrushModes(ListTag list) {
        return readEnumMap(list, BrushMode.class);
    }

    private static Optional<EnumMap<ToolProfile, PaletteMode>> readPaletteModes(ListTag list) {
        return readEnumMap(list, PaletteMode.class);
    }

    private static Optional<EnumMap<ToolProfile, GradientDirection>> readGradientDirections(ListTag list) {
        return readEnumMap(list, GradientDirection.class);
    }

    private static <E extends Enum<E>> Optional<EnumMap<ToolProfile, E>> readEnumMap(ListTag list, Class<E> enumClass) {
        EnumMap<ToolProfile, E> map = new EnumMap<>(ToolProfile.class);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            readProfile(tag.getString("profile")).ifPresent(profile ->
                    map.put(profile, readEnum(enumClass, tag.getString("value"), null)));
        }
        map.values().removeIf(java.util.Objects::isNull);
        return map.isEmpty() ? Optional.empty() : Optional.of(map);
    }

    private static Optional<EnumMap<ToolProfile, Integer>> readIntMap(ListTag list) {
        EnumMap<ToolProfile, Integer> map = new EnumMap<>(ToolProfile.class);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            readProfile(tag.getString("profile")).ifPresent(profile -> map.put(profile, tag.getInt("value")));
        }
        return map.isEmpty() ? Optional.empty() : Optional.of(map);
    }

    private static Optional<EnumMap<ToolProfile, Boolean>> readBooleanMap(ListTag list) {
        EnumMap<ToolProfile, Boolean> map = new EnumMap<>(ToolProfile.class);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            readProfile(tag.getString("profile")).ifPresent(profile -> map.put(profile, tag.getBoolean("value")));
        }
        return map.isEmpty() ? Optional.empty() : Optional.of(map);
    }

    private static ListTag writePaletteMap(EnumMap<ToolProfile, List<PaletteEntry>> map) {
        ListTag list = new ListTag();
        if (map == null) {
            return list;
        }
        for (Map.Entry<ToolProfile, List<PaletteEntry>> entry : map.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("profile", entry.getKey().name());
            ListTag entries = new ListTag();
            for (PaletteEntry paletteEntry : entry.getValue()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.put("state", NbtUtils.writeBlockState(paletteEntry.state()));
                entryTag.putInt("weight", paletteEntry.weight());
                entries.add(entryTag);
            }
            tag.put("entries", entries);
            list.add(tag);
        }
        return list;
    }

    private static Optional<EnumMap<ToolProfile, List<PaletteEntry>>> readPaletteMap(ListTag list, HolderLookup.Provider registries) {
        EnumMap<ToolProfile, List<PaletteEntry>> map = new EnumMap<>(ToolProfile.class);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            Optional<ToolProfile> profile = readProfile(tag.getString("profile"));
            if (profile.isEmpty()) {
                continue;
            }
            List<PaletteEntry> entries = new ArrayList<>();
            ListTag rawEntries = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < rawEntries.size(); j++) {
                CompoundTag entryTag = rawEntries.getCompound(j);
                BlockState state = readBlockState(entryTag.getCompound("state"), registries);
                if (!state.isAir()) {
                    entries.add(new PaletteEntry(state, entryTag.getInt("weight")));
                }
            }
            if (!entries.isEmpty()) {
                map.put(profile.get(), List.copyOf(entries));
            }
        }
        return map.isEmpty() ? Optional.empty() : Optional.of(map);
    }

    private static CompoundTag writeBlueprint(Blueprint blueprint) {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        for (Blueprint.Entry entry : blueprint.entries()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("offset", NbtUtils.writeBlockPos(entry.offset()));
            entryTag.put("state", NbtUtils.writeBlockState(entry.state()));
            if (entry.blockEntity() != null) {
                entryTag.put("blockEntity", entry.blockEntity().copy());
            }
            entries.add(entryTag);
        }
        tag.put("entries", entries);
        tag.put("entities", writeCapturedEntities(blueprint.entities()));
        return tag;
    }

    private static Blueprint readBlueprint(CompoundTag tag, HolderLookup.Provider registries) {
        List<Blueprint.Entry> entries = new ArrayList<>();
        ListTag rawEntries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < rawEntries.size(); i++) {
            CompoundTag entryTag = rawEntries.getCompound(i);
            Optional<BlockPos> offset = NbtUtils.readBlockPos(entryTag, "offset");
            BlockState state = readBlockState(entryTag.getCompound("state"), registries);
            if (offset.isPresent() && !state.isAir()) {
                CompoundTag blockEntity = entryTag.contains("blockEntity", Tag.TAG_COMPOUND)
                        ? entryTag.getCompound("blockEntity").copy()
                        : null;
                entries.add(new Blueprint.Entry(offset.get(), state, blockEntity));
            }
        }
        return new Blueprint(List.copyOf(entries), readCapturedEntities(tag.getList("entities", Tag.TAG_COMPOUND)));
    }

    private static CompoundTag writePlan(BuildPlan plan) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", plan.dimension().location().toString());
        ListTag entries = new ListTag();
        for (BuildPlan.Entry entry : plan.entries()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("pos", NbtUtils.writeBlockPos(entry.pos()));
            entryTag.put("state", NbtUtils.writeBlockState(entry.state()));
            if (entry.blockEntity() != null) {
                entryTag.put("blockEntity", entry.blockEntity().copy());
            }
            entries.add(entryTag);
        }
        tag.put("entries", entries);
        return tag;
    }

    private static BuildPlan readPlan(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceKey<Level> dimension = readDimension(tag.getString("dimension")).orElse(Level.OVERWORLD);
        List<BuildPlan.Entry> entries = new ArrayList<>();
        ListTag rawEntries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < rawEntries.size(); i++) {
            CompoundTag entryTag = rawEntries.getCompound(i);
            Optional<BlockPos> pos = NbtUtils.readBlockPos(entryTag, "pos");
            BlockState state = readBlockState(entryTag.getCompound("state"), registries);
            if (pos.isPresent() && !state.isAir()) {
                CompoundTag blockEntity = entryTag.contains("blockEntity", Tag.TAG_COMPOUND)
                        ? entryTag.getCompound("blockEntity").copy()
                        : null;
                entries.add(new BuildPlan.Entry(pos.get(), state, blockEntity));
            }
        }
        return new BuildPlan(dimension, List.copyOf(entries));
    }

    private static CompoundTag writePreset(SelectionPreset preset) {
        CompoundTag tag = new CompoundTag();
        tag.put("offset", NbtUtils.writeBlockPos(preset.offset()));
        tag.putString("shape", preset.shape().name());
        return tag;
    }

    private static SelectionPreset readPreset(CompoundTag tag) {
        return new SelectionPreset(
                NbtUtils.readBlockPos(tag, "offset").orElse(BlockPos.ZERO),
                readEnum(SelectionShape.class, tag.getString("shape"), SelectionShape.CUBOID));
    }

    private static ListTag writeUndoSnapshots(Deque<UndoSnapshot> snapshots) {
        ListTag list = new ListTag();
        if (snapshots == null) {
            return list;
        }
        for (UndoSnapshot snapshot : snapshots) {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", snapshot.dimension().location().toString());
            ListTag entries = new ListTag();
            for (UndoSnapshot.Entry entry : snapshot.entries()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.put("pos", NbtUtils.writeBlockPos(entry.pos()));
                entryTag.put("previousState", NbtUtils.writeBlockState(entry.previousState()));
                if (entry.previousBlockEntity() != null) {
                    entryTag.put("previousBlockEntity", entry.previousBlockEntity().copy());
                }
                entryTag.put("redoneState", NbtUtils.writeBlockState(entry.redoneState()));
                if (entry.redoneBlockEntity() != null) {
                    entryTag.put("redoneBlockEntity", entry.redoneBlockEntity().copy());
                }
                entryTag.putBoolean("mayRestorePrevious", entry.mayRestorePrevious());
                entries.add(entryTag);
            }
            tag.put("entries", entries);
            tag.put("refund", writeItemStacks(snapshot.refund()));
            tag.put("producedDrops", writeItemStacks(snapshot.producedDrops()));
            tag.put("removedEntities", writeCapturedEntities(snapshot.removedEntities()));
            tag.put("addedEntities", writeCapturedEntities(snapshot.addedEntities()));
            list.add(tag);
        }
        return list;
    }

    private static Deque<UndoSnapshot> readUndoSnapshots(ListTag list, HolderLookup.Provider registries) {
        Deque<UndoSnapshot> snapshots = new ArrayDeque<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ResourceKey<Level> dimension = readDimension(tag.getString("dimension")).orElse(Level.OVERWORLD);
            List<UndoSnapshot.Entry> entries = new ArrayList<>();
            ListTag rawEntries = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < rawEntries.size(); j++) {
                CompoundTag entryTag = rawEntries.getCompound(j);
                Optional<BlockPos> pos = NbtUtils.readBlockPos(entryTag, "pos");
                if (pos.isEmpty()) {
                    continue;
                }
                CompoundTag previousBlockEntity = entryTag.contains("previousBlockEntity", Tag.TAG_COMPOUND)
                        ? entryTag.getCompound("previousBlockEntity").copy()
                        : null;
                CompoundTag redoneBlockEntity = entryTag.contains("redoneBlockEntity", Tag.TAG_COMPOUND)
                        ? entryTag.getCompound("redoneBlockEntity").copy()
                        : null;
                entries.add(new UndoSnapshot.Entry(
                        pos.get(),
                        readBlockState(entryTag.getCompound("previousState"), registries),
                        previousBlockEntity,
                        readBlockState(entryTag.getCompound("redoneState"), registries),
                        redoneBlockEntity,
                        entryTag.getBoolean("mayRestorePrevious")));
            }
            if (!entries.isEmpty()) {
                snapshots.addLast(new UndoSnapshot(
                        dimension,
                        List.copyOf(entries),
                        readItemStacks(tag.getList("refund", Tag.TAG_COMPOUND), registries),
                        readItemStacks(tag.getList("producedDrops", Tag.TAG_COMPOUND), registries),
                        readCapturedEntities(tag.getList("removedEntities", Tag.TAG_COMPOUND)),
                        readCapturedEntities(tag.getList("addedEntities", Tag.TAG_COMPOUND))));
            }
        }
        return snapshots;
    }

    private static ListTag writeItemStacks(List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                list.add(stack.saveOptional(currentRegistryAccess()));
            }
        }
        return list;
    }

    private static List<ItemStack> readItemStacks(ListTag list, HolderLookup.Provider registries) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (tag.contains("item", Tag.TAG_STRING)) {
                Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(tag.getString("item"))).orElse(Items.AIR);
                if (item != Items.AIR) {
                    stacks.add(new ItemStack(item, tag.getInt("count")));
                }
                continue;
            }
            ItemStack stack = ItemStack.parseOptional(registries, tag);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    private static ListTag writeCapturedEntities(List<CapturedEntity> entities) {
        ListTag list = new ListTag();
        for (CapturedEntity entity : entities) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("offsetX", entity.offsetX());
            tag.putDouble("offsetY", entity.offsetY());
            tag.putDouble("offsetZ", entity.offsetZ());
            tag.put("entity", entity.tag().copy());
            list.add(tag);
        }
        return list;
    }

    private static List<CapturedEntity> readCapturedEntities(ListTag list) {
        List<CapturedEntity> entities = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (tag.contains("entity", Tag.TAG_COMPOUND)) {
                entities.add(new CapturedEntity(
                        tag.getDouble("offsetX"),
                        tag.getDouble("offsetY"),
                        tag.getDouble("offsetZ"),
                        tag.getCompound("entity")));
            }
        }
        return List.copyOf(entities);
    }

    private static HolderLookup.Provider currentRegistryAccess() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess();
    }

    private static ListTag writeRefund(Map<ItemStackKey, Integer> refund) {
        ListTag list = new ListTag();
        for (Map.Entry<ItemStackKey, Integer> entry : refund.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("item", entry.getKey().displayId());
            tag.putInt("count", entry.getValue());
            list.add(tag);
        }
        return list;
    }

    private static Map<ItemStackKey, Integer> readRefund(ListTag list) {
        Map<ItemStackKey, Integer> refund = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(tag.getString("item"))).orElse(Items.AIR);
            if (item != Items.AIR) {
                refund.put(new ItemStackKey(item), tag.getInt("count"));
            }
        }
        return Map.copyOf(refund);
    }

    private static Optional<ResourceKey<Level>> readDimension(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id)));
    }

    private static BlockState readBlockState(CompoundTag tag, HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        return NbtUtils.readBlockState(blocks, tag);
    }

    private static Optional<ToolProfile> readProfile(String value) {
        return Optional.ofNullable(readEnum(ToolProfile.class, value, null));
    }

    private static <E extends Enum<E>> E readEnum(Class<E> enumClass, String value, E fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static void transformBlueprint(ServerPlayer player, String messageKey, java.util.function.Function<BlockPos, BlockPos> transform) {
        Blueprint blueprint = BLUEPRINTS.get(player.getUUID());
        if (blueprint == null || blueprint.entries().isEmpty()) {
            player.displayClientMessage(Component.translatable("buildtools.error.no_blueprint"), false);
            return;
        }
        List<Blueprint.Entry> transformed = blueprint.entries().stream()
                .map(entry -> new Blueprint.Entry(transform.apply(entry.offset()), entry.state(), entry.blockEntity()))
                .toList();
        List<CapturedEntity> transformedEntities = blueprint.entities().stream()
                .map(entity -> {
                    BlockPos transformedOffset = transform.apply(new BlockPos(
                            (int) Math.round(entity.offsetX()),
                            (int) Math.round(entity.offsetY()),
                            (int) Math.round(entity.offsetZ())));
                    return entity.withOffset(transformedOffset.getX(), transformedOffset.getY(), transformedOffset.getZ());
                })
                .toList();
        BLUEPRINTS.put(player.getUUID(), new Blueprint(transformed, transformedEntities));
        PENDING_PASTE_ORIGINS.remove(player.getUUID());
        BuildOperationEngine.clearPendingOperation(player);
        player.displayClientMessage(Component.translatable(messageKey), true);
        sendPreview(player);
    }
}
