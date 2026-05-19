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

public final class PresetLibraryMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 54;
    private static final int SAVE_SLOT = 45;
    private static final int RENAME_SLOT = 46;
    private static final int DELETE_SLOT = 47;
    private static final int MOVE_UP_SLOT = 48;
    private static final int MOVE_DOWN_SLOT = 49;
    private static final int BACK_SLOT = 53;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ServerPlayer owner;
    private boolean renameMode;
    private boolean deleteMode;
    private boolean moveUpMode;
    private boolean moveDownMode;

    public PresetLibraryMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    }

    private PresetLibraryMenu(int containerId, Inventory inventory, ServerPlayer owner) {
        super(ModMenus.PRESET_LIBRARY_MENU.get(), containerId);
        this.owner = owner;
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new PresetLibraryMenu(containerId, inventory, player),
                Component.translatable("buildtools.menu.presets_title")));
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
        List<NamedSelectionPreset> presets = owner == null ? List.of() : BuildToolsState.selectionPresets(owner);
        for (int i = 0; i < Math.min(45, presets.size()); i++) {
            NamedSelectionPreset preset = presets.get(i);
            ItemStack stack = new ItemStack(Items.MAP);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(preset.name()).withStyle(ChatFormatting.AQUA));
            Component lore = Component.literal("Click to load. Shape: " + preset.preset().shape().name()).withStyle(ChatFormatting.GRAY);
            stack.set(DataComponents.LORE, new ItemLore(List.of(lore), List.of(lore)));
            menuItems.setItem(i, stack);
        }
        menuItems.setItem(SAVE_SLOT, utilityItem(Items.LIME_DYE, "buildtools.menu.preset_create", "buildtools.menu.preset_create.description", false));
        menuItems.setItem(RENAME_SLOT, utilityItem(Items.ANVIL, "buildtools.menu.preset_rename", "buildtools.menu.preset_rename.description", renameMode));
        menuItems.setItem(DELETE_SLOT, utilityItem(Items.REDSTONE, "buildtools.menu.preset_delete", "buildtools.menu.preset_delete.description", deleteMode));
        menuItems.setItem(MOVE_UP_SLOT, utilityItem(Items.ARROW, "buildtools.menu.preset_move_up", "buildtools.menu.preset_move.description", moveUpMode));
        menuItems.setItem(MOVE_DOWN_SLOT, utilityItem(Items.HOPPER, "buildtools.menu.preset_move_down", "buildtools.menu.preset_move.description", moveDownMode));
        menuItems.setItem(BACK_SLOT, utilityItem(Items.ARROW, "buildtools.menu.back", "buildtools.menu.back.description", false));
    }

    private boolean handleClick(ServerPlayer player, int slotId) {
        if (slotId >= 0 && slotId < 45) {
            if (renameMode) {
                BuildToolsState.beginPresetRenamePrompt(player, slotId);
            } else if (deleteMode) {
                BuildToolsState.deletePreset(player, slotId);
            } else if (moveUpMode) {
                BuildToolsState.movePreset(player, slotId, -1);
            } else if (moveDownMode) {
                BuildToolsState.movePreset(player, slotId, 1);
            } else {
                BuildToolsState.loadPreset(player, slotId);
            }
            return true;
        }
        switch (slotId) {
            case SAVE_SLOT -> BuildToolsState.saveNewPreset(player);
            case RENAME_SLOT -> renameMode = toggleExclusive(renameMode, 0);
            case DELETE_SLOT -> deleteMode = toggleExclusive(deleteMode, 1);
            case MOVE_UP_SLOT -> moveUpMode = toggleExclusive(moveUpMode, 2);
            case MOVE_DOWN_SLOT -> moveDownMode = toggleExclusive(moveDownMode, 3);
            case BACK_SLOT -> ToolMenuNavigation.openActiveToolMenu(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean toggleExclusive(boolean current, int mode) {
        renameMode = mode == 0 && !current;
        deleteMode = mode == 1 && !current;
        moveUpMode = mode == 2 && !current;
        moveDownMode = mode == 3 && !current;
        return !current;
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey, boolean selected) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey));
        Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        if (selected) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    private static final class FakeSlot extends Slot {
        private FakeSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public boolean isFake() { return true; }
    }
}
