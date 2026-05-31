package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModMenus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialSelectionMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 54;
    public static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 46;
    private static final int RESET_WEIGHTS_SLOT = 48;
    private static final int CLEAR_SLOT = 50;
    private static final int NEXT_SLOT = 53;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ServerPlayer owner;
    private int page;

    public MaterialSelectionMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null, 0);
    }

    private MaterialSelectionMenu(int containerId, Inventory inventory, ServerPlayer owner, int page) {
        super(ModMenus.MATERIAL_SELECTION_MENU.get(), containerId);
        this.owner = owner;
        this.page = page;
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new MaterialSelectionMenu(containerId, inventory, player, 0),
                Component.translatable("buildtools.menu.material_selection_title")));
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
        List<MaterialOption> options = options();
        int maxPage = maxPage(options.size());
        page = Math.max(0, Math.min(page, maxPage));
        int start = page * PAGE_SIZE;
        List<PaletteEntry> selected = owner == null ? List.of() : BuildToolsState.materialSelections(owner);
        int totalWeight = selected.stream().mapToInt(PaletteEntry::weight).sum();
        for (int i = 0; i < PAGE_SIZE && start + i < options.size(); i++) {
            MaterialOption option = options.get(start + i);
            menuItems.setItem(i, materialItem(option, selectionIndex(selected, option.state()), selectionWeight(selected, option.state()), totalWeight));
        }
        menuItems.setItem(PREVIOUS_SLOT, controlItem(Items.ARROW, Component.literal("Previous Page"), page > 0));
        menuItems.setItem(BACK_SLOT, controlItem(Items.ARROW, Component.translatable("buildtools.menu.back"), true));
        menuItems.setItem(RESET_WEIGHTS_SLOT, controlItem(Items.ANVIL, Component.translatable("buildtools.menu.reset_material_weights"), !selected.isEmpty()));
        menuItems.setItem(CLEAR_SLOT, controlItem(Items.BARRIER, Component.translatable("buildtools.menu.clear_material_selection"), !selected.isEmpty()));
        menuItems.setItem(NEXT_SLOT, controlItem(Items.ARROW, Component.literal("Next Page"), page < maxPage));
    }

    private boolean handleClick(ServerPlayer player, int slotId) {
        List<MaterialOption> options = options();
        int maxPage = maxPage(options.size());
        if (slotId == PREVIOUS_SLOT && page > 0) {
            page--;
            return true;
        }
        if (slotId == NEXT_SLOT && page < maxPage) {
            page++;
            return true;
        }
        if (slotId == BACK_SLOT) {
            ToolMenuNavigation.openActiveToolMenu(player);
            return false;
        }
        if (slotId == RESET_WEIGHTS_SLOT) {
            BuildToolsState.resetMaterialWeights(player);
            return true;
        }
        if (slotId == CLEAR_SLOT) {
            BuildToolsState.clearMaterialSelections(player);
            return true;
        }
        if (slotId >= 0 && slotId < PAGE_SIZE) {
            int index = page * PAGE_SIZE + slotId;
            if (index >= 0 && index < options.size()) {
                BuildToolsState.toggleMaterialSelection(player, options.get(index).state(), BuildToolsState.MAX_MATERIAL_SELECTIONS);
                return true;
            }
        }
        return false;
    }

    public void adjustMaterialWeight(ServerPlayer player, int slotId, int delta) {
        if (slotId < 0 || slotId >= PAGE_SIZE) {
            return;
        }
        List<MaterialOption> options = options();
        int index = page * PAGE_SIZE + slotId;
        if (index < 0 || index >= options.size()) {
            return;
        }
        List<PaletteEntry> selected = BuildToolsState.materialSelections(player);
        if (selectionIndex(selected, options.get(index).state()) <= 0) {
            return;
        }
        BuildToolsState.adjustMaterialWeight(player, options.get(index).state(), delta);
        populateMenuItems();
    }

    public boolean isSelectedMaterialSlot(Slot slot) {
        int slotId = this.slots.indexOf(slot);
        if (slotId < 0 || slotId >= PAGE_SIZE || owner == null) {
            return false;
        }
        List<MaterialOption> options = options();
        int index = page * PAGE_SIZE + slotId;
        return index >= 0
                && index < options.size()
                && selectionIndex(BuildToolsState.materialSelections(owner), options.get(index).state()) > 0;
    }

    private List<MaterialOption> options() {
        if (owner == null) {
            return List.of();
        }
        List<MaterialOption> options = new ArrayList<>();
        for (Map.Entry<ItemStackKey, Integer> entry : BuildingStorageManager.accessibleMaterialCounts(owner).entrySet()) {
            BlockState state = BuildMaterialSource.stateFromStack(new ItemStack(entry.getKey().item()));
            if (state == null || state.is(Blocks.AIR)) {
                continue;
            }
            options.add(new MaterialOption(state, entry.getValue()));
        }
        options.sort(Comparator.comparing(option -> option.state().getBlock().getName().getString()));
        return options;
    }

    private static int maxPage(int size) {
        return Math.max(0, (size - 1) / PAGE_SIZE);
    }

    private static int selectionIndex(List<PaletteEntry> selected, BlockState state) {
        for (int i = 0; i < selected.size(); i++) {
            if (selected.get(i).state().is(state.getBlock())) {
                return i + 1;
            }
        }
        return 0;
    }

    private static int selectionWeight(List<PaletteEntry> selected, BlockState state) {
        for (PaletteEntry entry : selected) {
            if (entry.state().is(state.getBlock())) {
                return entry.weight();
            }
        }
        return 0;
    }

    private static ItemStack materialItem(MaterialOption option, int selectionIndex, int selectionWeight, int totalWeight) {
        ItemStack stack = BuildMaterialSource.stackForState(option.state());
        Component name = option.state().getBlock().getName();
        if (selectionIndex > 0) {
            stack.setCount(selectionIndex);
            name = name.copy().withStyle(ChatFormatting.GREEN);
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        stack.set(DataComponents.CUSTOM_NAME, name);
        int percentage = totalWeight <= 0 ? 100 : Math.round((selectionWeight * 100.0F) / totalWeight);
        List<Component> lore = selectionIndex > 0
                ? List.of(
                        Component.literal("Available: " + option.count()).withStyle(ChatFormatting.GRAY),
                        Component.literal("Weight: " + selectionWeight + " (" + percentage + "%)").withStyle(ChatFormatting.GRAY),
                        Component.literal("Scroll to change weight. Shift-scroll changes by 10.").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("Click to remove from selection.").withStyle(ChatFormatting.DARK_GRAY))
                : List.of(
                        Component.literal("Available: " + option.count()).withStyle(ChatFormatting.GRAY),
                        Component.literal("Click to add to selection.").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore, lore));
        return stack;
    }

    private static ItemStack controlItem(net.minecraft.world.item.Item item, Component name, boolean active) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, active ? name : name.copy().withStyle(ChatFormatting.DARK_GRAY));
        return stack;
    }

    private record MaterialOption(BlockState state, int count) {
    }

    private static final class FakeSlot extends Slot {
        private FakeSlot(Container container, int slot, int x, int y) {
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
    }
}
