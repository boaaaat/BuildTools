package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModMenus;
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

public final class HelpMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 27;
    private static final int BACK_SLOT = 26;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);

    public HelpMenu(int containerId, Inventory inventory) {
        super(ModMenus.HELP_MENU.get(), containerId);
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new HelpMenu(containerId, inventory),
                Component.translatable("buildtools.menu.help_title")));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == BACK_SLOT && player instanceof ServerPlayer serverPlayer) {
            BuildToolsModeMenu.open(serverPlayer);
            return;
        }
        if (slotId >= MENU_SIZE) {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }

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
        menuItems.setItem(0, helpItem(Items.STICK, "buildtools.help.selection", "buildtools.help.selection.lines"));
        menuItems.setItem(1, helpItem(Items.BLAZE_ROD, "buildtools.help.builder", "buildtools.help.builder.lines"));
        menuItems.setItem(2, helpItem(Items.BRUSH, "buildtools.help.brush", "buildtools.help.brush.lines"));
        menuItems.setItem(3, helpItem(Items.IRON_PICKAXE, "buildtools.help.breaker", "buildtools.help.breaker.lines"));
        menuItems.setItem(4, helpItem(Items.MAP, "buildtools.help.blueprints", "buildtools.help.blueprints.lines"));
        menuItems.setItem(5, helpItem(Items.CLOCK, "buildtools.help.history", "buildtools.help.history.lines"));
        menuItems.setItem(BACK_SLOT, utilityItem(Items.ARROW, "buildtools.menu.back", "buildtools.menu.back.description"));
    }

    private static ItemStack helpItem(net.minecraft.world.item.Item item, String nameKey, String linesKey) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey).withStyle(ChatFormatting.AQUA));
        List<Component> lore = List.of(Component.translatable(linesKey).withStyle(ChatFormatting.GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore, lore));
        return stack;
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey));
        Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        return stack;
    }

    private static final class FakeSlot extends Slot {
        private FakeSlot(SimpleContainer container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public boolean isFake() { return true; }
    }
}
