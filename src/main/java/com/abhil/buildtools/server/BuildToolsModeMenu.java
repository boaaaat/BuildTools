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
    private static final int MENU_ROWS = 4;
    private static final int MENU_SIZE = MENU_ROWS * 9;
    private static final int SHAPE_START_SLOT = 9;
    private static final int ADVANCED_SHAPES_SLOT = 6;
    private static final int ADVANCED_SHAPE_START_SLOT = 9;
    private static final int ADVANCED_OPTION_START_SLOT = 18;
    private static final int BRUSH_RADIUS_SLOT = 9;
    private static final int BRUSH_DEPTH_SLOT = 10;
    private static final int BRUSH_DENSITY_SLOT = 11;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ToolProfile profile;
    private final ServerPlayer owner;
    private boolean advancedShapesPage;

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
            if (handleClick(serverPlayer, slotId, isRightClick(button, clickType))) {
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
        for (int row = 0; row < MENU_ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = column + row * 9;
                addSlot(new FakeSlot(menuItems, slot, 8 + column * 18, 18 + row * 18));
            }
        }
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 102 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 160));
        }
    }

    private void populateMenuItems() {
        menuItems.clearContent();
        if (advancedShapesPage) {
            populateAdvancedShapesPage();
            return;
        }
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
        menuItems.setItem(2, modeItem(Items.LIGHT_BLUE_STAINED_GLASS, BuildMode.SURFACE));
        menuItems.setItem(3, utilityItem(Items.BARRIER, "buildtools.menu.clear_selection", "buildtools.menu.clear_selection.description"));
        menuItems.setItem(4, utilityItem(Items.WRITABLE_BOOK, "buildtools.menu.save_preset", "buildtools.menu.save_preset.description"));
        menuItems.setItem(5, utilityItem(Items.BOOK, "buildtools.menu.presets", "buildtools.menu.presets.description"));
        menuItems.setItem(6, utilityItem(Items.CHEST, "buildtools.menu.material_checklist", "buildtools.menu.material_checklist.description"));
        menuItems.setItem(7, utilityItem(Items.BRICKS, "buildtools.menu.material_selection", "buildtools.menu.material_selection.description"));
        menuItems.setItem(8, utilityItem(Items.KNOWLEDGE_BOOK, "buildtools.menu.help", "buildtools.menu.help.description"));
        populateShapes(SHAPE_START_SLOT);
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
        populateShapes(SHAPE_START_SLOT);
        menuItems.setItem(33, utilityItem(Items.WRITABLE_BOOK, "buildtools.menu.save_preset", "buildtools.menu.save_preset.description"));
        menuItems.setItem(34, utilityItem(Items.BOOK, "buildtools.menu.presets", "buildtools.menu.presets.description"));
        menuItems.setItem(35, utilityItem(Items.KNOWLEDGE_BOOK, "buildtools.menu.help", "buildtools.menu.help.description"));
    }

    private void populateBrushMenu() {
        BrushMode brushMode = owner == null ? BrushMode.PAINT : BuildToolsState.brushMode(owner);
        int radius = owner == null ? 2 : BuildToolsState.brushRadius(owner);
        int depth = owner == null ? 1 : BuildToolsState.brushDepth(owner);
        int density = owner == null ? 100 : BuildToolsState.brushDensity(owner);
        menuItems.setItem(0, brushModeItem(Items.PAINTING, BrushMode.PAINT, brushMode));
        menuItems.setItem(1, brushModeItem(Items.BARRIER, BrushMode.ERASE, brushMode));
        menuItems.setItem(2, brushModeItem(Items.ORANGE_DYE, BrushMode.REPLACE, brushMode));
        menuItems.setItem(3, brushModeItem(Items.GRASS_BLOCK, BrushMode.SMOOTH, brushMode));
        menuItems.setItem(4, brushModeItem(Items.WHEAT_SEEDS, BrushMode.SCATTER, brushMode));
        menuItems.setItem(5, brushModeItem(Items.SNOWBALL, BrushMode.OVERLAY, brushMode));
        menuItems.setItem(6, brushModeItem(Items.AMETHYST_SHARD, BrushMode.BLEND, brushMode));
        menuItems.setItem(7, utilityItem(Items.BRICKS, "buildtools.menu.material_selection", "buildtools.menu.material_selection.description"));
        menuItems.setItem(BRUSH_RADIUS_SLOT, brushSettingItem(Items.PAINTING, "buildtools.menu.brush_radius", "buildtools.menu.brush_radius.description", radius));
        menuItems.setItem(BRUSH_DEPTH_SLOT, brushSettingItem(Items.DEEPSLATE, "buildtools.menu.brush_depth", "buildtools.menu.brush_depth.description", depth));
        menuItems.setItem(BRUSH_DENSITY_SLOT, brushSettingItem(Items.WHEAT_SEEDS, "buildtools.menu.brush_density", "buildtools.menu.brush_density.description", density));
        populateShapes(18);
    }

    private void populateBreakerMenu() {
        menuItems.setItem(0, utilityItem(Items.BARRIER, "buildtools.menu.clear_selection", "buildtools.menu.clear_selection.description"));
        menuItems.setItem(1, utilityItem(Items.ENDER_EYE, "buildtools.menu.rotate_selection", "buildtools.menu.rotate_selection.description"));
        menuItems.setItem(2, utilityItem(Items.WRITABLE_BOOK, "buildtools.menu.save_preset", "buildtools.menu.save_preset.description"));
        menuItems.setItem(3, utilityItem(Items.BOOK, "buildtools.menu.presets", "buildtools.menu.presets.description"));
        menuItems.setItem(4, breakerPresetItem(Items.IRON_PICKAXE, AreaBreakerPreset.NORMAL));
        menuItems.setItem(5, breakerPresetItem(Items.WHEAT_SEEDS, AreaBreakerPreset.CLEAR_SNOW_CROPS));
        menuItems.setItem(ADVANCED_SHAPES_SLOT, utilityItem(Items.NETHER_STAR, "buildtools.menu.advanced_shapes", "buildtools.menu.advanced_shapes.description"));
        menuItems.setItem(8, utilityItem(Items.KNOWLEDGE_BOOK, "buildtools.menu.help", "buildtools.menu.help.description"));
        populateShapes(SHAPE_START_SLOT);
    }

    private void populateAdvancedShapesPage() {
        menuItems.setItem(0, utilityItem(Items.ARROW, "buildtools.menu.back", "buildtools.menu.back.description"));
        SelectionShape[] shapes = SelectionShape.advancedStructureShapes();
        for (int i = 0; i < shapes.length; i++) {
            ItemStack stack = shapeIcon(shapes[i]);
            stack.set(DataComponents.CUSTOM_NAME, shapeName(shapes[i]));
            Component description = structureShapeDescription(shapes[i]).copy().withStyle(ChatFormatting.GRAY);
            stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
            setSelected(stack, owner != null && BuildToolsState.selectionShape(owner) == shapes[i]);
            menuItems.setItem(ADVANCED_SHAPE_START_SLOT + i, stack);
        }
        if (owner != null && BuildToolsState.selectionShape(owner).isAdvancedStructure()) {
            populateAdvancedShapeOptions(BuildToolsState.selectionShape(owner));
        }
    }

    private void populateAdvancedShapeOptions(SelectionShape shape) {
        switch (shape) {
            case GABLE_ROOF -> {
                option(18, Items.PAPER, AdvancedShapeOption.DETAIL, shapeDetailName());
                option(19, Items.COMPASS, AdvancedShapeOption.RIDGE, Component.translatable("buildtools.menu.advanced_shape.ridge", BuildToolsState.roofDirection(owner).displayName()));
                option(20, Items.FEATHER, AdvancedShapeOption.OVERHANG, Component.translatable("buildtools.menu.advanced_shape.overhang", BuildToolsState.roofOverhang(owner)));
                option(21, Items.OAK_TRAPDOOR, AdvancedShapeOption.END_CAPS, Component.translatable("buildtools.menu.advanced_shape.end_caps", onOff(BuildToolsState.gableEndCaps(owner))));
            }
            case HIP_ROOF -> {
                option(18, Items.PAPER, AdvancedShapeOption.DETAIL, shapeDetailName());
                option(19, Items.COMPASS, AdvancedShapeOption.RIDGE, Component.translatable("buildtools.menu.advanced_shape.cap_direction", BuildToolsState.roofDirection(owner).displayName()));
                option(20, Items.FEATHER, AdvancedShapeOption.OVERHANG, Component.translatable("buildtools.menu.advanced_shape.overhang", BuildToolsState.roofOverhang(owner)));
            }
            case A_FRAME -> {
                option(18, Items.PAPER, AdvancedShapeOption.DETAIL, shapeDetailName());
                option(19, Items.COMPASS, AdvancedShapeOption.RIDGE, Component.translatable("buildtools.menu.advanced_shape.ridge", BuildToolsState.roofDirection(owner).displayName()));
                option(20, Items.FEATHER, AdvancedShapeOption.OVERHANG, Component.translatable("buildtools.menu.advanced_shape.overhang", BuildToolsState.roofOverhang(owner)));
                option(21, Items.OAK_PLANKS, AdvancedShapeOption.FLOOR_FRAME, Component.translatable("buildtools.menu.advanced_shape.floor_frame", onOff(BuildToolsState.aFrameFloorFrame(owner))));
            }
            case ROOM_FRAME -> {
                option(18, Items.OAK_FENCE, AdvancedShapeOption.STUD_SPACING, Component.translatable("buildtools.menu.advanced_shape.stud_spacing", BuildToolsState.roomStudSpacing(owner)));
                option(19, Items.OAK_PLANKS, AdvancedShapeOption.FLOOR_BEAMS, Component.translatable("buildtools.menu.advanced_shape.floor_beams", onOff(BuildToolsState.roomFloorBeams(owner))));
                option(20, Items.OAK_SLAB, AdvancedShapeOption.CEILING_JOISTS, Component.translatable("buildtools.menu.advanced_shape.ceiling_joists", onOff(BuildToolsState.roomCeilingJoists(owner))));
            }
            case BRIDGE -> {
                option(18, Items.OAK_SLAB, AdvancedShapeOption.BRIDGE_WIDTH, Component.translatable("buildtools.menu.advanced_shape.bridge_width", BuildToolsState.bridgeWidth(owner)));
                option(19, Items.OAK_FENCE, AdvancedShapeOption.BRIDGE_RAILS, Component.translatable("buildtools.menu.advanced_shape.rails", onOff(BuildToolsState.bridgeRails(owner))));
                option(20, Items.CHAIN, AdvancedShapeOption.BRIDGE_SUPPORTS, Component.translatable("buildtools.menu.advanced_shape.supports", BuildToolsState.bridgeSupportMode(owner).displayName()));
                option(21, Items.SCAFFOLDING, AdvancedShapeOption.BRIDGE_SUPPORT_SPACING, Component.translatable("buildtools.menu.advanced_shape.support_spacing", BuildToolsState.bridgeSupportSpacing(owner)));
            }
            case TOWER -> {
                option(18, Items.PAPER, AdvancedShapeOption.DETAIL, shapeDetailName());
                option(19, Items.LADDER, AdvancedShapeOption.TOWER_FLOOR_HEIGHT, Component.translatable("buildtools.menu.advanced_shape.floor_height", BuildToolsState.towerFloorHeight(owner)));
                option(20, Items.STONE_BRICKS, AdvancedShapeOption.TOWER_WALL_THICKNESS, Component.translatable("buildtools.menu.advanced_shape.wall_thickness", BuildToolsState.towerWallThickness(owner)));
                option(21, Items.STONE_BRICK_STAIRS, AdvancedShapeOption.TOWER_TOP_STYLE, Component.translatable("buildtools.menu.advanced_shape.top_style", BuildToolsState.towerTopStyle(owner).displayName()));
            }
            default -> {
            }
        }
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
            if (shapes[i] == SelectionShape.ROAD) {
                int width = owner == null ? BuildToolsState.DEFAULT_ROAD_WIDTH : BuildToolsState.roadWidth(owner);
                Component description = Component.translatable("buildtools.menu.road_width.description").withStyle(ChatFormatting.GRAY);
                stack.set(DataComponents.CUSTOM_NAME, Component.translatable("buildtools.menu.road_width", width));
                stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
            } else if (shapes[i] == SelectionShape.ARCH) {
                int peak = owner == null ? BuildToolsState.DEFAULT_ARCH_PEAK : BuildToolsState.archPeak(owner);
                Component mode = owner == null
                        ? Component.translatable("buildtools.arch_mode.open")
                        : BuildToolsState.archMode(owner).displayName().copy()
                                .append(" / ")
                                .append(BuildToolsState.archDirection(owner).displayName());
                Component description = Component.translatable("buildtools.menu.arch.description", peak).withStyle(ChatFormatting.GRAY);
                stack.set(DataComponents.CUSTOM_NAME, Component.translatable("buildtools.menu.arch", mode));
                stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
            } else if (shapes[i] == SelectionShape.SPHERE || shapes[i] == SelectionShape.ELLIPSOID) {
                boolean hollow = owner != null && (shapes[i] == SelectionShape.SPHERE
                        ? BuildToolsState.sphereHollow(owner)
                        : BuildToolsState.ellipsoidHollow(owner));
                Component fill = Component.translatable(hollow ? "buildtools.shape_fill.hollow" : "buildtools.shape_fill.solid");
                Component description = Component.translatable("buildtools.menu.shape_hollow.description").withStyle(ChatFormatting.GRAY);
                stack.set(DataComponents.CUSTOM_NAME, Component.translatable("buildtools.menu.shape_hollow", shapes[i].displayName(), fill));
                stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
            } else if (BuildToolsState.supportsDetailMode(shapes[i])) {
                Component description = structureShapeDescription(shapes[i]).copy().withStyle(ChatFormatting.GRAY);
                stack.set(DataComponents.CUSTOM_NAME, shapeName(shapes[i]));
                stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
            } else {
                stack.set(DataComponents.CUSTOM_NAME, shapeName(shapes[i]));
            }
            setSelected(stack, owner != null && BuildToolsState.selectionShape(owner) == shapes[i]);
            menuItems.setItem(startSlot + i, stack);
        }
    }

    private static boolean isRightClick(int button, ClickType clickType) {
        return clickType == ClickType.PICKUP && button == 1;
    }

    private boolean handleClick(ServerPlayer player, int slotId, boolean rightClick) {
        if (advancedShapesPage) {
            return handleAdvancedShapesClick(player, slotId, rightClick);
        }
        return switch (profile) {
            case SELECTION, ADVANCED_SELECTION -> handleSelectionClick(player, slotId, rightClick);
            case BRUSH -> handleBrushClick(player, slotId);
            case BREAKER -> handleBreakerClick(player, slotId, rightClick);
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
            default -> handleBuilderClick(player, slotId, rightClick);
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

    private boolean handleBuilderClick(ServerPlayer player, int slotId, boolean rightClick) {
        if (slotId >= 0 && slotId < BuildMode.values().length) {
            BuildToolsState.setMode(player, BuildMode.values()[slotId]);
            return true;
        }
        switch (slotId) {
            case 3 -> BuildToolsState.clearSelection(player);
            case 4 -> BuildToolsState.savePreset(player);
            case 5 -> PresetLibraryMenu.open(player);
            case 6 -> MaterialChecklistMenu.open(player);
            case 7 -> MaterialSelectionMenu.open(player);
            case 8 -> HelpMenu.open(player);
            default -> {
                return handleShapeClick(player, slotId, SHAPE_START_SLOT, rightClick);
            }
        }
        return true;
    }

    private boolean handleSelectionClick(ServerPlayer player, int slotId, boolean rightClick) {
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
            case 33 -> BuildToolsState.savePreset(player);
            case 34 -> PresetLibraryMenu.open(player);
            case 35 -> HelpMenu.open(player);
            default -> {
                return handleShapeClick(player, slotId, SHAPE_START_SLOT, rightClick);
            }
        }
        return true;
    }

    private boolean handleBrushClick(ServerPlayer player, int slotId) {
        switch (slotId) {
            case 0 -> BuildToolsState.setBrushMode(player, BrushMode.PAINT);
            case 1 -> BuildToolsState.setBrushMode(player, BrushMode.ERASE);
            case 2 -> BuildToolsState.setBrushMode(player, BrushMode.REPLACE);
            case 3 -> BuildToolsState.setBrushMode(player, BrushMode.SMOOTH);
            case 4 -> BuildToolsState.setBrushMode(player, BrushMode.SCATTER);
            case 5 -> BuildToolsState.setBrushMode(player, BrushMode.OVERLAY);
            case 6 -> BuildToolsState.setBrushMode(player, BrushMode.BLEND);
            case 7 -> MaterialSelectionMenu.open(player);
            default -> {
                return handleShapeClick(player, slotId, 18, false);
            }
        }
        return true;
    }

    private boolean handleBreakerClick(ServerPlayer player, int slotId, boolean rightClick) {
        switch (slotId) {
            case 0 -> BuildToolsState.clearSelection(player);
            case 1 -> BuildToolsState.rotateSelection(player);
            case 2 -> BuildToolsState.savePreset(player);
            case 3 -> PresetLibraryMenu.open(player);
            case 4 -> BuildToolsState.setAreaBreakerPreset(player, AreaBreakerPreset.NORMAL);
            case 5 -> BuildToolsState.setAreaBreakerPreset(player, AreaBreakerPreset.CLEAR_SNOW_CROPS);
            case ADVANCED_SHAPES_SLOT -> advancedShapesPage = true;
            case 8 -> HelpMenu.open(player);
            default -> {
                return handleShapeClick(player, slotId, SHAPE_START_SLOT, rightClick);
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

    private boolean handleShapeClick(ServerPlayer player, int slotId, int startSlot, boolean rightClick) {
        int shapeIndex = slotId - startSlot;
        SelectionShape[] shapes = visibleShapes();
        if (shapeIndex >= 0 && shapeIndex < shapes.length) {
            SelectionShape shape = shapes[shapeIndex];
            if (BuildToolsState.selectionShape(player) == shape && handleSelectedShapeOptionClick(player, shape, rightClick)) {
                return true;
            }
            BuildToolsState.setShape(player, shapes[shapeIndex]);
            return true;
        }
        return false;
    }

    private boolean handleAdvancedShapesClick(ServerPlayer player, int slotId, boolean rightClick) {
        if (slotId == 0) {
            advancedShapesPage = false;
            return true;
        }
        int shapeIndex = slotId - ADVANCED_SHAPE_START_SLOT;
        SelectionShape[] shapes = SelectionShape.advancedStructureShapes();
        if (shapeIndex >= 0 && shapeIndex < shapes.length) {
            BuildToolsState.setShape(player, shapes[shapeIndex]);
            return true;
        }
        AdvancedShapeOption option = advancedShapeOption(slotId);
        if (option != null) {
            BuildToolsState.adjustAdvancedShapeOption(player, option, rightClick ? -1 : 1);
            return true;
        }
        return false;
    }

    private static boolean handleSelectedShapeOptionClick(ServerPlayer player, SelectionShape shape, boolean rightClick) {
        return switch (shape) {
            case ARCH -> {
                if (rightClick) {
                    BuildToolsState.cycleArchDirection(player);
                } else {
                    BuildToolsState.cycleArchMode(player);
                }
                yield true;
            }
            case SPHERE, ELLIPSOID -> {
                BuildToolsState.toggleShapeHollow(player, shape);
                yield true;
            }
            default -> {
                if (!BuildToolsState.supportsDetailMode(shape)) {
                    yield false;
                }
                if (rightClick && BuildToolsState.supportsRoofDirection(shape)) {
                    BuildToolsState.cycleRoofDirection(player);
                } else {
                    BuildToolsState.toggleShapeDetailMode(player, shape);
                }
                yield true;
            }
        };
    }

    public void adjustRoadWidth(ServerPlayer player, int delta) {
        BuildToolsState.changeRoadWidth(player, delta);
        populateMenuItems();
    }

    public void adjustArchPeak(ServerPlayer player, int delta) {
        BuildToolsState.changeArchPeak(player, delta);
        populateMenuItems();
    }

    public void adjustStairDirection(ServerPlayer player, int delta) {
        BuildToolsState.cycleStairDirection(player, delta);
        populateMenuItems();
    }

    public void adjustBridgeWidth(ServerPlayer player, int delta) {
        BuildToolsState.changeBridgeWidth(player, delta);
        populateMenuItems();
    }

    public void adjustTowerFloorHeight(ServerPlayer player, int delta) {
        BuildToolsState.changeTowerFloorHeight(player, delta);
        populateMenuItems();
    }

    public void adjustAdvancedShapeOption(ServerPlayer player, AdvancedShapeOption option, int delta) {
        BuildToolsState.adjustAdvancedShapeOption(player, option, delta);
        populateMenuItems();
    }

    public void adjustBrushSetting(ServerPlayer player, int setting, int delta) {
        if (profile != ToolProfile.BRUSH) {
            return;
        }
        switch (setting) {
            case 0 -> BuildToolsState.changeBrushRadius(player, delta);
            case 1 -> BuildToolsState.changeBrushDepth(player, delta);
            case 2 -> BuildToolsState.changeBrushDensity(player, delta);
            default -> {
                return;
            }
        }
        populateMenuItems();
    }

    public boolean isBrushRadiusSlot(Slot slot) {
        return this.slots.indexOf(slot) == BRUSH_RADIUS_SLOT && slot.getItem().is(Items.PAINTING);
    }

    public boolean isBrushDepthSlot(Slot slot) {
        return this.slots.indexOf(slot) == BRUSH_DEPTH_SLOT && slot.getItem().is(Items.DEEPSLATE);
    }

    public boolean isBrushDensitySlot(Slot slot) {
        return this.slots.indexOf(slot) == BRUSH_DENSITY_SLOT && slot.getItem().is(Items.WHEAT_SEEDS);
    }

    private SelectionShape[] visibleShapes() {
        SelectionShape[] shapes = owner == null ? SelectionShape.basicShapes() : BuildToolsState.availableShapes(owner);
        if (advancedShapesPage) {
            return shapes;
        }
        List<SelectionShape> visible = new ArrayList<>();
        for (SelectionShape shape : shapes) {
            if (!shape.isAdvancedStructure()) {
                visible.add(shape);
            }
        }
        return visible.toArray(SelectionShape[]::new);
    }

    public static boolean isRoadShapeSlot(int slot) {
        return slot == SHAPE_START_SLOT + SelectionShape.ROAD.ordinal();
    }

    public static boolean isArchShapeSlot(int slot) {
        return slot == SHAPE_START_SLOT + SelectionShape.ARCH.ordinal();
    }

    public static boolean isStairShapeSlot(int slot) {
        return slot == SHAPE_START_SLOT + SelectionShape.STAIRS.ordinal();
    }

    public boolean isRoadShapeSlot(Slot slot) {
        return isShapeSlot(slot, SelectionShape.ROAD);
    }

    public boolean isArchShapeSlot(Slot slot) {
        return isShapeSlot(slot, SelectionShape.ARCH);
    }

    public boolean isStairShapeSlot(Slot slot) {
        return isShapeSlot(slot, SelectionShape.STAIRS);
    }

    public boolean isBridgeShapeSlot(Slot slot) {
        return isShapeSlot(slot, SelectionShape.BRIDGE);
    }

    public boolean isTowerShapeSlot(Slot slot) {
        return isShapeSlot(slot, SelectionShape.TOWER);
    }

    public AdvancedShapeOption advancedShapeOption(Slot slot) {
        return advancedShapesPage ? advancedShapeOption(this.slots.indexOf(slot)) : null;
    }

    private AdvancedShapeOption advancedShapeOption(int slotId) {
        if (owner == null) {
            return null;
        }
        SelectionShape shape = BuildToolsState.selectionShape(owner);
        return switch (shape) {
            case GABLE_ROOF -> switch (slotId) {
                case 18 -> AdvancedShapeOption.DETAIL;
                case 19 -> AdvancedShapeOption.RIDGE;
                case 20 -> AdvancedShapeOption.OVERHANG;
                case 21 -> AdvancedShapeOption.END_CAPS;
                default -> null;
            };
            case HIP_ROOF -> switch (slotId) {
                case 18 -> AdvancedShapeOption.DETAIL;
                case 19 -> AdvancedShapeOption.RIDGE;
                case 20 -> AdvancedShapeOption.OVERHANG;
                default -> null;
            };
            case A_FRAME -> switch (slotId) {
                case 18 -> AdvancedShapeOption.DETAIL;
                case 19 -> AdvancedShapeOption.RIDGE;
                case 20 -> AdvancedShapeOption.OVERHANG;
                case 21 -> AdvancedShapeOption.FLOOR_FRAME;
                default -> null;
            };
            case ROOM_FRAME -> switch (slotId) {
                case 18 -> AdvancedShapeOption.STUD_SPACING;
                case 19 -> AdvancedShapeOption.FLOOR_BEAMS;
                case 20 -> AdvancedShapeOption.CEILING_JOISTS;
                default -> null;
            };
            case BRIDGE -> switch (slotId) {
                case 18 -> AdvancedShapeOption.BRIDGE_WIDTH;
                case 19 -> AdvancedShapeOption.BRIDGE_RAILS;
                case 20 -> AdvancedShapeOption.BRIDGE_SUPPORTS;
                case 21 -> AdvancedShapeOption.BRIDGE_SUPPORT_SPACING;
                default -> null;
            };
            case TOWER -> switch (slotId) {
                case 18 -> AdvancedShapeOption.DETAIL;
                case 19 -> AdvancedShapeOption.TOWER_FLOOR_HEIGHT;
                case 20 -> AdvancedShapeOption.TOWER_WALL_THICKNESS;
                case 21 -> AdvancedShapeOption.TOWER_TOP_STYLE;
                default -> null;
            };
            default -> null;
        };
    }

    private boolean isShapeSlot(Slot slot, SelectionShape shape) {
        int slotId = this.slots.indexOf(slot);
        SelectionShape[] shapes = visibleShapes();
        for (int i = 0; i < shapes.length; i++) {
            if (shapes[i] == shape) {
                return slotId == SHAPE_START_SLOT + i;
            }
        }
        return false;
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
            case PYRAMID -> Items.POINTED_DRIPSTONE;
            case GABLE_ROOF -> Items.OAK_STAIRS;
            case HIP_ROOF -> Items.DARK_OAK_STAIRS;
            case A_FRAME -> Items.SPRUCE_LOG;
            case ROOM_FRAME -> Items.OAK_LOG;
            case BRIDGE -> Items.OAK_SLAB;
            case TOWER -> Items.STONE_BRICKS;
            case CUSTOM_SMART -> Items.AMETHYST_SHARD;
            case STAIRS -> Items.STONE_STAIRS;
        });
    }

    private void option(int slot, net.minecraft.world.item.Item item, AdvancedShapeOption option, Component name) {
        ItemStack stack = named(item, name);
        Component description = Component.translatable("buildtools.menu.advanced_shape.option.description").withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        setSelected(stack, advancedShapeOption(slot) == option);
        menuItems.setItem(slot, stack);
    }

    private Component shapeDetailName() {
        return Component.translatable("buildtools.menu.advanced_shape.detail", owner == null
                ? Component.translatable("buildtools.shape_detail.plain")
                : BuildToolsState.shapeDetailMode(owner).displayName());
    }

    private static Component onOff(boolean enabled) {
        return Component.translatable(enabled ? "buildtools.option.on" : "buildtools.option.off");
    }

    private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private static ItemStack brushSettingItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey, int value) {
        ItemStack stack = named(item, Component.translatable(nameKey, value));
        Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        return stack;
    }

    private Component shapeName(SelectionShape shape) {
        if (shape == SelectionShape.STAIRS && owner != null) {
            return shape.displayName().copy()
                    .append(": ")
                    .append(DirectionDisplay.stairDirection(owner, BuildToolsState.stairDirectionOverride(owner)));
        }
        if (BuildToolsState.supportsDetailMode(shape)) {
            Component detail = owner == null
                    ? Component.translatable("buildtools.shape_detail.plain")
                    : BuildToolsState.shapeDetailMode(owner).displayName();
            var name = shape.displayName().copy().append(": ").append(detail);
            if (BuildToolsState.supportsRoofDirection(shape)) {
                Component direction = owner == null
                        ? Component.translatable("buildtools.roof_direction.auto")
                        : BuildToolsState.roofDirection(owner).displayName();
                name.append(" / ").append(direction);
            } else if (shape == SelectionShape.BRIDGE) {
                int width = owner == null ? BuildToolsState.DEFAULT_BRIDGE_WIDTH : BuildToolsState.bridgeWidth(owner);
                name.append(" / ").append(Component.translatable("buildtools.menu.bridge_width_short", width));
            } else if (shape == SelectionShape.TOWER) {
                int height = owner == null ? BuildToolsState.DEFAULT_TOWER_FLOOR_HEIGHT : BuildToolsState.towerFloorHeight(owner);
                name.append(" / ").append(Component.translatable("buildtools.menu.tower_floor_short", height));
            }
            return name;
        }
        return shape.displayName();
    }

    private Component structureShapeDescription(SelectionShape shape) {
        if (BuildToolsState.supportsRoofDirection(shape)) {
            return Component.translatable("buildtools.menu.roof_shape.description");
        }
        if (shape == SelectionShape.BRIDGE) {
            int width = owner == null ? BuildToolsState.DEFAULT_BRIDGE_WIDTH : BuildToolsState.bridgeWidth(owner);
            return Component.translatable("buildtools.menu.bridge.description", width);
        }
        if (shape == SelectionShape.TOWER) {
            int height = owner == null ? BuildToolsState.DEFAULT_TOWER_FLOOR_HEIGHT : BuildToolsState.towerFloorHeight(owner);
            return Component.translatable("buildtools.menu.tower.description", height);
        }
        return Component.translatable("buildtools.menu.structure_shape.description");
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
