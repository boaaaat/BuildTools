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

public final class StorageManagerMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 54;
    private static final int REFRESH_SLOT = 52;
    private static final int BACK_SLOT = 53;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ServerPlayer owner;
    private final boolean returnToToolMenu;
    private int pendingUnlinkIndex = -1;
    private List<BuildingStorageManager.LinkedStorage> storages = List.of();

    public StorageManagerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null, false);
    }

    private StorageManagerMenu(int containerId, Inventory inventory, ServerPlayer owner, boolean returnToToolMenu) {
        super(ModMenus.STORAGE_MANAGER_MENU.get(), containerId);
        this.owner = owner;
        this.returnToToolMenu = returnToToolMenu;
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        open(player, true);
    }

    public static void openStandalone(ServerPlayer player) {
        open(player, false);
    }

    private static void open(ServerPlayer player, boolean returnToToolMenu) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new StorageManagerMenu(containerId, inventory, player, returnToToolMenu),
                Component.translatable("buildtools.menu.storage_manager_title")));
    }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (slotId == BACK_SLOT) {
                if (returnToToolMenu) {
                    ToolMenuNavigation.openActiveToolMenu(serverPlayer);
                } else {
                    serverPlayer.closeContainer();
                }
                return;
            }
            if (slotId == REFRESH_SLOT) {
                pendingUnlinkIndex = -1;
                populateMenuItems();
                return;
            }
            if (slotId >= 0 && slotId < storages.size()) {
                if (pendingUnlinkIndex != slotId) {
                    pendingUnlinkIndex = slotId;
                    serverPlayer.displayClientMessage(Component.translatable("buildtools.message.storage_unlink_armed"), true);
                    populateMenuItems();
                    return;
                }
                BuildingStorageManager.LinkedStorage storage = storages.get(slotId);
                BuildingStorageManager.unlink(serverPlayer, storage.dimension(), storage.pos());
                pendingUnlinkIndex = -1;
                populateMenuItems();
                return;
            }
        }
        if (slotId >= MENU_SIZE) {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }

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
        menuItems.clearContent();
        storages = owner == null ? List.of() : BuildingStorageManager.linkedStorages(owner);
        if (storages.isEmpty()) {
            menuItems.setItem(0, named(Items.GRAY_DYE, Component.translatable("buildtools.menu.storage_manager_empty").withStyle(ChatFormatting.GRAY)));
        }
        for (int i = 0; i < storages.size() && i < 45; i++) {
            menuItems.setItem(i, storageItem(storages.get(i), i, pendingUnlinkIndex == i));
        }
        menuItems.setItem(REFRESH_SLOT, utilityItem(Items.CLOCK, "buildtools.menu.refresh", "buildtools.menu.refresh.description"));
        menuItems.setItem(BACK_SLOT, utilityItem(Items.ARROW, "buildtools.menu.back", "buildtools.menu.back.description"));
    }

    private static ItemStack storageItem(BuildingStorageManager.LinkedStorage storage, int index, boolean pending) {
        ItemStack stack = storage.icon().isEmpty() ? new ItemStack(Items.CHEST) : storage.icon().copyWithCount(1);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("buildtools.menu.storage_entry", index + 1)
                .withStyle(pending ? ChatFormatting.RED : storage.available() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.translatable("buildtools.menu.storage_dimension", storage.dimension().toString()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.translatable("buildtools.menu.storage_position", storage.pos().getX(), storage.pos().getY(), storage.pos().getZ()).withStyle(ChatFormatting.GRAY));
        Component availability = storage.available()
                ? Component.translatable("buildtools.menu.storage_available", storage.itemCount(), storage.freeSlots())
                : Component.translatable("buildtools.menu.storage_unavailable");
        lore.add(availability.copy().withStyle(storage.available() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        lore.add(Component.translatable(pending
                ? "buildtools.menu.storage_unlink_confirm"
                : "buildtools.menu.storage_unlink_help").withStyle(pending ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore, lore));
        if (pending) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey) {
        ItemStack stack = named(item, Component.translatable(nameKey));
        Component lore = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(lore), List.of(lore)));
        return stack;
    }

    private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private static final class FakeSlot extends Slot {
        private FakeSlot(SimpleContainer container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public boolean isFake() { return true; }
    }
}
