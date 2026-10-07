package com.synarsis.airtacticalarsenal.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OrlanEntity extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // Полный набор фаз полёта БПЛА
    public enum FlightPhase {
        LIFTING_OFF,
        LAUNCHING,
        CLIMBING,
        TAKEOFF,
        CRUISING,
        PATROLLING,
        RETURNING,
        LANDING,
        LANDED
    }

    // Синхронизируемые параметры (DataWatchers)
    public static final EntityDataAccessor<Integer> FLIGHT_PHASE =
            SynchedEntityData.defineId(OrlanEntity.class, EntityDataSerializers.INT);

    public static final EntityDataAccessor<Optional<BlockPos>> LAUNCHER_POS =
            SynchedEntityData.defineId(OrlanEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    public static final EntityDataAccessor<Optional<BlockPos>> TARGET_POS =
            SynchedEntityData.defineId(OrlanEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    public static final EntityDataAccessor<Float> TARGET_ALTITUDE =
            SynchedEntityData.defineId(OrlanEntity.class, EntityDataSerializers.FLOAT);

    // Внутренние поля состояния
    public boolean isLegitSpawn = false;
    private String ownerUUID = "";
    private String linkedPlayerUUID = "";
    private int remainingFlightTicks = 12000; // ~10 минут
    private float renderRoll = 0.0f;
    private boolean isFalling = false;
    private final List<BlockPos> waypoints = new ArrayList<>();

    // Стандартный конструктор Forge / Minecraft
    public OrlanEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    // Дополнительный конструктор для спавна из планшета (LaunchOrlanFromTabletPacket)
    public OrlanEntity(Level level, Vec3 launchPos, BlockPos targetPos, float launcherYaw, BlockPos launcherPos) {
        this(ModEntities.ORLAN.get(), level);
        this.setPos(launchPos.x, launchPos.y, launchPos.z);
        this.setYRot(launcherYaw);
        this.yRotO = launcherYaw;
        this.setLauncherPos(launcherPos);
        this.setTargetPos(targetPos);
        this.setTargetAltitude(launchPos.y + 50.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(FLIGHT_PHASE, FlightPhase.CRUISING.ordinal());
        this.entityData.define(LAUNCHER_POS, Optional.empty());
        this.entityData.define(TARGET_POS, Optional.empty());
        this.entityData.define(TARGET_ALTITUDE, 100.0f);
    }

    // --- GeckoLib ---
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // --- Владелец и Привязки ---
    public String getOwnerUUID() {
        return this.ownerUUID;
    }

    public void setOwnerUUID(String uuidStr) {
        this.ownerUUID = uuidStr != null ? uuidStr : "";
    }

    public ServerPlayer getOwner() {
        if (this.ownerUUID.isEmpty() || !(this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        try {
            return serverLevel.getServer().getPlayerList().getPlayer(UUID.fromString(this.ownerUUID));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setOwner(ServerPlayer player) {
        if (player != null) {
            this.ownerUUID = player.getUUID().toString();
        }
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return player != null && !this.ownerUUID.isEmpty() && this.ownerUUID.equals(player.getUUID().toString());
    }

    public void setLinkedPlayer(String uuidStr) {
        this.linkedPlayerUUID = uuidStr != null ? uuidStr : "";
    }

    public boolean isLinkedToPlayer(UUID playerUUID) {
        if (playerUUID == null || this.linkedPlayerUUID.isEmpty()) return false;
        return this.linkedPlayerUUID.equals(playerUUID.toString());
    }

    // --- Позиционирование и Высота ---
    public BlockPos getLauncherPos() {
        return this.entityData.get(LAUNCHER_POS).orElse(this.blockPosition());
    }

    public void setLauncherPos(BlockPos pos) {
        this.entityData.set(LAUNCHER_POS, Optional.ofNullable(pos));
    }

    public BlockPos getTargetPos() {
        return this.entityData.get(TARGET_POS).orElse(this.blockPosition());
    }

    public void setTargetPos(BlockPos pos) {
        this.entityData.set(TARGET_POS, Optional.ofNullable(pos));
    }

    public double getTargetAltitude() {
        return this.entityData.get(TARGET_ALTITUDE);
    }

    public void setTargetAltitude(double alt) {
        this.entityData.set(TARGET_ALTITUDE, (float) alt);
    }

    // --- Управление Фазами Полёта ---
    public int getFlightPhase() {
        return this.entityData.get(FLIGHT_PHASE);
    }

    public FlightPhase getFlightPhaseEnum() {
        int ordinal = this.entityData.get(FLIGHT_PHASE);
        FlightPhase[] phases = FlightPhase.values();
        if (ordinal >= 0 && ordinal < phases.length) {
            return phases[ordinal];
        }
        return FlightPhase.CRUISING;
    }

    public void setFlightPhase(FlightPhase phase) {
        this.entityData.set(FLIGHT_PHASE, phase.ordinal());
    }

    public void forceReturn() {
        this.setFlightPhase(FlightPhase.RETURNING);
        this.setTargetPos(this.getLauncherPos());
    }

    public void updateRouteInFlight(BlockPos newTarget, List<BlockPos> waypoints) {
        this.setTargetPos(newTarget);
        this.setWaypoints(waypoints);
    }

    public void setWaypoints(List<BlockPos> points) {
        this.waypoints.clear();
        if (points != null) {
            this.waypoints.addAll(points);
        }
    }

    public List<BlockPos> getWaypoints() {
        return this.waypoints;
    }

    // --- Параметры Рендера, HUD и Камеры ---
    public float getHealthPercent() {
        return this.getHealth() / this.getMaxHealth();
    }

    public int getRemainingFlightTicks() {
        return this.remainingFlightTicks;
    }

    public boolean getIsFalling() {
        return this.isFalling;
    }

    public float getRenderRoll(float partialTick) {
        return this.renderRoll;
    }

    public Vec3 getCameraPhysicsPos() {
        return this.position();
    }

    public float getCameraPlayerFallDistance() {
        return this.fallDistance;
    }

    public static List<OrlanEntity> getActiveOrlansByOwner(UUID ownerUUID) {
        return new ArrayList<>();
    }

    // --- NBT Сохранение/Загрузка ---
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.entityData.get(LAUNCHER_POS).isPresent()) {
            compound.put("LauncherPos", NbtUtils.writeBlockPos(this.entityData.get(LAUNCHER_POS).get()));
        }
        if (this.entityData.get(TARGET_POS).isPresent()) {
            compound.put("TargetPos", NbtUtils.writeBlockPos(this.entityData.get(TARGET_POS).get()));
        }
        compound.putString("OwnerUUID", this.ownerUUID);
        compound.putString("LinkedPlayerUUID", this.linkedPlayerUUID);
        compound.putInt("RemainingTicks", this.remainingFlightTicks);
        compound.putBoolean("IsLegitSpawn", this.isLegitSpawn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("LauncherPos")) {
            this.setLauncherPos(NbtUtils.readBlockPos(compound.getCompound("LauncherPos")));
        }
        if (compound.contains("TargetPos")) {
            this.setTargetPos(NbtUtils.readBlockPos(compound.getCompound("TargetPos")));
        }
        if (compound.contains("OwnerUUID")) {
            this.ownerUUID = compound.getString("OwnerUUID");
        }
        if (compound.contains("LinkedPlayerUUID")) {
            this.linkedPlayerUUID = compound.getString("LinkedPlayerUUID");
        }
        if (compound.contains("RemainingTicks")) {
            this.remainingFlightTicks = compound.getInt("RemainingTicks");
        }
        this.isLegitSpawn = compound.getBoolean("IsLegitSpawn");
    }
}