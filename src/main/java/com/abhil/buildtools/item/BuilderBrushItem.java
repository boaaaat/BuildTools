package com.abhil.buildtools.item;

import com.abhil.buildtools.server.BuildOperationEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

public class BuilderBrushItem extends BuildToolItem {
    public BuilderBrushItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (player.isShiftKeyDown()) {
                BuildOperationEngine.pickBrushTarget(player, context.getClickedPos());
            } else {
                BuildOperationEngine.applyBrush(player, context.getClickedPos(), context.getClickedFace(), false);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
}
