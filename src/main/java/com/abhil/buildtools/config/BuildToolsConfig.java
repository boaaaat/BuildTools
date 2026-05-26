package com.abhil.buildtools.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class BuildToolsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_OPERATION_VOLUME = BUILDER
            .comment("Maximum number of blocks a BuildTools operation may change.")
            .defineInRange("maxOperationVolume", 4096, 1, 262144);

    public static final ModConfigSpec.IntValue MAX_COPY_VOLUME = BUILDER
            .comment("Maximum number of non-air blocks a Blueprint Trowel may copy or paste.")
            .defineInRange("maxCopyVolume", 2048, 1, 262144);

    public static final ModConfigSpec.IntValue MAX_OPERATION_DISTANCE = BUILDER
            .comment("Maximum distance in blocks from the player to every edited block.")
            .defineInRange("maxOperationDistance", 64, 8, 512);

    public static final ModConfigSpec.IntValue BATCH_SIZE = BUILDER
            .comment("Maximum block changes applied per server tick.")
            .defineInRange("batchSize", 512, 1, 8192);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BuildToolsConfig() {
    }
}
