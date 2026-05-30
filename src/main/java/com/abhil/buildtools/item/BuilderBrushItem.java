package com.abhil.buildtools.item;

import com.abhil.buildtools.server.BuildOperationEngine;
import com.abhil.buildtools.server.BuildToolsModeMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class BuilderBrushItem extends Item {
    public BuilderBrushItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (player.isShiftKeyDown()) {
                BuildToolsModeMenu.open(player);
            } else {
                BuildOperationEngine.pickBrushTarget(player, context.getClickedPos());
            }
        }
        return InteractionResult.SUCCESS;
    }
}
