package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final int PAGE_SIZE = 45;
    private static final int SAVE_SLOT = 45;
    private static final int RENAME_SLOT = 46;
    private static final int DELETE_SLOT = 47;
    private static final int MOVE_UP_SLOT = 48;
    private static final int MOVE_DOWN_SLOT = 49;
    private static final int PREVIOUS_SLOT = 50;
    private static final int NEXT_SLOT = 51;
    private static final int BACK_SLOT = 53;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ServerPlayer owner;
    private boolean renameMode;
    private boolean deleteMode;
    private boolean moveUpMode;
    private boolean moveDownMode;
    private int pendingDeleteIndex = -1;
    private int page;
    private String searchQuery = "";
    private List<Integer> visibleIndices = List.of();

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
        visibleIndices = visibleIndices(presets);
        int maxPage = maxPage(visibleIndices.size());
        page = Math.max(0, Math.min(page, maxPage));
        int start = page * PAGE_SIZE;
        if (visibleIndices.isEmpty()) {
            menuItems.setItem(0, emptyItem(searchQuery.isBlank()
                    ? "buildtools.menu.preset_library_empty"
                    : "buildtools.menu.preset_no_results"));
        }
        for (int i = 0; i < PAGE_SIZE && start + i < visibleIndices.size(); i++) {
            int presetIndex = visibleIndices.get(start + i);
            NamedSelectionPreset preset = presets.get(presetIndex);
            ItemStack stack = new ItemStack(Items.MAP);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(preset.name()).withStyle(presetIndex == pendingDeleteIndex ? ChatFormatting.RED : ChatFormatting.AQUA));
            Component loadLore = Component.translatable("buildtools.menu.preset_entry.description", preset.preset().shape().displayName()).withStyle(ChatFormatting.GRAY);
            List<Component> lore = presetIndex == pendingDeleteIndex
                    ? List.of(loadLore, Component.translatable("buildtools.menu.delete_confirm").withStyle(ChatFormatting.RED))
                    : List.of(loadLore);
            stack.set(DataComponents.LORE, new ItemLore(lore, lore));
            if (presetIndex == pendingDeleteIndex) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            menuItems.setItem(i, stack);
        }
        menuItems.setItem(SAVE_SLOT, utilityItem(Items.LIME_DYE, "buildtools.menu.preset_create", "buildtools.menu.preset_create.description", false));
        menuItems.setItem(RENAME_SLOT, utilityItem(Items.ANVIL, "buildtools.menu.preset_rename", "buildtools.menu.preset_rename.description", renameMode));
        menuItems.setItem(DELETE_SLOT, utilityItem(Items.REDSTONE, "buildtools.menu.preset_delete", "buildtools.menu.preset_delete.description", deleteMode));
        menuItems.setItem(MOVE_UP_SLOT, utilityItem(Items.ARROW, "buildtools.menu.preset_move_up", "buildtools.menu.preset_move.description", moveUpMode));
        menuItems.setItem(MOVE_DOWN_SLOT, utilityItem(Items.HOPPER, "buildtools.menu.preset_move_down", "buildtools.menu.preset_move.description", moveDownMode));
        menuItems.setItem(PREVIOUS_SLOT, pageItem(Items.ARROW, "buildtools.menu.previous_page", page > 0, page, maxPage));
        menuItems.setItem(NEXT_SLOT, pageItem(Items.ARROW, "buildtools.menu.next_page", page < maxPage, page, maxPage));
        menuItems.setItem(BACK_SLOT, utilityItem(Items.ARROW, "buildtools.menu.back", "buildtools.menu.back.description", false));
    }

    private boolean handleClick(ServerPlayer player, int slotId) {
        if (slotId >= 0 && slotId < PAGE_SIZE) {
            int visibleIndex = page * PAGE_SIZE + slotId;
            if (visibleIndex < 0 || visibleIndex >= visibleIndices.size()) {
                return false;
            }
            int index = visibleIndices.get(visibleIndex);
            if (renameMode) {
                BuildToolsState.beginPresetRenamePrompt(player, index);
            } else if (deleteMode) {
                if (pendingDeleteIndex != index) {
                    pendingDeleteIndex = index;
                    player.displayClientMessage(Component.translatable("buildtools.message.delete_armed"), true);
                    return true;
                }
                BuildToolsState.deletePreset(player, index);
                deleteMode = false;
                pendingDeleteIndex = -1;
            } else if (moveUpMode) {
                BuildToolsState.movePreset(player, index, -1);
            } else if (moveDownMode) {
                BuildToolsState.movePreset(player, index, 1);
            } else {
                BuildToolsState.loadPreset(player, index);
            }
            return true;
        }
        switch (slotId) {
            case SAVE_SLOT -> {
                BuildToolsState.saveNewPreset(player);
            }
            case RENAME_SLOT -> renameMode = toggleExclusive(renameMode, 0);
            case DELETE_SLOT -> deleteMode = toggleExclusive(deleteMode, 1);
            case MOVE_UP_SLOT -> moveUpMode = toggleExclusive(moveUpMode, 2);
            case MOVE_DOWN_SLOT -> moveDownMode = toggleExclusive(moveDownMode, 3);
            case PREVIOUS_SLOT -> {
                if (page <= 0) {
                    return false;
                }
                page--;
                pendingDeleteIndex = -1;
            }
            case NEXT_SLOT -> {
                if (page >= maxPage(visibleIndices.size())) {
                    return false;
                }
                page++;
                pendingDeleteIndex = -1;
            }
            case BACK_SLOT -> ToolMenuNavigation.openActiveToolMenu(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    public void setSearchQuery(String query) {
        searchQuery = query == null ? "" : query.strip();
        page = 0;
        pendingDeleteIndex = -1;
        populateMenuItems();
    }

    private List<Integer> visibleIndices(List<NamedSelectionPreset> presets) {
        String query = searchQuery.toLowerCase(Locale.ROOT);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < presets.size(); i++) {
            if (query.isBlank() || presets.get(i).name().toLowerCase(Locale.ROOT).contains(query)) {
                indices.add(i);
            }
        }
        return List.copyOf(indices);
    }

    private boolean toggleExclusive(boolean current, int mode) {
        renameMode = mode == 0 && !current;
        deleteMode = mode == 1 && !current;
        moveUpMode = mode == 2 && !current;
        moveDownMode = mode == 3 && !current;
        pendingDeleteIndex = -1;
        return !current;
    }

    private static int maxPage(int size) {
        return Math.max(0, (size - 1) / PAGE_SIZE);
    }

    private static ItemStack pageItem(net.minecraft.world.item.Item item, String nameKey, boolean enabled, int page, int maxPage) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey).withStyle(enabled ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        Component description = Component.translatable("buildtools.menu.page.description", page + 1, maxPage + 1).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        return stack;
    }

    private static ItemStack emptyItem(String key) {
        ItemStack stack = new ItemStack(Items.GRAY_DYE);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(key).withStyle(ChatFormatting.GRAY));
        return stack;
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
