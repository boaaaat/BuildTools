package com.abhil.buildtools.item;

import com.abhil.buildtools.server.BuildOperationEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

public final class BlueprintTrowelItem extends BuildToolItem {
    public BlueprintTrowelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            boolean success;
            if (player.isShiftKeyDown()) {
                BlockPos origin = context.getClickedPos().relative(context.getClickedFace());
                success = BuildOperationEngine.pasteBlueprint(serverPlayer, origin);
            } else {
                success = BuildOperationEngine.copySelection(serverPlayer);
            }
            if (success) {
                context.getItemInHand().hurtAndBreak(1, serverPlayer.serverLevel(), serverPlayer, item -> serverPlayer.onEquippedItemBroken(item, LivingEntity.getSlotForHand(context.getHand())));
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
}
