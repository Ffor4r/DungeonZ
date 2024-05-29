package net.dungeonz.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.dungeonz.DungeonzMain;
import net.dungeonz.access.BossEntityAccess;
import net.dungeonz.access.ServerPlayerAccess;
import net.dungeonz.block.DungeonGateBlock;
import net.dungeonz.block.entity.DungeonGateEntity;
import net.dungeonz.block.entity.DungeonPortalEntity;
import net.dungeonz.block.entity.DungeonSpawnerEntity;
import net.dungeonz.init.BlockInit;
import net.dungeonz.init.TagInit;
import net.dungeonz.util.InventoryHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.rpgdifficulty.api.MobStrengthener;

public class DungeonPlacementHandler {

    public static TeleportTarget enter(ServerPlayerEntity serverPlayerEntity, ServerWorld dungeonWorld, ServerWorld oldWorld, DungeonPortalEntity portalEntity, BlockPos portalPos, String difficulty,
            boolean disableEffects) {
        BlockPos playerBlockPos = serverPlayerEntity.getBlockPos().mutableCopy();

        if (oldWorld.getBlockState(playerBlockPos).isOf(BlockInit.DUNGEON_PORTAL) || oldWorld.getBlockState(playerBlockPos.down()).isOf(BlockInit.DUNGEON_PORTAL)) {
            if (oldWorld.getBlockState(playerBlockPos).isOf(BlockInit.DUNGEON_PORTAL)) {
                playerBlockPos = playerBlockPos.up();
            }
            for (int i = 0; i < 4; i++) {
                if (oldWorld.getBlockState(playerBlockPos.offset(Direction.fromHorizontal(i), 1).up(0)).isAir()
                        && oldWorld.getBlockState(playerBlockPos.offset(Direction.fromHorizontal(i), 1).up(1)).isAir()) {
                    playerBlockPos = playerBlockPos.offset(Direction.fromHorizontal(i), 1);
                    break;
                }
                if (i == 3) {
                    playerBlockPos = BlockPos.ofFloored(PlayerEntity.findRespawnPosition(oldWorld.getServer().getWorld(serverPlayerEntity.getSpawnPointDimension()),
                            ((ServerPlayerAccess) serverPlayerEntity).getDungeonSpawnBlockPos(), 0.0f, true, true).get());
                }
            }
        }
        ((ServerPlayerAccess) serverPlayerEntity).setDungeonInfo(oldWorld, portalPos, playerBlockPos);
        if (disableEffects) {
            serverPlayerEntity.clearStatusEffects();
        }

        portalEntity.joinDungeon(serverPlayerEntity.getUuid());

        return new TeleportTarget(Vec3d.of(new BlockPos(0, 0, 0).add(portalPos.getX() * 16, 100, portalPos.getZ() * 16)).add(0.5, 0, 0.5), Vec3d.ZERO, 0, 0);
    }

    public static TeleportTarget leave(ServerPlayerEntity serverPlayerEntity, ServerWorld serverWorld) {
        if (serverWorld.getBlockEntity(((ServerPlayerAccess) serverPlayerEntity).getDungeonPortalBlockPos()) != null) {
            ((DungeonPortalEntity) serverWorld.getBlockEntity(((ServerPlayerAccess) serverPlayerEntity).getDungeonPortalBlockPos())).leaveDungeon(serverPlayerEntity.getUuid());
        }
        return new TeleportTarget(Vec3d.of(((ServerPlayerAccess) serverPlayerEntity).getDungeonSpawnBlockPos()).add(0.5, 0, 0.5), Vec3d.ZERO, serverWorld.random.nextFloat() * 360F, 0);
    }

    public static void generateDungeonStructure(ServerWorld world, BlockPos pos, DungeonPortalEntity portalEntity) {
        Registry<StructurePool> registry = world.getRegistryManager().get(RegistryKeys.TEMPLATE_POOL);

        RegistryEntry<StructurePool> registryEntry = registry.entryOf(RegistryKey.of(RegistryKeys.TEMPLATE_POOL, portalEntity.getDungeon().getStructurePoolId()));
        generate(world, portalEntity, portalEntity.getDungeon(), registryEntry, new Identifier("dungeonz:spawn"), 64, pos, false);
    }

