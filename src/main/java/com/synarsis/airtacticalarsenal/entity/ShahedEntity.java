package com.synarsis.airtacticalarsenal.entity;

import com.synarsis.airtacticalarsenal.chunk.MissileChunkManager;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.ShahedExplosionPacket;
import com.synarsis.airtacticalarsenal.network.ShahedSpawnPacket;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

public class ShahedEntity extends Projectile implements GeoEntity {

    public enum FlightPhase {
        WARMUP,
        LAUNCHING,
        CLIMBING,
        CRUISING,
        DIVING
    }

    private static final double LAUNCH_INITIAL_SPEED = 0.3;
    private static final double LAUNCH_ACCELERATION = 0.02;
    private static final double LAUNCH_MAX_SPEED = 1.2;
    private static final double LAUNCH_ANGLE_DEG = 20.0;
    private static final double CLIMB_ACCELERATION = 0.03;
    private static final double CLIMB_ANGLE_DEG = 25.0;
    private static final double CRUISE_ALTITUDE_MIN = 160.0;
    private static final double CRUISE_ALTITUDE_MAX = 170.0;
    private static final double CLIMB_DISTANCE = 300.0;
    private static final double ALTITUDE_TOLERANCE = 5.0;
    private static final double HEIGHT_CORRECTION_FACTOR = 0.15;
    private static final double DIVE_ACCELERATION = 0.08;

