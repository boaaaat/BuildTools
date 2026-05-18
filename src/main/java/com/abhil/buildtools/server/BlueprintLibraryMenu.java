package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModMenus;
import java.util.ArrayList;
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

public final class BlueprintLibraryMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 54;
    private static final int BLUEPRINT_SLOTS = 45;
    private static final int CREATE_SLOT = 45;
    private static final int DELETE_SLOT = 49;
    private static final int BACK_SLOT = 53;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ServerPlayer owner;
    private boolean deleteMode;

    public BlueprintLibraryMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    }

    private BlueprintLibraryMenu(int containerId, Inventory inventory, ServerPlayer owner) {
        super(ModMenus.BLUEPRINT_LIBRARY_MENU.get(), containerId);
        this.owner = owner;
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new BlueprintLibraryMenu(containerId, inventory, player),
                Component.translatable("buildtools.menu.blueprints_title")));
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
        List<SavedBlueprint> blueprints = owner == null ? List.of() : BuildToolsState.savedBlueprints(owner);
        String activeName = owner == null ? null : BuildToolsState.activeBlueprintName(owner).orElse(null);
        for (int i = 0; i < Math.min(BLUEPRINT_SLOTS, blueprints.size()); i++) {
            SavedBlueprint saved = blueprints.get(i);
            ItemStack stack = blueprintItem(saved, i, saved.name().equals(activeName));
            menuItems.setItem(i, stack);
        }
        menuItems.setItem(CREATE_SLOT, utilityItem(Items.LIME_DYE, "buildtools.menu.blueprint_create", "buildtools.menu.blueprint_create.description", false));
        menuItems.setItem(DELETE_SLOT, utilityItem(
                deleteMode ? Items.REDSTONE_TORCH : Items.REDSTONE,
                "buildtools.menu.blueprint_delete_mode",
                "buildtools.menu.blueprint_delete_mode.description",
                deleteMode));
        menuItems.setItem(BACK_SLOT, utilityItem(Items.ARROW, "buildtools.menu.back", "buildtools.menu.back.description", false));
    }

    private boolean handleClick(ServerPlayer player, int slotId) {
        if (slotId >= 0 && slotId < BLUEPRINT_SLOTS) {
            if (deleteMode) {
                BuildToolsState.deleteSavedBlueprint(player, slotId);
            } else {
                BuildToolsState.loadSavedBlueprint(player, slotId);
            }
            return true;
        }
        switch (slotId) {
            case CREATE_SLOT -> {
                return BuildOperationEngine.beginSavedBlueprintCreate(player);
            }
            case DELETE_SLOT -> {
                deleteMode = !deleteMode;
                return true;
            }
            case BACK_SLOT -> {
                BuildToolsModeMenu.open(player);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static ItemStack blueprintItem(SavedBlueprint saved, int index, boolean selected) {
        ItemStack stack = new ItemStack(selected ? Items.FILLED_MAP : Items.MAP);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(saved.name()).withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("buildtools.menu.blueprint_entry.description", index + 1).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal(saved.blueprint().entries().size() + " blocks, " + saved.blueprint().entities().size() + " entities").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore, lore));
        setSelected(stack, selected);
        return stack;
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey, boolean selected) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey));
        Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        setSelected(stack, selected);
        return stack;
    }

    private static void setSelected(ItemStack stack, boolean selected) {
        if (selected) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
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
