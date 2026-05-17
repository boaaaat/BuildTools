package com.abhil.buildtools.registry;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.item.BlueprintTrowelItem;
import com.abhil.buildtools.item.BuilderWandItem;
import com.abhil.buildtools.item.SelectionStaffItem;
import com.abhil.buildtools.item.UndoTokenItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BuildTools.MOD_ID);

    public static final DeferredItem<Item> SELECTION_STAFF = ITEMS.register("selection_staff",
            () -> new SelectionStaffItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BUILDER_WAND = ITEMS.register("builder_wand",
            () -> new BuilderWandItem(new Item.Properties().stacksTo(1).durability(512)));
    public static final DeferredItem<Item> BLUEPRINT_TROWEL = ITEMS.register("blueprint_trowel",
            () -> new BlueprintTrowelItem(new Item.Properties().stacksTo(1).durability(256)));
    public static final DeferredItem<Item> UNDO_TOKEN = ITEMS.register("undo_token",
            () -> new UndoTokenItem(new Item.Properties().stacksTo(16)));

    private ModItems() {
    }
}
