package com.abhil.buildtools.server;

import java.util.HashMap;
import java.util.Map;
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

public final class BuildToolsBuiltBlockData extends SavedData {
    private static final String NAME = "buildtools_built_blocks";
    private final Map<BlockRef, UUID> owners = new HashMap<>();

    public static SavedData.Factory<BuildToolsBuiltBlockData> factory() {
        return new SavedData.Factory<>(BuildToolsBuiltBlockData::new, BuildToolsBuiltBlockData::load);
    }

    public static BuildToolsBuiltBlockData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    private static BuildToolsBuiltBlockData load(CompoundTag tag, HolderLookup.Provider registries) {
        BuildToolsBuiltBlockData data = new BuildToolsBuiltBlockData();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (dimension == null) {
                continue;
            }
            try {
                UUID owner = UUID.fromString(entry.getString("owner"));
                NbtUtils.readBlockPos(entry, "pos").ifPresent(pos -> data.owners.put(new BlockRef(dimension, pos.immutable()), owner));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed ownership entries.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockRef, UUID> ownedBlock : owners.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("owner", ownedBlock.getValue().toString());
            entry.putString("dimension", ownedBlock.getKey().dimension().toString());
            entry.put("pos", NbtUtils.writeBlockPos(ownedBlock.getKey().pos()));
            list.add(entry);
        }
        tag.put("blocks", list);
        return tag;
    }

    public void mark(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        UUID previous = owners.put(new BlockRef(dimension.location(), pos.immutable()), owner);
        if (!owner.equals(previous)) {
            setDirty();
        }
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        if (owners.remove(new BlockRef(dimension.location(), pos.immutable())) != null) {
            setDirty();
        }
    }

    public void restore(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        if (owner == null) {
            remove(dimension, pos);
        } else {
            mark(owner, dimension, pos);
        }
    }

    public UUID owner(ResourceKey<Level> dimension, BlockPos pos) {
        return owners.get(new BlockRef(dimension.location(), pos.immutable()));
    }

    public boolean isBuiltBy(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        return owner.equals(owner(dimension, pos));
    }

    private record BlockRef(ResourceLocation dimension, BlockPos pos) {
    }
}
