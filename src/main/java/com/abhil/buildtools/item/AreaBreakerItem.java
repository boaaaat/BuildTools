package com.abhil.buildtools.item;

import com.abhil.buildtools.server.BuildOperationEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

public final class AreaBreakerItem extends BuildToolItem {
    public AreaBreakerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            BuildOperationEngine.executeBreaker(serverPlayer);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
}
