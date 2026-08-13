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
import net.minecraft.world.level.block.Blocks;

public final class PaletteLibraryMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 54;
    private static final int ACTIVE_SLOTS = BuildToolsState.MAX_MATERIAL_SELECTIONS;
    private static final int SAVED_START_SLOT = 18;
    private static final int SAVED_PAGE_SIZE = 27;
    private static final int SAVED_DELETE_KEY_OFFSET = 1000;
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
    private int pendingDeleteKey = -1;
    private int page;
    private String searchQuery = "";
    private List<Integer> visibleSavedIndices = List.of();

    public PaletteLibraryMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    }

    private PaletteLibraryMenu(int containerId, Inventory inventory, ServerPlayer owner) {
        super(ModMenus.PALETTE_LIBRARY_MENU.get(), containerId);
        this.owner = owner;
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new PaletteLibraryMenu(containerId, inventory, player),
                Component.translatable("buildtools.menu.palettes_title")));
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
        if (owner == null) {
            return;
        }
        List<PaletteEntry> active = BuildToolsState.paletteEntries(owner);
        for (int i = 0; i < Math.min(ACTIVE_SLOTS, active.size()); i++) {
            PaletteEntry entry = active.get(i);
            ItemStack stack = new ItemStack(entry.state().is(Blocks.WATER) ? Items.WATER_BUCKET : entry.state().is(Blocks.LAVA) ? Items.LAVA_BUCKET : entry.state().getBlock().asItem());
            boolean pendingDelete = pendingDeleteKey == i;
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("buildtools.menu.palette_active_entry", i + 1, entry.state().getBlock().getName())
                    .withStyle(pendingDelete ? ChatFormatting.RED : ChatFormatting.GREEN));
            Component entryLore = Component.translatable("buildtools.menu.palette_active_entry.description", entry.weight()).withStyle(ChatFormatting.GRAY);
            List<Component> lore = pendingDelete
                    ? List.of(entryLore, Component.translatable("buildtools.menu.delete_confirm").withStyle(ChatFormatting.RED))
                    : List.of(entryLore);
            stack.set(DataComponents.LORE, new ItemLore(lore, lore));
            if (pendingDelete) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            menuItems.setItem(i, stack);
        }
        List<SavedPalette> saved = BuildToolsState.savedPalettes(owner);
        if (active.isEmpty() && saved.isEmpty()) {
            menuItems.setItem(0, emptyItem("buildtools.menu.palette_library_empty"));
        }
        visibleSavedIndices = visibleSavedIndices(saved);
        if (!searchQuery.isBlank() && visibleSavedIndices.isEmpty()) {
            menuItems.setItem(SAVED_START_SLOT, emptyItem("buildtools.menu.palette_no_results"));
        }
        int maxPage = maxPage(visibleSavedIndices.size());
        page = Math.max(0, Math.min(page, maxPage));
        int start = page * SAVED_PAGE_SIZE;
        for (int i = 0; i < SAVED_PAGE_SIZE && start + i < visibleSavedIndices.size(); i++) {
            int paletteIndex = visibleSavedIndices.get(start + i);
            SavedPalette palette = saved.get(paletteIndex);
            ItemStack stack = new ItemStack(Items.PAINTING);
            boolean pendingDelete = pendingDeleteKey == savedDeleteKey(paletteIndex);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(palette.name()).withStyle(pendingDelete ? ChatFormatting.RED : ChatFormatting.AQUA));
            Component entryLore = Component.translatable("buildtools.menu.palette_entry.description", palette.entries().size()).withStyle(ChatFormatting.GRAY);
            List<Component> lore = pendingDelete
                    ? List.of(entryLore, Component.translatable("buildtools.menu.delete_confirm").withStyle(ChatFormatting.RED))
                    : List.of(entryLore);
            stack.set(DataComponents.LORE, new ItemLore(lore, lore));
            if (pendingDelete) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            menuItems.setItem(SAVED_START_SLOT + i, stack);
        }
        menuItems.setItem(SAVE_SLOT, utilityItem(Items.LIME_DYE, "buildtools.menu.palette_profile_save", "buildtools.menu.palette_profile_save.description", false));
        menuItems.setItem(RENAME_SLOT, utilityItem(Items.ANVIL, "buildtools.menu.palette_profile_rename", "buildtools.menu.palette_profile_rename.description", renameMode));
        menuItems.setItem(DELETE_SLOT, utilityItem(Items.REDSTONE, "buildtools.menu.palette_profile_delete", "buildtools.menu.palette_profile_delete.description", deleteMode));
        menuItems.setItem(MOVE_UP_SLOT, utilityItem(Items.ARROW, "buildtools.menu.palette_profile_move_up", "buildtools.menu.palette_profile_move.description", moveUpMode));
        menuItems.setItem(MOVE_DOWN_SLOT, utilityItem(Items.HOPPER, "buildtools.menu.palette_profile_move_down", "buildtools.menu.palette_profile_move.description", moveDownMode));
        menuItems.setItem(PREVIOUS_SLOT, pageItem(Items.ARROW, "buildtools.menu.previous_page", page > 0, page, maxPage));
        menuItems.setItem(NEXT_SLOT, pageItem(Items.ARROW, "buildtools.menu.next_page", page < maxPage, page, maxPage));
        menuItems.setItem(BACK_SLOT, utilityItem(Items.ARROW, "buildtools.menu.back", "buildtools.menu.back.description", false));
    }

    private boolean handleClick(ServerPlayer player, int slotId) {
        if (slotId >= 0 && slotId < ACTIVE_SLOTS) {
            if (slotId >= BuildToolsState.paletteEntries(player).size()) {
                return false;
            }
            if (deleteMode) {
                if (pendingDeleteKey != slotId) {
                    pendingDeleteKey = slotId;
                    player.displayClientMessage(Component.translatable("buildtools.message.delete_armed"), true);
                    return true;
                }
                BuildToolsState.removePaletteEntry(player, slotId);
                deleteMode = false;
                pendingDeleteKey = -1;
            } else if (moveUpMode) {
                BuildToolsState.movePaletteEntry(player, slotId, -1);
            } else if (moveDownMode) {
                BuildToolsState.movePaletteEntry(player, slotId, 1);
            } else {
                return false;
            }
            return true;
        }
        if (slotId >= SAVED_START_SLOT && slotId < SAVED_START_SLOT + SAVED_PAGE_SIZE) {
            int visibleIndex = page * SAVED_PAGE_SIZE + slotId - SAVED_START_SLOT;
            if (visibleIndex < 0 || visibleIndex >= visibleSavedIndices.size()) {
                return false;
            }
            int index = visibleSavedIndices.get(visibleIndex);
            if (renameMode) {
                BuildToolsState.beginPaletteRenamePrompt(player, index);
            } else if (deleteMode) {
                int deleteKey = savedDeleteKey(index);
                if (pendingDeleteKey != deleteKey) {
                    pendingDeleteKey = deleteKey;
                    player.displayClientMessage(Component.translatable("buildtools.message.delete_armed"), true);
                    return true;
                }
                BuildToolsState.deleteSavedPalette(player, index);
                deleteMode = false;
                pendingDeleteKey = -1;
            } else if (moveUpMode) {
                BuildToolsState.moveSavedPalette(player, index, -1);
            } else if (moveDownMode) {
                BuildToolsState.moveSavedPalette(player, index, 1);
            } else {
                BuildToolsState.loadSavedPalette(player, index);
            }
            return true;
        }
        switch (slotId) {
            case SAVE_SLOT -> {
                BuildToolsState.saveCurrentPalette(player);
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
                pendingDeleteKey = -1;
            }
            case NEXT_SLOT -> {
                if (page >= maxPage(visibleSavedIndices.size())) {
                    return false;
                }
                page++;
                pendingDeleteKey = -1;
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
        pendingDeleteKey = -1;
        populateMenuItems();
    }

    private List<Integer> visibleSavedIndices(List<SavedPalette> palettes) {
        String query = searchQuery.toLowerCase(Locale.ROOT);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < palettes.size(); i++) {
            if (query.isBlank() || palettes.get(i).name().toLowerCase(Locale.ROOT).contains(query)) {
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
        pendingDeleteKey = -1;
        return !current;
    }

    private static int maxPage(int size) {
        return Math.max(0, (size - 1) / SAVED_PAGE_SIZE);
    }

    private static int savedDeleteKey(int index) {
        return SAVED_DELETE_KEY_OFFSET + index;
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
        private FakeSlot(SimpleContainer container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public boolean isFake() { return true; }
    }
}
