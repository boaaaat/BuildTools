package com.abhil.buildtools.item;

import com.abhil.buildtools.server.AdvancedBuildToolsModeMenu;
import com.abhil.buildtools.server.BuildOperationEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class AdvancedBuilderWandItem extends BuildToolItem {
    public AdvancedBuilderWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                AdvancedBuildToolsModeMenu.open(serverPlayer);
            } else if (BuildOperationEngine.executeAdvancedBuilder(serverPlayer)) {
                context.getItemInHand().hurtAndBreak(1, serverPlayer.serverLevel(), serverPlayer, item -> serverPlayer.onEquippedItemBroken(item, LivingEntity.getSlotForHand(context.getHand())));
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player instanceof ServerPlayer serverPlayer && player.isShiftKeyDown()) {
            AdvancedBuildToolsModeMenu.open(serverPlayer);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }
}
