package com.abhil.buildtools.item;

import com.abhil.buildtools.server.BuildOperationEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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
            BlockPos origin = context.getClickedPos();
            if (player.isShiftKeyDown()) {
                BuildOperationEngine.copySelection(serverPlayer, origin);
            } else {
                BuildOperationEngine.previewOrConfirmBlueprintPaste(serverPlayer, origin);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
}
