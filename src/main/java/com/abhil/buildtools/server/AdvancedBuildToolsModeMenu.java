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
    public static final int PALETTE_START = 44;
    public static final int PALETTE_COUNT = 10;
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
        if (slotId >= 0 && slotId < MENU_SIZE && player instanceof ServerPlayer serverPlayer) {
            if (isPaletteSlot(slotId)) {
                handlePaletteClick(serverPlayer, slotId);
                return;
            }
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
        for (int slot = PALETTE_START; slot < PALETTE_START + PALETTE_COUNT; slot++) {
            if (menuItems.getItem(slot).isEmpty()) {
                ItemStack original = source.copy();
                setPaletteEntry(slot, new PaletteEntry(((BlockItem) source.getItem()).getBlock().defaultBlockState(), PaletteEntry.DEFAULT_WEIGHT));
                source.shrink(1);
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
        super.removed(player);
    }

    private void addMenuSlots() {
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = column + row * 9;
                if (isPaletteSlot(slot)) {
                    addSlot(new PaletteSlot(menuItems, slot, 8 + column * 18, 18 + row * 18));
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
        for (int i = 0; i < MENU_SIZE; i++) {
            menuItems.setItem(i, ItemStack.EMPTY);
        }
        menuItems.setItem(0, modeItem(Items.LIME_STAINED_GLASS, BuildMode.FILL));
        menuItems.setItem(1, modeItem(Items.ORANGE_STAINED_GLASS, BuildMode.REPLACE));
        menuItems.setItem(2, modeItem(Items.RED_STAINED_GLASS, BuildMode.OVERWRITE));
        menuItems.setItem(3, utilityItem(Items.PAPER, "buildtools.menu.materials", "buildtools.menu.materials.description"));
        menuItems.setItem(4, utilityItem(Items.POWDER_SNOW_BUCKET, "buildtools.menu.gradient", "buildtools.menu.gradient.description"));
        menuItems.setItem(5, utilityItem(Items.MAP, "buildtools.menu.save_plan", "buildtools.menu.save_plan.description"));
        menuItems.setItem(6, utilityItem(Items.FILLED_MAP, "buildtools.menu.build_plan", "buildtools.menu.build_plan.description"));
        menuItems.setItem(7, utilityItem(Items.DISPENSER, "buildtools.menu.random_pattern", "buildtools.menu.random_pattern.description"));
        menuItems.setItem(8, utilityItem(Items.HOPPER, "buildtools.menu.return_palette", "buildtools.menu.return_palette.description"));

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
                new ItemStack(Items.SLIME_BALL),
                new ItemStack(Items.GRAVEL),
                new ItemStack(Items.RAIL),
                new ItemStack(Items.STONE_BRICK_STAIRS),
                new ItemStack(Items.GLASS)
        };
        for (int i = 0; i < shapes.length; i++) {
            ItemStack stack = icons[i].copy();
            stack.set(DataComponents.CUSTOM_NAME, shapes[i].displayName());
            menuItems.setItem(9 + i, stack);
        }

        menuItems.setItem(43, utilityItem(Items.CHEST, "buildtools.menu.replace_palette", "buildtools.menu.replace_palette.description"));
        populatePaletteItems();
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
            case 4 -> BuildToolsState.toggleGradient(player);
            case 5 -> BuildOperationEngine.createPlan(player);
            case 6 -> BuildOperationEngine.applyPlan(player);
            case 7 -> BuildToolsState.toggleRandomPattern(player);
            case 8 -> returnPalette(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void populatePaletteItems() {
        if (owner == null) {
            return;
        }
        List<PaletteEntry> entries = BuildToolsState.paletteEntries(owner);
        int totalWeight = entries.stream().mapToInt(PaletteEntry::weight).sum();
        for (int i = 0; i < PALETTE_COUNT; i++) {
            int slot = PALETTE_START + i;
            menuItems.setItem(slot, i < entries.size() ? paletteItem(entries.get(i), totalWeight) : ItemStack.EMPTY);
        }
    }

    private void handlePaletteClick(ServerPlayer player, int slotId) {
        ItemStack carried = getCarried();
        if (!(carried.getItem() instanceof BlockItem blockItem) || !menuItems.getItem(slotId).isEmpty()) {
            return;
        }
        setPaletteEntry(slotId, new PaletteEntry(blockItem.getBlock().defaultBlockState(), PaletteEntry.DEFAULT_WEIGHT));
        carried.shrink(1);
        if (carried.isEmpty()) {
            setCarried(ItemStack.EMPTY);
        }
    }

    public void adjustPaletteWeight(ServerPlayer player, int slotId, int delta) {
        if (!isPaletteSlot(slotId)) {
            return;
        }
        List<PaletteEntry> entries = new ArrayList<>(BuildToolsState.paletteEntries(player));
        int index = slotId - PALETTE_START;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        PaletteEntry entry = entries.get(index);
        entries.set(index, new PaletteEntry(entry.state(), entry.weight() + delta));
        BuildToolsState.setPaletteEntries(player, entries);
        populateMenuItems();
    }

    private void setPaletteEntry(int slotId, PaletteEntry entry) {
        if (owner == null || !isPaletteSlot(slotId)) {
            return;
        }
        List<PaletteEntry> entries = new ArrayList<>(BuildToolsState.paletteEntries(owner));
        int index = slotId - PALETTE_START;
        while (entries.size() < index) {
            entries.add(new PaletteEntry(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), PaletteEntry.DEFAULT_WEIGHT));
        }
        if (index < entries.size()) {
            entries.set(index, entry);
        } else {
            entries.add(entry);
        }
        entries.removeIf(paletteEntry -> paletteEntry.state().isAir());
        BuildToolsState.setPaletteEntries(owner, entries);
        populateMenuItems();
    }

    private static ItemStack paletteItem(PaletteEntry entry, int totalWeight) {
        ItemStack stack = new ItemStack(entry.state().getBlock().asItem());
        double chance = totalWeight <= 0 ? 100.0D : (entry.weight() * 100.0D) / totalWeight;
        Component weight = Component.literal("Pattern weight: " + entry.weight() + " (" + Math.round(chance) + "%)").withStyle(ChatFormatting.GRAY);
        Component scroll = Component.translatable("buildtools.menu.palette_weight.description").withStyle(ChatFormatting.DARK_GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(weight, scroll), List.of(weight, scroll)));
        return stack;
    }

    private static void returnPalette(ServerPlayer player) {
        List<PaletteEntry> entries = BuildToolsState.paletteEntries(player);
        if (entries.isEmpty()) {
            return;
        }
        for (PaletteEntry entry : entries) {
            ItemStack stack = new ItemStack(entry.state().getBlock().asItem());
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        BuildToolsState.setPaletteEntries(player, List.of());
    }

    public static boolean isPaletteSlot(int slot) {
        return slot >= PALETTE_START && slot < PALETTE_START + PALETTE_COUNT;
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

    private static final class PaletteSlot extends Slot {
        private PaletteSlot(Container container, int slot, int x, int y) {
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
