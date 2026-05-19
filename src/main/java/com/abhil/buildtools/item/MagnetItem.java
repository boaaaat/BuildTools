package com.abhil.buildtools.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

public final class MagnetItem extends Item {
    private static final String ENABLED_TAG = "BuildToolsMagnetEnabled";

    public MagnetItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player instanceof ServerPlayer serverPlayer) {
            boolean enabled = !isEnabled(stack);
            setEnabled(stack, enabled);
            serverPlayer.displayClientMessage(Component.translatable(enabled
                    ? "buildtools.message.magnet_enabled"
                    : "buildtools.message.magnet_disabled"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(isEnabled(stack)
                ? "item.buildtools.magnet.enabled"
                : "item.buildtools.magnet.disabled").withStyle(isEnabled(stack) ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    public static boolean isEnabled(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return !tag.contains(ENABLED_TAG) || tag.getBoolean(ENABLED_TAG);
    }

    private static void setEnabled(ItemStack stack, boolean enabled) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ENABLED_TAG, enabled));
    }
}
