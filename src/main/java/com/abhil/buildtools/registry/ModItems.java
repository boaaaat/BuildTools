package com.abhil.buildtools.registry;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.item.AdvancedBuilderWandItem;
import com.abhil.buildtools.item.AdvancedSelectionStaffItem;
import com.abhil.buildtools.item.AreaBreakerItem;
import com.abhil.buildtools.item.BlueprintTrowelItem;
import com.abhil.buildtools.item.BuilderBrushItem;
import com.abhil.buildtools.item.BuilderWandItem;
import com.abhil.buildtools.item.MagnetItem;
import com.abhil.buildtools.item.RedoTokenItem;
import com.abhil.buildtools.item.SelectionStaffItem;
import com.abhil.buildtools.item.StorageLinkItem;
import com.abhil.buildtools.item.UndoTokenItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BuildTools.MOD_ID);

    public static final DeferredItem<Item> SELECTION_STAFF = ITEMS.register("selection_staff",
            () -> new SelectionStaffItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ADVANCED_SELECTION_STAFF = ITEMS.register("advanced_selection_staff",
            () -> new AdvancedSelectionStaffItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BUILDER_WAND = ITEMS.register("builder_wand",
            () -> new BuilderWandItem(new Item.Properties().stacksTo(1).durability(128)));
    public static final DeferredItem<Item> ADVANCED_BUILDER_WAND = ITEMS.register("advanced_builder_wand",
            () -> new AdvancedBuilderWandItem(new Item.Properties().stacksTo(1).durability(128)));
    public static final DeferredItem<Item> BUILDER_BRUSH = ITEMS.register("builder_brush",
            () -> new BuilderBrushItem(new Item.Properties().stacksTo(1).durability(256)));
    public static final DeferredItem<Item> AREA_BREAKER = ITEMS.register("area_breaker",
            () -> new AreaBreakerItem(new Item.Properties().stacksTo(1).durability(256)));
    public static final DeferredItem<Item> BLUEPRINT_TROWEL = ITEMS.register("blueprint_trowel",
            () -> new BlueprintTrowelItem(new Item.Properties().stacksTo(1).durability(128)));
    public static final DeferredItem<Item> UNDO_TOKEN = ITEMS.register("undo_token",
            () -> new UndoTokenItem(new Item.Properties().stacksTo(1).durability(10)));
    public static final DeferredItem<Item> REDO_TOKEN = ITEMS.register("redo_token",
            () -> new RedoTokenItem(new Item.Properties().stacksTo(1).durability(10)));
    public static final DeferredItem<Item> MAGNET = ITEMS.register("magnet",
            () -> new MagnetItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> STORAGE_LINK = ITEMS.register("storage_link",
            () -> new StorageLinkItem(new Item.Properties().stacksTo(4)));

    private ModItems() {
    }
}
