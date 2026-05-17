package com.abhil.buildtools.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class BuildToolsStorageData extends SavedData {
    private static final String NAME = "buildtools_storage";
    private final Set<StorageRef> storages = new HashSet<>();

    public static SavedData.Factory<BuildToolsStorageData> factory() {
        return new SavedData.Factory<>(BuildToolsStorageData::new, BuildToolsStorageData::load);
    }

    public static BuildToolsStorageData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    private static BuildToolsStorageData load(CompoundTag tag, HolderLookup.Provider registries) {
        BuildToolsStorageData data = new BuildToolsStorageData();
        ListTag list = tag.getList("storages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (dimension == null) {
                continue;
            }
            try {
                UUID owner = UUID.fromString(entry.getString("owner"));
                NbtUtils.readBlockPos(entry, "pos").ifPresent(pos -> data.storages.add(new StorageRef(owner, dimension, pos.immutable())));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed or legacy unowned entries.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (StorageRef storage : storages) {
            CompoundTag entry = new CompoundTag();
            entry.putString("owner", storage.owner().toString());
            entry.putString("dimension", storage.dimension().toString());
            entry.put("pos", NbtUtils.writeBlockPos(storage.pos()));
            list.add(entry);
        }
        tag.put("storages", list);
        return tag;
    }

    public boolean add(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        boolean changed = storages.add(new StorageRef(owner, dimension.location(), pos.immutable()));
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean remove(ResourceKey<Level> dimension, BlockPos pos) {
        boolean changed = storages.removeIf(storage -> storage.dimension().equals(dimension.location()) && storage.pos().equals(pos));
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean remove(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        boolean changed = storages.remove(new StorageRef(owner, dimension.location(), pos.immutable()));
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean contains(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        return storages.contains(new StorageRef(owner, dimension.location(), pos.immutable()));
    }

    public int count(UUID owner) {
        int count = 0;
        for (StorageRef storage : storages) {
            if (storage.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    public List<BlockPos> storages(UUID owner, ResourceKey<Level> dimension) {
        List<BlockPos> result = new ArrayList<>();
        ResourceLocation id = dimension.location();
        for (StorageRef storage : storages) {
            if (storage.owner().equals(owner) && storage.dimension().equals(id)) {
                result.add(storage.pos());
            }
        }
        return List.copyOf(result);
    }

    public List<BlockPos> storages(ResourceKey<Level> dimension) {
        List<BlockPos> result = new ArrayList<>();
        ResourceLocation id = dimension.location();
        for (StorageRef storage : storages) {
            if (storage.dimension().equals(id)) {
                result.add(storage.pos());
            }
        }
        return List.copyOf(result);
    }

    private record StorageRef(UUID owner, ResourceLocation dimension, BlockPos pos) {
    }
}
