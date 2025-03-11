package net.dungeonz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.At;

import net.dungeonz.block.entity.DungeonPortalEntity;
import net.dungeonz.dungeon.Dungeon;
import net.dungeonz.init.DimensionInit;
import net.dungeonz.network.DungeonServerPacket;
import net.dungeonz.util.DungeonHelper;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void onPlayerConnectMixin(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo info) {
        if (player.getWorld().getRegistryKey() == DimensionInit.DUNGEON_WORLD) {
            if (DungeonHelper.getCurrentDungeon(player) != null && DungeonHelper.getDungeonPortalEntity(player).getDungeonPlayerUuids().contains(player.getUuid())
                    && !DungeonHelper.getDungeonPortalEntity(player).isOnCooldown((int) player.getWorld().getTime())) {
                Dungeon dungeon = DungeonHelper.getCurrentDungeon(player);
                DungeonServerPacket.writeS2CDungeonInfoPacket(player, dungeon.getBreakableBlockIdList(), dungeon.getplaceableBlockIdList(), dungeon.isElytraAllowed());
            } else {
                DungeonHelper.teleportOutOfDungeon(player);
            }
        }
    }

    @Inject(method = "respawnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;onPlayerRespawned(Lnet/minecraft/server/network/ServerPlayerEntity;)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void respawnPlayerMixin(ServerPlayerEntity oldPlayer, boolean alive, Entity.RemovalReason removalReason, CallbackInfoReturnable<ServerPlayerEntity> info, TeleportTarget teleportTarget,
            ServerWorld serverWorld, ServerPlayerEntity serverPlayerEntity) {
        if (!alive && oldPlayer.getWorld().getRegistryKey() == DimensionInit.DUNGEON_WORLD && DungeonHelper.getDungeonPortalEntity(oldPlayer) != null) {
            DungeonPortalEntity dungeonPortalEntity = DungeonHelper.getDungeonPortalEntity(oldPlayer);
            if (!dungeonPortalEntity.getDungeon().isRespawnAllowed()) {
                dungeonPortalEntity.getDungeonPlayerUuids().remove(oldPlayer.getUuid());
                dungeonPortalEntity.addDeadDungeonPlayerUuids(serverPlayerEntity.getUuid());
                if (dungeonPortalEntity.getDungeonPlayerCount() == 0) {
                    dungeonPortalEntity.setCooldownTime(dungeonPortalEntity.getDungeon().getCooldown() + (int) serverWorld.getTime());
                }
                dungeonPortalEntity.markDirty();
            }
        }
    }

    @ModifyVariable(method = "respawnPlayer", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/server/network/ServerPlayerEntity;getRespawnTarget(ZLnet/minecraft/world/TeleportTarget$PostDimensionTransition;)Lnet/minecraft/world/TeleportTarget;", ordinal = 0), ordinal = 0)
    private TeleportTarget respawnPlayerMixin(TeleportTarget original, ServerPlayerEntity oldPlayer, boolean alive, Entity.RemovalReason removalReason) {
        if (!alive && oldPlayer.getWorld().getRegistryKey() == DimensionInit.DUNGEON_WORLD && DungeonHelper.getDungeonPortalEntity(oldPlayer) != null
                && DungeonHelper.getDungeonPortalEntity(oldPlayer).getDungeon().isRespawnAllowed()) {
            return new TeleportTarget(oldPlayer.getServerWorld(), Vec3d.of(DungeonHelper.getDungeonPortalEntity(oldPlayer).getDungeonStartModificationPos()), Vec3d.ZERO, oldPlayer.getYaw(), 0.0f, alive, TeleportTarget.NO_OP);
        }
        return original;
    }

}
