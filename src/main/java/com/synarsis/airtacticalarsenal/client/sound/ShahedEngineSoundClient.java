package com.synarsis.airtacticalarsenal.client.sound;

import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ShahedEngineSoundClient extends AbstractTickableSoundInstance {
    private final int entityId;
    private final Vec3 startPos;
    private final BlockPos targetPos;
    private final String diveMode;
    private boolean shouldStop = false;
    private boolean exploded = false;
    private boolean stopped = false;
    private long startTime;
    private long creationTime;
    private Vec3 explosionPos = null;
    private long explosionTime = 0L;
    private boolean entityRemovedLocally = false;
    private float pitchAtExplosion = 1.0f;
    private float volumeAtExplosion = 1.0f;
    private static final double CRUISE_SPEED = 1.5;
    private double diveDistance;
    private double diveTransitionDistance;
    private float currentPitch = 1.0f;
    private float targetPitch = 1.0f;
    private float currentVolumeMultiplier = 1.0f;
    private float targetVolumeMultiplier = 1.0f;
    private int divingTicks = 0;
    private int maxDivingTicks;

    private boolean inModeSwitchPhase = false;
    private int modeSwitchTicks = 0;
    private static final int MODE_SWITCH_DURATION_TICKS = 30;
    private boolean modeSwitchComplete = false;

    private Vec3 currentDronePos;
    private Vec3 lastDirection = new Vec3(1, 0, 0);
    private float localDiveProgress = 0.0f;
    private boolean isLateJoin = false;

    private int ticksAlive = 0;
    private int ticksEntityNotFound = 0;
    private static final int MAX_LIFETIME_TICKS = 6000;
    private static final int MAX_ENTITY_NOT_FOUND_TICKS = 100;

    private Vec3 lastServerPos = null;
    private Vec3 inferredVelocity = null;

    public ShahedEngineSoundClient(int entityId, Vec3 currentPos, BlockPos targetPos, String diveMode) {
        super(ModSounds.SHAHED_ENGINE.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.entityId = entityId;
        this.startPos = currentPos;
        this.currentDronePos = currentPos;
        this.targetPos = targetPos;
        this.diveMode = diveMode;
        this.looping = true;
        this.delay = 0;
        this.volume = 3.0f;
        this.pitch = 1.0f;

        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.creationTime = System.currentTimeMillis();
        this.startTime = this.creationTime;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            double distToStart = mc.player.position().distanceTo(currentPos);
            if (distToStart > 100) {
                this.isLateJoin = true;
                int soundDelayTicks = (int) (distToStart / 17.0);
                this.startTime = this.creationTime - (soundDelayTicks * 50L);
            }
        }

        if (diveMode.equals("high")) {
            this.diveDistance = 500.0;
            this.diveTransitionDistance = 530.0;
            this.maxDivingTicks = 200;
        } else {
            this.diveDistance = 150.0;
            this.diveTransitionDistance = 180.0;
            this.maxDivingTicks = 100;
        }

        this.x = currentPos.x;
        this.y = currentPos.y;
        this.z = currentPos.z;

        Vec3 targetVec = Vec3.atCenterOf(targetPos);
        Vec3 dir = targetVec.subtract(currentPos);
        if (dir.lengthSqr() > 0.01) {
            this.lastDirection = dir.normalize();
        }

        this.lastServerPos = currentPos;
        this.inferredVelocity = this.lastDirection.scale(CRUISE_SPEED);
    }

    public void updateDronePosition(Vec3 serverPos) {
        if (this.lastServerPos != null) {
            Vec3 diff = serverPos.subtract(this.lastServerPos);
            double distMoved = diff.length();
            if (distMoved > 1.0 && distMoved < 200.0) {
                this.inferredVelocity = diff.scale(1.0 / 40.0);
            }
        }
        this.lastServerPos = serverPos;
        this.currentDronePos = serverPos;
        this.ticksEntityNotFound = 0;
        this.x = this.currentDronePos.x;
        this.y = this.currentDronePos.y;
        this.z = this.currentDronePos.z;
    }

    public void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(this);
        }
    }

    public int getEntityId() {
        return this.entityId;
    }

    public boolean isExploded() {
        return this.exploded;
    }

    @Override
    public double getX() {
        return this.x;
    }

    @Override
    public double getY() {
        return this.y;
    }

    @Override
    public double getZ() {
        return this.z;
    }

    @Override
    public void tick() {
        if (this.stopped) {
            return;
        }
        this.ticksAlive++;
        if (this.ticksAlive > MAX_LIFETIME_TICKS) {
            this.forceStop();
            return;
        }
        if (this.shouldStop && !this.exploded) {
            this.volume = Math.max(0.0f, this.volume - 0.1f);
            if (this.volume <= 0.0f) {
                this.stopSoundCompletely();
            }
            return;
        }
        if (this.exploded) {
            this.handleExplosionSound();
            return;
        }
        this.updatePosition();
    }

    private void stopSoundCompletely() {
        this.stopped = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(this);
        }
    }

    private void handleExplosionSound() {
        long currentTime = System.currentTimeMillis();
        long timeSinceExplosion = currentTime - this.explosionTime;
        long fadeTimeMs = 1000;

        if (timeSinceExplosion > fadeTimeMs) {
            this.forceStop();
            return;
        }

        float fadeProgress = (float) timeSinceExplosion / (float) fadeTimeMs;
        this.pitch = this.pitchAtExplosion * (1.0f - fadeProgress * 0.2f);
        this.volume = this.volumeAtExplosion * (1.0f - fadeProgress);

        if (this.volume < 0.01f) {
            this.forceStop();
        }
    }

    private void forceStop() {
        this.volume = 0.0f;
        this.stopped = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(this);
        }
    }

    private void updatePosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            this.stopSound();
            return;
        }
        LocalPlayer player = mc.player;
        Vec3 targetVec = Vec3.atCenterOf(this.targetPos);

        boolean isFalling = false;
        if (mc.level != null) {
            net.minecraft.world.entity.Entity entity = mc.level.getEntity(this.entityId);
            if (entity != null && !entity.isRemoved()) {
                this.ticksEntityNotFound = 0;
                this.entityRemovedLocally = false;
                Vec3 realPos = entity.position();
                this.currentDronePos = realPos;
                this.x = this.currentDronePos.x;
                this.y = this.currentDronePos.y;
                this.z = this.currentDronePos.z;

                if (entity instanceof ShahedEntity shahed) {
                    isFalling = shahed.getIsFalling();
                }
            } else {
                this.ticksEntityNotFound++;
                if (this.ticksEntityNotFound > MAX_ENTITY_NOT_FOUND_TICKS) {
                    this.markExploded(this.currentDronePos);
                    return;
                }
            }
        }

        double horizontalDist = Math.sqrt(
                Math.pow(targetVec.x - this.currentDronePos.x, 2.0) +
                        Math.pow(targetVec.z - this.currentDronePos.z, 2.0));

        boolean entityActuallyDiving = false;
        if (mc.level != null) {
            net.minecraft.world.entity.Entity entity = mc.level.getEntity(this.entityId);
            if (entity instanceof ShahedEntity shahed) {
                entityActuallyDiving = shahed.getIsDiving();
            }
        }

        if (isFalling) {
            this.targetPitch = 2.4f;
            this.targetVolumeMultiplier = 3.0f;
        } else if (entityActuallyDiving) {
            this.targetPitch = 1.8f;
            this.targetVolumeMultiplier = 2.0f;
        } else {
            this.targetPitch = 1.0f;
            this.targetVolumeMultiplier = 1.0f;
        }

        this.currentPitch = Mth.approach(this.currentPitch, this.targetPitch, 0.05f);
        this.currentVolumeMultiplier = Mth.approach(this.currentVolumeMultiplier, this.targetVolumeMultiplier, 0.05f);

        this.pitch = this.currentPitch;
        Vec3 playerPos = player.position();
        double distance = this.currentDronePos.distanceTo(playerPos);
        float baseVolume = this.calculateVolume(distance);
        this.volume = baseVolume * this.currentVolumeMultiplier;

        if (isFalling && this.currentDronePos.y <= playerPos.y - 10) {
            this.markExploded(this.currentDronePos);
        }
    }

    private float calculateVolume(double distance) {
        if (distance >= 500.0) { return 0.0f; }
        if (distance < 30.0) { return 16.0f; }
        return (float) (16.0f * (1.0 - (distance / 500.0)));
    }

    public void markExploded(Vec3 position) {
        if (this.exploded || this.stopped) {
            return;
        }
        this.pitchAtExplosion = this.currentPitch;
        this.volumeAtExplosion = Math.min(this.volume, 4.0f);
        this.exploded = true;
        this.explosionPos = position;
        this.explosionTime = System.currentTimeMillis();
    }

    public void stopSound() {
        if (!this.exploded) {
            this.shouldStop = true;
        }
    }

    public void markEntityRemovedLocally() {
        this.entityRemovedLocally = true;
        this.stopSound();
    }

    @Override
    public boolean isStopped() {
        return this.stopped;
    }
}