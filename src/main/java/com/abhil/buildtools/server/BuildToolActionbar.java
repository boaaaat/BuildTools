package com.abhil.buildtools.server;

import com.abhil.buildtools.config.BuildToolsConfig;
import com.abhil.buildtools.network.ToolStatusPayload;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.shape.BuildMode;
import com.abhil.buildtools.shape.Selection;
import com.abhil.buildtools.shape.ShapeGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BuildToolActionbar {
    private static final int UPDATE_INTERVAL_TICKS = 10;

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
        if (held.isEmpty()) {
            PacketDistributor.sendToPlayer(player, ToolStatusPayload.hidden());
            return;
        }

        Component message = messageFor(player, held);
        if (message != null) {
            sendStatus(player, held, message);
        }
    }

    private static void sendStatus(ServerPlayer player, ItemStack held, Component message) {
        String[] parts = message.getString().split("\\s*\\|\\s*");
        String title = parts.length == 0 ? held.getHoverName().getString() : parts[0];
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isBlank()) {
                lines.add(parts[i]);
            }
        }
        PacketDistributor.sendToPlayer(player, new ToolStatusPayload(true, title, lines, accentColor(held)));
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

    private static Component messageFor(ServerPlayer player, ItemStack held) {
        if (held.is(ModItems.SELECTION_STAFF.get())) {
            return selectionMessage(player);
        }
        if (held.is(ModItems.ADVANCED_SELECTION_STAFF.get())) {
            return Component.literal("Advanced Selection Staff | Points: " + BuildToolsState.advancedPointCount(player) + " | " + selectionMessage(player).getString());
        }
        if (held.is(ModItems.BUILDER_WAND.get())) {
            return builderMessage(player);
        }
        if (held.is(ModItems.ADVANCED_BUILDER_WAND.get())) {
            return advancedBuilderMessage(player);
        }
        if (held.is(ModItems.BUILDER_BRUSH.get())) {
            return brushMessage(player);
        }
        if (held.is(ModItems.AREA_BREAKER.get())) {
            return breakerMessage(player);
        }
        if (held.is(ModItems.BLUEPRINT_TROWEL.get())) {
            return trowelMessage(player);
        }
        if (held.is(ModItems.UNDO_TOKEN.get())) {
            return historyMessage("Undo", BuildToolsState.peekUndo(player).orElse(null), player);
        }
        if (held.is(ModItems.REDO_TOKEN.get())) {
            return historyMessage("Redo", BuildToolsState.peekRedo(player).orElse(null), player);
        }
        return null;
    }

    private static Component selectionMessage(ServerPlayer player) {
        SelectionStats stats = selectionStats(player);
        if (!stats.valid()) {
            return Component.literal("Selection Staff | " + stats.status()).withStyle(ChatFormatting.AQUA);
        }
        BuildMode mode = BuildToolsState.mode(player);
        return Component.literal("Selection Staff | " + stats.shapeName()
                + " | Size: " + stats.dimensions()
                + " | Area: " + stats.total()
                + " | Air: " + stats.air()
                + " | Blocks: " + stats.solid()
                + " | " + modeName(mode) + ": " + stats.targetsFor(mode)
                + limitSuffix(stats));
    }

    private static Component builderMessage(ServerPlayer player) {
        SelectionStats stats = selectionStats(player);
        if (!stats.valid()) {
            return Component.literal("Builder Wand | " + stats.status()).withStyle(ChatFormatting.YELLOW);
        }

        ItemStack source = player.getOffhandItem();
        if (!(source.getItem() instanceof BlockItem blockItem)) {
            return Component.literal("Builder Wand | " + stats.shapeName()
                    + " | Size: " + stats.dimensions()
                    + " | Area: " + stats.total()
                    + " | Put the block to place in your offhand").withStyle(ChatFormatting.YELLOW);
        }

        BuildMode mode = BuildToolsState.mode(player);
        int targets = stats.targetsFor(mode);
        List<BlockState> targetStates = new ArrayList<>(targets);
        BlockState target = blockItem.getBlock().defaultBlockState();
        for (int i = 0; i < targets; i++) {
            targetStates.add(target);
        }
        BlockCostPlan costPlan = BlockCostPlan.create(player, targetStates);
        return Component.literal("Builder Wand | " + modeName(mode)
                + " " + source.getHoverName().getString()
                + " | Size: " + stats.dimensions()
                + " | Will place: " + targets
                + " | Air: " + stats.air()
                + " | Blocks: " + stats.solid()
                + " | Replace target: " + BuildToolsState.replaceTarget(player).getBlock().getName().getString()
                + costSuffix(player, costPlan)
                + limitSuffix(stats));
    }

    private static Component advancedBuilderMessage(ServerPlayer player) {
        Component base = builderMessage(player);
        int paletteSize = BuildToolsState.paletteEntries(player).size();
        String materialMode = BuildToolsState.paletteMode(player).displayName().getString();
        return Component.literal("Advanced " + base.getString() + " | Palette: " + paletteSize + " | Material mode: " + materialMode + " | Ghost/plan ready in menu");
    }

    private static Component brushMessage(ServerPlayer player) {
        ItemStack source = player.getOffhandItem();
        String offhand = source.getItem() instanceof BlockItem ? source.getHoverName().getString() : "missing offhand block";
        return Component.literal("Builder Brush | " + BuildToolsState.brushMode(player).displayName().getString()
                + " | Radius: " + BuildToolsState.brushRadius(player)
                + " | Block: " + offhand
                + " | Replace target: " + BuildToolsState.replaceTarget(player).getBlock().getName().getString()
                + " | Sneak right-click: menu");
    }

    private static Component breakerMessage(ServerPlayer player) {
        SelectionStats stats = selectionStats(player);
        if (!stats.valid()) {
            return Component.literal("Area Breaker | " + stats.status()).withStyle(ChatFormatting.RED);
        }
        return Component.literal("Area Breaker | " + stats.shapeName()
                + " | Size: " + stats.dimensions()
                + " | Area: " + stats.total()
                + " | Air: " + stats.air()
                + " | Will break: " + stats.solid()
                + " | Drops normal loot"
                + limitSuffix(stats));
    }

    private static Component trowelMessage(ServerPlayer player) {
        SelectionStats stats = selectionStats(player);
        Blueprint blueprint = BuildToolsState.blueprint(player).orElse(null);
        String saved = blueprint == null ? "No blueprint" : "Saved: " + blueprint.entries().size();

        if (player.isShiftKeyDown()) {
            if (blueprint == null || blueprint.entries().isEmpty()) {
                return Component.literal("Blueprint Trowel | Paste: no blueprint copied").withStyle(ChatFormatting.YELLOW);
            }
            BlockCostPlan costPlan = BlockCostPlan.create(player, blueprint.entries().stream().map(Blueprint.Entry::state).toList());
            String confirm = BuildToolsState.pendingPasteOrigin(player).isPresent() ? " | Click same spot to confirm" : " | Click block face to preview";
            return Component.literal("Blueprint Trowel | Paste | " + saved + costSuffix(player, costPlan) + confirm);
        }

        if (!stats.valid()) {
            return Component.literal("Blueprint Trowel | Copy | " + stats.status() + " | " + saved).withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal("Blueprint Trowel | Copy | Area: " + stats.total()
                + " | Size: " + stats.dimensions()
                + " | Air skipped: " + stats.air()
                + " | Copy blocks: " + stats.solid()
                + " | " + saved);
    }

    private static Component historyMessage(String label, UndoSnapshot snapshot, ServerPlayer player) {
        if (snapshot == null) {
            return Component.literal(label + " Token | No operation ready").withStyle(ChatFormatting.GRAY);
        }
        String modeNote = player.gameMode.isCreative() ? " | Creative: no token cost or block refund" : "";
        int count = label.equals("Undo") ? BuildToolsState.undoCount(player) : BuildToolsState.redoCount(player);
        return Component.literal(label + " Token | Ready: " + snapshot.entries().size() + " changes | History: " + count + modeNote);
    }

    private static SelectionStats selectionStats(ServerPlayer player) {
        Selection selection = BuildToolsState.selection(player);
        if (!selection.isComplete() || selection.dimension() == null) {
            String first = selection.firstOptional().isPresent() ? "Pos 1 set" : "Pos 1 missing";
            String second = selection.secondOptional().isPresent() ? "Pos 2 set" : "Pos 2 missing";
            return SelectionStats.invalid(first + ", " + second);
        }
        if (!selection.dimension().equals(player.level().dimension())) {
            return SelectionStats.invalid("Selection is in another dimension");
        }

        List<BlockPos> positions = ShapeGenerator.generate(selection);
        if (positions.isEmpty()) {
            return SelectionStats.invalid("Selected shape is empty");
        }

        int air = 0;
        int fillTargets = 0;
        int replaceTargets = 0;
        BlockState replaceMatch = BuildToolsState.replaceTarget(player);
        for (BlockPos pos : positions) {
            BlockState state = player.level().getBlockState(pos);
            if (state.isAir()) {
                air++;
            }
            if (state.canBeReplaced()) {
                fillTargets++;
            }
            if (BuildToolsState.matchesReplaceTargets(player, state, replaceMatch)) {
                replaceTargets++;
            }
        }

        return new SelectionStats(
                true,
                "",
                selection.shape().displayName().getString(),
                dimensions(selection),
                positions.size(),
                air,
                positions.size() - air,
                fillTargets,
                replaceTargets,
                positions.size() > BuildToolsConfig.MAX_OPERATION_VOLUME.get());
    }

    private static String dimensions(Selection selection) {
        int width = Math.abs(selection.second().getX() - selection.first().getX()) + 1;
        int height = Math.abs(selection.second().getY() - selection.first().getY()) + 1;
        int depth = Math.abs(selection.second().getZ() - selection.first().getZ()) + 1;
        return width + "x" + height + "x" + depth;
    }

    private static String modeName(BuildMode mode) {
        return mode.displayName().getString();
    }

    private static String costSuffix(ServerPlayer player, BlockCostPlan costPlan) {
        int required = costPlan.required().values().stream().mapToInt(Integer::intValue).sum();
        if (player.gameMode.isCreative()) {
            return " | Need: " + required + " (creative)";
        }
        int missing = costPlan.missing().values().stream().mapToInt(Integer::intValue).sum();
        if (missing > 0) {
            return " | Need: " + required + " | Missing: " + missing + " " + compactMissing(costPlan.missing());
        }
        return " | Need: " + required + " | Materials ready";
    }

    private static String limitSuffix(SelectionStats stats) {
        return stats.tooLarge() ? " | Over limit: " + BuildToolsConfig.MAX_OPERATION_VOLUME.get() : "";
    }

    private static String compactMissing(Map<ItemStackKey, Integer> missing) {
        if (missing.isEmpty()) {
            return "";
        }
        Map.Entry<ItemStackKey, Integer> first = missing.entrySet().iterator().next();
        int extraTypes = missing.size() - 1;
        return "(" + first.getValue() + "x " + first.getKey().stack(1).getHoverName().getString()
                + (extraTypes > 0 ? " +" + extraTypes + " more" : "") + ")";
    }

    private record SelectionStats(
            boolean valid,
            String status,
            String shapeName,
            String dimensions,
            int total,
            int air,
            int solid,
            int fillTargets,
            int replaceTargets,
            boolean tooLarge) {
        private static SelectionStats invalid(String status) {
            return new SelectionStats(false, status, "", "", 0, 0, 0, 0, 0, false);
        }

        private int targetsFor(BuildMode mode) {
            return switch (mode) {
                case FILL -> fillTargets;
                case REPLACE -> replaceTargets;
                case OVERWRITE -> total;
            };
        }
    }
}
