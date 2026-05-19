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
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialChecklistMenu extends AbstractContainerMenu {
    private static final int MENU_SIZE = 54;
    private static final int BACK_SLOT = 53;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ServerPlayer owner;

    public MaterialChecklistMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    }

    private MaterialChecklistMenu(int containerId, Inventory inventory, ServerPlayer owner) {
        super(ModMenus.MATERIAL_CHECKLIST_MENU.get(), containerId);
        this.owner = owner;
        populateMenuItems();
        addMenuSlots();
        addPlayerInventory(inventory);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new MaterialChecklistMenu(containerId, inventory, player),
                Component.translatable("buildtools.menu.material_checklist_title")));
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
        if (owner == null) {
            return;
        }
        List<BlockState> targets = MaterialChecklist.targetsFor(owner);
        BlockCostPlan plan = BlockCostPlan.create(owner, targets);
        int slot = 0;
        for (BlockCostPlan.MaterialLine line : plan.lines()) {
            if (slot >= 45) {
                break;
            }
            menuItems.setItem(slot++, materialItem(line));
        }
        if (slot == 0) {
            menuItems.setItem(0, named(Items.GRAY_DYE, Component.translatable("buildtools.menu.material_checklist_empty").withStyle(ChatFormatting.GRAY)));
        }
        menuItems.setItem(BACK_SLOT, utilityItem(Items.ARROW, "buildtools.menu.back", "buildtools.menu.back.description"));
    }

    private static ItemStack materialItem(BlockCostPlan.MaterialLine line) {
        ItemStack stack = line.key().stack(Math.max(1, Math.min(64, line.required())));
        stack.set(DataComponents.CUSTOM_NAME, line.key().stack(1).getHoverName().copy()
                .withStyle(line.missing() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Required: " + line.required()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Inventory: " + line.inventoryAvailable()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Linked storage: " + line.storageAvailable()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal(line.missing() > 0 ? "Missing: " + line.missing() : "Ready").withStyle(line.missing() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
        stack.set(DataComponents.LORE, new ItemLore(lore, lore));
        return stack;
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey) {
        ItemStack stack = named(item, Component.translatable(nameKey));
        Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        return stack;
    }

    private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
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
