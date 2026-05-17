package com.abhil.buildtools.server;

import com.abhil.buildtools.config.BuildToolsConfig;
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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class BuildOperationEngine {
    private static final Queue<BuildOperation> QUEUE = new ArrayDeque<>();

    private BuildOperationEngine() {
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
            positions.add(pos.immutable());
            targets.add(target);
            boolean restore = previous.isAir() || mode == BuildMode.FILL;
            undo.add(new UndoSnapshot.Entry(pos.immutable(), previous, target, restore));
            refund.merge(new ItemStackKey(target.getBlock().asItem()), 1, Integer::sum);
        }

        return enqueue(player, level, positions, targets, undo, refund);
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
            undo.add(new UndoSnapshot.Entry(pos.immutable(), previous, Blocks.AIR.defaultBlockState(), false));
        }

        return enqueueFree(player, level, positions, targets, undo, Map.of());
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
            entries.add(new BuildPlan.Entry(pos.immutable(), target));
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
            undo.add(new UndoSnapshot.Entry(entry.pos().immutable(), previous, entry.state(), previous.isAir() || previous.canBeReplaced()));
            refund.merge(new ItemStackKey(entry.state().getBlock().asItem()), 1, Integer::sum);
        }
        return enqueue(player, level, positions, targets, undo, refund);
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
        List<BlockPos> generated = brushMode == BrushMode.CYLINDER
                ? ShapeGenerator.cylinder(origin.offset(-2, 0, -2), origin.offset(2, 0, 2))
                : ShapeGenerator.sphere(origin.offset(-2, -2, -2), origin.offset(2, 2, 2), true);
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
            undo.add(new UndoSnapshot.Entry(pos.immutable(), previous, target, previous.isAir() || previous.canBeReplaced()));
            refund.merge(new ItemStackKey(target.getBlock().asItem()), 1, Integer::sum);
        }
        return enqueue(player, level, positions, targets, undo, refund);
    }

    private static boolean executeSmoothBrush(ServerPlayer player, BlockPos origin, BlockState fillState) {
        ServerLevel level = player.serverLevel();
        List<Integer> heights = new ArrayList<>();
        Map<BlockPos, Integer> tops = new LinkedHashMap<>();
        for (int x = origin.getX() - 2; x <= origin.getX() + 2; x++) {
            for (int z = origin.getZ() - 2; z <= origin.getZ() + 2; z++) {
                BlockPos top = findTopSolid(level, x, z, origin.getY() - 4, origin.getY() + 4);
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
                        undo.add(new UndoSnapshot.Entry(pos, previous, Blocks.AIR.defaultBlockState(), false));
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
                        undo.add(new UndoSnapshot.Entry(pos, previous, fillState, true));
                        refund.merge(new ItemStackKey(fillState.getBlock().asItem()), 1, Integer::sum);
                    }
                }
            }
        }
        return enqueue(player, level, positions, targets, undo, refund);
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
            undo.add(new UndoSnapshot.Entry(pos.immutable(), previous, entry.state(), previous.isAir() || previous.canBeReplaced()));
            refund.merge(new ItemStackKey(entry.state().getBlock().asItem()), 1, Integer::sum);
        }
        return enqueue(player, level, positions, targets, undo, refund);
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
        UndoSnapshot snapshot = BuildToolsState.takeUndo(player).orElse(null);
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
            if (entry.mayRestorePrevious()) {
                level.setBlock(entry.pos(), entry.previousState(), 3);
            } else {
                level.setBlock(entry.pos(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        if (!player.gameMode.isCreative()) {
            for (Map.Entry<ItemStackKey, Integer> refund : snapshot.refund().entrySet()) {
                ItemStack stack = refund.getKey().stack(refund.getValue());
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
        BuildToolsState.setRedo(player, snapshot);
        player.displayClientMessage(Component.translatable("buildtools.message.undo", snapshot.entries().size()), true);
        return true;
    }

    public static boolean redo(ServerPlayer player) {
        UndoSnapshot snapshot = BuildToolsState.takeRedo(player).orElse(null);
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
        for (UndoSnapshot.Entry entry : snapshot.entries()) {
            BlockState previous = level.getBlockState(entry.pos());
            if (!canTouch(player, level, entry.pos(), previous)) {
                return false;
            }
            positions.add(entry.pos().immutable());
            targets.add(entry.redoneState());
        }

        return enqueue(player, level, positions, targets, snapshot.entries(), snapshot.refund());
    }

    public static void tick() {
        int batch = BuildToolsConfig.BATCH_SIZE.get();
        while (batch > 0 && !QUEUE.isEmpty()) {
            BuildOperation operation = QUEUE.peek();
            int applied = applyBatch(operation, batch);
            batch -= applied;
            if (operation.positions().isEmpty()) {
                QUEUE.remove();
                BuildToolsState.setUndo(operation.player(), new UndoSnapshot(operation.dimension(), operation.undoEntries(), operation.refund()));
                operation.player().displayClientMessage(Component.translatable("buildtools.message.applied", operation.undoEntries().size()), true);
            } else if (applied == 0) {
                QUEUE.remove();
            }
        }
    }

    private static boolean enqueue(
            ServerPlayer player,
            ServerLevel level,
            List<BlockPos> positions,
            List<BlockState> targets,
            List<UndoSnapshot.Entry> undo,
            Map<ItemStackKey, Integer> refund) {
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
        QUEUE.add(new BuildOperation(player, level, level.dimension(), new ArrayList<>(positions), new ArrayList<>(targets), List.copyOf(undo), Map.copyOf(refund)));
        player.displayClientMessage(Component.translatable("buildtools.message.queued", positions.size()), true);
        return true;
    }

    private static boolean enqueueFree(
            ServerPlayer player,
            ServerLevel level,
            List<BlockPos> positions,
            List<BlockState> targets,
            List<UndoSnapshot.Entry> undo,
            Map<ItemStackKey, Integer> refund) {
        if (positions.isEmpty()) {
            fail(player, "buildtools.error.no_targets");
            return false;
        }
        QUEUE.add(new BuildOperation(player, level, level.dimension(), new ArrayList<>(positions), new ArrayList<>(targets), List.copyOf(undo), Map.copyOf(refund)));
        player.displayClientMessage(Component.translatable("buildtools.message.queued", positions.size()), true);
        return true;
    }

    private static int applyBatch(BuildOperation operation, int budget) {
        int applied = 0;
        while (applied < budget && !operation.positions().isEmpty()) {
            BlockPos pos = operation.positions().remove(0);
            BlockState target = operation.targetStates().remove(0);
            BlockState previous = operation.level().getBlockState(pos);
            if (target.isAir()) {
                operation.level().destroyBlock(pos, true, operation.player());
                applied++;
                continue;
            }
            if (!previous.isAir() && !previous.canBeReplaced()) {
                operation.level().destroyBlock(pos, true, operation.player());
            }
            operation.level().setBlock(pos, target, 3);
            applied++;
        }
        return applied;
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
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null || !previous.getFluidState().isEmpty()) {
            fail(player, "buildtools.error.protected_state");
            return false;
        }
        return true;
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

    private static void fail(ServerPlayer player, String key) {
        fail(player, Component.translatable(key));
    }

    private static void fail(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }
}
