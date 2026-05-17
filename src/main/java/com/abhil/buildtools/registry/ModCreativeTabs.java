package com.abhil.buildtools.registry;

import com.abhil.buildtools.BuildTools;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BuildTools.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BUILDTOOLS_TAB = CREATIVE_TABS.register(
            "buildtools",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.buildtools"))
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .icon(() -> ModItems.BUILDER_WAND.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SELECTION_STAFF.get());
                        output.accept(ModItems.BUILDER_WAND.get());
                        output.accept(ModItems.BLUEPRINT_TROWEL.get());
                        output.accept(ModItems.UNDO_TOKEN.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
