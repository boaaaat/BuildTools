package com.abhil.buildtools;

import com.abhil.buildtools.client.AdvancedBuildToolsModeScreen;
import com.abhil.buildtools.client.BuildToolStatusOverlay;
import com.abhil.buildtools.client.BuildToolsModeScreen;
import com.abhil.buildtools.client.ClientSelectionRenderer;
import com.abhil.buildtools.network.OpenToolMenuPayload;
import com.abhil.buildtools.network.ScrollToolPayload;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;

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

    @SubscribeEvent
    static void renderGui(RenderGuiEvent.Post event) {
        BuildToolStatusOverlay.render(event);
    }

    @SubscribeEvent
    static void mouseScrolled(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !minecraft.player.isShiftKeyDown()) {
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (!isScrollableBuildTool(held)) {
            return;
        }
        int direction = event.getScrollDeltaY() >= 0.0D ? 1 : -1;
        PacketDistributor.sendToServer(new ScrollToolPayload(direction));
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void attackClicked(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isAttack()
                || minecraft.player == null
                || minecraft.screen != null
                || minecraft.hitResult == null
                || minecraft.hitResult.getType() != HitResult.Type.MISS) {
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (!isAirMenuTool(held)) {
            return;
        }
        PacketDistributor.sendToServer(new OpenToolMenuPayload());
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    private static boolean isScrollableBuildTool(ItemStack stack) {
        return stack.is(ModItems.SELECTION_STAFF.get())
                || stack.is(ModItems.ADVANCED_SELECTION_STAFF.get())
                || stack.is(ModItems.BUILDER_WAND.get())
                || stack.is(ModItems.ADVANCED_BUILDER_WAND.get())
                || stack.is(ModItems.BUILDER_BRUSH.get())
                || stack.is(ModItems.AREA_BREAKER.get());
    }

    private static boolean isAirMenuTool(ItemStack stack) {
        return stack.is(ModItems.BUILDER_WAND.get())
                || stack.is(ModItems.ADVANCED_BUILDER_WAND.get())
                || stack.is(ModItems.BUILDER_BRUSH.get())
                || stack.is(ModItems.AREA_BREAKER.get())
                || stack.is(ModItems.BLUEPRINT_TROWEL.get())
                || stack.is(ModItems.UNDO_TOKEN.get())
                || stack.is(ModItems.REDO_TOKEN.get());
    }
}