    private static final EntityDataAccessor<BlockPos> TARGET_POS = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> IS_DIVING = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DIVE_MODE = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> FLIGHT_PHASE_ID = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SYNC_X_ROT = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNC_Y_ROT = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_DAMAGED = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CRITICALLY_DAMAGED = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> SHAHED_COLOR = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_ON_PAINT_STAND = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_STAND_YAW = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_CRUISE_ALT = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_DESTROYED = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.BOOLEAN);

    public boolean isLegitSpawn = false;
    public static final double CRUISE_SPEED = 1.5;
    public static final double DIVE_SPEED = 3.6;

    public static double getCruiseSpeed() { return ShahedConfig.getShahedCruiseSpeed(); }
    public static double getDiveSpeed() { return ShahedConfig.getShahedDiveSpeed(); }

    private FlightPhase flightPhase = FlightPhase.WARMUP;
    private int phaseTicks = 0;
    private double currentSpeed = 0;
    private Vec3 launchStartPos = Vec3.ZERO;
    private Vec3 launchDirection = Vec3.ZERO;
    private double targetAltitude = 165.0;
    private static final float EXPLOSION_RADIUS = 25.0f;
    private boolean isDiving = false;
    private float diveProgress = 0.0f;
    private double diveDistance = 300.0;
    private double diveTransitionDistance = 350.0;
    private static final double TURN_RATE_DEG = 3.0;
    private Vec3 lastDirection = new Vec3(0, -1, 0);
    private List<BlockPos> waypoints = new ArrayList<>();
    private int currentWaypointIndex = 0;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int syncTickCounter = 0;
    private static final int SYNC_INTERVAL = 40;
    private boolean initialPacketSent = false;
    private boolean exploding = false;
    private float maxHealth = 100.0f;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot, lerpXRot;
    private int lerpSteps = 0;
    private int destroyAnimTicks = 0;

    public ShahedEntity(EntityType<? extends ShahedEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public ShahedEntity(Level level, Vec3 startPos, BlockPos targetPos, String diveMode) {
        this(level, startPos, targetPos, diveMode, Float.NaN);
    }

    public ShahedEntity(Level level, Vec3 startPos, BlockPos targetPos, String diveMode, float launcherYaw) {
        this(ModEntities.SHAHED.get(), level);
        this.entityData.set(TARGET_POS, targetPos);
        this.entityData.set(DIVE_MODE, diveMode);
        this.diveDistance = 300.0;
        this.diveTransitionDistance = 350.0;

        this.flightPhase = FlightPhase.LAUNCHING;
        this.phaseTicks = 0;
        this.currentSpeed = LAUNCH_INITIAL_SPEED;
        this.launchStartPos = startPos;
        this.targetAltitude = CRUISE_ALTITUDE_MIN + level.random.nextDouble() * (CRUISE_ALTITUDE_MAX - CRUISE_ALTITUDE_MIN);
        this.entityData.set(TARGET_CRUISE_ALT, (float) this.targetAltitude);

        if (!Float.isNaN(launcherYaw)) {
            Vec3 dir = Vec3.directionFromRotation(0, launcherYaw).normalize();
            this.launchDirection = new Vec3(dir.x, 0, dir.z).normalize();
            this.lastDirection = this.launchDirection;
            this.setYRot(launcherYaw);

            Vec3 spawnOffset = startPos.add(this.launchDirection.scale(1.2)).add(0, 0.4, 0);
            this.setPos(spawnOffset);
            this.launchStartPos = spawnOffset;
        } else {
            Vec3 targetVec = Vec3.atCenterOf(targetPos);
            Vec3 horizontalDir = new Vec3(targetVec.x - startPos.x, 0, targetVec.z - startPos.z);
            if (horizontalDir.lengthSqr() > 1.0E-4) {
                this.launchDirection = horizontalDir.normalize();
                this.lastDirection = horizontalDir.normalize();
            } else {
                this.launchDirection = new Vec3(0, 0, 1);
                this.lastDirection = new Vec3(0, 0, 1);
            }
            this.setPos(startPos);
        }
        this.setXRot((float) -LAUNCH_ANGLE_DEG);
        this.entityData.set(FLIGHT_PHASE_ID, this.flightPhase.ordinal());
        this.entityData.set(SYNC_X_ROT, (float) -LAUNCH_ANGLE_DEG);
        this.entityData.set(SYNC_Y_ROT, this.getYRot());
    }

    public boolean getIsDiving() {
        return this.entityData.get(IS_DIVING) || this.flightPhase == FlightPhase.DIVING;
    }

    public void setWaypoints(List<BlockPos> waypoints) {
        this.waypoints = new ArrayList<>(waypoints);
        this.currentWaypointIndex = 0;
    }

    public double getTargetAltitude() {
        return this.entityData.get(TARGET_CRUISE_ALT);
    }

    public void setTargetAltitude(double altitude) {
        this.targetAltitude = altitude;
        this.entityData.set(TARGET_CRUISE_ALT, (float) altitude);
    }

    public void mountPaintStand(PaintStandEntity stand) {
        if (stand != null) {
            this.entityData.set(DATA_ON_PAINT_STAND, true);
            this.entityData.set(DATA_STAND_YAW, stand.getYRot());
            this.setPos(stand.getX(), stand.getY() + 0.5, stand.getZ());
            this.setYRot(stand.getYRot());
        } else {
            this.entityData.set(DATA_ON_PAINT_STAND, false);
        }
    }

    @Override public boolean isPickable() { return !this.isRemoved(); }
    @Override public boolean isAttackable() { return true; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (!this.level().isClientSide && !this.entityData.get(IS_DESTROYED)) {
            float currentHealth = this.entityData.get(HEALTH) - amount;
            this.entityData.set(HEALTH, Math.max(0.0f, currentHealth));
            if (currentHealth <= 0.0f) {
                this.entityData.set(IS_DESTROYED, true);
            }
            return true;
        }
        return false;
    }

    public boolean isOnPaintStand() { return this.entityData.get(DATA_ON_PAINT_STAND); }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 490000.0; }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        this.lerpX = x; this.lerpY = y; this.lerpZ = z;
        this.lerpYRot = yRot; this.lerpXRot = xRot; this.lerpSteps = steps + 1;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TARGET_POS, BlockPos.ZERO);
        this.entityData.define(IS_DIVING, false);
        this.entityData.define(DIVE_MODE, "low");
        this.entityData.define(FLIGHT_PHASE_ID, 0);
        this.entityData.define(SYNC_X_ROT, 0.0f);
        this.entityData.define(SYNC_Y_ROT, 0.0f);
        this.entityData.define(HEALTH, (float) ShahedConfig.getShahedMaxHealth());
        this.entityData.define(IS_DAMAGED, false);
        this.entityData.define(IS_CRITICALLY_DAMAGED, false);
        this.entityData.define(SHAHED_COLOR, "white");
        this.entityData.define(DATA_ON_PAINT_STAND, false);
        this.entityData.define(DATA_STAND_YAW, 0.0f);
        this.entityData.define(TARGET_CRUISE_ALT, 165.0f);
        this.entityData.define(IS_DESTROYED, false);
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !this.isLegitSpawn) {
            this.discard();
            return;
        }
        super.tick();

        // Плавный переход в спиральный штопор при уничтожении
        if (this.entityData.get(IS_DESTROYED)) {
            if (!this.level().isClientSide) {
                this.destroyAnimTicks++;

                double spiralAngle = this.destroyAnimTicks * 0.08;
                double spiralRadius = 3.5;

                double vx = Math.cos(spiralAngle) * spiralRadius;
                double vz = Math.sin(spiralAngle) * spiralRadius;

                this.setDeltaMovement(new Vec3(vx, -0.3, vz));

                if (this.horizontalCollision || this.onGround() || this.level().getBlockState(this.blockPosition()).isSolid() || this.destroyAnimTicks > 200) {
                    this.explode();
                    return;
                }
            }
            Vec3 m = this.getDeltaMovement();
            this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);

            if (this.level().isClientSide) {
                float progress = Math.min(1.0f, this.destroyAnimTicks / 35.0f);
                float targetXRot = 45.0f * progress;
                float targetYRot = (this.tickCount * 5.0f) % 360.0f;

                this.setYRot(targetYRot);
                this.setXRot(targetXRot);
                this.yRotO = targetYRot;
                this.xRotO = targetXRot;
            }
            return;
        }

        if (isOnPaintStand()) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide) {
                this.entityData.set(DATA_STAND_YAW, this.getYRot());
            }
            return;
        }

        if (this.level().isClientSide) {
            if (this.lerpSteps > 0) {
                double dx = (this.lerpX - this.getX()) / this.lerpSteps;
                double dy = (this.lerpY - this.getY()) / this.lerpSteps;
                double dz = (this.lerpZ - this.getZ()) / this.lerpSteps;
                this.setPos(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
                this.lerpSteps--;
            }
            float syncedXRot = this.entityData.get(SYNC_X_ROT);
            float syncedYRot = this.entityData.get(SYNC_Y_ROT);
            this.setYRot(syncedYRot);
            this.setXRot(syncedXRot);
            this.yRotO = syncedYRot;
            this.xRotO = syncedXRot;
            return;
        }

        BlockPos target = this.entityData.get(TARGET_POS);
        Vec3 targetVec = Vec3.atCenterOf(target);
        Vec3 currentPos = this.position();
        double horizontalDist = Math.sqrt(Math.pow(targetVec.x - currentPos.x, 2.0) + Math.pow(targetVec.z - currentPos.z, 2.0));

        Vec3 motion;
        switch (this.flightPhase) {
            case WARMUP:
                transitionToPhase(FlightPhase.LAUNCHING);
                motion = tickLaunchingPhase(targetVec, currentPos);
                break;
            case LAUNCHING:
                motion = tickLaunchingPhase(targetVec, currentPos);
                break;
            case CLIMBING:
                motion = tickClimbingPhase(targetVec, currentPos);
                break;
            case CRUISING:
                motion = tickCruisingPhase(targetVec, currentPos, horizontalDist);
                break;
            case DIVING:
                Vec3 diveTarget = new Vec3(targetVec.x, findSurfaceY(target), targetVec.z);
                motion = tickDivingPhase(diveTarget, currentPos);
                break;
            default:
                motion = this.lastDirection.scale(getCruiseSpeed());
        }

        this.phaseTicks++;
        this.setDeltaMovement(motion);
        if (motion.lengthSqr() > 1.0E-4) { this.lastDirection = motion.normalize(); }

        if (motion.lengthSqr() > 1.0E-4) {
            float targetYRot = (float) (Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI));
            double horizontalDistance = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            float targetXRot = (float) (-(Mth.atan2(motion.y, horizontalDistance) * (180.0D / Math.PI)));

            float newYRot = Mth.approachDegrees(this.getYRot(), targetYRot, 20.0F);
            float newXRot = Mth.approachDegrees(this.getXRot(), targetXRot, 20.0F);

            this.setYRot(newYRot);
            this.setXRot(newXRot);
            this.yRotO = newYRot;
            this.xRotO = newXRot;

            this.entityData.set(SYNC_X_ROT, newXRot);
            this.entityData.set(SYNC_Y_ROT, newYRot);
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            MissileChunkManager.updateChunksForMissile(this, serverLevel);
        }
        if (this.flightPhase == FlightPhase.DIVING && !this.isDiving) {
            this.isDiving = true;
            this.entityData.set(IS_DIVING, true);
        }

        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
            return;
        }

        if (this.isDiving) {
            double surfaceY = findSurfaceY(target);
            if (this.getY() <= surfaceY + 2.0) { this.explode(); return; }
        }

        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);

        if (!this.initialPacketSent) {
            this.initialPacketSent = true;
            NetworkHandler.sendToAllPlayers(new ShahedSpawnPacket(this.getId(), this.position(), target, this.entityData.get(DIVE_MODE)));
        }
        this.syncTickCounter++;
        if (this.syncTickCounter >= SYNC_INTERVAL) {
            this.syncTickCounter = 0;
            NetworkHandler.sendToAllPlayers(new ShahedSpawnPacket(this.getId(), this.position(), target, this.entityData.get(DIVE_MODE)));
        }
    }

    private Vec3 tickLaunchingPhase(Vec3 targetVec, Vec3 currentPos) {
        this.currentSpeed = Math.min(this.currentSpeed + LAUNCH_ACCELERATION, LAUNCH_MAX_SPEED);
        double launchAngleRad = Math.toRadians(LAUNCH_ANGLE_DEG);
        double horizontalSpeed = this.currentSpeed * Math.cos(launchAngleRad);
        double verticalSpeed = this.currentSpeed * Math.sin(launchAngleRad);
        Vec3 motion = new Vec3(this.launchDirection.x * horizontalSpeed, verticalSpeed, this.launchDirection.z * horizontalSpeed);
        if (this.currentSpeed >= LAUNCH_MAX_SPEED * 0.8) { transitionToPhase(FlightPhase.CLIMBING); }
        return motion;
    }

    private Vec3 tickClimbingPhase(Vec3 targetVec, Vec3 currentPos) {
        this.currentSpeed = Math.min(this.currentSpeed + CLIMB_ACCELERATION, getCruiseSpeed());
        double distanceFromLaunch = Math.sqrt(Math.pow(currentPos.x - this.launchStartPos.x, 2) + Math.pow(currentPos.z - this.launchStartPos.z, 2));
        double remainingHeight = this.targetAltitude - this.getY();
        double climbAngleRad = remainingHeight > 80 ? Math.toRadians(CLIMB_ANGLE_DEG) : Math.toRadians(3.0);
        double horizontalSpeed = this.currentSpeed * Math.cos(climbAngleRad);
        double verticalSpeed = this.currentSpeed * Math.sin(climbAngleRad);
        Vec3 motion = new Vec3(this.launchDirection.x * horizontalSpeed, verticalSpeed, this.launchDirection.z * horizontalSpeed);
        if (this.getY() >= this.targetAltitude - ALTITUDE_TOLERANCE && distanceFromLaunch >= CLIMB_DISTANCE) {
            transitionToPhase(FlightPhase.CRUISING);
        }
        return motion;
    }

    private Vec3 tickCruisingPhase(Vec3 targetVec, Vec3 currentPos, double horizontalDist) {
        this.currentSpeed = getCruiseSpeed();
        Vec3 navTarget = targetVec;
        boolean onFinalLeg = true;

        Vec3 desiredDir = new Vec3(navTarget.x - currentPos.x, 0, navTarget.z - currentPos.z);
        if (desiredDir.lengthSqr() > 1.0E-4) { desiredDir = desiredDir.normalize(); }
        else { desiredDir = this.lastDirection; }

        Vec3 smoothDir = smoothTurn(this.lastDirection, desiredDir, TURN_RATE_DEG);
        double heightDiff = this.targetAltitude - this.getY();
        double verticalCorrection = Math.max(-0.8, Math.min(0.8, heightDiff * HEIGHT_CORRECTION_FACTOR));
        Vec3 motion = new Vec3(smoothDir.x * this.currentSpeed, verticalCorrection, smoothDir.z * this.currentSpeed);

        if (horizontalDist < this.diveTransitionDistance) {
            transitionToPhase(FlightPhase.DIVING);
        }
        return motion;
    }

    private Vec3 tickDivingPhase(Vec3 targetVec, Vec3 currentPos) {
        Vec3 toTarget = targetVec.subtract(currentPos);
        double distanceToTarget = toTarget.length();
        if (distanceToTarget < 5.0) { return toTarget; }
        Vec3 directionToTarget = toTarget.normalize();
        this.currentSpeed = Math.min(this.currentSpeed + DIVE_ACCELERATION, getDiveSpeed());

        Vec3 desiredHoriz = new Vec3(directionToTarget.x, 0, directionToTarget.z);
        if (desiredHoriz.lengthSqr() > 1.0E-4) {
            desiredHoriz = desiredHoriz.normalize();
            Vec3 smoothHoriz = smoothTurn(this.lastDirection, desiredHoriz, 8.0);
            double smoothVy = this.lastDirection.y + (directionToTarget.y - this.lastDirection.y) * 0.5;
            return new Vec3(smoothHoriz.x, smoothVy, smoothHoriz.z).normalize().scale(this.currentSpeed);
        }
        return directionToTarget.scale(this.currentSpeed);
    }

    private static Vec3 smoothTurn(Vec3 current, Vec3 desired, double maxDegreesPerTick) {
        double currentYaw = Math.atan2(current.x, current.z);
        double desiredYaw = Math.atan2(desired.x, desired.z);
        double diff = desiredYaw - currentYaw;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        double maxRad = Math.toRadians(maxDegreesPerTick);
        if (Math.abs(diff) > maxRad) { diff = Math.signum(diff) * maxRad; }
        double newYaw = currentYaw + diff;
        return new Vec3(Math.sin(newYaw), 0, Math.cos(newYaw)).normalize();
    }

    private void transitionToPhase(FlightPhase newPhase) {
        if (this.flightPhase != newPhase) {
            this.flightPhase = newPhase; this.phaseTicks = 0;
            if (!this.level().isClientSide) { this.entityData.set(FLIGHT_PHASE_ID, newPhase.ordinal()); }
        }
    }

    private double findSurfaceY(BlockPos target) {
        if (this.level() == null) return target.getY();
        for (int y = 320; y >= -64; y--) {
            BlockPos checkPos = new BlockPos(target.getX(), y, target.getZ());
            if (!this.level().getBlockState(checkPos).isAir()) { return y + 1; }
        }
        return target.getY();
    }

    @Override protected void onHitBlock(BlockHitResult result) { super.onHitBlock(result); if (!this.level().isClientSide) { this.explode(); } }
    @Override protected void onHitEntity(EntityHitResult result) { super.onHitEntity(result); if (!this.level().isClientSide) { this.explode(); } }

    private void explode() {
        if (this.exploding) return;
        this.exploding = true;
        if (!this.level().isClientSide) {
            Vec3 pos = this.position();
            NetworkHandler.sendToAllPlayers(new ShahedExplosionPacket(pos));
            Entity owner = this.getOwner();
            AABB explosionBox = new AABB(pos.x - EXPLOSION_RADIUS, pos.y - EXPLOSION_RADIUS, pos.z - EXPLOSION_RADIUS, pos.x + EXPLOSION_RADIUS, pos.y + EXPLOSION_RADIUS, pos.z + EXPLOSION_RADIUS);
            List<Entity> entities = this.level().getEntities(this, explosionBox);
            DamageSource damageSource = (owner instanceof LivingEntity livingOwner) ? this.damageSources().explosion(this, livingOwner) : this.damageSources().explosion(this, null);
            for (Entity entity : entities) {
                if (entity == this) continue;
                double distance = entity.position().distanceTo(pos);
                if (distance > EXPLOSION_RADIUS) continue;
                entity.hurt(damageSource, (float) (40.0f * (1.0 - (distance / EXPLOSION_RADIUS))));
            }
            this.level().explode(this, pos.x, pos.y, pos.z, 6.0f, Level.ExplosionInteraction.MOB);
            this.discard();
        }
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }

    public boolean getIsFalling() { return this.entityData.get(IS_DESTROYED); }

    // Тики, прошедшие с момента НАЧАЛА штопора (а не общий возраст сущности).
    // Нужен рендереру, чтобы крен/тангаж при падении считались от 0, без рывка в момент сбития.
    public int getDestroyAnimTicks() { return this.destroyAnimTicks; }
    public String getShahedColor() { return this.entityData.get(SHAHED_COLOR); }
    public void setShahedColor(String color) { this.entityData.set(SHAHED_COLOR, color != null ? color : "white"); }
    public FlightPhase getFlightPhase() { return this.flightPhase; }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("ShahedColor", this.getShahedColor());
        compound.putFloat("Health", this.entityData.get(HEALTH));
        compound.putBoolean("IsDestroyed", this.entityData.get(IS_DESTROYED));
        compound.putDouble("TargetAltitude", this.targetAltitude);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("ShahedColor")) { this.setShahedColor(compound.getString("ShahedColor")); }
        if (compound.contains("Health")) { this.entityData.set(HEALTH, compound.getFloat("Health")); }
        if (compound.contains("IsDestroyed")) { this.entityData.set(IS_DESTROYED, compound.getBoolean("IsDestroyed")); }
        if (compound.contains("TargetAltitude")) { setTargetAltitude(compound.getDouble("TargetAltitude")); }
    }
}