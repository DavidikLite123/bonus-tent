package net.davidlits.bonustent;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.processor.BlockIgnoreStructureProcessor;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

public class BonusTentMod implements ModInitializer {
    public static final String MOD_ID = "bonus_tent";
    public static final Logger LOGGER = LogManager.getLogger("Bonus Tent");

    @Override
    public void onInitialize() {
        LOGGER.info("Bonus Tent Mod initialized!");
    }

    public static void onPlayerFirstSpawn(ServerPlayerEntity player) {
        // Check if this player has already been processed (using scoreboard tags as a simple persistent tag)
        if (player.getScoreboardTags().contains("bonus_tent.processed")) {
            return;
        }

        // Add the tag to mark this player as processed
        player.addScoreboardTag("bonus_tent.processed");

        // Verify if the world was configured to spawn a bonus chest
        boolean hasBonusChest = player.getServer().getSaveProperties().getGeneratorOptions().hasBonusChest();
        if (!hasBonusChest) {
            LOGGER.info("Bonus chest is disabled in world generator options. Skipping tent spawn.");
            return;
        }

        ServerWorld world = player.getServerWorld();

        // Use world persistent state to guarantee the tent is spawned exactly once per world save
        TentState state = world.getPersistentStateManager().getOrCreate(
            () -> new TentState("bonus_tent_spawned"),
            "bonus_tent_spawned"
        );

        if (state.isSpawned()) {
            LOGGER.info("Bonus tent has already been spawned in this world.");
            return;
        }

        state.setSpawned(true);

        // Find coordinates for placement
        BlockPos spawnPos = world.getSpawnPos();
        BlockPos surfacePos = null;

        // Search for a flat 5x5 grass/dirt clearing inside starting chunk areas (up to 48 block radius)
        outer:
        for (int limit = 16; limit <= 48; limit += 16) {
            for (int dx = -limit; dx <= limit; dx++) {
                for (int dz = -limit; dz <= limit; dz++) {
                    int x = spawnPos.getX() + dx;
                    int z = spawnPos.getZ() + dz;
                    int referenceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                    
                    if (isValid5x5FlatArea(world, x, z, referenceY)) {
                        surfacePos = new BlockPos(x, referenceY, z);
                        break outer;
                    }
                }
            }
        }

        // Fallback if no suitable clearing was found
        if (surfacePos == null) {
            surfacePos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, spawnPos);
        }

        LOGGER.info("Spawning bonus tent at coordinates: " + surfacePos.getX() + ", " + surfacePos.getY() + ", " + surfacePos.getZ());

        StructureManager structureManager = world.getStructureManager();
        Structure structure = structureManager.getStructure(new Identifier(MOD_ID, "spawn_tent"));

        if (structure != null) {
            StructurePlacementData placementData = new StructurePlacementData()
                .setRotation(BlockRotation.NONE)
                .setMirror(BlockMirror.NONE)
                .setIgnoreEntities(false); // Do not ignore entities or air blocks

            // Place structure: (world, pos, pivot, placementData, random, flags)
            // Flag 3 triggers block update and notifies observers
            structure.place(world, surfacePos, new BlockPos(0, 0, 0), placementData, new Random(), 3);
            LOGGER.info("Bonus tent structure successfully placed!");

            // Replace dirt under the tent and its perimeter with grass blocks
            for (int x = surfacePos.getX() - 1; x <= surfacePos.getX() + 5; x++) {
                for (int z = surfacePos.getZ() - 1; z <= surfacePos.getZ() + 5; z++) {
                    BlockPos groundPos = new BlockPos(x, surfacePos.getY() - 1, z);
                    if (world.getBlockState(groundPos).isOf(Blocks.DIRT)) {
                        world.setBlockState(groundPos, Blocks.GRASS_BLOCK.getDefaultState(), 3);
                    }
                }
            }

            // Spawn particle effects in a tall beacon to guide the player (30-40 blocks high)
            double cX = surfacePos.getX() + 2.5;
            double cZ = surfacePos.getZ() + 2.5;
            double cY = surfacePos.getY() + 1.0;
            for (int h = 0; h < 40; h++) {
                double particleY = cY + h;
                // Bright sparks and smoke visible from a distance
                world.spawnParticles(ParticleTypes.FLAME, cX, particleY, cZ, 5, 0.2, 0.2, 0.2, 0.02);
                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cX, particleY, cZ, 3, 0.3, 0.3, 0.3, 0.01);
            }

            // Iterate through structure bounds to replace structure blocks with air and assign unique loot tables
            BlockPos size = structure.getSize();
            for (int x = 0; x < size.getX(); x++) {
                for (int y = 0; y < size.getY(); y++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        BlockPos currentPos = surfacePos.add(x, y, z);
                        BlockState blockState = world.getBlockState(currentPos);
                        
                        if (blockState.isOf(Blocks.STRUCTURE_BLOCK)) {
                            // Replace structure block with air
                            world.setBlockState(currentPos, Blocks.AIR.getDefaultState(), 3);
                        } else {
                            BlockEntity blockEntity = world.getBlockEntity(currentPos);
                            if (blockEntity instanceof LootableContainerBlockEntity) {
                                Identifier lootTableId;
                                if (blockState.isOf(Blocks.BARREL)) {
                                    lootTableId = new Identifier(MOD_ID, "chests/barrel_loot");
                                    LOGGER.info("Assigned barrel loot table to container at " + currentPos.getX() + ", " + currentPos.getY() + ", " + currentPos.getZ());
                                } else {
                                    lootTableId = new Identifier(MOD_ID, "chests/tent_loot");
                                    LOGGER.info("Assigned chest loot table to container at " + currentPos.getX() + ", " + currentPos.getY() + ", " + currentPos.getZ());
                                }
                                ((LootableContainerBlockEntity) blockEntity).setLootTable(lootTableId, new Random().nextLong());
                                blockEntity.markDirty();
                            }
                        }
                    }
                }
            }
        } else {
            LOGGER.error("Could not find structure file: data/bonus_tent/structures/spawn_tent.nbt");
        }
    }

    private static boolean isValid5x5FlatArea(ServerWorld world, int startX, int startZ, int referenceY) {
        for (int x = startX; x < startX + 5; x++) {
            for (int z = startZ; z < startZ + 5; z++) {
                // Height must be exactly referenceY (flat area check)
                if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) != referenceY) {
                    return false;
                }

                // Ground block below must be GRASS_BLOCK or DIRT
                BlockPos groundPos = new BlockPos(x, referenceY - 1, z);
                BlockState groundState = world.getBlockState(groundPos);
                if (!groundState.isOf(Blocks.GRASS_BLOCK) && !groundState.isOf(Blocks.DIRT)) {
                    return false;
                }

                // Space above (up to 5 blocks) must not contain leaves, logs, or fluids
                for (int y = referenceY; y < referenceY + 5; y++) {
                    BlockPos currentPos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(currentPos);
                    
                    if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS) || !state.getFluidState().isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static class TentState extends PersistentState {
        private boolean spawned = false;

        public TentState(String key) {
            super(key);
        }

        @Override
        public void fromTag(NbtCompound tag) {
            spawned = tag.getBoolean("spawned");
        }

        @Override
        public NbtCompound writeNbt(NbtCompound tag) {
            tag.putBoolean("spawned", spawned);
            return tag;
        }

        public boolean isSpawned() {
            return spawned;
        }

        public void setSpawned(boolean spawned) {
            this.spawned = spawned;
            this.markDirty();
        }
    }
}
