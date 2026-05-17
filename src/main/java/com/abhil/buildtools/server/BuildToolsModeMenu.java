package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModMenus;
import com.abhil.buildtools.shape.BuildMode;
import com.abhil.buildtools.shape.SelectionShape;
import java.util.List;
import net.minecraft.ChatFormatting;
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

public final class BuildToolsModeMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 27;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);

    public BuildToolsModeMenu(int containerId, Inventory inventory) {
        super(ModMenus.MODE_MENU.get(), containerId);
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new BuildToolsModeMenu(containerId, inventory),
                Component.translatable("buildtools.menu.title")));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < MENU_SIZE && player instanceof ServerPlayer serverPlayer) {
            if (slotId >= 0 && slotId < BuildMode.values().length) {
                BuildToolsState.setMode(serverPlayer, BuildMode.values()[slotId]);
                return;
            }
            int shapeIndex = slotId - 9;
            if (shapeIndex >= 0 && shapeIndex < SelectionShape.values().length) {
                BuildToolsState.setShape(serverPlayer, SelectionShape.values()[shapeIndex]);
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
        menuItems.setItem(0, modeItem(Items.LIME_STAINED_GLASS, BuildMode.FILL));
        menuItems.setItem(1, modeItem(Items.ORANGE_STAINED_GLASS, BuildMode.REPLACE));
        menuItems.setItem(2, modeItem(Items.RED_STAINED_GLASS, BuildMode.OVERWRITE));

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
