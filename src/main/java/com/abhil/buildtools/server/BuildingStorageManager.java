package com.abhil.buildtools.server;

import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public final class BuildingStorageManager {
    private static final int MAX_STORAGES_PER_PLAYER = 10;

    private BuildingStorageManager() {
    }

    public static boolean mark(ServerPlayer player, BlockPos pos) {
        return toggle(player, pos).consumeLink();
    }

    public static LinkResult toggle(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockPos storagePos = canonicalStoragePos(level, pos);
        if (handler(level, storagePos) == null) {
            player.displayClientMessage(Component.translatable("buildtools.error.not_storage"), false);
            return LinkResult.FAILED;
        }
        BuildToolsStorageData data = BuildToolsStorageData.get(player.getServer());
        if (data.contains(player.getUUID(), level.dimension(), storagePos)) {
            data.remove(player.getUUID(), level.dimension(), storagePos);
            player.displayClientMessage(Component.translatable("buildtools.message.storage_untracked"), true);
            return LinkResult.UNLINKED;
        }
        trackedStorages(player);
        if (data.count(player.getUUID()) >= MAX_STORAGES_PER_PLAYER) {
            player.displayClientMessage(Component.translatable("buildtools.error.storage_limit", MAX_STORAGES_PER_PLAYER), false);
            return LinkResult.FAILED;
        }
        data.add(player.getUUID(), level.dimension(), storagePos);
        player.displayClientMessage(Component.translatable("buildtools.message.storage_tracked"), true);
        return LinkResult.LINKED;
    }

    public static void unmark(ServerLevel level, BlockPos pos) {
        BuildToolsStorageData data = BuildToolsStorageData.get(level.getServer());
        data.remove(level.dimension(), pos);
        data.remove(level.dimension(), canonicalStoragePos(level, pos));
    }

    public static int count(ServerPlayer player, ItemStackKey key) {
        int count = 0;
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : trackedStorages(player)) {
            IItemHandler handler = handler(level, pos);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.is(key.item())) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    public static Map<ItemStackKey, Integer> accessibleMaterialCounts(ServerPlayer player) {
        Map<ItemStackKey, Integer> counts = new LinkedHashMap<>();
        addInventoryMaterialCounts(player, counts);
        addStorageMaterialCounts(player, counts);
        return counts;
    }

    public static int extract(ServerPlayer player, ItemStackKey key, int amount) {
        int remaining = amount;
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : trackedStorages(player)) {
            IItemHandler handler = handler(level, pos);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.is(key.item())) {
                    ItemStack extracted = handler.extractItem(slot, remaining, false);
                    remaining -= extracted.getCount();
                }
            }
            if (remaining <= 0) {
                break;
            }
        }
        return amount - remaining;
    }

    public static void depositOrGive(ServerPlayer player, Map<ItemStackKey, Integer> items) {
        if (items.isEmpty()) {
            return;
        }
        depositOrGive(player, StoredItems.fromCounts(items));
    }

    public static void depositOrGive(ServerPlayer player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        for (ItemStack item : items) {
            if (item.isEmpty()) {
                continue;
            }
            ItemStack stack = item.copy();
            ItemStack remaining = deposit(player, stack);
            if (!remaining.isEmpty()) {
                remaining = giveToPlayer(player, remaining);
            }
            if (!remaining.isEmpty()) {
                player.drop(remaining, false);
            }
        }
    }

    public static void depositOrGiveStack(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remaining = deposit(player, stack.copy());
        if (!remaining.isEmpty()) {
            remaining = giveToPlayer(player, remaining);
        }
        if (!remaining.isEmpty()) {
            player.drop(remaining, false);
        }
    }

    public static boolean hasItems(ServerPlayer player, Map<ItemStackKey, Integer> items) {
        for (Map.Entry<ItemStackKey, Integer> entry : items.entrySet()) {
            int available = count(player, entry.getKey()) + countPlayerInventory(player, entry.getKey());
            if (available < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public static void extractItems(ServerPlayer player, Map<ItemStackKey, Integer> items) {
        for (Map.Entry<ItemStackKey, Integer> entry : items.entrySet()) {
            int remaining = entry.getValue() - extract(player, entry.getKey(), entry.getValue());
            if (remaining > 0) {
                extractPlayerInventory(player, entry.getKey(), remaining);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 8 != 0) {
            return;
        }
        BuildToolsStorageData data = BuildToolsStorageData.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            List<BlockPos> storages = data.storages(level.dimension());
            for (BlockPos pos : storages) {
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                if (handler(level, pos) == null) {
                    data.remove(level.dimension(), pos);
                    continue;
                }
                showTrackedParticles(level, pos);
            }
        }
    }

    private static ItemStack deposit(ServerPlayer player, ItemStack stack) {
        ItemStack remaining = stack.copy();
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : trackedStorages(player)) {
            IItemHandler handler = handler(level, pos);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    private static ItemStack giveToPlayer(ServerPlayer player, ItemStack stack) {
        ItemStack remaining = stack.copy();
        if (player.getInventory().add(remaining)) {
            return ItemStack.EMPTY;
        }
        return remaining;
    }

    private static int countPlayerInventory(ServerPlayer player, ItemStackKey key) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(key.item())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void addInventoryMaterialCounts(ServerPlayer player, Map<ItemStackKey, Integer> counts) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            addMaterialCount(counts, stack);
        }
    }

    private static void addStorageMaterialCounts(ServerPlayer player, Map<ItemStackKey, Integer> counts) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : trackedStorages(player)) {
            IItemHandler handler = handler(level, pos);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                addMaterialCount(counts, handler.getStackInSlot(slot));
            }
        }
    }

    private static void addMaterialCount(Map<ItemStackKey, Integer> counts, ItemStack stack) {
        if (stack.isEmpty() || BuildMaterialSource.stateFromStack(stack) == null) {
            return;
        }
        counts.merge(new ItemStackKey(stack.getItem()), stack.getCount(), Integer::sum);
    }

    private static int extractPlayerInventory(ServerPlayer player, ItemStackKey key, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(key.item())) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        player.getInventory().setChanged();
        return amount - remaining;
    }

    private static List<BlockPos> trackedStorages(ServerLevel level) {
        BuildToolsStorageData data = BuildToolsStorageData.get(level.getServer());
        List<BlockPos> positions = data.storages(level.dimension());
        Iterator<BlockPos> iterator = positions.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (level.hasChunkAt(pos) && handler(level, pos) == null) {
                data.remove(level.dimension(), pos);
            }
        }
        return data.storages(level.dimension());
    }

    private static List<BlockPos> trackedStorages(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BuildToolsStorageData data = BuildToolsStorageData.get(player.getServer());
        List<BlockPos> positions = data.storages(player.getUUID(), level.dimension());
        Iterator<BlockPos> iterator = positions.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (level.hasChunkAt(pos) && handler(level, pos) == null) {
                data.remove(level.dimension(), pos);
            }
        }
        return data.storages(player.getUUID(), level.dimension());
    }

    private static IItemHandler handler(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, level.getBlockEntity(pos), (Direction) null);
    }

    private static BlockPos canonicalStoragePos(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return pos;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos connected = pos.relative(ChestBlock.getConnectedDirection(state));
            return comparePositions(pos, connected) <= 0 ? pos.immutable() : connected.immutable();
        }
        return pos.immutable();
    }

    private static int comparePositions(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) {
            return x;
        }
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
    }

    private static void showTrackedParticles(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.1D;
        double z = pos.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.45D, 0.35D, 0.45D, 0.015D);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.15D, z, 4, 0.35D, 0.25D, 0.35D, 0.0D);
        level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 18, 0.55D, 0.35D, 0.55D, 0.04D);
    }

    public enum LinkResult {
        LINKED(true),
        UNLINKED(false),
        FAILED(false);

        private final boolean consumeLink;

        LinkResult(boolean consumeLink) {
            this.consumeLink = consumeLink;
        }

        public boolean consumeLink() {
            return consumeLink;
        }
    }
}
