package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModMenus;
import com.abhil.buildtools.shape.BuildMode;
import com.abhil.buildtools.shape.SelectionShape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;

public final class AdvancedBuildToolsModeMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 54;
    private static final int FILTER_START = 48;
    private static final int FILTER_COUNT = 6;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ServerPlayer owner;

    public AdvancedBuildToolsModeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    }

    private AdvancedBuildToolsModeMenu(int containerId, Inventory inventory, ServerPlayer owner) {
        super(ModMenus.ADVANCED_MODE_MENU.get(), containerId);
        this.owner = owner;
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new AdvancedBuildToolsModeMenu(containerId, inventory, player),
                Component.translatable("buildtools.menu.advanced_title")));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < MENU_SIZE && player instanceof ServerPlayer serverPlayer && !isFilterSlot(slotId)) {
            if (slotId >= 0 && slotId < BuildMode.values().length) {
                BuildToolsState.setMode(serverPlayer, BuildMode.values()[slotId]);
                return;
            }
            if (handleUtilityClick(serverPlayer, slotId)) {
                populateMenuItems();
                return;
            }
            int shapeIndex = slotId - 9;
            if (shapeIndex >= 0 && shapeIndex < SelectionShape.values().length) {
                BuildToolsState.setShape(serverPlayer, SelectionShape.values()[shapeIndex]);
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
        if (owner != null) {
            syncFilterSlots(owner);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < MENU_SIZE || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = this.slots.get(index).getItem();
        if (!(source.getItem() instanceof BlockItem)) {
            return ItemStack.EMPTY;
        }
        for (int slot = FILTER_START; slot < FILTER_START + FILTER_COUNT; slot++) {
            if (menuItems.getItem(slot).isEmpty()) {
                ItemStack original = source.copy();
                ItemStack copy = source.copyWithCount(1);
                source.shrink(1);
                menuItems.setItem(slot, copy);
                if (owner != null) {
                    syncFilterSlots(owner);
                }
                return original;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            syncFilterSlots(serverPlayer);
            for (int slot = FILTER_START; slot < FILTER_START + FILTER_COUNT; slot++) {
                ItemStack stack = menuItems.removeItemNoUpdate(slot);
                if (!stack.isEmpty() && !serverPlayer.getInventory().add(stack)) {
                    serverPlayer.drop(stack, false);
                }
            }
        }
        super.removed(player);
    }

    private void addMenuSlots() {
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = column + row * 9;
                if (isFilterSlot(slot)) {
                    addSlot(new FilterSlot(menuItems, slot, 8 + column * 18, 18 + row * 18));
                } else {
                    addSlot(new FakeSlot(menuItems, slot, 8 + column * 18, 18 + row * 18));
                }
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
        for (int i = 0; i < FILTER_START; i++) {
            menuItems.setItem(i, ItemStack.EMPTY);
        }
        menuItems.setItem(0, modeItem(Items.LIME_STAINED_GLASS, BuildMode.FILL));
        menuItems.setItem(1, modeItem(Items.ORANGE_STAINED_GLASS, BuildMode.REPLACE));
        menuItems.setItem(2, modeItem(Items.RED_STAINED_GLASS, BuildMode.OVERWRITE));
        menuItems.setItem(3, utilityItem(Items.PAPER, "buildtools.menu.materials", "buildtools.menu.materials.description"));
        menuItems.setItem(4, utilityItem(Items.WRITABLE_BOOK, "buildtools.menu.save_preset", "buildtools.menu.save_preset.description"));
        menuItems.setItem(5, utilityItem(Items.BOOK, "buildtools.menu.load_preset", "buildtools.menu.load_preset.description"));
        menuItems.setItem(6, utilityItem(Items.MAP, "buildtools.menu.save_plan", "buildtools.menu.save_plan.description"));
        menuItems.setItem(7, utilityItem(Items.FILLED_MAP, "buildtools.menu.build_plan", "buildtools.menu.build_plan.description"));
        menuItems.setItem(8, utilityItem(Items.ENDER_EYE, "buildtools.menu.rotate_selection", "buildtools.menu.rotate_selection.description"));

        SelectionShape[] shapes = SelectionShape.values();
        ItemStack[] icons = new ItemStack[] {
                new ItemStack(Items.STONE),
                new ItemStack(Items.BRICKS),
                new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.GLASS),
                new ItemStack(Items.SCAFFOLDING),
                new ItemStack(Items.STRING),
                new ItemStack(Items.COPPER_BLOCK),
                new ItemStack(Items.SNOWBALL),
                new ItemStack(Items.SLIME_BALL)
        };
        for (int i = 0; i < shapes.length; i++) {
            ItemStack stack = icons[i].copy();
            stack.set(DataComponents.CUSTOM_NAME, shapes[i].displayName());
            menuItems.setItem(9 + i, stack);
        }

        menuItems.setItem(18, utilityItem(Items.ARROW, "buildtools.menu.nudge_west", "buildtools.menu.nudge.description"));
        menuItems.setItem(19, utilityItem(Items.ARROW, "buildtools.menu.nudge_east", "buildtools.menu.nudge.description"));
        menuItems.setItem(20, utilityItem(Items.ARROW, "buildtools.menu.nudge_down", "buildtools.menu.nudge.description"));
        menuItems.setItem(21, utilityItem(Items.ARROW, "buildtools.menu.nudge_up", "buildtools.menu.nudge.description"));
        menuItems.setItem(22, utilityItem(Items.ARROW, "buildtools.menu.nudge_north", "buildtools.menu.nudge.description"));
        menuItems.setItem(23, utilityItem(Items.ARROW, "buildtools.menu.nudge_south", "buildtools.menu.nudge.description"));

        menuItems.setItem(27, utilityItem(Items.LIME_DYE, "buildtools.menu.expand_west", "buildtools.menu.expand.description"));
        menuItems.setItem(28, utilityItem(Items.LIME_DYE, "buildtools.menu.expand_east", "buildtools.menu.expand.description"));
        menuItems.setItem(29, utilityItem(Items.LIME_DYE, "buildtools.menu.expand_down", "buildtools.menu.expand.description"));
        menuItems.setItem(30, utilityItem(Items.LIME_DYE, "buildtools.menu.expand_up", "buildtools.menu.expand.description"));
        menuItems.setItem(31, utilityItem(Items.LIME_DYE, "buildtools.menu.expand_north", "buildtools.menu.expand.description"));
        menuItems.setItem(32, utilityItem(Items.LIME_DYE, "buildtools.menu.expand_south", "buildtools.menu.expand.description"));

        menuItems.setItem(36, utilityItem(Items.RED_DYE, "buildtools.menu.shrink_west", "buildtools.menu.shrink.description"));
        menuItems.setItem(37, utilityItem(Items.RED_DYE, "buildtools.menu.shrink_east", "buildtools.menu.shrink.description"));
        menuItems.setItem(38, utilityItem(Items.RED_DYE, "buildtools.menu.shrink_down", "buildtools.menu.shrink.description"));
        menuItems.setItem(39, utilityItem(Items.RED_DYE, "buildtools.menu.shrink_up", "buildtools.menu.shrink.description"));
        menuItems.setItem(40, utilityItem(Items.RED_DYE, "buildtools.menu.shrink_north", "buildtools.menu.shrink.description"));
        menuItems.setItem(41, utilityItem(Items.RED_DYE, "buildtools.menu.shrink_south", "buildtools.menu.shrink.description"));

        menuItems.setItem(45, utilityItem(Items.CLOCK, "buildtools.menu.rotate_blueprint", "buildtools.menu.rotate_blueprint.description"));
        menuItems.setItem(46, utilityItem(Items.IRON_BARS, "buildtools.menu.mirror_blueprint_x", "buildtools.menu.mirror_blueprint.description"));
        menuItems.setItem(47, utilityItem(Items.CHAIN, "buildtools.menu.mirror_blueprint_z", "buildtools.menu.mirror_blueprint.description"));
    }

    private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private static ItemStack modeItem(net.minecraft.world.item.Item item, BuildMode mode) {
        ItemStack stack = named(item, mode.displayName());
        Component description = mode.description().copy().withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        return stack;
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey) {
        ItemStack stack = named(item, Component.translatable(nameKey));
        Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        return stack;
    }

    private static boolean handleUtilityClick(ServerPlayer player, int slotId) {
        switch (slotId) {
            case 3 -> BuildToolsState.sendPreview(player);
            case 4 -> BuildToolsState.savePreset(player);
            case 5 -> BuildToolsState.loadPreset(player);
            case 6 -> BuildOperationEngine.createPlan(player);
            case 7 -> BuildOperationEngine.applyPlan(player);
            case 8 -> BuildToolsState.rotateSelection(player);
            case 18 -> BuildToolsState.nudgeSelection(player, Direction.WEST);
            case 19 -> BuildToolsState.nudgeSelection(player, Direction.EAST);
            case 20 -> BuildToolsState.nudgeSelection(player, Direction.DOWN);
            case 21 -> BuildToolsState.nudgeSelection(player, Direction.UP);
            case 22 -> BuildToolsState.nudgeSelection(player, Direction.NORTH);
            case 23 -> BuildToolsState.nudgeSelection(player, Direction.SOUTH);
            case 27 -> BuildToolsState.resizeSelection(player, Direction.WEST, 1);
            case 28 -> BuildToolsState.resizeSelection(player, Direction.EAST, 1);
            case 29 -> BuildToolsState.resizeSelection(player, Direction.DOWN, 1);
            case 30 -> BuildToolsState.resizeSelection(player, Direction.UP, 1);
            case 31 -> BuildToolsState.resizeSelection(player, Direction.NORTH, 1);
            case 32 -> BuildToolsState.resizeSelection(player, Direction.SOUTH, 1);
            case 36 -> BuildToolsState.resizeSelection(player, Direction.WEST, -1);
            case 37 -> BuildToolsState.resizeSelection(player, Direction.EAST, -1);
            case 38 -> BuildToolsState.resizeSelection(player, Direction.DOWN, -1);
            case 39 -> BuildToolsState.resizeSelection(player, Direction.UP, -1);
            case 40 -> BuildToolsState.resizeSelection(player, Direction.NORTH, -1);
            case 41 -> BuildToolsState.resizeSelection(player, Direction.SOUTH, -1);
            case 45 -> BuildToolsState.rotateBlueprint(player);
            case 46 -> BuildToolsState.mirrorBlueprintX(player);
            case 47 -> BuildToolsState.mirrorBlueprintZ(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void syncFilterSlots(ServerPlayer player) {
        List<BlockState> states = new ArrayList<>();
        for (int slot = FILTER_START; slot < FILTER_START + FILTER_COUNT; slot++) {
            ItemStack stack = menuItems.getItem(slot);
            if (stack.getItem() instanceof BlockItem blockItem) {
                states.add(blockItem.getBlock().defaultBlockState());
            }
        }
        BuildToolsState.setReplaceTargets(player, states);
    }

    private static boolean isFilterSlot(int slot) {
        return slot >= FILTER_START && slot < FILTER_START + FILTER_COUNT;
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

    private static final class FilterSlot extends Slot {
        private FilterSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof BlockItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
