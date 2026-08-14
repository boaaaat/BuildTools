package com.abhil.buildtools.server;

import com.abhil.buildtools.config.BuildToolsConfig;
import com.abhil.buildtools.network.ToolStatusPayload;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.shape.BuildMode;
import com.abhil.buildtools.shape.CustomShapeMode;
import com.abhil.buildtools.shape.Selection;
import com.abhil.buildtools.shape.SelectionShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BuildToolActionbar {
    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final int MAX_CACHE_AGE_TICKS = 40;
    /** Status text is informational and must never monopolize a server tick. */
    private static final int MAX_STATUS_SCAN_POSITIONS = 8192;
    private static final Map<UUID, CachedStatus> STATUS_CACHE = new HashMap<>();

    private BuildToolActionbar() {
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.tickCount % UPDATE_INTERVAL_TICKS == 0) {
                show(player);
            }
        }
    }

    private static void show(ServerPlayer player) {
        ItemStack held = heldBuildTool(player);
        CachedStatus cached = STATUS_CACHE.get(player.getUUID());
        if (held.isEmpty()) {
            if (cached != null) {
                STATUS_CACHE.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, ToolStatusPayload.hidden());
                BuildToolsState.clearMeasurementOverlay(player);
            }
            return;
        }
        if (cached != null
                && cached.item() == held.getItem()
                && !cached.dirty()
                && player.tickCount - cached.computedAtTick() < MAX_CACHE_AGE_TICKS) {
            return;
        }
        if (held.is(ModItems.ADVANCED_SELECTION_STAFF.get())) {
            BuildToolsState.refreshMeasurementOverlay(player);
        } else {
            BuildToolsState.clearMeasurementOverlay(player);
        }

        ToolStatus status = statusFor(player, held);
        if (status != null && (cached == null
                || cached.item() != held.getItem()
                || !status.equals(cached.status()))) {
            sendStatus(player, held, status);
        }
        STATUS_CACHE.put(player.getUUID(), new CachedStatus(held.getItem(), player.tickCount, false, status));
    }

    public static void markDirty(ServerPlayer player) {
        CachedStatus cached = STATUS_CACHE.get(player.getUUID());
        if (cached != null && !cached.dirty()) {
            STATUS_CACHE.put(player.getUUID(), new CachedStatus(cached.item(), cached.computedAtTick(), true, cached.status()));
        }
    }

    public static void clear(ServerPlayer player) {
        STATUS_CACHE.remove(player.getUUID());
    }

    private static void sendStatus(ServerPlayer player, ItemStack held, ToolStatus status) {
        PacketDistributor.sendToPlayer(player, new ToolStatusPayload(true, status.title(), status.lines(), accentColor(held)));
    }

    private static int accentColor(ItemStack held) {
        if (held.is(ModItems.SELECTION_STAFF.get()) || held.is(ModItems.ADVANCED_SELECTION_STAFF.get())) {
            return 0x41C7F4;
        }
        if (held.is(ModItems.BUILDER_WAND.get()) || held.is(ModItems.ADVANCED_BUILDER_WAND.get())) {
            return 0x60D96A;
        }
        if (held.is(ModItems.BUILDER_BRUSH.get())) {
            return 0xD2B45F;
        }
        if (held.is(ModItems.AREA_BREAKER.get())) {
            return 0xF05A4F;
        }
        if (held.is(ModItems.BLUEPRINT_TROWEL.get())) {
            return 0x7FA7FF;
        }
        return 0xDADADA;
    }

    private static ItemStack heldBuildTool(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (isBuildTool(mainHand)) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        return isBuildTool(offHand) ? offHand : ItemStack.EMPTY;
    }

    private static boolean isBuildTool(ItemStack stack) {
        return stack.is(ModItems.SELECTION_STAFF.get())
                || stack.is(ModItems.ADVANCED_SELECTION_STAFF.get())
                || stack.is(ModItems.BUILDER_WAND.get())
                || stack.is(ModItems.ADVANCED_BUILDER_WAND.get())
                || stack.is(ModItems.BUILDER_BRUSH.get())
                || stack.is(ModItems.AREA_BREAKER.get())
                || stack.is(ModItems.BLUEPRINT_TROWEL.get())
                || stack.is(ModItems.UNDO_TOKEN.get())
                || stack.is(ModItems.REDO_TOKEN.get());
    }

    private static ToolStatus statusFor(ServerPlayer player, ItemStack held) {
        if (held.is(ModItems.SELECTION_STAFF.get())) {
            return new ToolStatus(held.getHoverName(), selectionLines(player));
        }
        if (held.is(ModItems.ADVANCED_SELECTION_STAFF.get())) {
            return new ToolStatus(held.getHoverName(), advancedSelectionLines(player));
        }
        if (held.is(ModItems.BUILDER_WAND.get())) {
            return new ToolStatus(held.getHoverName(), builderLines(player));
        }
        if (held.is(ModItems.ADVANCED_BUILDER_WAND.get())) {
            return new ToolStatus(held.getHoverName(), advancedBuilderLines(player));
        }
        if (held.is(ModItems.BUILDER_BRUSH.get())) {
            return new ToolStatus(held.getHoverName(), brushLines(player));
        }
        if (held.is(ModItems.AREA_BREAKER.get())) {
            return new ToolStatus(held.getHoverName(), breakerLines(player));
        }
        if (held.is(ModItems.BLUEPRINT_TROWEL.get())) {
            return new ToolStatus(held.getHoverName(), trowelLines(player));
        }
        if (held.is(ModItems.UNDO_TOKEN.get())) {
            return new ToolStatus(held.getHoverName(), historyLines(true, BuildToolsState.peekUndo(player).orElse(null), player));
        }
        if (held.is(ModItems.REDO_TOKEN.get())) {
            return new ToolStatus(held.getHoverName(), historyLines(false, BuildToolsState.peekRedo(player).orElse(null), player));
        }
        return null;
    }

    private static List<Component> advancedSelectionLines(ServerPlayer player) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("buildtools.status.points", BuildToolsState.advancedPointCount(player)));
        SelectionShape shape = BuildToolsState.selectionShape(player);
        CustomShapeMode smartMode = BuildToolsState.customShapeMode(player);
        if (shape == SelectionShape.CUSTOM_SMART || smartMode != CustomShapeMode.AUTO) {
            lines.add(Component.translatable("buildtools.status.smart_mode", smartMode.displayName()));
        }
        List<Component> measurementLines = BuildToolsState.measurementStatusLines(player);
        if (!measurementLines.isEmpty()) {
            lines.addAll(measurementLines);
            return lines;
        }
        lines.addAll(selectionLines(player));
        return lines;
    }

    private static List<Component> selectionLines(ServerPlayer player) {
        SelectionStats stats = selectionStats(player);
        if (!stats.valid()) {
            return List.of(stats.status());
        }
        BuildMode mode = BuildToolsState.mode(player);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("buildtools.status.shape", stats.shapeName()));
        lines.add(Component.translatable("buildtools.status.size_area", stats.dimensions(), stats.total()));
        lines.add(Component.translatable("buildtools.status.air_blocks", stats.air(), stats.solid()));
        lines.add(Component.translatable("buildtools.status.mode_targets", mode.displayName(), stats.targetsFor(mode)));
        addLimitLine(lines, stats.targetsFor(mode));
        return lines;
    }

    private static List<Component> builderLines(ServerPlayer player) {
        SelectionStats stats = selectionStats(player);
        if (!stats.valid()) {
            return List.of(stats.status());
        }
        List<Component> lines = new ArrayList<>();
        BlockState selected = BuildToolsState.primaryMaterial(player);
        if (selected == null) {
            lines.add(Component.translatable("buildtools.status.shape", stats.shapeName()));
            lines.add(Component.translatable("buildtools.status.size_area", stats.dimensions(), stats.total()));
            lines.add(Component.translatable("buildtools.status.select_material"));
            return lines;
        }
        BuildMode mode = BuildToolsState.mode(player);
        List<BlockState> targetStates = MaterialChecklist.targetsFor(player);
        int targets = targetStates.size();
        BlockCostPlan costPlan = BlockCostPlan.create(player, targetStates);
        lines.add(Component.translatable("buildtools.status.mode_material", mode.displayName(), materialName(player)));
        lines.add(Component.translatable("buildtools.status.size", stats.dimensions()));
        lines.add(Component.translatable("buildtools.status.will_place", targets, stats.air(), stats.solid()));
        lines.add(Component.translatable("buildtools.status.replace_target", BuildToolsState.replaceTarget(player).getBlock().getName()));
        addCostLines(lines, player, costPlan);
        addLimitLine(lines, targets);
        return lines;
    }

    private static List<Component> advancedBuilderLines(ServerPlayer player) {
        List<Component> lines = new ArrayList<>(builderLines(player));
        if (BuildToolsState.primaryMaterial(player) == null) {
            return lines;
        }
        int paletteSize = BuildToolsState.materialSelections(player).size();
        PaletteMode paletteMode = BuildToolsState.paletteMode(player);
        lines.add(Component.translatable("buildtools.status.palette", paletteSize, paletteMode.displayName()));
        if (paletteMode == PaletteMode.GRADIENT) {
            lines.add(Component.translatable("buildtools.status.gradient", DirectionDisplay.gradientDirection(player, BuildToolsState.gradientDirection(player))));
        }
        lines.add(Component.translatable("buildtools.status.rotation", BuildToolsState.blockRotationMode(player).displayName()));
        lines.add(Component.translatable("buildtools.status.plan_hint"));
        return lines;
    }

    private static List<Component> brushLines(ServerPlayer player) {
        BlockState selected = BuildToolsState.primaryMaterial(player);
        Component material = selected == null ? Component.translatable("buildtools.option.none") : selected.getBlock().getName();
        return List.of(
                Component.translatable("buildtools.status.brush_mode_shape", BuildToolsState.brushMode(player).displayName(), BuildToolsState.selectionShape(player).displayName()),
                Component.translatable("buildtools.status.brush_size", BuildToolsState.brushRadius(player), BuildToolsState.brushDepth(player), BuildToolsState.brushDensity(player)),
                Component.translatable("buildtools.status.block_target", material, BuildToolsState.brushReplaceTarget(player).getBlock().getName()),
                Component.translatable("buildtools.status.brush_controls"));
    }

    private static List<Component> breakerLines(ServerPlayer player) {
        SelectionStats stats = selectionStats(player);
        if (!stats.valid()) {
            return List.of(stats.status());
        }
        AreaBreakerPreset preset = BuildToolsState.areaBreakerPreset(player);
        int willBreak = preset == AreaBreakerPreset.CLEAR_SNOW_CROPS
                ? (int) stats.positions().stream()
                        .filter(pos -> BuildOperationEngine.isClearSnowCropsTarget(player.level().getBlockState(pos)))
                        .count()
                : stats.solid();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("buildtools.status.shape_preset", stats.shapeName(), preset.displayName()));
        lines.add(Component.translatable("buildtools.status.size_area", stats.dimensions(), stats.total()));
        lines.add(Component.translatable("buildtools.status.will_break", willBreak, stats.air()));
        lines.add(Component.translatable("buildtools.status.breaker_history_hint"));
        addLimitLine(lines, willBreak);
        return lines;
    }

    private static List<Component> trowelLines(ServerPlayer player) {
        SelectionStats stats = selectionStats(player);
        Blueprint blueprint = BuildToolsState.blueprint(player).orElse(null);
        String activeName = BuildToolsState.activeBlueprintName(player).orElse(null);
        Component saved = blueprint == null
                ? Component.translatable("buildtools.status.no_blueprint")
                : Component.translatable("buildtools.status.blueprint_summary",
                        activeName == null ? Component.translatable("buildtools.status.clipboard") : Component.literal(activeName),
                        blueprint.entries().size());

        if (player.isShiftKeyDown()) {
            if (!stats.valid()) {
                return List.of(Component.translatable("buildtools.status.mode.copy"), stats.status(), saved);
            }
            return List.of(
                    Component.translatable("buildtools.status.mode.copy"),
                    Component.translatable("buildtools.status.size_area", stats.dimensions(), stats.total()),
                    Component.translatable("buildtools.status.copy_counts", stats.solid(), stats.air()),
                    saved);
        }

        if (blueprint == null || blueprint.entries().isEmpty() && blueprint.entities().isEmpty()) {
            return List.of(Component.translatable("buildtools.status.mode.paste"), Component.translatable("buildtools.status.no_blueprint"));
        }
        BlockCostPlan costPlan = BlockCostPlan.create(player, blueprint.entries().stream().map(Blueprint.Entry::state).toList());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("buildtools.status.mode.paste"));
        lines.add(saved);
        addCostLines(lines, player, costPlan);
        lines.add(Component.translatable(BuildToolsState.pendingPasteOrigin(player).isPresent()
                ? "buildtools.status.confirm_same_spot"
                : "buildtools.status.preview_block_face"));
        lines.add(Component.translatable("buildtools.status.sneak_copy"));
        return lines;
    }

    private static List<Component> historyLines(boolean undo, UndoSnapshot snapshot, ServerPlayer player) {
        if (snapshot == null) {
            return List.of(Component.translatable("buildtools.status.history.none"));
        }
        int count = undo ? BuildToolsState.undoCount(player) : BuildToolsState.redoCount(player);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("buildtools.status.history.ready", snapshot.entries().size()));
        lines.add(Component.translatable("buildtools.status.history.count", count));
        if (player.gameMode.isCreative()) {
            lines.add(Component.translatable("buildtools.status.history.creative"));
        }
        return lines;
    }

    private static SelectionStats selectionStats(ServerPlayer player) {
        Selection selection = BuildToolsState.selection(player);
        if (selection.dimension() == null) {
            Component first = Component.translatable(selection.firstOptional().isPresent()
                    ? "buildtools.status.position_one_set"
                    : "buildtools.status.position_one_missing");
            Component second = selection.shape() == SelectionShape.CUSTOM_SMART
                    ? Component.translatable("buildtools.status.custom_points", BuildToolsState.advancedPointCount(player))
                    : Component.translatable(selection.secondOptional().isPresent()
                            ? "buildtools.status.position_two_set"
                            : "buildtools.status.position_two_missing");
            return SelectionStats.invalid(Component.translatable("buildtools.status.selection_incomplete", first, second));
        }
        if (!selection.dimension().equals(player.level().dimension())) {
            return SelectionStats.invalid(Component.translatable("buildtools.status.selection_other_dimension"));
        }

        List<BlockPos> positions = BuildToolsState.generatedSelection(player);
        if (positions.isEmpty()) {
            return SelectionStats.invalid(Component.translatable("buildtools.status.selection_empty"));
        }
        if (positions.size() > MAX_STATUS_SCAN_POSITIONS) {
            return SelectionStats.invalid(Component.translatable(
                    "buildtools.status.selection_scan_limited", positions.size(), MAX_STATUS_SCAN_POSITIONS));
        }

        // getBlockState synchronously requests a chunk when it is not loaded. This method runs
        // from the server tick, so doing that for status text can deadlock the tick until the
        // watchdog terminates the server. Operations perform their own loaded-chunk validation;
        // the action bar should simply defer its live counts until every selected chunk is loaded.
        for (BlockPos pos : positions) {
            if (!player.level().hasChunkAt(pos)) {
                return SelectionStats.invalid(Component.translatable("buildtools.error.unloaded"));
            }
        }

        int air = 0;
        int fillTargets = 0;
        int replaceTargets = 0;
        int surfaceTargets = 0;
        BlockState replaceMatch = BuildToolsState.replaceTarget(player);
        for (BlockPos pos : positions) {
            BlockState state = player.level().getBlockState(pos);
            if (state.isAir()) {
                air++;
            }
            if (state.canBeReplaced()) {
                fillTargets++;
            }
            if (state.canBeReplaced() && touchesMatchingBlock(player, pos, replaceMatch)) {
                replaceTargets++;
            }
        }
        surfaceTargets = SurfacePlacementSupport.candidates(player.level(), positions).size();

        return new SelectionStats(
                true,
                Component.empty(),
                shapeName(player, selection.shape()),
                dimensions(positions),
                positions.size(),
                air,
                positions.size() - air,
                fillTargets,
                replaceTargets,
                surfaceTargets,
                positions);
    }

    private static boolean touchesMatchingBlock(ServerPlayer player, BlockPos pos, BlockState match) {
        if (match == null || match.isAir()) {
            return false;
        }
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (player.level().hasChunkAt(adjacent)
                    && player.level().getBlockState(adjacent).is(match.getBlock())) {
                return true;
            }
        }
        return false;
    }

    private static String dimensions(List<BlockPos> positions) {
        int minX = positions.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int minY = positions.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxX = positions.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int maxY = positions.stream().mapToInt(BlockPos::getY).max().orElse(0);
        int maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        return width + "x" + height + "x" + depth;
    }

    private static Component shapeName(ServerPlayer player, SelectionShape shape) {
        if (shape == SelectionShape.STAIRS) {
            return Component.translatable("buildtools.status.stairs_shape", shape.displayName(),
                    DirectionDisplay.stairDirection(player, BuildToolsState.stairDirectionOverride(player)));
        }
        return shape.displayName();
    }

    private static void addCostLines(List<Component> lines, ServerPlayer player, BlockCostPlan costPlan) {
        int required = costPlan.required().values().stream().mapToInt(Integer::intValue).sum();
        if (player.gameMode.isCreative()) {
            lines.add(Component.translatable("buildtools.status.materials_creative", required));
            return;
        }
        int missing = costPlan.missing().values().stream().mapToInt(Integer::intValue).sum();
        if (missing > 0) {
            lines.add(Component.translatable("buildtools.status.materials_missing", required, missing, compactMissing(costPlan.missing())));
            return;
        }
        lines.add(Component.translatable("buildtools.status.materials_ready", required));
    }

    private static Component materialName(ServerPlayer player) {
        List<PaletteEntry> materials = BuildToolsState.materialSelections(player);
        if (materials.isEmpty()) {
            return Component.translatable("buildtools.option.none");
        }
        Component name = materials.getFirst().state().getBlock().getName();
        int extra = materials.size() - 1;
        return extra <= 0 ? name : Component.translatable("buildtools.status.material_plus", name, extra);
    }

    private static void addLimitLine(List<Component> lines, int changes) {
        if (changes > BuildToolsConfig.MAX_OPERATION_VOLUME.get()) {
            lines.add(Component.translatable("buildtools.status.over_limit", changes, BuildToolsConfig.MAX_OPERATION_VOLUME.get()));
        }
    }

    private static Component compactMissing(Map<ItemStackKey, Integer> missing) {
        if (missing.isEmpty()) {
            return Component.empty();
        }
        Map.Entry<ItemStackKey, Integer> first = missing.entrySet().iterator().next();
        int extraTypes = missing.size() - 1;
        return extraTypes > 0
                ? Component.translatable("buildtools.status.missing_summary_more", first.getValue(), first.getKey().stack(1).getHoverName(), extraTypes)
                : Component.translatable("buildtools.status.missing_summary", first.getValue(), first.getKey().stack(1).getHoverName());
    }

    private record SelectionStats(
            boolean valid,
            Component status,
            Component shapeName,
            String dimensions,
            int total,
            int air,
            int solid,
            int fillTargets,
            int replaceTargets,
            int surfaceTargets,
            List<BlockPos> positions) {
        private static SelectionStats invalid(Component status) {
            return new SelectionStats(false, status, Component.empty(), "", 0, 0, 0, 0, 0, 0, List.of());
        }

        private int targetsFor(BuildMode mode) {
            return switch (mode) {
                case FILL -> fillTargets;
                case REPLACE -> replaceTargets;
                case SURFACE -> surfaceTargets;
            };
        }
    }

    private record ToolStatus(Component title, List<Component> lines) {
    }

    private record CachedStatus(Item item, int computedAtTick, boolean dirty, ToolStatus status) {
    }
}
