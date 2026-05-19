package com.abhil.buildtools.server;

import com.abhil.buildtools.registry.ModItems;
import com.abhil.buildtools.item.MagnetItem;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class MagnetCollector {
    private static final double RANGE = 10.0D;
    private static final int UPDATE_INTERVAL_TICKS = 5;

    private MagnetCollector() {
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.tickCount % UPDATE_INTERVAL_TICKS == 0 && hasMagnet(player)) {
                collectNearbyDrops(player);
            }
        }
    }

    private static void collectNearbyDrops(ServerPlayer player) {
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(RANGE),
                drop -> !drop.isRemoved() && !drop.getItem().isEmpty() && canCollect(player, drop));
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            ItemStack original = stack.copy();
            Item item = stack.getItem();
            int originalCount = stack.getCount();
            if (player.getInventory().add(stack)) {
                int pickedUp = originalCount - stack.getCount();
                if (pickedUp > 0) {
                    player.take(drop, pickedUp);
                    player.awardStat(Stats.ITEM_PICKED_UP.get(item), pickedUp);
                    player.onItemPickup(drop);
                }
                if (stack.isEmpty()) {
                    drop.discard();
                } else {
                    drop.setItem(stack);
                }
            } else {
                drop.setItem(original);
            }
        }
    }

    private static boolean hasMagnet(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.MAGNET.get()) && MagnetItem.isEnabled(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canCollect(ServerPlayer player, ItemEntity drop) {
        UUID target = drop.getTarget();
        return target == null || target.equals(player.getUUID());
    }
}