    private static boolean generate(ServerWorld world, DungeonPortalEntity portalEntity, Dungeon dungeon, RegistryEntry<StructurePool> structurePool, Identifier id, int size, BlockPos pos,
            boolean keepJigsaws) {
        ChunkGenerator chunkGenerator = world.getChunkManager().getChunkGenerator();
        StructureTemplateManager structureTemplateManager = world.getStructureTemplateManager();
        StructureAccessor structureAccessor = world.getStructureAccessor();
        Random random = world.getRandom();
        Structure.Context context = new Structure.Context(world.getRegistryManager(), chunkGenerator, chunkGenerator.getBiomeSource(), world.getChunkManager().getNoiseConfig(),
                structureTemplateManager, world.getSeed(), new ChunkPos(pos), world, registryEntry -> true);
        Optional<Structure.StructurePosition> optional = StructurePoolBasedGenerator.generate(context, structurePool, Optional.of(id), size, pos, false, Optional.empty(), 512);
        if (optional.isPresent()) {
            HashMap<Integer, ArrayList<BlockPos>> blockIdPosMap = new HashMap<Integer, ArrayList<BlockPos>>();
            ArrayList<BlockPos> chestPosList = new ArrayList<BlockPos>();
            ArrayList<BlockPos> exitPosList = new ArrayList<BlockPos>();
            ArrayList<BlockPos> gatePosList = new ArrayList<BlockPos>();
            HashMap<BlockPos, Integer> spawnerPosEntityIdMap = new HashMap<BlockPos, Integer>();
            Block exitBlock = Registries.BLOCK.get(dungeon.getExitBlockId());
            Block bossLootBlock = Registries.BLOCK.get(dungeon.getBossLootBlockId());

            StructurePiecesCollector structurePiecesCollector = optional.get().generate();
            for (StructurePiece structurePiece : structurePiecesCollector.toList().pieces()) {
                if (!(structurePiece instanceof PoolStructurePiece)) {
                    continue;
                }
                PoolStructurePiece poolStructurePiece = (PoolStructurePiece) structurePiece;
                poolStructurePiece.generate((StructureWorldAccess) world, structureAccessor, chunkGenerator, random, BlockBox.infinite(), pos, keepJigsaws);

                portalEntity.addDungeonEdge(poolStructurePiece.getBoundingBox().getMinX(), poolStructurePiece.getBoundingBox().getMinY(), poolStructurePiece.getBoundingBox().getMinZ());
                portalEntity.addDungeonEdge(poolStructurePiece.getBoundingBox().getMaxX(), poolStructurePiece.getBoundingBox().getMaxY(), poolStructurePiece.getBoundingBox().getMaxZ());
                for (int i = poolStructurePiece.getBoundingBox().getMinX(); i <= poolStructurePiece.getBoundingBox().getMaxX(); i++) {
                    for (int u = poolStructurePiece.getBoundingBox().getMinY(); u <= poolStructurePiece.getBoundingBox().getMaxY(); u++) {
                        for (int o = poolStructurePiece.getBoundingBox().getMinZ(); o <= poolStructurePiece.getBoundingBox().getMaxZ(); o++) {
                            BlockPos checkPos = new BlockPos(i, u, o);
                            BlockState state = world.getBlockState(checkPos);
                            if (!state.isAir()) {
                                int blockId = Registries.BLOCK.getRawId(state.getBlock());
                                if (dungeon.containsBlockId(blockId)) {
                                    if (!blockIdPosMap.containsKey(blockId)) {
                                        ArrayList<BlockPos> newList = new ArrayList<BlockPos>();
                                        newList.add(checkPos);
                                        blockIdPosMap.put(blockId, newList);
                                    } else {
                                        blockIdPosMap.get(blockId).add(checkPos);
                                    }
                                } else if (dungeon.getBossBlockId() == blockId) {
                                    portalEntity.setBossBlockPos(checkPos);
                                } else if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.BARREL) || state.isOf(Blocks.TRAPPED_CHEST)) {
                                    chestPosList.add(checkPos);
                                } else if (state.isOf(exitBlock)) {
                                    exitPosList.add(checkPos);
                                } else if (state.isOf(bossLootBlock)) {
                                    portalEntity.setBossLootBlockPos(checkPos);
                                } else if (state.isOf(BlockInit.DUNGEON_SPAWNER)) {
                                    spawnerPosEntityIdMap.put(checkPos, ((DungeonSpawnerEntity) world.getBlockEntity(checkPos)).getLogic().getEntityId());
                                } else if (state.isOf(BlockInit.DUNGEON_GATE)) {
                                    gatePosList.add(checkPos);
                                    if (world.getBlockEntity(checkPos) != null && ((DungeonGateEntity) world.getBlockEntity(checkPos)).getUnlockItem() == null) {
                                        DungeonGateEntity dungeonGateEntity = (DungeonGateEntity) world.getBlockEntity(checkPos);
                                        dungeonGateEntity.addDungeonEdge(poolStructurePiece.getBoundingBox().getMinX(), poolStructurePiece.getBoundingBox().getMinY(),
                                                poolStructurePiece.getBoundingBox().getMinZ());
                                        dungeonGateEntity.addDungeonEdge(poolStructurePiece.getBoundingBox().getMaxX(), poolStructurePiece.getBoundingBox().getMaxY(),
                                                poolStructurePiece.getBoundingBox().getMaxZ());
                                        dungeonGateEntity.markDirty();
                                    }
                                } else if (state.getBlock() instanceof FallingBlock fallingBlock) {

                                }
                            }
                        }
                    }
                }
            }
            portalEntity.setChestPosList(chestPosList);
            portalEntity.setExitPosList(exitPosList);
            portalEntity.setBlockMap(blockIdPosMap);
            portalEntity.setSpawnerPosEntityIdMap(spawnerPosEntityIdMap);
            portalEntity.setGatePosList(gatePosList);
            portalEntity.markDirty();
            return true;
        }
        return false;
    }

    public static void prepareDungeon(ServerWorld dungeonWorld, DungeonPortalEntity portalEntity) {
        List<ChunkPos> chunkPosList = new ArrayList<ChunkPos>();
        for (int i = 0; i < portalEntity.getDungeonEdgeList().size() / 6; i++) {
            int x1 = portalEntity.getDungeonEdgeList().get(6 * i);
            int z1 = portalEntity.getDungeonEdgeList().get(2 + 6 * i);

            int x2 = portalEntity.getDungeonEdgeList().get(3 + 6 * i);
            int z2 = portalEntity.getDungeonEdgeList().get(5 + 6 * i);

            int xCount = Math.abs(x1 - x2) / 16 + ((x1 - x2) % 16 == 0 ? 0 : 1);
            int zCount = Math.abs(z1 - z2) / 16 + ((z1 - z2) % 16 == 0 ? 0 : 1);
            for (int u = 0; u < xCount; u++) {
                for (int o = 0; o < zCount; o++) {
                    ChunkPos chunkPos = new ChunkPos(ChunkSectionPos.getSectionCoord(x1 + 16 * u), ChunkSectionPos.getSectionCoord(z1 + 16 * o));
                    if (!chunkPosList.contains(chunkPos)) {
                        chunkPosList.add(chunkPos);
                    }
                }
            }
        }
        for (int i = 0; i < chunkPosList.size(); i++) {
            dungeonWorld.getChunkManager().addTicket(ChunkTicketType.PORTAL, chunkPosList.get(i), 1, chunkPosList.get(i).getStartPos());
        }
    }

    public static void refreshDungeon(MinecraftServer server, ServerWorld world, DungeonPortalEntity portalEntity, Dungeon dungeon, String difficulty, boolean luck) {

        // Could be tested with create = true
        // world.getChunkManager().threadedAnvilChunkStorage.getChunk(holder, requiredStatus).thenApply(either -> {
        // // This block will be executed when the CompletableFuture completes
        // // either contains the result of the getChunk method
        // return either.map(chunk -> {
        // // Do something with the Chunk
        // System.out.println("Got chunk: " + chunk);
        // return chunk;
        // }, unloaded -> {
        // // Handle the Unloaded case
        // System.out.println("Chunk is unloaded");
        // return null;
        // });
        // }).thenAccept(result -> {
        // // This block will be executed after the thenApply block
        // System.out.println("Completed processing of chunk");
        // });

        // Refresh mobs
        for (int u = 0; u < portalEntity.getDungeonEdgeList().size() / 6; u++) {
            List<Entity> entities = world.getOtherEntities(null,
                    new Box(portalEntity.getDungeonEdgeList().get(6 * u), portalEntity.getDungeonEdgeList().get(1 + 6 * u), portalEntity.getDungeonEdgeList().get(2 + 6 * u),
                            portalEntity.getDungeonEdgeList().get(3 + 6 * u), portalEntity.getDungeonEdgeList().get(4 + 6 * u), portalEntity.getDungeonEdgeList().get(5 + 6 * u)));

            for (int i = 0; i < entities.size(); i++) {
                if (!(entities.get(i) instanceof ServerPlayerEntity)
                        && (entities.get(i) instanceof LivingEntity || entities.get(i) instanceof ItemEntity || entities.get(i) instanceof ProjectileEntity)) {
                    entities.get(i).discard();
                }
            }
        }
        portalEntity.setDifficulty(difficulty);

        Iterator<Entry<Integer, ArrayList<BlockPos>>> iterator = portalEntity.getBlockMap().entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Integer, ArrayList<BlockPos>> entry = iterator.next();
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (dungeon.getBlockIdBlockReplacementMap().get(entry.getKey()) != -1) {
                    if (dungeon.getBlockIdBlockReplacementMap().get(entry.getKey()) == 0) {
                        world.removeBlock(entry.getValue().get(i), false);
                    } else {
                        world.setBlockState(entry.getValue().get(i), Registries.BLOCK.get(dungeon.getBlockIdBlockReplacementMap().get(entry.getKey())).getDefaultState(), 3);
                    }
                }
                // dungeon.getBlockIdEntitySpawnChanceMap().containsKey(blockId) &&
                if (world.getRandom().nextFloat() <= dungeon.getBlockIdEntitySpawnChanceMap().get(entry.getKey()).get(difficulty)) {
                    MobEntity mobEntity = createMob(world, dungeon.getBlockIdEntityMap().get(entry.getKey()).get(world.getRandom().nextInt(dungeon.getBlockIdEntityMap().get(entry.getKey()).size())),
                            null);
                    // hopefully initialize doesn't lead to problems
                    mobEntity.initialize(world, world.getLocalDifficulty(entry.getValue().get(i)), SpawnReason.STRUCTURE, null, null);
                    mobEntity.setPersistent();
                    strengthenMob(mobEntity, dungeon, difficulty, false);
                    dungeon.getBlockIdBlockReplacementMap().get(entry.getKey());
                    mobEntity.refreshPositionAndAngles(entry.getValue().get(i), 360f * world.getRandom().nextFloat(), 0.0f);
                    world.spawnEntity(mobEntity);
                }
            }
        }

        // Refresh boss
        MobEntity bossEntity = createMob(world, dungeon.getBossEntityType(), dungeon.getBossNbtCompound());
        bossEntity.initialize(world, world.getLocalDifficulty(portalEntity.getBossBlockPos()), SpawnReason.STRUCTURE, null, null);
        bossEntity.setPersistent();
        ((BossEntityAccess) bossEntity).setBoss(portalEntity.getPos(), portalEntity.getWorld().getRegistryKey().getValue().toString());
        strengthenMob(bossEntity, dungeon, difficulty, true);

        if (dungeon.getBlockIdBlockReplacementMap().get(dungeon.getBossBlockId()) != -1) {
            if (dungeon.getBlockIdBlockReplacementMap().get(dungeon.getBossBlockId()) == 0) {
                world.removeBlock(portalEntity.getBossBlockPos(), false);
            } else {
                world.setBlockState(portalEntity.getBossBlockPos(), Registries.BLOCK.get(dungeon.getBlockIdBlockReplacementMap().get(dungeon.getBossBlockId())).getDefaultState(), 3);
            }
        }
        bossEntity.refreshPositionAndAngles(portalEntity.getBossBlockPos(), 360f * world.getRandom().nextFloat(), 0.0f);
        world.spawnEntity(bossEntity);

        // Refresh chests
        for (int i = 0; i < portalEntity.getChestPosList().size(); i++) {
            String lootTableString = dungeon.getDifficultyLootTableIdMap().get(difficulty).get(world.getRandom().nextInt(dungeon.getDifficultyLootTableIdMap().get(difficulty).size()));
            InventoryHelper.fillInventoryWithLoot(server, world, portalEntity.getChestPosList().get(i), lootTableString, luck);
        }
        // Refresh exit
        for (int i = 0; i < portalEntity.getExitPosList().size(); i++) {
            world.setBlockState(portalEntity.getExitPosList().get(i),
                    dungeon.getBlockIdBlockReplacementMap().containsKey(dungeon.getExitBlockId()) && dungeon.getBlockIdBlockReplacementMap().get(dungeon.getExitBlockId()) != -1
                            ? Registries.BLOCK.get(dungeon.getBlockIdBlockReplacementMap().get(dungeon.getExitBlockId())).getDefaultState()
                            : Registries.BLOCK.get(dungeon.getExitBlockId()).getDefaultState(),
                    3);
        } // Refresh gates
        for (int i = 0; i < portalEntity.getGatePosList().size(); i++) {
            world.setBlockState(portalEntity.getGatePosList().get(i), world.getBlockState(portalEntity.getGatePosList().get(i)).cycle(DungeonGateBlock.ENABLED));
        }
        // Refresh boss loot
        world.setBlockState(portalEntity.getBossLootBlockPos(),
                dungeon.getBlockIdBlockReplacementMap().containsKey(dungeon.getBossLootBlockId()) && dungeon.getBlockIdBlockReplacementMap().get(dungeon.getBossLootBlockId()) != -1
                        ? Registries.BLOCK.get(dungeon.getBlockIdBlockReplacementMap().get(dungeon.getBossLootBlockId())).getDefaultState()
                        : Registries.BLOCK.get(dungeon.getBossLootBlockId()).getDefaultState(),
                3);
        // Refresh spawner
        Iterator<Entry<BlockPos, Integer>> spawnerPosIterator = portalEntity.getSpawnerPosEntityIdMap().entrySet().iterator();
        while (spawnerPosIterator.hasNext()) {
            Entry<BlockPos, Integer> entry = spawnerPosIterator.next();
            world.setBlockState(entry.getKey(), BlockInit.DUNGEON_SPAWNER.getDefaultState(), 3);
            ((DungeonSpawnerEntity) world.getBlockEntity(entry.getKey())).getLogic().setDungeonInfo(dungeon, difficulty,
                    dungeon.getSpawnerEntityIdMap().containsKey(entry.getValue()) ? dungeon.getSpawnerEntityIdMap().get(entry.getValue()) : 0, Registries.ENTITY_TYPE.get(entry.getValue()));
        }
        // Refresh blocks
        Iterator<Entry<BlockPos, Integer>> replaceBlockIterator = portalEntity.getReplaceBlockIdMap().entrySet().iterator();
        while (replaceBlockIterator.hasNext()) {
            Entry<BlockPos, Integer> entry = replaceBlockIterator.next();
            world.setBlockState(entry.getKey(), Registries.BLOCK.get(entry.getValue()).getDefaultState(), 3);
        }
        portalEntity.getDungeonPlayerUuids().clear();
        portalEntity.getDeadDungeonPlayerUUIDs().clear();
        portalEntity.markDirty();
    }

    @Nullable
    private static MobEntity createMob(ServerWorld world, EntityType<?> type, @Nullable NbtCompound nbt) {
        MobEntity mobEntity;
        try {
            Object entity = type.create(world);
            if (!(entity instanceof MobEntity)) {
                throw new IllegalStateException("Trying to spawn a non-mob: " + Registries.ENTITY_TYPE.getId(type));
            }
            mobEntity = (MobEntity) entity;

        } catch (Exception exception) {
            DungeonzMain.LOGGER.warn("Failed to create mob", exception);
            return null;
        }
        if (nbt != null) {
            NbtCompound nbtCompound = mobEntity.writeNbt(new NbtCompound());
            nbtCompound.copyFrom(nbt);
            mobEntity.readNbt(nbtCompound);
        }
        if (mobEntity.getType().isIn(TagInit.IMMUNE_TO_ZOMBIFICATION)) {
            NbtCompound nbtCompound = mobEntity.writeNbt(new NbtCompound());
            nbtCompound.putBoolean("IsImmuneToZombification", true);
            mobEntity.readNbt(nbtCompound);
        }
        return mobEntity;
    }

    public static void strengthenMob(MobEntity mobEntity, Dungeon dungeon, String difficulty, boolean isBossEntity) {
        double mobHealth = mobEntity.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH);
        double mobDamage = 0.0D;
        double mobProtection = 0.0D;

        boolean hasAttackDamageAttribute = mobEntity.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        boolean hasArmorAttribute = mobEntity.getAttributes().hasAttribute(EntityAttributes.GENERIC_ARMOR);

        if (hasAttackDamageAttribute) {
            mobDamage = mobEntity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        }
        if (hasArmorAttribute) {
            mobProtection = mobEntity.getAttributeValue(EntityAttributes.GENERIC_ARMOR);
        }
        float strengthFactor = 0.0f;
        if (isBossEntity) {
            strengthFactor = dungeon.getDifficultyBossModificatorMap().get(difficulty);
        } else {
            strengthFactor = dungeon.getDifficultyMobModificatorMap().get(difficulty);
        }
        mobHealth *= strengthFactor;
        mobDamage *= strengthFactor;
        mobProtection *= strengthFactor;

        // round factor
        mobHealth = Math.round(mobHealth * 100.0D) / 100.0D;
        mobDamage = Math.round(mobDamage * 100.0D) / 100.0D;
        mobProtection = Math.round(mobProtection * 100.0D) / 100.0D;

        // Set Values
        mobEntity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(mobHealth);
        mobEntity.heal(mobEntity.getMaxHealth());
        if (hasAttackDamageAttribute) {
            mobEntity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(mobDamage);
        }
        if (hasArmorAttribute) {
            mobEntity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(mobProtection);
        }
        if (DungeonzMain.isRpgDifficultyLoaded) {
            MobStrengthener.setMobHealthMultiplier(mobEntity, strengthFactor);
        }

    }

}
