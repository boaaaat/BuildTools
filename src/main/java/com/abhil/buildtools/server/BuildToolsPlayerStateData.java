package com.abhil.buildtools.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class BuildToolsPlayerStateData extends SavedData {
    private static final String NAME = "buildtools_player_state";
    private final Map<UUID, CompoundTag> players = new HashMap<>();

    public static SavedData.Factory<BuildToolsPlayerStateData> factory() {
        return new SavedData.Factory<>(BuildToolsPlayerStateData::new, BuildToolsPlayerStateData::load);
    }

    public static BuildToolsPlayerStateData get(net.minecraft.server.level.ServerPlayer player) {
        return player.getServer().overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    private static BuildToolsPlayerStateData load(CompoundTag tag, HolderLookup.Provider registries) {
        BuildToolsPlayerStateData data = new BuildToolsPlayerStateData();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try {
                data.players.put(UUID.fromString(key), players.getCompound(key));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy entries instead of blocking the whole save file.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        this.players.forEach((uuid, state) -> players.put(uuid.toString(), state.copy()));
        tag.put("players", players);
        return tag;
    }

    public CompoundTag getPlayer(UUID uuid) {
        CompoundTag tag = players.get(uuid);
        return tag == null ? new CompoundTag() : tag.copy();
    }

    public void putPlayer(UUID uuid, CompoundTag tag) {
        players.put(uuid, tag.copy());
        setDirty();
    }
}
