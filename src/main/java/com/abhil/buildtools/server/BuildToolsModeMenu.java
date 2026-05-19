package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModMenus;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.shape.BrushMode;
import com.abhil.buildtools.shape.BuildMode;
import com.abhil.buildtools.shape.SelectionShape;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class BuildToolsModeMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 27;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ToolProfile profile;
    private final ServerPlayer owner;

    public BuildToolsModeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    }

    private BuildToolsModeMenu(int containerId, Inventory inventory, ServerPlayer owner) {
        super(ModMenus.MODE_MENU.get(), containerId);
        this.owner = owner;
        this.profile = owner == null ? ToolProfile.BUILDER : BuildToolsState.activeToolProfile(owner);
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new BuildToolsModeMenu(containerId, inventory, player),
                menuTitle(player)));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < MENU_SIZE && player instanceof ServerPlayer serverPlayer) {
            if (handleClick(serverPlayer, slotId)) {
                populateMenuItems();
                return;
            }
        }
        if (slotId >= MENU_SIZE) {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private void addMenuSlots() {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = column + row * 9;
                addSlot(new FakeSlot(menuItems, slot, 8 + column * 18, 18 + row * 18));
            }
        }
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    private void populateMenuItems() {
        menuItems.clearContent();
        switch (profile) {
            case SELECTION, ADVANCED_SELECTION -> populateSelectionMenu();
            case BRUSH -> populateBrushMenu();
            case BREAKER -> populateBreakerMenu();
            case TROWEL -> populateTrowelMenu();
            case UNDO -> populateHistoryMenu(true);
            case REDO -> populateHistoryMenu(false);
            default -> populateBuilderMenu();
        }
    }

    private void populateHistoryMenu(boolean undo) {
        List<UndoSnapshot> history = owner == null ? List.of()
                : undo ? BuildToolsState.undoHistory(owner) : BuildToolsState.redoHistory(owner);
        Map<ItemStackKey, Integer> storedDrops = owner == null ? Map.of() : BuildToolsState.storedDrops(owner);
        if (history.isEmpty()) {
            menuItems.setItem(0, emptyHistoryItem(undo));
            menuItems.setItem(25, utilityItem(Items.BARRIER, "buildtools.menu.clear_history", "buildtools.menu.clear_history.description"));
            menuItems.setItem(26, collectDropsItem(storedDrops));
            return;
        }
        for (int i = 0; i < Math.min(MENU_SIZE - 1, history.size()); i++) {
            menuItems.setItem(i, historyItem(undo, i, history.get(i)));
        }
        menuItems.setItem(25, utilityItem(Items.BARRIER, "buildtools.menu.clear_history", "buildtools.menu.clear_history.description"));
        menuItems.setItem(26, collectDropsItem(storedDrops));
    }

    private void populateBuilderMenu() {
        menuItems.setItem(0, modeItem(Items.LIME_STAINED_GLASS, BuildMode.FILL));
        menuItems.setItem(1, modeItem(Items.ORANGE_STAINED_GLASS, BuildMode.REPLACE));
        menuItems.setItem(2, modeItem(Items.RED_STAINED_GLASS, BuildMode.OVERWRITE));
        menuItems.setItem(3, utilityItem(Items.BARRIER, "buildtools.menu.clear_selection", "buildtools.menu.clear_selection.description"));
        menuItems.setItem(4, utilityItem(Items.WRITABLE_BOOK, "buildtools.menu.save_preset", "buildtools.menu.save_preset.description"));
        menuItems.setItem(5, utilityItem(Items.BOOK, "buildtools.menu.presets", "buildtools.menu.presets.description"));
        menuItems.setItem(6, utilityItem(Items.CHEST, "buildtools.menu.material_checklist", "buildtools.menu.material_checklist.description"));
        menuItems.setItem(8, utilityItem(Items.KNOWLEDGE_BOOK, "buildtools.menu.help", "buildtools.menu.help.description"));
        populateShapes(9);
    }

    private void populateSelectionMenu() {
        boolean shared = owner != null && BuildToolsState.selectionVisibleToOthers(owner);
        menuItems.setItem(0, NudgeMenuItems.item(owner, Direction.WEST, "buildtools.menu.nudge.description"));
        menuItems.setItem(1, NudgeMenuItems.item(owner, Direction.EAST, "buildtools.menu.nudge.description"));
        menuItems.setItem(2, NudgeMenuItems.item(owner, Direction.DOWN, "buildtools.menu.nudge.description"));
        menuItems.setItem(3, NudgeMenuItems.item(owner, Direction.UP, "buildtools.menu.nudge.description"));
        menuItems.setItem(4, NudgeMenuItems.item(owner, Direction.NORTH, "buildtools.menu.nudge.description"));
        menuItems.setItem(5, NudgeMenuItems.item(owner, Direction.SOUTH, "buildtools.menu.nudge.description"));
        menuItems.setItem(6, utilityItem(Items.BARRIER, "buildtools.menu.clear_selection", "buildtools.menu.clear_selection.description"));
        menuItems.setItem(7, utilityItem(Items.ENDER_EYE, "buildtools.menu.rotate_selection", "buildtools.menu.rotate_selection.description"));
        menuItems.setItem(8, utilityItem(
                shared ? Items.ENDER_EYE : Items.ENDER_PEARL,
                "buildtools.menu.selection_visibility",
                "buildtools.menu.selection_visibility.description",
                shared));
        populateShapes(9);
        menuItems.setItem(22, utilityItem(Items.WRITABLE_BOOK, "buildtools.menu.save_preset", "buildtools.menu.save_preset.description"));
        menuItems.setItem(23, utilityItem(Items.BOOK, "buildtools.menu.presets", "buildtools.menu.presets.description"));
        menuItems.setItem(24, utilityItem(Items.KNOWLEDGE_BOOK, "buildtools.menu.help", "buildtools.menu.help.description"));
    }

    private void populateBrushMenu() {
        BrushMode brushMode = owner == null ? BrushMode.SPHERE : BuildToolsState.brushMode(owner);
        int radius = owner == null ? 2 : BuildToolsState.brushRadius(owner);
        menuItems.setItem(0, brushModeItem(Items.SLIME_BALL, BrushMode.SPHERE, brushMode));
        menuItems.setItem(1, brushModeItem(Items.SCAFFOLDING, BrushMode.CYLINDER, brushMode));
        menuItems.setItem(2, brushModeItem(Items.GRASS_BLOCK, BrushMode.SMOOTH, brushMode));
        menuItems.setItem(3, brushModeItem(Items.ORANGE_DYE, BrushMode.REPLACE, brushMode));
        menuItems.setItem(9, utilityItem(Items.LIGHT_BLUE_DYE, "buildtools.menu.brush_smaller", "buildtools.menu.brush_radius.description"));
        menuItems.setItem(10, named(Items.PAINTING, Component.translatable("buildtools.menu.brush_radius", radius)));
        menuItems.setItem(11, utilityItem(Items.BLUE_DYE, "buildtools.menu.brush_larger", "buildtools.menu.brush_radius.description"));
    }

    private void populateBreakerMenu() {
        menuItems.setItem(0, utilityItem(Items.BARRIER, "buildtools.menu.clear_selection", "buildtools.menu.clear_selection.description"));
        menuItems.setItem(1, utilityItem(Items.ENDER_EYE, "buildtools.menu.rotate_selection", "buildtools.menu.rotate_selection.description"));
        menuItems.setItem(2, utilityItem(Items.WRITABLE_BOOK, "buildtools.menu.save_preset", "buildtools.menu.save_preset.description"));
        menuItems.setItem(3, utilityItem(Items.BOOK, "buildtools.menu.presets", "buildtools.menu.presets.description"));
        menuItems.setItem(4, breakerPresetItem(Items.IRON_PICKAXE, AreaBreakerPreset.NORMAL));
        menuItems.setItem(5, breakerPresetItem(Items.WHEAT_SEEDS, AreaBreakerPreset.CLEAR_SNOW_CROPS));
        menuItems.setItem(8, utilityItem(Items.KNOWLEDGE_BOOK, "buildtools.menu.help", "buildtools.menu.help.description"));
        populateShapes(9);
    }

    private void populateTrowelMenu() {
        menuItems.setItem(0, utilityItem(Items.PAPER, "buildtools.menu.copy_blueprint", "buildtools.menu.copy_blueprint.description"));
        menuItems.setItem(1, utilityItem(Items.BOOKSHELF, "buildtools.menu.blueprints", "buildtools.menu.blueprints.description"));
        menuItems.setItem(2, utilityItem(Items.ENDER_PEARL, "buildtools.menu.paste_blueprint_here", "buildtools.menu.paste_blueprint_here.description"));
        menuItems.setItem(3, utilityItem(Items.ENDER_EYE, "buildtools.menu.paste_blueprint_selection", "buildtools.menu.paste_blueprint_selection.description"));
        menuItems.setItem(4, utilityItem(Items.LIME_DYE, "buildtools.menu.confirm_paste", "buildtools.menu.confirm_paste.description"));
        menuItems.setItem(5, utilityItem(Items.BARRIER, "buildtools.menu.cancel_paste", "buildtools.menu.cancel_paste.description"));
        menuItems.setItem(6, utilityItem(Items.CLOCK, "buildtools.menu.rotate_blueprint", "buildtools.menu.rotate_blueprint.description"));
        menuItems.setItem(7, utilityItem(Items.IRON_BARS, "buildtools.menu.mirror_blueprint_x", "buildtools.menu.mirror_blueprint.description"));
        menuItems.setItem(8, utilityItem(Items.CHAIN, "buildtools.menu.mirror_blueprint_z", "buildtools.menu.mirror_blueprint.description"));
        menuItems.setItem(16, utilityItem(Items.CHEST, "buildtools.menu.material_checklist", "buildtools.menu.material_checklist.description"));
        menuItems.setItem(17, utilityItem(Items.WRITABLE_BOOK, "buildtools.menu.clear_selection", "buildtools.menu.clear_selection.description"));
        menuItems.setItem(18, NudgeMenuItems.item(owner, Direction.WEST, "buildtools.menu.nudge_paste.description"));
        menuItems.setItem(19, NudgeMenuItems.item(owner, Direction.EAST, "buildtools.menu.nudge_paste.description"));
        menuItems.setItem(20, NudgeMenuItems.item(owner, Direction.DOWN, "buildtools.menu.nudge_paste.description"));
        menuItems.setItem(21, NudgeMenuItems.item(owner, Direction.UP, "buildtools.menu.nudge_paste.description"));
        menuItems.setItem(22, NudgeMenuItems.item(owner, Direction.NORTH, "buildtools.menu.nudge_paste.description"));
        menuItems.setItem(23, NudgeMenuItems.item(owner, Direction.SOUTH, "buildtools.menu.nudge_paste.description"));
        menuItems.setItem(26, utilityItem(Items.KNOWLEDGE_BOOK, "buildtools.menu.help", "buildtools.menu.help.description"));
    }

    private void populateShapes(int startSlot) {
        SelectionShape[] shapes = visibleShapes();
        for (int i = 0; i < shapes.length; i++) {
            ItemStack stack = shapeIcon(shapes[i]);
            stack.set(DataComponents.CUSTOM_NAME, shapes[i].displayName());
            setSelected(stack, owner != null && BuildToolsState.selectionShape(owner) == shapes[i]);
            menuItems.setItem(startSlot + i, stack);
        }
    }

    private boolean handleClick(ServerPlayer player, int slotId) {
        return switch (profile) {
            case SELECTION, ADVANCED_SELECTION -> handleSelectionClick(player, slotId);
            case BRUSH -> handleBrushClick(player, slotId);
            case BREAKER -> handleBreakerClick(player, slotId);
            case TROWEL -> handleTrowelClick(player, slotId);
            case UNDO -> {
                if (slotId == 26 && BuildOperationEngine.collectStoredDrops(player)) {
                    yield true;
                }
                if (slotId == 25) {
                    BuildToolsState.clearHistory(player);
                    yield true;
                }
                if (slotId == 0 && BuildToolsState.undoCount(player) > 0) {
                    if (BuildOperationEngine.undo(player)) {
                        damageHeldHistoryToken(player, ModItems.UNDO_TOKEN.get());
                        yield true;
                    }
                }
                yield false;
            }
            case REDO -> {
                if (slotId == 26 && BuildOperationEngine.collectStoredDrops(player)) {
                    yield true;
                }
                if (slotId == 25) {
                    BuildToolsState.clearHistory(player);
                    yield true;
                }
                if (slotId == 0 && BuildToolsState.redoCount(player) > 0) {
                    if (BuildOperationEngine.redo(player)) {
                        damageHeldHistoryToken(player, ModItems.REDO_TOKEN.get());
                        yield true;
                    }
                }
                yield false;
            }
            default -> handleBuilderClick(player, slotId);
        };
    }

    private static void damageHeldHistoryToken(ServerPlayer player, Item item) {
        if (player.gameMode.isCreative()) {
            return;
        }
        if (!damageHeldHistoryToken(player, InteractionHand.MAIN_HAND, item)) {
            damageHeldHistoryToken(player, InteractionHand.OFF_HAND, item);
        }
    }

    private static boolean damageHeldHistoryToken(ServerPlayer player, InteractionHand hand, Item item) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(item)) {
            return false;
        }
        stack.hurtAndBreak(1, player.serverLevel(), player,
                broken -> player.onEquippedItemBroken(broken, LivingEntity.getSlotForHand(hand)));
        return true;
    }

    private boolean handleBuilderClick(ServerPlayer player, int slotId) {
        if (slotId >= 0 && slotId < BuildMode.values().length) {
            BuildToolsState.setMode(player, BuildMode.values()[slotId]);
            return true;
        }
        switch (slotId) {
            case 3 -> BuildToolsState.clearSelection(player);
            case 4 -> BuildToolsState.savePreset(player);
            case 5 -> PresetLibraryMenu.open(player);
            case 6 -> MaterialChecklistMenu.open(player);
            case 8 -> HelpMenu.open(player);
            default -> {
                return handleShapeClick(player, slotId, 9);
            }
        }
        return true;
    }

    private boolean handleSelectionClick(ServerPlayer player, int slotId) {
        switch (slotId) {
            case 0 -> BuildToolsState.nudgeSelection(player, Direction.WEST);
            case 1 -> BuildToolsState.nudgeSelection(player, Direction.EAST);
            case 2 -> BuildToolsState.nudgeSelection(player, Direction.DOWN);
            case 3 -> BuildToolsState.nudgeSelection(player, Direction.UP);
            case 4 -> BuildToolsState.nudgeSelection(player, Direction.NORTH);
            case 5 -> BuildToolsState.nudgeSelection(player, Direction.SOUTH);
            case 6 -> BuildToolsState.clearSelection(player);
            case 7 -> BuildToolsState.rotateSelection(player);
            case 8 -> BuildToolsState.toggleSelectionVisibility(player);
            case 22 -> BuildToolsState.savePreset(player);
            case 23 -> PresetLibraryMenu.open(player);
            case 24 -> HelpMenu.open(player);
            default -> {
                return handleShapeClick(player, slotId, 9);
            }
        }
        return true;
    }

    private boolean handleBrushClick(ServerPlayer player, int slotId) {
        switch (slotId) {
            case 0 -> BuildToolsState.setBrushMode(player, BrushMode.SPHERE);
            case 1 -> BuildToolsState.setBrushMode(player, BrushMode.CYLINDER);
            case 2 -> BuildToolsState.setBrushMode(player, BrushMode.SMOOTH);
            case 3 -> BuildToolsState.setBrushMode(player, BrushMode.REPLACE);
            case 9 -> BuildToolsState.changeBrushRadius(player, -1);
            case 11 -> BuildToolsState.changeBrushRadius(player, 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean handleBreakerClick(ServerPlayer player, int slotId) {
        switch (slotId) {
            case 0 -> BuildToolsState.clearSelection(player);
            case 1 -> BuildToolsState.rotateSelection(player);
            case 2 -> BuildToolsState.savePreset(player);
            case 3 -> PresetLibraryMenu.open(player);
            case 4 -> BuildToolsState.setAreaBreakerPreset(player, AreaBreakerPreset.NORMAL);
            case 5 -> BuildToolsState.setAreaBreakerPreset(player, AreaBreakerPreset.CLEAR_SNOW_CROPS);
            case 8 -> HelpMenu.open(player);
            default -> {
                return handleShapeClick(player, slotId, 9);
            }
        }
        return true;
    }

    private boolean handleTrowelClick(ServerPlayer player, int slotId) {
        switch (slotId) {
            case 0 -> BuildOperationEngine.copySelection(player);
            case 1 -> BlueprintLibraryMenu.open(player);
            case 2 -> BuildOperationEngine.previewBlueprintPasteAtPlayer(player);
            case 3 -> BuildOperationEngine.previewBlueprintPasteAtSelection(player);
            case 4 -> BuildOperationEngine.confirmPendingBlueprintPaste(player);
            case 5 -> BuildToolsState.clearPendingPaste(player);
            case 6 -> BuildToolsState.rotateBlueprint(player);
            case 7 -> BuildToolsState.mirrorBlueprintX(player);
            case 8 -> BuildToolsState.mirrorBlueprintZ(player);
            case 16 -> MaterialChecklistMenu.open(player);
            case 17 -> BuildToolsState.clearSelection(player);
            case 18 -> BuildOperationEngine.nudgePendingBlueprintPaste(player, net.minecraft.core.Direction.WEST);
            case 19 -> BuildOperationEngine.nudgePendingBlueprintPaste(player, net.minecraft.core.Direction.EAST);
            case 20 -> BuildOperationEngine.nudgePendingBlueprintPaste(player, net.minecraft.core.Direction.DOWN);
            case 21 -> BuildOperationEngine.nudgePendingBlueprintPaste(player, net.minecraft.core.Direction.UP);
            case 22 -> BuildOperationEngine.nudgePendingBlueprintPaste(player, net.minecraft.core.Direction.NORTH);
            case 23 -> BuildOperationEngine.nudgePendingBlueprintPaste(player, net.minecraft.core.Direction.SOUTH);
            case 26 -> HelpMenu.open(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean handleShapeClick(ServerPlayer player, int slotId, int startSlot) {
        int shapeIndex = slotId - startSlot;
        SelectionShape[] shapes = visibleShapes();
        if (shapeIndex >= 0 && shapeIndex < shapes.length) {
            BuildToolsState.setShape(player, shapes[shapeIndex]);
            return true;
        }
        return false;
    }

    private SelectionShape[] visibleShapes() {
        return owner == null ? SelectionShape.basicShapes() : BuildToolsState.availableShapes(owner);
    }

    private static ItemStack shapeIcon(SelectionShape shape) {
        return new ItemStack(switch (shape) {
            case CUBOID -> Items.STONE;
            case WALLS -> Items.BRICKS;
            case FLOOR -> Items.OAK_PLANKS;
            case CEILING -> Items.SMOOTH_STONE_SLAB;
            case HOLLOW_BOX -> Items.GLASS;
            case LINE -> Items.STRING;
            case CYLINDER -> Items.GRAVEL;
            case SPHERE -> Items.SNOWBALL;
            case ELLIPSOID -> Items.SLIME_BALL;
            case ROAD -> Items.RAIL;
            case TUNNEL -> Items.RAIL;
            case ARCH -> Items.STONE_BRICK_STAIRS;
            case DOME -> Items.COPPER_BLOCK;
            case CUSTOM_SMART -> Items.AMETHYST_SHARD;
            case STAIRS -> Items.STONE_STAIRS;
        });
    }

    private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private static Component menuTitle(ServerPlayer player) {
        ItemStack heldTool = heldTool(player);
        if (!heldTool.isEmpty()) {
            return heldTool.getHoverName();
        }
        return Component.translatable("buildtools.menu.title");
    }

    private static ItemStack heldTool(ServerPlayer player) {
        if (ToolProfile.isBuildTool(player.getMainHandItem())) {
            return player.getMainHandItem();
        }
        if (ToolProfile.isBuildTool(player.getOffhandItem())) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private ItemStack modeItem(net.minecraft.world.item.Item item, BuildMode mode) {
        ItemStack stack = named(item, mode.displayName());
        Component description = mode.description().copy().withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        setSelected(stack, owner != null && BuildToolsState.mode(owner) == mode);
        return stack;
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey) {
        return utilityItem(item, nameKey, descriptionKey, false);
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey, boolean selected) {
        ItemStack stack = named(item, Component.translatable(nameKey));
        Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        setSelected(stack, selected);
        return stack;
    }

    private static ItemStack brushModeItem(net.minecraft.world.item.Item item, BrushMode mode, BrushMode selectedMode) {
        ItemStack stack = named(item, mode.displayName());
        Component description = Component.translatable("buildtools.brush." + mode.name().toLowerCase(java.util.Locale.ROOT) + ".description")
                .withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        setSelected(stack, mode == selectedMode);
        return stack;
    }

    private ItemStack breakerPresetItem(net.minecraft.world.item.Item item, AreaBreakerPreset preset) {
        ItemStack stack = named(item, preset.displayName());
        Component description = Component.translatable("buildtools.area_breaker_preset." + preset.name().toLowerCase(java.util.Locale.ROOT) + ".description")
                .withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        setSelected(stack, owner != null && BuildToolsState.areaBreakerPreset(owner) == preset);
        return stack;
    }

    private static void setSelected(ItemStack stack, boolean selected) {
        if (selected) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
    }

    private static ItemStack emptyHistoryItem(boolean undo) {
        ItemStack stack = named(undo ? Items.GRAY_DYE : Items.LIGHT_GRAY_DYE,
                Component.literal(undo ? "No undo history" : "No redo history").withStyle(ChatFormatting.GRAY));
        Component description = Component.literal("Build actions will appear here after you use tools.").withStyle(ChatFormatting.DARK_GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        return stack;
    }

    private static ItemStack historyItem(boolean undo, int index, UndoSnapshot snapshot) {
        HistoryStats stats = HistoryStats.of(snapshot);
        ItemStack stack = named(undo ? Items.CLOCK : Items.COMPASS, Component.literal(historyTitle(undo, index, stats.total()))
                .withStyle(index == 0 ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
        List<Component> lore = historyLore(undo, index, snapshot, stats);
        stack.set(DataComponents.LORE, new ItemLore(lore, lore));
        return stack;
    }

    private static String historyTitle(boolean undo, int index, int total) {
        String action = undo ? "Undo" : "Redo";
        String suffix = index == 0 ? "next" : "queued";
        return action + " #" + (index + 1) + " (" + suffix + "): " + total + " changes";
    }

    private static List<Component> historyLore(boolean undo, int index, UndoSnapshot snapshot, HistoryStats stats) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(index == 0 ? "Click to " + (undo ? "undo" : "redo") + " this action." : "Older history entry; undo/redo runs in order.")
                .withStyle(index == 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        lore.add(Component.literal("Dimension: " + snapshot.dimension().location()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Placed: " + stats.placed() + "  Removed: " + stats.removed() + "  Replaced: " + stats.replaced())
                .withStyle(ChatFormatting.GRAY));
        String setBlocks = blockSummary(snapshot, true);
        if (!setBlocks.isEmpty()) {
            lore.add(Component.literal("Set: " + setBlocks).withStyle(ChatFormatting.AQUA));
        }
        String clearedBlocks = blockSummary(snapshot, false);
        if (!clearedBlocks.isEmpty()) {
            lore.add(Component.literal("Cleared: " + clearedBlocks).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (!snapshot.refund().isEmpty()) {
            lore.add(Component.literal("Materials: " + materialSummary(snapshot.refund())).withStyle(ChatFormatting.AQUA));
        }
        if (!snapshot.producedDrops().isEmpty()) {
            lore.add(Component.literal("Stored drops: " + materialSummary(snapshot.producedDrops())).withStyle(ChatFormatting.GREEN));
        }
        return lore;
    }

    private static ItemStack collectDropsItem(Map<ItemStackKey, Integer> drops) {
        ItemStack stack = named(drops.isEmpty() ? Items.GRAY_DYE : Items.HOPPER,
                Component.translatable("buildtools.menu.collect_stored_drops").withStyle(drops.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        Component description = (drops.isEmpty()
                ? Component.translatable("buildtools.menu.collect_stored_drops.empty")
                : Component.translatable("buildtools.menu.collect_stored_drops.description", materialSummary(drops)))
                .withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        return stack;
    }

    private static String blockSummary(UndoSnapshot snapshot, boolean redoneBlocks) {
        Map<String, Integer> counts = new HashMap<>();
        for (UndoSnapshot.Entry entry : snapshot.entries()) {
            if (redoneBlocks && entry.redoneState().isAir()) {
                continue;
            }
            if (!redoneBlocks && (!entry.redoneState().isAir() || entry.previousState().isAir())) {
                continue;
            }
            String name = (redoneBlocks ? entry.redoneState() : entry.previousState()).getBlock().getName().getString();
            counts.merge(name, 1, Integer::sum);
        }
        return countedSummary(counts);
    }

    private static String materialSummary(List<ItemStack> refund) {
        return countedSummary(StoredItems.toCounts(refund).entrySet().stream()
                .collect(HashMap::new, (counts, entry) -> counts.put(entry.getKey().stack(1).getHoverName().getString(), entry.getValue()), HashMap::putAll));
    }

    private static String materialSummary(Map<ItemStackKey, Integer> refund) {
        return countedSummary(refund.entrySet().stream()
                .collect(HashMap::new, (counts, entry) -> counts.put(entry.getKey().stack(1).getHoverName().getString(), entry.getValue()), HashMap::putAll));
    }

    private static String countedSummary(Map<String, Integer> counts) {
        List<String> parts = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .forEach(entry -> parts.add(entry.getValue() + "x " + entry.getKey()));
        int remaining = Math.max(0, counts.size() - parts.size());
        String summary = String.join(", ", parts);
        return remaining == 0 ? summary : summary + ", +" + remaining + " more";
    }

    private record HistoryStats(int total, int placed, int removed, int replaced) {
        private static HistoryStats of(UndoSnapshot snapshot) {
            int placed = 0;
            int removed = 0;
            int replaced = 0;
            for (UndoSnapshot.Entry entry : snapshot.entries()) {
                if (entry.redoneState().isAir()) {
                    removed++;
                } else if (entry.previousState().isAir() || entry.previousState().canBeReplaced()) {
                    placed++;
                } else {
                    replaced++;
                }
            }
            return new HistoryStats(snapshot.entries().size(), placed, removed, replaced);
        }
    }

    private static final class FakeSlot extends Slot {
        private FakeSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isFake() {
            return true;
        }
    }
}
