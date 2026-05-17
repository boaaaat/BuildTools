package com.abhil.buildtools.server;

import com.abhil.buildtools.config.BuildToolsConfig;
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

        BlockState replaceMatch = level.getBlockState(selection.first());
        for (BlockPos pos : generated) {
            BlockState previous = level.getBlockState(pos);
            if (!canTouch(player, level, pos, previous)) {
                return false;
            }
            if (mode == BuildMode.FILL && !previous.canBeReplaced()) {
                continue;
            }
            if (mode == BuildMode.REPLACE && !previous.is(replaceMatch.getBlock())) {
                continue;
            }
            positions.add(pos.immutable());
            targets.add(target);
            boolean restore = previous.isAir() || mode == BuildMode.FILL;
            undo.add(new UndoSnapshot.Entry(pos.immutable(), previous, restore));
            refund.merge(new ItemStackKey(target.getBlock().asItem()), 1, Integer::sum);
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
            undo.add(new UndoSnapshot.Entry(pos.immutable(), previous, previous.isAir() || previous.canBeReplaced()));
            refund.merge(new ItemStackKey(entry.state().getBlock().asItem()), 1, Integer::sum);
        }
        return enqueue(player, level, positions, targets, undo, refund);
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
        for (Map.Entry<ItemStackKey, Integer> refund : snapshot.refund().entrySet()) {
            ItemStack stack = refund.getKey().stack(refund.getValue());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.displayClientMessage(Component.translatable("buildtools.message.undo", snapshot.entries().size()), true);
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

    private static int applyBatch(BuildOperation operation, int budget) {
        int applied = 0;
        while (applied < budget && !operation.positions().isEmpty()) {
            BlockPos pos = operation.positions().remove(0);
            BlockState target = operation.targetStates().remove(0);
            BlockState previous = operation.level().getBlockState(pos);
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
