package com.abhil.buildtools.item;

import com.abhil.buildtools.server.BuildOperationEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

public final class BuilderBrushItem extends BuildToolItem {
    public BuilderBrushItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos origin = context.getClickedPos().relative(context.getClickedFace());
            BuildOperationEngine.executeBrush(serverPlayer, origin);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
}
