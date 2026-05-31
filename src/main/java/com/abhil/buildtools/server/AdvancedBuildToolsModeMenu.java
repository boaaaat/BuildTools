package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModMenus;
import com.abhil.buildtools.shape.BuildMode;
import com.abhil.buildtools.shape.SelectionShape;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class AdvancedBuildToolsModeMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 54;
    private static final int SHAPE_START_SLOT = 9;
    private static final int GRADIENT_SLOT = 4;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ServerPlayer owner;
    private final ToolProfile profile;

    public AdvancedBuildToolsModeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    }

    private AdvancedBuildToolsModeMenu(int containerId, Inventory inventory, ServerPlayer owner) {
        super(ModMenus.ADVANCED_MODE_MENU.get(), containerId);
        this.owner = owner;
        this.profile = owner == null ? ToolProfile.ADVANCED_BUILDER : BuildToolsState.activeToolProfile(owner);
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new AdvancedBuildToolsModeMenu(containerId, inventory, player),
                menuTitle(player)));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < MENU_SIZE && player instanceof ServerPlayer serverPlayer) {
            boolean rightClick = clickType == ClickType.PICKUP && button == 1;
            if (isAdvancedSelectionMenu()) {
                if (handleSelectionClick(serverPlayer, slotId, rightClick)) {
                    populateMenuItems();
                    return;
                }
            } else if (slotId >= 0 && slotId < BuildMode.values().length) {
                BuildToolsState.setMode(serverPlayer, BuildMode.values()[slotId]);
                populateMenuItems();
                return;
            } else if (handleUtilityClick(serverPlayer, slotId)) {
                populateMenuItems();
                return;
            }
            int shapeIndex = shapeIndex(slotId);
            SelectionShape[] shapes = visibleShapes();
            if (shapeIndex >= 0 && shapeIndex < shapes.length) {
                handleShapeClick(serverPlayer, shapes[shapeIndex], rightClick);
                populateMenuItems();
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
    }

    private void addMenuSlots() {
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = column + row * 9;
                addSlot(new FakeSlot(menuItems, slot, 8 + column * 18, 18 + row * 18));
            }
        }
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 138 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 196));
        }
    }

    private void populateMenuItems() {
        for (int i = 0; i < MENU_SIZE; i++) {
            menuItems.setItem(i, ItemStack.EMPTY);
        }
        if (isAdvancedSelectionMenu()) {
            populateAdvancedSelectionMenu();
            return;
        }
        populateAdvancedBuilderMenu();
    }

    private void populateAdvancedBuilderMenu() {
        menuItems.setItem(0, modeItem(Items.LIME_STAINED_GLASS, BuildMode.FILL));
        menuItems.setItem(1, modeItem(Items.ORANGE_STAINED_GLASS, BuildMode.REPLACE));
        menuItems.setItem(2, modeItem(Items.LIGHT_BLUE_STAINED_GLASS, BuildMode.SURFACE));
        menuItems.setItem(3, utilityItem(Items.CHEST, "buildtools.menu.material_checklist", "buildtools.menu.material_checklist.description"));
        ItemStack gradient = utilityItem(Items.POWDER_SNOW_BUCKET, "buildtools.menu.gradient", "buildtools.menu.gradient.description",
                owner != null && BuildToolsState.paletteMode(owner) == PaletteMode.GRADIENT);
        if (owner != null) {
            gradient.set(DataComponents.CUSTOM_NAME, Component.translatable("buildtools.menu.gradient")
                    .append(": ")
                    .append(DirectionDisplay.gradientDirection(owner, BuildToolsState.gradientDirection(owner))));
        }
        menuItems.setItem(GRADIENT_SLOT, gradient);
        menuItems.setItem(5, utilityItem(Items.MAP, "buildtools.menu.save_plan", "buildtools.menu.save_plan.description"));
        menuItems.setItem(6, utilityItem(Items.FILLED_MAP, "buildtools.menu.build_plan", "buildtools.menu.build_plan.description"));
        menuItems.setItem(7, utilityItem(Items.DISPENSER, "buildtools.menu.random_pattern", "buildtools.menu.random_pattern.description",
                owner != null && BuildToolsState.paletteMode(owner) == PaletteMode.RANDOM));
        menuItems.setItem(8, utilityItem(Items.BRICKS, "buildtools.menu.material_selection", "buildtools.menu.material_selection.description"));

        populateShapes(SHAPE_START_SLOT);

        menuItems.setItem(39, utilityItem(Items.KNOWLEDGE_BOOK, "buildtools.menu.help", "buildtools.menu.help.description"));
    }

    private void populateAdvancedSelectionMenu() {
        boolean shared = owner != null && BuildToolsState.selectionVisibleToOthers(owner);
        menuItems.setItem(0, utilityItem(Items.BARRIER, "buildtools.menu.clear_advanced_points", "buildtools.menu.clear_advanced_points.description"));
        menuItems.setItem(1, utilityItem(Items.RED_DYE, "buildtools.menu.clear_selection", "buildtools.menu.clear_selection.description"));
        menuItems.setItem(2, utilityItem(Items.ENDER_EYE, "buildtools.menu.rotate_selection", "buildtools.menu.rotate_selection.description"));
        menuItems.setItem(3, utilityItem(Items.WRITABLE_BOOK, "buildtools.menu.save_preset", "buildtools.menu.save_preset.description"));
        menuItems.setItem(4, utilityItem(Items.BOOK, "buildtools.menu.presets", "buildtools.menu.presets.description"));
        menuItems.setItem(5, stateItem(
                Items.AMETHYST_SHARD,
                Component.translatable("buildtools.menu.custom_shape_mode").append(": ").append(owner == null
                        ? Component.translatable("buildtools.custom_shape_mode.auto")
                        : BuildToolsState.customShapeMode(owner).displayName()),
                Component.translatable("buildtools.menu.custom_shape_mode.description"),
                owner != null && BuildToolsState.selectionShape(owner) == SelectionShape.CUSTOM_SMART));
        populateShapes(SHAPE_START_SLOT);
        menuItems.setItem(27, NudgeMenuItems.item(owner, Direction.WEST, "buildtools.menu.nudge.description"));
        menuItems.setItem(28, NudgeMenuItems.item(owner, Direction.EAST, "buildtools.menu.nudge.description"));
        menuItems.setItem(29, NudgeMenuItems.item(owner, Direction.DOWN, "buildtools.menu.nudge.description"));
        menuItems.setItem(30, NudgeMenuItems.item(owner, Direction.UP, "buildtools.menu.nudge.description"));
        menuItems.setItem(31, NudgeMenuItems.item(owner, Direction.NORTH, "buildtools.menu.nudge.description"));
        menuItems.setItem(32, NudgeMenuItems.item(owner, Direction.SOUTH, "buildtools.menu.nudge.description"));
        menuItems.setItem(33, utilityItem(
                shared ? Items.ENDER_EYE : Items.ENDER_PEARL,
                "buildtools.menu.selection_visibility",
                "buildtools.menu.selection_visibility.description",
                shared));
        menuItems.setItem(36, NudgeMenuItems.expandItem(owner, Direction.WEST));
        menuItems.setItem(37, NudgeMenuItems.expandItem(owner, Direction.EAST));
        menuItems.setItem(38, NudgeMenuItems.expandItem(owner, Direction.DOWN));
        menuItems.setItem(39, NudgeMenuItems.expandItem(owner, Direction.UP));
        menuItems.setItem(40, NudgeMenuItems.expandItem(owner, Direction.NORTH));
        menuItems.setItem(41, NudgeMenuItems.expandItem(owner, Direction.SOUTH));
        menuItems.setItem(53, utilityItem(Items.KNOWLEDGE_BOOK, "buildtools.menu.help", "buildtools.menu.help.description"));
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
            } else {
                stack.set(DataComponents.CUSTOM_NAME, shapeName(shapes[i]));
            }
            setSelected(stack, owner != null && BuildToolsState.selectionShape(owner) == shapes[i]);
            menuItems.setItem(startSlot + i, stack);
        }
    }

    private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private Component shapeName(SelectionShape shape) {
        if (shape == SelectionShape.STAIRS && owner != null) {
            return shape.displayName().copy()
                    .append(": ")
                    .append(DirectionDisplay.stairDirection(owner, BuildToolsState.stairDirectionOverride(owner)));
        }
        return shape.displayName();
    }

    private static Component menuTitle(ServerPlayer player) {
        ItemStack heldTool = heldTool(player);
        if (!heldTool.isEmpty()) {
            return heldTool.getHoverName();
        }
        return Component.translatable("buildtools.menu.advanced_title");
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

    private static ItemStack stateItem(net.minecraft.world.item.Item item, Component name, Component description, boolean selected) {
        ItemStack stack = named(item, name);
        Component styledDescription = description.copy().withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(styledDescription), List.of(styledDescription)));
        setSelected(stack, selected);
        return stack;
    }

    private static void setSelected(ItemStack stack, boolean selected) {
        if (selected) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
    }

    private static boolean handleUtilityClick(ServerPlayer player, int slotId) {
        switch (slotId) {
            case 3 -> MaterialChecklistMenu.open(player);
            case 4 -> BuildToolsState.toggleGradient(player);
            case 5 -> BuildOperationEngine.createPlan(player);
            case 6 -> BuildOperationEngine.applyPlan(player);
            case 7 -> BuildToolsState.toggleRandomPattern(player);
            case 8 -> MaterialSelectionMenu.open(player);
            case 39 -> HelpMenu.open(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean handleSelectionClick(ServerPlayer player, int slotId, boolean rightClick) {
        switch (slotId) {
            case 0 -> BuildToolsState.clearAdvancedPoints(player);
            case 1 -> BuildToolsState.clearSelection(player);
            case 2 -> BuildToolsState.rotateSelection(player);
            case 3 -> BuildToolsState.savePreset(player);
            case 4 -> PresetLibraryMenu.open(player);
            case 5 -> BuildToolsState.cycleCustomShapeMode(player);
            case 27 -> BuildToolsState.nudgeSelection(player, Direction.WEST);
            case 28 -> BuildToolsState.nudgeSelection(player, Direction.EAST);
            case 29 -> BuildToolsState.nudgeSelection(player, Direction.DOWN);
            case 30 -> BuildToolsState.nudgeSelection(player, Direction.UP);
            case 31 -> BuildToolsState.nudgeSelection(player, Direction.NORTH);
            case 32 -> BuildToolsState.nudgeSelection(player, Direction.SOUTH);
            case 33 -> BuildToolsState.toggleSelectionVisibility(player);
            case 36 -> BuildToolsState.resizeSelection(player, Direction.WEST, 1);
            case 37 -> BuildToolsState.resizeSelection(player, Direction.EAST, 1);
            case 38 -> BuildToolsState.resizeSelection(player, Direction.DOWN, 1);
            case 39 -> BuildToolsState.resizeSelection(player, Direction.UP, 1);
            case 40 -> BuildToolsState.resizeSelection(player, Direction.NORTH, 1);
            case 41 -> BuildToolsState.resizeSelection(player, Direction.SOUTH, 1);
            case 53 -> HelpMenu.open(player);
            default -> {
                int shapeIndex = shapeIndex(slotId);
                SelectionShape[] shapes = visibleShapes();
                if (shapeIndex >= 0 && shapeIndex < shapes.length) {
                    handleShapeClick(player, shapes[shapeIndex], rightClick);
                    return true;
                }
                return false;
            }
        }
        return true;
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

    public void adjustGradientDirection(ServerPlayer player, int delta) {
        BuildToolsState.cycleGradientDirection(player, delta);
        populateMenuItems();
    }

    private static void handleShapeClick(ServerPlayer player, SelectionShape shape, boolean rightClick) {
        if (BuildToolsState.selectionShape(player) == shape && handleSelectedShapeOptionClick(player, shape, rightClick)) {
            return;
        }
        BuildToolsState.setShape(player, shape);
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
            default -> false;
        };
    }

    private boolean isAdvancedSelectionMenu() {
        return profile == ToolProfile.ADVANCED_SELECTION;
    }

    private SelectionShape[] visibleShapes() {
        if (owner != null) {
            return BuildToolsState.availableShapes(owner);
        }
        return isAdvancedSelectionMenu() ? SelectionShape.advancedSelectionShapes() : SelectionShape.shapesWithStairs();
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

    private static int shapeIndex(int slotId) {
        return slotId - SHAPE_START_SLOT;
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

    public static boolean isGradientSlot(int slot) {
        return slot == GRADIENT_SLOT;
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

    public boolean isGradientSlot(Slot slot) {
        return isGradientSlot(this.slots.indexOf(slot));
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
