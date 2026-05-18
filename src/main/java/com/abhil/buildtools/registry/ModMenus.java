package com.abhil.buildtools.registry;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import com.abhil.buildtools.server.BlueprintLibraryMenu;
import com.abhil.buildtools.server.BuildToolsModeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BuildTools.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<BuildToolsModeMenu>> MODE_MENU = MENUS.register(
            "mode_menu",
            () -> new MenuType<>(BuildToolsModeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<AdvancedBuildToolsModeMenu>> ADVANCED_MODE_MENU = MENUS.register(
            "advanced_mode_menu",
            () -> new MenuType<>(AdvancedBuildToolsModeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<BlueprintLibraryMenu>> BLUEPRINT_LIBRARY_MENU = MENUS.register(
            "blueprint_library_menu",
            () -> new MenuType<>(BlueprintLibraryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {
    }
}
