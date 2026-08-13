package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModMenus;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int MISSING_ONLY_SLOT = 46;
    private static final int REFRESH_SLOT = 47;
    private static final int BACK_SLOT = 52;
    private static final int NEXT_SLOT = 53;
    private final SimpleContainer menuItems = new SimpleContainer(MENU_SIZE);
    private final ServerPlayer owner;
    private int page;
    private boolean missingOnly;

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
        if (player instanceof ServerPlayer serverPlayer) {
            if (slotId == BACK_SLOT) {
                ToolMenuNavigation.openActiveToolMenu(serverPlayer);
                return;
            }
            if (slotId == PREVIOUS_SLOT && page > 0) {
                page--;
                populateMenuItems();
                return;
            }
            if (slotId == NEXT_SLOT && page < maxPage(materialLines().size())) {
                page++;
                populateMenuItems();
                return;
            }
            if (slotId == MISSING_ONLY_SLOT) {
                missingOnly = !missingOnly;
                page = 0;
                populateMenuItems();
                return;
            }
            if (slotId == REFRESH_SLOT) {
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
        if (owner == null) {
            return;
        }
        List<BlockCostPlan.MaterialLine> lines = materialLines();
        int maxPage = maxPage(lines.size());
        page = Math.max(0, Math.min(page, maxPage));
        int start = page * PAGE_SIZE;
        int shown = 0;
        for (int i = 0; i < PAGE_SIZE && start + i < lines.size(); i++) {
            menuItems.setItem(i, materialItem(lines.get(start + i)));
            shown++;
        }
        if (shown == 0) {
            menuItems.setItem(0, named(Items.GRAY_DYE, Component.translatable(missingOnly
                    ? "buildtools.menu.material_checklist_no_missing"
                    : "buildtools.menu.material_checklist_empty").withStyle(ChatFormatting.GRAY)));
        }
        menuItems.setItem(PREVIOUS_SLOT, pageItem("buildtools.menu.previous_page", page > 0, page, maxPage));
        menuItems.setItem(MISSING_ONLY_SLOT, utilityItem(
                missingOnly ? Items.REDSTONE_TORCH : Items.REDSTONE,
                "buildtools.menu.material_missing_only",
                "buildtools.menu.material_missing_only.description",
                missingOnly));
        menuItems.setItem(REFRESH_SLOT, utilityItem(Items.CLOCK, "buildtools.menu.refresh", "buildtools.menu.refresh.description", false));
        menuItems.setItem(BACK_SLOT, utilityItem(Items.ARROW, "buildtools.menu.back", "buildtools.menu.back.description"));
        menuItems.setItem(NEXT_SLOT, pageItem("buildtools.menu.next_page", page < maxPage, page, maxPage));
    }

    private List<BlockCostPlan.MaterialLine> materialLines() {
        if (owner == null) {
            return List.of();
        }
        List<BlockState> targets = MaterialChecklist.targetsFor(owner);
        return BlockCostPlan.create(owner, targets).lines().stream()
                .filter(line -> !missingOnly || line.missing() > 0)
                .sorted(Comparator.comparingInt((BlockCostPlan.MaterialLine line) -> line.missing() == 0 ? 1 : 0)
                        .thenComparing(line -> line.key().stack(1).getHoverName().getString()))
                .toList();
    }

    private static int maxPage(int size) {
        return Math.max(0, (size - 1) / PAGE_SIZE);
    }

    private static ItemStack materialItem(BlockCostPlan.MaterialLine line) {
        ItemStack stack = line.key().stack(Math.max(1, Math.min(64, line.required())));
        stack.set(DataComponents.CUSTOM_NAME, line.key().stack(1).getHoverName().copy()
                .withStyle(line.missing() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("buildtools.menu.material_required", line.required()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.translatable("buildtools.menu.material_inventory", line.inventoryAvailable()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.translatable("buildtools.menu.material_storage", line.storageAvailable()).withStyle(ChatFormatting.GRAY));
        Component availability = line.missing() > 0
                ? Component.translatable("buildtools.menu.material_missing", line.missing())
                : Component.translatable("buildtools.menu.material_ready");
        lore.add(availability.copy().withStyle(line.missing() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
        stack.set(DataComponents.LORE, new ItemLore(lore, lore));
        return stack;
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey) {
        return utilityItem(item, nameKey, descriptionKey, false);
    }

    private static ItemStack utilityItem(net.minecraft.world.item.Item item, String nameKey, String descriptionKey, boolean selected) {
        ItemStack stack = named(item, Component.translatable(nameKey));
        Component description = Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(description), List.of(description)));
        if (selected) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    private static ItemStack pageItem(String nameKey, boolean enabled, int page, int maxPage) {
        ItemStack stack = named(Items.ARROW, Component.translatable(nameKey)
                .withStyle(enabled ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        Component description = Component.translatable("buildtools.menu.page.description", page + 1, maxPage + 1)
                .withStyle(ChatFormatting.GRAY);
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
