package com.abhil.buildtools;

import com.abhil.buildtools.client.AdvancedBuildToolsModeScreen;
import com.abhil.buildtools.client.BlueprintLibraryScreen;
import com.abhil.buildtools.client.BuildToolStatusOverlay;
import com.abhil.buildtools.client.BuildToolsModeScreen;
import com.abhil.buildtools.client.ClientSelectionRenderer;
import com.abhil.buildtools.client.MaterialChecklistScreen;
import com.abhil.buildtools.client.HelpScreen;
import com.abhil.buildtools.client.PaletteLibraryScreen;
import com.abhil.buildtools.client.PresetLibraryScreen;
import com.abhil.buildtools.network.AdvancedSelectionActionPayload;
import com.abhil.buildtools.network.OpenToolMenuPayload;
import com.abhil.buildtools.network.ScrollToolPayload;
import com.abhil.buildtools.network.ShortcutActionPayload;
import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.registry.ModMenus;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = BuildTools.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BuildTools.MOD_ID, value = Dist.CLIENT)
public final class BuildToolsClient {
    private static boolean advancedSelectionAttackDown;
    private static final String KEY_CATEGORY = "key.categories.buildtools";
    private static final KeyMapping OPEN_TOOL_MENU = key("open_tool_menu");
    private static final KeyMapping CYCLE_SHAPE = key("cycle_shape");
    private static final KeyMapping CYCLE_MODE = key("cycle_mode");
    private static final KeyMapping CONFIRM_PREVIEW = key("confirm_preview");
    private static final KeyMapping CANCEL_PREVIEW = key("cancel_preview");
    private static final KeyMapping UNDO = key("undo");
    private static final KeyMapping REDO = key("redo");
    private static final KeyMapping NUDGE_WEST = key("nudge_west");
    private static final KeyMapping NUDGE_EAST = key("nudge_east");
    private static final KeyMapping NUDGE_NORTH = key("nudge_north");
    private static final KeyMapping NUDGE_SOUTH = key("nudge_south");
    private static final KeyMapping NUDGE_UP = key("nudge_up");
    private static final KeyMapping NUDGE_DOWN = key("nudge_down");

    public BuildToolsClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerKeyMappings);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private static KeyMapping key(String name) {
        return new KeyMapping("key.buildtools." + name, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TOOL_MENU);
        event.register(CYCLE_SHAPE);
        event.register(CYCLE_MODE);
        event.register(CONFIRM_PREVIEW);
        event.register(CANCEL_PREVIEW);
        event.register(UNDO);
        event.register(REDO);
        event.register(NUDGE_WEST);
        event.register(NUDGE_EAST);
        event.register(NUDGE_NORTH);
        event.register(NUDGE_SOUTH);
        event.register(NUDGE_UP);
        event.register(NUDGE_DOWN);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MODE_MENU.get(), BuildToolsModeScreen::new);
        event.register(ModMenus.ADVANCED_MODE_MENU.get(), AdvancedBuildToolsModeScreen::new);
        event.register(ModMenus.BLUEPRINT_LIBRARY_MENU.get(), BlueprintLibraryScreen::new);
        event.register(ModMenus.MATERIAL_CHECKLIST_MENU.get(), MaterialChecklistScreen::new);
        event.register(ModMenus.PRESET_LIBRARY_MENU.get(), PresetLibraryScreen::new);
        event.register(ModMenus.PALETTE_LIBRARY_MENU.get(), PaletteLibraryScreen::new);
        event.register(ModMenus.HELP_MENU.get(), HelpScreen::new);
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
    static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyAttack.isDown()) {
            advancedSelectionAttackDown = false;
        }
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        handleShortcut(OPEN_TOOL_MENU, "open_menu");
        handleShortcut(CYCLE_SHAPE, "cycle_shape");
        handleShortcut(CYCLE_MODE, "cycle_mode");
        handleShortcut(CONFIRM_PREVIEW, "confirm_preview");
        handleShortcut(CANCEL_PREVIEW, "cancel_preview");
        handleShortcut(UNDO, "undo");
        handleShortcut(REDO, "redo");
        handleNudge(NUDGE_WEST, Direction.WEST);
        handleNudge(NUDGE_EAST, Direction.EAST);
        handleNudge(NUDGE_NORTH, Direction.NORTH);
        handleNudge(NUDGE_SOUTH, Direction.SOUTH);
        handleNudge(NUDGE_UP, Direction.UP);
        handleNudge(NUDGE_DOWN, Direction.DOWN);
    }

    private static void handleShortcut(KeyMapping key, String action) {
        while (key.consumeClick()) {
            PacketDistributor.sendToServer(new ShortcutActionPayload(action, "", 1));
        }
    }

    private static void handleNudge(KeyMapping key, Direction direction) {
        while (key.consumeClick()) {
            PacketDistributor.sendToServer(new ShortcutActionPayload("nudge", direction.name(), shortcutAmount()));
        }
    }

    private static int shortcutAmount() {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            return 10;
        }
        return minecraft.player != null && minecraft.player.isShiftKeyDown() ? 5 : 1;
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
                || minecraft.hitResult == null) {
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (held.is(ModItems.ADVANCED_SELECTION_STAFF.get())) {
            if (advancedSelectionAttackDown) {
                event.setSwingHand(false);
                event.setCanceled(true);
                return;
            }
            advancedSelectionAttackDown = true;
            PacketDistributor.sendToServer(new AdvancedSelectionActionPayload(minecraft.player.isShiftKeyDown()));
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (minecraft.hitResult.getType() != HitResult.Type.MISS) {
            return;
        }
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
