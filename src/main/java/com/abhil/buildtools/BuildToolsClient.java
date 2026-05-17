package com.abhil.buildtools;

import com.abhil.buildtools.client.ClientSelectionRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = BuildTools.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BuildTools.MOD_ID, value = Dist.CLIENT)
public final class BuildToolsClient {
    public BuildToolsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void renderLevel(RenderLevelStageEvent event) {
        ClientSelectionRenderer.render(event);
    }
}
