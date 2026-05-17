package com.abhil.buildtools.item;

import com.abhil.buildtools.server.BuildOperationEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class RedoTokenItem extends BuildToolItem {
    public RedoTokenItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (BuildOperationEngine.redo(serverPlayer) && !serverPlayer.gameMode.isCreative()) {
                stack.hurtAndBreak(1, serverPlayer.serverLevel(), serverPlayer,
                        item -> serverPlayer.onEquippedItemBroken(item, LivingEntity.getSlotForHand(usedHand)));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
