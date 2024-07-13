package net.dungeonz.block.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.jetbrains.annotations.Nullable;

import net.dungeonz.block.screen.DungeonPortalScreenHandler;
import net.dungeonz.dungeon.Dungeon;
import net.dungeonz.dungeon.DungeonPlacementHandler;
import net.dungeonz.init.BlockInit;
import net.dungeonz.init.ConfigInit;
import net.dungeonz.init.CriteriaInit;
import net.dungeonz.init.DimensionInit;
import net.dungeonz.init.SoundInit;
import net.dungeonz.network.DungeonServerPacket;
import net.dungeonz.network.packet.DungeonPortalPacket;
import net.dungeonz.util.DungeonHelper;
import net.dungeonz.util.InventoryHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class DungeonPortalEntity extends EndPortalBlockEntity implements ExtendedScreenHandlerFactory<DungeonPortalPacket> {

    private Text title = Text.translatable("container.dungeon_portal");
    private String dungeonType = "";
    private String difficulty = "";
    private boolean dungeonStructureGenerated = false;
    private List<UUID> dungeonPlayerUuids = new ArrayList<UUID>();
    private List<UUID> deadDungeonPlayerUuids = new ArrayList<UUID>();
    private int maxGroupSize = 0;
    private int minGroupSize = 0;
    private List<UUID> waitingUuids = new ArrayList<UUID>();
    private int cooldownTime = 0;
    private int autoKickTime = 0;
    private boolean disableEffects = false;
    private boolean privateGroup = false;
    private HashMap<Integer, ArrayList<BlockPos>> blockBlockPosMap = new HashMap<Integer, ArrayList<BlockPos>>();
    private List<BlockPos> chestPosList = new ArrayList<BlockPos>();
    private List<BlockPos> exitPosList = new ArrayList<BlockPos>();
    private List<BlockPos> gatePosList = new ArrayList<BlockPos>();
    private BlockPos bossBlockPos = new BlockPos(0, 0, 0);
    private BlockPos bossLootBlockPos = new BlockPos(0, 0, 0);
    private HashMap<BlockPos, Integer> spawnerPosEntityIdMap = new HashMap<BlockPos, Integer>();
    private HashMap<BlockPos, Integer> replacePosBlockIdMap = new HashMap<BlockPos, Integer>();
    private List<Integer> dungeonEdgeList = new ArrayList<Integer>();
    private int dungeonTeleportCountdown = 0;

    public DungeonPortalEntity(BlockPos pos, BlockState state) {
        super(BlockInit.DUNGEON_PORTAL_ENTITY, pos, state);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        this.dungeonType = nbt.getString("DungeonType");
        this.difficulty = nbt.getString("Difficulty");
        this.dungeonStructureGenerated = nbt.getBoolean("DungeonStructureGenerated");
        this.dungeonPlayerUuids.clear();
        for (int i = 0; i < nbt.getInt("DungeonPlayerCount"); i++) {
            this.dungeonPlayerUuids.add(nbt.getUuid("PlayerUUID" + i));
        }
        this.deadDungeonPlayerUuids.clear();
        for (int i = 0; i < nbt.getInt("DeadDungeonPlayerCount"); i++) {
            this.deadDungeonPlayerUuids.add(nbt.getUuid("DeadPlayerUUID" + i));
        }
        this.maxGroupSize = nbt.getInt("MaxGroupSize");
        this.minGroupSize = nbt.getInt("MinGroupSize");
        this.cooldownTime = nbt.getInt("CooldownTime");
        this.autoKickTime = nbt.getInt("AutoKickTime");
        this.disableEffects = nbt.getBoolean("DisableEffects");
        this.privateGroup = nbt.getBoolean("PrivateGroup");
        this.blockBlockPosMap.clear();
        if (nbt.getInt("BlockMapSize") > 0) {
            for (int i = 0; i < nbt.getInt("BlockMapSize"); i++) {
                ArrayList<BlockPos> posList = new ArrayList<>();
                for (int u = 0; u < nbt.getInt("BlockListSize" + i); u++) {
                    posList.add(new BlockPos(nbt.getInt("BlockPosX" + i + "" + u), nbt.getInt("BlockPosY" + i + "" + u), nbt.getInt("BlockPosZ" + i + "" + u)));
                }
                this.blockBlockPosMap.put(nbt.getInt("BlockId" + i), posList);
            }
        }

        this.bossBlockPos = new BlockPos(nbt.getInt("BossPosX"), nbt.getInt("BossPosY"), nbt.getInt("BossPosZ"));
        this.bossLootBlockPos = new BlockPos(nbt.getInt("BossLootPosX"), nbt.getInt("BossLootPosY"), nbt.getInt("BossLootPosZ"));

        if (nbt.getInt("ChestListSize") > 0) {
            this.chestPosList.clear();
            for (int i = 0; i < nbt.getInt("ChestListSize"); i++) {
                this.chestPosList.add(new BlockPos(nbt.getInt("ChestPosX" + i), nbt.getInt("ChestPosY" + i), nbt.getInt("ChestPosZ" + i)));
            }
        }

        if (nbt.getInt("ExitListSize") > 0) {
            this.exitPosList.clear();
            for (int i = 0; i < nbt.getInt("ExitListSize"); i++) {
                this.exitPosList.add(new BlockPos(nbt.getInt("ExitPosX" + i), nbt.getInt("ExitPosY" + i), nbt.getInt("ExitPosZ" + i)));
            }
        }

        if (nbt.getInt("SpawnerMapSize") > 0) {
            this.spawnerPosEntityIdMap.clear();
            for (int i = 0; i < nbt.getInt("SpawnerListSize"); i++) {
                this.spawnerPosEntityIdMap.put(new BlockPos(nbt.getInt("SpawnerPosX" + i), nbt.getInt("SpawnerPosY" + i), nbt.getInt("SpawnerPosZ" + i)), nbt.getInt("SpawnerEntityId" + i));
            }
        }

        if (nbt.getInt("ReplacePosSize") > 0) {
            this.replacePosBlockIdMap.clear();
            for (int i = 0; i < nbt.getInt("ReplacePosSize"); i++) {
                this.replacePosBlockIdMap.put(new BlockPos(nbt.getInt("ReplacePosX" + i), nbt.getInt("ReplacePosY" + i), nbt.getInt("ReplacePosZ" + i)), nbt.getInt("ReplaceBlockId" + i));
            }
        }

        if (nbt.getInt("DungeonEdgeSize") > 0) {
            this.dungeonEdgeList.clear();
            for (int i = 0; i < nbt.getInt("DungeonEdgeSize") / 3; i++) {
                this.dungeonEdgeList.add(nbt.getInt("DungeonEdgeX" + i));
                this.dungeonEdgeList.add(nbt.getInt("DungeonEdgeY" + i));
                this.dungeonEdgeList.add(nbt.getInt("DungeonEdgeZ" + i));
            }
        }

        if (nbt.getInt("GateListSize") > 0) {
            this.gatePosList.clear();
            for (int i = 0; i < nbt.getInt("GateListSize"); i++) {
                this.gatePosList.add(new BlockPos(nbt.getInt("GatePosX" + i), nbt.getInt("GatePosY" + i), nbt.getInt("GatePosZ" + i)));
            }
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putString("DungeonType", this.dungeonType);
        nbt.putString("Difficulty", this.difficulty);
        nbt.putBoolean("DungeonStructureGenerated", this.dungeonStructureGenerated);
        nbt.putInt("DungeonPlayerCount", this.dungeonPlayerUuids.size());
        for (int i = 0; i < this.dungeonPlayerUuids.size(); i++) {
            nbt.putUuid("PlayerUUID" + i, this.dungeonPlayerUuids.get(i));
        }
        nbt.putInt("DeadDungeonPlayerCount", this.deadDungeonPlayerUuids.size());
        for (int i = 0; i < this.deadDungeonPlayerUuids.size(); i++) {
            nbt.putUuid("DeadPlayerUUID" + i, this.deadDungeonPlayerUuids.get(i));
        }
        nbt.putInt("MaxGroupSize", this.maxGroupSize);
        nbt.putInt("MinGroupSize", this.minGroupSize);
        nbt.putInt("CooldownTime", this.cooldownTime);
        nbt.putInt("AutoKickTime", this.autoKickTime);
        nbt.putBoolean("DisableEffects", this.disableEffects);
        nbt.putBoolean("PrivateGroup", this.privateGroup);

        nbt.putInt("BlockMapSize", this.blockBlockPosMap.size());
        if (this.blockBlockPosMap.size() > 0) {
            int blockCount = 0;
            Iterator<Entry<Integer, ArrayList<BlockPos>>> iterator = this.blockBlockPosMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<Integer, ArrayList<BlockPos>> entry = iterator.next();
                nbt.putInt("BlockId" + blockCount, entry.getKey());
                nbt.putInt("BlockListSize" + blockCount, entry.getValue().size());
                for (int i = 0; i < entry.getValue().size(); i++) {
                    nbt.putInt("BlockPosX" + blockCount + "" + i, entry.getValue().get(i).getX());
                    nbt.putInt("BlockPosY" + blockCount + "" + i, entry.getValue().get(i).getY());
                    nbt.putInt("BlockPosZ" + blockCount + "" + i, entry.getValue().get(i).getZ());
                }
                blockCount++;
            }
        }

        nbt.putInt("BossPosX", this.bossBlockPos.getX());
        nbt.putInt("BossPosY", this.bossBlockPos.getY());
        nbt.putInt("BossPosZ", this.bossBlockPos.getZ());

        nbt.putInt("BossLootPosX", this.bossLootBlockPos.getX());
        nbt.putInt("BossLootPosY", this.bossLootBlockPos.getY());
        nbt.putInt("BossLootPosZ", this.bossLootBlockPos.getZ());

        nbt.putInt("ChestListSize", this.chestPosList.size());
        if (this.chestPosList.size() > 0) {
            for (int i = 0; i < this.chestPosList.size(); i++) {
                nbt.putInt("ChestPosX" + i, this.chestPosList.get(i).getX());
                nbt.putInt("ChestPosY" + i, this.chestPosList.get(i).getY());
                nbt.putInt("ChestPosZ" + i, this.chestPosList.get(i).getZ());
            }
        }

        nbt.putInt("ExitListSize", this.exitPosList.size());
        if (this.exitPosList.size() > 0) {
            for (int i = 0; i < this.exitPosList.size(); i++) {
                nbt.putInt("ExitPosX" + i, this.exitPosList.get(i).getX());
                nbt.putInt("ExitPosY" + i, this.exitPosList.get(i).getY());
                nbt.putInt("ExitPosZ" + i, this.exitPosList.get(i).getZ());
            }
        }

        nbt.putInt("SpawnerMapSize", this.spawnerPosEntityIdMap.size());
        if (this.spawnerPosEntityIdMap.size() > 0) {
            Iterator<Entry<BlockPos, Integer>> iterator = this.spawnerPosEntityIdMap.entrySet().iterator();
            int count = 0;
            while (iterator.hasNext()) {
                Entry<BlockPos, Integer> entry = iterator.next();
                nbt.putInt("SpawnerPosX" + count, entry.getKey().getX());
                nbt.putInt("SpawnerPosY" + count, entry.getKey().getY());
                nbt.putInt("SpawnerPosZ" + count, entry.getKey().getZ());
                nbt.putInt("SpawnerEntityId" + count, entry.getValue());
                count++;
            }
        }

        nbt.putInt("ReplacePosSize", this.replacePosBlockIdMap.size());
        if (this.replacePosBlockIdMap.size() > 0) {
            Iterator<Entry<BlockPos, Integer>> iterator = this.replacePosBlockIdMap.entrySet().iterator();
            int count = 0;
            while (iterator.hasNext()) {
                Entry<BlockPos, Integer> entry = iterator.next();
                nbt.putInt("ReplacePosX" + count, entry.getKey().getX());
                nbt.putInt("ReplacePosY" + count, entry.getKey().getY());
                nbt.putInt("ReplacePosZ" + count, entry.getKey().getZ());
                nbt.putInt("ReplaceBlockId" + count, entry.getValue());
                count++;
            }
        }

        nbt.putInt("DungeonEdgeSize", this.dungeonEdgeList.size());
        if (this.dungeonEdgeList.size() > 0) {
            for (int i = 0; i < this.dungeonEdgeList.size() / 3; i++) {
                nbt.putInt("DungeonEdgeX" + i, this.dungeonEdgeList.get(3 * i));
                nbt.putInt("DungeonEdgeY" + i, this.dungeonEdgeList.get(1 + 3 * i));
                nbt.putInt("DungeonEdgeZ" + i, this.dungeonEdgeList.get(2 + 3 * i));
            }
        }

        nbt.putInt("GateListSize", this.gatePosList.size());
        if (this.gatePosList.size() > 0) {
            for (int i = 0; i < this.gatePosList.size(); i++) {
                nbt.putInt("GatePosX" + i, this.gatePosList.get(i).getX());
                nbt.putInt("GatePosY" + i, this.gatePosList.get(i).getY());
                nbt.putInt("GatePosZ" + i, this.gatePosList.get(i).getZ());
            }
        }
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, DungeonPortalEntity blockEntity) {
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, DungeonPortalEntity blockEntity) {
        if (blockEntity.getDungeonPlayerCount() > 0) {
            if (blockEntity.autoKickTime == 0) {
                blockEntity.autoKickTime = (int) world.getTime() + 432000;
            } else if (blockEntity.autoKickTime < (int) world.getTime()) {
                if (blockEntity.getDungeon() != null) {
                    blockEntity.setCooldownTime(blockEntity.getDungeon().getCooldown() + (int) blockEntity.getWorld().getTime());
                    for (int i = 0; i < blockEntity.getDungeonPlayerUuids().size(); i++) {
                        ServerPlayerEntity player = (ServerPlayerEntity) world.getPlayerByUuid(blockEntity.getDungeonPlayerUuids().get(i));
                        if (DungeonHelper.getCurrentDungeon(player) != null) {
                            DungeonHelper.teleportOutOfDungeon(player);
                            player.sendMessage(Text.translatable("text.dungeonz.dungeon_autokick"));
                        }
                    }
                }
                blockEntity.getDungeonPlayerUuids().clear();
                blockEntity.getDeadDungeonPlayerUUIDs().clear();
                blockEntity.autoKickTime = 0;
            }
        } else if (blockEntity.autoKickTime != 0) {
            blockEntity.autoKickTime = 0;
        }
        if (blockEntity.dungeonTeleportCountdown >= 1) {
            if (blockEntity.dungeonTeleportCountdown % 20 == 0) {
                for (int i = 0; i < blockEntity.getWaitingUuids().size(); i++) {
                    if (((ServerWorld) blockEntity.getWorld()).getEntity(blockEntity.getWaitingUuids().get(i)) != null
                            && ((ServerWorld) blockEntity.getWorld()).getEntity(blockEntity.getWaitingUuids().get(i)) instanceof ServerPlayerEntity serverPlayerEntity) {
                        DungeonServerPacket.writeS2CDungeonTeleportCountdown(serverPlayerEntity, blockEntity.dungeonTeleportCountdown);
                    }
                }

            }
            blockEntity.dungeonTeleportCountdown--;

            if (blockEntity.dungeonTeleportCountdown == (ConfigInit.CONFIG.defaultDungeonTeleportCountdown / 2)) {
                DungeonPlacementHandler.refreshDungeon(((ServerWorld) blockEntity.getWorld()).getServer(), blockEntity.getWorld().getServer().getWorld(DimensionInit.DUNGEON_WORLD), blockEntity,
                        blockEntity.getDungeon(), blockEntity.getDifficulty(), blockEntity.getDisableEffects());
            }

            if (blockEntity.dungeonTeleportCountdown == 0) {
                for (int i = 0; i < blockEntity.getWaitingUuids().size(); i++) {
                    if (((ServerWorld) blockEntity.getWorld()).getEntity(blockEntity.getWaitingUuids().get(i)) != null
                            && ((ServerWorld) blockEntity.getWorld()).getEntity(blockEntity.getWaitingUuids().get(i)) instanceof ServerPlayerEntity serverPlayerEntity) {
                        DungeonHelper.teleportPlayer(serverPlayerEntity, blockEntity.getWorld().getServer().getWorld(DimensionInit.DUNGEON_WORLD), blockEntity, blockEntity.getPos());
                    }
                }
                blockEntity.getWaitingUuids().clear();
            }
        }
    }

    @Override
    public Text getDisplayName() {
        if (this.getDungeon() != null) {
            return Text.translatable("dungeon." + this.getDungeonType());
        }
        return title;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(WrapperLookup registryLookup) {
        return this.createNbt(registryLookup);
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
        return new DungeonPortalScreenHandler(syncId, playerInventory, this, ScreenHandlerContext.create(world, pos));
    }

    @Override
    public boolean shouldDrawSide(Direction direction) {
        return true;
    }

    @Override
    public DungeonPortalPacket getScreenOpeningData(ServerPlayerEntity player) {
        List<String> difficulties = new ArrayList<String>();
        Map<String, List<ItemStack>> possibleLoot = new HashMap<>();
        List<ItemStack> requiredItemStacks = new ArrayList<ItemStack>();

        if (this.getDungeon() != null) {
            difficulties = this.getDungeon().getDifficultyList();
            possibleLoot = DungeonHelper.getPossibleLootItemStackMap(this.getDungeon(), player.getServer());
            requiredItemStacks = DungeonHelper.getRequiredItemStackList(this.getDungeon());
        }

        return new DungeonPortalPacket(this.pos, this.getDungeonPlayerUuids(), this.getDeadDungeonPlayerUUIDs(), difficulties, possibleLoot, requiredItemStacks, this.getMaxGroupSize(),
                this.getMinGroupSize(), this.getWaitingUuids().size(), this.getCooldownTime(), this.getDifficulty(), this.getDisableEffects(), this.getPrivateGroup());
    }

    public void finishDungeon(ServerWorld world, BlockPos pos) {
        List<PlayerEntity> players = world.getPlayers(TargetPredicate.createAttackable().setBaseMaxDistance(64.0), null, new Box(pos).expand(64.0, 64.0, 64.0));
        for (int i = 0; i < players.size(); i++) {
            CriteriaInit.DUNGEON_COMPLETION.trigger((ServerPlayerEntity) players.get(i), this.getDungeonType(), this.getDifficulty());
        }
        world.playSound(null, pos, SoundInit.DUNGEON_COMPLETION_EVENT, SoundCategory.BLOCKS, 1.0f, 0.9f + world.getRandom().nextFloat() * 0.2f);

        for (int i = 0; i < this.getExitPosList().size(); i++) {
            world.setBlockState(this.getExitPosList().get(i), BlockInit.DUNGEON_PORTAL.getDefaultState(), 3);
        }

        world.setBlockState(this.getBossLootBlockPos(), Blocks.CHEST.getDefaultState(), 3);
        InventoryHelper.fillInventoryWithLoot(world.getServer(), world, this.getBossLootBlockPos(), this.getDungeon().getDifficultyBossLootTableMap().get(this.getDifficulty()),
                this.getDisableEffects());

        this.setCooldownTime(this.getDungeon().getCooldown() + (int) this.getWorld().getTime());
        markDirty();
    }

    @Nullable
    public Dungeon getDungeon() {
        return Dungeon.getDungeon(this.dungeonType);
    }

    public void setDungeonType(String dungeonType) {
        this.dungeonType = dungeonType;
    }

    public String getDungeonType() {
        return this.dungeonType;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getDifficulty() {
        return this.difficulty;
    }

    public void setDungeonStructureGenerated() {
        this.dungeonStructureGenerated = true;
    }

    public boolean isDungeonStructureGenerated() {
        return this.dungeonStructureGenerated;
    }

    public void joinDungeon(UUID playerUuid) {
        if (!this.dungeonPlayerUuids.contains(playerUuid)) {
            this.dungeonPlayerUuids.add(playerUuid);
        }
    }

    public void leaveDungeon(UUID playerUuid) {
        this.dungeonPlayerUuids.remove(playerUuid);
    }

    public int getDungeonPlayerCount() {
        return this.dungeonPlayerUuids.size();
    }

    public void setDungeonPlayerUuids(List<UUID> dungeonPlayerUuids) {
        this.dungeonPlayerUuids = dungeonPlayerUuids;
    }

    public List<UUID> getDungeonPlayerUuids() {
        return this.dungeonPlayerUuids;
    }

    public void addDeadDungeonPlayerUuids(UUID deadDungeonPlayerUuids) {
        this.deadDungeonPlayerUuids.add(deadDungeonPlayerUuids);
    }

    public void setDeadDungeonPlayerUuids(List<UUID> deadDungeonPlayerUuids) {
        this.deadDungeonPlayerUuids = deadDungeonPlayerUuids;
    }

    public List<UUID> getDeadDungeonPlayerUUIDs() {
        return this.deadDungeonPlayerUuids;
    }

    // Might lead to issues if using "="
    public void setBlockMap(HashMap<Integer, ArrayList<BlockPos>> map) {
        this.blockBlockPosMap = map;
    }

    public HashMap<Integer, ArrayList<BlockPos>> getBlockMap() {
        return this.blockBlockPosMap;
    }

    public void setCooldownTime(int cooldownTime) {
        this.cooldownTime = cooldownTime;
    }

    public int getCooldownTime() {
        return this.cooldownTime;
    }

    public boolean isOnCooldown(int currentTime) {
        if (this.cooldownTime <= currentTime) {
            return false;
        }
        return true;
    }

    public void setMaxGroupSize(int maxGroupSize) {
        this.maxGroupSize = maxGroupSize;
    }

    public void setMinGroupSize(int minGroupSize) {
        this.minGroupSize = minGroupSize;
    }

    public List<UUID> getWaitingUuids() {
        return this.waitingUuids;
    }

    public void addWaitingUuid(UUID uuid) {
        if (!this.waitingUuids.contains(uuid)) {
            this.waitingUuids.add(uuid);
        }
    }

    public int getMaxGroupSize() {
        return this.maxGroupSize;
    }

    public int getMinGroupSize() {
        return this.minGroupSize;
    }

    public void setDisableEffects(boolean disableEffects) {
        this.disableEffects = disableEffects;
    }

    public boolean getDisableEffects() {
        return this.disableEffects;
    }

    public void setPrivateGroup(boolean privateGroup) {
        this.privateGroup = privateGroup;
    }

    public boolean getPrivateGroup() {
        return this.privateGroup;
    }

    public void setBossBlockPos(BlockPos pos) {
        this.bossBlockPos = pos;
    }

    public BlockPos getBossBlockPos() {
        return this.bossBlockPos;
    }

    public void setBossLootBlockPos(BlockPos pos) {
        this.bossLootBlockPos = pos;
    }

    public BlockPos getBossLootBlockPos() {
        return this.bossLootBlockPos;
    }

    public void setChestPosList(List<BlockPos> chestPosList) {
        this.chestPosList = chestPosList;
    }

    public List<BlockPos> getChestPosList() {
        return this.chestPosList;
    }

    public void setGatePosList(List<BlockPos> gatePosList) {
        this.gatePosList = gatePosList;
    }

    public List<BlockPos> getGatePosList() {
        return this.gatePosList;
    }

    public void setExitPosList(List<BlockPos> exitPosList) {
        this.exitPosList = exitPosList;
    }

    public List<BlockPos> getExitPosList() {
        return this.exitPosList;
    }

    public void addDungeonEdge(int edgeX, int edgeY, int edgeZ) {
        this.dungeonEdgeList.add(edgeX);
        this.dungeonEdgeList.add(edgeY);
        this.dungeonEdgeList.add(edgeZ);
    }

    public List<Integer> getDungeonEdgeList() {
        return this.dungeonEdgeList;
    }

    public void setSpawnerPosEntityIdMap(HashMap<BlockPos, Integer> spawnerPosEntityIdMap) {
        this.spawnerPosEntityIdMap = spawnerPosEntityIdMap;
    }

    public HashMap<BlockPos, Integer> getSpawnerPosEntityIdMap() {
        return this.spawnerPosEntityIdMap;
    }

    public void setReplaceBlockIdMap(HashMap<BlockPos, Integer> replacePosBlockIdMap) {
        this.replacePosBlockIdMap = replacePosBlockIdMap;
    }

    public void addReplaceBlockId(BlockPos pos, Block block) {
        this.replacePosBlockIdMap.put(pos, Registries.BLOCK.getRawId(block));
    }

    public HashMap<BlockPos, Integer> getReplaceBlockIdMap() {
        return this.replacePosBlockIdMap;
    }

    public void startDungeonTeleportCountdown(ServerWorld dungeonWorld) {
        this.dungeonTeleportCountdown = ConfigInit.CONFIG.defaultDungeonTeleportCountdown;

        boolean isDungeonStructureGenerated = this.isDungeonStructureGenerated();
        if (!isDungeonStructureGenerated) {
            this.setDungeonStructureGenerated();
            DungeonPlacementHandler.generateDungeonStructure(dungeonWorld, new BlockPos(0, 0, 0).add(this.getPos().getX() * 16, 100, this.getPos().getZ() * 16), this);
        } else {
            DungeonPlacementHandler.prepareDungeon(dungeonWorld, this);
        }
        this.markDirty();
    }

    public int getdungeonTeleportCountdown() {
        return this.dungeonTeleportCountdown;
    }

}
