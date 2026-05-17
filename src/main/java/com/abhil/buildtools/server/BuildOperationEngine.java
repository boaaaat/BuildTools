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
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BuildOperationEngine {
    private static final Queue<BuildOperation> QUEUE = new ArrayDeque<>();
    private static final Map<UUID, PendingOperation> PENDING_OPERATIONS = new HashMap<>();
    private static final ThreadLocal<DropCapture> ACTIVE_DROP_CAPTURE = new ThreadLocal<>();

    private BuildOperationEngine() {
    }

    public static void clearPendingOperation(ServerPlayer player) {
        PENDING_OPERATIONS.remove(player.getUUID());
    }

    static void captureToolDrop(EntityJoinLevelEvent event) {
        DropCapture capture = ACTIVE_DROP_CAPTURE.get();
        if (capture == null || event.getLevel() != capture.level()) {
            return;
        }
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return;
        }
        capture.drops().add(stack.copy());
        event.setCanceled(true);
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
        BlockState target = materialState(source);
        if (target == null && advanced && !palette.isEmpty()) {
            target = palette.getFirst().state();
        }
        if (target == null) {
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
        List<CompoundTag> targetBlockEntities = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        List<ItemStack> refund = new ArrayList<>();
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
            targetBlockEntities.add(null);
            boolean restore = previous.isAir() || mode == BuildMode.FILL;
            undo.add(undoEntry(level, pos, previous, targetState, restore));
            addUndoRefund(refund, targetState);
        }

        boolean trackHistory = hasHistoryItems(player);
        List<CapturedEntity> removedEntities = captureDecorEntities(level, BlockPos.ZERO, positions);
        List<ItemStack> producedDrops = withEntityDrops(dropsForChanges(player, level, undo), removedEntities);
        String label = advanced ? "advanced builder" : "builder";
        return previewOrConfirm(player, level, new PendingOperation(
                level.dimension(),
                label,
                List.copyOf(positions),
                List.copyOf(targets),
                copyBlockEntities(targetBlockEntities),
                List.copyOf(undo),
                StoredItems.copyOf(refund),
                producedDrops,
                removedEntities,
                List.of(),
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
        List<CompoundTag> targetBlockEntities = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        for (BlockPos pos : generated) {
            BlockState previous = level.getBlockState(pos);
            if (previous.isAir() && previous.getFluidState().isEmpty()) {
                continue;
            }
            if (!canTouch(player, level, pos, previous)) {
                return false;
            }
            positions.add(pos.immutable());
            targets.add(Blocks.AIR.defaultBlockState());
            targetBlockEntities.add(null);
            undo.add(undoEntry(level, pos, previous, Blocks.AIR.defaultBlockState(), false));
        }

        boolean trackHistory = hasHistoryItems(player);
        List<CapturedEntity> removedEntities = captureDecorEntities(level, BlockPos.ZERO, generated);
        List<ItemStack> producedDrops = withEntityDrops(dropsForChanges(player, level, undo), removedEntities);
        String label = "area break";
        return previewOrConfirm(player, level, new PendingOperation(
                level.dimension(),
                label,
                List.copyOf(positions),
                List.copyOf(targets),
                copyBlockEntities(targetBlockEntities),
                List.copyOf(undo),
                List.of(),
                producedDrops,
                removedEntities,
                List.of(),
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
        BlockState target = materialState(source);
        if (target == null && !palette.isEmpty()) {
            target = palette.getFirst().state();
        }
        if (target == null) {
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
        List<CompoundTag> targetBlockEntities = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        List<ItemStack> refund = new ArrayList<>();
        for (BuildPlan.Entry entry : plan.entries()) {
            BlockState previous = level.getBlockState(entry.pos());
            if (!canTouch(player, level, entry.pos(), previous)) {
                return false;
            }
            positions.add(entry.pos().immutable());
            targets.add(entry.state());
            targetBlockEntities.add(entry.blockEntity());
            undo.add(undoEntry(level, entry.pos(), previous, entry.state(), entry.blockEntity(), previous.isAir() || previous.canBeReplaced()));
            addUndoRefund(refund, entry.state());
        }
        String label = "build plan";
        boolean trackHistory = hasHistoryItems(player);
        List<CapturedEntity> removedEntities = captureDecorEntities(level, BlockPos.ZERO, positions);
        List<ItemStack> producedDrops = withEntityDrops(dropsForChanges(player, level, undo), removedEntities);
        return previewOrConfirm(player, level, new PendingOperation(
                level.dimension(),
                label,
                List.copyOf(positions),
                List.copyOf(targets),
                copyBlockEntities(targetBlockEntities),
                List.copyOf(undo),
                StoredItems.copyOf(refund),
                producedDrops,
                removedEntities,
                List.of(),
                trackHistory,
                false,
                signature(label, level, positions, targets)));
    }

    public static boolean executeBrush(ServerPlayer player, BlockPos origin) {
        ItemStack source = player.getOffhandItem();
        BlockState target = materialState(source);
        if (target == null) {
            fail(player, "buildtools.error.offhand_block");
            return false;
        }
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
        List<CompoundTag> targetBlockEntities = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        List<ItemStack> refund = new ArrayList<>();
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
            targetBlockEntities.add(null);
            undo.add(undoEntry(level, pos, previous, target, previous.isAir() || previous.canBeReplaced()));
            addUndoRefund(refund, target);
        }
        boolean trackHistory = hasHistoryItems(player);
        List<CapturedEntity> removedEntities = captureDecorEntities(level, BlockPos.ZERO, positions);
        List<ItemStack> producedDrops = withEntityDrops(dropsForChanges(player, level, undo), removedEntities);
        String label = "brush";
        return previewOrConfirm(player, level, new PendingOperation(
                level.dimension(),
                label,
                List.copyOf(positions),
                List.copyOf(targets),
                copyBlockEntities(targetBlockEntities),
                List.copyOf(undo),
                StoredItems.copyOf(refund),
                producedDrops,
                removedEntities,
                List.of(),
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
        List<CompoundTag> targetBlockEntities = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        List<ItemStack> refund = new ArrayList<>();

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
                        targetBlockEntities.add(null);
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
                        targetBlockEntities.add(null);
                        undo.add(undoEntry(level, pos, previous, fillState, true));
                        addUndoRefund(refund, fillState);
                    }
                }
            }
        }
        boolean trackHistory = hasHistoryItems(player);
        List<CapturedEntity> removedEntities = captureDecorEntities(level, BlockPos.ZERO, positions);
        List<ItemStack> producedDrops = withEntityDrops(dropsForChanges(player, level, undo), removedEntities);
        String label = "smooth brush";
        return previewOrConfirm(player, level, new PendingOperation(
                level.dimension(),
                label,
                List.copyOf(positions),
                List.copyOf(targets),
                copyBlockEntities(targetBlockEntities),
                List.copyOf(undo),
                StoredItems.copyOf(refund),
                producedDrops,
                removedEntities,
                List.of(),
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
            if (state.isAir() && state.getFluidState().isEmpty()) {
                continue;
            }
            if (!isSupportedTarget(state)) {
                fail(player, "buildtools.error.copy_unsupported");
                return false;
            }
            entries.add(new Blueprint.Entry(pos.subtract(origin), state, captureBlockEntity(level, pos)));
        }
        List<CapturedEntity> entities = captureDecorEntities(level, origin, positions);
        if (entries.isEmpty() && entities.isEmpty()) {
            fail(player, "buildtools.error.no_targets");
            return false;
        }
        if (entries.size() > BuildToolsConfig.MAX_COPY_VOLUME.get()) {
            fail(player, Component.translatable("buildtools.error.copy_too_large", entries.size(), BuildToolsConfig.MAX_COPY_VOLUME.get()));
            return false;
        }
        BuildToolsState.setBlueprint(player, new Blueprint(List.copyOf(entries), entities));
        player.displayClientMessage(Component.translatable("buildtools.message.copied", entries.size() + entities.size()), true);
        return true;
    }

    public static boolean pasteBlueprint(ServerPlayer player, BlockPos origin) {
        Blueprint blueprint = BuildToolsState.blueprint(player).orElse(null);
        if (blueprint == null || blueprint.entries().isEmpty() && blueprint.entities().isEmpty()) {
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
        List<CompoundTag> targetBlockEntities = new ArrayList<>();
        List<UndoSnapshot.Entry> undo = new ArrayList<>();
        List<ItemStack> refund = new ArrayList<>();
        for (Blueprint.Entry entry : blueprint.entries()) {
            BlockPos pos = origin.offset(entry.offset());
            BlockState previous = level.getBlockState(pos);
            if (!canTouch(player, level, pos, previous)) {
                return false;
            }
            positions.add(pos.immutable());
            targets.add(entry.state());
            targetBlockEntities.add(entry.blockEntity());
            undo.add(undoEntry(level, pos, previous, entry.state(), entry.blockEntity(), previous.isAir() || previous.canBeReplaced()));
            addUndoRefund(refund, entry.state());
        }
        boolean trackHistory = hasHistoryItems(player);
        List<CapturedEntity> addedEntities = absoluteEntities(blueprint.entities(), origin);
        List<CapturedEntity> removedEntities = captureDecorEntities(level, BlockPos.ZERO, positions);
        return enqueue(player, level, positions, targets, copyBlockEntities(targetBlockEntities), undo, refund, withEntityDrops(dropsForChanges(player, level, undo), removedEntities), removedEntities, addedEntities, trackHistory);
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
        if (blueprint == null || blueprint.entries().isEmpty() && blueprint.entities().isEmpty()) {
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
        for (CapturedEntity entity : blueprint.entities()) {
            positions.add(BlockPos.containing(origin.getX() + entity.offsetX(), origin.getY() + entity.offsetY(), origin.getZ() + entity.offsetZ()));
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
        UndoSnapshot redoSnapshot = new UndoSnapshot(snapshot.dimension(), captureCurrentAsRedone(level, snapshot.entries()), snapshot.refund(), snapshot.producedDrops(), snapshot.removedEntities(), snapshot.addedEntities());
        BuildToolsState.takeUndo(player);
        removeCapturedEntities(level, snapshot.addedEntities());
        List<ItemStack> unexpectedDrops = new ArrayList<>();
        List<ItemStack> undoDropBudget = new ArrayList<>(StoredItems.copyOf(snapshot.refund()));
        for (UndoSnapshot.Entry entry : snapshot.entries()) {
            unexpectedDrops.addAll(capturedDropsBeyondBudget(undoDropBudget, restoreBlock(level, entry.pos(), entry.previousState(), entry.previousBlockEntity())));
        }
        spawnCapturedEntities(level, snapshot.removedEntities());
        if (!player.gameMode.isCreative()) {
            BuildingStorageManager.depositOrGive(player, snapshot.refund());
            BuildingStorageManager.depositOrGive(player, unexpectedDrops);
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
        List<CompoundTag> targetBlockEntities = new ArrayList<>();
        List<UndoSnapshot.Entry> undoEntries = new ArrayList<>();
        for (UndoSnapshot.Entry entry : snapshot.entries()) {
            if (!canRestore(player, level, entry.pos())) {
                return false;
            }
            BlockState previous = level.getBlockState(entry.pos());
            positions.add(entry.pos().immutable());
            targets.add(entry.redoneState());
            targetBlockEntities.add(entry.redoneBlockEntity() == null ? null : entry.redoneBlockEntity().copy());
            undoEntries.add(new UndoSnapshot.Entry(
                    entry.pos().immutable(),
                    previous,
                    captureBlockEntity(level, entry.pos()),
                    entry.redoneState(),
                    entry.redoneBlockEntity() == null ? null : entry.redoneBlockEntity().copy(),
                    true));
        }

        boolean trackHistory = hasHistoryItems(player);
        boolean queued = enqueue(player, level, positions, targets, copyBlockEntities(targetBlockEntities), List.copyOf(undoEntries), snapshot.refund(), snapshot.producedDrops(), snapshot.removedEntities(), snapshot.addedEntities(), trackHistory);
        if (queued) {
            BuildToolsState.takeRedo(player);
        }
        return queued;
    }

    public static boolean collectStoredDrops(ServerPlayer player) {
        List<ItemStack> drops = BuildToolsState.storedDropStacks(player);
        if (drops.isEmpty()) {
            fail(player, "buildtools.error.no_stored_drops");
            return false;
        }
        BuildingStorageManager.depositOrGive(player, drops);
        BuildToolsState.clearHistory(player);
        player.displayClientMessage(Component.translatable(
                "buildtools.message.stored_drops_collected",
                StoredItems.total(drops)), true);
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
                spawnCapturedEntities(operation.level(), operation.addedEntities());
                UndoSnapshot snapshot = new UndoSnapshot(operation.dimension(), operation.undoEntries(), operation.refund(), operation.producedDrops(), operation.removedEntities(), operation.addedEntities());
                int drops = StoredItems.total(operation.producedDrops());
                if (operation.trackHistory()) {
                    BuildToolsState.setUndo(operation.player(), snapshot);
                } else {
                    if (drops > 0 && !operation.player().gameMode.isCreative()) {
                        BuildingStorageManager.depositOrGive(operation.player(), operation.producedDrops());
                    }
                    BuildToolsState.clearHistory(operation.player());
                }
                Component message = Component.translatable("buildtools.message.applied", operation.undoEntries().size());
                if (drops > 0) {
                    message = operation.trackHistory()
                            ? Component.translatable("buildtools.message.applied_collected", operation.undoEntries().size(), drops)
                            : Component.translatable("buildtools.message.applied_deposited", operation.undoEntries().size(), drops);
                }
                operation.player().displayClientMessage(message, true);
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
                    ? enqueueFree(player, level, operation.positions(), operation.targets(), operation.targetBlockEntities(), operation.undo(), operation.refund(), operation.producedDrops(), operation.removedEntities(), operation.addedEntities(), operation.trackHistory())
                    : enqueue(player, level, operation.positions(), operation.targets(), operation.targetBlockEntities(), operation.undo(), operation.refund(), operation.producedDrops(), operation.removedEntities(), operation.addedEntities(), operation.trackHistory());
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
                durabilityPreviewLine(player, operation.positions().size()),
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

    private static String durabilityPreviewLine(ServerPlayer player, int blockChanges) {
        ItemStack tool = activeDurabilityTool(player);
        if (tool.isEmpty() || !tool.isDamageableItem() || player.gameMode.isCreative()) {
            return "";
        }
        int cost = durabilityCost(blockChanges);
        int remaining = remainingDurability(tool);
        return "Durability cost: " + cost + " | remaining: " + remaining;
    }

    private static String dropPreviewLine(PendingOperation operation) {
        int drops = StoredItems.total(operation.producedDrops());
        if (!operation.trackHistory()) {
            return drops <= 0 ? "No undo or redo token | no history" : "Sends " + drops + " drops to linked storage";
        }
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
            List<CompoundTag> targetBlockEntities,
            List<UndoSnapshot.Entry> undo,
            List<ItemStack> refund,
            List<ItemStack> producedDrops,
            List<CapturedEntity> removedEntities,
            List<CapturedEntity> addedEntities,
            boolean trackHistory) {
        if (positions.isEmpty() && removedEntities.isEmpty() && addedEntities.isEmpty()) {
            fail(player, "buildtools.error.no_targets");
            return false;
        }
        int durabilityCost = durabilityCost(positions.size());
        if (!hasDurabilityForOperation(player, durabilityCost)) {
            return false;
        }
        BlockCostPlan costPlan = BlockCostPlan.create(player, targets);
        if (!costPlan.canAfford()) {
            player.displayClientMessage(costPlan.missingMessage(), false);
            return false;
        }
        costPlan.consume(player);
        damageOperationTool(player, durabilityCost);
        removeCapturedEntities(level, removedEntities);
        List<ItemStack> storedDrops = StoredItems.copyOf(producedDrops);
        QUEUE.add(new BuildOperation(player, level, level.dimension(), new ArrayList<>(positions), new ArrayList<>(targets), new ArrayList<>(copyBlockEntities(targetBlockEntities)), List.copyOf(undo), StoredItems.copyOf(refund), new ArrayList<>(storedDrops), new ArrayList<>(storedDrops), List.copyOf(removedEntities), List.copyOf(addedEntities), trackHistory));
        player.displayClientMessage(Component.translatable("buildtools.message.queued", positions.size()), true);
        return true;
    }

    private static boolean enqueueFree(
            ServerPlayer player,
            ServerLevel level,
            List<BlockPos> positions,
            List<BlockState> targets,
            List<CompoundTag> targetBlockEntities,
            List<UndoSnapshot.Entry> undo,
            List<ItemStack> refund,
            List<ItemStack> producedDrops,
            List<CapturedEntity> removedEntities,
            List<CapturedEntity> addedEntities,
            boolean trackHistory) {
        if (positions.isEmpty() && removedEntities.isEmpty() && addedEntities.isEmpty()) {
            fail(player, "buildtools.error.no_targets");
            return false;
        }
        int durabilityCost = durabilityCost(positions.size());
        if (!hasDurabilityForOperation(player, durabilityCost)) {
            return false;
        }
        damageOperationTool(player, durabilityCost);
        removeCapturedEntities(level, removedEntities);
        List<ItemStack> storedDrops = StoredItems.copyOf(producedDrops);
        QUEUE.add(new BuildOperation(player, level, level.dimension(), new ArrayList<>(positions), new ArrayList<>(targets), new ArrayList<>(copyBlockEntities(targetBlockEntities)), List.copyOf(undo), StoredItems.copyOf(refund), new ArrayList<>(storedDrops), new ArrayList<>(storedDrops), List.copyOf(removedEntities), List.copyOf(addedEntities), trackHistory));
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
            CompoundTag targetBlockEntity = operation.targetBlockEntities().remove(0);
            BlockState previous = operation.level().getBlockState(pos);
            if (target.isAir()) {
                appendCapturedDrops(operation, clearBlockWithoutDrops(operation.level(), pos, Blocks.AIR.defaultBlockState()));
                applied++;
                continue;
            }
            if (!previous.isAir() && !previous.canBeReplaced()) {
                appendCapturedDrops(operation, clearBlockWithoutDrops(operation.level(), pos, Blocks.AIR.defaultBlockState()));
            }
            appendCapturedDrops(operation, restoreBlock(operation.level(), pos, target, targetBlockEntity == null ? entry.redoneBlockEntity() : targetBlockEntity));
            applied++;
        }
        return applied;
    }

    private static UndoSnapshot.Entry undoEntry(ServerLevel level, BlockPos pos, BlockState previous, BlockState redone, boolean mayRestorePrevious) {
        return undoEntry(level, pos, previous, redone, null, mayRestorePrevious);
    }

    private static UndoSnapshot.Entry undoEntry(ServerLevel level, BlockPos pos, BlockState previous, BlockState redone, CompoundTag redoneBlockEntity, boolean mayRestorePrevious) {
        return new UndoSnapshot.Entry(pos.immutable(), previous, captureBlockEntity(level, pos), redone, redoneBlockEntity, mayRestorePrevious);
    }

    private static List<ItemStack> dropsForChanges(ServerPlayer player, ServerLevel level, List<UndoSnapshot.Entry> entries) {
        List<ItemStack> drops = new ArrayList<>();
        for (UndoSnapshot.Entry entry : entries) {
            if (!shouldCollectDrops(entry.previousState(), entry.redoneState())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(entry.pos());
            ItemStack preserved = preservedBlockEntityStack(entry.previousState(), blockEntity, level);
            if (!preserved.isEmpty()) {
                drops.add(preserved);
                continue;
            }
            for (ItemStack stack : Block.getDrops(entry.previousState(), level, entry.pos(), blockEntity, player, player.getMainHandItem())) {
                if (!stack.isEmpty()) {
                    drops.add(stack.copy());
                }
            }
        }
        return StoredItems.copyOf(drops);
    }

    private static List<ItemStack> withEntityDrops(List<ItemStack> blockDrops, List<CapturedEntity> removedEntities) {
        List<ItemStack> drops = new ArrayList<>(StoredItems.copyOf(blockDrops));
        for (CapturedEntity entity : removedEntities) {
            ItemStack stack = entityDrop(entity);
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
            ItemStack contained = framedItem(entity);
            if (!contained.isEmpty()) {
                drops.add(contained);
            }
        }
        return StoredItems.copyOf(drops);
    }

    private static ItemStack entityDrop(CapturedEntity entity) {
        String id = entity.tag().getString("id");
        ItemStack stack = switch (id) {
            case "minecraft:item_frame" -> new ItemStack(Items.ITEM_FRAME);
            case "minecraft:glow_item_frame" -> new ItemStack(Items.GLOW_ITEM_FRAME);
            case "minecraft:painting" -> new ItemStack(Items.PAINTING);
            case "minecraft:armor_stand" -> new ItemStack(Items.ARMOR_STAND);
            case "minecraft:leash_knot" -> new ItemStack(Items.LEAD);
            default -> ItemStack.EMPTY;
        };
        if (!stack.isEmpty()) {
            CompoundTag data = entity.tag().copy();
            data.remove("Pos");
            data.remove("UUID");
            data.remove("UUIDMost");
            data.remove("UUIDLeast");
            stack.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        }
        return stack;
    }

    private static ItemStack framedItem(CapturedEntity entity) {
        if (!entity.tag().contains("Item", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(currentRegistryAccess(), entity.tag().getCompound("Item"));
    }

    private static net.minecraft.core.HolderLookup.Provider currentRegistryAccess() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess();
    }

    private static boolean shouldCollectDrops(BlockState previous, BlockState redone) {
        return !previous.isAir() && !previous.canBeReplaced() && !redone.is(previous.getBlock());
    }

    private static ItemStack preservedBlockEntityStack(BlockState state, BlockEntity blockEntity, ServerLevel level) {
        if (!(blockEntity instanceof Container) || state.getBlock().asItem() == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        blockEntity.saveToItem(stack, level.registryAccess());
        return stack;
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

    private static List<CompoundTag> copyBlockEntities(List<CompoundTag> tags) {
        List<CompoundTag> copied = new ArrayList<>();
        for (CompoundTag tag : tags) {
            copied.add(tag == null ? null : tag.copy());
        }
        return copied;
    }

    private static List<ItemStack> restoreBlock(ServerLevel level, BlockPos pos, BlockState state, CompoundTag blockEntityTag) {
        BlockState previous = level.getBlockState(pos);
        List<ItemStack> capturedDrops = clearBlockWithoutDrops(level, pos, state);
        if (state.isAir()) {
            level.removeBlockEntity(pos);
            return capturedDrops;
        }
        if (blockEntityTag == null) {
            return capturedDrops;
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
        return capturedDrops;
    }

    private static List<ItemStack> clearBlockWithoutDrops(ServerLevel level, BlockPos pos, BlockState replacement) {
        return captureSpawnedDrops(level, () -> {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Container container) {
                container.clearContent();
                blockEntity.setChanged();
            }
            level.removeBlockEntity(pos);
            level.setBlock(pos, replacement, 3);
        });
    }

    private static List<ItemStack> captureSpawnedDrops(ServerLevel level, Runnable action) {
        DropCapture previous = ACTIVE_DROP_CAPTURE.get();
        DropCapture capture = new DropCapture(level, new ArrayList<>());
        ACTIVE_DROP_CAPTURE.set(capture);
        try {
            action.run();
        } finally {
            if (previous == null) {
                ACTIVE_DROP_CAPTURE.remove();
            } else {
                ACTIVE_DROP_CAPTURE.set(previous);
            }
        }
        return StoredItems.copyOf(capture.drops());
    }

    private static void appendCapturedDrops(BuildOperation operation, List<ItemStack> capturedDrops) {
        operation.producedDrops().addAll(capturedDropsBeyondBudget(operation.expectedDropBudget(), capturedDrops));
    }

    private static List<ItemStack> capturedDropsBeyondBudget(List<ItemStack> budget, List<ItemStack> capturedDrops) {
        List<ItemStack> unexpected = new ArrayList<>();
        for (ItemStack captured : capturedDrops) {
            ItemStack remaining = captured.copy();
            for (ItemStack expected : budget) {
                if (remaining.isEmpty()) {
                    break;
                }
                if (!ItemStack.isSameItemSameComponents(remaining, expected)) {
                    continue;
                }
                int consumed = Math.min(remaining.getCount(), expected.getCount());
                remaining.shrink(consumed);
                expected.shrink(consumed);
            }
            if (!remaining.isEmpty()) {
                unexpected.add(remaining);
            }
        }
        budget.removeIf(ItemStack::isEmpty);
        return StoredItems.copyOf(unexpected);
    }

    private static List<CapturedEntity> captureDecorEntities(ServerLevel level, BlockPos origin, List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return List.of();
        }
        Set<BlockPos> selected = new HashSet<>(positions);
        AABB bounds = bounds(positions).inflate(2.0D);
        List<CapturedEntity> captured = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, bounds, BuildOperationEngine::isSupportedDecorEntity)) {
            if (!selected.contains(entity.blockPosition())) {
                continue;
            }
            CompoundTag tag = entity.saveWithoutId(new CompoundTag());
            tag.putString("id", EntityType.getKey(entity.getType()).toString());
            captured.add(new CapturedEntity(
                    entity.getX() - origin.getX(),
                    entity.getY() - origin.getY(),
                    entity.getZ() - origin.getZ(),
                    tag));
        }
        return List.copyOf(captured);
    }

    private static List<CapturedEntity> absoluteEntities(List<CapturedEntity> entities, BlockPos origin) {
        return entities.stream()
                .map(entity -> entity.withOffset(origin.getX() + entity.offsetX(), origin.getY() + entity.offsetY(), origin.getZ() + entity.offsetZ()))
                .toList();
    }

    private static void removeCapturedEntities(ServerLevel level, List<CapturedEntity> entities) {
        for (CapturedEntity captured : entities) {
            AABB bounds = new AABB(
                    captured.offsetX() - 1.0D,
                    captured.offsetY() - 1.0D,
                    captured.offsetZ() - 1.0D,
                    captured.offsetX() + 1.0D,
                    captured.offsetY() + 1.0D,
                    captured.offsetZ() + 1.0D);
            for (Entity entity : level.getEntities((Entity) null, bounds, BuildOperationEngine::isSupportedDecorEntity)) {
                if (matchesCapturedEntity(entity, captured)) {
                    entity.discard();
                }
            }
        }
    }

    private static boolean matchesCapturedEntity(Entity entity, CapturedEntity captured) {
        String capturedType = captured.tag().getString("id");
        return EntityType.getKey(entity.getType()).toString().equals(capturedType)
                && entity.distanceToSqr(captured.offsetX(), captured.offsetY(), captured.offsetZ()) < 0.25D;
    }

    private static void spawnCapturedEntities(ServerLevel level, List<CapturedEntity> entities) {
        for (CapturedEntity captured : entities) {
            CompoundTag tag = captured.tag().copy();
            tag.remove("UUID");
            tag.remove("UUIDMost");
            tag.remove("UUIDLeast");
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(captured.offsetX()));
            pos.add(DoubleTag.valueOf(captured.offsetY()));
            pos.add(DoubleTag.valueOf(captured.offsetZ()));
            tag.put("Pos", pos);
            Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> loaded);
            if (entity != null) {
                level.addFreshEntity(entity);
            }
        }
    }

    private static AABB bounds(List<BlockPos> positions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private static boolean isSupportedDecorEntity(Entity entity) {
        return entity instanceof ItemFrame
                || entity instanceof GlowItemFrame
                || entity instanceof Painting
                || entity instanceof ArmorStand
                || entity instanceof LeashFenceKnotEntity;
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
        return true;
    }

    private static BlockState materialState(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState();
        }
        if (stack.is(Items.WATER_BUCKET)) {
            return Blocks.WATER.defaultBlockState();
        }
        if (stack.is(Items.LAVA_BUCKET)) {
            return Blocks.LAVA.defaultBlockState();
        }
        return null;
    }

    private static void addUndoRefund(List<ItemStack> refund, BlockState state) {
        if (state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
            return;
        }
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (!stack.isEmpty() && !stack.is(Items.AIR)) {
            refund.add(stack);
        }
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
        return (state.is(Blocks.WATER) || state.is(Blocks.LAVA))
                || !state.isAir()
                && state.getBlock().asItem() != Blocks.AIR.asItem();
    }

    private static boolean hasHistoryItems(ServerPlayer player) {
        return hasInventoryItem(player, com.abhil.buildtools.registry.ModItems.UNDO_TOKEN.get())
                || hasInventoryItem(player, com.abhil.buildtools.registry.ModItems.REDO_TOKEN.get());
    }

    private static int durabilityCost(int blockChanges) {
        return 1 + Math.max(0, blockChanges) / 400;
    }

    private static boolean hasDurabilityForOperation(ServerPlayer player, int cost) {
        ItemStack tool = activeDurabilityTool(player);
        if (tool.isEmpty() || !tool.isDamageableItem() || player.gameMode.isCreative()) {
            return true;
        }
        int remaining = remainingDurability(tool);
        if (cost <= remaining) {
            return true;
        }
        fail(player, Component.translatable("buildtools.error.not_enough_durability", cost, remaining));
        return false;
    }

    private static void damageOperationTool(ServerPlayer player, int cost) {
        if (player.gameMode.isCreative()) {
            return;
        }
        if (!damageOperationTool(player, InteractionHand.MAIN_HAND, cost)) {
            damageOperationTool(player, InteractionHand.OFF_HAND, cost);
        }
    }

    private static boolean damageOperationTool(ServerPlayer player, InteractionHand hand, int cost) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isDurabilityOperationTool(stack) || !stack.isDamageableItem()) {
            return false;
        }
        stack.hurtAndBreak(cost, player.serverLevel(), player,
                broken -> player.onEquippedItemBroken(broken, LivingEntity.getSlotForHand(hand)));
        return true;
    }

    private static ItemStack activeDurabilityTool(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (isDurabilityOperationTool(mainHand)) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        return isDurabilityOperationTool(offHand) ? offHand : ItemStack.EMPTY;
    }

    private static boolean isDurabilityOperationTool(ItemStack stack) {
        if (!ToolProfile.isBuildTool(stack)) {
            return false;
        }
        return switch (ToolProfile.from(stack)) {
            case BUILDER, ADVANCED_BUILDER, BRUSH, BREAKER, TROWEL -> true;
            default -> false;
        };
    }

    private static int remainingDurability(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
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
            List<CompoundTag> targetBlockEntities,
            List<UndoSnapshot.Entry> undo,
            List<ItemStack> refund,
            List<ItemStack> producedDrops,
            List<CapturedEntity> removedEntities,
            List<CapturedEntity> addedEntities,
            boolean trackHistory,
            boolean free,
            String signature) {
    }

    private record DropCapture(ServerLevel level, List<ItemStack> drops) {
    }
}
