package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModMenus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final int SORT_SLOT = 46;
    private static final int CATEGORY_SLOT = 47;
    private static final int RENAME_SLOT = 48;
    private static final int DELETE_SLOT = 49;
    private static final int MOVE_UP_SLOT = 50;
    private static final int MOVE_DOWN_SLOT = 51;
    private static final int SET_CATEGORY_SLOT = 52;
    private static final int BACK_SLOT = 53;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ServerPlayer owner;
    private boolean deleteMode;
    private boolean renameMode;
    private boolean moveUpMode;
    private boolean moveDownMode;
    private boolean categoryAssignMode;
    private String searchQuery = "";
    private String categoryFilter = "";
    private SortMode sortMode = SortMode.RECENT;
    private List<Integer> visibleIndices = List.of();

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
        visibleIndices = visibleIndices(blueprints);
        for (int i = 0; i < Math.min(BLUEPRINT_SLOTS, visibleIndices.size()); i++) {
            int originalIndex = visibleIndices.get(i);
            SavedBlueprint saved = blueprints.get(originalIndex);
            ItemStack stack = blueprintItem(saved, originalIndex, saved.name().equals(activeName));
            menuItems.setItem(i, stack);
        }
        menuItems.setItem(CREATE_SLOT, utilityItem(Items.LIME_DYE, "buildtools.menu.blueprint_create", "buildtools.menu.blueprint_create.description", false));
        menuItems.setItem(SORT_SLOT, named(Items.COMPASS, Component.translatable("buildtools.menu.blueprint_sort").append(": ").append(sortMode.displayName)));
        menuItems.setItem(CATEGORY_SLOT, named(Items.NAME_TAG, Component.translatable("buildtools.menu.blueprint_category_filter").append(": ").append(categoryFilter.isBlank() ? Component.translatable("buildtools.menu.blueprint_category_all") : Component.literal(categoryFilter))));
        menuItems.setItem(RENAME_SLOT, utilityItem(Items.ANVIL, "buildtools.menu.blueprint_rename", "buildtools.menu.blueprint_rename.description", renameMode));
        menuItems.setItem(DELETE_SLOT, utilityItem(
                deleteMode ? Items.REDSTONE_TORCH : Items.REDSTONE,
                "buildtools.menu.blueprint_delete_mode",
                "buildtools.menu.blueprint_delete_mode.description",
                deleteMode));
        menuItems.setItem(MOVE_UP_SLOT, utilityItem(Items.ARROW, "buildtools.menu.blueprint_move_up", "buildtools.menu.blueprint_move.description", moveUpMode));
        menuItems.setItem(MOVE_DOWN_SLOT, utilityItem(Items.HOPPER, "buildtools.menu.blueprint_move_down", "buildtools.menu.blueprint_move.description", moveDownMode));
        menuItems.setItem(SET_CATEGORY_SLOT, utilityItem(Items.BOOK, "buildtools.menu.blueprint_set_category", "buildtools.menu.blueprint_set_category.description", categoryAssignMode));
        menuItems.setItem(BACK_SLOT, utilityItem(Items.ARROW, "buildtools.menu.back", "buildtools.menu.back.description", false));
    }

    private boolean handleClick(ServerPlayer player, int slotId) {
        if (slotId >= 0 && slotId < BLUEPRINT_SLOTS) {
            if (slotId >= visibleIndices.size()) {
                return false;
            }
            int originalIndex = visibleIndices.get(slotId);
            if (deleteMode) {
                BuildToolsState.deleteSavedBlueprint(player, originalIndex);
            } else if (renameMode) {
                BuildToolsState.beginBlueprintRenamePrompt(player, originalIndex);
            } else if (moveUpMode) {
                BuildToolsState.moveSavedBlueprint(player, originalIndex, -1);
            } else if (moveDownMode) {
                BuildToolsState.moveSavedBlueprint(player, originalIndex, 1);
            } else if (categoryAssignMode) {
                BuildToolsState.beginBlueprintCategoryPrompt(player, originalIndex);
            } else {
                BuildToolsState.loadSavedBlueprint(player, originalIndex);
            }
            return true;
        }
        switch (slotId) {
            case CREATE_SLOT -> {
                return BuildOperationEngine.beginSavedBlueprintCreate(player);
            }
            case SORT_SLOT -> {
                sortMode = sortMode.next();
                return true;
            }
            case CATEGORY_SLOT -> {
                cycleCategoryFilter(player);
                return true;
            }
            case RENAME_SLOT -> {
                renameMode = !renameMode;
                deleteMode = false;
                moveUpMode = false;
                moveDownMode = false;
                categoryAssignMode = false;
                return true;
            }
            case DELETE_SLOT -> {
                deleteMode = !deleteMode;
                renameMode = false;
                moveUpMode = false;
                moveDownMode = false;
                categoryAssignMode = false;
                return true;
            }
            case MOVE_UP_SLOT -> {
                moveUpMode = !moveUpMode;
                deleteMode = false;
                renameMode = false;
                moveDownMode = false;
                categoryAssignMode = false;
                return true;
            }
            case MOVE_DOWN_SLOT -> {
                moveDownMode = !moveDownMode;
                deleteMode = false;
                renameMode = false;
                moveUpMode = false;
                categoryAssignMode = false;
                return true;
            }
            case SET_CATEGORY_SLOT -> {
                categoryAssignMode = !categoryAssignMode;
                deleteMode = false;
                renameMode = false;
                moveUpMode = false;
                moveDownMode = false;
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

    public void setSearchQuery(String query) {
        searchQuery = query == null ? "" : query.strip();
        populateMenuItems();
    }

    private List<Integer> visibleIndices(List<SavedBlueprint> blueprints) {
        String query = searchQuery.toLowerCase(Locale.ROOT);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < blueprints.size(); i++) {
            SavedBlueprint saved = blueprints.get(i);
            if (!categoryFilter.isBlank() && !saved.category().equalsIgnoreCase(categoryFilter)) {
                continue;
            }
            if (!query.isBlank()
                    && !saved.name().toLowerCase(Locale.ROOT).contains(query)
                    && !saved.category().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            indices.add(i);
        }
        indices.sort(sortMode.comparator(blueprints));
        return List.copyOf(indices);
    }

    private void cycleCategoryFilter(ServerPlayer player) {
        List<SavedBlueprint> saved = BuildToolsState.savedBlueprints(player);
        Set<String> categories = new LinkedHashSet<>();
        for (SavedBlueprint blueprint : saved) {
            categories.add(blueprint.category());
        }
        List<String> options = new ArrayList<>(categories);
        if (options.isEmpty()) {
            categoryFilter = "";
            return;
        }
        if (categoryFilter.isBlank()) {
            categoryFilter = options.getFirst();
            return;
        }
        int index = options.indexOf(categoryFilter);
        categoryFilter = index < 0 || index + 1 >= options.size() ? "" : options.get(index + 1);
    }

    private static ItemStack blueprintItem(SavedBlueprint saved, int index, boolean selected) {
        ItemStack stack = new ItemStack(selected ? Items.FILLED_MAP : Items.MAP);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(saved.name()).withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("buildtools.menu.blueprint_entry.description", index + 1).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Category: " + saved.category()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal(saved.blueprint().entries().size() + " blocks, " + saved.blueprint().entities().size() + " entities").withStyle(ChatFormatting.DARK_GRAY));
        if (saved.lastUsedTick() > 0) {
            lore.add(Component.literal("Recently used").withStyle(ChatFormatting.DARK_GRAY));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore, lore));
        setSelected(stack, selected);
        return stack;
    }

    private enum SortMode {
        RECENT(Component.translatable("buildtools.menu.blueprint_sort_recent")),
        NAME(Component.translatable("buildtools.menu.blueprint_sort_name")),
        SIZE(Component.translatable("buildtools.menu.blueprint_sort_size"));

        private final Component displayName;

        SortMode(Component displayName) {
            this.displayName = displayName;
        }

        private SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private Comparator<Integer> comparator(List<SavedBlueprint> blueprints) {
            return switch (this) {
                case NAME -> Comparator.comparing(index -> blueprints.get(index).name().toLowerCase(Locale.ROOT));
                case SIZE -> Comparator.<Integer>comparingInt(index -> blueprints.get(index).blueprint().entries().size()).reversed();
                case RECENT -> Comparator.<Integer>comparingLong(index -> blueprints.get(index).lastUsedTick()).reversed()
                        .thenComparingInt(index -> index);
            };
        }
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey, boolean selected) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey));
        Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        setSelected(stack, selected);
        return stack;
    }

    private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
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
