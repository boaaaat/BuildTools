package com.abhil.buildtools;

import com.abhil.buildtools.config.BuildToolsConfig;
import com.abhil.buildtools.network.BuildToolsNetworking;
import com.abhil.buildtools.registry.ModCreativeTabs;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.server.BuildToolServerEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(BuildTools.MOD_ID)
public final class BuildTools {
    public static final String MOD_ID = "buildtools";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BuildTools(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(BuildToolsNetworking::register);

        NeoForge.EVENT_BUS.register(BuildToolServerEvents.class);
        modContainer.registerConfig(ModConfig.Type.COMMON, BuildToolsConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
