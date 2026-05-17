package com.abhil.buildtools;

import com.abhil.buildtools.client.AdvancedBuildToolsModeScreen;
import com.abhil.buildtools.client.BuildToolsModeScreen;
import com.abhil.buildtools.client.ClientSelectionRenderer;
import com.abhil.buildtools.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = BuildTools.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BuildTools.MOD_ID, value = Dist.CLIENT)
public final class BuildToolsClient {
    public BuildToolsClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::registerScreens);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MODE_MENU.get(), BuildToolsModeScreen::new);
        event.register(ModMenus.ADVANCED_MODE_MENU.get(), AdvancedBuildToolsModeScreen::new);
    }

    @SubscribeEvent
    static void renderLevel(RenderLevelStageEvent event) {
        ClientSelectionRenderer.render(event);
    }
}
