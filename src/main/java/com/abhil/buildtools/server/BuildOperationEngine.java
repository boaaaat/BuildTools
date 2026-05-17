package com.abhil.buildtools.server;

import com.abhil.buildtools.config.BuildToolsConfig;
import com.abhil.buildtools.network.BuildToolsNetworking;
import com.abhil.buildtools.network.PreviewPayload;
import com.abhil.buildtools.network.ToolStatusPayload;
import com.abhil.buildtools.shape.BrushMode;
import com.abhil.buildtools.shape.BuildMode;
import com.abhil.buildtools.shape.Selection;
import com.abhil.buildtools.shape.ShapeGenerator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BuildOperationEngine {
    private static final Queue<BuildOperation> QUEUE = new ArrayDeque<>();
    private static final Map<UUID, PendingOperation> PENDING_OPERATIONS = new HashMap<>();

    private BuildOperationEngine() {
    }

    public static void clearPendingOperation(ServerPlayer player) {
        PENDING_OPERATIONS.remove(player.getUUID());
    }

    public static boolean executeBuilder(ServerPlayer player) {
        return executeBuilder(player, false);
    }

    public static boolean executeAdvancedBuilder(ServerPlayer player) {
        return executeBuilder(player, true);
    }

    private static boolean executeBuilder(ServerPlayer player, boolean advanced) {
        Selection selection = BuildToolsState.selection(player);
        if (!selection.isComplete() || selection.dimension() == null) {
            fail(player, "buildtools.error.incomplete_selection");
            return false;
        }
        if (!selection.dimension().equals(player.level().dimension())) {
            fail(player, "buildtools.error.dimension_mismatch");
            return false;
        }
        List<PaletteEntry> palette = advanced ? BuildToolsState.paletteEntries(player) : List.of();
        ItemStack source = player.getOffhandItem();
        BlockState target;
        if (source.getItem() instanceof BlockItem blockItem) {
            target = blockItem.getBlock().defaultBlockState();
        } else if (advanced && !palette.isEmpty()) {
            target = palette.getFirst().state();
        } else {
            fail(player, "buildtools.error.offhand_block");
            return false;
        }
        if (!isSupportedTarget(target)) {
            fail(player, "buildtools.error.unsupported_block");
            return false;
        }

        List<BlockPos> generated = ShapeGenerator.generate(selection);
        if (generated.isEmpty()) {
            fail(player, "buildtools.error.empty_shape");
            return false;
        }
        if (generated.size() > BuildToolsConfig.MAX_OPERATION_VOLUME.get()) {
            fail(player, Component.translatable("buildtools.error.too_large", generated.size(), BuildToolsConfig.MAX_OPERATION_VOLUME.get()));
            return false;
        }

        BuildMode mode = BuildToolsState.mode(player);
        PaletteMode paletteMode = advanced ? BuildToolsState.paletteMode(player) : PaletteMode.SINGLE;
        GradientDirection gradientDirection = advanced ? BuildToolsState.gradientDirection(player) : GradientDirection.Y;
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> targets = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        Map<ItemStackKey, Integer> refund = new LinkedHashMap<>();
        ServerLevel level = player.serverLevel();

        BlockState replaceMatch = advanced ? BuildToolsState.replaceTarget(player) : level.getBlockState(selection.first());
        for (BlockPos pos : generated) {
            BlockState previous = level.getBlockState(pos);
            if (!canTouch(player, level, pos, previous)) {
                return false;
            }
            if (mode == BuildMode.FILL && !previous.canBeReplaced()) {
                continue;
            }
            if (mode == BuildMode.REPLACE && !(advanced ? BuildToolsState.matchesReplaceTargets(player, previous, replaceMatch) : previous.is(replaceMatch.getBlock()))) {
                continue;
            }
            BlockState targetState = materialTarget(selection, pos, target, palette, paletteMode, gradientDirection);
            positions.add(pos.immutable());
            targets.add(targetState);
            boolean restore = previous.isAir() || mode == BuildMode.FILL;
            undo.add(undoEntry(level, pos, previous, targetState, restore));
            refund.merge(new ItemStackKey(targetState.getBlock().asItem()), 1, Integer::sum);
        }

        boolean trackHistory = hasHistoryItems(player);
        String label = advanced ? "advanced builder" : "builder";
        return previewOrConfirm(player, level, new PendingOperation(
                level.dimension(),
                label,
                List.copyOf(positions),
                List.copyOf(targets),
                List.copyOf(undo),
                Map.copyOf(refund),
                trackHistory ? dropsForChanges(player, level, undo) : Map.of(),
                trackHistory,
                false,
                signature(label, level, positions, targets)));
    }

    public static boolean executeBreaker(ServerPlayer player) {
        Selection selection = BuildToolsState.selection(player);
        if (!selection.isComplete() || selection.dimension() == null) {
            fail(player, "buildtools.error.incomplete_selection");
            return false;
        }
        if (!selection.dimension().equals(player.level().dimension())) {
            fail(player, "buildtools.error.dimension_mismatch");
            return false;
        }
        List<BlockPos> generated = ShapeGenerator.generate(selection);
        if (generated.isEmpty()) {
            fail(player, "buildtools.error.empty_shape");
            return false;
        }
        if (generated.size() > BuildToolsConfig.MAX_OPERATION_VOLUME.get()) {
            fail(player, Component.translatable("buildtools.error.too_large", generated.size(), BuildToolsConfig.MAX_OPERATION_VOLUME.get()));
            return false;
        }

        ServerLevel level = player.serverLevel();
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> targets = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        for (BlockPos pos : generated) {
            BlockState previous = level.getBlockState(pos);
            if (previous.isAir()) {
                continue;
            }
            if (!canTouch(player, level, pos, previous)) {
                return false;
            }
            positions.add(pos.immutable());
            targets.add(Blocks.AIR.defaultBlockState());
            undo.add(undoEntry(level, pos, previous, Blocks.AIR.defaultBlockState(), false));
        }

        boolean trackHistory = hasHistoryItems(player);
        String label = "area break";
        return previewOrConfirm(player, level, new PendingOperation(
                level.dimension(),
                label,
                List.copyOf(positions),
                List.copyOf(targets),
                List.copyOf(undo),
                Map.of(),
                trackHistory ? dropsForChanges(player, level, undo) : Map.of(),
                trackHistory,
                true,
                signature(label, level, positions, targets)));
    }

    public static boolean createPlan(ServerPlayer player) {
        Selection selection = BuildToolsState.selection(player);
        if (!selection.isComplete() || selection.dimension() == null) {
            fail(player, "buildtools.error.incomplete_selection");
            return false;
        }
        if (!selection.dimension().equals(player.level().dimension())) {
            fail(player, "buildtools.error.dimension_mismatch");
            return false;
        }
        List<PaletteEntry> palette = BuildToolsState.paletteEntries(player);
        ItemStack source = player.getOffhandItem();
        BlockState target;
        if (source.getItem() instanceof BlockItem blockItem) {
            target = blockItem.getBlock().defaultBlockState();
        } else if (!palette.isEmpty()) {
            target = palette.getFirst().state();
        } else {
            fail(player, "buildtools.error.offhand_block");
            return false;
        }
        if (!isSupportedTarget(target)) {
            fail(player, "buildtools.error.unsupported_block");
            return false;
        }

        List<BlockPos> generated = ShapeGenerator.generate(selection);
        if (generated.isEmpty()) {
            fail(player, "buildtools.error.empty_shape");
            return false;
        }
        if (generated.size() > BuildToolsConfig.MAX_OPERATION_VOLUME.get()) {
            fail(player, Component.translatable("buildtools.error.too_large", generated.size(), BuildToolsConfig.MAX_OPERATION_VOLUME.get()));
            return false;
        }

        BuildMode mode = BuildToolsState.mode(player);
        PaletteMode paletteMode = BuildToolsState.paletteMode(player);
        GradientDirection gradientDirection = BuildToolsState.gradientDirection(player);
        BlockState replaceMatch = BuildToolsState.replaceTarget(player);
        List<BuildPlan.Entry> entries = new ArrayList<>();
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : generated) {
            BlockState previous = level.getBlockState(pos);
            if (mode == BuildMode.FILL && !previous.canBeReplaced()) {
                continue;
            }
            if (mode == BuildMode.REPLACE && !BuildToolsState.matchesReplaceTargets(player, previous, replaceMatch)) {
                continue;
            }
            entries.add(new BuildPlan.Entry(pos.immutable(), materialTarget(selection, pos, target, palette, paletteMode, gradientDirection)));
        }
        if (entries.isEmpty()) {
            fail(player, "buildtools.error.no_targets");
            return false;
        }
        BuildToolsState.setPlan(player, new BuildPlan(level.dimension(), List.copyOf(entries)));
        return true;
    }

    public static boolean applyPlan(ServerPlayer player) {
        BuildPlan plan = BuildToolsState.plan(player).orElse(null);
        if (plan == null || plan.entries().isEmpty()) {
            fail(player, "buildtools.error.no_plan");
            return false;
        }
        if (!plan.dimension().equals(player.level().dimension())) {
            fail(player, "buildtools.error.dimension_mismatch");
            return false;
        }

        ServerLevel level = player.serverLevel();
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> targets = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        Map<ItemStackKey, Integer> refund = new LinkedHashMap<>();
        for (BuildPlan.Entry entry : plan.entries()) {
            BlockState previous = level.getBlockState(entry.pos());
            if (!canTouch(player, level, entry.pos(), previous)) {
                return false;
            }
            positions.add(entry.pos().immutable());
            targets.add(entry.state());
            undo.add(undoEntry(level, entry.pos(), previous, entry.state(), previous.isAir() || previous.canBeReplaced()));
            refund.merge(new ItemStackKey(entry.state().getBlock().asItem()), 1, Integer::sum);
        }
        String label = "build plan";
        boolean trackHistory = hasHistoryItems(player);
        return previewOrConfirm(player, level, new PendingOperation(
                level.dimension(),
                label,
                List.copyOf(positions),
                List.copyOf(targets),
                List.copyOf(undo),
                Map.copyOf(refund),
                trackHistory ? dropsForChanges(player, level, undo) : Map.of(),
                trackHistory,
                false,
                signature(label, level, positions, targets)));
    }

    public static boolean executeBrush(ServerPlayer player, BlockPos origin) {
        ItemStack source = player.getOffhandItem();
        if (!(source.getItem() instanceof BlockItem blockItem)) {
            fail(player, "buildtools.error.offhand_block");
            return false;
        }
        BlockState target = blockItem.getBlock().defaultBlockState();
        if (!isSupportedTarget(target)) {
            fail(player, "buildtools.error.unsupported_block");
            return false;
        }

        BrushMode brushMode = BuildToolsState.brushMode(player);
        return brushMode == BrushMode.SMOOTH
                ? executeSmoothBrush(player, origin, target)
                : executePlaceBrush(player, origin, target, brushMode);
    }

    private static boolean executePlaceBrush(ServerPlayer player, BlockPos origin, BlockState target, BrushMode brushMode) {
        int radius = BuildToolsState.brushRadius(player);
        List<BlockPos> generated = brushMode == BrushMode.CYLINDER
                ? ShapeGenerator.cylinder(origin.offset(-radius, 0, -radius), origin.offset(radius, 0, radius))
                : ShapeGenerator.sphere(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius), true);
        ServerLevel level = player.serverLevel();
        BlockState replaceTarget = BuildToolsState.replaceTarget(player);
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> targets = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        Map<ItemStackKey, Integer> refund = new LinkedHashMap<>();
        for (BlockPos pos : generated) {
            BlockState previous = level.getBlockState(pos);
            if (!canTouch(player, level, pos, previous)) {
                return false;
            }
            if (brushMode == BrushMode.REPLACE && !previous.is(replaceTarget.getBlock())) {
                continue;
            }
            if (brushMode != BrushMode.REPLACE && !previous.canBeReplaced()) {
                continue;
            }
            positions.add(pos.immutable());
            targets.add(target);
            undo.add(undoEntry(level, pos, previous, target, previous.isAir() || previous.canBeReplaced()));
            refund.merge(new ItemStackKey(target.getBlock().asItem()), 1, Integer::sum);
        }
        boolean trackHistory = hasHistoryItems(player);
        String label = "brush";
        return previewOrConfirm(player, level, new PendingOperation(
                level.dimension(),
                label,
                List.copyOf(positions),
                List.copyOf(targets),
                List.copyOf(undo),
                Map.copyOf(refund),
                trackHistory ? dropsForChanges(player, level, undo) : Map.of(),
                trackHistory,
                false,
                signature(label + ":" + brushMode.name() + ":" + radius, level, positions, targets)));
    }

    private static boolean executeSmoothBrush(ServerPlayer player, BlockPos origin, BlockState fillState) {
        ServerLevel level = player.serverLevel();
        int radius = BuildToolsState.brushRadius(player);
        List<Integer> heights = new ArrayList<>();
        Map<BlockPos, Integer> tops = new LinkedHashMap<>();
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                BlockPos top = findTopSolid(level, x, z, origin.getY() - radius * 2, origin.getY() + radius * 2);
                if (top != null) {
                    heights.add(top.getY());
                    tops.put(new BlockPos(x, 0, z), top.getY());
                }
            }
        }
        if (heights.isEmpty()) {
            fail(player, "buildtools.error.no_targets");
            return false;
        }
        int average = Mth.floor(heights.stream().mapToInt(Integer::intValue).average().orElse(origin.getY()));
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> targets = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        Map<ItemStackKey, Integer> refund = new LinkedHashMap<>();

        for (Map.Entry<BlockPos, Integer> entry : tops.entrySet()) {
            int x = entry.getKey().getX();
            int z = entry.getKey().getZ();
            int topY = entry.getValue();
            if (topY > average) {
                for (int y = average + 1; y <= topY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState previous = level.getBlockState(pos);
                    if (!previous.isAir()) {
                        if (!canTouch(player, level, pos, previous)) {
                            return false;
                        }
                        positions.add(pos);
                        targets.add(Blocks.AIR.defaultBlockState());
                        undo.add(undoEntry(level, pos, previous, Blocks.AIR.defaultBlockState(), false));
                    }
                }
            } else if (topY < average) {
                for (int y = topY + 1; y <= average; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState previous = level.getBlockState(pos);
                    if (previous.canBeReplaced()) {
                        if (!canTouch(player, level, pos, previous)) {
                            return false;
                        }
                        positions.add(pos);
                        targets.add(fillState);
                        undo.add(undoEntry(level, pos, previous, fillState, true));
                        refund.merge(new ItemStackKey(fillState.getBlock().asItem()), 1, Integer::sum);
                    }
                }
            }
        }
        boolean trackHistory = hasHistoryItems(player);
        String label = "smooth brush";
        return previewOrConfirm(player, level, new PendingOperation(
                level.dimension(),
                label,
                List.copyOf(positions),
                List.copyOf(targets),
                List.copyOf(undo),
                Map.copyOf(refund),
                trackHistory ? dropsForChanges(player, level, undo) : Map.of(),
                trackHistory,
                false,
                signature(label + ":" + radius, level, positions, targets)));
    }

    public static boolean copySelection(ServerPlayer player) {
        Selection selection = BuildToolsState.selection(player);
        if (!selection.isComplete() || selection.dimension() == null || !selection.dimension().equals(player.level().dimension())) {
            fail(player, "buildtools.error.incomplete_selection");
            return false;
        }
        List<BlockPos> positions = ShapeGenerator.generate(selection);
        if (positions.size() > BuildToolsConfig.MAX_COPY_VOLUME.get()) {
            fail(player, Component.translatable("buildtools.error.copy_too_large", positions.size(), BuildToolsConfig.MAX_COPY_VOLUME.get()));
            return false;
        }
        ServerLevel level = player.serverLevel();
        BlockPos origin = selection.first();
        List<Blueprint.Entry> entries = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (!isSupportedTarget(state) || level.getBlockEntity(pos) != null) {
                fail(player, "buildtools.error.copy_unsupported");
                return false;
            }
            entries.add(new Blueprint.Entry(pos.subtract(origin), state));
        }
        if (entries.size() > BuildToolsConfig.MAX_COPY_VOLUME.get()) {
            fail(player, Component.translatable("buildtools.error.copy_too_large", entries.size(), BuildToolsConfig.MAX_COPY_VOLUME.get()));
            return false;
        }
        BuildToolsState.setBlueprint(player, new Blueprint(List.copyOf(entries)));
        player.displayClientMessage(Component.translatable("buildtools.message.copied", entries.size()), true);
        return true;
    }

    public static boolean pasteBlueprint(ServerPlayer player, BlockPos origin) {
        Blueprint blueprint = BuildToolsState.blueprint(player).orElse(null);
        if (blueprint == null || blueprint.entries().isEmpty()) {
            fail(player, "buildtools.error.no_blueprint");
            return false;
        }
        if (blueprint.entries().size() > BuildToolsConfig.MAX_COPY_VOLUME.get()) {
            fail(player, Component.translatable("buildtools.error.copy_too_large", blueprint.entries().size(), BuildToolsConfig.MAX_COPY_VOLUME.get()));
            return false;
        }

        ServerLevel level = player.serverLevel();
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> targets = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        Map<ItemStackKey, Integer> refund = new HashMap<>();
        for (Blueprint.Entry entry : blueprint.entries()) {
            BlockPos pos = origin.offset(entry.offset());
            BlockState previous = level.getBlockState(pos);
            if (!canTouch(player, level, pos, previous)) {
                return false;
            }
            positions.add(pos.immutable());
            targets.add(entry.state());
            undo.add(undoEntry(level, pos, previous, entry.state(), previous.isAir() || previous.canBeReplaced()));
            refund.merge(new ItemStackKey(entry.state().getBlock().asItem()), 1, Integer::sum);
        }
        boolean trackHistory = hasHistoryItems(player);
        return enqueue(player, level, positions, targets, undo, refund, trackHistory ? dropsForChanges(player, level, undo) : Map.of(), trackHistory);
    }

    public static boolean previewOrConfirmBlueprintPaste(ServerPlayer player, BlockPos origin) {
        if (BuildToolsState.pendingPasteOrigin(player).filter(origin::equals).isPresent()) {
            boolean success = pasteBlueprint(player, origin);
            if (success) {
                BuildToolsState.clearPendingPaste(player);
            }
            return success;
        }
        previewBlueprintPaste(player, origin);
        return false;
    }

    public static boolean previewBlueprintPasteAtPlayer(ServerPlayer player) {
        previewBlueprintPaste(player, player.blockPosition());
        return false;
    }

    public static boolean previewBlueprintPasteAtSelection(ServerPlayer player) {
        Selection selection = BuildToolsState.selection(player);
        if (!selection.isComplete() || selection.dimension() == null || !selection.dimension().equals(player.level().dimension())) {
            fail(player, "buildtools.error.incomplete_selection");
            return false;
        }
        previewBlueprintPaste(player, selection.first());
        return false;
    }

    public static boolean confirmPendingBlueprintPaste(ServerPlayer player) {
        BlockPos origin = BuildToolsState.pendingPasteOrigin(player).orElse(null);
        if (origin == null) {
            fail(player, "buildtools.error.no_paste_preview");
            return false;
        }
        boolean success = pasteBlueprint(player, origin);
        if (success) {
            BuildToolsState.clearPendingPaste(player);
        }
        return success;
    }

    public static boolean nudgePendingBlueprintPaste(ServerPlayer player, net.minecraft.core.Direction direction) {
        BlockPos origin = BuildToolsState.pendingPasteOrigin(player).orElse(null);
        if (origin == null) {
            fail(player, "buildtools.error.no_paste_preview");
            return false;
        }
        previewBlueprintPaste(player, origin.relative(direction));
        return true;
    }

    private static void previewBlueprintPaste(ServerPlayer player, BlockPos origin) {
        Blueprint blueprint = BuildToolsState.blueprint(player).orElse(null);
        if (blueprint == null || blueprint.entries().isEmpty()) {
            fail(player, "buildtools.error.no_blueprint");
            return;
        }
        if (blueprint.entries().size() > BuildToolsConfig.MAX_COPY_VOLUME.get()) {
            fail(player, Component.translatable("buildtools.error.copy_too_large", blueprint.entries().size(), BuildToolsConfig.MAX_COPY_VOLUME.get()));
            return;
        }

        ServerLevel level = player.serverLevel();
        List<BlockPos> positions = new ArrayList<>();
        for (Blueprint.Entry entry : blueprint.entries()) {
            BlockPos pos = origin.offset(entry.offset());
            BlockState previous = level.getBlockState(pos);
            if (!canTouch(player, level, pos, previous)) {
                return;
            }
            positions.add(pos.immutable());
        }
        BuildToolsState.setPendingPastePreview(player, origin, positions);
    }

    public static boolean undo(ServerPlayer player) {
        UndoSnapshot snapshot = BuildToolsState.peekUndo(player).orElse(null);
        if (snapshot == null) {
            fail(player, "buildtools.error.no_undo");
            return false;
        }
        if (!snapshot.dimension().equals(player.level().dimension())) {
            fail(player, "buildtools.error.dimension_mismatch");
            return false;
        }
        ServerLevel level = player.serverLevel();
        for (UndoSnapshot.Entry entry : snapshot.entries()) {
            if (!canRestore(player, level, entry.pos())) {
                return false;
            }
        }
        UndoSnapshot redoSnapshot = new UndoSnapshot(snapshot.dimension(), captureCurrentAsRedone(level, snapshot.entries()), snapshot.refund(), snapshot.producedDrops());
        BuildToolsState.takeUndo(player);
        for (UndoSnapshot.Entry entry : snapshot.entries()) {
            restoreBlock(level, entry.pos(), entry.previousState(), entry.previousBlockEntity());
        }
        if (!player.gameMode.isCreative()) {
            BuildingStorageManager.depositOrGive(player, snapshot.refund());
        }
        if (hasHistoryItems(player)) {
            BuildToolsState.setRedo(player, redoSnapshot);
        } else {
            BuildToolsState.clearRedo(player);
        }
        player.displayClientMessage(Component.translatable("buildtools.message.undo", snapshot.entries().size()), true);
        return true;
    }

    public static boolean redo(ServerPlayer player) {
        UndoSnapshot snapshot = BuildToolsState.peekRedo(player).orElse(null);
        if (snapshot == null) {
            fail(player, "buildtools.error.no_redo");
            return false;
        }
        if (!snapshot.dimension().equals(player.level().dimension())) {
            fail(player, "buildtools.error.dimension_mismatch");
            return false;
        }

        ServerLevel level = player.serverLevel();
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> targets = new ArrayList<>();
        List<UndoSnapshot.Entry> undoEntries = new ArrayList<>();
        for (UndoSnapshot.Entry entry : snapshot.entries()) {
            if (!canRestore(player, level, entry.pos())) {
                return false;
            }
            BlockState previous = level.getBlockState(entry.pos());
            positions.add(entry.pos().immutable());
            targets.add(entry.redoneState());
            undoEntries.add(new UndoSnapshot.Entry(
                    entry.pos().immutable(),
                    previous,
                    captureBlockEntity(level, entry.pos()),
                    entry.redoneState(),
                    entry.redoneBlockEntity() == null ? null : entry.redoneBlockEntity().copy(),
                    true));
        }

        boolean trackHistory = hasHistoryItems(player);
        boolean queued = enqueue(player, level, positions, targets, List.copyOf(undoEntries), snapshot.refund(), trackHistory ? snapshot.producedDrops() : Map.of(), trackHistory);
        if (queued) {
            BuildToolsState.takeRedo(player);
        }
        return queued;
    }

    public static boolean collectStoredDrops(ServerPlayer player) {
        Map<ItemStackKey, Integer> drops = BuildToolsState.storedDrops(player);
        if (drops.isEmpty()) {
            fail(player, "buildtools.error.no_stored_drops");
            return false;
        }
        BuildingStorageManager.depositOrGive(player, drops);
        BuildToolsState.clearHistory(player);
        player.displayClientMessage(Component.translatable(
                "buildtools.message.stored_drops_collected",
                drops.values().stream().mapToInt(Integer::intValue).sum()), true);
        return true;
    }

    public static void tick() {
        int batch = BuildToolsConfig.BATCH_SIZE.get();
        while (batch > 0 && !QUEUE.isEmpty()) {
            BuildOperation operation = QUEUE.peek();
            int applied = applyBatch(operation, batch);
            batch -= applied;
            if (operation.positions().isEmpty()) {
                QUEUE.remove();
                UndoSnapshot snapshot = new UndoSnapshot(operation.dimension(), operation.undoEntries(), operation.refund(), operation.producedDrops());
                if (operation.trackHistory()) {
                    BuildToolsState.setUndo(operation.player(), snapshot);
                } else {
                    BuildToolsState.clearHistory(operation.player());
                }
                int drops = operation.producedDrops().values().stream().mapToInt(Integer::intValue).sum();
                operation.player().displayClientMessage(drops > 0
                        ? Component.translatable("buildtools.message.applied_collected", operation.undoEntries().size(), drops)
                        : Component.translatable("buildtools.message.applied", operation.undoEntries().size()), true);
            } else if (applied == 0) {
                QUEUE.remove();
            }
        }
    }

    private static boolean previewOrConfirm(ServerPlayer player, ServerLevel level, PendingOperation operation) {
        PendingOperation pending = PENDING_OPERATIONS.get(player.getUUID());
        if (pending != null && pending.signature().equals(operation.signature())) {
            PENDING_OPERATIONS.remove(player.getUUID());
            return operation.free()
                    ? enqueueFree(player, level, operation.positions(), operation.targets(), operation.undo(), operation.refund(), operation.producedDrops(), operation.trackHistory())
                    : enqueue(player, level, operation.positions(), operation.targets(), operation.undo(), operation.refund(), operation.producedDrops(), operation.trackHistory());
        }

        PENDING_OPERATIONS.put(player.getUUID(), operation);
        List<BlockPos> preview = operation.positions();
        if (preview.size() > BuildToolsNetworking.MAX_PREVIEW_POSITIONS) {
            preview = preview.subList(0, BuildToolsNetworking.MAX_PREVIEW_POSITIONS);
        }
        PacketDistributor.sendToPlayer(player, new PreviewPayload(preview, true));
        PacketDistributor.sendToPlayer(player, new ToolStatusPayload(true, "Preview Ready", List.of(
                operation.label() + ": " + operation.positions().size() + " changes",
                materialPreviewLine(player, operation),
                dropPreviewLine(operation),
                "Use the same tool again to confirm"), 0xF4C542));
        return false;
    }

    private static String materialPreviewLine(ServerPlayer player, PendingOperation operation) {
        if (operation.free()) {
            return "";
        }
        BlockCostPlan costPlan = BlockCostPlan.create(player, operation.targets());
        int required = costPlan.required().values().stream().mapToInt(Integer::intValue).sum();
        if (costPlan.canAfford()) {
            return "Materials ready: " + required;
        }
        int missing = costPlan.missing().values().stream().mapToInt(Integer::intValue).sum();
        return "Need " + required + " | missing " + missing;
    }

    private static String dropPreviewLine(PendingOperation operation) {
        if (!operation.trackHistory()) {
            return "No undo or redo token | drops behave normally";
        }
        int drops = operation.producedDrops().values().stream().mapToInt(Integer::intValue).sum();
        return drops <= 0 ? "" : "Stores " + drops + " drops in history";
    }

    private static String signature(String label, ServerLevel level, List<BlockPos> positions, List<BlockState> targets) {
        return label + ":" + level.dimension().location() + ":" + positions.hashCode() + ":" + targets.hashCode();
    }

    private static boolean enqueue(
            ServerPlayer player,
            ServerLevel level,
            List<BlockPos> positions,
            List<BlockState> targets,
            List<UndoSnapshot.Entry> undo,
            Map<ItemStackKey, Integer> refund,
            Map<ItemStackKey, Integer> producedDrops,
            boolean trackHistory) {
        if (positions.isEmpty()) {
            fail(player, "buildtools.error.no_targets");
            return false;
        }
        BlockCostPlan costPlan = BlockCostPlan.create(player, targets);
        if (!costPlan.canAfford()) {
            player.displayClientMessage(costPlan.missingMessage(), false);
            return false;
        }
        costPlan.consume(player);
        QUEUE.add(new BuildOperation(player, level, level.dimension(), new ArrayList<>(positions), new ArrayList<>(targets), List.copyOf(undo), Map.copyOf(refund), Map.copyOf(producedDrops), trackHistory));
        player.displayClientMessage(Component.translatable("buildtools.message.queued", positions.size()), true);
        return true;
    }

    private static boolean enqueueFree(
            ServerPlayer player,
            ServerLevel level,
            List<BlockPos> positions,
            List<BlockState> targets,
            List<UndoSnapshot.Entry> undo,
            Map<ItemStackKey, Integer> refund,
            Map<ItemStackKey, Integer> producedDrops,
            boolean trackHistory) {
        if (positions.isEmpty()) {
            fail(player, "buildtools.error.no_targets");
            return false;
        }
        QUEUE.add(new BuildOperation(player, level, level.dimension(), new ArrayList<>(positions), new ArrayList<>(targets), List.copyOf(undo), Map.copyOf(refund), Map.copyOf(producedDrops), trackHistory));
        player.displayClientMessage(Component.translatable("buildtools.message.queued", positions.size()), true);
        return true;
    }

    private static int applyBatch(BuildOperation operation, int budget) {
        int applied = 0;
        while (applied < budget && !operation.positions().isEmpty()) {
            int entryIndex = operation.undoEntries().size() - operation.positions().size();
            UndoSnapshot.Entry entry = operation.undoEntries().get(entryIndex);
            BlockPos pos = operation.positions().remove(0);
            BlockState target = operation.targetStates().remove(0);
            BlockState previous = operation.level().getBlockState(pos);
            if (target.isAir()) {
                operation.level().destroyBlock(pos, !operation.trackHistory(), operation.player());
                applied++;
                continue;
            }
            if (!previous.isAir() && !previous.canBeReplaced()) {
                operation.level().destroyBlock(pos, !operation.trackHistory(), operation.player());
            }
            restoreBlock(operation.level(), pos, target, entry.redoneBlockEntity());
            applied++;
        }
        return applied;
    }

    private static UndoSnapshot.Entry undoEntry(ServerLevel level, BlockPos pos, BlockState previous, BlockState redone, boolean mayRestorePrevious) {
        return new UndoSnapshot.Entry(pos.immutable(), previous, captureBlockEntity(level, pos), redone, null, mayRestorePrevious);
    }

    private static Map<ItemStackKey, Integer> dropsForChanges(ServerPlayer player, ServerLevel level, List<UndoSnapshot.Entry> entries) {
        Map<ItemStackKey, Integer> drops = new LinkedHashMap<>();
        for (UndoSnapshot.Entry entry : entries) {
            if (!shouldCollectDrops(entry.previousState(), entry.redoneState())) {
                continue;
            }
            for (ItemStack stack : Block.getDrops(entry.previousState(), level, entry.pos(), null, player, player.getMainHandItem())) {
                if (!stack.isEmpty()) {
                    drops.merge(new ItemStackKey(stack.getItem()), stack.getCount(), Integer::sum);
                }
            }
        }
        return Map.copyOf(drops);
    }

    private static boolean shouldCollectDrops(BlockState previous, BlockState redone) {
        return !previous.isAir() && !previous.canBeReplaced() && !redone.is(previous.getBlock());
    }

    private static List<UndoSnapshot.Entry> captureCurrentAsRedone(ServerLevel level, List<UndoSnapshot.Entry> entries) {
        List<UndoSnapshot.Entry> captured = new ArrayList<>();
        for (UndoSnapshot.Entry entry : entries) {
            captured.add(new UndoSnapshot.Entry(
                    entry.pos().immutable(),
                    entry.previousState(),
                    entry.previousBlockEntity() == null ? null : entry.previousBlockEntity().copy(),
                    level.getBlockState(entry.pos()),
                    captureBlockEntity(level, entry.pos()),
                    true));
        }
        return List.copyOf(captured);
    }

    private static CompoundTag captureBlockEntity(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess()).copy();
    }

    private static void restoreBlock(ServerLevel level, BlockPos pos, BlockState state, CompoundTag blockEntityTag) {
        BlockState previous = level.getBlockState(pos);
        level.setBlock(pos, state, 3);
        if (state.isAir()) {
            level.removeBlockEntity(pos);
            return;
        }
        if (blockEntityTag == null) {
            return;
        }

        CompoundTag restoredTag = blockEntityTag.copy();
        restoredTag.putInt("x", pos.getX());
        restoredTag.putInt("y", pos.getY());
        restoredTag.putInt("z", pos.getZ());
        BlockEntity restored = BlockEntity.loadStatic(pos, state, restoredTag, level.registryAccess());
        if (restored != null) {
            level.setBlockEntity(restored);
            restored.setChanged();
            level.sendBlockUpdated(pos, previous, state, 3);
        }
    }

    private static boolean canRestore(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!Level.isInSpawnableBounds(pos)) {
            fail(player, "buildtools.error.out_of_world");
            return false;
        }
        if (!level.hasChunk(new ChunkPos(pos).x, new ChunkPos(pos).z)) {
            fail(player, "buildtools.error.unloaded");
            return false;
        }
        if (player.blockPosition().distSqr(pos) > BuildToolsConfig.MAX_OPERATION_DISTANCE.get() * BuildToolsConfig.MAX_OPERATION_DISTANCE.get()) {
            fail(player, "buildtools.error.too_far");
            return false;
        }
        return true;
    }

    private static boolean canTouch(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState previous) {
        if (!Level.isInSpawnableBounds(pos)) {
            fail(player, "buildtools.error.out_of_world");
            return false;
        }
        if (!level.hasChunk(new ChunkPos(pos).x, new ChunkPos(pos).z)) {
            fail(player, "buildtools.error.unloaded");
            return false;
        }
        if (player.blockPosition().distSqr(pos) > BuildToolsConfig.MAX_OPERATION_DISTANCE.get() * BuildToolsConfig.MAX_OPERATION_DISTANCE.get()) {
            fail(player, "buildtools.error.too_far");
            return false;
        }
        if (!previous.getFluidState().isEmpty()) {
            fail(player, "buildtools.error.protected_state");
            return false;
        }
        if (level.getBlockEntity(pos) != null) {
            fail(player, "buildtools.error.protected_state");
            return false;
        }
        return true;
    }

    private static BlockState materialTarget(Selection selection, BlockPos pos, BlockState fallback, List<PaletteEntry> palette, PaletteMode mode, GradientDirection gradientDirection) {
        if (mode == PaletteMode.RANDOM && !palette.isEmpty()) {
            return randomTarget(pos, palette);
        }
        if (mode == PaletteMode.GRADIENT) {
            return gradientTarget(selection, pos, fallback, gradientPalette(fallback, palette), gradientDirection);
        }
        return fallback;
    }

    private static List<BlockState> gradientPalette(BlockState fallback, List<PaletteEntry> entries) {
        if (entries.isEmpty()) {
            return List.of(fallback);
        }
        List<BlockState> palette = new ArrayList<>();
        for (PaletteEntry entry : entries) {
            if (palette.stream().noneMatch(state -> state.is(entry.state().getBlock()))) {
                palette.add(entry.state());
            }
        }
        return palette;
    }

    private static BlockState randomTarget(BlockPos pos, List<PaletteEntry> palette) {
        int total = palette.stream().mapToInt(PaletteEntry::weight).sum();
        if (total <= 0) {
            return palette.getFirst().state();
        }
        int roll = Math.floorMod(weightedHash(pos), total);
        int cursor = 0;
        for (PaletteEntry entry : palette) {
            cursor += entry.weight();
            if (roll < cursor) {
                return entry.state();
            }
        }
        return palette.getLast().state();
    }

    private static int weightedHash(BlockPos pos) {
        long value = pos.asLong();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (int) value;
    }

    private static BlockState gradientTarget(Selection selection, BlockPos pos, BlockState fallback, List<BlockState> palette, GradientDirection direction) {
        if (palette.size() <= 1 || !selection.isComplete()) {
            return fallback;
        }
        double min = direction == GradientDirection.POINT_ORDER
                ? 0.0D
                : Math.min(gradientCoordinate(selection, selection.first(), direction), gradientCoordinate(selection, selection.second(), direction));
        double max = direction == GradientDirection.POINT_ORDER
                ? pointOrderLength(selection)
                : Math.max(gradientCoordinate(selection, selection.first(), direction), gradientCoordinate(selection, selection.second(), direction));
        if (min == max) {
            return palette.get(0);
        }
        double ratio = (gradientCoordinate(selection, pos, direction) - min) / (max - min);
        int index = Mth.clamp((int) Math.round(ratio * (palette.size() - 1)), 0, palette.size() - 1);
        return palette.get(index);
    }

    private static double gradientCoordinate(Selection selection, BlockPos pos, GradientDirection direction) {
        return switch (direction) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
            case POINT_ORDER -> pointOrderCoordinate(selection, pos);
        };
    }

    private static double pointOrderCoordinate(Selection selection, BlockPos pos) {
        List<BlockPos> points = selection.points();
        if (points.size() < 2) {
            return pos.distSqr(selection.first());
        }
        double bestDistance = Double.MAX_VALUE;
        double bestCoordinate = 0.0D;
        double traveled = 0.0D;
        for (int i = 1; i < points.size(); i++) {
            BlockPos a = points.get(i - 1);
            BlockPos b = points.get(i);
            double segmentLength = Math.sqrt(a.distSqr(b));
            if (segmentLength <= 0.0D) {
                continue;
            }
            double t = projectionRatio(a, b, pos);
            double closestX = a.getX() + (b.getX() - a.getX()) * t;
            double closestY = a.getY() + (b.getY() - a.getY()) * t;
            double closestZ = a.getZ() + (b.getZ() - a.getZ()) * t;
            double dx = pos.getX() - closestX;
            double dy = pos.getY() - closestY;
            double dz = pos.getZ() - closestZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestCoordinate = traveled + segmentLength * t;
            }
            traveled += segmentLength;
        }
        return bestCoordinate;
    }

    private static double pointOrderLength(Selection selection) {
        List<BlockPos> points = selection.points();
        if (points.size() < 2) {
            return Math.sqrt(selection.first().distSqr(selection.second()));
        }
        double length = 0.0D;
        for (int i = 1; i < points.size(); i++) {
            length += Math.sqrt(points.get(i - 1).distSqr(points.get(i)));
        }
        return length;
    }

    private static double projectionRatio(BlockPos a, BlockPos b, BlockPos pos) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double dz = b.getZ() - a.getZ();
        double lengthSqr = dx * dx + dy * dy + dz * dz;
        if (lengthSqr <= 0.0D) {
            return 0.0D;
        }
        double dot = (pos.getX() - a.getX()) * dx + (pos.getY() - a.getY()) * dy + (pos.getZ() - a.getZ()) * dz;
        return Mth.clamp(dot / lengthSqr, 0.0D, 1.0D);
    }

    private static BlockPos findTopSolid(ServerLevel level, int x, int z, int minY, int maxY) {
        int clampedMin = Mth.clamp(minY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        int clampedMax = Mth.clamp(maxY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        for (int y = clampedMax; y >= clampedMin; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isSupportedTarget(BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && state.getBlock().asItem() != Blocks.AIR.asItem();
    }

    private static boolean hasHistoryItems(ServerPlayer player) {
        return hasInventoryItem(player, com.abhil.buildtools.registry.ModItems.UNDO_TOKEN.get())
                || hasInventoryItem(player, com.abhil.buildtools.registry.ModItems.REDO_TOKEN.get());
    }

    private static boolean hasInventoryItem(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void fail(ServerPlayer player, String key) {
        fail(player, Component.translatable(key));
    }

    private static void fail(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }

    private record PendingOperation(
            ResourceKey<Level> dimension,
            String label,
            List<BlockPos> positions,
            List<BlockState> targets,
            List<UndoSnapshot.Entry> undo,
            Map<ItemStackKey, Integer> refund,
            Map<ItemStackKey, Integer> producedDrops,
            boolean trackHistory,
            boolean free,
            String signature) {
    }
}
